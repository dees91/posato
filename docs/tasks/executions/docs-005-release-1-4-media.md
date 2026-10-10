# Execution: `DOCS-005`

- **Brief:** [Prepare the public packaging for Posato 1.4](../specifications/docs-005-release-1-4-media.md)
- **Status:** `ready for review`; merges with the 1.4 release go
- **Review tier:** `standard`
- **Implementer:** Claude
- **Branch:** `task/docs-005-release-1-4-media`

## Plan

1. The 1.4 copy without devices; 2. recapture the showcase on the 1.4 tree
with the `DOCS-004` fixture and adapt the scripts to `DESIGN-004`; 3. recalibrate
the storyboard and render; 4. recapture the store screenshots; 5. independent
review and closeout.

## Decisions

Decided by the agent under the maintainer's delegation of 2026-10-09 unless
marked otherwise.

- The `MACOS-026` one-time limit goes into the GitHub release notes, which
  Mac users read, and not into the App Store What's New: the iPhone app has
  no quit question.
- What's New does not repeat the advice to update every device: 1.4 keeps the
  1.3 sync format (ADR 0006 unchanged since `v1.3.0`), so 1.3 and 1.4 devices
  keep syncing.
- The `Evening` fixture set holds Chess from the start, so the schedule list
  shows no missing-apps notice.
- The duration scene keeps its callout "5 minutes to 24 hours.": the scene
  times and copy are unchanged, and the new choices are visible in the
  captures.
- The App Store description names "your own length on the timer" instead of
  "5 minutes to 24 hours", because the iPhone timer allows up to 23 hours 59
  minutes; the README and the site keep the 5-minute to 24-hour range.

## Result

- **Text.** The README and `posato.app` describe the 1.4 quick choices and
  name the 1.4.0 downloads; the limits page Availability says 1.4. The App
  Store description and [What's New](../../store/en-US/whats-new-1.4.0.txt)
  cover the native design on iPhone and iPad and the quick choices; the
  update feed notes and the release notes below add the Mac items. The copy
  had a Clarity review: the vague "calmer controls" now names the press
  feedback, the release-notes opening no longer implies that the first
  update skips the quit question, and the iPhone description no longer
  promises a 24-hour timer.
- **Captures.** Every showcase capture was taken again on `27c593d`. The
  capture scripts now leave an open set before opening the Pause sets list
  on the Mac, save the timed plan through `flow schedule`, rename sets
  through the set's own menu and the alert field on the iPhone, tap the iOS
  length by its spoken label "45 minutes", and delete the iPhone schedule
  with a row swipe. The Mac and iPhone session states were captured at the
  same time, so both end at 8:49.
- **Media.** Storyboard revision 5: scenes, timing, and copy unchanged; the
  Mac targets for the set row, the add field, the tabs, Choose apps, Review
  session, and Save schedule, and all three iPhone targets were recalibrated;
  the hero's first crop and the sync scene's Mac crop moved down 32 capture
  pixels for the new feedback line. `npm run media` passed `verify`: GIF
  1,558,142 bytes, site hero 515,041 bytes, walkthrough 2,924,643 bytes.
- **Store screenshots.** Both sets recaptured on an iPhone 17 Pro Max
  (iOS 26.5) and the iPad Pro 13-inch (M5) Simulators from a 1.4.0 build
  (`Version.xcconfig` bumped only for the build and restored).
- **`DESIGN.md`.** The single Mac choice group with **Until end of day**,
  its visible labels, and the 25-minute reset are recorded as accepted by
  the maintainer without objection (2026-10-10); the iOS reflow still
  awaits confirmation.

## `SESSION-007` follow-up: Mac VoiceOver

The Mac accessibility tree gives the length buttons the titles "1 h" and
"8 h" (`AXRadioButton`, run `20261010-070746`); a guest script to read the
description attribute timed out on the Automation prompt. What VoiceOver
speaks stays `open` and is idea 36 in the
[idea queue](../../wiki/topics/mvp-open-questions.md); it is not fixed here.

## GitHub release notes for 1.4.0

For `RELEASE-006` to paste into the release. The update feed notes, which the
release commit signs, are [`1.4.0-appcast-notes.txt`](../../releases/1.4.0-appcast-notes.txt).

> Posato 1.4 feels at home on iPhone and iPad, offers longer pauses in one tap, and, from this version on, updates on the Mac without asking you to quit.
>
> Posato for Mac 1.4 is available now. Posato for iPhone 1.4 arrives on the App Store once Apple has reviewed it.
>
> ## New
>
> - **A native feel on iPhone and iPad.** Navigation bars with large titles, system menus and time pickers, and swipe actions in lists. Swipe from the left edge to go back. On the Mac, buttons and rows dim when you press them instead of showing a ripple.
> - **Longer quick choices.** Start a pause for 1, 2, 4, or 8 hours, or **Until end of day**, next to 25 and 45 minutes. Until end of day ends at midnight on the device's clock.
> - **Updates without the quit question.** From 1.4, an update installed from Posato on the Mac replaces it without asking "Quit Posato?".
>
> ## Fixes
>
> - **Mac:** Posato no longer reports setup as incomplete while its background helper works, for example on a busy Mac.
> - **Mac:** one press of **Remove workspace** removes a workspace with a long iCloud history, and Posato shows that the removal is in progress.
>
> ## Updating on Mac
>
> Posato 1.1 and later offer this update in the app, or choose **Check for Updates…** in the Posato menu; installing waits until your pause has ended. Updating from 1.2 or 1.3 still asks "Quit Posato?" once while a schedule is on; choose **Quit** to continue. From Posato 1.0, download `Posato-1.4.0.dmg` below, quit Posato, and replace the application in Applications. On an Intel Mac, download `Posato-1.4.0-intel.dmg`.
>
> ## Requirements and verification
>
> - macOS 13 or later on Apple silicon or Intel; iOS 18 or later from the [App Store](https://apps.apple.com/app/posato/id6812237585).
> - Both DMGs are signed with Developer ID and notarized by Apple. `SHA256SUMS` lists their checksums; `appcast.xml` (Apple silicon) and `appcast-intel.xml` (Intel) are the signed update feeds that Posato reads.

## Checks

- Mac captures: Tart `primary` clones on macOS 26, runs `20261010-0703*` to
  `20261010-0717*`, and the synchronized session states `20261010-0803*` to
  `20261010-0804*`; both clones destroyed.
- iPhone captures: test iPhone, runs `20261010-0728*` to `20261010-0734*`
  and `20261010-080408-af13`, `20261010-080449-6b52`.
- Store screenshots: 1320 x 2868 and 2064 x 2752 RGB PNGs without alpha.
- `video/`: `npm ci`, `npm run compare:hero`, `npm run media` with `verify`;
  a contact sheet of every walkthrough click frame.
- `website/`: `npm run build`; the changes are copy and same-size images.
- Privacy: tracked PNGs show only synthetic domains, set and schedule names,
  counts, and the Simulator status bar.
