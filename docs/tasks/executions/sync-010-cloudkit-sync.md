# Execution: `SYNC-010`

- **Brief:** [Publish and consume pending encrypted bundles with truthful sync status and retry](../specifications/sync-010-cloudkit-sync.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending assignment (brief prepared 2026-09-08)
- **Reviewer:** independent plan review complete (changes-required, resolved in the brief); completed-change review pending
- **Branch:** `feature/sync-010-cloudkit-sync`
- **Worktree:** `~/Projects/Polyglot/posato-sync-010`
- **Updated:** 2026-09-08

## Plan

1. `D1` through `D5` accepted by the maintainer on 2026-09-08 as recommended.
2. Common mailbox port over `MailboxTypes`; both adapters implement it; the
   `D4` token-expiry outcome on both native providers and adapters.
3. Acknowledgement and clear queries in `SyncReplica.sq` and
   `SyncBootstrap.sq` (no schema migration), a writer-owned acknowledgement
   under the revision check, `BootstrapStore.clearEstablished`;
   `SqlSyncReplicaStoreContractTest`.
4. Exchange orchestrator beside `AppleBootstrap`: established check first,
   workspace-key read used directly as the transport key, writer open and
   reopen, publish leg, consume leg with `exactRefetchAvailable = false`,
   account handling, coalesced process-scoped single flight; `commonTest`
   over fakes.
5. Anchor-gated removal sequence (zone, key item, local clear) with the `D2`
   control; `D1` outbox feed after the exact-domain commit.
6. Process-scoped core and graph composition on both platforms inside the
   existing single provider functions; real-graph `jvmTest` and `iosTest`.
7. Status section under `D3`, strings, `DESIGN.md` amendment, the
   `verify-posato` sync recipe rows, then the physical matrix, `./gradlew
   quality`, independent completed-change review, closeout and PR. Closeout
   also corrects the wiki sentence that still keeps explicit removal with
   `SYNC-009` and adds the missing `observed` entries for `SYNC-009` and this
   task.

## High-risk plan review

- **Verdict:** `changes-required` (independent review, 2026-09-08; 1
  Critical, 9 Required, 6 Recommended, 2 Optional), corrections applied to
  the brief before implementation.
- **Critical or Required findings:** `C-1` a second HKDF step for the
  transport key silently amended ADR 0006, which makes the 32-byte workspace
  key the transport key itself; `R-1` acknowledging a bundle by deleting the
  outbox row outside the writer would freeze the writer, whose checkpoint
  verification compares the whole snapshot; `R-2` `6.sqm` was a schema
  migration for query-only changes; `R-3` the core was runtime-scoped while a
  second iOS runtime can exist; `R-4` no per-exchange anchor check, so a
  stale device could publish into a re-created workspace; `R-5` removal
  could delete a zone whose anchor this device never joined; `R-6` `AC-05`
  claimed the non-removing device could establish, while its kept
  established row yields action required; `R-7` the receipt semantics for a
  rejected bundle were undefined and hid a pinned cursor; `R-8` `D1` claimed
  atomicity across two separate transactions and had no failure or pre-link
  story; `R-9` a mid-operation account switch is not physically observable.
- **Resolution:** `C-1` the key bytes are the transport key, no derivation.
  `R-1` a writer-owned acknowledgement under the revision check. `R-2`
  queries only, no migration. `R-3` process-scoped core sharing the flight
  mutex. `R-4` and `R-5` the established check gates every exchange and the
  zone delete. `R-6` `AC-05` and the `D2` copy state the other device's
  action-required state and own removal. `R-7` `exactRefetchAvailable =
  false`, pinned cursor recorded as an accepted limit. `R-8` `D1` orders the
  two commits, defines the failure and pre-link behavior, and records the
  interim divergence as an accepted risk. `R-9` `AC-03` is a sign-out before
  an attempt with the other device receiving the bundle once. Accepted
  Recommended items: coalesced triggers, launch and foreground only on an
  established workspace, airplane-mode retry row, completed write surface,
  a scoped use-and-clear on `WorkspaceKeyValue`, the undelivered-changes
  sentence in the removal confirmation, and the orchestrator placed beside
  `AppleBootstrap`. Declined: shortening the brief below what the High-risk
  boundaries need.

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

- The maintainer's iCloud account already holds the `SYNC-009` workspace, so
  the physical matrix begins from an established state and the first
  from-empty row depends on the removal built here.
- Accepted MVP limits recorded by the plan review: a rejected bundle pins the
  cursor until a later task adds exact refetch; edits made before linking are
  not backfilled until `SYNC-011`.

## Final

- **Status:** `active`
- **Outcome:** pending
