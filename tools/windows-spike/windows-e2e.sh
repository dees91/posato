#!/bin/bash
# Windows spike E2E: disposable clone of the golden VM -> Posato jar over a shared folder -> launch in the user
# session -> drive onboarding through the Java Access Bridge with assertions -> screenshot -> delete the clone.
# usage: windows-e2e.sh <posato-windows-spike.jar> <evidence dir>
# Environment: POSATO_TART_WINDOWS (the Tart build with Windows guests, required), POSATO_TART_WINDOWS_HOME (its
# TART_HOME, default ~/.tart-windows), POSATO_VM_WINDOWS_GOLDEN (default posato-golden-windows).
set -u
JAR=$1; OUT=$2
TART=${POSATO_TART_WINDOWS:?set POSATO_TART_WINDOWS to the Tart build with Windows guests}
export TART_HOME=${POSATO_TART_WINDOWS_HOME:-$HOME/.tart-windows}
GOLDEN=${POSATO_VM_WINDOWS_GOLDEN:-posato-golden-windows}
VM=posato-run-windows
SHARE="$OUT/share"
HERE=$(cd "$(dirname "$0")" && pwd)
# A fresh staging directory, so a jar left by an earlier run can never stand in for a missing one.
# The probe and the screenshot script travel with each run, so changing them needs no new golden VM
rm -rf "$SHARE" && mkdir -p "$SHARE" && cp "$JAR" "$SHARE/posato.jar" \
  && cp "$HERE/guest/jab_probe.py" "$HERE/guest/screenshot.ps1" "$SHARE/" \
  || { echo "FAIL: could not stage $JAR and the guest scripts in $SHARE" >&2; exit 1; }
t0=$(date +%s); log() { echo "[$(( $(date +%s) - t0 ))s] $*" | tee -a "$OUT/run.log"; }
fail() { log "FAIL: $*"; "$TART" stop "$VM" --timeout 60 >/dev/null 2>&1; "$TART" delete "$VM" >/dev/null 2>&1; exit 1; }
gx() { "$TART" exec "$VM" "$@"; }

"$TART" delete "$VM" >/dev/null 2>&1
"$TART" clone "$GOLDEN" "$VM" || fail clone
log "cloned"
"$TART" run --no-graphics --dir="spike:$SHARE" "$VM" > "$OUT/tart-run.log" 2>&1 &
for i in $(seq 1 60); do gx cmd /c ver >/dev/null 2>&1 && break; sleep 2; done
gx cmd /c ver >/dev/null 2>&1 || fail "guest agent never answered"
log "guest agent answers"
for i in $(seq 1 30); do gx cmd /c "if exist Z:\\spike\\posato.jar (exit 0) else (exit 1)" >/dev/null 2>&1 && break; sleep 2; done
# Right after boot a read from the shared folder can fail with "Error performing inpage operation"; it passes on retry
for f in posato.jar jab_probe.py screenshot.ps1; do
  for attempt in 1 2 3 4 5; do
    gx cmd /c "copy /y Z:\\spike\\$f C:\\Tools\\$f" >/dev/null && break
    [ $attempt -eq 5 ] && fail "$f not reachable on Z:"
    log "retrying the copy of $f"; sleep 3
  done
done
log "jar and probe copied from the shared folder"
gx cmd /c C:\\Tools\\run-posato.cmd &
for i in $(seq 1 30); do
  W=$(gx powershell -NoProfile -Command '(Get-Process java -ErrorAction SilentlyContinue | Where-Object MainWindowTitle | Select-Object -First 1).MainWindowTitle' 2>/dev/null | tr -d '\r')
  [ "$W" = "Posato" ] && break; sleep 2
done
[ "$W" = "Posato" ] || fail "no Posato window"
log "Posato window shown"
probe() { local name=$1; shift; gx C:\\Tools\\Python\\python.exe C:\\Tools\\jab_probe.py "$@" > "$OUT/$name.json" 2> "$OUT/$name.err"; }
step() { local name=$1; probe "$@"; local rc=$?; [ $rc -eq 0 ] || fail "$name (exit $rc, see $name.err)"; log "ok: $name"; }
probe 01-welcome dump || fail "welcome step not read (see 01-welcome.err)"
grep -q '"name": "1 of 5"' "$OUT/01-welcome.json" || fail "welcome step not read"
log "ok: 01-welcome read through JAB"
step 02-privacy press "Make some space" expect "2 of 5"
step 03-access press Continue expect "3 of 5"
step 04-website press "Not now" expect "4 of 5"
step 05-typed type - example.com
step 06-added press Add expect "1 website saved"
step 07-summary press Continue expect "5 of 5"
step 08-session press "Go to Session" expect "Start a session"
gx powershell -NoProfile -ExecutionPolicy Bypass -File C:\\Tools\\screenshot.ps1 -Path Z:\\spike\\session.png >/dev/null || fail screenshot
cp "$SHARE/session.png" "$OUT/session.png"; log "screenshot captured"
gx powershell -NoProfile -Command 'Stop-Process -Name java -Force' >/dev/null 2>&1
"$TART" stop "$VM" --timeout 60 >/dev/null 2>&1; "$TART" delete "$VM"; rm -f "$SHARE/posato.jar" "$SHARE/jab_probe.py" "$SHARE/screenshot.ps1"
log "PASS: clone deleted"
