# Execution: `DOCS-007`

- **Brief:** [Prepare the public packaging for Posato 1.6](../specifications/docs-007-release-1-6-media.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Claude
- **Reviewer:** independent agent (completed-change review)
- **Branch:** `docs-007-release-1-6-media`
- **Updated:** 2026-11-04

## Plan

1. Copy without devices: the limits page and ADR 0010, `PRIVACY.md`, the
   README, the site, the store listings, and the release notes, in every
   shipped language, plus the provenance cleanups.
2. Media: Windows screenshots in a Tart Windows guest, stills of a break and
   of a website that opens briefly, the hero's device row, and the social
   preview.
3. Independent review with back-translation, `qualityLint`, the site build,
   and `npm run media`.

## Decisions

- `user-confirmed` 2026-11-04: Windows details and limits, the
  `SCHEDULE-007` manual-session rule, media for new things only, and merging
  through `babysit-pr` before `RELEASE-010`, with the whole 1.6 copy on
  `posato.app` before the release.
- Agent decisions under the delegation: the Windows badge uses a neutral
  four-pane window glyph drawn for Posato, because Simple Icons has no
  Windows mark and Microsoft's logos need permission; the break step pairs
  the Mac (a break) with Android (the **Open briefly** dialog); the Windows
  screenshot sits under the install steps; the translations also received
  the `SCHEDULE-007` and `TARGETS-013` privacy and limits text they had
  missed; `PRIVACY.md` is dated 4 November 2026 in every language.

## Result

- **Windows** on the limits page, in ADR 0010 (confirmed details), the
  README, the site in six languages with a download button, MSI install
  steps, and a screenshot, `PRIVACY.md` and its translations, the store
  description, and the hero and social preview with five platforms.
- **1.6 features**: a step about a break and a website that opens briefly,
  the break limits, the earlier-version sync limit, store text, What's New,
  update feed notes, and the GitHub release notes below, in every shipped
  language where the surface is translated.
- **Media**: new captures `mac-break`, `android-window-dialog`,
  `windows-websites`, `windows-active-45`; new still `step-break.png`;
  re-rendered hero, GIF, walkthrough cards, and social preview; poster and
  other stills unchanged.

## Open for the maintainer

- Upload the regenerated `.github/assets/social-preview.png` in the
  repository settings by hand.
- Polish: the site and store translate workspace as "work area", the app as
  "space" (`sync_remove_workspace` in `values-pl`); the button label now
  matches the app.
- `RELEASE-010`: the MSI signing line and, if unsigned, the SmartScreen line
  and limit.
- Driver gaps seen while capturing: on Android `flow set` fails at Save
  (`action_save_short`) and `flow set --window` finds no `targets_open_briefly`
  id; on the Mac `flow set --rename-from` refuses `--website`.

## Review

- Independent agent, 2026-11-04: no Critical; Required fixed (the policy's
  effective date; the whole-site early publication recorded as the owner's
  choice). Recommended fixed: German pause and break wording, the signing
  line, the Windows privacy paragraph; Optional folded: the Windows account
  clause, Windows 11 on x64 and Arm, French quick choices. Back-translation
  of German, French, Spanish, and Portuguese matched the English.

## GitHub release notes for 1.6.0

For `RELEASE-010` to paste into the release. The update feed notes, which
the release commit signs, are
[`1.6.0-appcast-notes.txt`](../../releases/1.6.0-appcast-notes.txt). If
`RELEASE-010` ships the MSI packages unsigned, add the SmartScreen line under
Installing and the matching limit before publishing, and use the unsigned
variant of the verification line below; otherwise name the signing identity.

> Posato 1.6 comes to Windows, lets you take a break in a scheduled pause, and opens a website briefly during a pause when you choose.
>
> Posato for Mac, Linux, Android, and Windows 1.6 is available now. Posato for iPhone 1.6 arrives on the App Store once Apple has reviewed it.
>
> ## New
>
> - **Posato for Windows.** Windows 11 on x64 and Arm, installed from an MSI with one administrator approval. A small pausing service blocks paused websites by the addresses their names lead to, with no pause page, and ends paused apps chosen from the Start menu. Closing the window keeps Posato running with an icon in the notification area, and **Open Posato at login** in About Posato starts it in the background. It syncs with your other devices through a folder.
> - **Take a break.** During a scheduled pause, choose **Take a break** on Session for 5, 10, or 15 minutes. Restrictions pause on that device and, once they sync, on your others, and return on their own. Each scheduled pause allows one break; **End break** brings restrictions back at once.
> - **Open a website briefly.** In a pause set, choose **Open briefly…** in a website's menu: during a pause it opens every 30 minutes, hour, or 2 hours, for 5, 10, or 15 minutes, on each device's own clock. Session shows when it opens next, and on a Mac the pause page says so.
> - **Your language.** Posato follows the system language in Polish, German, French, Spanish, and Portuguese, as well as English, on every platform.
>
> ## Fixes
>
> - The last onboarding step on Linux and Android names that device's own setup instead of the Mac's.
>
> ## Installing
>
> - **Mac:** Posato 1.1 and later offer this update in the app, or choose **Check for Updates…** in the Posato menu. During a pause, Posato does not install the update; install it after the pause ends. From Posato 1.0, download `Posato-1.6.0.dmg` below, quit Posato, and replace the application in Applications. On an Intel Mac, download `Posato-1.6.0-intel.dmg`.
> - **Linux:** download `posato_1.6.0_arm64.deb` or `posato_1.6.0_amd64.deb` for x86-64, run `sudo apt install ./posato_1.6.0_<arch>.deb`, then open Posato.
> - **Android:** on the phone, download `Posato-1.6.0.apk`, open it, and install it over the old version.
> - **Windows:** download `Posato-1.6.0-x64.msi`, or `Posato-1.6.0-arm64.msi` for an Arm computer, open it, and approve the installer as an administrator. Then open Posato from the Start menu and follow the first run.
>
> Update Posato on every device: a device on an earlier version stops syncing once another device takes a break or sets a rule for a website to open briefly. Read the [limits](https://posato.app/limits/) before you rely on Posato, especially for Windows.
>
> ## Requirements and verification
>
> - macOS 13 or later on Apple silicon or Intel; iOS 18 or later from the [App Store](https://apps.apple.com/app/posato/id6812237585); Ubuntu 24.04 or later; Android 13 or later; Windows 11 on x64 or Arm.
> - Both DMGs are signed with Developer ID and notarized by Apple. The APK is release-signed. The MSI packages are signed with `<identity>` (or: are not signed yet, so Windows SmartScreen asks before running them). `SHA256SUMS` lists the checksums of every file below; `appcast.xml` (Apple silicon) and `appcast-intel.xml` (Intel) are the signed update feeds that Posato for Mac reads.

## Checks

| Check | Result |
| --- | --- |
| `./gradlew quality` | passed on the branch head |
| `website/` `npm run build`; phone (390 px) and wide (1440 px) renders | passed: 30 pages, no overflow at 390 px |
| `video/` `npm run media` (check, renders, `verify`) | passed, with `compare:hero` showing only the five-device row and the close line |
| Captures (`build/verification/runs/`), 2026-11-04 | Mac VM `20261104-094212-fcbd` to `-095448-966a`; Android `-094056-0b15` to `-094700-e699`; Windows `-095553-0e52` to `-101154-4de2` |
| Privacy scan of new tracked PNGs | example domains and sample sets only; bars cropped; no metadata |
