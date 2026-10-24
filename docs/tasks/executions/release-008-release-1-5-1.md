# Execution: `RELEASE-008`

- **Brief:** [Verify and publish Posato 1.5.1 for Mac](../specifications/release-008-release-1-5-1.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** pending
- **Branch:** `docs/release-008-release-1-5-1`
- **Updated:** 2026-10-24

## Plan

`releasing.md` steps, with these differences (`agent-delegated` under the
maintainer's request of 2026-10-24 unless marked):

1. **Open:** roadmap revision 30, this brief, draft pull request with
   milestone `1.5.1`, rows In Progress in the Projects mirror.
2. **R:** `MARKETING_VERSION = 1.5.1`, `docs/releases/1.5.1-appcast-notes.txt`,
   and the Mac file names on `website/src/pages/index.astro` and
   `docs/product/limits-and-platforms.md`; the GitHub release notes are
   below, as in the `DOCS-006` record. `./gradlew quality iosSwiftTest`
   in a clean clone of R.
3. **Candidates** from a clean clone of R: arm64 and x86-64 release feeds,
   build 31 with `-PposatoMacOsPreviousBuildNumber=30`; the x86-64
   verification candidate in a second clean clone with a throwaway key; the
   host checks of both DMGs, including the absence of the verification seam
   key. Two arm64 test candidates A and B from a third clean clone of R:
   candidate channel, a loopback `appcast-test.xml` served in the guest with
   `jwebserver`, the throwaway key; never published. Download the 1.5.0 `.deb` files and APK and
   check them against the 1.5.0 `SHA256SUMS`. Steps 3.6 to 3.9 (iOS, store,
   Android, Linux) are skipped.
4. **Matrix:**
   - `primary` (macOS 26), fresh arm64 release DMG: setup, a manual pause
     with `observe`, a schedule that starts with the window closed, and the
     `MACOS-030` harness against the installed release helper (it holds the
     fix in the shipped build).
   - `primary`, the 1.5.1 update gate through Sparkle (`MACOS-030` left it
     to this row): candidate A replaces the release DMG; while a schedule
     holds restrictions, **Install Update** to B refuses with "End the
     current pause first", also after quitting and reopening during it; a
     manual pause started during the schedule blocks and ends; with the
     automatic-start consent off, a running schedule applies nothing and B
     installs, keeping data and the helper.
   - `peer` (macOS 26), kept for step 7: 1.5.0 from its DMG with the helper,
     a website, and a schedule with consent that has started on its own; a
     70-minute pause that ends while 1.5.0 shows "Restrictions need
     attention", left stuck and not reopened, with no schedule running
     before step 7.
   - `ventura` (macOS 13): the x86-64 release DMG passes Gatekeeper, says
     "This version is for Intel Macs", and leaves nothing after Quit; the
     verification candidate passes setup and a blocking pause.
   - Not repeated: `legacy` (macOS 15), the `primary` `--replace` upgrade
     from 1.5.0 (the in-app update on `peer` covers it), the iPhone,
     iCloud, Linux, Android, and folder sync. Those builds are not rebuilt;
     the next iOS, Linux, or Android release must cover the shared
     `PauseClaims` and `EnforcementPort.replace` change.
5. **Review:** independent completed-change review.
6. **Merge and tag** after the go, which the maintainer gave in advance with
   the request (`user-confirmed`); the tag rule of step 6.
7. **Release:** a draft with the eight assets, compared byte for byte, then
   latest; both public feeds verify. On the stuck `peer`, **Install Update**
   first, recording 1.5.0's answer, then quit and reopen Posato as the notes
   say and update; data, the schedule, and the helper are kept, the schedule
   starts on its own on 1.5.1, and `observe` blocks.
   **Rollback:** `gh release edit v1.5.0 --latest` and revert the site and
   availability-page change on `main`; a fix build is 32 with
   `-PposatoMacOsPreviousBuildNumber=31`.
8. Projects Done for `MACOS-030` and `RELEASE-008`, milestone closed;
   `posato.app` and the availability page name the 1.5.1 Mac files after
   Cloudflare deploys `main`, and their links answer 200. No store
   submission; the closeout records the publication.

## High-risk plan review

- **Verdict:** changes-required, folded into this plan.
- **Required findings:** (1) the 1.5.1 update gate never ran through
  Sparkle in a real build; (2) the `peer` update must start from a stuck
  1.5.0; (3) the in-app notes lacked the quit-and-reopen step; (4) "a
  scheduled pause that pauses nothing" overstated the rule; (5) step 8.2's
  public checks were missing.
- **Resolution:** candidates A and B on `primary`; `peer` stays stuck until
  step 7; both notes say quit, reopen, and install; the wording is "could
  not apply its restrictions"; step 8 checks the site and links.

## Result

- Pending.

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |

## GitHub release notes for 1.5.1

```markdown
Posato 1.5.1 for Mac keeps Posato connected to its background helper for as long as it runs.

## Fixes

- **Mac:** after about an hour of a pause, or a few days of schedules, Posato for Mac could lose its connection to its background helper. It then showed "Restrictions need attention", kept websites paused after the pause ended, said in This Mac that the background helper could not be checked or enabled, and refused to install updates. Posato now keeps its helper connected for as long as it runs.
- **Mac:** a scheduled pause that could not apply its restrictions no longer holds an update back, and the refusal during a pause now says "End the current pause first".

## Installing

- **Mac:** Posato 1.1 and later offer this update in the app, or choose **Check for Updates…** in the Posato menu. If Posato 1.5.0 shows "Restrictions need attention" or says the update can't be installed now, quit Posato and open it again first. During a pause, Posato does not install the update; install it after the pause ends. From Posato 1.0, download `Posato-1.5.1.dmg` below, quit Posato, and replace the application in Applications. On an Intel Mac, download `Posato-1.5.1-intel.dmg`.
- **Linux and Android** do not change in 1.5.1, so the 1.5.0 packages are attached again. **Linux:** download `posato_1.5.0_arm64.deb` or `posato_1.5.0_amd64.deb` for x86-64, run `sudo apt install ./posato_1.5.0_<arch>.deb`, then open Posato and follow the first run. **Android:** on the phone, download `Posato-1.5.0.apk` and open it. When Android asks, allow installing apps from this source, then install, open Posato, and follow the first run.
- **iPhone:** Posato for iPhone stays at 1.5 on the [App Store](https://apps.apple.com/app/posato/id6812237585).

Read the [limits](https://posato.app/limits/) before you rely on Posato.

## Requirements and verification

- macOS 13 or later on Apple silicon or Intel; iOS 18 or later; Ubuntu 24.04 or later; Android 13 or later.
- Both DMGs are signed with Developer ID and notarized by Apple. The APK is release-signed. `SHA256SUMS` lists the checksums of every file below; `appcast.xml` (Apple silicon) and `appcast-intel.xml` (Intel) are the signed update feeds that Posato for Mac reads.
```

