# Execution: `DOCS-003`

- **Brief:** [Prepare the public packaging for Posato 1.2](../specifications/docs-003-release-1-2-media.md)
- **Status:** `ready for review`
- **Review tier:** `standard`
- **Implementer:** Claude
- **Reviewer:** independent completed-change review
- **Branch:** `docs/docs-003-release-1-2-media`, stacked on `feature/schedule-002-iphone-host` (#102)
- **Updated:** 2026-09-27

## Plan

1. Recapture every showcase state on the 1.2 applications: the Mac in a Tart VM, the iPhone on the test iPhone, both in Dark Mode, without the maintainer.
2. Revise the storyboard with a schedule scene, render with `npm run media`, and review a contact sheet.
3. Describe 1.2 in the README, on `posato.app`, and on the limits page.
4. Recapture the App Store screenshots on the Simulators with a Schedules screen, and write the description, What's New, and the GitHub release notes.
5. Extend `posato-provisioning store prepare` so the description can be uploaded with the tool, as `AGENTS.md` requires, instead of by hand.

## Result

- **Showcase.** Storyboard revision 2: the hero is 26 seconds and ends on a schedule being saved; the 52.6-second walkthrough adds the schedule and a scheduled pause running on its own. The Mac no longer shows an administrator prompt at Start, because the VM finished the unified setup. `npm run media` rendered every output within budget: the GIF stepped down to 864 x 540 at 12 fps (9,664,528 bytes), the site hero MP4 is 1,571,481 bytes, the walkthrough 2,948,868 bytes. A new `StepSchedules` still joins the README.
- **Capture scripts.** `video/capture/mac-captures.sh` now drives a VM (`--vm`), picks Chess through recognized screen text because the resident helper and the picker are two processes with one name, and captures schedules. `iphone-captures.sh` needs no hand: Screen Time consent, the app picker, and the notification prompt are driven; the test schedule is deleted afterwards. `collect.sh` also reads VM run folders.
- **Pages.** README and `posato.app` gain "Plan pauses ahead" with the schedules still and screenshots, the one-time Mac setup, schedule sync, and the update-every-device advice; the limits page drops the `IOS-006` limit fixed in 1.2 and states the schedule limits (Mac setup and a running Posato, 15-minute minimum, device clocks, 1.1 devices stop syncing). The site builds; checked at 1280 and 390 pixels wide, with no horizontal scroll.
- **App Store.** Four screenshots per device (Schedules added as the third; About moved to fourth), captured from a 1.2.0 Simulator build with `Version.xcconfig` bumped only for the capture and restored; `description.txt` and `whats-new-1.2.0.txt` are the upload inputs.
- **Tool.** `store prepare --description <file>` sets the en-US description; What's New and the description go in one PATCH, and a matching text is left alone. Tests cover both paths and the input checks.
- **Deviation.** The platform matrix on the limits page still lists the 1.1 checks; `RELEASE-004` replaces it with the verified 1.2 matrix.

## GitHub release notes for 1.2.0

For `RELEASE-004` to paste into the release; replace the verification line with the recorded results.

> Posato 1.2 keeps Posato ready on your Mac and adds schedules on Mac and iPhone.
>
> ## New
>
> - **Schedules.** Plan recurring pauses by weekday and time in the new **Schedules** section. They start and end on their own on each device you set up, also when Posato's window is closed. If your Mac starts up or you sign in during a scheduled pause, it joins the pause until its planned end. Skip the next one or end one early. On a Mac, quitting Posato stops new scheduled starts until it opens again; closing the window does not.
> - **One setup on the Mac.** A single **Set up Posato** turns on blocking, opens Posato quietly at login, and lets pauses and schedules start without asking for your password each time. Find it in onboarding, on the Session screen, and in Schedules; **This Mac** keeps the detailed controls.
> - **Menu bar.** Posato stays in the menu bar when you close its window. Start, check, or end a pause from there.
> - **Pause notices.** Posato tells you when a pause ends, or when one starts on another device. You can turn this off in **About Posato**.
>
> ## Fixes
>
> - **iPhone:** reopening Posato during a session no longer ends it early.
>
> ## Update all your devices
>
> If you use Posato on more than one device, update all of them. Once a device saves a schedule, devices still on Posato 1.1 stop syncing until they update.
>
> ## Updating on Mac
>
> Posato 1.1 offers this update in the app, or choose **Check for Updates…** in the Posato menu; installing waits until your pause has ended. From Posato 1.0, download `Posato-1.2.0.dmg` below, quit Posato, and replace the application in Applications.
>
> ## Requirements and verification
>
> - macOS 15 or later on Apple silicon; iOS 18 or later from the [App Store](https://apps.apple.com/app/posato/id6812237585).
> - The DMG is signed with Developer ID and notarized by Apple. `SHA256SUMS` lists its checksum; `appcast.xml` is the signed update feed that Posato reads.

## Review

- **Completed-change review:** pending.

## Checks

- `video/`: `npm ci`, `npm run check` (10 storyboard tests), `npm run media` with `npm run verify`; hero contact sheet reviewed (it caught a notification dialog in one iPhone frame and a Session frame in place of the added-website state; both were recaptured and rendered again).
- Mac captures: Tart VM `primary` line, runs `20260927-2046*` to `20260927-2105*` and `20260927-2124*` under ignored `build/verification/runs/`.
- iPhone captures: test iPhone, runs `20260927-2100*` to `20260927-2124*`.
- Store screenshots: iPhone 17 Pro Max and iPad Pro 13-inch (M5) Simulators, 1320 x 2868 and 2064 x 2752 RGB PNGs without alpha.
- `website/`: `npm ci`, `npm run build`, preview at desktop and phone widths.
- `./gradlew :posato-provisioning:test`: passed, including the two new tests.
- Privacy: the tracked PNGs show only synthetic domains, schedule names, and counts; the phone's own account appears only in an ignored run folder.

## Final

- **Status:** `ready for review`; merge together with the `RELEASE-004` publication, because README and `posato.app` then describe 1.2.
