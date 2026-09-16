#!/usr/bin/env bash
# Encodes the hero master for the posato.app hero: a silent, faststart H.264
# loop under 3 MiB plus a JPEG poster for the first paint and Reduce Motion.
set -euo pipefail

video_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
input="${video_dir}/out/hero-master.mp4"
media_dir="${video_dir}/../website/public/media"
output="${media_dir}/hero.mp4"
poster="${media_dir}/hero-poster.jpg"
limit_bytes=$((3 * 1024 * 1024))

command -v ffmpeg >/dev/null || { echo "ffmpeg is required" >&2; exit 1; }
test -f "${input}" || { echo "Render the master first: npm run render:hero" >&2; exit 1; }
mkdir -p "${media_dir}"

encode() {
  local crf="$1" scale="$2"
  ffmpeg -hide_banner -loglevel warning -y -i "${input}" -an \
    -vf "scale=${scale}:flags=lanczos,format=yuv420p" \
    -c:v libx264 -profile:v high -level 4.1 -preset slow -tune stillimage -crf "${crf}" -g 60 \
    -movflags +faststart -map_metadata -1 "${output}"
  echo "Web MP4 at crf ${crf}, ${scale}: $(stat -f%z "${output}") bytes"
}

encode 27 1600:1000
(( $(stat -f%z "${output}") <= limit_bytes )) || encode 28 1600:1000
(( $(stat -f%z "${output}") <= limit_bytes )) || encode 28 1280:800
if (( $(stat -f%z "${output}") > limit_bytes )); then
  echo "Unable to keep ${output} under 3 MiB; change the storyboard, not the duration." >&2
  exit 1
fi

ffmpeg -hide_banner -loglevel warning -y -ss 1.333 -i "${input}" -frames:v 1 \
  -vf "scale=1600:1000:flags=lanczos" -q:v 4 -map_metadata -1 "${poster}"
echo "Wrote ${output} and ${poster} ($(stat -f%z "${poster}") bytes)."
