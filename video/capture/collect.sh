#!/usr/bin/env bash
# Copies named raw captures from a posato-control run into video/public, or
# OUT_DIR when set, and reduces them with FFmpeg: Mac captures to 1272 pixels
# wide, iPhone captures to 660 pixels wide, and Android captures, after the
# status and navigation bars are cropped off, to 660 pixels wide. Nothing else
# is changed.
#
#   video/capture/collect.sh mac-websites mac-apps ...
#
# Each posato-control call is its own run, so the newest run holding a capture
# of that name wins.
set -euo pipefail

runs_dir="${RUNS_DIR:-build/verification/runs}"
video_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out_dir="${OUT_DIR:-${video_dir}/public}"
command -v ffmpeg >/dev/null || { echo "ffmpeg is required" >&2; exit 1; }

for name in "$@"; do
  # Desktop runs in a VM write guest/screenshots/<name>.png; iPhone driver
  # runs write driver/<device>/screenshot-<index>-<name>.png.
  source="$(ls -t "${runs_dir}"/*/guest/screenshots/"${name}.png" "${runs_dir}"/*/screenshots/"${name}.png" "${runs_dir}"/*/driver/*/screenshot-*-"${name}.png" 2>/dev/null | head -1 || true)"
  test -n "${source}" || { echo "No run holds screenshots/${name}.png" >&2; exit 1; }
  echo "${source}"
  crop=""
  case "${name}" in
    mac-*) width=1272 ;;
    iphone-*) width=660 ;;
    # The Galaxy XCover7's status bar is 73 pixels and its navigation bar 126
    # pixels of a 1080 x 2408 screen; ANDROID_BARS overrides them as "top:bottom".
    android-*)
      width=660
      bars="${ANDROID_BARS:-73:126}"
      crop="crop=iw:ih-${bars%%:*}-${bars##*:}:0:${bars%%:*},"
      ;;
    *) echo "Unknown capture prefix: ${name}" >&2; exit 1 ;;
  esac
  ffmpeg -hide_banner -loglevel error -y -i "${source}" -vf "${crop}scale=${width}:-1:flags=lanczos" -map_metadata -1 "${out_dir}/${name}.png"
  echo "${out_dir}/${name}.png $(stat -f%z "${out_dir}/${name}.png") bytes"
done
