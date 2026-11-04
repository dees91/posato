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
- **Release assets:** the eight of `releasing.md` step 7 plus
  `Posato-1.6.0-arm64.msi` and `Posato-1.6.0-x64.msi`; one `SHA256SUMS`
  covers the other ten.
- **Store:** `store prepare` writes the 1.6 description and What's New for
  `en-US` and, for the first time, `pl`, `de-DE`, `fr-FR`, `es-ES`, and
  `pt-BR`; release type `after-approval`.
- **Reduced matrix** (`user-confirmed` 2026-11-04, as in 1.4 and 1.5): no
  replacement of the 1.5.2 DMG, no iPhone upgrade over the 1.5.0 build, and
  no iCloud run. Kept: `primary` with the fresh arm64 DMG, `ventura` with
  the Intel DMG and its verification candidate, the test iPhone with R, the
  Linux clone with the arm64 `.deb`, the test Android phone with the signed
  APK, a Windows clone with the arm64 MSI, folder sync between release
  builds including Windows, and the in-app update from 1.5.2 on a kept
  `peer` clone.
- **Row checks:** on iOS, Linux, and Android, the shared session changes
  those builds have not shipped since 1.5.0 (`PauseClaims` and
  `EnforcementPort.replace` from `MACOS-030`, and the `SESSION-009` branch
  of `clearAfterEnd` from `MACOS-029`): a pause whose items change while it
  runs keeps restricting, and a pause that ends while Posato is closed
  clears. The 1.6 features get one representative run per target under the
  proportionate rule: a break in a scheduled pause, and a website that
  opens briefly.
- **Publication:** `user-confirmed` 2026-11-04: the agent merges, tags, and
  publishes once the checks and the completed-change review pass. A product
  defect holds the release; its fix goes test-first in its own pull request
  through `babysit-pr`, then a new candidate. After publication, a failure
  follows the rollback of `releasing.md` step 7.

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
