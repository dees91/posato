# Execution: `DOCS-004`

- **Brief:** [Prepare the public packaging for Posato 1.3](../specifications/docs-004-release-1-3-media.md)
- **Status:** `ready for review`; merges with the 1.3 release go
- **Review tier:** `standard`
- **Implementer:** Claude
- **Branch:** `docs/docs-004-release-1-3-media`

## Plan

1. Text without devices: README, `posato.app`, the App Store subtitle,
   description, and What's New for 1.3, the `PRIVACY.md` pause set edits, and
   the GitHub release notes.
2. Recapture the showcase states on the 1.3 applications with two synthetic
   pause sets, `Focus` (the default) and `Evening`: the Mac in a Tart VM, the
   iPhone on the test iPhone, both in Dark Mode, without the maintainer.
3. Storyboard revision 4 with the **Pause sets** destination and the set
   choice, `npm run media`, and a contact sheet review.
4. Recapture the App Store screenshots on the Simulators.
5. Independent completed-change review, then closeout.

## Decisions

- **Subtitle** (`user-confirmed`, 2026-10-02): "Space for what matters.",
  because the product line has 33 characters and the subtitle allows 30.

## Result

- **Text.** The README, `posato.app`, the App Store description and What's
  New, and the store listing describe pause sets, Intel Macs on macOS 13,
  back gestures, and the update advice; `PRIVACY.md` carries the pause set
  edits from the [pause set rules](../../product/pause-sets-decisions.md#privacy),
  including the corrected sentence that the iPhone extension's copy is
  excluded from device backups.
- **Captures.** Every showcase capture was taken again on the 1.3 tree
  (`cfec5ef`), plus `mac-pause-sets` and `iphone-pause-sets`; the capture
  scripts open sets from **Pause sets**, choose a set in the schedule editor,
  scroll to **Review session** on a phone, and treat the Screen Time passcode
  step as optional when Face ID approves.
- **Media.** Storyboard revision 4: the walkthrough's websites scene goes
  through **Pause sets** and Focus, every click target is recalibrated, and
  the hero's first crop moved down to the set's website list. `npm run media`
  passed `verify`: GIF 1,620,233 bytes, site hero 497,081 bytes, walkthrough
  2,945,899 bytes, stills and social preview within budget.
- **Store screenshots.** Both sets recaptured on the iPhone 17 Pro Max and
  iPad Pro 13-inch (M5) Simulators from a 1.3.0 build (`Version.xcconfig`
  bumped only for the build and restored).

## Findings for the maintainer

Product defects observed while capturing; this row changes no product code.

1. **iOS crash, release blocker (`observed`, 3 of 4 tries on the iPhone 17
   Pro Max and iPad Pro 13-inch Simulators).** Pause sets → open a set →
   **Back to pause sets** → **Schedules** ends the app with
   `IllegalStateException: This NavigationEventDispatcher has already been
   disposed and cannot be used`, thrown by `androidx.navigationevent` while
   Compose disposes unused movable content (`NAV-001` Navigation 3). The Mac
   did not crash on the same path during the captures. Evidence: ignored
   `build/verification/docs004-nav-crash/`.
2. **Stale copy (`observed` on Mac and iPhone).** An active session says
   "The actions below edit current Paused items, which restrictions follow.";
   **Paused items** no longer exists in 1.3.
3. **Scheduled pause summary (`observed` on the Mac).** While `Evening reading`
   (set Evening: one website, no apps) ran, Session listed "2 websites,
   1 application", the counts of the default set Focus. `inferred`: the
   summary reads the general selection instead of the running parts' sets.

## Publication gates for `RELEASE-005`

- Fix or accept finding 1 before App Review; the store screenshots avoid the
  crashing path.
- Set the `PRIVACY.md` effective date to the publication date.
- Known artefact: the walkthrough frame `mac-scheduled-active` shows the
  counts of finding 3 ("2 websites, 1 application" for the set Evening);
  recapture it once finding 3 is fixed, or accept it.
- The maintainer accepts the `PRIVACY.md` edits, the screenshots, and the
  subtitle.

## GitHub release notes for 1.3.0

For `RELEASE-005` to paste into the release.

> Posato 1.3 adds pause sets, runs on Intel Macs, and lets you go back with a gesture.
>
> Posato for Mac 1.3 is available now. Posato for iPhone 1.3 arrives on the App Store once Apple has reviewed it.
>
> ## New
>
> - **Pause sets.** Keep websites and apps in named sets, such as one for work and one for evenings, and choose a set for each session or schedule. One set is the default, and your current choices become your first set. When pauses overlap, Posato pauses everything in their sets, within each device's limits, and each item stays paused until the last pause that includes it ends. On iPhone a pause covers up to 50 apps. App choices stay on each device; set names and websites sync with iCloud.
> - **Intel Macs and macOS 13.** Posato for Mac now runs on macOS 13 Ventura or later, on Apple silicon and on Intel Macs. The Intel build was checked under Rosetta in a virtual machine, not on Intel hardware.
> - **Go back with a gesture.** Swipe from the left edge on iPhone and iPad. On the Mac, swipe with two fingers on the trackpad or press Command-[.
>
> ## Fixes
>
> - **Mac:** the pause page appears again in Chrome and Safari once you let Posato control the browser.
> - **Mac:** connections to `localhost`, `127.0.0.1`, and `::1` keep working during a pause, so local tools are not blocked.
> - **Mac:** if the network service that holds Posato's proxy settings disappears during a pause, Posato says restrictions need attention instead of reporting them active, and the next pause applies again.
> - **Mac:** the background helper no longer keeps a processor core busy during a pause.
> - A session you start or end on your iPhone reaches your other devices without **Sync now**.
> - A schedule you change follows its new times on every device, also after it ended earlier that day.
>
> ## Update all your devices
>
> Once one device runs Posato 1.3, devices on an earlier version stop syncing until they update.
>
> ## Updating on Mac
>
> Posato 1.1 and later offer this update in the app, or choose **Check for Updates…** in the Posato menu; installing waits until your pause has ended. From Posato 1.0, download `Posato-1.3.0.dmg` below, quit Posato, and replace the application in Applications. On an Intel Mac, download `Posato-1.3.0-intel.dmg`.
>
> ## Requirements and verification
>
> - macOS 13 or later on Apple silicon or Intel; iOS 18 or later from the [App Store](https://apps.apple.com/app/posato/id6812237585).
> - Both DMGs are signed with Developer ID and notarized by Apple. `SHA256SUMS` lists their checksums; `appcast.xml` (Apple silicon) and `appcast-intel.xml` (Intel) are the signed update feeds that Posato reads.

## Review

- **Completed-change review:** `changes-required`, two Required findings,
  both fixed. The site's Availability and the limits page still described
  1.2 (now 1.3, both DMGs, macOS 13, and the Rosetta-only Intel check, with
  the 1.3 sync rule on the limits page); and a release-notes fix described
  a version-2 table defect that never shipped (dropped).
- **Recommended, taken:** "within each device's limits" in every overlap
  sentence and the 50-app iPhone cap in the release notes; the `MACOS-020`
  wording; the pre-1.3 shared app group name in the `PRIVACY.md` iCloud list;
  the `mac-scheduled-active` counts named as a gate above. **Optional, taken:**
  "pause sets" in the demo alt texts.

## Checks

- `video/`: `npm ci`, `npm run check` (6 storyboard tests), `npm run media`
  with `verify`; a contact sheet of every click frame and the hero opening.
- Mac captures: Tart `primary` clone, runs `20261002-1619*` to
  `20261002-1627*`; the clone was destroyed.
- iPhone captures: test iPhone, runs `20261002-1634*` to `20261002-1648*`.
- Store screenshots: 1320 x 2868 and 2064 x 2752 RGB PNGs without alpha.
- `website/`: `npm ci`, `npm run build`; no horizontal scroll at 390 and
  1440 pixels wide.
- Privacy: tracked PNGs show only synthetic domains, set and schedule names,
  and counts.
