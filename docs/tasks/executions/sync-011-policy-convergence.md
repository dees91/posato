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
2. Add the applied-base store (`SyncAppliedPolicy.sq`, `7.sqm`, one
   singleton row validated with `TargetPolicy.fromStoredValues`, corruption
   fail-closed); write it only in the transaction that replaces the merged
   policy; clear it in the transaction that clears the replica.
3. Add the reconciler per `D1` and `D2`: `AppleMailboxExchange` returns the
   projection; the authoring half runs after `writers.open()` and before the
   publish leg, the apply half after consume; the raw store is injected into
   `AppleSync` and shares one write gate with `SyncTargetPolicyStore`, held
   only for read, merge, and apply (user saves never take the flight mutex,
   so ordering is latency only); compare-and-set with one re-read retry;
   capacity refusal per `D5` with the reason on the sync state; no replica
   writes. Cover every merge and skip case in `commonTest`.
4. Extend the outbox diff to the group name per `D4` (projection wins on a
   both-changed name; no default authored over an existing projected name);
   leave the queue drop in place per `D7` and test recovery at the next
   pass.
5. Add the defaulted change signal on `LocalTargetPolicyStore`, forward it
   in the decorator, emit it from reconciler applies only, and subscribe the
   two view models; keep the existing fakes compiling; verify the
   `TARGETS-001` editor rules under a remote change and that a screen never
   re-reads its own save.
6. Build the two-harness convergence tests with a cursor-aware shared fake
   mailbox and distinct wall clocks, invert the persistence assertion, and
   add the mid-session frozen-set case.
7. Update `DESIGN.md`, the sync and application-group recipes, the threat
   model owner rows, the wiki topic, and the wiki log at closeout; run
   focused tests, `./gradlew quality`, the Simulator fixtures, and the
   attended physical matrix; write the threat-model closeout statement;
   obtain the independent completed-change review.

## High-risk plan review

- **Verdict:** `changes-required` (independent reviewer, 2026-09-10),
  resolved in the brief before handoff.
- **Critical or Required findings:** the base's write and advance rules were
  unspecified, and two natural implementations (base written before the
  apply, or advanced on a refused apply) would author removals for the
  peer's websites; `AC-02` and two physical rows required a group removal
  control the product does not expose; a backfill authored after the publish
  leg would not leave in the same pass; the offline row expected one
  acceptance although a fresh author registers first; the outcome promised
  the Session summary during an active session; cleanup could not restore
  per-device originals after the union on link; the generic action-required
  copy is untruthful for a capacity refusal; the threat-model closeout missed
  the `A-04` and `T-14` owner rows; `D7`'s head retention had no terminal
  rule and could wedge the outbox; the write surface missed the websites
  recipe sentence, the second-install line, and the decorator forwarding of
  the change signal.
- **Resolution:** base advances only inside the policy transaction and never
  on a refused or failed apply, with skip rules for what the projection
  already holds or lacks and fail-closed corruption; `AC-02` limits the
  physical claim to presence, absence stays fake-only; the reconciler has an
  authoring half before publish and an apply half after consume; the offline
  row counts authored bundles; the summary claim excludes an active session;
  cleanup removes fixture domains only and the union is accepted before the
  run; `D5` proposes a reason-specific message; `A-04` and `T-14` added;
  `D7` makes the reconciler the authoritative recovery and keeps the queue
  drop; the write surface is complete. Advisory items folded: signal emitted
  by reconciler applies only, projection wins on a both-changed name, base
  validated like the policy tables, the re-add-on-link consequence recorded
  in `DESIGN.md`, iOS reapply timing noted, the FIFO is an optimization.

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
- On iOS a converged change is reapplied inside a session only on poll
  loss, retry, or relaunch; no reapply schedule is guaranteed.
- Linking converges both devices to the union of their websites, and a
  website a peer removed before this device linked comes back for both.
- A rejected bundle still pins the cursor and now delays visible
  convergence; exact refetch stays out of scope.
- Websites that originated remotely stay local after **Remove workspace**
  without provenance; the removal copy already says local websites stay.
- Session start, early end, and expiry convergence remain `SYNC-012`.

## Final

- **Status:** pending
- **Outcome:** pending
