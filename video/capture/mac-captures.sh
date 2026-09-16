#!/usr/bin/env bash
# Drives the staged Posato macOS app through posato-control and captures the
# states the storyboard needs. Run from the repository root with the app
# already launched by this tool (`launch -t desktop`) and `doctor -t desktop`
# ok. The fixture is the two synthetic domains example.com and example.net and
# the built-in Chess application; nothing else is changed.
#
#   video/capture/mac-captures.sh items      # unattended: session, websites, apps
#   video/capture/mac-captures.sh session    # attended: confirm the administrator prompt
#
# Raw captures land in build/verification/runs/<run-id>/screenshots/; collect
# them with video/capture/collect.sh.
set -euo pipefail

PC="${PC:-tools/posato-control/build/install/posato-control/bin/posato-control}"
pc() { "${PC}" "$1" -t desktop "${@:2}"; }
ok() { python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["artifacts"] or d["ok"]); sys.exit(0 if d["ok"] else 1)'; }
shot() { echo "capture ${1}"; pc screenshot --name "$1" | ok; }
ready() { pc wait --for exists --text "Paused items" --role button --timeout-seconds 30 | ok; }

items() {
  ready
  shot mac-session-inactive
  pc tap --text "Paused items" --role button | ok
  pc wait --for exists --text "Search" --timeout-seconds 30 | ok
  pc tap --text "Actions for example.net" --role button | ok
  pc tap --text "Remove" --role button | ok
  pc wait --for absent --text "example.net" --timeout-seconds 20 | ok
  shot mac-websites-empty
  pc type --role textField --input "example.net" --clear | ok
  shot mac-websites-typed
  pc press --key return | ok
  pc wait --for exists --text "example.net" --timeout-seconds 20 | ok
  pc tap --text "Done" --role button | ok
  shot mac-websites
  pc tap --text-contains "Apps" --role button | ok
  pc wait --for exists --text "Chess" --timeout-seconds 20 | ok
  pc tap --text "Actions for Chess" --role button | ok
  pc tap --text "Remove" --role button | ok
  pc wait --for exists --text "Make room beyond the browser." --timeout-seconds 20 | ok
  shot mac-apps-empty
  pc tap --text "Choose apps" --role button | ok
  pc wait --process PosatoMacOSHelper --for exists --timeout-seconds 30 | ok
  pc press --process PosatoMacOSHelper --key g --modifiers cmd,shift | ok
  pc type --process PosatoMacOSHelper --input "/System/Applications/Chess.app" --clear --submit | ok
  pc press --process PosatoMacOSHelper --key return | ok
  pc wait --process PosatoMacOSHelper --for absent --timeout-seconds 20 | ok
  pc wait --for exists --text "Chess" --timeout-seconds 20 | ok
  shot mac-apps
  pc tap --text "Session" --role button | ok
  pc wait --for exists --text "Start a session" --role button --timeout-seconds 20 | ok
}

session() {
  ready
  pc tap --text "Start a session" --role button | ok
  pc wait --for exists --text "Review session" --role button --timeout-seconds 20 | ok
  shot mac-duration
  pc tap --text "45 min" --role button | ok
  shot mac-duration-45
  pc tap --text "Review session" --role button | ok
  pc wait --for exists --text "Start this pause" --role button --timeout-seconds 20 | ok
  shot mac-review-45
  pc tap --text "Start this pause" --role button | ok
  echo "Confirm the administrator prompt on the Mac now."
  pc wait --for exists --text "End session early" --role button --timeout-seconds 240 | ok
  pc wait --for exists --text "Restrictions active." --timeout-seconds 90 | ok
  shot mac-active-45
  pc tap --text "End session early" --role button | ok
  pc wait --for exists --text "Ready to return?" --timeout-seconds 20 | ok
  shot mac-end-confirm
  pc tap --text "End session" --role button | ok
  pc wait --for exists --text "Start a session" --role button --timeout-seconds 30 | ok
  shot mac-session-ended
}

case "${1:-}" in
  items) items ;;
  session) session ;;
  *) echo "usage: $0 items|session" >&2; exit 2 ;;
esac
