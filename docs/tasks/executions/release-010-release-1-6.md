# Execution: `RELEASE-010`

- **Brief:** [Verify and publish Posato 1.6.0](../specifications/release-010-release-1-6.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent agents (plan and completed change)
- **Branch:** `release-010-release-1-6`
- **Updated:** 2026-11-04

## Plan

Revision 2, after the plan review. `releasing.md` steps 1 to 9 with the
brief's differences, which hold the details:

1. **Before R:** two tool pull requests through `babysit-pr`:
   `posato-provisioning` prepares first-time store localizations, and
   `posato-control` drives pause-set saves and website windows on Android.
   Disk: the owner approved cleaning about 20 GiB of other projects' build
   outputs (50.5 GiB free after it); the Windows golden's lease is renewed
   to 2026-12-04.
2. **R:** `MARKETING_VERSION = 1.6.0`; Android `versionCode` 2 and
   `versionName` 1.6.0; the SmartScreen and Smart App Control lines for the
   unsigned MSI in six languages; the `PLATFORM-005` row (revision 39); the
   release notes below. `DOCS-007` already merged the packaging and dated
   `PRIVACY.md`, so step 6.1 only rebases. `quality iosSwiftTest` in a clean
   clone of R.
3. **Candidates** from clean clones of R: both macOS feeds (build 33), the
   x86-64 verification candidate, host checks, iOS build 9, `store prepare`
   for six locales with every field, the signed APK, both `.deb` files, the
   x64 MSI from one recorded dispatch, the arm64 MSI with recorded
   provenance; MSI properties and runtimes read. Hashes into this record.
4. **Matrix** in the brief's order, with the row checks and the 1.6
   feature runs per target, the 1.5.0 in-place upgrades on Linux and
   Android, and the `peer` update after publication.
5. Completed-change review, merge, tag, the GitHub Release with ten
   assets, the `peer` update, `store submit`, public checks, Projects,
   milestone; closeout after App Review.

## Plan review

- **Verdict:** `changes-required`, then folded (revision 2). No Critical.
  Required: first-time store fields; ten assets with nine in `SHA256SUMS`;
  arm64 MSI provenance and MSI properties; a `PauseClaims` scenario that
  reaches it (iOS does not use it); the Android driver gaps and named 1.6
  runs per target; disk and VM order; rollback for the MSI and APK.
  Recommended accepted: Smart App Control limit, CRLF in the workflow files,
  bundled runtimes, migration evidence, 1.5.0 in-place upgrades on Linux
  and Android, `SESSION-009` as a no-regression check, the x64 MSI limit.

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
