# Execution: `SYNC-011`

- **Brief:** [Policy convergence](../specifications/sync-011-policy-convergence.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending handoff to an implementing agent
- **Reviewer:** plan reviewer and completed-change reviewer pending
- **Branch:** `feature/sync-011-policy-sync`
- **Updated:** 2026-09-10

## Plan

1. Confirm the `observed` facts behind the brief: the exchange discards the
   projection, the outbox diffs exact domains only, the two view models
   re-read only on their refresh requests, and `AppleSyncPersistenceTest`
   asserts that only the replica changes.
2. Add the applied-base store (`SyncAppliedPolicy.sq`, `7.sqm`) with
   boundary validation and migration tests; clear it in
   `AppleWorkspaceRemoval`.
3. Add the reconciler per `D1` and `D2`: projection out of
   `AppleMailboxExchange`, three-way merge, raw-store write behind the shared
   gate with one conflict retry, capacity refusal per `D5`, no replica
   writes, no new status. Cover every merge case in `commonTest`.
4. Extend the outbox diff to the group name per `D4` and retain the head
   change until authored per `D7`.
5. Add the store change signal and the two view-model subscriptions per
   `D3`; verify the `TARGETS-001` editor rules still hold under a remote
   change.
6. Build the two-harness convergence tests, invert the persistence
   assertion, and add the mid-session frozen-set case.
7. Update `DESIGN.md`, the sync and application-group recipes, the threat
   model owner rows, the wiki topic, and the wiki log at closeout; run
   focused tests, `./gradlew quality`, the Simulator fixtures, and the
   attended physical matrix; write the threat-model closeout statement;
   obtain the independent completed-change review.

## High-risk plan review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Result

- Pending implementation.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Reconciler merge cases over fakes | pending | |
| Two-harness convergence and inverted persistence case | pending | |
| `7.sqm` migration verification | pending | |
| `./gradlew quality` | pending | |
| Simulator website and first-install fixtures | pending | |
| Physical bidirectional website convergence | pending | |
| Physical application group convergence, selections local | pending | |
| Physical pre-link backfill after removal and re-link | pending | |
| Physical offline retry | pending | |
| Threat-model closeout statement and owner rows | pending | |

## Blockers and accepted risks

- Maintainer decisions `D1` to `D7` in the brief precede implementation.
- A rejected bundle still pins the cursor and now delays visible
  convergence; exact refetch stays out of scope.
- Websites that originated remotely stay local after **Remove workspace**
  without provenance; the removal copy already says local websites stay.
- Session start, early end, and expiry convergence remain `SYNC-012`.

## Final

- **Status:** pending
- **Outcome:** pending
