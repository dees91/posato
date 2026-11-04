# `RELEASE-010`: Verify and publish Posato 1.6.0

- **Review tier:** `high-risk`
- **Tier reason:** Signed artifacts, two stable update feeds, an App Store
  submission, and the first Windows packages cannot be taken back once
  installations read them.
- **Dependencies:** `ONBOARDING-005`, `SCHEDULE-007`, `TARGETS-013`,
  `PLATFORM-002`, `DOCS-007` (#223, `f5acb7b1`), `I18N-001`, and `I18N-002`
  are merged. Release 1.6, wave R1.6/W4.
- **Integration group:** `PR-RELEASE-1-6`, milestone `1.6.0`.
- **Authority:** [release roadmap](../release-roadmap.md), row
  `RELEASE-010`; [`releasing.md`](../../development/releasing.md); the
  [`RELEASE-007` record](../executions/release-007-release-1-5.md) (the last
  release with Linux, Android, and iOS); the
  [`PLATFORM-002` record](../executions/platform-002-windows.md) (Windows
  packaging); [ADR 0008](../../decisions/0008-macos-update-delivery.md).
- **Record:** [execution record](../executions/release-010-release-1-6.md)

## Outcome

Follow `releasing.md`. Differences for 1.6.0:

- **Versions:** `MARKETING_VERSION = 1.6.0`; macOS build 33 for both
  architectures (both published feeds hold 32); Android `versionCode` 2 and
  `versionName` 1.6.0 (1.5.0 published `versionCode` 1); the iOS build is
  `store status`'s `nextBuildNumber`.
- **Windows, new:** the arm64 MSI is built in a Tart Windows guest from R
  (`posato-control build -t windows`), the x64 MSI by the `Windows package`
  workflow dispatched with R's full SHA, which also smoke-tests it. Both are
  **unsigned** (`user-confirmed` 2026-11-04): the install steps, the limits,
  and the release notes say that Windows SmartScreen asks before the
  installer runs, in every shipped language, and a backlog row takes up
  Authenticode signing.
- **Release assets:** ten: the eight of `releasing.md` step 7 plus
  `Posato-1.6.0-arm64.msi` and `Posato-1.6.0-x64.msi`; one `SHA256SUMS`
  covers the other nine.
- **MSI provenance:** the arm64 MSI is built with `POSATO_REPO_ROOT` set to a
  clean clone of R (its HEAD and an empty `git status` recorded, the input
  jar and `version.txt` hashed); the x64 MSI comes from one recorded
  `workflow_dispatch` run on R, its `.commit` and `.sha256` files read with
  carriage returns stripped. Both MSIs are read in the guest: `ProductVersion`
  1.6.0 and `UpgradeCode` `5BBD64CA-6B87-429A-90A8-0F4FA147E299`, so later
  versions upgrade them. The bundled Java runtime of each is recorded.
- **Store:** `store prepare` writes the 1.6 listing for `en-US` and, for the
  first time, `pl`, `de-DE`, `fr-FR`, `es-ES`, and `pt-BR`: name, subtitle,
  description, What's New, keywords, support and privacy policy URLs, and
  screenshots. The tool cannot yet set every field for a new locale; a
  separate `posato-provisioning` pull request through `babysit-pr` adds that
  before the candidates (`user-confirmed` 2026-11-04). Step 3 checks every
  required field in all six. Release type `after-approval`
  (`user-confirmed`).
- **Reduced matrix** (`user-confirmed` 2026-11-04, as in 1.4 and 1.5): no
  replacement of the 1.5.2 DMG, no iPhone upgrade over the 1.5.0 build, and
  no iCloud run. Kept: `primary` with the fresh arm64 DMG, `ventura` with
  the Intel DMG and its verification candidate, the test iPhone with R, the
  Linux clone with the arm64 `.deb`, the test Android phone with the signed
  APK, a Windows clone with the arm64 MSI, folder sync between release
  builds including Windows, and the in-app update from 1.5.2 on a kept
  `peer` clone.
- **Row checks:** the shared session changes those builds have not shipped
  since 1.5.0. `PauseClaims` and `EnforcementPort.replace` (`MACOS-030`) run
  only on Android and the desktops: on Linux and Android, a manual pause
  runs, a schedule starts inside it, the manual pause ends early, and the
  schedule's website stays blocked until the schedule ends, then everything
  is released. The `SESSION-009` branch of `clearAfterEnd` (`MACOS-029`) has
  no visible effect outside the Mac, so on iOS, Linux, and Android a pause
  received through the folder workspace ends while Posato is terminated and
  shows as ended, with no notice or loop, as a no-regression check; the
  known `SESSION-011` expired-session residue on Linux and Windows is not a
  failure. iOS does not use `PauseClaims`.
- **1.6 features**, one representative run per target under the
  proportionate rule: a break in a scheduled pause on the Mac, the iPhone,
  Linux, Android, and Windows; a website that opens briefly on the Mac,
  Linux, Android, and Windows. On the iPhone the website window is driven
  with plain taps, or recorded as a stated limit citing the `TARGETS-013`
  device evidence. Android driver gaps are fixed first in a separate
  `posato-control` pull request.
- **Migrations:** 15 and 16 rebuild the sync intent queues; without upgrade
  runs, the evidence is `SqlScheduleMigrationTest` and
  `SqlPauseSetWindowMigrationTest` on the JVM and the iOS Simulator in
  `quality iosSwiftTest`, the `peer` update, and a cheap in-place upgrade
  from the 1.5.0 APK and `.deb` with a set and a schedule kept, because 1.6
  is the first update Linux and Android users get.
- **Order and space:** `primary`, then `ventura`, then the Linux clone (it
  creates the folder workspace), the phone joins, then the Windows clone
  and `primary` join; those clones go before `peer` is created last with
  1.5.2. Each clone and clean clone is claimed in `machine-ledger` and
  released after its check.
- **Stated limits:** the x64 MSI is smoke-tested only on the Windows Server
  runner, and the x86-64 `.deb` only by the workflow; with Smart App Control
  on, Windows blocks the unsigned installer, which the limits say in every
  language.
- **Publication:** `user-confirmed` 2026-11-04: the agent merges, tags, and
  publishes once the checks and the completed-change review pass. A product
  defect holds the release; its fix goes test-first in its own pull request
  through `babysit-pr`, then a new candidate. After publication, a failure
  follows the rollback of `releasing.md` step 7.

- **Rollback after publication:** `releasing.md` step 7 for the Mac. A fix
  for Windows needs a new MSI version (1.6.1), because the MSI refuses a
  downgrade or a rebuilt 1.6.0; a fixed APK needs `versionCode` 3 or higher;
  a fixed macOS build needs a number above 33. The `v1.6.0` assets stay
  downloadable when another release becomes latest; the site's Windows
  lines stay unless Windows itself is withdrawn, which then reverts them
  in a separate pull request.

## Boundaries

- No new features; a defect found here gets its own row unless the release
  is held for it.
- Public copy beyond the SmartScreen lines is `DOCS-007`'s, already merged.
- Google Play, the Microsoft Store, and Authenticode signing are out of
  scope.

## Acceptance

- `AC-01`: both DMGs and feeds, the APK, both `.deb` files, and both MSI
  packages come from R; the record lists their hashes and the consumed
  build numbers, and the host checks of `releasing.md` step 3 pass.
- `AC-02`: the matrix in the record passes, including the row checks, and
  after publication 1.5.2 updates in the app to 1.6.0.
- `AC-03`: tag `v1.6.0` and its GitHub Release exist and are latest; the
  downloaded assets match the verified outputs; both public feeds verify.
- `AC-04`: iOS 1.6.0 is `WAITING_FOR_REVIEW` with the six listings; the
  site, the README, and the availability page show the release; Projects
  says Done and milestone `1.6.0` is closed.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- The matrix in the record's plan, on the release builds and the Intel
  verification candidate.

## Decisions or blockers

- Decided 2026-11-04 (`user-confirmed`): unsigned MSI packages, the reduced
  matrix, and publication by the agent.
