#!/bin/bash
# Creates the Windows golden VM for the PLATFORM-002 spike from a Windows 11 ARM64 ISO, with no interaction:
# unattended install, then the guest agent, drivers, JDK, Python and the probe over SSH.
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
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
t0=$(date +%s); log() { echo "[$(( $(date +%s) - t0 ))s] $*"; }

"$TART" create "$VM" --from-iso "$ISO"
log "Windows installed"

# The fork's install adds storage, display, network and file-sharing drivers; tart exec and the clipboard need
# viosock and vioserial from the same virtio-win image, which tart create left in its cache
VIRTIO=$(ls "$TART_HOME"/cache/windows/*.iso | head -1)
MOUNT=$(hdiutil attach -readonly -nobrowse -plist "$VIRTIO" | plutil -extract system-entities.0.mount-point raw - 2>/dev/null \
  || hdiutil info | awk '/virtio-win/{f=1} f&&/\/Volumes\//{print $NF; exit}')
KIT="$WORK/TartSetup"
mkdir -p "$KIT/drivers/viosock" "$KIT/drivers/vioserial"
cp "$MOUNT"/viosock/w11/ARM64/* "$KIT/drivers/viosock/"
cp "$MOUNT"/vioserial/w11/ARM64/* "$KIT/drivers/vioserial/"
hdiutil detach "$MOUNT" >/dev/null
cp "$AGENT" "$KIT/tart-guest-agent.exe"
cp "$HERE"/guest/setup-guest.ps1 "$HERE"/guest/jab_probe.py "$HERE"/guest/screenshot.ps1 "$KIT/"

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
"$TART" exec "$VM" query user || true  # exits 1 even when it lists the session
"$TART" stop "$VM" --timeout 120
wait
log "golden VM $VM ready"
