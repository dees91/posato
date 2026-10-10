# Execution: `DOCS-005`

- **Brief:** [Prepare the public packaging for Posato 1.4](../specifications/docs-005-release-1-4-media.md)
- **Status:** `done`; merges with the 1.4 release go
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
- Chess was chosen in `Evening` with single commands after the first
  schedule list and before its final capture, so the final list and the
  scheduled pause show no missing-apps notice; the Pause sets frame was
  captured before and shows Evening without apps.
- The duration scene keeps its callout "5 minutes to 24 hours."; the App
  Store description says "your own length on the timer", because the iPhone
  timer allows up to 23 hours 59 minutes, which the README and site now say.

## Result

- **Text.** The README and `posato.app` describe the 1.4 quick choices and
  name the 1.4.0 downloads; the limits page Availability says 1.4. The App
  Store description and [What's New](../../store/en-US/whats-new-1.4.0.txt)
  cover the native design on iPhone and iPad and the quick choices; the
  update feed notes and the release notes below add the Mac items. A Clarity
  review made the design and update wording concrete.
- **Captures.** Every showcase capture was taken again on `27c593d` with
  the capture scripts adapted to the `DESIGN-004` labels and gestures; the
  Mac and iPhone session states were captured together, so both end at 8:49.
- **Media.** Storyboard revision 5: scenes, timing, and copy unchanged;
  click targets recalibrated; the hero's first crop and the sync scene's Mac
  crop moved down 32 capture pixels for the new feedback line. `npm run media` passed `verify`: GIF
  1,558,142 bytes, site hero 515,041 bytes, walkthrough 2,924,643 bytes.
- **Store screenshots.** Both sets recaptured on an iPhone 17 Pro Max
  (iOS 26.5) and the iPad Pro 13-inch (M5) Simulators from a 1.4.0 build
  (`Version.xcconfig` bumped only for the build and restored).
- **`DESIGN.md`.** The single Mac choice group with **Until end of day**,
  its visible labels, the 25-minute reset, and the iOS reflow to two rows of
  three are `user-confirmed`: the maintainer accepted all four, and the 1.4
  store screenshot set, by questionnaire on 2026-10-10.

## `SESSION-007` follow-up: Mac VoiceOver

The Mac accessibility tree gives the length buttons the titles "1 h" and
"8 h" (`AXRadioButton`, run `20261010-070746`); a guest script to read the
description attribute timed out on the Automation prompt. What VoiceOver
speaks stays `open` and is idea 36 in the
[idea queue](../../wiki/topics/mvp-open-questions.md), owned by the backlog
row `SESSION-008`; it is not fixed here.

## Review

- **Completed-change review** of `9c18b312`: `changes-required`, three
  Required findings, all fixed: the feed and release notes now say Posato
  does not install an update during a pause; the README and site name the
  iPhone's 23 hours 59 minutes; and the four `SESSION-007` choices are
  `user-confirmed` by the maintainer's questionnaire of 2026-10-10.
  Recommended, taken: the iOS 26 claim, a What's New line about the swipe
  that follows the finger instead of 1.3's edge swipe, the `MACOS-027`
  wording, the `SESSION-008` backlog row for idea 36, and fixture claims that
  match the runs. Optional, not taken: the iPad status bar shows the date in
  the Simulator's region format; recapturing all four frames for it was not
  worth it. A stale app count in the Mac Pause sets list is out of scope and
  investigated separately.

## GitHub release notes for 1.4.0

For `RELEASE-006` to paste into the release. The update feed notes, which the
release commit signs, are [`1.4.0-appcast-notes.txt`](../../releases/1.4.0-appcast-notes.txt).

> Posato 1.4 feels at home on iPhone and iPad, offers longer pauses in one tap, and, from this version on, updates on the Mac without asking you to quit.
>
> Posato for Mac 1.4 is available now. Posato for iPhone 1.4 arrives on the App Store once Apple has reviewed it.
>
> ## New
>
> - **A native feel on iPhone and iPad.** Navigation bars with large titles, system menus and time pickers, and swipe actions in lists. When you swipe back from the left edge, the whole screen follows your finger. On the Mac, buttons and rows dim when you press them instead of showing a ripple.
> - **Longer quick choices.** Start a pause for 1, 2, 4, or 8 hours, or **Until end of day**, next to 25 and 45 minutes. Until end of day ends at midnight on the device's clock.
> - **Updates without the quit question.** From 1.4, an update installed from Posato on the Mac replaces it without asking "Quit Posato?".
>
> ## Fixes
>
> - **Mac:** Posato no longer reports setup as incomplete when a busy Mac slows its background helper for a few minutes.
> - **Mac:** one press of **Remove workspace** removes a workspace with a long iCloud history, and Posato shows that the removal is in progress.
>
> ## Updating on Mac
>
> Posato 1.1 and later offer this update in the app, or choose **Check for Updates…** in the Posato menu. Updating from 1.2 or 1.3 still asks "Quit Posato?" once while a schedule is on; choose **Quit** to continue. During a pause, Posato does not install the update; install it after the pause ends. From Posato 1.0, download `Posato-1.4.0.dmg` below, quit Posato, and replace the application in Applications. On an Intel Mac, download `Posato-1.4.0-intel.dmg`.
>
> ## Requirements and verification
>
> - macOS 13 or later on Apple silicon or Intel; iOS 18 or later from the [App Store](https://apps.apple.com/app/posato/id6812237585). Posato 1.4 was checked on iOS 26.
> - Both DMGs are signed with Developer ID and notarized by Apple. `SHA256SUMS` lists their checksums; `appcast.xml` (Apple silicon) and `appcast-intel.xml` (Intel) are the signed update feeds that Posato reads.

## Checks

- Mac: Tart `primary` clones on macOS 26, runs `20261010-0703*` to
  `-0717*` and `-0803*` to `-0804*`, both destroyed. iPhone: test iPhone,
  runs `20261010-0728*` to `-0734*`, `-080408-af13`, `-080449-6b52`.
- Store screenshots: 1320 x 2868 and 2064 x 2752 RGB PNGs without alpha.
- `video/`: `npm ci`, `compare:hero`, `media` with `verify`, and a contact
  sheet of every walkthrough click frame; `website/`: `npm run build`.
- 1.4 was checked on iOS 26 and macOS 26; the notes claim iOS 26 only
  (`user-confirmed`, 2026-10-10). Tracked PNGs show only synthetic content.
