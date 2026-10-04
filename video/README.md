# Posato showcase media

The root README uses the `Hero` composition as a looping GIF and links the
longer `Walkthrough`; the composed `StepWebsites`, `StepDuration`, and
`StepSchedules` stills are the README's step screenshots; `SocialPreview` is the repository
card; and posato.app plays the hero as a silent looping video. Every output
shows real Posato captures with synthetic choices. The walkthrough and the
stills place them in the same generic device frames as the site, and the
walkthrough names each click with a synthetic cursor; the hero crops the Mac
captures directly and shows its one click, Start, the same way.
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

After new captures or crop changes, run `npm run compare:hero` before
`npm run media` and look at `out/hero-compare/hero-compare.png`: it places each
key hero frame of the published hero (`origin/main`, or a revision passed after
`--`) beside the same frame rendered from this tree. A new row in a capture
moves every crop taken from it, and nothing else reports that.

Outputs:

- `../.github/assets/demo.gif`: 960 x 600, 15 fps, 24 seconds, infinite loop, 1,620,233 bytes.
- `../.github/assets/step-websites.png`, `step-duration.png`, and
  `step-schedules.png`: 1920 x 1080 composed stills on a transparent
  background.
- `../.github/assets/social-preview.png`: 1280 x 640 repository card; upload it
  under the repository's social preview setting by hand.
- `../website/public/media/hero.mp4` and `hero-poster.jpg`: 1600 x 1000, 30 fps H.264, silent,
  faststart, 497,081 bytes, with a JPEG poster for the first paint, phones, and
  Reduce Motion.
- `out/hero-master.mp4` and `out/walkthrough-master.mp4`: 1600 x 1000, 30 fps,
  ignored intermediates.
- `../website/public/media/walkthrough.mp4`: 52.6 seconds, silent, faststart,
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

All captures come from the product tree of revision `cfec5ef` (the `main`
head the `DOCS-004` branch started from, Posato 1.3), driven through
`posato-control` by the scripts in `capture/` without a hand on either
device, and reduced with FFmpeg only: Mac frames to 1272 pixels wide, iPhone
frames to 660 pixels wide. No label, timer value, service result, or
application UI has been reconstructed or retouched. Earlier captures are in
Git history.

- `mac-*.png`: development-signed Posato in a disposable Tart VM on macOS 26
  in Dark Mode, never on the maintainer's Mac. The fixture is two pause sets,
  `Focus` (the first set, renamed and the default, with `example.com`,
  `example.net`, and the built-in Chess application) and `Evening`
  (`example.net`, and Chess added after the scheduled-pause frame), and two
  schedules, `Deep work` (Focus, weekdays 09:00 to 11:00) and `Evening reading`
  (Evening, daily, one hour). The VM finished the unified setup first, so the
  session started without an administrator prompt. `capture/mac-captures.sh`
  opens Focus from **Pause sets** for `apps` and `websites`, captures the
  list with `sets`, and picks Chess through recognized screen text, because
  the resident helper and the picker are two processes with one name;
  `session` selects 45 minutes with the default set, reviews, starts, and
  ends early; `schedules HH:MM` saves `Deep work`, captures it alone, then
  saves `Evening reading` with the Evening set; and `scheduled` captures
  Session once `Evening reading` started on its own. The duration, review,
  and active frames were captured within one minute so their end times agree.
- `iphone-*.png`: the same tree on the dedicated test iPhone in a fresh
  install, in Dark Mode, after the first-install skip scenario. The Focus and
  Evening sets, the two synthetic domains, the Screen Time consent, one
  built-in application in Focus (Calculator, which Posato shows only as a
  count), the notification permission, and the `Deep work` schedule were all
  driven by `capture/iphone-captures.sh`, which deletes the schedule again.
  The active frame follows the app reporting **Restrictions active.** iCloud
  was off on both devices, so matching items and the schedule on the iPhone
  do not demonstrate sync.

The timeline selects observed states; its cuts do not assert elapsed session
time or iCloud delivery, and the native application pickers and the
time wheels are elided between captures. Raw verification captures,
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
