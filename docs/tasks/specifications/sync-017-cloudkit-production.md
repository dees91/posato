# `SYNC-017`: Make iCloud sync work in the CloudKit Production environment

- **Review tier:** `high-risk`
- **Tier reason:** Deploying the Production schema is irreversible (Production record types and fields cannot be deleted), touches an account-level resource, and puts real user data under the release builds.
- **Dependencies:** completed `MACOS-008` (merged as `9e7e677`) and `IOS-003` (merged as `9324a60`), whose release builds already use CloudKit Production.
- **Integration group:** `PR-CLOUDKIT-PRODUCTION`, roadmap wave Release/R2, in parallel with `MACOS-009` and `DESIGN-003`.
- **Authority:** [MVP roadmap](../mvp-roadmap.md), [encrypted operation ADR](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md) (no compaction in format 1), [workspace bootstrap ADR](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md) (schema, production handover), [privacy policy](../../../PRIVACY.md), [MACOS-008 record](../executions/macos-008-developer-id-distribution.md), [IOS-003 record](../executions/ios-003-app-store-release.md).

## Outcome

The Production schema matches what the code writes, and a Developer ID Mac package and a TestFlight iPhone build signed in to the same Apple Account link, converge websites and sessions, and remove the workspace in Production. Quota and retention behavior is measured and stated.

## Boundaries

- `observed` starting point: zone `PosatoSyncV1` with record types `PosatoWorkspaceV1` and `PosatoEncryptedBundleV1`, identical in the iOS app and the macOS companion, and no queryable index (ADR 0007). Release entitlements select Production; no Production schema is deployed, the companion has not launched under the release signature, and the Keychain group across environments is a source claim.
- Before deployment, audit the Development schema against the code: deployment copies every record type and field, including ones left by earlier experiments. The agent prepares the exact schema and a checklist; the maintainer deploys in the CloudKit Console. Credentials and management tokens never enter Git.
- Add no record type, field, index, subscription, or format change beyond what the code already uses. Mailbox compaction stays out of scope (ADR 0006).
- Quota and retention: measure record sizes and counts for a representative workload against the person's iCloud storage, and decide whether the privacy policy, README, or a later row needs a limit or disclosure.
- Physical runs create data in the maintainer's own iCloud account; each run ends with **Remove workspace**. The Mac is shared with `MACOS-009` and `DESIGN-003`, so physical gates run one after the other.

## Acceptance

- `AC-01` — The Development schema contains exactly the fields the code writes, and the deployed Production schema matches it.
- `AC-02` — The release macOS companion launches under the Developer ID signature, reaches Production, and uses the synchronizable Keychain workspace key.
- `AC-03` — On the same Apple Account, the Developer ID Mac and the TestFlight iPhone link, converge a website change both ways, share a session start and an early end, and complete Remove workspace and a fresh link.
- `AC-04` — Measured record sizes, counts, and retention after the AC-03 workload are recorded with a decision on quota limits and disclosures.

## Verification

- Independent plan review before any Console deployment; independent completed-change review after.
- Schema comparison between the code, Development, and Production, recorded without account identifiers.
- Physical runs through [verify-posato](../../../.agents/skills/verify-posato/SKILL.md) on release artifacts; evidence stays under `build/verification/`. `./gradlew quality` for any code change.

## Decisions or blockers

- **Blocker (maintainer):** deploy the audited schema to Production in the CloudKit Console after the plan review.
- **Blocker:** a current TestFlight build and a Developer ID package on the devices used for AC-03.
- **Open:** whether a quota or retention finding needs a product change before release or only a disclosure.
