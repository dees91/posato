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

8. Derive the onboarding count from policy reads on step entry and after
   own saves, with receipt-keyed focus (`D9`, `AC-07`).
9. Overlay unauthored intent during apply (`D10`); post-consume authoring
   on the no-base pass (`D11`); at-cap `D5` reason with clearing condition
   and reason-aware status copy.
10. Compare-before-update refresh in both view models; `D8`/`D5` placement
    on existing surfaces; rare-state previews; physical screenshot list.

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

## Delta plan re-review (2026-09-10 amendments)

- **Scope:** `D9`, `D10`, `D11`, the `D5` correction, `AC-07`, and the
  presentation clarifications.
- **Verdict:** `changes-required` (independent reviewer, 2026-09-10);
  all findings accepted and folded into the brief before handoff.
- **Critical or Required findings:** (1) base `AC-04` capacity clause
  unqualified vs the `D5`-correction silence below cap — qualified to
  at-cap; (2) `D11` said extras “sort first” — greater HLC sorts last,
  corrected; (3) `AC-07` named a nonexistent “focus receipt” — now
  `browser.lastReceipt` with the holder-plus-browser test shape (verified
  against `TargetsBrowserState.accept` and `OnboardingScreen` wiring).
- **Recommended findings (accepted):** UI-triggered reads with pure
  `advance()`; generic re-add sentence plus `message()` composition;
  no-base seeding skips pending intents; content-equality
  (domains plus group name) skips write and signal. **Optional
  (accepted):** `D2` cross-reference to `D11`; below-cap missing-domain
  silence recorded as an accepted rare limit.
- **Resolution:** brief amended; reviewer claims 3–4 independently
  verified in code before folding; ready for implementation.

## Result

- Schema/migration plus intent durability and drainer done; `:shared:jvmTest`
  green (486 tests, 0 failures).
- Pass-order core `PolicyReconciler.apply` holds the brief's shared write
  gate across read, merge, and apply. The gate existed in the brief (step 3,
  `D2` rationale) but was never implemented; without it a user save racing
  an apply could hit `REVISION_CONFLICT` and surface `operation_error_update`
  instead of waiting, so compare-and-set plus retry alone is not equivalent.
  `LocalTargetPolicyStore.withWriteGate` is backed by one `Mutex` in
  `SqlLocalTargetPolicyStore`; `SyncTargetPolicyStore.replace` uses it
  instead of its own lock and the reconciler holds it for the whole apply
  (never nested, so no reentrancy). The rewrite also fixed a latent receiver
  error (`projected.toReconcileOutcome()` now
  `projection.toReconcileOutcome()`, audit lives on `SyncProjection`).
- Detekt `TooManyFunctions` (max 11) resolved without suppressions:
  `AppleSyncAuthoring.skipped` is now top-level `isSkipped`;
  `PolicyReconciler` keeps 10 members with pure merge/projection/outcome
  helpers at file scope; SQL statement helpers moved to internal
  `SqlLocalPolicyStatements.kt`; iOS graph folds the driver provider into
  `provideDatabase` (`SqlDriver` has no other consumer).
- `:shared:jvmTest` (487 tests, incl. a write-gate mutual-exclusion test) and `:shared:detekt` green after the above.
- `D3` change signal done: `LocalTargetPolicyStore.policyChanges` defaults to
  `emptyFlow()` on the interface (fakes compile untouched), the SQL store
  `tryEmit`s only on successful `replaceWithBase` (reconciler applies; plain
  saves stay silent), the decorator forwards the flow. `TargetsViewModel`
  re-reads silently through a new signal lifecycle (no `isLoading` flash,
  existing editor reconcile; `onStart` seed keeps `combine` alive on the
  empty default), `SessionViewModel` merges the signal into its silent
  targets lifecycle (setup draft untouched). No new member functions (both
  view models sit at the 11-function gate).
- `D9` onboarding derivation done: `refreshSavedWebsites()` reads the real
  policy on website/summary step entry (UI-triggered effect, `advance()`
  stays pure, zero-reads test stands); post-save counts derive from the
  replace result or the already-read snapshot instead of `+=`; the
  focus-clearing effect keys on `browser.lastReceipt` with `addedCount > 0`,
  so a remote arrival never dismisses the keyboard. No live subscription.
- `:shared:jvmTest` (493 tests: +2 signal emit/silence, +1 decorator
  forwarding, +1 targets silent re-read, +1 session refresh with surviving
  draft, +1 join-fetch-skip-summary) and `:shared:detekt` green.
- Policy pass wired into `AppleSync` (`D2`/`D4`/`D11`): the exchange is split
  into `publishPending` + `consume` legs; `runExchange` reads the base,
  drains pre-consume only when a base exists, publishes, consumes, then
  `runPolicyPhase` seeds and drains post-consume on no-base passes, decides
  the group name, runs the second publish leg, and applies last. Outcomes
  map to status plus the new `SyncAttentionReason` (`LOCAL_CAPACITY`,
  `SHARED_CAPACITY`); corruption, conflict, and storage failure clear the
  reason and report action required or retryable, removal clears the reason.
  Leg helpers are top-level to respect the 11-function and 3-return gates.
- First no-base passes record the base and advance the revision even with no
  visible change (brief: the base is written only in the replace
  transaction); savers must use the latest snapshot revision, and the `D3`
  signal keeps view-model snapshots fresh. The policy mutex is per store
  instance; production shares one instance via the graphs, and the harness
  tests now share `harness.sqlPolicy`/`harness.syncPolicy` for the same
  single gate.
- Brief-mandated test inversions: the reordered-bundles case now asserts the
  replica and the local policy both change (with base + `COMPLETED`); the
  unlinked edit and the pre-removal save are authored to the (new) workspace
  instead of dropped (`AC-03`/`D11`).
- `A3`/`A4` reason-aware copy done: `message()` takes the reason (local
  `D5` copy unchanged, shared copy plus the compare-and-re-add instruction);
  `summary()` untouched; the section, onboarding notice, and announcements
  inherit the reason; `D8` lands as a Session caption when unlinked and a
  websites-clause caption in onboarding; design-review-only preview samples
  cover both reasons at normal and large text.
- Two-harness convergence diagnosed and fixed. Both harnesses shared one
  deterministic crypto stream, so the peers minted colliding bundle/author
  identifiers with divergent contents (wall-clock-driven HLC) and the
  protocol correctly reported `REPLAY_CONFLICT` on the peer's first fetch
  (observed: both mailbox identifiers inside the peer's accepted keys with
  unequal bytes). Fixture-only fix: `FakeSyncCryptoProvider` takes a
  `streamSeed` (default 0 keeps byte-identical output), the harness takes an
  optional provider, every second harness in the convergence file uses seed
  `0x5A`, and the union case expects 4 mailbox bundles. No product-code
  change: identical identifiers with different bytes must stay a replay
  conflict.
- `D4` stale-projection base lag fixed in `runPolicyPhase`: the projection
  is now re-read after the group-name decision and its republish leg, so the
  base recorded by `apply` includes a name authored in the same pass.
  Previously the base lagged by one pass and a local rename in the window
  was clobbered as a remote change.
- `./gradlew quality` green: ktlint import order and entry bracing fixed in
  the touched test and main files (no suppressions; one formatter-induced
  unused-`Unit` rewritten as `if`), `verifySqlDelightMigration` and
  `iosSwiftTest` inside the gate pass.
- Verify-posato recipes updated per `AC-06`: the sync recipe asserts union
  convergence after each **Sync now**, the websites recipe states that saved
  websites synchronize across linked devices.
- Threat-model owner rows `A-04` (policy base and intents) and `T-03`
  (base-kept converged apply) extended with `SYNC-011` in the same change.
- Review-fix round (7 P1 implemented, P1-5 merge gate pending the maintainer's physical runs, P2 triaged): `publish`
  clears the stale capacity reason with a sub-second refusal-then-zone-missing
  test over a fabricated full projection (one transaction, no engine authoring);
  capacity classifies before the bounded-model conversion over a new
  unbounded merge representation (`AppliedWorkspaceFull` renamed to
  `RefusedWorkspaceFull`, first-attempt/retry share one merge path) with 4
  direct tests; the established merge applies the reduced winner for
  already-authored losing edits with 2 convergence tests; first-link seeding
  holds the write gate and skips removal-pending domains with a deterministic
  interleaving regression; the lost-removal case runs end to end across a
  same-database reopen plus a during-fetch intent-row extension; `D9`/`D10`/
  `D11` and the `D5` correction relabelled `inferred`; `DESIGN.md`, the wiki
  topic, and the sync/application-group skill recipes updated with no second
  wiki-log entry.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Reconciler merge cases over fakes | pass | `:shared:jvmTest`, 510 tests, 0 failures; `iosSimulatorArm64Test`, 508 tests, 0 failures |
| Two-harness convergence and inverted persistence case | pass | `AppleSyncConvergenceTest`, 10/10 green (incl. refusal reason-clearing in 0.75 s JVM / 1.0 s iOS, 2 losing-edit cases) |
| `7.sqm` migration verification | pass | `verifySqlDelightMigration` inside `./gradlew quality` |
| `./gradlew quality` | pass | `BUILD SUCCESSFUL`, no new suppression |
| Simulator website and first-install fixtures | pass | `posato-control` sim runs under `build/verification/runs/`: `first-install-skip`, `add-website`, `website-edit`, `remove-website`, `first-install` all `ok:true`, scenarios unchanged |
| Physical bidirectional website convergence | pending | needs maintainer Mac + iPhone |
| Physical application group convergence, selections local | pending | needs maintainer Mac + iPhone |
| Physical pre-link backfill after removal and re-link | pending | needs maintainer Mac + iPhone |
| Physical offline retry | pending | needs maintainer Mac + iPhone |
| Threat-model closeout statement and owner rows | pass | `A-04`/`T-03` carry `SYNC-011`; closeout: convergence adds no new transport, key, or diagnostic surface — base/intents stay app-private, bundles stay encrypted, status copy carries no domain values |

## Blockers and accepted risks

- `D1` to `D8` accepted by the maintainer on 2026-09-10; start at step 2.
- iOS reapplies a converged change inside a session only on poll loss,
  retry, or relaunch; linking converges both devices to the union of their
  websites and re-adds a website a peer removed before this device linked.
- A rejected bundle still pins the cursor and now delays visible
  convergence; remotely originated websites stay local after **Remove
  workspace** without provenance, as the removal copy already says.

## Final

- **Status:** pending
- **Outcome:** pending
