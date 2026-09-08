# Execution: `SYNC-010`

- **Brief:** [Publish and consume pending encrypted bundles with truthful sync status and retry](../specifications/sync-010-cloudkit-sync.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** implementation agent (2026-09-08)
- **Reviewer:** independent plan review pending; completed-change review pending
- **Branch:** `feature/sync-010-cloudkit-sync`
- **Worktree:** `~/Projects/Polyglot/posato-sync-010`
- **Updated:** 2026-09-08

## Plan

1. Maintainer answers `D1` through `D3`; `D4` and `D5` stand unless changed.
2. Common mailbox port over `MailboxTypes`; both adapters implement it; the
   `D4` token-expiry outcome on both native providers and adapters.
3. `6.sqm` with `deletePendingBundle`; store acknowledgement in one
   transaction; `SqlSyncReplicaStoreContractTest` and migration verification.
4. Exchange orchestrator in `commonMain`: workspace-key read, transport-key
   derivation, writer open, publish leg, consume leg, account-isolation
   handling, process-scoped single flight shared with `AppleBootstrap`;
   `commonTest` over fakes.
5. Removal sequence and its port use (zone, key items, local clear) with the
   `D2` control; `D1` outbox feed from the exact-domain commit path.
6. Graph composition on both platforms inside the existing single provider
   functions; real-graph `jvmTest` and `iosTest`.
7. Status section under `D3`, strings, `DESIGN.md` amendment, the
   `verify-posato` sync recipe rows, then the physical matrix, `./gradlew
   quality`, independent completed-change review, closeout and PR. Closeout
   also corrects the wiki sentence that still keeps explicit removal with
   `SYNC-009` and adds the missing `observed` entries for `SYNC-009` and this
   task.

## High-risk plan review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Result

- Pending.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| pending | | |

## Blockers and accepted risks

- `D1`, `D2`, and `D3` are maintainer decisions; implementation of the
  affected steps waits for them.
- The maintainer's iCloud account already holds the `SYNC-009` workspace, so
  the physical matrix begins from an established state and the first
  from-empty row depends on the removal built here.

## Final

- **Status:** `active`
- **Outcome:** pending
