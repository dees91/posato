# `RELEASE-006`: Verify the 1.4.0 candidates and publish Posato 1.4

- **Review tier:** `high-risk`
- **Tier reason:** Signed artifacts, two stable update feeds, and an App
  Review submission cannot be taken back once people install them. Release
  1.4 is also the first whose companion build has a verification-only
  variant (`SYNC-021`), and no release build may contain it.
- **Dependencies:** every 1.4 row: `DESIGN-004`, `IOS-007`, `MACOS-026`,
  `SESSION-007`, `MACOS-027`, `SYNC-021` (merged), and `DOCS-005`, which
  holds the media, pages, store text and screenshots, and the release notes.
  Release 1.4, wave R1.4/W3.
- **Integration group:** `PR-RELEASE-1-4`, milestone `1.4.0`.
- **Authority:** [release roadmap](../release-roadmap.md) revision 21, row
  `RELEASE-006`; [`releasing.md`](../../development/releasing.md) (the
  standing route); the [RELEASE-005 record](../executions/release-005-release-1-3.md);
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
  and its verification-only zone deletion amendment (2026-10-09);
  [ADR 0008](../../decisions/0008-macos-update-delivery.md).
- **Record:** [execution record](../executions/release-006-release-1-4.md)

## Outcome

Follow `releasing.md`. Differences for 1.4:

- **Version:** `MARKETING_VERSION = 1.4.0`; macOS build 29 for both
  architectures (both published feeds hold 28; the `MACOS-026` candidates
  9101 to 9103 read a loopback feed signed with a throwaway key and ran
  only in destroyed VM clones, so no installation reading the stable feeds
  holds them); iOS build 7 (`store status`: next free).
- **No verification seams in any release artifact.** The release packaging
  check and the DMG scan must report no seam key and no marker for both
  architectures, and the build must run without
  `posatoMacOsVerificationSeams`. The record cites the task output.
- **Update from 1.3.0:** the in-app update replaces 1.3.0 with 1.4.0 and
  keeps data, schedules, and the helper. Because the quit fix lives in the
  build being replaced, 1.3.0 still asks "Quit Posato?" once while a
  schedule is on; click Quit (`MACOS-026`). The notes from `DOCS-005` say
  so.
- **Reduced matrix:** no stored-data migration since `v1.3.0` (no schema,
  entitlement, or privacy-manifest change), so the replacement and iPhone
  upgrade checks of 1.3 are not repeated; the in-app update covers data
  retention. `MACOS-027` changed the helper daemon and the signing
  timeouts, so a cold boot and the updated installation are checked for
  ready setup and a schedule that starts on its own.
- **Sync format:** unchanged since `v1.3.0` (`inferred` from the diff: the
  sync changes are the macOS removal budget and its progress state, not the
  synced kinds or payloads), so iOS 1.3 and a 1.4 Mac work together while
  iOS 1.4 waits for App Review.

## Boundaries

- The agent does everything except the Keychain prompts for the release
  update key (and the Developer ID key if macOS asks), batched into as few
  requests as possible, and the publication go.
- No new features. A defect found here gets its own row unless the release
  is held for it.

## Acceptance

- `AC-01`: both macOS candidates and the iOS candidate come from R and pass
  the release checks of `releasing.md` step 3, including the seam-absence
  checks.
- `AC-02`: the reduced matrix in the record passes on the candidates; after
  publication, 1.3.0 updates in the app to 1.4.0.
- `AC-03`: tag `v1.4.0` and its GitHub Release exist and are latest; the
  downloaded assets match the verified outputs; both public feeds verify.
- `AC-04`: the site, README, availability page, and `PRIVACY.md` describe
  1.4 from `DOCS-005`; the iOS build is submitted with the 1.4 store text and
  screenshots.
- `AC-05`: the record holds the verdict for R, the checksums, the consumed
  build numbers, and the `TB-08`/`T-13` review.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

The reduced matrix in the record, run once on the candidates of R, per the
maintainer's order of 2026-10-09 to keep VM and iPhone verification to a
minimum. Independent plan review before R is built; one completed-change
review before tagging.

## Decisions or blockers

- **Blocker (maintainer):** the Keychain prompts during signing; the
  publication go.
- **`user-confirmed` (2026-10-10):** the reduced matrix in the record, App
  Store release type `after-approval`, and claiming iOS 26 only, as in 1.3.
- **Decided by the agent under the maintainer's delegation (2026-10-09):**
  the build numbers and the `DOCS-005` coupling in the record.
