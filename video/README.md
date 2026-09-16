# Posato showcase media

The root README uses the `Hero` composition as a looping GIF. `Walkthrough`
adds the review screen and more time for each step. Both include a recording
of deliberately ending the Mac session.
Both use real Posato captures with synthetic choices, composed in the accepted
[Posato palette](../DESIGN.md). This is documentation artwork, not a test of
cross-device delivery or the supported OS matrix.

## Reproduce

Use Node.js 22 or later, npm, and FFmpeg with the `libx264` encoder. From this
folder:

```shell
npm ci
npm run lint
npm run render:hero
npm run export:gif
npm run render:walkthrough
```

The first render downloads Remotion's Chrome Headless Shell. Subsequent renders
use the cached browser. All image and video inputs are local; no account,
signing material, device, font download, or running Posato instance is needed.
The system Arial font is used for captions; renders on another operating
system may resolve that font differently. Versions of the JavaScript packages
are exact and the dependency graph is locked by `package-lock.json`.

- `../.github/assets/demo.gif`: 960 × 600, 12 fps, 24 seconds, no audio.
- `out/hero.mp4`: 1600 × 1000, 30 fps, intermediate for the GIF.
- `out/walkthrough.mp4`: 1600 × 1000, 30 fps, 47 seconds, no audio.

For interactive editing, run `npm run dev -- --no-open` and open the printed
local URL. Choose Hero or Walkthrough. Animations use the frame clock; they
do not depend on CSS transitions or wall-clock timers.

## Media budget and publication

Keep the tracked GIF at or below 3 MiB, each PNG at or below 250 KiB, and all
tracked media together at or below 8 MiB. Keep the walkthrough below 10 MiB
for the attachment handoff. Rendered MP4s and previews under `out/` are ignored.
The short source recording in `public/` is tracked so a clean install can
reproduce the walkthrough.

The maintainer uploaded `out/walkthrough.mp4` as a GitHub attachment on
2026-09-16, and the root README links to it. While the repository is private,
the attachment is reachable only when signed in to GitHub; `RELEASE-002`
rechecks the link at publication. There is still no public download channel.

## Capture provenance

- `mac-*.png`: development-signed Posato at the DOCS-001 starting product
  revision, `7a3aee7`, driven through `posato-control` on macOS. The existing
  fixture contained `example.com`, `example.net`, and the built-in Chess app.
  No website or application choice was changed. The active-session frame was
  captured only after the maintainer approved the system prompt and the app
  reported **Restrictions active.** The session was subsequently ended.
- `mac-end.mp4`: real recording of that Mac window, including the early-end
  confirmation and return to the inactive state. FFmpeg removes the exterior
  window shadow, scales to 1272 × 936, converts to H.264/yuv420p at 30 fps,
  removes metadata, and includes no audio. Playback runs at its original speed.
- `iphone-websites.png`, `iphone-apps.png`, and `iphone-active.png`: the same
  development revision on a physical iPhone, driven through `posato-control`.
  The maintainer granted Screen Time access and chose one built-in application.
  The two synthetic domains were added through onboarding. The active frame
  follows the app reporting **Restrictions active.** This was a separate local
  session, with iCloud left off; matching items do not demonstrate sync.
- `iphone-duration.png`: a reduced copy of the accepted DESIGN-002
  [store screenshot](../docs/store/en-US/listing.md#screenshots), which shows
  the full duration controls in the real app on a larger Simulator. This frame
  does not demonstrate Screen Time permission or enforcement.

PNG preparation only reduces resolution with FFmpeg: Mac frames to 1272 pixels
wide, iPhone frames to 660 pixels wide. No labels, timer values, service results,
or application UI have been reconstructed or retouched. The timeline selects
observed states; its cuts do not assert elapsed session time or iCloud delivery.
The Mac's visible sync status is independent of its local restriction status.
Raw verification captures, logs, and identifiers remain in ignored
`build/verification/`; only these reviewed artwork exports are tracked.

## Dependencies and licenses

This project uses Remotion 4.0.525, React 19.2.3, and TypeScript 5.9.3. Remotion
and its CLI/media/transitions packages are development tools for rendering;
they are not dependencies of either Posato application. The generated blank
Remotion scaffold was adapted for this project; unused Tailwind dependencies
were removed. ESLint 9.39.5 replaces the scaffold's 9.19.0 to address the
plugin-kit advisory while retaining its flat-config compatibility.

Posato's source compositions and artwork use the repository's Apache-2.0
license. Remotion has its own [license](https://github.com/remotion-dev/remotion/blob/v4.0.525/LICENSE.md),
which permits individuals to create videos and images for free, including
commercial use. The maintainer confirmed individual use on 2026-09-16, and the
installed package's license was checked. Other users must check their own
eligibility. React uses MIT; TypeScript uses Apache-2.0; dependency notices
remain in their npm packages. FFmpeg is an external tool with its own
[license conditions](https://ffmpeg.org/legal.html), which depend on the build.
