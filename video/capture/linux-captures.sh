#!/usr/bin/env bash
# Drives Posato in the Linux Tart clone through posato-control and captures
# the states DOCS-006 shows. Run from the repository root after
# `build -t linux`, `linux create`, `install -t linux`, and `launch -t linux`
# on a fresh clone, with no macOS clone running. Posato on Linux follows
# GNOME's color scheme (LINUX-002): THEME=light (the default) or dark sets it
# with gsettings before each step, and dark captures are named
# `linux-<state>-dark`. Nothing runs on the host Mac.
#
#   video/capture/linux-captures.sh screen      # set the guest to 1280 x 961 so the maximized window matches the Mac aspect
#   video/capture/linux-captures.sh first-run   # onboarding: the step that installs the pausing service, then the rest
#   video/capture/linux-captures.sh fixture     # Focus (renamed My set, example.com, example.net, Calculator) and Evening (example.net)
#   video/capture/linux-captures.sh websites    # Focus, Websites
#   video/capture/linux-captures.sh session     # 45 minutes with Focus, then restrictions active, then ended early
#   video/capture/linux-captures.sh schedules   # Deep work (weekdays 09:00-11:00) and Evening reading (daily 20:00-21:00)
#   video/capture/linux-captures.sh folder      # a folder workspace in /mnt/shared/posato-sync, its pairing code, the linked section; then removed
#   video/capture/linux-captures.sh collect DIR # crop the title bar, scale to 1272 x 936, and write each capture of THEME to DIR
#
# Raw captures land in build/verification/runs/<run-id>/screenshots/.
set -euo pipefail

PC="${PC:-tools/posato-control/build/install/posato-control/bin/posato-control}"
RUNS="${RUNS_DIR:-build/verification/runs}"
NAMES=(linux-first-run linux-websites linux-duration-45 linux-active-45 linux-schedules linux-add-device linux-folder-sync)
pc() { "${PC}" "$1" -t linux "${@:2}"; }
ok() { python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["artifacts"] or d["result"] or d["ok"]); sys.exit(0 if d["ok"] else 1)'; }
has() { pc find "$@" | python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin)["result"] else 1)'; }
THEME="${THEME:-light}"
suffix() { [ "${THEME}" = dark ] && echo "-dark" || true; }
shot() { echo "capture ${1}$(suffix)"; pc screenshot --name "${1}$(suffix)" | ok; }
# Posato re-reads the scheme when dconf rewrites the user's settings folder.
theme() {
  local scheme=default
  [ "${THEME}" = dark ] && scheme=prefer-dark
  "${PC}" linux exec --script "dbus-run-session -- gsettings set org.gnome.desktop.interface color-scheme ${scheme}" | ok >/dev/null
  sleep 2
}

screen() {
  # The Linux window opens maximized; a 1280 x 961 screen leaves a 1280 x 942
  # window, the aspect of the 1272 x 936 Mac captures.
  "${PC}" linux exec --script 'export DISPLAY=${DISPLAY:-:0}
xrandr --newmode cap1280x961 99.55 1280 1344 1472 1664 961 964 974 997 -hsync +vsync
xrandr --addmode Virtual-1 cap1280x961
xrandr --output Virtual-1 --mode cap1280x961' | ok
}

first_run() {
  pc tap --text "Make some space" --role button | ok
  pc tap --text "Continue" --role button | ok
  pc wait --for exists --text "Allow pausing on this computer" --role button --timeout-seconds 20 | ok
  shot linux-first-run
  "${PC}" flow onboard -t linux | ok
}

fixture() {
  pc tap --text "Pause sets" --role button | ok
  # The set menu and the rename dialog are popups outside the window's tree,
  # so they are pressed by their text.
  pc tap --text "More actions for My set" --role button | ok
  pc tap --text "Rename" | ok
  pc type --role textField --input "Focus" --clear | ok
  pc tap --text "Save" --role button | ok
  pc tap --text-contains "Focus, Default" --role button | ok
  pc tap --text-contains "Websites," --role button | ok
  for site in example.com example.net; do
    pc type --role textField --input "${site}" --clear | ok
    pc tap --text "Add" --role button | ok
  done
  pc tap --text-contains "Apps," --role button | ok
  pc tap --text "Choose apps" --role button | ok
  pc tap --text "Calculator" --role button | ok
  pc tap --text "Choose" --role button | ok
  pc tap --text "Back to pause sets" --role button | ok
  "${PC}" flow set -t linux --name Evening --website example.net | ok
}

websites() {
  pc tap --text "Pause sets" --role button | ok
  pc tap --text-contains "Focus, Default" --role button | ok
  pc tap --text-contains "Websites," --role button | ok
  pc wait --for exists --text-contains "example.net" --role text --timeout-seconds 20 | ok
  shot linux-websites
  pc tap --text "Back to pause sets" --role button | ok
}

session() {
  pc tap --text "Session" --role button | ok
  pc tap --text "Start a session" --role button | ok
  pc wait --for exists --text "Review session" --role button --timeout-seconds 20 | ok
  pc tap --text "45 min" --role button | ok
  shot linux-duration-45
  pc tap --text "Review session" --role button | ok
  pc wait --for exists --text "Start this pause" --role button --timeout-seconds 20 | ok
  pc tap --text "Start this pause" --role button | ok
  pc wait --for exists --text "Restrictions active." --timeout-seconds 90 | ok
  shot linux-active-45
  pc tap --text "End session early" --role button | ok
  pc wait --for exists --text "Ready to return?" --timeout-seconds 20 | ok
  pc tap --text "End session" --role button | ok
  pc wait --for exists --text "Start a session" --role button --timeout-seconds 30 | ok
}

schedules() {
  # flow schedule adds a row each time, so a second theme only opens the list.
  pc tap --text "Schedules" --role button | ok
  if ! has --text "Actions for Deep work" --role button; then
    "${PC}" flow schedule -t linux --name "Deep work" --start 09:00 --end 11:00 --set Focus --days weekdays | ok
    "${PC}" flow schedule -t linux --name "Evening reading" --start 20:00 --end 21:00 --set Evening --days every | ok
  fi
  pc wait --for exists --text "Actions for Evening reading" --role button --timeout-seconds 20 | ok
  shot linux-schedules
}

folder() {
  "${PC}" flow folder link -t linux --path /mnt/shared/posato-sync | ok
  "${PC}" flow folder offer -t linux | ok
  shot linux-add-device
  "${PC}" flow folder done -t linux | ok
  shot linux-folder-sync
  "${PC}" flow folder remove -t linux | ok
}

collect() {
  local out="${1:?output directory}"
  mkdir -p "${out}"
  for base in "${NAMES[@]}"; do
    local source name="${base}$(suffix)"
    source="$(ls -t "${RUNS}"/*/screenshots/"${name}.png" 2>/dev/null | head -1 || true)"
    test -n "${source}" || { echo "No run holds screenshots/${name}.png; skipped" >&2; continue; }
    python3 - "${source}" "${out}/${name}.png" <<'EOF'
import io, sys
from PIL import Image
raw, out = sys.argv[1], sys.argv[2]
im = Image.open(raw).convert("RGB")
w, h = im.size
# Drop the window manager's title bar: keep the 942-pixel window at the bottom.
im = im.crop((0, h - 942, w, h)).resize((1272, 936), Image.LANCZOS)
buf = io.BytesIO()
im.save(buf, "PNG", optimize=True)
if buf.tell() > 250 * 1024:
    buf = io.BytesIO()
    im.quantize(256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(buf, "PNG", optimize=True)
open(out, "wb").write(buf.getvalue())
print(out, buf.tell(), "bytes")
EOF
  done
}

case "${1:-}" in
  first-run|websites|session|schedules|folder) theme ;;
esac
case "${1:-}" in
  screen) screen ;;
  first-run) first_run ;;
  fixture) fixture ;;
  websites) websites ;;
  session) session ;;
  schedules) schedules ;;
  folder) folder ;;
  collect) collect "${2:-}" ;;
  *) echo "usage: $0 screen|first-run|fixture|websites|session|schedules|folder|collect DIR" >&2; exit 2 ;;
esac
