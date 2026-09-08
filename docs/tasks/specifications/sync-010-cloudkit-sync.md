# `SYNC-010`: Publish and consume pending encrypted bundles with truthful sync status and retry

- **Review tier:** `high-risk`
- **Tier reason:** First change that moves encrypted operations between the two
  devices through the person's private CloudKit database, reads the workspace
  key on a user path, derives the transport key from it, and deletes a
  workspace from iCloud and the synchronizable Keychain. A wrong step
  acknowledges a bundle the cloud never stored, accepts a bundle under a
  switched account, advances a cursor past unaccepted work, leaks key bytes
  through a graph or a log, or deletes the wrong resource.
- **Dependencies:** completed `SYNC-009` (`AppleBootstrap` facade, both graphs,
  the **Sync with iCloud** control), `SYNC-002`/`SYNC-013` (`SyncOperationCore`,
  `SyncWriter`, `SqlSyncReplicaStore`), `SYNC-007` and `SYNC-008` (the two
  mailbox adapters and the shared `MailboxTypes` vocabulary), `MODEL-001`
  (the local exact-domain slice whose edits feed the outbox under `D1`),
  `QUALITY-002`, `QUALITY-004`, and `QUALITY-005` (driver)
- **Integration group:** `PR-CLOUDKIT-SYNC`
- **Authority:** `SYNC-010` in MVP roadmap revision 12 (wave P3/W3.7; this
  task does not amend the roadmap),
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
  (ongoing mailbox exchange runs between the binding preflight and postflight;
  "A failed check leaves pending work and the last accepted cursor and engine
  state unchanged"; "An account-binding failure … authorizes no … fetched-
  bundle acceptance, cursor advancement, engine-state acceptance, or
  publication acknowledgement"; "Removing a workspace is a separate explicit
  destructive action"; the `SYNC-010` evidence clause: "an account switch
  exposes no fetched bundle to common code, advances no cursor or accepted
  engine state, acknowledges no sent bundle, and preserves and restages
  unchanged pending work only after the established binding returns"),
  [ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md)
  (publishing never edits a bundle; duplicates are idempotent; "only then
  advance transport progress"; retry reuses the same immutable bytes),
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
established; an account switch during either leg acknowledges, accepts, and
advances nothing; and an explicit destructive action removes exactly the
Posato zone and known key items so a fresh **Sync with iCloud** on either
device starts a new workspace without a console step.

## Boundaries

- Define the common mailbox port in `commonMain` over the existing
  `MailboxTypes` vocabulary (publish one bundle, fetch one bounded change page
  from an opaque cursor, delete the exact zone and verify absence) and make
  `MacOsMailboxAdapter` and `IosMailboxAdapter` implement it. No new record
  type, field, wire layout, or payload bound; `PosatoEncryptedBundleV1` and
  the 65,536-byte cap stay as `SYNC-007` and `SYNC-008` shipped them.
- Publish: for each row of `sync_pending_bundle` in `rowid` order, save the
  exact bytes; `Saved` and `Identical` acknowledge the bundle by deleting its
  outbox row in one transaction; `Retryable`, `AccountChanged`, and
  `UnknownOutcome` leave the row and the status reports a retryable local
  attempt; `Conflict` is an integrity failure that reports action required and
  acknowledges nothing. The needed `deletePendingBundle` query ships in
  migration `6.sqm`, mirroring `deleteStagedBundle`.
- Consume: fetch from the cursor in `sync_replica_state.transport_progress`,
  hand each page's bundle to `SyncWriter.acceptRemote` with the page's next
  cursor as the receipt, and advance the cursor only inside that accepting,
  staging, or duplicate transaction. Nothing projects an accepted operation
  into `exact_domain_policy`, `application_policy`, or `local_session`;
  `SYNC-011` and `SYNC-012` own convergence and the visible effect.
- Open the writer on a user path: the first exchange after linking reads the
  key through `BootstrapCoordinator.readWorkspaceKey`, derives the 32-byte
  `TransportKey` with the existing `hkdfSha256` under a fixed format-1 info
  label, opens `SyncOperationCore` for the established `SyncContext`, and
  clears the workspace-key copy. `WorkspaceKeyValue`, `TransportKey`, and
  `SyncContext` stay `internal`; the key never reaches the database, a graph
  accessor that outlives the read, a `toString()`, a log, or driver evidence.
- One process-scoped exchange at a time, sharing the `AppleBootstrap` flight
  rule: consent, exchange, and removal never overlap, and cancelling one
  waiting UI caller does not cancel a shared exchange another caller needs.
  Both native providers accept one in-flight call per instance, so this
  serialization is the coordinator those providers document as future work.
- No `CKSyncEngine`, subscription, background timer, or push. The ADR 0007
  evidence clause is engine-neutral; explicit fetch and save operations
  between the existing preflight and postflight satisfy it. Whether an engine
  is ever introduced stays with release work.
- Account isolation reuses the established binding as `SYNC-007` and
  `SYNC-008` built it: a failed postflight discards fetched bytes, keeps the
  cursor, keeps the outbox row, and reports action required; the next
  exchange under the original binding restages the unchanged pending work.
- Removal is a second explicit control with its own confirmation, offered
  only on a linked device, under decision `D2`. It runs zone delete, then
  known key-item delete, then the local clear, each under the established
  binding with independent absence verification; an account failure or
  indeterminate result stops the remaining steps, keeps the local
  `Established` row, and reports action required for the same exact resource.
  Nothing enumerates, garbage-collects, or touches unrelated CloudKit or
  Keychain data.
- Status vocabulary is the accepted seven states (local-only, pending local
  work, syncing, completed local attempt, retryable failure, waiting for the
  workspace key, action required) as one category plus next action, replaced
  when the state changes. No synchronization time, device list, attempt
  history, count of policy values, or claim that any other device received a
  change. Diagnostics stay unimplemented in this task; the status is product
  state.
- Write surface: `shared/src/commonMain/**/feature/sync/**` (new `mailbox`
  port, exchange orchestration, `ui`), `shared/src/{jvmMain,iosMain}/**/feature/sync/**`,
  `shared/src/*/di/**`, `SyncReplica.sq`, `SyncBootstrap.sq`, `6.sqm`, the
  Swift CloudKit providers only for the `D4` token-expiry outcome,
  `PosatoApplication.kt`, the sync slot of the session screen, strings,
  `DESIGN.md`, and `.agents/skills/verify-posato/features/sync.md`. Under
  `D1`, additionally the exact-domain commit path in `feature/targets`.
- Non-goal: onboarding presentation and the first-install flow
  (`ONBOARDING-001`), domain and policy convergence (`SYNC-011`), session
  intent (`SYNC-012`), production schema deployment, quota, and retention
  (`RELEASE-001`), the `SYNC-008` preflight and postflight helper refactor
  (still deferred), and the `SYNC-013` validation-layer collapse, which no
  authority assigns here.

## Acceptance

- `AC-01` — A bundle committed to the outbox is published as one immutable
  record: `Saved` or `Identical` clears its outbox row in the same
  transaction; `Retryable`, `AccountChanged`, and `UnknownOutcome` keep the
  row, report a retryable attempt, and a relaunch restages the same bytes;
  `Conflict` reports action required and clears nothing. Physically, a bundle
  authored on one device appears once in the other device's accepted history.
- `AC-02` — Consumption fetches from the last accepted cursor and advances the
  cursor only inside the transaction that accepts, stages, or deduplicates the
  bundle; duplicate and reordered delivery leaves the replica unchanged; a
  fetched bundle that fails validation replaces no state and advances nothing;
  no accepted operation changes a visible domain, policy, or session.
- `AC-03` — With an account switch during a publish or a fetch, common code
  receives no fetched bytes, acknowledges no bundle, and advances no cursor;
  the status reports action required; after the original account returns, the
  next exchange restages the unchanged pending work and completes. Proven
  physically on both devices.
- `AC-04` — The sync section reports exactly one of the seven states from the
  local attempt, never a time, device list, or delivery claim; each retryable
  outcome offers the same explicit retry; a press during a running exchange
  starts no second exchange in the process.
- `AC-05` — The explicit removal deletes only the exact zone and the known key
  items under the established binding, verifies absence independently,
  preserves unrelated CloudKit and Keychain data, and clears the local
  established state last; a failure in an earlier step stops later steps and
  keeps the local state; afterwards **Sync with iCloud** on either device
  establishes a new workspace with no console deletion.
- `AC-06` — The first exchange after linking reads the workspace key through
  the coordinator, opens the writer, and clears the key copy; on a device that
  lost a simultaneous opt-in, the adopted key still opens the writer, which
  closes the `SYNC-009` `AC-03` readability clause physically. No key byte,
  transport key, binding, anchor, or cursor appears in any `toString()`, log,
  status string, or committed evidence, and `./gradlew quality` passes with
  no new suppression.

## Verification

- `commonTest` over fakes for the exchange orchestrator: outbox order,
  acknowledgement only on `Saved`/`Identical`, cursor advance only in the
  accepting transaction, restage after `AccountChanged` and `UnknownOutcome`,
  single flight across two callers, writer opened once per process, key copy
  cleared, and the removal sequence stopping at each failing step.
- `SqlSyncReplicaStoreContractTest` and `verifySqlDelightMigration` for
  `6.sqm`; `BootstrapRedactionTest` and `SyncDomainRedactionTest` extended
  with every new carrier.
- `jvmTest` and `iosTest` over the real graphs: mailbox adapters and replica
  store built once, exchange dispatched off the main thread, an unverifiable
  companion degrading to a retryable status.
- Adapter tests on both platforms for the `D4` token-expiry outcome.
- Physical Mac and iPhone under the maintainer's iCloud account: publish and
  consume in both directions, a relaunch restaging a bundle whose save
  returned no outcome, an account sign-out mid-exchange, removal from each
  device followed by a fresh establishment, and the losing-device key read.
  Mac evidence is the `sync_pending_bundle`, `sync_accepted_bundle`, and
  `sync_replica_state` row counts and null checks through `db query`; iPhone
  evidence is status text after relaunch, because the device database is not
  readable by the driver. Evidence stays under the ignored
  `build/verification/`.
- `./gradlew quality`, `git diff --check`, and the scoped secret and path scan.

## Decisions or blockers

- `D1` open, blocking (what fills the outbox): today no production path
  constructs a `LocalSyncMutation`, so the outbox is empty on every device and
  `AC-01` through `AC-03` have no physical observable. Recommended: after a
  device is linked, an accepted exact-domain edit also commits its
  `PresentDomain` or `RemoveDomain` mutation through `SyncWriter.mutate` in
  the same user action, so the local store stays the visible truth and the
  bundle is real. Remote acceptance still projects nothing; `SYNC-011` owns
  the read side and reconciles the two stores. Alternative: keep the outbox
  empty and defer every physical publish row to `SYNC-011`, which leaves this
  task with fake-only evidence for its own outcome.
- `D2` open, blocking (removal surface): ADR 0007 names the action but no
  control. Recommended: a secondary **Remove workspace** control in the sync
  section, shown only when linked, with a confirmation that states it deletes
  the shared workspace from iCloud for every device that joined it, keeps the
  domains on this device, and requires **Sync with iCloud** again afterwards;
  it uses the destructive color from `DESIGN.md`. It clears
  `sync_bootstrap_state` and the `sync_*` replica tables, never
  `exact_domain_policy`, `application_policy`, or `local_session`.
- `D3` open, blocking (status surface and `DESIGN.md`): this task extends the
  `SYNC-009` section from bootstrap outcomes to the seven accepted states plus
  one **Sync now** action on a linked device, and amends the "current
  implementation boundary" of `DESIGN.md` for that one section. Full
  onboarding presentation stays with `ONBOARDING-001`.
- `D4` recommended, closes the wiki open question: an expired server change
  token becomes a distinct adapter outcome on both platforms and common code
  restarts from the first page without touching accepted state, because
  bundles are immutable and `acceptRemote` deduplicates; the cursor is
  replaced only when the accepting transaction commits. Today both adapters
  map it to `UnknownOutcome` with no recovery.
- `D5` recommended (retry policy): retry is an explicit opportunity, not a
  loop: the person's **Sync now**, application launch or foreground on a
  linked device, and the end of a local commit under `D1`. No backoff timer,
  no background delivery. This satisfies "waiting and retry behavior" without
  claiming a delivery time; scheduling beyond that stays with release work.
- Physical gate: one iPhone and one Mac under the maintainer's iCloud account.
  The Mac holds the established workspace from `SYNC-009`; the first removal
  row therefore starts from real state, and every from-empty row after that
  uses the new removal instead of a console deletion.
