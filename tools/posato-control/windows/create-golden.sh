#!/bin/bash
# Creates the Windows golden VM for posato-control's Windows target from a Windows 11 ARM64 ISO, with no interaction:
# unattended install, then the guest agent, drivers, JDK, and Python over SSH. posato-control copies its own guest
# scripts into each clone, so they are not part of the golden VM.
# usage: create-golden.sh <Windows 11 ARM64 ISO>
# Environment: POSATO_TART_WINDOWS (the Tart build with Windows guests, required), POSATO_TART_WINDOWS_AGENT
# (tart-guest-agent.exe built for windows/arm64, required), POSATO_TART_WINDOWS_HOME (default ~/.tart-windows),
# POSATO_VM_WINDOWS_GOLDEN (default posato-golden-windows).
set -euo pipefail
ISO=$1
TART=${POSATO_TART_WINDOWS:?set POSATO_TART_WINDOWS to the Tart build with Windows guests}
AGENT=${POSATO_TART_WINDOWS_AGENT:?set POSATO_TART_WINDOWS_AGENT to tart-guest-agent.exe for windows/arm64}
export TART_HOME=${POSATO_TART_WINDOWS_HOME:-$HOME/.tart-windows}
VM=${POSATO_VM_WINDOWS_GOLDEN:-posato-golden-windows}
HERE=$(cd "$(dirname "$0")" && pwd)
# An existing golden VM stays untouched: delete it yourself to build a new one
if "$TART" get "$VM" >/dev/null 2>&1; then
  echo "VM $VM already exists in $TART_HOME; delete it first to rebuild it" >&2
  exit 1
fi
WORK=$(mktemp -d)
# A failed run leaves no half-built golden VM behind, so the next run can start over;
# only a VM this run created is deleted
OWN_VM=0
cleanup() {
  local rc=$?
  if [ $rc -ne 0 ] && [ $OWN_VM -eq 1 ]; then
    "$TART" stop "$VM" --timeout 30 >/dev/null 2>&1 || true
    wait || true
    "$TART" delete "$VM" >/dev/null 2>&1 || true
  fi
  rm -rf "$WORK"
}
trap cleanup EXIT
t0=$(date +%s); log() { echo "[$(( $(date +%s) - t0 ))s] $*"; }

OWN_VM=1
"$TART" create "$VM" --from-iso "$ISO"
log "Windows installed"

# The fork's install adds storage, display, network and file-sharing drivers; tart exec and the clipboard need
# viosock and vioserial from the same virtio-win image, which tart create left in its cache
# The cache names files by digest: this is the one VirtioWin.swift pins (virtio-win 0.1.302)
VIRTIO="$TART_HOME/cache/windows/sha256:303f7ae40dad495d6ae474fdc571df58958a4dbc5c37a522d80f9a203867949d.iso"
[ -f "$VIRTIO" ] || { echo "no virtio-win 0.1.302 image in $TART_HOME/cache/windows" >&2; exit 1; }
MOUNT=$(hdiutil attach -readonly -nobrowse -plist "$VIRTIO" | plutil -extract system-entities.0.mount-point raw - 2>/dev/null \
  || hdiutil info | awk '/virtio-win/{f=1} f&&/\/Volumes\//{print $NF; exit}')
KIT="$WORK/TartSetup"
mkdir -p "$KIT/drivers/viosock" "$KIT/drivers/vioserial"
cp "$MOUNT"/viosock/w11/ARM64/* "$KIT/drivers/viosock/"
cp "$MOUNT"/vioserial/w11/ARM64/* "$KIT/drivers/vioserial/"
hdiutil detach "$MOUNT" >/dev/null
cp "$AGENT" "$KIT/tart-guest-agent.exe"
cp "$HERE"/setup-guest.ps1 "$KIT/"

# The fork's image signs in as admin/admin with SSH open on Tart's NAT network; the agent isn't there yet.
# OpenSSH runs commands through cmd.exe, which leaves a stray quote on a nested `cmd /c`, so commands run bare.
printf '#!/bin/sh\necho admin\n' > "$WORK/askpass"; chmod +x "$WORK/askpass"
SSH_OPTS=(-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR
  -o PreferredAuthentications=password -o PubkeyAuthentication=no -o ConnectTimeout=10)
export SSH_ASKPASS="$WORK/askpass" SSH_ASKPASS_REQUIRE=force DISPLAY=none

"$TART" run --no-graphics "$VM" > "$WORK/run.log" 2>&1 &
IP=$("$TART" ip "$VM" --wait 300)
for _ in $(seq 1 60); do ssh "${SSH_OPTS[@]}" "admin@$IP" ver >/dev/null 2>&1 && break; sleep 5; done
ssh "${SSH_OPTS[@]}" "admin@$IP" ver >/dev/null
log "SSH answers at $IP"
scp -q -r "${SSH_OPTS[@]}" "$KIT" "admin@$IP:C:/TartSetup"
ssh "${SSH_OPTS[@]}" "admin@$IP" 'powershell -NoProfile -ExecutionPolicy Bypass -File C:\TartSetup\setup-guest.ps1'
ssh "${SSH_OPTS[@]}" "admin@$IP" 'rmdir /s /q C:\TartSetup'
ssh "${SSH_OPTS[@]}" "admin@$IP" 'shutdown /s /t 0' || true
wait
log "guest set up"

# One more boot: the agent must answer in the signed-in session, and UAC is off from now on
"$TART" run --no-graphics "$VM" > "$WORK/run.log" 2>&1 &
for _ in $(seq 1 60); do "$TART" exec "$VM" cmd /c ver >/dev/null 2>&1 && break; sleep 5; done
"$TART" exec "$VM" cmd /c ver >/dev/null || { echo "the guest agent does not answer after the reboot" >&2; exit 1; }
"$TART" exec "$VM" query user || true  # exits 1 even when it lists the session
"$TART" stop "$VM" --timeout 120
wait
log "golden VM $VM ready"
