# Execution: `SYNC-013`

- **Brief:** [Remove dead synchronization code without changing behavior](../specifications/sync-013-simplify-sync-core.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** pending
- **Reviewer:** pending until assigned
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

- Pending.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pending | |

## Blockers and accepted risks

- None known. The iOS runtime wiring is intentionally retained; see the brief.
