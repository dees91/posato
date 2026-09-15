# Execution: `SYNC-017`

- **Brief:** [`sync-017-cloudkit-production.md`](../specifications/sync-017-cloudkit-production.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code (agent)
- **Reviewer:** pending (independent agent)
- **Branch:** `feature/sync-017-cloudkit-production`
- **Updated:** 2026-09-15

## Plan

`observed` starting facts: both adapters write only `PosatoWorkspaceV1`
(`workspaceId`, `transportEpochId`, `keyEpochId`) and `PosatoEncryptedBundleV1`
(`payload`), all `BYTES`, in zone `PosatoSyncV1`; enumeration uses the changes
traversal, never a query. Both adapters map `CKError.quotaExceeded` to
retryable. The release and development Mac apps share
`~/Library/Application Support/Posato/posato-policy.db`, and `IOS-003` observed
that a TestFlight install over the development build keeps local state.
`xcrun cktool` exports a container schema per environment with a management
token.

1. **Expected schema.** Derive the exact schema from both adapters' record
   construction and ADR 0007: two record types, four `BYTES` fields, no
   queryable, sortable, or searchable index on any custom or system field,
   plus the system `Users` type. The file stays under `build/verification/`.
2. **Token (maintainer).** Create a CloudKit management token in the Console
   and save it with `xcrun cktool save-token --type management` to the login
   Keychain. The token and team identifier never enter Git or evidence files.
3. **Audit Development.** `cktool export-schema` for Development and
   Production; strip the team identifier; diff Development against step 1.
   Production must be empty apart from system types. Record the categorical
   diff (types, fields, indexes, grants) in this record.
4. **Correct Development if needed (decision D2).** Extra types, fields, or
   indexes are removed by `cktool reset-schema` followed by
   `cktool import-schema --validate` of the step 1 file, only after both
   development builds have run **Remove workspace**. Re-export and re-diff.
5. **Deploy (maintainer).** Console → Development schema → Deploy Schema
   Changes to Production, after the plan review passes and the step 3 or 4
   diff is empty. Re-export Production and diff it against Development
   (`AC-01`).
6. **Artifacts.** Check whether sync-relevant paths (`shared/`, `iosApp/`,
   `macosSyncCompanion/`) differ between main and the sources of TestFlight
   build 1 and the installed Developer ID build 4. Reuse them when identical;
   otherwise build and notarize a new Mac candidate and upload iOS build 2+
   (decision D4). Record source commits and build numbers.
7. **Local-state baseline.** On the development builds: **Remove workspace**
   on both devices, then `posato-control reset --target desktop --yes`
   (backup retained). Announce a Mac exclusivity window to `MACOS-009` and
   `DESIGN-003`. Install the release builds.
8. **Physical run (attended, `AC-02`, `AC-03`).** Mac first: **Sync with
   iCloud** on the Developer ID app; confirm the companion process runs from
   `/Applications` with a Developer ID signature and Production entitlement
   (`codesign -dvv --entitlements` on the running path). Maintainer confirms
   one `PosatoSyncV1` zone in the Console Production private database. iPhone
   (TestFlight) joins; key adoption is proven by a completed exchange after
   relaunch. Converge a synthetic website both ways, start a session on one
   device and end it early from the other, then **Remove workspace** on both
   and link fresh once. Mac counts come from the read-only queries in the
   sync feature map; iPhone evidence is its status and the Mac's receipt.
9. **Measure (`AC-04`).** After the workload and before the final removal,
   record on the Mac only aggregates: `count(*)`,
   `sum(length(bundle_bytes))`, and `max(length(bundle_bytes))` from
   `sync_accepted_bundle`, grouped by the steps that produced them
   (registration, website change, session start or end). Extrapolate a
   heavy-use year against the 5 GB free iCloud tier and the 65,536-byte cap.
   CloudKit per-record overhead stays a `source-claim`.
10. **Quota and retention decision (D5).** State what a full iCloud account
    does today (retryable with no storage-specific status, from code and
    existing mapper tests) and propose disclosure only, or a follow-up row.
    The maintainer decides; no product change inside this task without that.
11. **Cleanup and closeout.** End with **Remove workspace** in Production on
    both devices; restore the development builds and Mac database backup;
    update this record, the cross-device synchronization topic's production
    question, the first-release-readiness row, and one wiki-log entry.
    `./gradlew quality` runs only if code changed.

## Decisions for the maintainer

- **D1 audit tool:** `cktool` export and diff (recommended) or Console
  inspection only.
- **D2 Development extras:** reset and import the exact schema (recommended
  when any extra exists) or deploy with them.
- **D3 tracked schema file:** none; ADR 0007 stays the authority
  (recommended) or add a `.ckdb` file with a named consumer.
- **D4 artifacts:** reuse identical builds (recommended) or always rebuild.
- **D5 quota:** open until step 9.

## High-risk plan review

- **Verdict:** pending
- **Critical or Required findings:** pending
- **Resolution:** pending

## Result

- Pending.

## Completed-change review

- **Verdict:** pending
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Pending | — | — |

## Blockers and accepted risks

- Maintainer: management token, Console deployment, attended physical run.
- Production record types and fields are permanent after step 5.

## Final

- **Status:** pending
- **Outcome:** pending
