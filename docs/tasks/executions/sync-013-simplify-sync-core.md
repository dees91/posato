# Execution: `SYNC-013`

- **Brief:** [Remove dead synchronization code without changing behavior](../specifications/sync-013-simplify-sync-core.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Codex `/root`
- **Reviewer:** Codex `/root/sync_013_review`
- **Branch:** `feature/sync-013-simplify-sync-core`
- **Updated:** 2026-09-02

## Plan

1. Confirm with `grep` that the four queries and `SyncProjectionDigest` have
   no production caller, then delete the query blocks from `SyncReplica.sq`.
2. Move the digest helper into `commonTest` next to `SyncReducerTest` and
   `CanonicalBufferLifecycleTest`, keeping their assertions unchanged.
3. Run the shared suites and `./gradlew quality`, obtain one independent
   completed-change review with evidence, and close this record.

## Result

- Removed the four unused SQLDelight query blocks and moved the unchanged
  projection digest helper from `commonMain` to `commonTest`.
- Kept the schema, migrations, wire format, provider contract, validation
  layers, and iOS runtime wiring unchanged.

## Completed-change review

- **Verdict:** `approve`
- **Critical or Required findings:** none
- **Resolution:** The reviewer inspected the complete staged diff, confirmed
  production callers for all 14 remaining queries and the byte-identical
  source-set move, and independently passed the shared suites, migration
  verification, and aggregate quality gate.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew :shared:jvmTest :shared:iosSimulatorArm64Test :shared:verifySqlDelightMigration` | `pass` | JVM and iOS Simulator suites and SQLDelight migration verification passed before review and in the independent review. |
| `./gradlew quality` | `pass` | The author passed 115 actionable tasks; the independent reviewer passed the same current-input gate with 112 actionable tasks. |
| Source and diff scans | `pass` | Removed names have no source reference, every remaining query has a production caller, the digest exists only in `commonTest`, and `git diff --cached --check` passed. |

## Blockers and accepted risks

- None. The iOS runtime wiring and duplicated validation layers remain
  intentionally retained under the brief's accepted boundaries.
