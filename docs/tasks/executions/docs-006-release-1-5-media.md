# Execution: `DOCS-006`

- **Brief:** [Prepare the public packaging for Posato 1.5](../specifications/docs-006-release-1-5-media.md)
- **Status:** `done`; merges with the 1.5 release go
- **Review tier:** `standard`
- **Implementer:** Claude
- **Branch:** `docs-006-release-1-5-media`

## Plan

1. The 1.5 copy without devices; 2. Linux and Android captures; 3. review.
On 2026-10-21 the maintainer widened the row (`user-confirmed`): four
equal platforms, fresh captures of all four, `PRIVACY.md`, the ADR 0010
split, and a `/visual-pr` description with light and dark screenshots.

## Decisions

Agent decisions under the delegations of 2026-10-09 and 2026-10-21 unless
marked otherwise.

- App Store text names Linux but not Android (guideline 2.3.10); no new
  App Store screenshots. The APK name `Posato-1.5.0.apk` is `user-confirmed`.
- `user-confirmed` 2026-10-22: every tracked capture is light (the warm
  `#F4F3EB` surface) on the site, README, and video, whose canvas stays
  dark; the stills pair the Mac with a phone (websites and folder with
  Android, duration and schedules with the iPhone); Linux appears in the
  hero's device row and, light and dark, in the pull request.
- Mac, Linux, and Android badges are outlined from Inter (OFL) with Simple
  Icons (CC0) marks; the App Store badge is Apple's.
- `user-confirmed`: ADR 0010's mixed Android and Linux limit bullet is split
  by platform, meaning unchanged.
- The folder wording and Android Dark Mode fixes concern platforms and
  modes new in 1.5, so the notes describe them as 1.5 behavior; only the
  first-set fix (`SYNC-024`) is listed under Fixes.

## Result

- **Equal platforms:** four badges and install steps on the site and in the
  README; the hero names and shows all four.
- **1.5 copy after the rebase** onto `d0b34fa1`: export and import
  (`TARGETS-007`), the two-step folder join (`SYNC-026`), Linux in the
  background with a status icon and **Open Posato at login** (`LINUX-003`)
  and its limit without the AppIndicator extension, the warmer light look,
  and the first-set fix. The Linux "keep Posato open" caveat is gone.
- **Privacy policy** for 1.5 from the code, with main's Export and import
  section after the blocking sections.
- **Captures** in light from `d0b34fa1`; `video/README.md` has provenance.
  The iPhone's Screen Time consent ran in Dark appearance, because the
  driver cannot read the passcode keypad in Light, then the captures in Light.

## Open for the maintainer

- Upload the regenerated `.github/assets/social-preview.png` in the
  repository settings by hand.
- Left to `RELEASE-007`: check the x86-64 `.deb` and date the policy.

## Review

- Independent agents, 2026-10-21 (two passes): no Critical; Required
  fixed (privacy gaps, alt text, pairing expiry, the Linux schedule claim).
- Independent agent, 2026-10-22: no Critical; Required fixed (the Linux
  icon wording, `AC-03` and `AC-06` amended, provenance qualified); the
  iPhone recapture followed the same day.

## GitHub release notes for 1.5.0

For `RELEASE-007` to paste into the release. The update feed notes, which
the release commit signs, are
[`1.5.0-appcast-notes.txt`](../../releases/1.5.0-appcast-notes.txt).

> Posato 1.5 comes to Linux and Android, and syncs through a folder that a service you already use keeps in sync.
>
> Posato for Mac, Linux, and Android 1.5 is available now. Posato for iPhone 1.5 arrives on the App Store once Apple has reviewed it.
>
> ## New
>
> - **Posato for Linux.** Ubuntu 24.04 or later on arm64 and x86-64. Posato installs a small pausing service once, with your password. Paused websites are blocked through `/etc/hosts`, with no pause page, and paused apps are ended. Closing the window keeps Posato running with an icon in the status area, **Open Posato at login** in About Posato starts it in the background, and it follows the desktop's light or dark style.
> - **Posato for Android.** Android 13 or later, installed from the APK below. Paused websites are blocked by a local VPN that only answers website names, with no pause page, and paused apps are covered by a block screen that follows Dark Mode. The first run asks for the VPN, usage access, display over other apps, notifications, exact alarms, access to files for a sync folder, and running without battery limits.
> - **Sync through a folder.** Keep your workspace in a folder that Dropbox, OneDrive, Syncthing, iCloud Drive, or another service keeps in sync. Posato writes only encrypted files there. The Mac and iPhone can use a folder instead of iCloud; a workspace uses one or the other.
> - **Add a device in two steps.** On a device in the workspace, choose **Add a device** for a one-time pairing code that works for 10 minutes. On the new device, choose its copy of the same folder and scan the QR code with a phone's camera, open the shared link, or type the code, in either order; it joins as soon as it has both. There is no way to remove a single device: to stop sharing with one, remove the workspace and create a new one in another folder.
> - **Export and import pause sets.** Choose **Export pause sets…** in the **…** menu of Pause sets to save your sets, their websites, the default set, and your schedules to a file, optionally protected by a code that Posato shows once, and **Import…** it on any device. App choices stay on each device.
> - **A warmer light look** on every platform.
> - **Duplicate a pause set** from its menu. A set with websites and no apps chosen on this device no longer shows a warning.
> - **Last sync.** The sync section shows when this device last synced.
>
> ## Fixes
>
> - A device that renamed its first pause set or chose another default before linking keeps them when it creates a workspace.
> - A device that joins during another device's pause starts pausing right away.
> - The Pause sets list shows newly chosen or removed apps at once.
> - **Mac:** the Posato window no longer opens with only its toolbar.
>
> ## Installing
>
> - **Mac:** Posato 1.1 and later offer this update in the app, or choose **Check for Updates…** in the Posato menu. During a pause, Posato does not install the update; install it after the pause ends. From Posato 1.0, download `Posato-1.5.0.dmg` below, quit Posato, and replace the application in Applications. On an Intel Mac, download `Posato-1.5.0-intel.dmg`.
> - **Linux:** download `posato_1.5.0_arm64.deb` or `posato_1.5.0_amd64.deb` for x86-64, run `sudo apt install ./posato_1.5.0_<arch>.deb`, then open Posato and follow the first run.
> - **Android:** on the phone, download `Posato-1.5.0.apk` and open it. When Android asks, allow installing apps from this source, then install, open Posato, and follow the first run.
>
> Read the [limits](https://posato.app/limits/) before you rely on Posato, especially for Linux, Android, and folder sync.
>
> ## Requirements and verification
>
> - macOS 13 or later on Apple silicon or Intel; iOS 18 or later from the [App Store](https://apps.apple.com/app/posato/id6812237585); Ubuntu 24.04 or later; Android 13 or later.
> - Both DMGs are signed with Developer ID and notarized by Apple. The APK is release-signed. `SHA256SUMS` lists the checksums of every file below; `appcast.xml` (Apple silicon) and `appcast-intel.xml` (Intel) are the signed update feeds that Posato for Mac reads.

## Checks

| Check | Result |
| --- | --- |
| `./gradlew qualityLint` | passed after the review fixes |
| `website/` `npm run build`; wide and phone renders | passed: 5 pages, light stills, no overflow at 390 px |
| `video/` `npm run media` (check, renders, `verify`) | passed: GIF 2,043,946 bytes, site MP4 559,638 bytes |
| Captures (`build/verification/runs/`), 2026-10-22 | Mac `20261022-124124-44d8` to `-131644-748f`; iPhone `-122357-15fa` to `-124055-b330`; Android `-125434-2359` to `-132829-2e53`; Linux `-131713-5c3e` to `-132711-4c0e` |
| iPhone recapture, 2026-10-22 | consent (Dark) `20261022-140740-f2d5`; `iphone-apps-empty` `-140919-a5b0`, `iphone-apps` `-140935-3e91`, `iphone-active-45` `-141050-40e6`, `iphone-schedules` `-141203-27e2` |
| Export and import pair (`step-export`), 2026-10-22 | `mac-export` `20261022-143953-2843` (VM, cancelled, code made for the dialog only); `iphone-import` `-143441-628a` after `flow export` `-143314-9724`, cancelled; `media`, site build, `qualityLint` passed |
| Privacy scan of tracked PNGs | example domains, synthetic folders, expired pairing codes only; bars cropped; no metadata |
