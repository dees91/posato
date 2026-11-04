# Execution: `RELEASE-010`

- **Brief:** [Verify and publish Posato 1.6.0](../specifications/release-010-release-1-6.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent agents (plan and completed change)
- **Branch:** `release-010-release-1-6`
- **Updated:** 2026-11-04

## Plan

`releasing.md` steps 1 to 9 with the brief's differences:

1. **R:** `MARKETING_VERSION = 1.6.0`; Android `versionCode` 2 and
   `versionName` 1.6.0; the SmartScreen lines for the unsigned MSI on the
   limits page, the README, the site in six languages, and the release
   notes; the Windows signing backlog row (roadmap revision 39). The
   appcast notes are on `main` from `DOCS-007`. `quality iosSwiftTest` in a
   clean clone of R.
2. **Candidates from a clean clone of R:** arm64 and x86-64 release feeds
   with build 33; the x86-64 verification candidate (throwaway key,
   loopback feed); host checks; iOS archive and upload; `store prepare` for
   six locales; the signed APK; both `.deb` files from the workflow; the
   x64 MSI from the `Windows package` workflow on R; the arm64 MSI built in
   a Windows clone from R. Hashes into this record.
3. **Matrix** (reduced): `primary` fresh DMG (setup, manual pause, a
   schedule with the window closed, a break, a website that opens briefly,
   `observe`); `ventura`; the test iPhone with R (core flow, a pause whose
   items change while it runs, an end while Posato is closed, a break); the
   Linux clone with the `.deb` (same row checks plus `/etc/hosts`); the
   Android phone with the APK (same row checks); a Windows clone with the
   arm64 MSI (install, onboarding, a pause that blocks a website and ends an
   app, early end, uninstall, SmartScreen not driven); folder sync chain
   Linux, Android, Windows, Mac; a kept `peer` clone on 1.5.2.
4. Completed-change review, then merge, tag, GitHub Release with eleven
   assets, the in-app update on `peer`, `store submit`, public checks,
   Projects, milestone, closeout after App Review.

## Result

- Pending.

## GitHub release notes for 1.6.0

From the `DOCS-007` draft, with the unsigned-MSI lines (`user-confirmed` 2026-11-04).

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
> - **Windows:** download `Posato-1.6.0-x64.msi`, or `Posato-1.6.0-arm64.msi` for an Arm computer, open it, and approve the installer as an administrator. The packages are not signed yet, so Windows SmartScreen may stop the installer: choose **More info**, then **Run anyway**. Then open Posato from the Start menu and follow the first run.
>
> Update Posato on every device: a device on an earlier version stops syncing once another device takes a break or sets a rule for a website to open briefly. Read the [limits](https://posato.app/limits/) before you rely on Posato, especially for Windows.
>
> ## Requirements and verification
>
> - macOS 13 or later on Apple silicon or Intel; iOS 18 or later from the [App Store](https://apps.apple.com/app/posato/id6812237585); Ubuntu 24.04 or later; Android 13 or later; Windows 11 on x64 or Arm.
> - Both DMGs are signed with Developer ID and notarized by Apple. The APK is release-signed. The MSI packages are not signed yet. `SHA256SUMS` lists the checksums of every file below; `appcast.xml` (Apple silicon) and `appcast-intel.xml` (Intel) are the signed update feeds that Posato for Mac reads.
