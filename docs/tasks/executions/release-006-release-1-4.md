# Execution: `RELEASE-006`

- **Brief:** [Verify the 1.4.0 candidates and publish Posato 1.4](../specifications/release-006-release-1-4.md)
- **Status:** `active`: plan revised after the plan review; waiting for
  its re-review and for `DOCS-005`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent plan review (changes-required, folded here)
- **Branch:** `docs/release-006-release-1-4`
- **Updated:** 2026-10-10

## Plan

`releasing.md` steps 1 to 9, with these differences.

1. **Open (done).** Draft pull request #162, milestone `1.4.0`, Projects
   item In Progress.
   - **`DOCS-005` coupling:** R is cut only after the `DOCS-005` release
     notes and appcast notes text are final. `DOCS-005` keeps its pull
     request within the tag allowlist of `releasing.md` step 6; a doc-only
     change it needs outside it (`DESIGN.md`, `.agents/`) merges to `main`
     before R is cut, so the allowlist is not widened. Before asking for the
     go, `git diff --name-only <R> origin/main` is checked against the
     allowlist.
2. **Release commit R:** `MARKETING_VERSION = 1.4.0` and
   `docs/releases/1.4.0-appcast-notes.txt` (one file for both feeds; it
   names the one-time "Quit Posato?" when updating from 1.2 or 1.3 while a
   schedule is on). `./gradlew quality iosSwiftTest` in a clean clone of R.
3. **Candidates from a clean clone of R**, with no
   `posatoMacOsVerificationSeams` property anywhere (command line,
   `gradle.properties`, environment):
   - arm64: `generateMacOsUpdateFeed`, release channel, build 29; then
     x86-64 with `-PposatoMacOsArchitecture=x86_64` in the same clone.
   - x86-64 verification candidate in a second clean clone (candidate
     channel, `-PposatoMacOsAllowRosetta=true`, loopback feed, throwaway
     key), never published.
   - **Seam absence:** keep the `verifyMacOsReleasePackaging` output and the
     `generateMacOsUpdateFeed` DMG scan output for both architectures, both
     passing. In each mounted DMG and in the verification candidate,
     `grep -a -c posato-verification-seams-v1` on the companion binary
     prints 0, and `plutil -extract PosatoVerificationSeams raw` fails on
     each application `Info.plist`.
   - Host DMG checks as in `releasing.md` step 3.5.
   - iOS: archive, export, inspect, validate, upload as build 7.
   - Store record, nothing submitted:
     `posato-provisioning store prepare --version 1.4.0 --build 7
     --whats-new <DOCS-005 whats-new.txt> --release after-approval
     --description <DOCS-005 description.txt>
     --screenshots docs/store/en-US/screenshots`, plus `--subtitle` only if
     `DOCS-005` changes it, at a pinned `DOCS-005` head; a rerun reports
     every item `unchanged`.
4. **Reduced matrix** (`user-confirmed` 2026-10-10), once each:
   - `primary` (macOS 26), fresh arm64 DMG: unified setup reads ready; a
     manual pause chosen with **1 h** and one with **Until end of day** shows
     the expected end (`SESSION-007`), `observe` blocked then allowed. Then
     `vm shutdown` and `vm boot`: This Mac reads ready, and a schedule
     starts on its own with the window closed while `observe` blocks
     (`MACOS-027` daemon and timeouts).
   - `legacy` (macOS 15), fresh arm64 DMG: setup and one manual pause with
     `observe`.
   - `ventura` (macOS 13): the x86-64 release DMG passes Gatekeeper and
     shows "This version is for Intel Macs"; the verification candidate
     passes setup and one blocking pause.
   - Test iPhone, R's build: a manual pause with the shield and "Website Not
     Allowed", and the edge swipe on one pushed screen (`DESIGN-004`). Build
     7 is installed once from TestFlight and opened, because the Release
     configuration otherwise never runs before submission; if the driver
     cannot install from TestFlight, that is recorded as a blocker with the
     exact command, not done attended.
   - iCloud on `primary` with a plain development package of R (no
     `--verification-seams`, no `vm sync` after `vm onboard`): `flow icloud
     link`, `pause-sets-notice-desktop.json`, `flow icloud remove
     --timeout-seconds 1500`. Pass = one press ends not linked. An extra
     press is recorded against `SYNC-022` and does not hold the release.
   - `peer` clone with 1.3.0 set up (helper, websites, an application,
     update consent answered) and a schedule whose window is off during
     Install and Relaunch and starts a few minutes after it, kept for step 7.
   - **Not repeated:** the replacement install over 1.3.0 and the iPhone
     upgrade over 1.3.0 (no data migration since `v1.3.0`).
5. **Completed-change review**, including the seam-absence evidence and the
   entitlement comparison with `v1.3.0` (expected: unchanged).
6. **Merge and tag after the go**, with the tag rule of `releasing.md`
   step 6.
7. **GitHub Release and the in-app update** on the kept `peer` clone:
   1.3.0's own "Quit Posato?" is expected during Install and Relaunch while
   the schedule is on; click Quit. Then setup reads ready; data and helper
   are kept; the kept schedule starts on its own and `observe` blocks; Check
   for Updates on 1.4.0 against the stable feed answers up to date (the new
   relaunch callback did not break the updater).
   - **Rollback:** `gh release edit v1.3.0 --latest`; v1.3.0 has both feeds,
     so the 1.3 Intel 404 exception no longer applies. A fix build is 30 or
     higher and passes `-PposatoMacOsPreviousBuildNumber=29`.
8. **iOS submission and public checks**; milestone `1.4.0` closed;
   Projects Done.
9. **Closeout** after App Review.

**Maintainer touchpoints, nothing else attended:**

- Signing in step 3 runs back to back while the maintainer is present, one
  Keychain action per message, never Always Allow for the release key: the
  Developer ID key (arm64, x86-64, and the second-clone verification
  candidate), the release update key (two feeds), and the Apple
  Distribution key at iOS export.
- Anything App Store Connect cannot do through the API (an agreement or an
  export-compliance question), if it comes up.
- The publication go before step 6.
- Uploading `.github/assets/social-preview.png` in the repository settings,
  if `DOCS-005` changes it.

**Decisions (agent, under the maintainer's delegation of 2026-10-09):**
macOS build 29 and iOS build 7; the `DOCS-005` coupling above.

**`user-confirmed` (2026-10-10):** the reduced matrix in step 4, App Store
release type `after-approval`, and claiming iOS 26 only.

## High-risk plan review

- **Verdict:** changes-required (2026-10-10), re-review pending
- **Critical or Required findings:** the legacy login launch was wrongly
  called unchanged (`MACOS-027` changed the daemon); the risk acceptances
  need `user-confirmed`.
- **Resolution:** a cold boot on `primary` and the updated `peer`
  installation check ready setup and a schedule that starts on its own; the
  three acceptances are `user-confirmed` (2026-10-10). All Recommended
  and Optional findings are folded above.
