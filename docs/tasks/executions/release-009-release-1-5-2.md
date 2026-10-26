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
   host checks of both DMGs, including the absence of the verification
   seam key. Before R, `git diff --name-only v1.5.1 <R parent>` is checked
   again; a 1.6 product change there would move the base to `v1.5.1`.
   Download the 1.5.0 `.deb` files and APK and
   check them against the 1.5.0 `SHA256SUMS`. Steps 3.6 to 3.9 are skipped.
4. **Matrix:**
   - `primary` (macOS 26), fresh arm64 release DMG: setup with the grant
     and automatic starts; a manual pause with `observe`; a schedule that
     starts with the window closed; `MACOS-029` on the release: the helper
     killed during a pause is replaced and the pause applies again by
     itself; Posato ended and opened again during a pause applies by
     itself; with the network off every attempt fails and the notice
     returns; `MACOS-031` on the release: the daemon stall harness against
     the installed release helper (two 8 s `vm stall`s).
   - `peer` (macOS 26), kept for step 7: 1.5.1 from its DMG, set up with
     the helper, **Open Posato at login**, **Start sessions without the
     password**, and automatic starts (`vm onboard`), a website, and a
     schedule that starts on its own.
   - `ventura` (macOS 13): the x86-64 release DMG passes Gatekeeper, says
     "This version is for Intel Macs", and leaves nothing after Quit; the
     verification candidate passes setup and a blocking pause.
   - Not repeated: the update gate's candidates A and B (the gate is
     unchanged since 1.5.1 proved it through Sparkle; the new automatic
     apply reads a closing update as transient, `MaintenanceAdmission`
     refuses the apply at the port, and a recovery stops when its pause
     ends), `legacy` (macOS 15: `MACOS-031` changes timing in code that is
     the same on every macOS version, and `RELEASE-008` skipped it too), the
     `--replace` upgrade (the in-app update on `peer` covers it), the
     iPhone, iCloud, Linux, Android, and folder sync (not rebuilt), and a
     scheduled start on `ventura` (shared code with arm64).
5. **Review:** independent completed-change review.
6. **Merge and tag** after the go, which the maintainer gave with the
   request (`user-confirmed`); the tag rule of step 6.
7. **Release:** a draft with the eight assets, compared byte for byte, then
   latest; both public feeds verify. On `peer`: **Check for Updates**,
   **Install and Relaunch** to 1.5.2; data, the schedule, and the helper
   are kept, the schedule starts on its own on 1.5.2, and `observe` blocks;
   then, in a pause, `vm kill --process helper` and the pause applies again
   by itself, which proves the updated helper, daemon, and session owner.
   **Rollback:** `gh release edit v1.5.1 --latest` and revert the site and
   availability-page change on `main`; a fix build is 33 with
   `-PposatoMacOsPreviousBuildNumber=32`.
8. Before merge, the site and availability-page edits are checked for an
   em dash. Projects Done for `MACOS-031`, `MACOS-029`, and `RELEASE-009`, milestone
   closed; `posato.app` and the availability page name the 1.5.2 Mac files
   after Cloudflare deploys `main`, and their links answer 200.

## Carried forward

The next iOS, Linux, or Android release must verify on that platform the
`SESSION-009` branch of `clearAfterEnd` (it runs on every host) and the
`PauseClaims` and `EnforcementPort.replace` change that `RELEASE-008`
already left to it; the `RELEASE-010` row names them.

## High-risk plan review

- **Verdict:** changes-required, folded into this plan.
- **Required finding:** the note on applying again overstated when it
  happens: the scheduled-start gate needs **Open Posato at login**, **Start
  sessions without the password**, and the automatic-start consent. Both
  notes now name that setup.
- **Recommended, taken:** guidance for a Mac still on 1.5.1 showing the
  notice; a truer reason for skipping the update gate's candidates; both
  fixes checked again on `peer` after the in-app update; the relaunch and
  the failing-attempts runs on the release; the appcast notes drafted
  below; a recheck of `main` before R; the shared-code obligation carried
  forward; the seam-key host check; the `legacy` reason; an em-dash check.

## Appcast notes for 1.5.2

```text
Posato 1.5.2

When the Mac was busy for a few seconds, a pause could lose its restrictions and show "Restrictions need attention". Posato now keeps them through such a moment.

When Posato is set up to start pauses on its own (Open Posato at login and Start sessions without the password on), it applies a running pause again by itself after losing its background helper, after it opens again, and when a pause arrives from your other devices. It asks you only when it cannot.

During a pause, Posato does not install the update; install it after the pause ends.
```

## GitHub release notes for 1.5.2

```markdown
Posato 1.5.2 for Mac keeps a pause working through a busy moment and restores it by itself when it can.

## Fixes

- **Mac:** when the Mac was busy for a few seconds, a pause could lose its restrictions and show "Restrictions need attention" while websites were no longer paused. Posato now keeps them through such a moment.
- **Mac:** when Posato is set up to start pauses on its own (**Open Posato at login** and **Start sessions without the password** on), it applies a running pause again by itself after it lost its background helper, after it was opened again, and when a pause arrives from your other devices. Meanwhile it says "Applying restrictions again…". It asks you only when it cannot do this itself.
- **Mac:** a pause from another device that ended before you resumed it no longer leaves its notice behind.

## Installing

- **Mac:** Posato 1.1 and later offer this update in the app, or choose **Check for Updates…** in the Posato menu. During a pause, Posato does not install the update; install it after the pause ends. If Posato 1.5.1 shows "Restrictions need attention" during a pause, choose **Retry**; to update, end the pause early or wait until it ends. If Posato 1.5.0 says the update can't be installed now, quit Posato and open it again first. From Posato 1.0, download `Posato-1.5.2.dmg` below, quit Posato, and replace the application in Applications. On an Intel Mac, download `Posato-1.5.2-intel.dmg`.
- **Linux and Android** do not change in 1.5.2, so the 1.5.0 packages are attached again. **Linux:** download `posato_1.5.0_arm64.deb` or `posato_1.5.0_amd64.deb` for x86-64, run `sudo apt install ./posato_1.5.0_<arch>.deb`, then open Posato and follow the first run. **Android:** on the phone, download `Posato-1.5.0.apk` and open it. When Android asks, allow installing apps from this source, then install, open Posato, and follow the first run.
- **iPhone:** Posato for iPhone stays at 1.5 on the [App Store](https://apps.apple.com/app/posato/id6812237585).

Read the [limits](https://posato.app/limits/) before you rely on Posato.

## Requirements and verification

- macOS 13 or later on Apple silicon or Intel; iOS 18 or later; Ubuntu 24.04 or later; Android 13 or later.
- Both DMGs are signed with Developer ID and notarized by Apple. The APK is release-signed. `SHA256SUMS` lists the checksums of every file below; `appcast.xml` (Apple silicon) and `appcast-intel.xml` (Intel) are the signed update feeds that Posato for Mac reads.
```

## Result

- **R** = `b81414def225468e2ef9cc1c941ef5dcb094da5e` (`MARKETING_VERSION =
  1.5.2` on `main` `e039672e` plus this row's documentation);
  `git diff --name-only v1.5.1 <R parent>` showed only the 1.5.2 product
  changes and the spike's verification task; clean-clone `quality
  iosSwiftTest` passed (7 min 17 s).
- **macOS build 32**, both architectures from one clean clone of R, G2
  Developer ID (`AE452250…D651`): `Posato-1.5.2.dmg` `a531f2ff…b49`,
  `appcast.xml` `66a6002e…44f`, `Posato-1.5.2-intel.dmg` `4a4c269c…938`,
  `appcast-intel.xml` `0d40e98e…3f`. Host checks of both DMGs: 38 Mach-O
  files of the DMG's architecture each, no `PosatoAllowsRosetta` or
  `PosatoVerificationSeams` key, the stable feed per architecture with one
  `SUPublicEDKey`, minimum macOS 13.0, build 32, Temurin 21.0.12.1 as in
  1.5.1, strict deep `codesign`, `stapler validate`, and `spctl` (Notarized
  Developer ID); app, helper, and sync companion entitlements equal 1.5.1's.
- **Reattached:** the 1.5.0 `.deb` files and APK match the 1.5.0
  `SHA256SUMS`; the new `SHA256SUMS` lists all seven files.
- **Never published:** the Intel verification candidate 9501
  (`060f471e…335`), signed with the throwaway key in Keychain account
  `posato-test-release-009`.
- **Retrospective note:** a harness started with `nohup … &` in one
  `vm exec` lost its helper and daemon when that exec ended (it worked in
  `MACOS-031`); running the harness inside a `vm exec` that lasts the whole
  test, with the stalls from parallel calls, passed. The `MACOS-031` recipe
  in the verify skill needs that change (outside R's allowed paths, so not
  here).

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `peer` (macOS 26), `Posato-1.5.1.dmg`: setup with the helper, grant, login item, `scheduleConsent` true, Focus with example.com, schedule Evening 10:01-10:21 | started on its own, `observe --expect blocked` | `build/verification/runs/20261026-100308-a4f7` |
| `primary` (macOS 26), fresh `Posato-1.5.2.dmg` (`vm install`, `vm onboard`): a manual pause | blocked | `20261026-100815-2a58` |
| Same, `vm kill --process helper` during the pause | a new helper at 10:08:24 applied by itself; **Restrictions active** throughout; blocked | `20261026-100827-4f63` to `20261026-100855-e8a2`, `20261026-100858-4fdb` |
| Same, Posato ended and opened again during the pause | **Restrictions active**, blocked | `20261026-100926-beb7`, `20261026-100929-32fc` |
| Same, network off | "Applying restrictions again…" from 10:09:58 to about 10:10:30, then the notice with **Retry**; **Retry** with the network back applied without a prompt | `20261026-100958-18b8` to `20261026-101041-3df6`, `20261026-101125-9aba` |
| Same, End session early, then a schedule Morning 10:15-10:31 with the window closed | loaded after the end; the schedule started on its own (menu "restrictions active"), blocked | `20261026-101136-93f1`, `20261026-101726-a87b` |
| Same, `MACOS-031` daemon stall harness against the installed release helper, two 8 s stalls at 10:33:47 and 10:34:00 | pass: ten Status `Applied` with the proxy on, change, clear | `build/verification/release-009/stall-harness-release.json` |
| `ventura` (macOS 13.6), `Posato-1.5.2-intel.dmg` | `spctl` Notarized Developer ID; "This version is for Intel Macs"; after Quit no Posato process, launchd job, or Application Support folder | host `vm exec`/`vm text`, 2026-10-26 10:4x |
| `ventura`, Intel verification candidate 9501 | setup, a pause blocked, a killed helper replaced and the pause applied again, after End early loaded | `20261026-104554-f001`, `20261026-104622-d7cc`, `20261026-104633-a587` |
