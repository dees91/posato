# `RELEASE-008`: Verify and publish Posato 1.5.1 for Mac

- **Review tier:** `high-risk`
- **Tier reason:** Signed artifacts and two stable update feeds cannot be
  taken back once installations read them.
- **Dependencies:** `MACOS-030` (#197, merged as `59696a51`). Release 1.5.1,
  wave R1.5.1/W2.
- **Integration group:** `PR-RELEASE-1-5-1`, milestone `1.5.1`.
- **Authority:** [release roadmap](../release-roadmap.md) revision 30, row
  `RELEASE-008`; [`releasing.md`](../../development/releasing.md); the
  [RELEASE-007 record](../executions/release-007-release-1-5.md);
  [ADR 0008](../../decisions/0008-macos-update-delivery.md). The maintainer
  asked for the release up to the GitHub Release assets on 2026-10-24.
- **Record:** [execution record](../executions/release-008-release-1-5-1.md)

## Outcome

Follow `releasing.md`. Differences for 1.5.1:

- **Mac only:** `MARKETING_VERSION = 1.5.1`; macOS build 31 for both
  architectures (both published feeds hold 30). No iOS build, store
  submission, Android APK, or Linux package is made.
- **Release assets:** the five macOS assets, plus the 1.5.0
  `posato_1.5.0_arm64.deb`, `posato_1.5.0_amd64.deb`, and
  `Posato-1.5.0.apk` attached again byte for byte, so `releases/latest`,
  where the README and the site send people, still offers every platform;
  one `SHA256SUMS` covers the seven files.
- **Packaging in this pull request:** the release notes, the appcast notes,
  and the Mac file names on the site and the availability page; no separate
  packaging row.
- **Notes say how a stuck 1.5.0 updates:** quit and reopen Posato, then
  check for updates, because 1.5.0's own update gate can refuse.

## Boundaries

- No new features; a defect found here gets its own row unless the release
  is held for it.
- The App Store, Google Play, and the Linux and Android builds are out of
  scope.

## Acceptance

- `AC-01`: both DMGs and feeds come from a clean clone of R and pass the
  host checks of `releasing.md` step 3; the record lists their hashes and
  build 31.
- `AC-02`: the matrix in the record passes, and after publication 1.5.0
  updates in the app to 1.5.1, starting from a 1.5.0 that lost its helper.
- `AC-03`: tag `v1.5.1` and its GitHub Release exist and are latest; the
  downloaded assets match the verified outputs and the 1.5.0 Linux and
  Android files; both public feeds verify.
- `AC-04`: the site and the availability page name the 1.5.1 Mac files.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- The matrix in the record's plan, on the release DMGs and the Intel
  verification candidate.

## Decisions or blockers

- None.
