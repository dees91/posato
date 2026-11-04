#!/usr/bin/env bash
# Drives Posato in the Windows Tart clone through posato-control and captures
# the states DOCS-007 shows. Run from the repository root after `windows
# create`, `build -t windows`, `windows destroy`, `windows create`, `install
# -t windows`, `launch -t windows --fresh`, and `flow onboard -t windows`, with
# no macOS clone running. Posato on Windows follows the apps' light or dark
# mode: THEME=light (the default) or dark sets AppsUseLightTheme for the
# signed-in user and relaunches Posato before each step, and dark captures are
# named `windows-<state>-dark`. Nothing runs on the host Mac.
#
# The fixture matches the other captures: Focus (the first set, renamed and
# the default, with example.com, which opens every hour for 10 minutes, and
# example.net) and Evening (example.net). The golden lists no neutral program
# under Choose apps, so no application is chosen.
#
#   video/capture/windows-captures.sh fixture     # Focus with both domains and the window rule, Evening
#   video/capture/windows-captures.sh websites    # Focus, Websites, the rule's caption under example.com
#   video/capture/windows-captures.sh session     # a 45-minute pause with Focus, restrictions active, then ended early
#   video/capture/windows-captures.sh collect DIR # crop the title bar and the taskbar and write each capture of THEME to DIR
#
# Raw captures land in build/verification/runs/<run-id>/screenshots/.
set -euo pipefail

PC="${PC:-tools/posato-control/build/install/posato-control/bin/posato-control}"
RUNS="${RUNS_DIR:-build/verification/runs}"
NAMES=(windows-websites windows-active-45)
pc() { "${PC}" "$1" -t windows "${@:2}"; }
flow() { "${PC}" flow "$1" -t windows "${@:2}"; }
ok() { python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["artifacts"] or d["result"] or d["ok"]); sys.exit(0 if d["ok"] else 1)'; }
THEME="${THEME:-light}"
suffix() { [ "${THEME}" = dark ] && echo "-dark" || true; }
shot() { sleep 1.5; echo "capture ${1}$(suffix)"; pc screenshot --name "${1}$(suffix)" | ok; }
# A relaunch also clears the "1 added" hint that an earlier Add leaves.
theme() {
  local light=1
  [ "${THEME}" = dark ] && light=0
  "${PC}" windows exec --script "Set-ItemProperty HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize -Name AppsUseLightTheme -Value ${light} -Type DWord" | ok >/dev/null
  pc terminate | ok >/dev/null
  pc launch | ok >/dev/null
  pc wait --for exists --text "Pause sets" --role button --timeout-seconds 60 | ok >/dev/null
}

fixture() {
  flow set --name Focus --rename-from "My set" | ok
  pc tap --text-contains "Focus, Default" --role button | ok
  pc tap --text-contains "Websites," --role button | ok
  for site in example.com example.net; do
    pc type --role textField --input "${site}" --clear | ok
    pc tap --text "Add" --role button | ok
  done
  pc wait --for exists --text "Websites, 2" --role button --timeout-seconds 20 | ok
  pc tap --text "Back to pause sets" --role button | ok
  flow set --name Focus --window example.com=60/10 | ok
  flow set --name Evening --website example.net | ok
}

websites() {
  pc tap --text "Pause sets" --role button | ok
  pc tap --text-contains "Focus, Default" --role button | ok
  pc tap --text-contains "Websites," --role button | ok
  pc wait --for exists --text-contains "Opens every hour for 10 min" --timeout-seconds 20 | ok
  shot windows-websites
  pc tap --text "Back to pause sets" --role button | ok
}

session() {
  pc tap --text "Session" --role button | ok
  flow session --set Focus --minutes 45 | ok
  pc wait --for exists --text "Restrictions active." --timeout-seconds 90 | ok
  shot windows-active-45
  pc tap --text "End session early" --role button | ok
  pc wait --for exists --text "Ready to return?" --timeout-seconds 20 | ok
  pc tap --text "End session" --role button | ok
  pc wait --for exists --text "Start a session" --role button --timeout-seconds 30 | ok
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
# The guest's display is 1024 x 768 at 100 percent: keep the maximized
# window between its 23-pixel title bar and the 48-pixel taskbar, unscaled.
im = im.crop((0, 23, w, h - 48))
buf = io.BytesIO()
im.save(buf, "PNG", optimize=True)
if buf.tell() > 250 * 1024:
    buf = io.BytesIO()
    im.quantize(256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(buf, "PNG", optimize=True)
open(out, "wb").write(buf.getvalue())
print(out, im.size, buf.tell(), "bytes")
EOF
  done
}

case "${1:-}" in
  websites|session) theme ;;
esac
case "${1:-}" in
  fixture) fixture ;;
  websites) websites ;;
  session) session ;;
  collect) collect "${2:-}" ;;
  *) echo "usage: $0 fixture|websites|session|collect DIR" >&2; exit 2 ;;
esac
