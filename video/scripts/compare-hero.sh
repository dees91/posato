#!/usr/bin/env bash
# Renders the hero's key frames from the current storyboard and captures and
# places each beside the same frame of the hero published at a Git revision
# (default origin/main), so a change of captures or crops shows as a contact
# sheet before the media is rendered: a new row in a capture moves every crop
# taken from it.
#
#   npm run compare:hero              # against origin/main
#   npm run compare:hero -- v1.2.0    # against a tag or commit
set -euo pipefail

video_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ref="${1:-origin/main}"
out="${video_dir}/out/hero-compare"
command -v ffmpeg >/dev/null || { echo "ffmpeg is required" >&2; exit 1; }
rm -rf "${out}" && mkdir -p "${out}"

git -C "${video_dir}" show "${ref}:website/public/media/hero.mp4" > "${out}/reference.mp4"
# The middle of every state, the click on Start, and the poster frame.
frames="$(cd "${video_dir}" && node --import tsx -e '
import("./src/storyboard.ts").then(({ HERO }) => {
  const states = ["choice", "duration", "start", "active", "sync", "privacy", "close"];
  const middles = states.map((name) => Math.round((HERO[name].start + HERO[name].end) / 2));
  console.log([...new Set([...middles, HERO.start.click, HERO.posterFrame])].sort((a, b) => a - b).join(" "));
})')"

rows=()
for frame in ${frames}; do
  (cd "${video_dir}" && npx remotion still Hero "${out}/new-${frame}.png" --frame "${frame}" --log error >/dev/null)
  ffmpeg -hide_banner -loglevel error -y -i "${out}/reference.mp4" -vf "select=eq(n\\,${frame})" -frames:v 1 "${out}/old-${frame}.png"
  ffmpeg -hide_banner -loglevel error -y -i "${out}/old-${frame}.png" -i "${out}/new-${frame}.png" \
    -filter_complex "[0]scale=640:400[a];[1]scale=640:400[b];[a][b]hstack" "${out}/row-${frame}.png"
  rows+=(-i "${out}/row-${frame}.png")
done
ffmpeg -hide_banner -loglevel error -y "${rows[@]}" -filter_complex "vstack=inputs=$(( ${#rows[@]} / 2 ))" "${out}/hero-compare.png"
echo "Frames ${frames}: ${ref} on the left, this tree on the right in out/hero-compare/hero-compare.png"
