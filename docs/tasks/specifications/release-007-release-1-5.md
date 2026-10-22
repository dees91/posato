# `RELEASE-007`: Verify the 1.5.0 candidates and publish Posato 1.5

- **Review tier:** `high-risk`
- **Tier reason:** Signed artifacts, two stable update feeds, and an App
  Review submission cannot be taken back once people install them. Release
  1.5 also publishes the first Linux and Android packages: the Android
  signing key it creates must sign every later Android update, so losing or
  leaking it cannot be repaired.
- **Dependencies:** every 1.5 row is merged, including `DOCS-006` (#178),
  which holds the media, pages, store text and screenshots, and the release
  notes. Release 1.5, wave R1.5/W6.
- **Integration group:** `PR-RELEASE-1-5`, milestone `1.5.0`.
- **Authority:** [release roadmap](../release-roadmap.md), row
  `RELEASE-007`; [`releasing.md`](../../development/releasing.md) (the
  standing route); the [RELEASE-006 record](../executions/release-006-release-1-4.md);
  [ADR 0008](../../decisions/0008-macos-update-delivery.md);
  [ADR 0010](../../decisions/0010-linux-android-and-folder-workspace.md);
  the [DOCS-006 record](../executions/docs-006-release-1-5-media.md) (the
  GitHub release notes).
- **Record:** [execution record](../executions/release-007-release-1-5.md)

## Outcome

Follow `releasing.md`. Differences for 1.5:

- **Version:** `MARKETING_VERSION = 1.5.0`; macOS build 30 for both
  architectures (both published feeds hold 29); iOS build: the next free
  number from `store status`; Android `versionName` 1.5.0, `versionCode` 1.
- **Data migration:** `migrations/14.sqm` and the sync bootstrap tables
  changed since `v1.4.0`. The matrix stays reduced as in 1.4 by the
  maintainer's choice; the in-app update from 1.4.0 proves the migration on
  macOS.
- **Linux:** `.deb` packages for arm64 and x86-64, both built and
  smoke-tested by a new GitHub Actions workflow on `ubuntu-24.04` and
  `ubuntu-24.04-arm` from R; the arm64 package also passes the Linux E2E in
  the Tart clone. No update feed: an update is a new `.deb` installed over
  the old one.
- **Android:** a release build type and a signing configuration that reads
  the keystore path from the ignored `local.properties` and the passwords
  from the login Keychain. The release-signed `Posato-1.5.0.apk` passes
  the Android E2E on the test phone. No update feed: an update is a new APK
  signed with the same key.
- **Release assets:** the five macOS assets plus the two `.deb` files and
  the APK; one `SHA256SUMS` covers all of them.
- **Runbook:** `releasing.md` gains the Linux and Android steps
  (prerequisites, candidates, checks, assets) proven here.

## Boundaries

- The agent does everything except the maintainer touchpoints in the
  [execution record](../executions/release-007-release-1-5.md#plan).
- The Android keystore and its passwords never enter Git, CI secrets, or a
  pull request; GitHub Actions builds only the Linux packages.
- Google Play is out of scope: the APK ships on GitHub Releases only.
- No new features. A defect found here gets its own row unless the release
  is held for it.

## Acceptance

- `AC-01`: the macOS, iOS, Linux, and Android candidates come from R and
  pass the release checks of `releasing.md` step 3, including the
  seam-absence checks and, for the APK, `apksigner verify` with the
  release certificate.
- `AC-02`: the matrix in the record passes on the candidates; after
  publication, 1.4.0 updates in the app to 1.5.0 on macOS.
- `AC-03`: tag `v1.5.0` and its GitHub Release exist and are latest; the
  downloaded assets match the verified outputs; both public feeds verify.
- `AC-04`: the site, README, availability page, and `PRIVACY.md` describe
  1.5 from `DOCS-006`, and their Linux and Android download links reach the
  published files; the iOS build is submitted with the 1.5 store text and
  screenshots.
- `AC-05`: the record holds the verdict for R, the checksums, the consumed
  build numbers, the Android signing certificate fingerprint, and the
  `TB-08`/`T-13` review.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

The matrix in the record, run once on the candidates of R: macOS in Tart
VMs, iOS on the test iPhone, Linux in the Tart Ubuntu clone, Android on the
test phone. Independent plan review before R is built; one completed-change
review before tagging.

## Decisions or blockers

- **Blocker (maintainer):** a backup of the Android keystore and its
  password outside this Mac, before the APK is published.
- **`user-confirmed` (2026-10-22):** the Android keystore lives outside the
  repository with its password in the login Keychain, and the maintainer
  backs it up; both `.deb` packages are built on GitHub Actions runners.
- **`user-confirmed` (2026-10-22):** the publication go in advance, given
  when all checks and the completed-change review pass; the maintainer is
  away during the run. A product defect found by a check holds the
  release: the agent fixes it test-first in its own pull request through
  `babysit-pr`, builds a new candidate, and continues; after publication,
  the `releasing.md` rollback applies. The matrix is reduced as in 1.4: the
  replacement over 1.4.0 and the iPhone upgrade over 1.4.0 are not
  repeated, and the in-app update from 1.4.0 covers migration 14 on macOS.
- **Decided by the agent under the maintainer's delegation (2026-10-22):**
  App Store release type `after-approval` and claiming iOS 26 only, as in
  1.4.
