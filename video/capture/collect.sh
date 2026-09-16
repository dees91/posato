#!/usr/bin/env bash
# Copies named raw captures from a posato-control run into video/public and
# reduces them with FFmpeg: Mac captures to 1272 pixels wide, iPhone captures
# to 660 pixels wide. Nothing else is changed.
#
#   video/capture/collect.sh mac-websites mac-apps ...
#
# Each posato-control call is its own run, so the newest run holding a capture
# of that name wins.
set -euo pipefail

runs_dir="${RUNS_DIR:-build/verification/runs}"
video_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v ffmpeg >/dev/null || { echo "ffmpeg is required" >&2; exit 1; }

for name in "$@"; do
  source="$(ls -t "${runs_dir}"/*/screenshots/"${name}.png" 2>/dev/null | head -1 || true)"
  test -n "${source}" || { echo "No run holds screenshots/${name}.png" >&2; exit 1; }
  echo "${source}"
  case "${name}" in
    mac-*) width=1272 ;;
    iphone-*) width=660 ;;
    *) echo "Unknown capture prefix: ${name}" >&2; exit 1 ;;
  esac
  ffmpeg -hide_banner -loglevel error -y -i "${source}" -vf "scale=${width}:-1:flags=lanczos" -map_metadata -1 "${video_dir}/public/${name}.png"
  echo "public/${name}.png $(stat -f%z "${video_dir}/public/${name}.png") bytes"
done
