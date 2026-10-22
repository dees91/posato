# Posato showcase media

The root README uses the `Hero` composition as a looping GIF and links the
longer `Walkthrough`; the composed `StepWebsites`, `StepDuration`,
`StepSchedules`, `StepFolder`, and `StepExport` stills are the README's step screenshots,
each pairing the Mac with a phone (iPhone or Android); `SocialPreview` is the repository
card; and posato.app plays the hero as a silent looping video. Every output
shows real Posato captures with synthetic choices. The walkthrough and the
stills place them in the same generic device frames as the site, and the
walkthrough names each click with a synthetic cursor; the hero crops the Mac
captures directly and shows its one click, Start, the same way, then shows
a change to one pause set reaching a Mac, an iPhone, a Linux computer, and an Android phone, drawn as outlines.
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

- `../.github/assets/demo.gif`: 960 x 600, 15 fps, 24 seconds, infinite loop, 2,043,946 bytes.
- `../.github/assets/step-websites.png` (Mac and Android), `step-duration.png`
  (Mac and iPhone), `step-schedules.png` (Mac and iPhone), and
  `step-folder.png` (Mac and Android), and `step-export.png` (Mac and
  iPhone): 1920 x 1080 composed stills on a
  transparent background.
- `../.github/assets/social-preview.png`: 1280 x 640 repository card; upload it
  under the repository's social preview setting by hand.
- `../website/public/media/hero.mp4` and `hero-poster.jpg`: 1600 x 1000, 30 fps H.264, silent,
  faststart, 559,638 bytes, with a JPEG poster for the first paint, phones, and
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

All captures come from the product tree of revision `d0b34fa1` (the `main`
head of Posato 1.5 that `DOCS-006` captured on 2026-10-22), driven through
`posato-control` by the scripts in `capture/` without a hand on any
device, and reduced with FFmpeg or PIL only: Mac frames to 1272 pixels
wide, iPhone and Android frames to 660 pixels wide. No label, timer value,
service result, or application UI has been reconstructed or retouched.
Tracked captures are in the light appearance (`user-confirmed`
2026-10-22); earlier dark captures are in Git history.

- `mac-*.png`: development-signed Posato in a disposable Tart VM on macOS 26
  in Light Mode, never on the maintainer's Mac. The fixture is two pause
  sets, `Focus` (the first set, renamed with `flow set --rename-from` and the
  default, with `example.com`, `example.net`, and the built-in Chess
  application) and `Evening` (`example.net`), and two schedules, `Deep work`
  (Focus, weekdays 09:00 to 11:00) and `Evening reading` (Evening, daily,
  one hour from a time a few minutes after the capture, so that
  `mac-scheduled-active` shows it running). `vm onboard` finished the
  unified setup first, so the session started without an administrator
  prompt. `capture/mac-captures.sh` opens Focus from **Pause sets** for
  `apps` and `websites`, captures the list with `sets`, and picks Chess
  through recognized screen text, because the resident helper and the picker
  are two processes with one name; `session` selects 45 minutes with the
  default set, reviews, starts, and ends early; `schedules HH:MM` saves
  `Deep work`, captures it alone, then saves `Evening reading` through
  `flow schedule`; `scheduled` captures Session once `Evening reading`
  started on its own; and `folder` creates a folder workspace in the guest's
  `~/PosatoSync` and shows **Add a device** for `mac-add-device`, whose
  pairing code expired when `unfolder` removed the workspace. `export`
  switches on **Protect with a code** in the Export dialog for `mac-export`
  and presses **Cancel**, so its code protects no file.
- `iphone-*.png`: the same tree on the dedicated test iPhone in a fresh
  install, in Light Mode set by `appearance`, after the first-install skip
  scenario, with the Focus and Evening sets and the `Deep work` schedule
  driven by `capture/iphone-captures.sh`, with Calculator as the one
  application. Screen Time consent (`screen-time-consent.json` from the Apps
  tab) ran with the phone in Dark appearance, because the driver cannot read
  the passcode keypad in Light; the captures then ran in Light. iCloud was
  off, so matching items do not demonstrate sync. `import` adds `Deep work`
  turned off, exports through `flow export` (Save to Files into the driver
  host's folder), deletes the schedule, opens that file from **Import…**, and
  captures the review for `iphone-import` before **Cancel**.
- `android-*.png`: the same tree on a Samsung Galaxy XCover7 with Android 14
  in the light theme set by `appearance`, driven by
  `capture/android-captures.sh` with the same sets (Calculator as the app)
  and schedules; the status bar and navigation bar are cropped.
  `android-join` shows the two-step join after choosing the phone's
  synthetic `PosatoSync` folder, into which `posato-control relay` copied the
  Mac's workspace: the folder is chosen and the pairing code step is open.
- `capture/linux-captures.sh` drives a disposable Tart Ubuntu 24.04 clone in
  light and dark (`THEME`); its frames are in the pull request, not tracked,
  because the stills pair the Mac with a phone.

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
