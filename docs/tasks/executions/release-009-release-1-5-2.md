# Execution: `RELEASE-009`

- **Brief:** [Verify and publish Posato 1.5.2 for Mac](../specifications/release-009-release-1-5-2.md)
- **Status:** `in-progress`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent agents (plan review, completed-change review)
- **Branch:** `docs/release-009-release-1-5-2`
- **Updated:** 2026-10-26

## Plan

`releasing.md` steps, with these differences (`agent-delegated` under the
maintainer's request of 2026-10-26 unless marked):

1. **Open:** this brief, a draft pull request with milestone `1.5.2`, the
   row In Progress in the Projects mirror (Release field 1.5).
2. **R:** `MARKETING_VERSION = 1.5.2`, `docs/releases/1.5.2-appcast-notes.txt`,
   and the Mac file names on `website/src/pages/index.astro` and
   `docs/product/limits-and-platforms.md`; the GitHub release notes are
   below. `./gradlew quality iosSwiftTest` in a clean clone of R.
3. **Candidates** from a clean clone of R: arm64 and x86-64 release feeds,
   build 32 with `-PposatoMacOsPreviousBuildNumber=31`; the x86-64
   verification candidate in a second clean clone with a throwaway key; the
   host checks of both DMGs. Download the 1.5.0 `.deb` files and APK and
   check them against the 1.5.0 `SHA256SUMS`. Steps 3.6 to 3.9 are skipped.
4. **Matrix:**
   - `primary` (macOS 26), fresh arm64 release DMG: setup with the grant
     and automatic starts; a manual pause with `observe`; a schedule that
     starts with the window closed; `MACOS-029` on the release: the helper
     killed during a pause is replaced and the pause applies again by
     itself; `MACOS-031` on the release: the daemon stall harness against
     the installed release helper (two 8 s `vm stall`s).
   - `peer` (macOS 26), kept for step 7: 1.5.1 from its DMG with the
     helper, a website, and a schedule with consent that starts on its own.
   - `ventura` (macOS 13): the x86-64 release DMG passes Gatekeeper, says
     "This version is for Intel Macs", and leaves nothing after Quit; the
     verification candidate passes setup and a blocking pause.
   - Not repeated: the update gate's candidates A and B (unchanged since
     1.5.1, which proved it through Sparkle), `legacy` (macOS 15), the
     `--replace` upgrade (the in-app update on `peer` covers it), the
     iPhone, iCloud, Linux, Android, and folder sync (not rebuilt), and a
     scheduled start on `ventura` (shared code with arm64).
5. **Review:** independent completed-change review.
6. **Merge and tag** after the go, which the maintainer gave with the
   request (`user-confirmed`); the tag rule of step 6.
7. **Release:** a draft with the eight assets, compared byte for byte, then
   latest; both public feeds verify. On `peer`: **Check for Updates**,
   **Install and Relaunch** to 1.5.2; data, the schedule, and the helper
   are kept, the schedule starts on its own on 1.5.2, and `observe` blocks.
   **Rollback:** `gh release edit v1.5.1 --latest` and revert the site and
   availability-page change on `main`; a fix build is 33 with
   `-PposatoMacOsPreviousBuildNumber=32`.
8. Projects Done for `MACOS-031`, `MACOS-029`, and `RELEASE-009`, milestone
   closed; `posato.app` and the availability page name the 1.5.2 Mac files
   after Cloudflare deploys `main`, and their links answer 200.

## GitHub release notes for 1.5.2

```markdown
Posato 1.5.2 for Mac keeps a pause working through a busy moment and restores it by itself when it can.

## Fixes

- **Mac:** when the Mac was busy for a few seconds, a pause could lose its restrictions and show "Restrictions need attention" while websites were no longer paused. Posato now keeps them through such a moment.
- **Mac:** with **Start sessions without the password** on, Posato applies a running pause again by itself after it lost its background helper, after it was opened again, and when a pause arrives from your other devices. Meanwhile it says "Applying restrictions again…". It asks you only when it cannot do this itself.
- **Mac:** a pause from another device that ended before you resumed it no longer leaves its notice behind.

## Installing

- **Mac:** Posato 1.1 and later offer this update in the app, or choose **Check for Updates…** in the Posato menu. During a pause, Posato does not install the update; install it after the pause ends. If Posato 1.5.0 says the update can't be installed now, quit Posato and open it again first. From Posato 1.0, download `Posato-1.5.2.dmg` below, quit Posato, and replace the application in Applications. On an Intel Mac, download `Posato-1.5.2-intel.dmg`.
- **Linux and Android** do not change in 1.5.2, so the 1.5.0 packages are attached again. **Linux:** download `posato_1.5.0_arm64.deb` or `posato_1.5.0_amd64.deb` for x86-64, run `sudo apt install ./posato_1.5.0_<arch>.deb`, then open Posato and follow the first run. **Android:** on the phone, download `Posato-1.5.0.apk` and open it. When Android asks, allow installing apps from this source, then install, open Posato, and follow the first run.
- **iPhone:** Posato for iPhone stays at 1.5 on the [App Store](https://apps.apple.com/app/posato/id6812237585).

Read the [limits](https://posato.app/limits/) before you rely on Posato.

## Requirements and verification

- macOS 13 or later on Apple silicon or Intel; iOS 18 or later; Ubuntu 24.04 or later; Android 13 or later.
- Both DMGs are signed with Developer ID and notarized by Apple. The APK is release-signed. `SHA256SUMS` lists the checksums of every file below; `appcast.xml` (Apple silicon) and `appcast-intel.xml` (Intel) are the signed update feeds that Posato for Mac reads.
```
