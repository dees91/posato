#!/usr/bin/env bash
# Encodes the walkthrough that posato.app serves and the root README links to.
set -euo pipefail

video_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
input="${video_dir}/out/walkthrough-master.mp4"
output="${video_dir}/../website/public/media/walkthrough.mp4"
limit_bytes=$((10 * 1024 * 1024))

command -v ffmpeg >/dev/null || { echo "ffmpeg is required" >&2; exit 1; }
test -f "${input}" || { echo "Render the master first: npm run render:walkthrough" >&2; exit 1; }

ffmpeg -hide_banner -loglevel warning -y -i "${input}" -an \
  -c:v libx264 -profile:v high -preset slow -crf 24 -pix_fmt yuv420p \
  -movflags +faststart -map_metadata -1 "${output}"
size="$(stat -f%z "${output}")"
(( size <= limit_bytes )) || { echo "Walkthrough exceeds 10 MiB: ${size}" >&2; exit 1; }
echo "Wrote ${output} (${size} bytes)."
