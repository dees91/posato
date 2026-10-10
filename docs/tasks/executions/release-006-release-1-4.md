# Execution: `RELEASE-006`

- **Brief:** [Verify the 1.4.0 candidates and publish Posato 1.4](../specifications/release-006-release-1-4.md)
- **Status:** `active`: candidates verified; waiting for the
  completed-change review, then the publication sitting
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
     go, `git diff --name-only <R> origin/main`, and the same diff against
     the `DOCS-005` head rebased on `main`, are checked against the
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
   - Disk: copy and hash each output, then delete the second clone's build
     outputs; keep at least 20 GB free and never pass `--allow-low-disk`.
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
4. **Reduced matrix** (`user-confirmed` 2026-10-10), once each. Run
   `doctor` before each `vm create`, answer `update-consent` first on each
   fresh release DMG, and destroy each clone right after its check.
   - `primary` (macOS 26), fresh arm64 DMG: unified setup reads ready; a
     manual pause chosen with **1 h** and one with **Until end of day** shows
     the expected end (`SESSION-007`), `observe` blocked then allowed.
   - `primary` cold boot (`MACOS-027` daemon and timeouts): create a
     schedule that starts a few minutes after the boot, then `vm shutdown`
     and `vm boot`; `observe` blocks when the window starts, before Posato
     is opened by hand; then launch, and This Mac reads ready.
   - `legacy` (macOS 15), fresh arm64 DMG: setup and one manual pause with
     `observe`.
   - `ventura` (macOS 13): the x86-64 release DMG passes Gatekeeper and
     shows "This version is for Intel Macs"; the verification candidate
     passes setup and one blocking pause.
   - Test iPhone, R's build: a manual pause with the shield and "Website Not
     Allowed", and the edge swipe on one pushed screen (`DESIGN-004`).
   - Build 7 from TestFlight: best-effort, not run unattended, and does not
     hold the release (`posato-control` cannot install from TestFlight and
     is not extended for this release). It runs only if the maintainer
     installs build 7 themselves, after the development-build checks;
     "opened" means the app launches past onboarding and Session renders.
   - iCloud on `primary` with a plain development package of R (no
     `--verification-seams`, no `vm sync` after `vm onboard`): `flow icloud
     link`, `pause-sets-notice-desktop.json`, `flow icloud remove
     --timeout-seconds 1500`. Pass = one press ends not linked. An extra
     press is recorded against `SYNC-022` and does not hold the release.
     A WAIT_TIMEOUT is investigated before `vm destroy`; it holds the
     release only if the row never reaches not linked after one manual
     retry, and otherwise is recorded against `SYNC-022`.
   - `peer` clone with 1.3.0 set up (helper, websites, an application,
     update consent answered), kept for step 7.
   - **Not repeated** from `releasing.md` step 4, covered by the confirmed
     reduced matrix: the replacement install and the iPhone upgrade over
     1.3.0 (no data migration since `v1.3.0`); the legacy login launch
     (replaced by the `primary` cold boot); the schedule ending on its own
     on `primary`; `ventura` leaving nothing after Quit and the verification
     candidate's scheduled start; `pause-sets-desktop.json`.
5. **Completed-change review**, including the seam-absence evidence and the
   entitlement comparison with `v1.3.0` (expected: unchanged).
6. **Merge and tag after the go**, with the tag rule of `releasing.md`
   step 6.
7. **GitHub Release and the in-app update** on the kept `peer` clone. Right
   before Check for Updates, a schedule on 1.3.0 is enabled with its window
   not active during Install and Relaunch, starting about 10 minutes later
   (`flow schedule`, or the guest clock through `tart exec`). 1.3.0's own
   "Quit Posato?" is expected during Install and Relaunch while
   the schedule is on; click Quit. Then setup reads ready; data and helper
   are kept; the kept schedule starts on its own and `observe` blocks; Check
   for Updates on 1.4.0 against the stable feed answers up to date (the new
   relaunch callback did not break the updater).
   - **Rollback:** as in `releasing.md` (`gh release edit v1.3.0 --latest`,
     hold iOS), but revert only the public files `DOCS-005` changed
     (README, `website/`, the limits page) instead of its whole commit,
     plus: v1.3.0 has both feeds, so
     the 1.3 Intel 404 exception no longer applies, and a fix build is 30 or
     higher with `-PposatoMacOsPreviousBuildNumber=29`.
8. **iOS submission and public checks**; milestone `1.4.0` closed;
   Projects Done.
9. **Closeout** after App Review.

**Maintainer touchpoints, nothing else attended:**

- `superseded`: signing in step 3 was to run back to back with the
  maintainer present and never Always Allow for the release key. The
  maintainer's Keychain had access pre-granted, which they confirmed as the
  practice (`user-confirmed` 2026-10-10, below), so no prompt appeared.
- Anything App Store Connect cannot do through the API (an agreement or an
  export-compliance question), if it comes up.
- The publication go before step 6.
- Uploading `.github/assets/social-preview.png` in the repository settings,
  if `DOCS-005` changes it.

**Decisions (agent, under the maintainer's delegation of 2026-10-09):**
macOS build 29 and iOS build 7; the `DOCS-005` coupling above.

**`user-confirmed` (2026-10-10):** the reduced matrix in step 4, App Store
release type `after-approval`, and claiming iOS 26 only. Also: Keychain
access to the signing keys, the release update key included, is pre-granted
with Always Allow (chosen at 1.3.0, accepted again on 2026-10-10), so no
prompt appeared and signing ran unattended; `releasing.md` now says so. The
publication go is given in advance, to apply once the completed-change
review passes. Opening build 7 from TestFlight is left to the maintainer,
outside the release gate.

## High-risk plan review

- **Verdict:** approved after fold (2026-10-10); the first round was
  changes-required, and the re-review of `fe2e98c3` added one Required
  finding
- **Critical or Required findings:** the legacy login launch was wrongly
  called unchanged (`MACOS-027` changed the daemon); the risk acceptances
  need `user-confirmed`.
- **Resolution:** a cold boot on `primary` and the updated `peer`
  installation check ready setup and a schedule that starts on its own; the
  three acceptances are `user-confirmed` (2026-10-10). The re-review's
  TestFlight finding made that step best-effort. All Recommended and
  Optional findings of both rounds are folded above.

## Result

- **R** = `16a00f3d843930f4e61581b1ee627e74dd0b0a39`: `MARKETING_VERSION =
  1.4.0` on `main` `d0662fa3` (with `DOCS-005` #163 merged, so the appcast
  notes and store files are final) plus this row's brief and record. Clean
  clone of R: `./gradlew quality iosSwiftTest` passed (7 min 56 s).
- **Deviation:** `DOCS-005` (#163) merged before R rather than at
  publication, so the public README, site, and limits page claimed 1.4 from
  that merge on; publication follows promptly, and the rollback above is
  scoped to those files.
- **Reruns:** the first arm64 feed run stopped at configuration because the
  notes path was relative to `desktopApp/` ("The release notes file
  1.4.0-appcast-notes.txt does not exist"); it was rerun with an absolute
  path. Nothing was signed by the failed run.
- **Host incident** (`observed`, once): macOS 27.0.1 on the build Mac had no Rosetta, so the
  first x86-64 build stopped at `:desktopApp:checkRuntime` (bad CPU type for
  the x86-64 Temurin `java`) before any signing. The maintainer installed
  Rosetta 2 (`softwareupdate --install-rosetta`); `releasing.md` now checks
  for it before step 3.
- **macOS 1.4.0 (29)**, release channel, Developer ID G2, floor 28 from the
  published feeds:
  - `Posato-1.4.0.dmg` (arm64)
    `4983d3ef6fa5daa181058b40158bc0f0664c1e32275f615671e0c84f479a1534`;
    `appcast.xml`
    `a7185b40fdae17cc35c48adef295c2105560e96a1911eea11c6cd8061fe8fa67`;
  - `Posato-1.4.0-intel.dmg` (x86-64)
    `877a0806e29a6566dd8bf80fd1acea2b2119da911ae08449ea84b3997f0309c3`;
    `appcast-intel.xml`
    `fde0ca466e4ba5528e1dcdf04898e8ea35abc8193ad86d7984848ff887e9389a`;
  - merged `SHA256SUMS`
    `ebfda48aef06ba7f7ebd9c651231fe4e9e83ccbaf478a52c944c78dccf09f09d`,
    `shasum -a 256 -c` OK; each feed has one item, `sparkle:version` 29,
    and an enclosure length equal to its DMG.
  - Consumed macOS build number: 29 (both architectures).
- **Host DMG checks, both architectures:** every Mach-O has the DMG's
  architecture; no Rosetta switch; `SUFeedURL` ends in `appcast.xml` or
  `appcast-intel.xml` with the release key; minimum macOS 13.0; Temurin
  21.0.12.1 in both; strict deep `codesign`, `spctl` (Notarized Developer
  ID), and `stapler validate` pass. The application, helper, and companion
  entitlements equal 1.3.0's in both DMGs.
- **No verification seams:** `verifyMacOsReleasePackaging` and the
  `generateMacOsUpdateFeed` DMG scan ran and passed for both; `grep -a -c
  posato-verification-seams-v1` prints 0 for the companion and for every
  executable in both release DMGs and in the verification candidate;
  `plutil -extract PosatoVerificationSeams` fails on every bundle's
  `Info.plist`.
- **x86-64 verification candidate** (second clean clone, candidate channel,
  Rosetta switch, loopback feed, throwaway key never stored, build 9201):
  notarized,
  `0ac292b3c66571db27100041dafe1ba5682694e49d9940f9bf28d752697e5bb0`; never
  published; that clone was deleted after hashing.
- **iOS 1.4.0 (7):** archived, exported (Apple Distribution, `get-task-allow`
  false, Family Controls, the app group, CloudKit `Production`, the sync
  keychain group, both privacy manifests), validated, uploaded, `VALID`.
  IPA `81c0287e6142e2f265ac35aea7f769c973c77ecc42c26eddefa43bfe9aa7f02c`.
  Consumed iOS build number: 7.
- **Store record:** `store prepare` created 1.4.0, attached build 7, set
  `after-approval`, What's New, and the description, and replaced both
  screenshot sets from R's `DOCS-005` files; a rerun reported every item
  `unchanged`; `store status`: `PREPARE_FOR_SUBMISSION`. Not submitted.

### Verification on the candidates (2026-10-10)

Run directories are under this worktree's ignored `build/verification/runs/`;
the command transcript is `build/verification/release-006/transcript.log`.
The host was loaded by an unrelated training job throughout.

| Check | Target | Result |
| --- | --- | --- |
| Fresh arm64 DMG: setup, update consent, This Mac ready | `primary`, macOS 26 | pass (`090652-4260`, `090741-09e0`) |
| Pause with **1 h**: "Until 10:10 AM, 59 min left", `observe` blocked, End early, allowed | `primary` | pass (`091006-1c40`, `091012-5776`, `091022-6111`, `091029-0e5d`) |
| Pause **Until end of day**: "Until 10/11/26, 12:00 AM", blocked, End early, allowed | `primary` | pass (`091031-8261`, `091040-d5fb`, `091054-d94b`, `091101-9a61`) |
| Cold boot: schedule 09:19 set before `vm shutdown`/`vm boot`; Posato started at login; `observe` blocked before any manual open; then This Mac "Background helper enabled" | `primary` | pass (`091106-b3de`, `092019-1bcc`, `092040-12ab`) |
| Fresh arm64 DMG: setup and one blocking pause | `legacy`, macOS 15 | pass (`091357-9c00`, `091433-4872`, `091635-b6c0`) |
| x86-64 release DMG: Gatekeeper accepted, "This version is for Intel Macs" | `ventura`, macOS 13 | pass (`092252-4717`, `092349-0f01`) |
| x86-64 verification candidate: setup and one blocking pause | `ventura` | pass (`092613-a0bd`, `092831-0da8`) |
| Plain development package of R: link (13 presses, sync completed), pause-set notice, **one press** of Remove workspace to not linked (3 min 21 s) | `primary` | pass (`092726-5652`, `093621-85c7`, `093629-0383`) |
| R's development build: a pause shows "Website Not Allowed" in Safari; End early; edge swipe back from a set to the list | test iPhone | pass (`094208-d6d5`, `094221-bc0b`, `094231-7b48`) |
| 1.3.0 installed, set up, update consent allowed, a set with two websites; kept running for step 7 | `peer` | ready (`093047-03a3`, `093133-d2a2`, `093307-58f2`) |

Deviation: the `peer` 1.3.0 installation holds no application, because the
1.3.0 picker could not be driven (`PROCESS_NOT_ALLOWED` for the helper
process); data retention after the update is checked on the sets, websites,
schedule, and helper.

### TB-08 and T-13 review of the release artifacts

- R's product sources equal `main` `d0662fa3`; R differs only in
  `Version.xcconfig` and this row's documentation.
- New since `v1.3.0`: the Sparkle relaunch callback that lets an update
  replace Posato without the quit question (`MACOS-026`); the daemon's
  connection counting and idle exit, the readiness retry, and the 30 s and
  45 s signing-check limits (`MACOS-027`); the macOS removal budget and the
  verification seams, compiled out of every release build (`SYNC-021`); the
  interface changes of `DESIGN-004` and `SESSION-007`. No entitlement,
  stored-data schema, or privacy-manifest change: the application, helper,
  and companion entitlements equal 1.3.0's in both DMGs.
- Both DMGs: Notarized Developer ID, stapled, strict deep verification,
  minimum macOS 13.0, no Rosetta switch, single-architecture Mach-O files,
  Temurin 21.0.12.1, `SUFeedURL` per architecture with one `SUPublicEDKey`,
  no seam key or marker.
- Each feed holds one item: build 29, 1.4.0, minimum 13.0, enclosure under
  `releases/download/v1.4.0/` with the DMG's length; the build task verified
  both signatures against the embedded key.
- The IPA: Apple Distribution on the app and the extension, Family Controls,
  the app group, CloudKit Production, `get-task-allow` false, both privacy
  manifests.
- The release key stayed in the maintainer's Keychain; access to it is
  pre-granted, an accepted residual risk recorded under `T-13`.

