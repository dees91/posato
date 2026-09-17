#!/usr/bin/env bash
# Asserts the output contract of every tracked and hand-off media file.
set -euo pipefail

video_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo_root="$(cd "${video_dir}/.." && pwd)"
hero="${video_dir}/out/hero-master.mp4"
walkthrough="${video_dir}/out/walkthrough-master.mp4"
attachment="${video_dir}/../website/public/media/walkthrough.mp4"
gif="${repo_root}/.github/assets/demo.gif"
web="${repo_root}/website/public/media/hero.mp4"
poster="${repo_root}/website/public/media/hero-poster.jpg"
stills=("${repo_root}/.github/assets/step-websites.png" "${repo_root}/.github/assets/step-duration.png")
social="${repo_root}/.github/assets/social-preview.png"
mib=$((1024 * 1024))

for tool in ffprobe ffmpeg xxd node; do
  command -v "${tool}" >/dev/null || { echo "${tool} is required" >&2; exit 1; }
done
for file in "${hero}" "${walkthrough}" "${attachment}" "${gif}" "${web}" "${poster}" "${stills[@]}" "${social}"; do
  test -f "${file}" || { echo "Missing ${file}" >&2; exit 1; }
done

hero_frames="$(cd "${video_dir}" && node --import tsx -e 'import("./src/storyboard.ts").then((m) => console.log(m.totalFrames(m.HERO)))')"
walkthrough_frames="$(cd "${video_dir}" && node --import tsx -e 'import("./src/storyboard.ts").then((m) => console.log(m.totalFrames(m.WALKTHROUGH)))')"
hero_seconds="$(printf '%d.000000' $((hero_frames / 30)))"

meta() { ffprobe -v error -select_streams v:0 -show_entries stream=width,height,r_frame_rate,nb_frames -of csv=p=0:s=x "$1" | sed 's/x$//'; }
types() { ffprobe -v error -show_entries stream=codec_type -of csv=p=0 "$1" | tr '\n' ',' | sed 's/,$//'; }
duration() { ffprobe -v error -show_entries format=duration -of csv=p=0 "$1"; }
size() { stat -f%z "$1"; }
codec() { ffprobe -v error -select_streams v:0 -show_entries stream=codec_name,pix_fmt -of csv=p=0 "$1"; }

test "$(meta "${hero}")" = "1600x1000x30/1x${hero_frames}" || { echo "Unexpected hero metadata: $(meta "${hero}")" >&2; exit 1; }
test "$(types "${hero}")" = "video" || { echo "Hero must be one silent video stream" >&2; exit 1; }
test "$(meta "${walkthrough}")" = "1600x1000x30/1x${walkthrough_frames}" || { echo "Unexpected walkthrough metadata: $(meta "${walkthrough}")" >&2; exit 1; }
test "$(types "${walkthrough}")" = "video" || { echo "Walkthrough must be one silent video stream" >&2; exit 1; }

gif_meta="$(meta "${gif}")"
case "${gif_meta}" in
  960x600x15/1x$((hero_frames / 2))|960x600x12/1x$((hero_frames * 2 / 5))|864x540x12/1x$((hero_frames * 2 / 5))) ;;
  *) echo "Unexpected GIF metadata: ${gif_meta}" >&2; exit 1 ;;
esac
test "$(duration "${gif}")" = "${hero_seconds}" || { echo "Unexpected GIF duration: $(duration "${gif}")" >&2; exit 1; }
(( $(size "${gif}") <= 10 * mib )) || { echo "GIF exceeds 10 MiB" >&2; exit 1; }
loop_extension="$(xxd -p "${gif}" | tr -d '\n' | grep -Eo '21ff0b4e45545343415045322e300301[0-9a-f]{4}00' | head -1)"
test "${loop_extension}" = "21ff0b4e45545343415045322e300301000000" || { echo "GIF does not declare an infinite loop" >&2; exit 1; }
seam="$(ffmpeg -v error -i "${gif}" -vf "select='eq(n,0)+eq(n,$((hero_frames / 2 - 1)))+eq(n,$((hero_frames * 2 / 5 - 1)))',signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=-" -f null - 2>/dev/null | grep -o 'YAVG=[0-9.]*' | cut -d= -f2)"
for value in ${seam}; do
  # Limited-range luma of the #141B17 canvas is 37; the GIF palette lands within a few steps.
  awk -v v="${value}" 'BEGIN { exit !(v <= 45) }' || { echo "GIF loop seam is not near the canvas: YAVG ${value}" >&2; exit 1; }
done

test "$(codec "${web}")" = "h264,yuv420p" || { echo "Web MP4 must be h264 yuv420p: $(codec "${web}")" >&2; exit 1; }
test "$(types "${web}")" = "video" || { echo "Web MP4 must be silent" >&2; exit 1; }
case "$(meta "${web}")" in 1600x1000x30/1x${hero_frames}|1280x800x30/1x${hero_frames}) ;; *) echo "Unexpected web MP4 metadata: $(meta "${web}")" >&2; exit 1 ;; esac
(( $(size "${web}") <= 3 * mib )) || { echo "Web MP4 exceeds 3 MiB" >&2; exit 1; }
moov="$(grep -obUa moov "${web}" | head -1 | cut -d: -f1)"
mdat="$(grep -obUa mdat "${web}" | head -1 | cut -d: -f1)"
(( moov < mdat )) || { echo "Web MP4 is not faststart (moov after mdat)" >&2; exit 1; }
test "$(ffprobe -v error -select_streams v:0 -show_entries stream=codec_name,width,height -of csv=p=0 "${poster}")" = "mjpeg,1600,1000" || { echo "Poster must be a 1600x1000 JPEG" >&2; exit 1; }
(( $(size "${poster}") <= 300 * 1024 )) || { echo "Poster exceeds 300 KiB" >&2; exit 1; }

(( $(size "${attachment}") <= 10 * mib )) || { echo "Attachment exceeds 10 MiB" >&2; exit 1; }
test "$(meta "${attachment}")" = "1600x1000x30/1x${walkthrough_frames}" || { echo "Unexpected attachment metadata: $(meta "${attachment}")" >&2; exit 1; }

for still in "${stills[@]}"; do
  test "$(ffprobe -v error -select_streams v:0 -show_entries stream=codec_name,width,height -of csv=p=0 "${still}")" = "png,1920,1080" || { echo "Still must be a 1920x1080 PNG: ${still}" >&2; exit 1; }
  (( $(size "${still}") <= 1 * mib )) || { echo "Still exceeds 1 MiB: ${still}" >&2; exit 1; }
done
test "$(ffprobe -v error -select_streams v:0 -show_entries stream=codec_name,width,height -of csv=p=0 "${social}")" = "png,1280,640" || { echo "Social preview must be a 1280x640 PNG" >&2; exit 1; }
(( $(size "${social}") <= 1 * mib )) || { echo "Social preview exceeds 1 MiB" >&2; exit 1; }

for capture in "${video_dir}"/public/mac-*.png; do
  test "$(ffprobe -v error -select_streams v:0 -show_entries stream=width -of csv=p=0 "${capture}")" = "1272" || { echo "Mac capture is not 1272 wide: ${capture}" >&2; exit 1; }
  (( $(size "${capture}") <= 250 * 1024 )) || { echo "Capture exceeds 250 KiB: ${capture}" >&2; exit 1; }
done
for capture in "${video_dir}"/public/iphone-*.png; do
  test "$(ffprobe -v error -select_streams v:0 -show_entries stream=width -of csv=p=0 "${capture}")" = "660" || { echo "iPhone capture is not 660 wide: ${capture}" >&2; exit 1; }
  (( $(size "${capture}") <= 250 * 1024 )) || { echo "Capture exceeds 250 KiB: ${capture}" >&2; exit 1; }
done

echo "Verified hero ($(meta "${hero}")), walkthrough ($(meta "${walkthrough}")), GIF (${gif_meta}, $(size "${gif}") bytes, infinite loop, seam YAVG ${seam//$'\n'/ }), web MP4 ($(size "${web}") bytes, faststart), poster, attachment ($(size "${attachment}") bytes), stills, and social preview."
