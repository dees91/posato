# `SYNC-010`: Publish and consume pending encrypted bundles with truthful sync status and retry

- **Review tier:** `high-risk`
- **Tier reason:** First change that moves encrypted operations between the two
  devices through the person's private CloudKit database, reads the workspace
  key on a user path, and deletes a workspace from iCloud and the
  synchronizable Keychain. A wrong step acknowledges a bundle the cloud never
  stored, accepts a bundle under a switched account, advances a cursor past
  unaccepted work, publishes into a workspace this device never joined, leaks
  key bytes through a graph or a log, or deletes another workspace's zone.
- **Dependencies:** completed `SYNC-009` (`AppleBootstrap` facade, both graphs,
  the **Sync with iCloud** control), `SYNC-002` and `SYNC-013`
  (`SyncOperationCore`, `SyncWriter`, `SqlSyncReplicaStore`), `SYNC-007` and
  `SYNC-008` (the two mailbox adapters and the shared `MailboxTypes`
  vocabulary), `MODEL-001` (the local exact-domain slice whose edits feed the
  outbox under `D1`), `QUALITY-002`, `QUALITY-004`, and `QUALITY-005` (driver)
- **Integration group:** `PR-CLOUDKIT-SYNC`
- **Authority:** `SYNC-010` in MVP roadmap revision 12 (wave P3/W3.7; this
  task does not amend the roadmap),
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
  (ongoing mailbox exchange runs between the binding preflight and postflight;
  "A failed check leaves pending work and the last accepted cursor and engine
  state unchanged"; "An account-binding failure … authorizes no … fetched-
  bundle acceptance, cursor advancement, engine-state acceptance, or
  publication acknowledgement"; "definitive anchor difference, anchor absence,
  or zone absence after establishment stop synchronization"; "Removing a
  workspace is a separate explicit destructive action"; the `SYNC-010`
  evidence clause: "an account switch exposes no fetched bundle to common
  code, advances no cursor or accepted engine state, acknowledges no sent
  bundle, and preserves and restages unchanged pending work only after the
  established binding returns"),
  [ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md)
  (the 32-byte workspace key is the transport key from which each bundle key
  is derived; publishing never edits a bundle; duplicates are idempotent;
  "only then advance transport progress"; retry reuses the same immutable
  bytes; an unexplained replica change freezes the writer),
  [ADR 0002](../../decisions/0002-synchronization-trust-and-workspace-modes.md)
  ("The product must expose waiting and retry behavior"; removal is not a
  Posato-level revocation of an Apple-trusted installation),
  the [MVP scope](../../product/mvp-scope.md) ("Delivery is best-effort and
  retryable; the product does not promise device wake-up, eventual delivery,
  or a delivery time"),
  [`DESIGN.md`](../../../DESIGN.md) (the seven status distinctions are an
  accepted future contract; the current control reports bootstrap outcomes
  only; amended by this task per `D3`),
  [the diagnostics policy](../../security/diagnostics-and-support-data.md)
  (user-visible status is product state with one stable category and next
  action, never a timeline or a count of policy values),
  and the `SYNC-009` decisions `D2` and `D3` (removal, its port method, and
  its consent surface belong here; `WorkspaceKeyValue` stays `internal`)

## Outcome

On the linked development-signed Mac and iPhone, a bundle committed to the
local outbox on one device is published as one immutable CloudKit record and
accepted into the other device's replica from the last accepted cursor, in
both directions, with the sync section reporting only what the local attempt
established; an account failure before either leg acknowledges, accepts, and
advances nothing; and an explicit destructive action removes exactly this
workspace's zone and known key items so a fresh **Sync with iCloud** on the
removing device starts a new workspace without a console step.

## Boundaries

- Define the common mailbox port in `commonMain` over the existing
  `MailboxTypes` vocabulary (publish one bundle, fetch one bounded change page
  from an opaque cursor, delete the exact zone and verify absence) and make
  `MacOsMailboxAdapter` and `IosMailboxAdapter` implement it. No new record
  type, field, wire layout, payload bound, or protocol version;
  `PosatoEncryptedBundleV1` stays as `SYNC-007` and `SYNC-008` shipped it.
- Every exchange and every removal starts with the existing established
  check of `BootstrapCoordinator` (binding, zone, and anchor equal to the
  local established workspace). Any non-ready result stops both legs and the
  destructive steps, so a device whose workspace was removed and re-created
  elsewhere never publishes into, fetches from, or deletes a zone whose
  anchor it did not join.
- Publish through the writer: the outbox rows are saved in commit order with
  their exact bytes; `Saved` and `Identical` acknowledge one bundle through a
  new writer operation that commits under the writer mutex with the store's
  revision check, so the checkpoint the writer verifies before every later
  commit still explains the replica. `Retryable`, `AccountChanged`, and
  `UnknownOutcome` acknowledge nothing and report a retryable attempt;
  `Conflict` reports action required and acknowledges nothing. The
  orchestrator never mutates the replica store directly.
- Consume: fetch from the cursor in the replica's transport progress, hand
  each page's bundle to `SyncWriter.acceptRemote` with the page's next cursor
  as the receipt and `exactRefetchAvailable = false`, because neither adapter
  can request one record again. Accepted, staged, and duplicate outcomes
  advance the cursor inside their own transaction; a rejected bundle advances
  nothing, pins the cursor, and reports action required, which is an accepted
  MVP limit recorded in the execution record. Nothing projects an accepted
  operation into `exact_domain_policy`, `application_policy`, or
  `local_session`; `SYNC-011` and `SYNC-012` own convergence and the visible
  effect.
- Open the writer on a user path: the first exchange after linking reads the
  key through `BootstrapCoordinator.readWorkspaceKey`, uses the 32 bytes as
  the `TransportKey` exactly as ADR 0006 defines it, with no further
  derivation or label, opens `SyncOperationCore` for the established
  `SyncContext`, and clears the copy through a scoped use-and-clear on
  `WorkspaceKeyValue`. `WorkspaceKeyValue`, `TransportKey`, and `SyncContext`
  stay `internal`; the key never reaches the database, a graph accessor that
  outlives the read, a `toString()`, a log, or driver evidence. A frozen
  writer is closed and reopened with a fresh key read on the next exchange.
- The core, the writer, and the exchange are process-scoped, sharing the
  `AppleBootstrap` flight mutex, because `ComposeRoot.makeUIViewController`
  can build a second iOS runtime and two cores over one database freeze each
  other through the revision check. Consent, exchange, and removal never
  overlap; triggers coalesce to at most one queued exchange; cancelling one
  waiting UI caller does not cancel a shared exchange another caller needs.
  The desktop graph, which has no replica store or core today, gains both.
- No `CKSyncEngine`, subscription, background timer, or push. The ADR 0007
  evidence clause is engine-neutral; explicit fetch and save operations
  between the existing preflight and postflight satisfy it. Whether an engine
  is ever introduced stays with release work.
- Removal is a second explicit control with its own confirmation, offered
  only on a linked device, under `D2`. After the established check it deletes
  the zone only when the anchor equals the local workspace or the zone is
  definitively absent, then the known key item, then the local established
  and replica state, each under the established binding with independent
  absence verification. A different anchor deletes only this device's key
  item, clears local state, and reports action required for the zone. An
  account failure or indeterminate result stops the remaining steps and keeps
  the local state. Already-absent zone and item count as removed. Nothing
  enumerates, garbage-collects, or touches unrelated CloudKit or Keychain
  data. The other device keeps its established row and reports action
  required until it runs its own removal.
- Status vocabulary is the accepted seven states (local-only, pending local
  work, syncing, completed local attempt, retryable failure, waiting for the
  workspace key, action required) as one category plus next action, replaced
  when the state changes. No synchronization time, device list, attempt
  history, count of policy values, or claim that any other device received a
  change. Diagnostics stay unimplemented in this task; the status is product
  state.
- Write surface: `shared/src/commonMain/**/feature/sync/**` (the common port,
  the orchestrator beside `AppleBootstrap` in `bootstrap/`, the writer
  acknowledgement, `ui`), `shared/src/{jvmMain,iosMain}/**/feature/sync/**`,
  `shared/src/*/di/**`, the `SyncReplica.sq` and `SyncBootstrap.sq` queries
  without a schema migration, `macosSyncCompanion/**` and `iosApp/**` only for
  the `D4` outcome inside the unchanged protocol version, `PosatoApplication.kt`,
  the sync slot in `feature/session/ui/**`, strings, `DESIGN.md`, the wiki
  synchronization topic and log, and
  `.agents/skills/verify-posato/features/sync.md`. Under `D1`, additionally
  the exact-domain commit path in `feature/targets`.
- Non-goal: onboarding presentation and the first-install flow
  (`ONBOARDING-001`), domain and policy convergence including a backfill of
  edits made before linking (`SYNC-011`), session intent (`SYNC-012`),
  production schema deployment, quota, and retention (`RELEASE-001`), the
  `SYNC-008` preflight and postflight helper refactor (still deferred), and
  the validation-layer collapse that `SYNC-013` retained, which no authority
  assigns here.

## Acceptance

- `AC-01` — A bundle committed to the outbox is published as one immutable
  record: `Saved` or `Identical` acknowledges it through the writer and the
  writer keeps working afterwards; `Retryable`, `AccountChanged`, and
  `UnknownOutcome` keep the row, report a retryable attempt, and a later
  exchange restages the same bytes; `Conflict` reports action required and
  clears nothing. Physically, a bundle authored on one device appears exactly
  once in the other device's accepted history.
- `AC-02` — Consumption fetches from the last accepted cursor and advances it
  only inside the transaction that accepts, stages, or deduplicates the
  bundle; duplicate and reordered delivery leaves the replica unchanged; a
  rejected bundle replaces no state and advances nothing; an exchange against
  a zone whose anchor differs from the local workspace publishes and accepts
  nothing; no accepted operation changes a visible domain, policy, or session.
- `AC-03` — With the account signed out before an attempt while one bundle is
  pending, common code receives no fetched bytes, acknowledges no bundle, and
  advances no cursor, and the status reports action required; after the
  original account returns, the next exchange restages the unchanged pending
  work and the other device receives that bundle once. Proven physically in
  both directions; the mid-operation postflight stays with adapter tests.
- `AC-04` — The sync section reports exactly one of the seven states from the
  local attempt, never a time, device list, or delivery claim; each retryable
  outcome offers the same explicit retry; a press during a running exchange
  starts no second exchange in the process; launch and foreground trigger an
  exchange only on an established workspace, never on a candidate.
- `AC-05` — The explicit removal deletes only this workspace's zone and the
  known key item under the established binding, verifies absence
  independently, preserves unrelated CloudKit and Keychain data, and clears
  the local state last; a failure in an earlier step stops later steps and
  keeps the local state; afterwards **Sync with iCloud** on the removing
  device establishes a new workspace with no console deletion, and the other
  device reports action required until its own removal, after which it can
  join the new workspace.
- `AC-06` — The first exchange after linking reads the workspace key through
  the coordinator, opens the writer, and clears the key copy; on a device that
  lost a simultaneous opt-in, the adopted key still opens the writer, which
  closes the `SYNC-009` `AC-03` readability clause physically. No key byte,
  binding, anchor, or cursor appears in any `toString()`, log, status string,
  or committed evidence, and `./gradlew quality` passes with no new
  suppression.

## Verification

- `commonTest` over fakes for the orchestrator: acknowledgement only on
  `Saved`/`Identical` and the writer still accepting a mutation afterwards,
  cursor advance only in the accepting transaction, rejected bundle pinning
  the cursor, restage after `AccountChanged` and `UnknownOutcome`, a
  different anchor stopping both legs and the zone delete, coalesced
  triggers, single flight across two runtimes, writer opened once per
  process, key copy cleared, and the removal sequence stopping at each
  failing step.
- `SqlSyncReplicaStoreContractTest` for the acknowledgement and clear
  queries; `BootstrapRedactionTest` and `SyncDomainRedactionTest` extended
  with every new carrier.
- `jvmTest` and `iosTest` over the real graphs: one core and one mailbox
  adapter per process, exchange dispatched off the main thread, an
  unverifiable companion degrading to a retryable status.
- Adapter and native tests on both platforms for the `D4` outcome.
- Physical Mac and iPhone under the maintainer's iCloud account: publish and
  consume in both directions; airplane mode giving a retryable attempt and
  the restaged bundle arriving once after reconnect; sign-out before an
  attempt per `AC-03`; removal from each device followed by the other
  device's action-required state, its own removal, and a fresh establishment;
  the losing-device key read. Mac evidence is the `sync_pending_bundle`,
  `sync_accepted_bundle`, and `sync_replica_state` counts and null checks
  through `db query`; iPhone evidence is status text after relaunch plus the
  Mac receiving each iPhone-authored bundle once, because the device database
  is not readable by the driver. Evidence stays under the ignored
  `build/verification/`.
- `./gradlew quality`, `git diff --check`, and the scoped secret and path scan.

## Decisions or blockers

- `D1` decided (`user-confirmed`, 2026-09-08, the recommendation below): today no production path
  constructs a `LocalSyncMutation`, so the outbox is empty on every device and
  `AC-01` through `AC-03` have no physical observable. Recommended: on a
  linked device, an accepted exact-domain edit first commits the local store,
  which stays the visible truth, then commits the per-domain `PresentDomain`
  or `RemoveDomain` mutations through the writer as a separate transaction.
  A failed or uncertain mutation leaves the local store as truth and surfaces
  action required; a frozen writer reopens on the next exchange. Edits made
  before linking, or while the writer is unavailable, are not backfilled here;
  `SYNC-011` owns the read side, the backfill, and reconciling the two stores,
  and that interim divergence is an accepted risk. The rejected alternative
  kept the outbox empty and deferred every physical publish row to `SYNC-011`,
  leaving this task with fake-only evidence for its own outcome.
- `D2` decided (`user-confirmed`, 2026-09-08): ADR 0007 names the action but
  no control. Accepted: a secondary **Remove workspace** control in the sync
  section, shown only when linked, in the destructive color from
  `DESIGN.md`, with a confirmation that states it deletes the shared
  workspace from iCloud, that changes not yet delivered from this device are
  discarded, that the domains on this device stay, that every other device
  must remove and link again, and that **Sync with iCloud** is required
  afterwards. It clears the bootstrap state and the `sync_*` replica tables,
  never `exact_domain_policy`, `application_policy`, or `local_session`.
- `D3` decided (`user-confirmed`, 2026-09-08): this task extends the
  `SYNC-009` section from bootstrap outcomes to the seven accepted states plus
  one **Sync now** action on a linked device, and amends the "current
  implementation boundary" of `DESIGN.md` for that one section. Full
  onboarding presentation stays with `ONBOARDING-001`.
- `D4` decided (`user-confirmed`, 2026-09-08), closes the wiki open question: an expired server change
  token becomes a distinct outcome on both native providers and adapters,
  inside the unchanged protocol version, and common code restarts from the
  first page without touching accepted state, because bundles are immutable
  and a duplicate advances the cursor in its own transaction. Today both
  adapters map it to `UnknownOutcome` with no recovery.
- `D5` decided (`user-confirmed`, 2026-09-08, retry policy): retry is an explicit opportunity, not a
  loop: the person's **Sync now**, application launch or foreground on an
  established workspace, and the end of a local commit under `D1`, coalesced
  to one running and at most one queued exchange. No backoff timer, no
  background delivery. This satisfies "waiting and retry behavior" without
  claiming a delivery time; scheduling beyond that stays with release work.
- Physical gate: one iPhone and one Mac under the maintainer's iCloud account.
  The Mac holds the established workspace from `SYNC-009`; the first removal
  row therefore starts from real state, and every from-empty row after that
  uses the new removal instead of a console deletion.
