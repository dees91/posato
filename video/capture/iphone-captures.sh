#!/usr/bin/env bash
# Drives Posato on the connected, unlocked test iPhone through posato-control
# and captures the states the storyboard needs, without a hand on the phone.
# Run from the repository root after `build -t device --driver`, a fresh
# `install -t device`, `launch -t device`, and `first-install-skip.json`, with
# the phone in Dark Mode. The fixture is two pause sets, Focus (the first set,
# renamed, with the two synthetic domains and one built-in application:
# Calculator, which Posato shows only as a count) and Evening (example.net),
# and one schedule named Deep work, which `schedules` deletes again so the
# phone is not restricted on weekday mornings.
#
#   video/capture/iphone-captures.sh websites    # Focus with both domains, then the Pause sets list
#   video/capture/iphone-captures.sh apps        # Screen Time access, Apps tab: empty, then one application
#   video/capture/iphone-captures.sh session     # 45 minutes selected, active session, ended early
#   video/capture/iphone-captures.sh schedules   # Schedules with Deep work, then deleted
set -euo pipefail

PC="${PC:-tools/posato-control/build/install/posato-control/bin/posato-control}"
SCENARIOS="tools/posato-control/fixtures/scenarios"
pc() { "${PC}" "$1" -t device "${@:2}"; }
ok() { python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["artifacts"] or d["ok"]); sys.exit(0 if d["ok"] else 1)'; }
shot() { echo "capture ${1}"; pc screenshot --name "$1" | ok; }
ready() { pc wait --for exists --text "Pause sets" --role button --timeout-seconds 60 | ok; }
open_sets() {
  pc tap --text "Pause sets" --role button | ok
  pc wait --for exists --text "New set" --role button --timeout-seconds 60 | ok
}
open_focus() {
  open_sets
  pc tap --text-contains "Focus, Default" --role button | ok
  pc wait --for exists --text "Back to Pause sets" --role button --timeout-seconds 60 | ok
}
# Adds domains in the open set; Done closes the entry and the keyboard.
# The keyboard covers later rows, so the domains are checked after Done.
add_domains() {
  for domain in "$@"; do
    pc type --role textField --input "${domain}" --clear --submit | ok
  done
  pc tap --text "Done" --role button | ok
  for domain in "$@"; do
    pc wait --for exists --text-contains "${domain}" --role text --timeout-seconds 30 | ok
  done
}
# Runs scenario steps given as JSON, for steps the single commands lack (scroll, system scopes).
steps() {
  printf '{"version":1,"launch":{"terminateExisting":false},"defaults":{"timeoutSeconds":30},"steps":%s}' "$1" | pc run --scenario - | ok
}

# The first session or schedule asks once for notification permission; allow it
# so the system dialog is not in a capture.
allow_notices() {
  steps '[{"name":"allow","action":"tap","optional":true,"timeoutSeconds":10,"query":{"scope":"springboard","text":"Allow","role":"button"}}]' >/dev/null
}

websites() {
  ready
  open_sets
  # The set's own menu renames it; the alert's field is found by its placeholder.
  pc tap --text-contains "My set, Default" --role button | ok
  pc wait --for exists --text "Back to Pause sets" --role button --timeout-seconds 60 | ok
  pc tap --text "More actions for My set" --role button | ok
  pc tap --text "Rename" --role button | ok
  pc type --text "Name" --role textField --input "Focus" --clear | ok
  pc tap --text "Save" --role button | ok
  pc wait --for exists --text "More actions for Focus" --role button --timeout-seconds 60 | ok
  add_domains example.com example.net
  pc tap --text "Back to Pause sets" --role button | ok
  pc tap --text-contains "Focus, Default" --role button | ok
  pc wait --for exists --text-contains "example.net" --role text --timeout-seconds 30 | ok
  shot iphone-websites
  pc tap --text "Back to Pause sets" --role button | ok
  pc tap --text "New set" --role button | ok
  pc type --text "Name" --role textField --input "Evening" --clear | ok
  pc tap --text "Save" --role button | ok
  pc wait --for exists --text "Back to Pause sets" --role button --timeout-seconds 60 | ok
  add_domains example.net
  pc tap --text "Back to Pause sets" --role button | ok
  pc wait --for exists --text-contains "Evening, " --role button --timeout-seconds 60 | ok
  shot iphone-pause-sets
  pc tap --text "Session" --role button | ok
}

apps() {
  ready
  open_focus
  pc tap --text-contains "Apps," --role button | ok
  if pc find --text-contains "Allow Screen Time access" | python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin)["result"] else 1)'; then
    # Choose apps asks for Screen Time first; the system dialog closes if the
    # driver starts a new run, so the request and the answers share one run.
    python3 - "${SCENARIOS}/screen-time-consent.json" <<'PY' | pc run --scenario - | ok
import json, sys
scenario = json.load(open(sys.argv[1]))
scenario["steps"][0] = {"name": "request", "action": "tap", "query": {"text": "Choose apps", "role": "button"}}
scenario["steps"] = [s for s in scenario["steps"] if s["name"] not in ("allowed", "allowed-shot")]
# Face ID can approve without the passcode keypad.
for step in scenario["steps"]:
    if step["name"] == "passcode":
        step["optional"] = True
print(json.dumps(scenario))
PY
    # The picker opens after consent; close it to capture the empty tab first.
    pc tap --text "Cancel" --role button | ok
  fi
  pc wait --for exists --text "Choose apps" --role button --timeout-seconds 60 | ok
  shot iphone-apps-empty
  steps '[{"name":"choose","action":"tap","query":{"text":"Choose apps","role":"button"}},
    {"name":"search-ready","action":"waitFor","state":"exists","query":{"text":"Search"}},
    {"name":"search","action":"type","query":{"text":"Search"},"text":"Calculator"},
    {"name":"select","action":"tap","query":{"textContains":"Calculator, "}},
    {"name":"save","action":"tap","query":{"text":"Save","role":"button"}},
    {"name":"chosen","action":"waitFor","state":"exists","query":{"textContains":"Apps, 1","role":"button"}}]'
  shot iphone-apps
  pc tap --text "Back to Pause sets" --role button | ok
  pc tap --text "Session" --role button | ok
}

session() {
  ready
  pc tap --text "Session" --role button | ok
  pc wait --for exists --text "Start a session" --role button --timeout-seconds 60 | ok
  pc tap --text "Start a session" --role button | ok
  # The Pause set row pushes Review session below the fold on a phone.
  pc wait --for exists --text "45 minutes" --role button --timeout-seconds 60 | ok
  pc tap --text "45 minutes" --role button | ok
  shot iphone-duration-45
  steps '[{"name":"review-reveal","action":"scrollTo","query":{"text":"Review session","role":"button"}},
    {"name":"review","action":"tap","query":{"text":"Review session","role":"button"}}]'
  pc wait --for exists --text "Start this pause" --role button --timeout-seconds 60 | ok
  pc tap --text "Start this pause" --role button | ok
  allow_notices
  pc wait --for exists --text "End session early" --role button --timeout-seconds 180 | ok
  pc wait --for exists --text "Restrictions active." --timeout-seconds 120 | ok
  shot iphone-active-45
  pc tap --text "End session early" --role button | ok
  pc wait --for exists --text "End session" --role button --timeout-seconds 60 | ok
  pc tap --text "End session" --role button | ok
  pc wait --for exists --text "Start a session" --role button --timeout-seconds 60 | ok
}

schedules() {
  ready
  pc tap --text "Schedules" --role button | ok
  pc wait --for exists --text "Add schedule" --role button --timeout-seconds 60 | ok
  pc tap --text "Add schedule" --role button | ok
  pc wait --for exists --role textField --timeout-seconds 60 | ok
  # Submitting closes the keyboard, which would cover Save schedule.
  pc type --role textField --input "Deep work" --clear --submit | ok
  steps '[{"name":"save-reveal","action":"scrollTo","query":{"text":"Save schedule","role":"button"}},
    {"name":"save","action":"tap","query":{"text":"Save schedule","role":"button"}}]'
  allow_notices
  pc wait --for exists --text-contains "Deep work," --role button --timeout-seconds 60 | ok
  shot iphone-schedules
  # A schedule row's actions sit behind a swipe; the deletion asks once more.
  steps '[{"name":"swipe","action":"swipeLeft","query":{"textContains":"Deep work,","role":"button"}},
    {"name":"ask","action":"tap","query":{"text":"Delete","role":"button"}},
    {"name":"question","action":"waitFor","state":"exists","query":{"text":"Keep","role":"button"}},
    {"name":"confirm","action":"tap","query":{"text":"Delete","role":"button"}},
    {"name":"deleted","action":"waitFor","state":"absent","query":{"textContains":"Deep work,","role":"button"}}]'
  pc tap --text "Session" --role button | ok
}

case "${1:-}" in
  websites) websites ;;
  apps) apps ;;
  session) session ;;
  schedules) schedules ;;
  *) echo "usage: $0 websites|apps|session|schedules" >&2; exit 2 ;;
esac
