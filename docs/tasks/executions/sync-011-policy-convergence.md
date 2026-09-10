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
2. Add `SyncLocalPolicy.sq` and `7.sqm`: ordered intent rows and the base
   singleton, validated with `TargetPolicy.fromStoredValues`, corruption
   fail-closed; the decorator records intents inside the store's save
   transaction while linked; the base is written only in the transaction
   that replaces the merged policy; both clear with the replica.
3. Replace `AppleSyncAuthoring`'s channel with the intent drainer and add
   the reconciler per `D1`, `D2`, and `D7`: domain intents authored after
   `writers.open()` and before publish, consume, the name decided after
   consume (`D4`) and published by a second leg, apply last; the raw store
   injected into `AppleSync` shares one write gate with the decorator, held
   only for read, merge, and apply (user saves never take the flight mutex);
   compare-and-set with one re-read retry; capacity reasons per `D5`; no
   replica writes.
4. Cover in `commonTest` the lost-removal case, a save during an in-flight
   exchange, the fresh-replica default name against a custom workspace
   name, every merge and skip case, both capacity reasons with their exits,
   and corruption.
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
- **Critical or Required findings:** unspecified base write and advance
  rules (a base written before the apply, or advanced on a refused apply,
  authors removals for the peer's websites); `AC-02` needed a group removal
  control that does not exist; a backfill authored after publish would not
  leave in the same pass; the offline row ignored author registration; the
  summary claim ignored an active session; cleanup could not restore
  per-device originals; untruthful capacity copy; missing `A-04`/`T-14`
  owner rows; `D7` head retention could wedge the outbox; write-surface
  gaps (websites recipe, second-install line, decorator signal).
- **Resolution:** base advances only inside the policy transaction and never
  on a refused or failed apply, skip rules, fail-closed corruption; `AC-02`
  physical claim limited to presence; the offline row counts authored
  bundles; the summary claim excludes an active session; cleanup removes
  fixture domains only; `D5` reason-specific copy; `A-04` and `T-14` added;
  write surface completed. Advisory items folded: signal from reconciler
  applies only, base validated like the policy tables, re-add-on-link
  consequence in `DESIGN.md`, iOS reapply timing noted.
- **Maintainer review (2026-09-10):** two gaps reopened `D1`/`D7` and
  `D2`/`D4`. A base-only merge cannot recover a lost removal (website added
  and authored, exchange fails, website removed, process closes: the
  projection still holds it and the merge re-adds it), so local intent is
  now durable, written in the save transaction, replacing the volatile
  queue accepted by `SYNC-010` `D1`. A fresh replica authoring its default
  group name before consume could override an existing custom name by the
  greatest-key rule, so the name is decided after consume and a default is
  never authored over a projected name. Also: `D5` tests the exit from
  overflow and separates the local cap from reducer capacity; `D6` promises
  no immediate restriction change; `AC-05` allows synthetic fixture domains
  in ignored evidence; `D8` adds one pre-link sentence.

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

- Maintainer decisions `D1` to `D8` in the brief precede implementation.
- iOS reapplies a converged change inside a session only on poll loss,
  retry, or relaunch; linking converges both devices to the union of their
  websites and re-adds a website a peer removed before this device linked.
- A rejected bundle still pins the cursor and now delays visible
  convergence; remotely originated websites stay local after **Remove
  workspace** without provenance, as the removal copy already says.

## Final

- **Status:** pending
- **Outcome:** pending
