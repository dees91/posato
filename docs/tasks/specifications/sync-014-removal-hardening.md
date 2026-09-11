# `SYNC-014`: Refuse to re-adopt a removed workspace so a quick re-link never lands in a zone under deletion

- **Review tier:** `high-risk`
- **Tier reason:** The change touches the deterministic bootstrap protocol
  around destructive removal (ADR 0007), adds a persistent store with a
  migration, and its wrong shape could either strand a device in a workspace
  CloudKit is deleting or, on the candidate path, delete the device's own
  fresh key item before adopting a doomed workspace. The physical proof needs
  two devices, workspace removal, timed re-links, and an Apple-side purge
  nobody can force.
- **Dependencies:** completed `SYNC-009` (bootstrap, removal), `SYNC-010`
  (**Remove workspace** control and its established check), `SYNC-011`
  (removal clears intents and the applied base), `ONBOARDING-002` (the
  join-only continuation), `QUALITY-002` and `QUALITY-004` (driver)
- **Integration group:** `PR-REMOVAL-HARDENING`
- **Authority:** `SYNC-014` in MVP roadmap revision 13 (wave P4/W4.3b, added
  after the `SYNC-011` physical gate);
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
  (step 4: a found anchor is adopted only through its exact Keychain item;
  step 9: a losing candidate deletes exactly its own item, then reads the
  winner's; "Removing a workspace is a separate explicit destructive action
  ... must verify absence independently"; "No invalid input replaces the last
  established binding"; the fixed zone and fixed anchor record are "the sole
  concurrent-first-run arbiter", unchanged here);
  [ADR 0002](../../decisions/0002-synchronization-trust-and-workspace-modes.md)
  (`open`: no controllable propagation time; waiting and retry must be
  exposed and verified physically);
  `SYNC-010` decision `D2` (removal deletes the exact zone and key item,
  keeps local websites; the peer reports action required until its own
  removal) and the sync recipe step 7 ("The peer's old anchor must never
  delete the newly established zone");
  [the threat model](../../security/apple-mvp-threat-model.md) (`T-04`
  "never a replacement key, parallel workspace"; `T-14` idempotent deletion
  with "independent absence ... checks", residual "Provider or backup
  retention may outlive immediate deletion"; the persistent-store change
  rule);
  [`DESIGN.md`](../../../DESIGN.md) (removal copy: "The removing device can
  then use **Sync with iCloud** to start again"; the seven statuses; no time
  claim); and the `SYNC-011` execution record (the observation: re-linking
  within minutes of removal plus establish adopted a stale key and reported
  completed inside a ghost zone; settling about eight minutes healed it)

## Outcome

After **Remove workspace**, this device never adopts the workspace it
removed: while CloudKit still surfaces the old anchor and the old key item
is still readable, **Sync with iCloud** reports that it did not finish and
creates, deletes, and adopts nothing; once the old anchor is gone it
establishes a fresh workspace, and once a peer's new anchor is visible it
joins that one; a persisted candidate or a consented join continuation never
adopts or cleans up against a removed workspace; the recipe states the settle
rule and the ghost symptom; and the seven statuses keep their meaning.

## Boundaries

- Mechanism (`observed` code facts): both platforms read the zone and the
  anchor by identifier and delete the zone with a modify operation; the zone
  name and the anchor record identifier are fixed; `deleteZoneAndVerifyAbsent`
  checks only that the zone fetch says missing right afterwards; each
  establish mints a fresh workspace identifier and key item; Keychain
  deletion is verified locally while iCloud Keychain propagates on its own
  schedule. `hypothesis`: CloudKit purges a deleted zone's records after the
  delete returns, so re-creating the same zone identifier within minutes can
  surface the old anchor record again, and the old key item can still be, or
  come back, in the local iCloud Keychain; the establishing device then adopts
  the removed workspace instead of creating one, publishes into a zone under
  deletion, and a peer joins the same ghost until the purge completes and the
  zone vanishes. The task reproduces before it fixes (`D4`).
- The guard is a durable, device-local tombstone of removed workspace
  identifiers (`D1`): removal records the cleared workspace identifier in the
  same transaction that clears the established row, for every removal
  outcome that clears it (ready, zone missing, different anchor); the store
  keeps the most recent identifiers up to a small bound and never
  synchronizes, diagnoses, or displays them; a workspace identifier is not
  key material and the row holds nothing else.
- Every adoption path consults the tombstone before any read of the key item
  and before any cleanup: a fresh attempt that finds a tombstoned anchor
  returns retryable, creates no zone, candidate, anchor, or item, and leaves
  the store at none; a persisted candidate that meets a tombstoned winning
  anchor returns retryable and keeps its own candidate and item (it never
  runs the losing-candidate cleanup against a ghost); the join-only
  continuation returns unchanged for a tombstoned anchor. The explicit press
  keeps the accepted protocol for every non-tombstoned anchor. A tombstoned
  anchor that later disappears leads to the normal fresh establish; a peer's
  new anchor leads to the normal join.
- Status and copy (`D2`): the refusal reports retryable through the existing
  unlinked copy, so the person retries with the same action after a while;
  no sentence names a duration, the purge, or CloudKit. The recipe, not the
  app, states the settle rule.
- Removal on the peer is unchanged: a different anchor still permits only
  the old key item's deletion, and a zone the peer sees as its own removed
  workspace is a zone under deletion anyway. That the peer's by-identifier
  reads could return a stale anchor equal to its own while a fresh anchor
  exists stays an accepted limit, stated in the record, because the reads
  are by identifier and the observation showed the resurrected anchor, not a
  hidden fresh one.
- Write surface: `shared/src/commonMain/**/feature/sync/bootstrap/**`
  (`SqlBootstrapStore` and `BootstrapStore` for the tombstone, the removal
  transaction, `BootstrapCoordinator`, `BootstrapAnchorPhase`,
  `BootstrapJoinPhase`), `SyncBootstrap.sq` plus `migrations/8.sqm`, the
  fakes and tests (`FakeBootstrapPorts`, `BootstrapCoordinatorTest`,
  `BootstrapJoinTest`, the `AppleSync` harness cases, a migration test),
  ADR 0007 (a dated `user-confirmed` amendment on refusing removed
  workspaces), the `verify-posato` sync recipe (step 7 settle rule, a
  ghost-zone gotcha, the quick re-link row), the threat model (`SYNC-014`
  added to the `T-04` and `T-14` owner rows), the cross-device wiki topic
  (the observed bullet gains the follow-up and, after the run, the
  reproduction result), and the wiki log.
- Non-goal: changing the zone name, the anchor record identifier, or the
  concurrent-first-run arbiter; an anchor creation-date check (recorded as a
  later option if the reproduction shows fresh-install devices without a
  tombstone hitting the ghost); any timer, poll, or in-app wait; a new status
  category or a reason string; total-key-loss recovery; the unchanged
  `SYNC-012` scope.

## Acceptance

- `AC-01` — Over the fakes, after a removal the store holds the removed
  workspace identifier; a fresh attempt that finds that anchor while the old
  key item is still readable returns retryable with zero zone saves, anchor
  creates, key creates, key deletes, and store writes; once the anchor read
  returns missing the next attempt establishes a fresh workspace; once the
  anchor read returns a different, non-tombstoned anchor the next attempt
  joins it.
- `AC-02` — A persisted candidate meeting a tombstoned winning anchor returns
  retryable, keeps its candidate row and its own key item, and deletes
  nothing; the join-only continuation returns unchanged for a tombstoned
  anchor; the explicit press on any non-tombstoned anchor behaves as the
  existing `BootstrapCoordinatorTest` cases require, unchanged.
- `AC-03` — The tombstone is written in the transaction that clears the
  established row for ready, zone-missing, and different-anchor removals,
  survives relaunch, is bounded to the most recent identifiers, and never
  appears in status copy, logs, diagnostics, or evidence; migration `8.sqm`
  is verified with existing rows intact.
- `AC-04` — Physical: on the Mac, **Remove workspace** followed within one
  minute by **Sync with iCloud** reports retryable while CloudKit still
  surfaces the old anchor and never reports completed in the removed
  workspace; after settling, the same action establishes a fresh workspace
  and the iPhone joins it and receives a website added while unlinked. When
  the purge is already complete at the first re-link, the row records a
  direct fresh establish and the guard stays proven by `AC-01`; two timed
  attempts are made before that fallback is recorded.
- `AC-05` — `./gradlew quality` passes with no new suppression; the sync
  recipe carries the settle rule and the ghost gotcha; the threat-model owner
  rows are updated in the same pull request.

## Verification

- `commonTest`: `BootstrapCoordinatorTest` with the fake cloud port scripted
  to keep returning the removed anchor after `deleteZoneAndVerifyAbsent` and
  the fake key port still holding the removed item (fresh attempt refused,
  later missing anchor establishes, later foreign anchor joins); the
  candidate path against a tombstoned winner; `BootstrapJoinTest` for the
  continuation; `SqlBootstrapStore` tests for the tombstone write inside the
  clearing transaction, the bound, and relaunch survival; `AppleSyncTest`
  removal-then-relink through the harness with the scripted resurrection
  (status retryable, no established row, no accepted bundle); the migration
  test for `8.sqm`.
- `jvmTest` and `iosTest` over the real graphs: construction touches no
  provider; existing removal and second-install cases unchanged.
- Physical, attended, signed Mac and iPhone on one account, both linked at
  the start, with wall-clock timestamps recorded for each press: Mac
  **Remove workspace**, add `design-proof-15.example` while unlinked, then
  **Sync with iCloud** within one minute; record the status; repeat the press
  each minute until it establishes; iPhone **Remove workspace** then **Sync
  with iCloud**; confirm the join and the arrival of the website. If the
  first attempt establishes directly, repeat the whole sequence once; if
  both establish directly, record the fallback of `AC-04`. Mac evidence:
  `select count(*) from sync_bootstrap_state` and the established-column
  null check per the recipe; iPhone evidence: status text and the website in
  Paused items. Cleanup removes the fixture domain only.
- Closeout statement for the threat model: the tombstone holds workspace
  identifiers only (already stored while established), adds no transport,
  key, or diagnostic surface, and strengthens `T-04` and `T-14`; the model
  gains `SYNC-014` on those rows.
- `./gradlew quality`, `git diff --check`, and the scoped secret and path
  scan.

## Decisions or blockers

- `D1` guard shape. Recommended: the device-local tombstone consulted by
  every adoption path, because it is deterministic, needs no clock or Apple
  behavior claim, and closes the observed case on every device that
  performed the removal. Alternatives: a docs-only settle rule (no product
  guard; the ghost stays reachable); comparing the anchor's CloudKit creation
  date with the zone save (covers fresh installs too, but rests on a
  provider field's semantics after a purge and is recorded as a later
  option); unique zone or anchor identifiers per establish (closes the
  class but changes the accepted arbiter and the fixed-zone removal, a
  separate ADR decision).
- `D2` refusal status. Recommended: retryable with the existing unlinked copy
  and no new string, because the attempt truly did not finish and the same
  action retries. Alternative: an action-required reason naming the pending
  removal, which adds copy that must avoid a time claim for a wait the app
  cannot measure.
- `D3` tombstone scope. Recommended: per device, written for every removal
  outcome that clears the established row, bounded to the last 32
  identifiers, never expiring on time, never synchronized. Alternative: a
  time-to-live, which needs a wall-clock claim the protocol avoids.
- `D4` evidence. Recommended: reproduction first with timed presses and two
  attempts, the guard merged on the fake-port cases plus the recorded
  observation when the purge does not reproduce, and the settle rule in the
  recipe either way. Alternative: block the merge on a physical reproduction,
  which depends on Apple-side timing.
- Physical gate: the maintainer's Mac and iPhone on one account; removal and
  re-link are attended and destructive for the linked workspace; reserved
  synthetic domain only; the run leaves both devices linked to the newest
  workspace.
