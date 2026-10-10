# Execution: `RELEASE-006`

- **Brief:** [Verify the 1.4.0 candidates and publish Posato 1.4](../specifications/release-006-release-1-4.md)
- **Status:** `active`: macOS 1.4.0 published 2026-10-10; iOS 1.4.0 waiting
  for App Review. Pending (`releasing.md` step 9): record the App Review
  outcome, set `done`, and delete the never-published verification candidates
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent plan review and completed-change review
- **Branch:** `docs/release-006-release-1-4` (#162), closeout `docs/release-006-closeout`
- **Updated:** 2026-10-10

## Plan

`releasing.md` steps 1 to 9 with these differences: macOS build 29 and iOS
build 7; seam-absence checks on both architectures and the Intel
verification candidate; a reduced matrix (`primary` 1 h and Until end of day
pauses and a cold boot with a schedule, `legacy` and `ventura` once each,
iCloud link and one-press removal, the test iPhone shield and edge swipe);
the in-app update 1.3.0 to 1.4.0 on `peer` with a schedule that starts on
its own afterwards. Not repeated: the replacement and iPhone upgrades over
1.3.0 (no data migration), the legacy login launch, the schedule end on
`primary`, `ventura`'s cleanup after Quit and the candidate's scheduled
start, `pause-sets-desktop.json`.

**`user-confirmed` (2026-10-10):** the reduced matrix, `after-approval`,
claiming iOS 26 only; Keychain access to the signing keys, the release update
key included, pre-granted with Always Allow, so signing ran unattended (an
accepted `T-13` residual risk, now in `releasing.md`); the publication go in
advance; "we go ahead either way" after the first update attempt; opening
build 7 from TestFlight left to the maintainer, outside the release gate.

## High-risk plan review

Approved after two folds: the cold boot and the updated installation check
ready setup and a schedule that starts on its own (`MACOS-027` changed the
daemon); the risk acceptances became `user-confirmed`; the TestFlight launch
became best-effort.

## Completed-change review

Approved for publication with no Critical or Required findings; it
independently checked the hashes, the four EdDSA signatures, the Mach-O
architectures, seam absence, signatures, entitlements, the IPA, the store
record, and the run evidence. Its Recommended findings are folded: the early
`DOCS-005` merge, the superseded Keychain touchpoint, the `T-13` consequence,
this TB-08/T-13 section, the rerun notes, and the `observed` label below.

## Result

- **R** = `16a00f3d843930f4e61581b1ee627e74dd0b0a39` (`MARKETING_VERSION =
  1.4.0` on `d0662fa3`); clean-clone `quality iosSwiftTest` passed. Merge
  `71569302716eb97377991b95551f437248c9b0b8`; the allowlist check passed;
  tag `v1.4.0` (annotated) on it.
- **GitHub Release** v1.4.0, latest, `DOCS-005` notes, five assets
  byte-identical to the verified outputs:
  `Posato-1.4.0.dmg` `4983d3ef…1534`, `Posato-1.4.0-intel.dmg`
  `877a0806…09c3`, `appcast.xml` `a7185b40…fa67`, `appcast-intel.xml`
  `fde0ca46…389a`, `SHA256SUMS` `ebfda48a…f09d`. Both
  `releases/latest/download` feeds serve build 29 and both archive
  signatures verify against the embedded key.
- **iOS 1.4.0 (7)**, IPA `81c0287e…f02c`: `VALID`, prepared from R's
  `DOCS-005` files, submitted: `WAITING_FOR_REVIEW`, `AFTER_APPROVAL`.
- **Consumed build numbers:** macOS 29 (both), iOS 7. The never-published
  Intel verification candidate (9201, `0ac292b3…5bb0`) and the outputs stay
  outside the repository until App Review ends.
- **Deviations:** `DOCS-005` (#163) merged before R, so the public README,
  site, and limits page claimed 1.4 from then; the rollback was scoped to
  those files. The `peer` application check (`O-1`) ran on 1.4.0 after the
  update, because the 1.3.0 picker could not be driven.
- **Incidents:** macOS 27.0.1 on the build Mac had no Rosetta (`observed`,
  once); the first x86-64 build stopped at `checkRuntime` before signing and
  the maintainer installed it; `releasing.md` now checks for it. The first
  arm64 feed run refused a relative notes path at configuration and was
  rerun.
- **First in-app update attempt:** after 1.3.0 to 1.4.0, a schedule did not
  start ("Setup required on this Mac") because that clone's automatic-start
  consent read 0; one tap on **Allow schedules to start on this Mac** started
  it at once. Latest went back to v1.3.0 for about ten minutes. Why that clone's
  consent read 0 is `open`; the consent code is unchanged since v1.3.0.
- **Rerun (decisive):** a fresh 1.3.0 with consent 1, verified at every step;
  a schedule started on its own on 1.3.0; a second schedule was set, 1.3.0
  updated through the real stable feed (its own "Quit Posato?" appeared and
  Quit continued), 1.4.0 kept the consent, and the second schedule started
  on its own: `observe` blocked both websites and Calculator, added on 1.4.0.
  No upgrade regression.

### Verification on the candidates and the release (2026-10-10)

Run directories are under the release worktree's ignored
`build/verification/runs/`; the transcript is
`build/verification/release-006/transcript.log`. The host carried an
unrelated training job throughout.

| Check | Target | Result |
| --- | --- | --- |
| Setup, 1 h and Until end of day pauses with `observe`, cold boot with a scheduled start before any manual open, This Mac ready | `primary`, arm64 DMG | pass |
| Setup and one blocking pause | `legacy`, arm64 DMG | pass |
| Intel DMG refuses under Rosetta; candidate sets up and blocks | `ventura` | pass |
| iCloud link, pause-set notice, one press of Remove workspace (3 min 21 s) | `primary`, R's development package | pass |
| Website Not Allowed, End early, edge swipe back | test iPhone, R's development build | pass |
| 1.3.0 to 1.4.0 in the app, schedule starts on its own, apps blocked, Check for Updates up to date | `peer`, real stable feed | pass on the rerun (`104439-08b3`) |
| README, site, limits and privacy pages, download links | public | 200 |

### TB-08 and T-13 review of the release artifacts

- R's product sources equal `d0662fa3`; R differs only in `Version.xcconfig`
  and this row's documentation.
- New since `v1.3.0`: the Sparkle relaunch callback (`MACOS-026`); the
  daemon's connection counting and idle exit, the readiness retry, and the
  signing-check limits (`MACOS-027`); the removal budget and the
  verification seams, compiled out of every release build (`SYNC-021`); the
  interface changes of `DESIGN-004` and `SESSION-007`. No entitlement,
  schema, or privacy-manifest change; the app, helper, and companion
  entitlements equal 1.3.0's in both DMGs.
- Both DMGs: notarized, stapled, strict deep verification, minimum macOS
  13.0, single-architecture Mach-O files, no Rosetta switch, Temurin
  21.0.12.1, one `SUPublicEDKey`, no seam key or marker.
- The IPA: Apple Distribution, Family Controls, the app group, CloudKit
  Production, `get-task-allow` false, both privacy manifests.
- The release key stayed in the maintainer's Keychain, with access
  pre-granted (accepted `T-13` residual risk).
