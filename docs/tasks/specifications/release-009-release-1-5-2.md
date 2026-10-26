# `RELEASE-009`: Verify and publish Posato 1.5.2 for Mac

- **Review tier:** `high-risk`
- **Tier reason:** Signed artifacts and two stable update feeds cannot be
  taken back once installations read them.
- **Dependencies:** `MACOS-031` (#204, `8dcfac4b`) and `MACOS-029` (#205,
  `e039672e`). Release 1.5.2, wave R1.5.2/W2.
- **Integration group:** `PR-RELEASE-1-5-2`, milestone `1.5.2`.
- **Authority:** [release roadmap](../release-roadmap.md) revision 32, row
  `RELEASE-009`; [`releasing.md`](../../development/releasing.md); the
  [RELEASE-008 record](../executions/release-008-release-1-5-1.md);
  [ADR 0008](../../decisions/0008-macos-update-delivery.md). The maintainer
  asked on 2026-10-26 for 1.5.2 in the same way as 1.5.1, which went up to
  the published GitHub Release.
- **Record:** [execution record](../executions/release-009-release-1-5-2.md)

## Outcome

Follow `releasing.md`. Differences for 1.5.2:

- **Mac only:** `MARKETING_VERSION = 1.5.2`; macOS build 32 for both
  architectures (both published feeds hold 31). No iOS build, store
  submission, Android APK, or Linux package is made.
- **Release assets:** the five macOS assets, plus the 1.5.0
  `posato_1.5.0_arm64.deb`, `posato_1.5.0_amd64.deb`, and
  `Posato-1.5.0.apk` attached again byte for byte; one `SHA256SUMS` covers
  the seven files.
- **Packaging in this pull request:** the release notes, the appcast notes,
  and the Mac file names on the site and the availability page.
- **Base:** `main`; since `v1.5.1` it holds only the 1.5.2 product changes
  (the `PLATFORM-002` spike added a verification jar task outside the Mac
  package), so the roadmap's "branch from `v1.5.1`" rule does not apply.

## Boundaries

- No new features; a defect found here gets its own row unless the release
  is held for it.
- The App Store, Google Play, and the Linux and Android builds are out of
  scope; the shared session change ships to them with their next release.

## Acceptance

- `AC-01`: both DMGs and feeds come from a clean clone of R and pass the
  host checks of `releasing.md` step 3; the record lists their hashes and
  build 32.
- `AC-02`: the matrix in the record passes, including both fixes on the
  release build, and after publication 1.5.1 updates in the app to 1.5.2.
- `AC-03`: tag `v1.5.2` and its GitHub Release exist and are latest; the
  downloaded assets match the verified outputs and the 1.5.0 Linux and
  Android files; both public feeds verify.
- `AC-04`: the site and the availability page name the 1.5.2 Mac files.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- The matrix in the record's plan, on the release DMGs and the Intel
  verification candidate.

## Decisions or blockers

- None.
