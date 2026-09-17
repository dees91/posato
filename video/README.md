# Posato showcase media

The root README uses the `Hero` composition as a looping GIF and links the
longer `Walkthrough`; the composed `StepWebsites` and `StepDuration`
stills are the README's step screenshots; `SocialPreview` is the repository
card; and posato.app plays the hero as a silent looping video. Every output
shows real Posato captures with synthetic choices inside the same generic
device frames as the site; both videos add a synthetic cursor that names each click.
[STORYBOARD.md](STORYBOARD.md) is the human contract and
`src/storyboard.ts` its executable projection; `src/storyboard.test.ts`
enforces the storyboard rules. This is documentation artwork, not a test of
cross-device delivery or the supported OS matrix.

## Reproduce

Use Node.js 22 or later, npm, and FFmpeg with the `libx264` encoder. From this
folder:

```shell
npm ci
npm run media
```

`media` runs lint and the storyboard test, renders both masters, converts the
GIF, the site MP4 and its poster, the site walkthrough, and the stills,
then verifies every output (`npm run verify`). The first render downloads
Remotion's Chrome Headless Shell. All inputs are local; no account, signing
material, device, font download, or running Posato instance is needed. Titles
and callouts use the system sans-serif stack, so another operating system may
resolve a different font. Package versions are exact and locked by
`package-lock.json`. For interactive editing and target calibration run
`npm run dev -- --no-open` and open the printed local URL.

Outputs:

- `../.github/assets/demo.gif`: 960 x 600, 12 fps, 22 seconds, infinite loop, 8,909,899 bytes.
- `../.github/assets/step-websites.png` and `step-duration.png`: 1920 x 1080
  composed stills on a transparent background.
- `../.github/assets/social-preview.png`: 1280 x 640 repository card; upload it
  under the repository's social preview setting by hand.
- `../website/public/media/hero.mp4` and `hero-poster.jpg`: 1600 x 1000, 30 fps H.264, silent,
  faststart, 1,281,500 bytes, with a JPEG poster for the first paint and
  Reduce Motion.
- `out/hero-master.mp4` and `out/walkthrough-master.mp4`: 1600 x 1000, 30 fps,
  ignored intermediates.
- `../website/public/media/walkthrough.mp4`: 42 seconds, silent, faststart,
  served at `https://posato.app/media/walkthrough.mp4` and linked from the root
  README.

## Media budget and publication

The tracked GIF stays at or below 10 MiB, the site MP4 at or below 3 MiB, its
poster at or below 300 KiB, each composed still and the social preview at or
below 1 MiB, each capture at or below 250 KiB, and the walkthrough
below 10 MiB. `scripts/render-gif.sh` lowers frame rate, then palette, then
size to stay within budget and never shortens the story; `scripts/verify-output.sh`
asserts dimensions, durations, the infinite-loop extension, the loop seam,
faststart, capture widths, and every size budget.

The maintainer uploaded the walkthrough as a GitHub attachment on 2026-09-16,
but an attachment uploaded while the repository was private stayed unreachable
to signed-out visitors after it became public. `RELEASE-002` moved the
walkthrough to posato.app on 2026-09-17 (`user-confirmed`).

## Capture provenance

All captures come from the product revision `6850481` (the `main` commit the
`DOCS-002` branch started from), driven through `posato-control` by the
scripts in `capture/`, and reduced with FFmpeg only: Mac frames to 1272 pixels
wide, iPhone frames to 660 pixels wide. No label, timer value, service result,
or application UI has been reconstructed or retouched.

- `mac-*.png`: development-signed Posato on macOS. The fixture is
  `example.com`, `example.net`, and the built-in Chess application.
  `capture/mac-captures.sh items` removes and re-adds `example.net` and Chess
  to capture the empty, typed, and saved states, driving the native application
  picker by keyboard; `capture/mac-captures.sh session` selects 45 minutes,
  reviews, starts the pause while the maintainer confirms the administrator
  prompt, and ends it early. The setup, review, and active frames were captured
  within one minute so their end times agree. The Session screen shows the
  Mac's real state: an earlier session ended early and iCloud sync needing
  attention; the Mac's visible sync status is independent of its local
  restriction status.
- `iphone-*.png`: the same revision on a physical iPhone 13 mini in a fresh
  install, driven through `posato-control` after the first-install skip
  scenario. The two synthetic domains were added through the app; the
  maintainer granted Screen Time access and chose one built-in application in
  the system picker (`capture/iphone-captures.sh`). The active frame follows
  the app reporting **Restrictions active.** iCloud was left off, so matching
  items do not demonstrate sync.

The timeline selects observed states; its cuts do not assert elapsed session
time or iCloud delivery, and the native application pickers and the
administrator prompt are elided between captures. Raw verification captures,
logs, and identifiers remain in ignored `build/verification/`; only these
reviewed artwork exports are tracked.

## Dependencies and licenses

This project uses Remotion 4.0.525, React 19.2.3, TypeScript 5.9.3, and tsx for
the storyboard test. Remotion and its CLI package are development tools for
rendering; they are not dependencies of either Posato application. ESLint
9.39.5 keeps the flat-config compatibility of the Remotion scaffold this
project was adapted from.

Posato's source compositions and artwork use the repository's Apache-2.0
license. Remotion has its own [license](https://github.com/remotion-dev/remotion/blob/v4.0.525/LICENSE.md),
which permits individuals to create videos and images for free, including
commercial use. The maintainer confirmed individual use on 2026-09-16, and the
installed package's license was checked. Other users must check their own
eligibility. React uses MIT; TypeScript uses Apache-2.0; dependency notices
remain in their npm packages. FFmpeg is an external tool with its own
[license conditions](https://ffmpeg.org/legal.html), which depend on the build.
