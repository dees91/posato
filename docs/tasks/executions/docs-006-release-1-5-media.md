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
  App Store screenshots. Package names follow the build; the APK name
  `Posato-1.5.0.apk` is `user-confirmed`.
- `user-confirmed`: each "How it works" step pairs a generic computer with a
  phone instead of repeating near-identical screens: websites on the Mac and
  Android, the 45-minute setup on Linux and the iPhone, schedules on the Mac
  and the iPhone, folder pairing on Linux and Android. The separate Linux and
  Android section became an "Install Posato" section for all four.
- The new Linux and Android badges and the Mac badge's text are outlined
  from Inter (OFL) with Simple Icons (CC0) marks; the App Store badge is
  Apple's.
- The site, README, and video keep dark captures; Linux is light because
  Posato on Linux has no dark palette. Light Android and Linux sets are only
  in the pull request.
- `user-confirmed`: ADR 0010's limit bullet that ran Android, Linux, Android
  is split by platform on the limits page and in the ADR, meaning unchanged.

## Result

- **Equal platforms.** Hero and install section: four badges and one
  requirements line; README Quick start the same. Hero video: the sync scene
  shows a Mac, Linux, an iPhone, and Android with "Sync through iCloud or a
  folder you choose", privacy reads "Your cloud. Your data.", the close names
  the four platforms; the social preview shows an iPhone and Android.
- **Privacy policy** for 1.5 from the code: folder files and what the folder
  service sees, the key file (Android Keystore sealed), the pairing code and
  link, the Linux service, the Android VPN (Google Public DNS fallback when
  the network offers none), usage access, and permissions.
- **Captures** from `305d5bd7` on all four (Mac in a Tart VM, the test
  iPhone, a Tart Ubuntu clone, the Galaxy XCover7), new capture scripts.

## Findings for the maintainer

`observed`, not fixed here: on Android, linking a folder workspace renamed
the default set back to "My set" (twice) and folder mode showed "Sync with
iCloud didn't finish."; Linux ignores the desktop's dark theme and the
Android block screen ignores Dark Mode; Android loses the pairing-code step
when the screen is recreated.

## Review

- Independent agent, 2026-10-21 (first pass): no Critical; two Required
  fixed (privacy gap recorded, then closed by `AC-07`; alt text).
- Independent agent, 2026-10-21 (second pass, widened row): no Critical;
  two Required fixed: the pairing offer expiry and key recovery wording in
  `PRIVACY.md`, and the schedule claim, which Linux does not meet with its
  window closed. Recommended taken: notices only on Mac and iPhone, VPN
  consent in the notes, mark attributions; unused Android captures dropped.
- Left to `RELEASE-007`: check the x86-64 `.deb`, date the policy, and
  whether the limits page adds that Linux schedules need Posato open.

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
> - **Posato for Linux.** Ubuntu 24.04 or later on arm64 and x86-64. Posato installs a small pausing service once, with your password. Paused websites are blocked through `/etc/hosts`, with no pause page, and paused apps are ended.
> - **Posato for Android.** Android 13 or later, installed from the APK below. Paused websites are blocked by a local VPN that only answers website names, with no pause page, and paused apps are covered by a block screen. The first run asks for a local VPN that only answers website names, usage access, display over other apps, notifications, exact alarms, access to files for a sync folder, and running without battery limits.
> - **Sync through a folder.** Keep your workspace in a folder that Dropbox, OneDrive, Syncthing, iCloud Drive, or another service keeps in sync. Posato writes only encrypted files there. The Mac and iPhone can use a folder instead of iCloud; a workspace uses one or the other.
> - **Add a device by code, QR code, or link.** On the new device, choose its copy of the same folder. On a device in the workspace, choose **Add a device** for a one-time pairing code that works for 10 minutes. Scan its QR code with a phone's camera, use **Share link** or **Copy link**, or type the code. There is no way to remove a single device: to stop sharing with one, remove the workspace and create a new one in another folder.
> - **Duplicate a pause set** from its menu. A set with websites and no apps chosen on this device no longer shows a warning.
> - **Last sync.** The sync section shows when this device last synced.
>
> ## Fixes
>
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
| `./gradlew qualityLint` | passed on `1910d1d6` and after the review fixes |
| `website/` `npm run build`; wide and phone renders | passed: 5 pages, four badges, install grid |
| `video/` `npm run media` (check, renders, `verify`) | passed: GIF 1,979,021 bytes, site MP4 548,696 bytes |
| Captures (`build/verification/runs/`) | Linux `20261021-094149-2207` to `-094816-cf41`; Mac `20261021-100611-5631`, `-100849-d08d`; iPhone `20261021-102925-336a` to `-103419-b5f3`; Android `20261021-093919-363a` to `-110451-3842` |
| Privacy scan of tracked PNGs | example domains, synthetic folders, expired pairing codes only; bars cropped; no metadata |
