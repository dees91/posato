#!/usr/bin/env bash
# Drives Posato on a connected, unlocked Android phone through posato-control
# and captures the states the site, the README, and the release need, without
# a hand on the phone. Run from the repository root after `build -t android`.
# ANDROID_SERIAL names the phone; THEME=dark (the default) or light switches
# the system theme first and names light captures `android-<state>-light`.
# A theme switch recreates Posato's screen, so each step starts from Session.
#
# The fixture matches the Mac and iPhone captures: two pause sets, Focus (the
# first set, renamed and the default, with the two synthetic domains and the
# built-in Calculator) and Evening (example.net), and the schedules Deep work
# (Focus, weekdays 09:00 to 11:00) and Evening reading (Evening, daily).
#
#   video/capture/android-captures.sh first-run  # fresh install without grants: the access step of onboarding
#   video/capture/android-captures.sh fixture    # after onboarding: Focus with domains and Calculator, Evening
#   video/capture/android-captures.sh websites   # Focus with both domains
#   video/capture/android-captures.sh session    # 45 minutes, review, active, block screen over Calculator, ended early
#   video/capture/android-captures.sh schedules  # Deep work and Evening reading, then the Pause sets list that uses them
#   video/capture/android-captures.sh offer DIR  # creates a folder workspace in DIR and shows Add a device
#   video/capture/android-captures.sh join DIR   # DIR holds another device's workspace: the pairing code step
set -euo pipefail

PC="${PC:-tools/posato-control/build/install/posato-control/bin/posato-control}"
SERIAL="${ANDROID_SERIAL:?set ANDROID_SERIAL to the adb serial of the phone}"
THEME="${THEME:-dark}"
pc() { "${PC}" "$1" -t android --udid "${SERIAL}" "${@:2}"; }
flow() { "${PC}" flow "$1" -t android --udid "${SERIAL}" "${@:2}"; }
ok() { python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["artifacts"] or d["ok"]); sys.exit(0 if d["ok"] else 1)'; }
# Navigation and the theme switch animate; a capture waits for them to settle.
shot() {
  local name="android-$1"
  [ "${THEME}" = light ] && name="${name}-light"
  sleep 1.5
  echo "capture ${name}"
  pc screenshot --name "${name}" | ok
}
steps() {
  printf '{"version":1,"launch":{"terminateExisting":false},"defaults":{"timeoutSeconds":30},"steps":%s}' "$1" | pc run --scenario - | ok
}
theme() {
  local night=yes
  [ "${THEME}" = light ] && night=no
  adb -s "${SERIAL}" shell cmd uimode night "${night}" >/dev/null
  sleep 2
  pc launch | ok
}
home() {
  pc wait --for exists --text "Session" --role button --timeout-seconds 60 | ok
  pc tap --text "Session" --role button | ok
}
found() { pc find "$@" | python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin)["result"] else 1)'; }
# The Folder sync row expands on a tap and stays expanded until the next launch.
open_folder_sync() {
  home
  steps '[{"name":"reveal","action":"scrollTo","query":{"textContains":"Folder sync","role":"button"}}]'
  if ! found --text "Use another folder" --role button && ! found --text "Use this folder" --role button; then
    pc tap --text-contains "Folder sync" --role button | ok
  fi
}

first_run() {
  theme
  pc wait --for exists --text "Make some space" --role button --timeout-seconds 60 | ok
  pc tap --text "Make some space" --role button | ok
  pc tap --text "Continue" --role button | ok
  pc wait --for exists --text "Allow access" --role button --timeout-seconds 60 | ok
  shot first-run
}

fixture() {
  home
  pc tap --text "Pause sets" --role button | ok
  pc wait --for exists --text "More actions for My set" --role button --timeout-seconds 30 | ok
  pc tap --text "More actions for My set" --role button | ok
  pc tap --text "Rename" --role button | ok
  pc type --role textField --input "Focus" --clear | ok
  pc tap --text "Save" --role button | ok
  pc wait --for exists --text-contains "Focus, Default" --role button --timeout-seconds 30 | ok
  pc tap --text-contains "Focus, Default" --role button | ok
  for domain in example.com example.net; do
    pc type --role textField --input "${domain}" --clear | ok
    pc tap --text "Add" --role button | ok
  done
  pc wait --for exists --text "Websites, 2" --role button --timeout-seconds 30 | ok
  # The picker lists every launchable app on the phone, so it is never captured.
  pc tap --text-contains "Apps," --role button | ok
  pc tap --text "Choose apps" --role button | ok
  pc tap --text "Calculator" --role button | ok
  pc tap --text "CHOOSE" --role button | ok
  pc wait --for exists --text "Apps, 1" --role button --timeout-seconds 30 | ok
  pc tap --text "Back to pause sets" --role button | ok
  flow set --name Evening --website example.net | ok
}

websites() {
  theme
  home
  pc tap --text "Pause sets" --role button | ok
  pc wait --for exists --text-contains "Focus, Default" --role button --timeout-seconds 30 | ok
  pc tap --text-contains "Focus, Default" --role button | ok
  pc wait --for exists --text-contains "example.net" --timeout-seconds 30 | ok
  shot websites
  pc tap --text "Back to pause sets" --role button | ok
}

session() {
  theme
  home
  pc tap --text "Start a session" --role button | ok
  pc wait --for exists --text "45 min" --role button --timeout-seconds 60 | ok
  pc tap --text "45 min" --role button | ok
  shot duration-45
  steps '[{"name":"review-reveal","action":"scrollTo","query":{"text":"Review session","role":"button"}},
    {"name":"review","action":"tap","query":{"text":"Review session","role":"button"}}]'
  pc wait --for exists --text "Start this pause" --role button --timeout-seconds 60 | ok
  shot review-45
  pc tap --text "Start this pause" --role button | ok
  pc wait --for exists --text "Restrictions active." --timeout-seconds 120 | ok
  shot active-45
  # Calculator is in Focus; the block screen covers it while the pause runs.
  adb -s "${SERIAL}" shell monkey -p com.sec.android.app.popupcalculator -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 \
    || adb -s "${SERIAL}" shell monkey -p com.google.android.calculator -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  pc wait --for exists --text "Paused by Posato" --timeout-seconds 30 | ok
  shot block-screen
  pc tap --text "Go to the home screen" --role button | ok
  pc launch | ok
  home
  pc tap --text "End session early" --role button | ok
  pc tap --text "End session" --role button | ok
  pc wait --for exists --text "Start a session" --role button --timeout-seconds 60 | ok
}

schedules() {
  theme
  home
  # Run it outside both plans (weekdays 09:00 to 11:00, daily 20:00 to 21:00),
  # or the plan that is due starts a pause during the run.
  flow schedule --name "Deep work" --set Focus --days weekdays --start 09:00 --end 11:00 | ok
  flow schedule --name "Evening reading" --set Evening --start 20:00 --end 21:00 | ok
  shot schedules
  pc tap --text "Pause sets" --role button | ok
  pc wait --for exists --text-contains "Used by Deep work" --role button --timeout-seconds 30 | ok
  shot pause-sets
}

offer() {
  theme
  flow folder link --path "$1" | ok
  pc tap --text "Add a device" --role button | ok
  pc wait --for exists --text "Share link" --role button --timeout-seconds 60 | ok
  shot add-device
  pc tap --text "Done" --role button | ok
}

join() {
  theme
  open_folder_sync
  # A device that left a workspace offers its last folder first.
  steps '[{"name":"another-reveal","action":"scrollTo","optional":true,"timeoutSeconds":10,"query":{"text":"Use another folder","role":"button"}},
    {"name":"another","action":"tap","optional":true,"timeoutSeconds":5,"query":{"text":"Use another folder","role":"button"}}]'
  steps '[{"name":"field","action":"scrollTo","query":{"role":"textField"}}]'
  pc type --role textField --input "$1" --clear | ok
  steps '[{"name":"use-reveal","action":"scrollTo","query":{"text":"Use this folder","role":"button"}},
    {"name":"use","action":"tap","query":{"text":"Use this folder","role":"button"}},
    {"name":"sync-reveal","action":"scrollTo","query":{"text":"Sync with this folder","role":"button"}},
    {"name":"sync","action":"tap","query":{"text":"Sync with this folder","role":"button"}}]'
  pc wait --for exists --text "Join" --role button --timeout-seconds 60 | ok
  shot_join
}

# The pairing code step is lost when the theme switch recreates the screen,
# so each theme walks to it again.
shot_join() {
  steps '[{"name":"reveal","action":"scrollTo","query":{"text":"Check again","role":"button"}}]'
  adb -s "${SERIAL}" shell input swipe 540 1500 540 1200 400
  shot join
}

case "${1:-}" in
  first-run) first_run ;;
  fixture) fixture ;;
  websites) websites ;;
  session) session ;;
  schedules) schedules ;;
  offer) offer "${2:?folder}" ;;
  join) join "${2:?folder}" ;;
  *) echo "usage: $0 first-run|fixture|websites|session|schedules|offer DIR|join DIR" >&2; exit 2 ;;
esac
