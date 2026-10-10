# Execution: `RELEASE-006`

- **Brief:** [Verify the 1.4.0 candidates and publish Posato 1.4](../specifications/release-006-release-1-4.md)
- **Status:** `active`: plan written; waiting for the independent plan
  review and for `DOCS-005`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent plan review, pending
- **Branch:** `docs/release-006-release-1-4`
- **Updated:** 2026-10-10

## Plan

`releasing.md` steps 1 to 9, with these differences.

1. **Open (done here).** Draft pull request, milestone `1.4.0`, Projects
   item In Progress. `DOCS-005` runs in parallel in its own pull request; R
   waits for it only for the store files and the notes, not for code.
2. **Release commit R:** `MARKETING_VERSION = 1.4.0` and
   `docs/releases/1.4.0-appcast-notes.txt` (one file for both feeds; it
   names the one-time "Quit Posato?" when updating from 1.2 or 1.3 while a
   schedule is on). `./gradlew quality iosSwiftTest` in a clean clone of R.
3. **Candidates from a clean clone of R**, with no
   `posatoMacOsVerificationSeams` property anywhere (command line,
   `gradle.properties`, environment):
   - arm64: `generateMacOsUpdateFeed`, release channel, build 29; then
     x86-64 with `-PposatoMacOsArchitecture=x86_64` in the same clone.
   - **Seam absence, both architectures:** keep the `verifyMacOsReleasePackaging`
     output and the `generateMacOsUpdateFeed` DMG scan output, which must
     pass with no seam key and no marker; also `grep -c
     posato-verification-seams-v1` on the companion binary inside each
     mounted DMG must print 0.
   - x86-64 verification candidate in a second clean clone (candidate
     channel, `-PposatoMacOsAllowRosetta=true`, loopback feed, throwaway
     key), never published.
   - Host DMG checks as in `releasing.md` step 3.5.
   - iOS: archive, export, inspect, validate, upload as build 7.
   - `store prepare --version 1.4.0 --build 7` with the `DOCS-005` files at a
     pinned head; a rerun reports `unchanged`. Nothing is submitted.
4. **Reduced matrix**, once each:
   - `primary` (macOS 26), fresh arm64 DMG: unified setup reads ready
     (`MACOS-027`); a manual pause chosen with **1 h** and one with **Until
     end of day** shows the expected end (`SESSION-007`), `observe` blocked
     then allowed; a schedule starts with the window closed.
   - `legacy` (macOS 15), fresh arm64 DMG: setup and one manual pause with
     `observe`. The login launch is not repeated (unchanged since 1.3).
   - `ventura` (macOS 13): the x86-64 release DMG passes Gatekeeper and
     shows "This version is for Intel Macs"; the verification candidate
     passes setup and one blocking pause.
   - Test iPhone, R's build: a manual pause with the shield and "Website Not
     Allowed", and the edge swipe on one pushed screen (`DESIGN-004`).
   - iCloud on `primary` with the development build of R: `flow icloud
     link`, `pause-sets-notice-desktop.json`, one press of **Remove
     workspace** (`flow icloud remove`, `--timeout-seconds 1500`), as the
     per-release sync check. The test account keeps its long history
     (`SYNC-022`).
   - Keep a `peer` clone with 1.3.0 set up (helper, websites, an
     application, a schedule, update consent answered) for step 7.
   - **Not repeated:** the replacement install over 1.3.0 and the iPhone
     upgrade over 1.3.0 (no data migration since `v1.3.0`); the legacy
     login launch.
5. **Completed-change review**, including the seam-absence evidence and the
   entitlement comparison with `v1.3.0` (expected: unchanged).
6. **Merge and tag after the go**, with the tag rule of `releasing.md`
   step 6.
7. **GitHub Release and the in-app update** on the kept `peer` clone:
   1.3.0's own "Quit Posato?" is expected during Install and Relaunch with a
   schedule on; click Quit. Then data, schedule, and helper are kept and
   `observe` blocks. Rollback as in `releasing.md`; a fix build is 30 or
   higher.
8. **iOS submission and public checks**; milestone `1.4.0` closed;
   Projects Done.
9. **Closeout** after App Review.

**Maintainer touchpoints, nothing else attended:**

- Keychain prompts during step 3, asked in one batch: the release update key
  for the two feeds, and the Developer ID key if macOS asks.
- The publication go before step 6.

**Decisions (agent, under the maintainer's delegation of 2026-10-09):** the
reduced matrix above; macOS build 29 and iOS build 7; App Store release type
`after-approval`; iOS 26 is the only iOS version claimed, as in 1.3.

## High-risk plan review

- **Verdict:** pending
