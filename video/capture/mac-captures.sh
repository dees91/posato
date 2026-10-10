#!/usr/bin/env bash
# Drives the development-signed Posato macOS app in a Tart VM through
# posato-control and captures the states the storyboard needs. Run from the
# repository root after `build -t desktop`, `vm create`, `launch -t desktop
# --vm <line>`, the unified setup (`mac-unified-onboarding-desktop.json`), and
# the pause set fixture: the first set renamed to Focus with example.com and
# example.net, and a second set, Evening, with example.net. Choose Chess in
# Evening before the final `schedules` capture, or the Evening reading row
# shows that apps aren't chosen. The guest is switched
# to Dark Mode with `vm exec --script "defaults write -g AppleInterfaceStyle
# Dark"` before the app launches. Nothing runs on the host Mac.
#
#   video/capture/mac-captures.sh apps               # Focus, Apps tab: empty, then Chess chosen
#   video/capture/mac-captures.sh sets               # Pause sets: Focus (default) and Evening
#   video/capture/mac-captures.sh websites           # Focus, Websites: empty, typed, added, at rest
#   video/capture/mac-captures.sh session            # Session at rest, 45 minutes, review, active, ended early
#   video/capture/mac-captures.sh schedules HH:MM    # a weekday plan alone, then with a daily Evening plan starting at HH:MM
#   video/capture/mac-captures.sh evening HH:MM      # only the daily plan, when schedules stopped after Deep work
#   video/capture/mac-captures.sh scheduled          # after HH:MM: the scheduled pause running
#
# Raw captures land in build/verification/runs/<run-id>/guest/screenshots/;
# collect them with video/capture/collect.sh.
set -euo pipefail

PC="${PC:-tools/posato-control/build/install/posato-control/bin/posato-control}"
VM="${VM:-primary}"
pc() { "${PC}" "$1" -t desktop --vm "${VM}" "${@:2}"; }
ok() { python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["artifacts"] or d["ok"]); sys.exit(0 if d["ok"] else 1)'; }
shot() { echo "capture ${1}"; pc screenshot --name "$1" | ok; }
ready() { pc wait --for exists --text "Pause sets" --role button --timeout-seconds 30 | ok; }
# Opens the Pause sets list; the destination keeps its stack, so an open set is left first.
open_sets() {
  pc tap --text "Pause sets" --role button | ok
  if pc find --text "Back to pause sets" --role button | python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin)["result"] else 1)'; then
    pc tap --text "Back to pause sets" --role button | ok
  fi
  pc wait --for exists --text "New set" --role button --timeout-seconds 30 | ok
}
# Opens the Focus set from the Pause sets list.
open_focus() {
  open_sets
  pc tap --text-contains "Focus, Default" --role button | ok
  pc wait --for exists --text "Back to pause sets" --role button --timeout-seconds 30 | ok
}
back_to_sets() { pc tap --text "Back to pause sets" --role button | ok; }
# Scrolls an element into view and presses it; scrolling exists only as a scenario step.
reveal_tap() {
  local query
  query="$(python3 -c 'import json,sys; q={"text": sys.argv[1]}; q.update({"role": sys.argv[2]} if len(sys.argv) > 2 else {}); print(json.dumps(q))' "$@")"
  printf '{"version":1,"launch":{"terminateExisting":false},"steps":[{"name":"reveal","action":"scrollTo","query":%s},{"name":"tap","action":"tap","query":%s}]}' "${query}" "${query}" | pc run --scenario - | ok >/dev/null
}

apps() {
  ready
  open_focus
  pc tap --text-contains "Apps," --role button | ok
  pc wait --for exists --text "Make room beyond the browser." --timeout-seconds 20 | ok
  shot mac-apps-empty
  pc tap --text "Choose apps" --role button | ok
  # The resident helper and the picker are two PosatoMacOSHelper processes, so
  # the picker is driven by recognized text on the guest screen instead.
  "${PC}" vm wait-text --line "${VM}" --text "Chess" | ok
  "${PC}" vm click --line "${VM}" --text "Chess" | ok
  "${PC}" vm click --line "${VM}" --text "Choose" --exact | ok
  pc wait --for exists --text "Chess" --timeout-seconds 20 | ok
  shot mac-apps
  back_to_sets
}

sets() {
  ready
  open_sets
  pc wait --for exists --text-contains "Evening, " --role button --timeout-seconds 30 | ok
  shot mac-pause-sets
}

websites() {
  ready
  open_focus
  pc tap --text-contains "Websites," --role button | ok
  pc wait --for exists --text "Search" --timeout-seconds 30 | ok
  pc tap --text "Actions for example.net" --role button | ok
  pc tap --text "Remove" --role button | ok
  pc wait --for absent --text "example.net" --timeout-seconds 20 | ok
  shot mac-websites-empty
  pc type --role textField --input "example.net" --clear | ok
  shot mac-websites-typed
  pc tap --text "Add" --role button | ok
  pc wait --for exists --text-contains "example.net" --role text --timeout-seconds 20 | ok
  shot mac-websites-added
  back_to_sets
  pc tap --text-contains "Focus, Default" --role button | ok
  pc wait --for exists --text-contains "example.net" --role text --timeout-seconds 20 | ok
  shot mac-websites
  back_to_sets
}

session() {
  ready
  pc tap --text "Session" --role button | ok
  pc wait --for exists --text "Start a session" --role button --timeout-seconds 20 | ok
  shot mac-session-inactive
  pc tap --text "Start a session" --role button | ok
  pc wait --for exists --text "Review session" --role button --timeout-seconds 20 | ok
  shot mac-duration
  pc tap --text "45 min" --role button | ok
  shot mac-duration-45
  pc tap --text "Review session" --role button | ok
  pc wait --for exists --text "Start this pause" --role button --timeout-seconds 20 | ok
  shot mac-review-45
  pc tap --text "Start this pause" --role button | ok
  # After the unified setup, Start asks for no password.
  pc wait --for exists --text "End session early" --role button --timeout-seconds 90 | ok
  pc wait --for exists --text "Restrictions active." --timeout-seconds 90 | ok
  shot mac-active-45
  pc tap --text "End session early" --role button | ok
  pc wait --for exists --text "Ready to return?" --timeout-seconds 20 | ok
  shot mac-end-confirm
  pc tap --text "End session" --role button | ok
  pc wait --for exists --text "Start a session" --role button --timeout-seconds 30 | ok
}

schedules() {
  local start="${1:?start time HH:MM}"
  ready
  pc tap --text "Schedules" --role button | ok
  pc wait --for exists --text "Add schedule" --role button --timeout-seconds 20 | ok
  shot mac-schedules-empty
  pc tap --text "Add schedule" --role button | ok
  pc wait --for exists --role textField --timeout-seconds 20 | ok
  pc type --role textField --input "Deep work" --clear | ok
  shot mac-schedule-editor
  reveal_tap "Save schedule" button
  pc wait --for exists --text "Actions for Deep work" --role button --timeout-seconds 20 | ok
  shot mac-schedules-one
  evening "${start}"
}

# A daily plan with the Evening set that starts at HH:MM, so the running
# scheduled pause can be captured; flow schedule sets the editor's times.
evening() {
  local start="${1:?start time HH:MM}" hour minute end
  hour=$((10#${start%:*})); minute=$((10#${start#*:}))
  end="$(printf '%02d:%02d' $(((hour + 1) % 24)) "${minute}")"
  "${PC}" flow schedule -t desktop --vm "${VM}" --name "Evening reading" --start "${start}" --end "${end}" --set Evening --days every | ok
  pc wait --for exists --text "Actions for Evening reading" --role button --timeout-seconds 20 | ok
  shot mac-schedules
}

scheduled() {
  pc tap --text "Session" --role button | ok
  pc wait --for exists --text "A scheduled pause is running on this device." --timeout-seconds 120 | ok
  shot mac-scheduled-active
}

case "${1:-}" in
  apps) apps ;;
  sets) sets ;;
  websites) websites ;;
  session) session ;;
  schedules) schedules "${2:-}" ;;
  evening) evening "${2:-}" ;;
  scheduled) scheduled ;;
  *) echo "usage: $0 apps|sets|websites|session|schedules HH:MM|evening HH:MM|scheduled" >&2; exit 2 ;;
esac
