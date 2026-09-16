#!/usr/bin/env bash
# Drives Posato on a connected, unlocked iPhone through posato-control and
# captures the states the storyboard needs. Run from the repository root after
# `build -t device --driver` (and `install -t device` for a development build).
# The maintainer chooses one built-in application in the Family Controls
# picker when asked; nothing else needs a hand. The fixture is the two
# synthetic domains and one unnamed built-in application.
#
#   video/capture/iphone-captures.sh apps      # Apps tab: empty, then one application chosen
#   video/capture/iphone-captures.sh session   # 45 minutes selected, active session, ended early
set -euo pipefail

PC="${PC:-tools/posato-control/build/install/posato-control/bin/posato-control}"
pc() { "${PC}" "$1" -t device "${@:2}"; }
ok() { python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["artifacts"] or d["ok"]); sys.exit(0 if d["ok"] else 1)'; }
shot() { echo "capture ${1}"; pc screenshot --name "$1" | ok; }
ready() { pc wait --for exists --text "Paused items" --role button --timeout-seconds 60 | ok; }
present() { pc find --text "$1" --role button 2>/dev/null | python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin)["ok"] else 1)'; }

apps() {
  ready
  pc tap --text "Paused items" --role button | ok
  pc wait --for exists --text "Search" --timeout-seconds 60 | ok
  pc tap --text-contains "Apps" --role button | ok
  if present "Clear selection"; then
    pc tap --text "Clear selection" --role button | ok
  fi
  pc wait --for exists --text "Make room beyond the browser." --timeout-seconds 60 | ok
  shot iphone-apps-empty
  pc tap --text "Choose apps" --role button | ok
  echo "Choose one built-in application in the picker on the iPhone, then tap Save there if the sheet has it."
  pc wait --for exists --text "Clear selection" --role button --timeout-seconds 300 | ok
  shot iphone-apps
  pc tap --text "Session" --role button | ok
}

session() {
  ready
  pc wait --for exists --text "Start a session" --role button --timeout-seconds 60 | ok
  pc tap --text "Start a session" --role button | ok
  pc wait --for exists --text "Review session" --role button --timeout-seconds 60 | ok
  pc tap --text "45 min" --role button | ok
  shot iphone-duration-45
  pc tap --text "Review session" --role button | ok
  pc wait --for exists --text "Start this pause" --role button --timeout-seconds 60 | ok
  pc tap --text "Start this pause" --role button | ok
  pc wait --for exists --text "End session early" --role button --timeout-seconds 180 | ok
  pc wait --for exists --text "Restrictions active." --timeout-seconds 120 | ok
  shot iphone-active-45
  pc tap --text "End session early" --role button | ok
  pc wait --for exists --text "End session" --role button --timeout-seconds 60 | ok
  pc tap --text "End session" --role button | ok
  pc wait --for exists --text "Start a session" --role button --timeout-seconds 60 | ok
}

case "${1:-}" in
  apps) apps ;;
  session) session ;;
  *) echo "usage: $0 apps|session" >&2; exit 2 ;;
esac
