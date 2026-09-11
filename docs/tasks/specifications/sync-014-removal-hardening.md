# `SYNC-014`: Refuse to re-adopt a removed workspace so a quick re-link never lands in a zone under deletion

- **Review tier:** `high-risk`
- **Tier reason:** The change touches the deterministic bootstrap protocol
  around destructive removal (ADR 0007), adds a persistent store with a
  migration, and its wrong shape could either strand a device in a workspace
  CloudKit is deleting or, on both candidate paths, delete the device's own
  fresh key item before adopting a doomed workspace. The physical proof needs
  two devices, workspace removal, timed re-links, and an Apple-side purge
  nobody can force.
- **Dependencies:** completed `SYNC-009`, `SYNC-010`, `SYNC-011`
- **Integration group:** `PR-REMOVAL-HARDENING`
- **Authority:** `SYNC-014` in MVP roadmap revision 13 (wave P4/W4.3b);
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
  (steps 2, 4, and 9 of the deterministic bootstrap; removal "must verify
  absence independently"; "No invalid input replaces the last established
  binding"; the fixed zone and anchor record stay the arbiter);
  [ADR 0002](../../decisions/0002-synchronization-trust-and-workspace-modes.md)
  (no controllable propagation time; waiting and retry verified physically);
  `SYNC-010` decision `D2` and the sync recipe step 7 (the peer's old anchor
  never deletes the newly established zone);
  [the threat model](../../security/apple-mvp-threat-model.md) (`A-04`
  bootstrap state, `T-04`, `T-14`, the persistent-store change rule);
  [`DESIGN.md`](../../../DESIGN.md) (removal copy, the seven statuses, no
  time claim); and the `SYNC-011` execution record (the observation: a
  re-link minutes after removal plus establish adopted a stale key and
  reported completed inside a zone that later vanished; it settled after
  about eight minutes)

## Outcome

After **Remove workspace**, this device never adopts the workspace it
removed: while CloudKit still surfaces the old anchor, **Sync with iCloud**
reports that it did not finish and adopts, deletes, and persists nothing
beyond the fixed zone save the protocol already makes; once the old anchor is
gone it establishes a fresh workspace that survives the purge; once a peer's
new anchor is visible it joins that one; a persisted candidate never runs its
losing-candidate cleanup against a removed workspace; the recipe states the
settle rule and the ghost symptom; and the seven statuses keep their meaning.

## Boundaries

- The guard is a durable, device-local tombstone of removed workspace
  identifiers (`D1`): removal records the cleared identifier inside the
  transaction that clears the established row, for every outcome that clears
  it (ready, zone missing, different anchor); the store keeps the most recent
  identifiers up to a bound enforced in that transaction, never synchronizes,
  diagnoses, or displays them, and clears them only with the database. A
  workspace identifier is not key material and the row holds nothing else.
  No legitimate re-join of a tombstoned workspace exists: every tombstoning
  outcome saw the anchor gone or replaced, and every establish mints fresh
  identifiers, so the tombstone never expires on time and is not cleared on
  an account change.
- The guard sits at two choke points, before any key-item read and before
  any deletion. Fresh attempt: after the zone step, a found anchor whose
  workspace identifier is tombstoned returns retryable, persists no
  candidate, creates no anchor or item, and leaves the store at none; the
  fixed zone save that ADR 0007 step 2 performs before the anchor read may
  already have happened and is not a violation. `adoptWinner`, reached from
  both `reconcileFoundAnchor` and the anchor-create conflict reconciliation:
  a tombstoned winner returns retryable before the losing-candidate item is
  deleted, so the candidate row and its own item stay intact. The join-only
  continuation refuses a tombstoned anchor as defense in depth (its retained
  anchor already passed a bootstrap). The own-anchor confirmations, the
  established check, and the established key read need no guard. Every
  non-tombstoned anchor keeps the accepted protocol.
- Status and copy (`D2`): the refusal reports retryable through the existing
  unlinked copy; no sentence names a duration, the purge, or CloudKit. The
  recipe, not the app, states the settle rule and the ghost gotcha, including
  the honest recovery for a ghost that never purges: any device that still
  reads ready on it presses **Remove workspace** again.
- Removal on the peer is unchanged: a different anchor still permits only
  the old key item's deletion. A stale by-identifier read on the peer stays
  an accepted limit recorded in the record; the observed shape and the
  conditional anchor create both point at the resurrected record, not at a
  hidden fresh one.
- Write surface: `shared/src/commonMain/**/feature/sync/bootstrap/**`
  (`SqlBootstrapStore` and `BootstrapStore`, the removal transaction,
  `BootstrapCoordinator`, `BootstrapAnchorPhase`, `BootstrapJoinPhase`),
  `SyncBootstrap.sq` plus `migrations/8.sqm`, the fakes and tests
  (`FakeBootstrapPorts`, `BootstrapCoordinatorTest`, `BootstrapJoinTest`,
  `SqlBootstrapStoreTest`, the `AppleSync` harness cases, a migration test,
  `BootstrapRedactionTest` if a carrier type is added), ADR 0007 (a dated
  `user-confirmed` amendment), the `verify-posato` sync recipe (step 7
  settle rule and bounded re-link steps, a ghost-zone gotcha), the threat
  model (`SYNC-014` on the `A-04`, `T-04`, and `T-14` owner rows; the
  fresh-install limit in the `T-14` residual), the cross-device wiki topic,
  and the wiki log.
- Non-goal: changing the zone name, the anchor record identifier, or the
  concurrent-first-run arbiter; an anchor creation-date check (a later
  option if a fresh install without a tombstone is shown to hit the ghost);
  any timer, poll, or in-app wait; a new status category, reason, or string;
  the unchanged `SYNC-012` scope, which serializes after this task because
  both touch the schema, the harness, and the sync recipe.

## Acceptance

- `AC-01` — Over the fakes, after a removal the store holds the removed
  identifier. A fresh attempt against the resurrected anchor is refused in
  both shapes: zone missing then created then the tombstoned anchor found
  (exactly one zone save), and zone found then the tombstoned anchor found
  (no zone save); both with zero candidate persists, anchor creates, key
  creates, key deletes, and a store still at none, whether or not the old
  key item is still readable. Once the anchor read returns missing the next
  attempt establishes fresh; once it returns a non-tombstoned anchor the next
  attempt joins.
- `AC-02` — Both candidate paths are refused before cleanup: a persisted
  candidate meeting a tombstoned foreign anchor, and a fresh mint whose
  anchor create conflicts with the resurrected record, return retryable with
  the candidate row and the own item intact and zero deletes; the join-only
  continuation returns unchanged; every existing `BootstrapCoordinatorTest`
  case is unchanged.
- `AC-03` — The tombstone is written in the clearing transaction for ready,
  zone-missing, and different-anchor removals, survives relaunch, evicts the
  oldest beyond its bound inside that transaction, and never appears in
  status copy, logs, diagnostics, or evidence; migration `8.sqm` is verified
  with existing rows intact.
- `AC-04` — Physical, with the observable facts only: after **Remove
  workspace** and re-link presses within the window, the Mac never reports
  completed and never gains an established row while the old anchor is
  surfaced (tombstone count query at one, `sync_bootstrap_state` at zero);
  after settling, the same action establishes, the iPhone joins, the website
  added while unlinked arrives, and after a further wait past the observed
  settle both devices still complete an exchange with the Mac holding one
  established row. When the first re-link establishes directly, the sequence
  is repeated once in the both-removed variant; two direct establishes are
  recorded as the fallback, with the refusal proven by `AC-01` and `AC-02`.
- `AC-05` — `./gradlew quality` passes with no new suppression; the sync
  recipe carries the settle rule, the bounded re-link steps, and the ghost
  gotcha; the threat-model rows are updated in the same pull request.

## Verification

- `commonTest`: `BootstrapCoordinatorTest` with the fake cloud port scripted
  for the two fresh-attempt shapes, the candidate path against a tombstoned
  winner, and the conflict path (missing, mint, persist, conflict, tombstoned
  found); `BootstrapJoinTest` for the continuation; `SqlBootstrapStoreTest`
  for the write inside the clearing transaction, the bound, and relaunch;
  `AppleSyncTest` removal-then-relink through the harness with the scripted
  resurrection (retryable, no established row, no accepted bundle); the
  migration test for `8.sqm`.
- `jvmTest` and `iosTest` over the real graphs: construction touches no
  provider; existing removal and second-install cases unchanged.
- Physical, attended, signed Mac and iPhone on one account, both linked at
  the start; per press record the wall-clock time, the device, the status,
  whether the peer was still linked, the press count, and the Mac counts
  (`sync_bootstrap_state`, the tombstone table, `sync_accepted_bundle`).
  Run 1 (peer still linked): Mac **Remove workspace**, add
  `design-proof-15.example` while unlinked, **Sync with iCloud** within one
  minute, then at most four more presses one minute apart; when it
  establishes, iPhone **Remove workspace** then **Sync with iCloud**; confirm
  the join and the website; wait at least ten minutes; **Sync now** on both
  and confirm completed with one established row on the Mac. Run 2 (both
  removed first): the same with the iPhone removed before the Mac's re-link.
  If the presses run out without establishing, record blocked with the
  timestamps. Optional attended check: the CloudKit Console showing whether
  the `workspace` record still exists during the window. Cleanup removes the
  fixture domain only; both devices end linked to the newest workspace.
- Closeout statement for the threat model: the tombstone holds workspace
  identifiers only, adds no transport, key, or diagnostic surface, and
  strengthens `T-04` and `T-14`; `SYNC-014` joins the `A-04`, `T-04`, and
  `T-14` owner rows and the fresh-install residual is recorded.
- `./gradlew quality`, `git diff --check`, and the scoped secret and path
  scan.

## Decisions or blockers

- `D1` decided (`user-confirmed`, 2026-09-11): the device-local tombstone at the
  two choke points, because it is deterministic, needs no clock or Apple
  behavior claim, and closes the observed case on every device that performed
  the removal. Alternatives: a docs-only settle rule (no product guard); the
  anchor creation-date comparison (covers fresh installs but rests on a provider
  field after a purge; kept as a later option); unique zone or anchor
  identifiers per establish (closes the class but changes the accepted arbiter
  and the fixed-zone removal, a separate ADR decision).
- `D2` decided (`user-confirmed`, 2026-09-11): retryable with the existing
  unlinked copy and no new string, because the attempt truly did not finish and
  the same action retries; a retry hint in that copy would be a `DESIGN.md` copy
  decision, not a status change. Alternative: an action-required reason naming
  the pending removal, which needs copy that avoids a time claim the app cannot
  measure.
- `D3` decided (`user-confirmed`, 2026-09-11): per device, keyed by workspace
  identifier, written for every clearing removal outcome, bounded to the last 32
  with the oldest evicted in the same transaction, never expiring, not cleared
  on account change, cleared only with the database, never synchronized.
  Alternative: a time-to-live, which needs a wall-clock claim the protocol
  avoids.
- `D4` evidence and mechanism. The mechanism is a hypothesis with three
  candidates the runs must separate: a server-side purge re-attached by the
  same-identifier zone save (predicts a conflicting anchor create), the old key
  item returning through iCloud Keychain from the still-linked peer before the
  deletion propagates, or a stale by-identifier read (the only model under which
  the peer could delete a fresh zone). Decided (`user-confirmed`, 2026-09-11):
  the two timed runs above with bounded presses, the guard merged on the
  fake-port cases plus the recorded observation when the purge does not
  reproduce, and the settle rule in the recipe either way. Alternative: block
  the merge on a physical reproduction, which depends on Apple-side timing.
- Physical gate: the maintainer's Mac and iPhone on one account; removal and
  re-link are attended and destructive for the linked workspace; reserved
  synthetic domain only.
