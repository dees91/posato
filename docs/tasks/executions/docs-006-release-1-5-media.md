# Execution: `DOCS-006`

- **Brief:** [Prepare the public packaging for Posato 1.5](../specifications/docs-006-release-1-5-media.md)
- **Status:** `done`; merges with the 1.5 release go
- **Review tier:** `standard`
- **Implementer:** Claude
- **Branch:** `docs-006-release-1-5-media`

## Plan

1. The 1.5 copy without devices: the limits page with ADR 0010's limits,
   the README, `posato.app`, the store text, the update feed notes, and the
   release notes; 2. capture Linux and Android screenshots and place them in
   the README and on the site; 3. independent review and closeout.

## Decisions

Decided by the agent under the maintainer's delegation of 2026-10-09 unless
marked otherwise.

- The App Store description and What's New name Linux but not Android: App
  Review guideline 2.3.10 does not allow other mobile platforms in the
  metadata. The README, the site, and the release notes name both.
- No new App Store screenshots: the four iPhone and iPad screens show
  websites, the duration, schedules, and About, which 1.5 did not change
  apart from the version on About. `RELEASE-007` decides whether About needs
  a recapture.
- The package names follow the build: jpackage writes
  `posato_<version>_<arch>.deb`, with `arm64` and `amd64` as Debian names
  the architectures. The APK name `Posato-1.5.0.apk` follows the DMG names;
  `RELEASE-007` keeps it or corrects every mention.
- Linux and Android have no updater (only the Mac reads an update feed), so
  the README and the site tell people to install the newer package the same
  way.
- **Blocker for `RELEASE-007`:** `PRIVACY.md` covers only iCloud. It says
  nothing about folder sync, the workspace key kept in a file, the Android
  local VPN and usage access, or the Linux root service. A published policy
  is the maintainer's decision, so this row leaves it unchanged, and 1.5 is
  not published until the maintainer updates it.
- ADR 0010's limit bullet that runs Android, Linux, Android reads as if the
  launcher and permission sentences were about Linux. It is accepted text,
  copied word for word, so a split is left to the maintainer.

## Result

- **Limits page.** Linux (Ubuntu 24.04 or later, arm64 and x86-64 `.deb`)
  and Android (13 or later, APK) are available from GitHub Releases and
  listed under Supported platforms; every ADR 0010 limit is on the page; only
  Windows stays planned. ADR 0010 now points to the page.
- **Text.** The README and `posato.app` describe 1.5, folder sync, and
  install steps for the `.deb` and the APK, and add a Linux and Android line
  to the short limits; the update feed notes are
  [`1.5.0-appcast-notes.txt`](../../releases/1.5.0-appcast-notes.txt); the
  App Store [description](../../store/en-US/description.txt) and
  [What's New](../../store/en-US/whats-new-1.5.0.txt) cover folder sync and
  joining by QR code or link.
- **Captures.** A 45-minute pause with example.com from `main` `305d5bd7`
  on the Linux clone (Europe/Warsaw time zone) and on the Galaxy XCover7,
  with its status bar cropped off. They are stored as
  `video/public/linux-session.png` and `android-session.png` for the site,
  and as the framed `.github/assets/step-linux-android.png` for the README.

## Review

- Independent agent, 2026-10-21: no Critical; two Required. The privacy
  policy gap is now a recorded blocker for `RELEASE-007`. The site's alt
  text claimed one pause on both devices; it now names the phone's pause.
  Recommended taken: each join description says to choose the same folder
  on the new device, the README image moved under the install steps, and
  the store description names Linux computers. Left to `RELEASE-007`: the
  APK file name and the x86-64 `.deb` name, which only the release build
  fixes.

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
> - **Posato for Android.** Android 13 or later, installed from the APK below. Paused websites are blocked by a local VPN that only answers website names, with no pause page, and paused apps are covered by a block screen. The first run asks for usage access, display over other apps, notifications, exact alarms, access to files for a sync folder, and running without battery limits.
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
| `./gradlew qualityLint` | passed on the uncommitted text change (2026-10-21) |
| `website/` `npm ci` and `npm run build` | passed: 5 pages, Linux and Android images in `dist` |
| Linux capture run | runs `20261021-012511-95e7`, `20261021-012546-2464` |
| Android capture run | runs `20261021-012303-1ad9`, `20261021-012338-7259` |
| Privacy scan of new tracked PNGs | Status bar and navigation bar cropped from the phone image; only `example.com` and app UI are visible; PIL wrote the PNGs without metadata |
