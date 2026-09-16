#!/usr/bin/env bash
# Converts the hero master into the README GIF. The ladder lowers frame rate,
# palette, then size to stay under the budget; it never shortens the story.
set -euo pipefail

video_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
input="${video_dir}/out/hero-master.mp4"
output="${video_dir}/../.github/assets/demo.gif"
limit_bytes=$((10 * 1024 * 1024))

command -v ffmpeg >/dev/null || { echo "ffmpeg is required" >&2; exit 1; }
command -v ffprobe >/dev/null || { echo "ffprobe is required" >&2; exit 1; }
test -f "${input}" || { echo "Render the master first: npm run render:hero" >&2; exit 1; }
mkdir -p "$(dirname "${output}")"

render_gif() {
  local fps="$1" width="$2" colors="$3" bayer="$4"
  ffmpeg -hide_banner -loglevel warning -y -i "${input}" \
    -filter_complex "fps=${fps},scale=${width}:-1:flags=lanczos,split[source][palette_input];[palette_input]palettegen=max_colors=${colors}:stats_mode=diff[palette];[source][palette]paletteuse=dither=bayer:bayer_scale=${bayer}:diff_mode=rectangle" \
    -loop 0 "${output}"
  echo "GIF at ${fps} fps, ${width} px, ${colors} colors: $(stat -f%z "${output}") bytes"
}

render_gif 15 960 192 4
(( $(stat -f%z "${output}") <= limit_bytes )) || render_gif 12 960 160 4
(( $(stat -f%z "${output}") <= limit_bytes )) || render_gif 12 864 160 4
(( $(stat -f%z "${output}") <= limit_bytes )) || render_gif 12 864 128 5
if (( $(stat -f%z "${output}") > limit_bytes )); then
  echo "Unable to keep ${output} under 10 MiB; change the storyboard, not the duration." >&2
  exit 1
fi

duration="$(ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "${output}")"
echo "Wrote ${output} ($(stat -f%z "${output}") bytes, ${duration}s)."
