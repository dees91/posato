# `SYNC-015`: Remove a workspace by deleting its records and keeping the zone

- **Review tier:** `high-risk`
- **Tier reason:** The change rewrites the destructive removal step of the
  bootstrap protocol on both platforms, changes what a linked peer sees
  after removal, amends ADR 0007, and its wrong shape could delete records
  outside the workspace, leave bundles behind that a new workspace would
  consume, or strand the peer without a removal path. The physical proof
  needs the timed re-link that lost a workspace in the `SYNC-014` gate.
- **Dependencies:** completed `SYNC-014` (tombstone, timed recipe, Run 1
  observation), `SYNC-010` (exchange, removal control), `SYNC-009` (zone and
  anchor protocol)
- **Integration group:** `PR-RECORD-REMOVAL`
- **Authority:** `SYNC-015` in MVP roadmap revision 14 (wave P4/W4.3c);
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
  (removal "may delete only the exact Posato zone and exact known
  workspace-key items, must verify absence independently, and must preserve
  unrelated CloudKit and Keychain data", amended here to records inside the
  exact zone; "definitive anchor difference, anchor absence, or zone absence
  after establishment stop synchronization"; the fixed zone and anchor
  record stay the arbiter; the `SYNC-014` amendment);
  [the MVP scope](../../product/mvp-scope.md) ("without manual state
  repair"; no delivery-time promise);
  `SYNC-010` decision `D2` (removal keeps local websites; the peer reports
  action required until its own removal) and the sync recipe steps 7 and 8;
  [the threat model](../../security/apple-mvp-threat-model.md) (`T-14`
  "Exact namespaces and selectors; explicit destructive intent; idempotent
  deletion; independent absence and outside-scope preservation checks", its
  purge-window residual, `T-04`, `A-04`, `A-06`);
  [`DESIGN.md`](../../../DESIGN.md) (removal copy: deletes the iCloud
  workspace and undelivered device changes, keeps local websites, "The
  removing device can then use **Sync with iCloud** to start again"); and the
  `SYNC-014` execution record (Run 1: a fresh workspace established about
  two and a half minutes after removal was lost to the purge; Run 2 passed)

## Outcome

**Remove workspace** deletes the anchor and every bundle record inside the
exact Posato zone and deletes the workspace key, verifies that no record
remains, and keeps the zone; a linked peer sees the anchor gone, reports
action required, and can still remove its own workspace; **Sync with iCloud**
right after removal, on either device and while the peer is still linked,
establishes a workspace that survives, exchanges normally, and delivers a
website added while unlinked; no zone is ever deleted by the app, so no
late zone purge exists; the `SYNC-014` tombstone keeps working unchanged;
and the recipe's settle rule is retired once the timed proof passes.

## Boundaries

- Removal (`D1`): after the established check says ready, the mailbox port
  deletes the exact anchor record and every bundle record of the exact zone
  under the established binding, in bounded batches, idempotently (a record
  already gone is success), then verifies absence independently: the anchor
  read says missing and a changes fetch from the first page yields no bundle.
  Only then the workspace key is deleted and the local state cleared as
  today, including the tombstone. An account failure, a retryable or
  unknown provider result, or a remaining record stops the destructive
  steps with the existing retryable or action-required outcomes and keeps
  the established row, so the person can retry. The zone `PosatoSyncV1` is
  never deleted; an empty zone holds no data class.
- Peer semantics (`D2`): a linked device whose established check finds the
  zone but no anchor reports a new `ANCHOR_MISSING` established status,
  shown as the existing action-required copy; removal on that device treats
  it like `ZONE_MISSING`: it skips the record deletion (nothing of its own
  remains), deletes its own key item, clears local state, and tombstones the
  workspace. A different anchor keeps today's `DIFFERENT_ANCHOR` behavior.
  Exchange, publish, and the join-only continuation treat `ANCHOR_MISSING`
  exactly as anchor absence today: they stop without deleting or merging
  pending work.
- Deleted-record entries in the changes feed (`D3`): both native backends
  already collect deletions; the adapters map a deletion entry to a
  progress-only page (no bundle, cursor advances), which the consume loop
  already accepts. A freshly established workspace that reads a zone with
  deletion tombstones in its changes feed therefore completes without
  accepting anything.
- Re-establish (`D4`): after removal the zone is found, the anchor is
  missing, and the existing fresh-attempt path mints a new workspace and
  creates the anchor in the existing zone; no zone save happens. The
  `SYNC-014` tombstone stays as written; a resurrected old anchor can no
  longer appear, but the guard costs nothing and covers provider anomalies.
- Copy (`D5`): the removal confirmation and the seven statuses stay as
  written; "deletes the shared workspace from iCloud" remains true because
  every record of the workspace is deleted. No new string.
- Write surface: `shared/src/commonMain/**/feature/sync/mailbox/MailboxPort.kt`
  and `MailboxTypes.kt` (the record-deletion operation replacing
  `deleteZoneAndVerifyAbsent`, its result type), `feature/sync/bootstrap/`
  (`AppleWorkspaceRemoval`, `EstablishedWorkspaceCheck`, `AppleSync` status
  mapping, `BootstrapCoordinator` where `EstablishedStatus` is matched),
  `shared/src/jvmMain/**/feature/sync/macos/MacOsMailboxAdapter.kt` and the
  companion protocol (`macosSyncCompanion/Sources/PosatoMacOSSync/`:
  `Protocol.swift` request code, `MailboxRequestHandler.swift`,
  `CloudStore.swift`, `CloudBackend.swift`),
  `shared/src/iosMain/**/feature/sync/data/IosCloudKitMailboxProvider.kt`
  and `iosApp/iosApp/CloudKitMailboxProvider.swift` plus its live backend,
  the fakes and tests (`FakeMailboxPort`, `SharedFakeMailboxPort`, the
  harness removal cases in `AppleSyncPersistenceTest` and `AppleSyncTest`,
  adapter tests on both targets, Swift tests where the companion has them),
  ADR 0007 (dated `user-confirmed` amendment), the threat model (`T-14`
  control and residual columns, `A-06`), the sync recipe (steps 7 and 8 and
  the ghost gotcha), the cross-device wiki topic, and the wiki log.
- Non-goal: a new zone identity per establish (kept as the fallback if the
  timed proof fails); deleting the zone in any path; changing the
  concurrent-first-run arbiter, the anchor record, the bundle format, or the
  key-item protocol; the `SYNC-014` tombstone; removing the zone when it is
  empty; the `SYNC-012` scope, which may run in parallel because its write
  surface (exchange pass, session stores, `feature/session`) is disjoint;
  the shared harness, recipe, threat-model, and wiki files are merged by
  whichever task lands second.

## Acceptance

- `AC-01` — Over the fakes, removal on a ready workspace deletes the anchor
  and every bundle record, verifies absence, deletes the key item, and
  clears local state with the tombstone written; a remaining record, a
  retryable or unknown delete result, or an account failure stops before the
  key deletion and keeps the established row; a second removal after a
  partial one completes idempotently; no zone delete call exists anywhere.
- `AC-02` — Over the fakes, a linked peer whose check finds the zone and no
  anchor reports `ANCHOR_MISSING` as action required, publishes and accepts
  nothing, and its removal deletes only its own key item, clears local
  state, and tombstones the workspace; a different anchor keeps today's
  behavior.
- `AC-03` — Over the fakes, a changes page carrying only a deletion entry
  advances the cursor and accepts nothing; a fresh establish after removal
  saves no zone, creates the anchor in the found zone, and its first
  exchange completes.
- `AC-04` — Physical, both directions, signed Mac and iPhone on one
  account, both linked at the start, with the `SYNC-014` per-press capture:
  Run 1 shape (Mac **Remove workspace**, add `design-proof-16.example` while
  unlinked, **Sync with iCloud** within three minutes while the iPhone is
  still linked; iPhone shows action required, removes, links) and the
  both-removed shape; in both, the website arrives on the iPhone, and after
  a wait of at least ten minutes both devices still complete an exchange
  with the Mac holding one established row; the Mac's accepted count stays
  stable across repeat exchanges; then the devices reversed. A run that
  loses the website or the row is a failure, not a fallback.
- `AC-05` — Adapter tests on both targets prove the batch deletion, the
  absence verification, and the deletion-entry mapping against the native
  fakes; `./gradlew quality` passes with no new suppression; the recipe's
  settle rule is retired to a note once `AC-04` passes; the threat-model
  `T-14` control names record deletion and the purge-window residual is
  closed there.

## Verification

- `commonTest`: `AppleWorkspaceRemoval` over `FakeMailboxPort` (success,
  partial, remaining record, retryable, unknown, account changed,
  idempotent second pass); `EstablishedWorkspaceCheck` for `ANCHOR_MISSING`;
  `AppleSync` harness removal-then-relink with the peer's view (anchor gone,
  action required, peer removal, fresh establish in the found zone, first
  exchange completes); the existing removal, different-anchor, and tombstone
  cases unchanged; deletion-entry page in `AppleMailboxExchange`.
- `jvmTest` and `iosTest` adapter cases: the companion request and the iOS
  provider delete records in batches, report a remaining record, and map
  deletion entries; construction touches no provider.
- Physical, attended, per `AC-04`, recorded per press with the Mac counts
  (`sync_bootstrap_state`, `sync_removed_workspace`, `sync_accepted_bundle`)
  and the run directories; an attended CloudKit Console look at the zone
  after removal (records gone, zone present) is optional evidence.
- Closeout statement for the threat model: removal deletes only records of
  the exact Posato zone under the established binding and verifies absence;
  no zone purge exists, so the purge-window residual closes; the fresh
  install residual narrows to provider anomalies.
- `./gradlew quality`, `git diff --check`, and the scoped secret and path
  scan.

## Decisions or blockers

- `D1` removal shape. Recommended: delete records, keep the zone, verify by
  anchor read and first-page changes fetch, idempotent batches. Alternatives:
  keep deleting the zone and rely on the settle rule (the observed loss
  stays reachable); a new zone identity per establish (closes the class too
  but changes the arbiter, the schema, both adapters, and every recipe
  query; kept as the fallback if `AC-04` fails).
- `D2` peer status. Recommended: a distinct `ANCHOR_MISSING` established
  status with the existing action-required copy, treated like zone-missing
  by removal so the peer never needs manual repair. Alternative: keep
  mapping anchor absence to the generic action required, which today makes
  the peer's removal stop before clearing its state.
- `D3` deletion entries. Recommended: progress-only pages. Alternative:
  reject them, which pins the cursor of every workspace established after a
  removal.
- `D4` tombstone and settle rule. Recommended: keep the tombstone; retire
  the ten-minute settle rule to a historical note only after `AC-04` passes
  in both shapes and directions. Alternative: retire it on the design
  argument alone.
- Physical gate: the maintainer's Mac and iPhone on one account; removal
  and re-link are attended and destructive for the linked workspace;
  reserved synthetic domain only; the gate runs after or before the
  `SYNC-012` gate, never at the same time.
