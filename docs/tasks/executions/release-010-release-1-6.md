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

- **Verdict:** `approved` on revision 2 (`6ddad06a`), after a first pass of
  `changes-required`. No Critical. Recommended folded after approval: the
  Linux and macOS clones never run together in the folder chain.
  First-pass Required: first-time store fields; ten assets with nine in `SHA256SUMS`;
  arm64 MSI provenance and MSI properties; a `PauseClaims` scenario that
  reaches it (iOS does not use it); the Android driver gaps and named 1.6
  runs per target; disk and VM order; rollback for the MSI and APK.
  Recommended accepted: Smart App Control limit, CRLF in the workflow files,
  bundled runtimes, migration evidence, 1.5.0 in-place upgrades on Linux
  and Android, `SESSION-009` as a no-regression check, the x64 MSI limit.

## Result

- **R** = `6ddad06aab0087510f72ee7d340046b89826a2cf`; `./gradlew quality
  iosSwiftTest` passed in a clean clone of R on 2026-11-04 (11 min; Swift
  suites 28 and 246 tests, 0 failures). Later commits change only `docs/`.
- **macOS, build 33** (published feeds held 32), G2 Developer ID
  `AE452250…D651`, from a clean clone of R:
  - `Posato-1.6.0.dmg` `3681dc66fe4b220654218a89359fff4824c51116ac6aae79176ce0c12505094b`,
    `appcast.xml` `fd48a0010c1334fd3d41241ec8ee96ea62b63e340968998d304b7c906a4c29e2`;
  - `Posato-1.6.0-intel.dmg` `b9c85fe1164e4a3803af517805d7eb08e4f930a853ef7689fa519039163f1557`,
    `appcast-intel.xml` `200c6ce60f67af3260f86306170998505e43d589dbe8575dfc9754cbfdf9bc78`;
  - host checks of both: every Mach-O of the DMG's architecture, the stable
    feed per architecture with one `SUPublicEDKey`, minimum macOS 13.0,
    build 33, no Rosetta switch, Temurin 21.0.12.1 in both, app
    entitlements `automation.apple-events` and `cs.allow-jit`, strict deep
    `codesign`, `stapler validate`, `spctl` (Notarized Developer ID).
- **Intel verification candidate 9601** (never published), throwaway key in
  Keychain account `posato-test-release-010`, loopback feed:
  `Posato-1.6.0-9601-intel-test.dmg` `e3b5537fb9dd7957eb1b62f01dcc0cabe65b9d618d7bd0c1e0ffc1aefcfb3d28`.
- **iOS 1.6.0 (9):** archived and exported from a clean clone of R; both
  bundles signed by Apple Distribution with Family Controls and the app
  group, the app with the Production CloudKit container and the keychain
  group, `get-task-allow` false, privacy manifests in both; validated,
  uploaded, `VALID`. IPA `065825235358184c97a579f252873e32dce4ebe9c8bd67b5b82700a1c9887f17`.
- **Android:** `Posato-1.6.0.apk` `da48c2fdd78481b3d02f3d4d123eed838297e6c3fdcfbad5de027ff0c4170afa`,
  `versionCode` 2, `versionName` 1.6.0, v2 and v3 signatures, certificate
  SHA-256 `9d9c67…d5b1` equal to 1.5.0's; no password in the clone or
  Gradle caches.
- **Linux:** `Linux packages` run 41855088627 on R; both `.commit` files
  equal R and both `.sha256` check: `posato_1.6.0_arm64.deb`
  `aa5c7a2a5ef1ea0c2cc00759bdee8250a53277fa21386d51e2251c46759b354a`,
  `posato_1.6.0_amd64.deb`
  `cf8b0a109e667d5f2e87bebbe15f0188d07d485a98fc816f72c6337814237db9`.
- **Windows:** x64 from `Windows package` run 41855097423 (dispatch on R,
  `.commit` equal to R after stripping carriage returns, `.sha256` checks):
  `Posato-1.6.0-x64.msi`
  `cd1d5aea6e651dda02c6632c16c9dba4064421330d04e923972323e33226755d`. arm64 built by `posato-control build -t windows`
  with `POSATO_REPO_ROOT` on a clean clone of R (HEAD R, empty
  `git status`): input `posato.jar` `595c416f…cca03`, `version.txt` 1.6.0,
  `Posato-1.6.0-arm64.msi` `2ddc34b3f2c1ee0f77689c84802f9982eb2476a52e1d7ecab3caa29acbbc0721`.
  Both MSIs read in the guest (run `20261104-165559-3904`):
  `ProductVersion` 1.6.0, `UpgradeCode` `{5BBD64CA-6B87-429A-90A8-0F4FA147E299}`,
  both unsigned.
- **Store:** `store prepare` (with `#225`) created 1.6.0 with build 9,
  `after-approval`, and wrote en-US plus the first `pl`, `de-DE`, `fr-FR`,
  `es-ES`, and `pt-BR` listings: name, subtitle, description, What's New,
  keywords, support, marketing, and privacy URLs, and screenshots. Two
  locales were rerun after a network timeout and a slow screenshot
  delivery; `store status --version 1.6.0` with all six locales reports
  `localizationsComplete: true` and `PREPARE_FOR_SUBMISSION`.

### Verification matrix (2026-11-04 and 05)

Runs under `build/verification/runs/` of the clean clone of R. Every line ran
on the release builds, except `ventura`'s candidate 9601 and the iPhone's
build of R.

| Target | Checks | Result |
| --- | --- | --- |
| `primary`, arm64 DMG | Gatekeeper, onboarding, update consent; manual pause (site and Safari); schedule start and end with the window closed; break; website window at both boundaries | pass |
| `ventura`, Intel DMG and 9601 | Gatekeeper, Intel notice, nothing left after Quit; setup, pause, scheduled start | pass |
| iPhone, R | core flow; break; a pause that ends while Posato is terminated shows as ended | pass; website window is a stated limit (`TARGETS-013` device evidence) |
| Linux, arm64 `.deb` | onboarding; four lines per site, app ended, exact restore; `PauseClaims`; website window with Posato terminated; break; upgrade from 1.5.0 keeping a set, a site, and a schedule | pass |
| Android, signed APK | upgrade from 1.5.0 keeping sets and a schedule; onboarding; pause; `PauseClaims`; break; website window (DNS samples); received pause ending while terminated | pass |
| Windows, arm64 MSI | install, onboarding, pause with early end, uninstall; website window; break | pass |
| Folder sync | Linux and Windows, Android and Windows, Android and `primary` | starts and ends cross; **defect 1** below |
| `peer`, 1.5.2 | setup, schedule starts by itself | pass; the update runs after publication |

The x64 MSI and the x86-64 `.deb` have only their workflow smoke tests.
Windows' bundled runtime reads `JAVA_VERSION="21.0.13.0.2"`; the DMGs carry
Temurin 21.0.12.1.

**Defects found:**

1. A pause received through the folder sometimes shows "Restrictions need
   attention" and is not enforced until Retry: Windows 3 of 13, Mac 1 of 2
   (first pause after joining). Retry applies it at once.
2. On Windows, End early on a schedule received from another device left the
   local manual pause running, although the dialog says it ends any session
   running now; with both pauses local, the same dialog ends both.

Both came with 1.5 (code at `v1.3.0` to R is the same) and affect only the
desktop builds. The likely cause, from code reading (not shown in a log):
SQLite refuses a write at once while another connection commits. The session
intent and expiry reads also write, and `readDesiredLocked` turns that
retryable halt into `APPLY_FAILED`; the manual and scheduled early ends write
in parallel, and `END_FAILED` is replaced on the next status. Android
serializes its database work.

Decided 2026-11-05 (`user-confirmed`): publish 1.6.0 with both as known
issues in the GitHub release notes; the `SESSION-012` backlog row (roadmap
revision 40) fixes them.

Driver gaps met: host text recognition needs a HiDPI `ventura` display
(`--display 960x600pt`); `vm prompt toggle` misses the switch on that
display; Windows `flow schedule` picks the first set with a duplicate name;
the Android `flow set` Save needs a tap.

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
> ## Known issues
>
> - On a Mac, Linux, or Windows computer, a pause that arrives from another device can now and then show "Restrictions need attention" without taking effect. Choose **Retry** on Session to apply it.
> - On those computers, **End early** during a scheduled pause can now and then leave a manual pause running. End it again on Session.
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
