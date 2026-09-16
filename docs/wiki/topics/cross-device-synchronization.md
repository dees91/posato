# Cross-Device Synchronization

## Direction

- `user-confirmed` (2026-08-25): transport, payload encryption, workspace-key
  delivery, and device admission are independent concerns. Their accepted
  production boundary is
  [ADR 0002](../../decisions/0002-synchronization-trust-and-workspace-modes.md).
- `user-confirmed`: the first workspace uses CloudKit Private Database as the
  transport, synchronizable Keychain for workspace-key delivery, and Apple
  Account/iCloud Keychain trust for membership across one Mac and one iPhone.
- `user-confirmed`: each Apple installation exposes one **Sync with iCloud**
  action. Blocker adds no QR, invitation, or approval by another Blocker
  installation; Apple may still require its own system-level device approval.
- `user-confirmed`: the application performs no provider login and operates no
  product account, product-operated shared user-data backend, or
  synchronization relay.
- `user-confirmed`: application-layer E2EE, signed operations, validation, and
  one compatible operation format apply to every transport.
- `user-confirmed`: a later portable workspace may use one user-selected
  synchronized directory across Apple, Android, and Linux. It uses explicit
  Blocker membership, QR enrollment, per-device key wrapping, key epochs,
  prospective revocation, and optional recovery.
- `user-confirmed`: each workspace has one active transport. Moving to a
  portable transport creates a new workspace and key epoch through explicit
  export/import; there is no live bridge, dual-write, or parallel authority.
- `user-confirmed`: synchronization uses an eventually convergent model when
  delivery succeeds, exposes a manual **Sync now** action, and promises neither
  device wake-up nor a delivery SLA.

## Accepted MVP boundary

`user-confirmed` (2026-08-25): the MVP synchronizes exact domain policy,
semantic application policy, and active-session intent between one Mac and one
iPhone after **Sync with iCloud** is chosen on each installation. Opaque
platform application selections remain local and attach to the synchronized
semantic policy. Schedules and total-key-loss recovery are later work.

If CloudKit contains a workspace but the synchronizable workspace key has not
arrived, the installation waits and reports that state. It must not interpret
the workspace as empty, generate a replacement key, or create a parallel
workspace.

The accepted product contract describes delivery as best-effort and retryable.
The earlier direction's use of "eventual" describes the convergence model when
delivery succeeds; it is not a promise that every change will eventually reach
a sleeping, offline, misconfigured, or permanently unavailable device.

## Accepted second-install continuation

`user-confirmed` (2026-09-10): ONBOARDING-002 continues a consented fresh
join through manual Check again and bounded foreground checks, retaining the
account and workspace context in process memory. Missing keys permit reads
only; verified adoption permits established-row persistence and normal
exchange. A changed account or workspace cannot silently start a new setup.
Waiting is not persisted in this iteration; restart returns to explicit
consent, an accepted UX limitation rather than a completed recovery design.
The [task brief](../../tasks/specifications/onboarding-002-second-install.md)
defines the accepted scope; ADR 0007 records the dated consent amendment.
`observed` in the ONBOARDING-002 implementation: the coordinator retains the
fresh-join context, both retry routes use exact reads, and unit tests cover
adoption, context loss, transient failures, and overlapping opportunities.
`observed` (2026-09-10): fresh iPhone joining a Mac-created workspace and
fresh Mac joining an iPhone-created workspace both completed through the
onboarding UI. Each device retained its own application selection after
relaunch; Mac pending/accepted counts stayed unchanged by selection. Keys were
available immediately in both runs. Delayed-key retry and its announcement
behavior retain unit/code evidence only; no physical waiting or spoken-delivery
claim follows from these runs. The execution record holds categorical results.

`source-claim`: the read-only PoC `WorkspaceBootstrap` and Apple sync report
provide explicit-consent and exact-item lifecycle evidence, but use a different
workspace/identity model. ONBOARDING-002 keeps the production ADR 0007 phases;
no PoC source or machine-specific evidence is imported.

## Bounded PoC result

`observed`: a KMP/Compose application synchronized a complete local-first state
through CloudKit Private Database and Keychain on one physical Mac and one
physical iPhone. The corrected final evidence ran through the normal desktop
and iOS application entry points and repeated the physical scenario matrix.

The exercised cases included:

- initial configuration and workspace bootstrap;
- publication and retrieval in both directions;
- offline concurrent edits followed by deterministic convergence;
- duplicate and reordered delivery;
- pending work surviving application restart;
- native helper failure followed by recovery;
- rejection of invalid encrypted input without replacing valid state;
- zero pending work and equal application state after convergence;
- exact cleanup and clean-source configuration behavior.

The result demonstrates feasibility for the tested topology. It does not prove
production reliability, CloudKit latency, quota behavior, background delivery,
larger membership, or portable transport.

## Local-first model

The common local replica is authoritative. CloudKit or another transport is a
mailbox, not the source of truth and not the merge engine.

The PoC model used immutable operations for:

- policy upsert and tombstone;
- schedule upsert and tombstone;
- bounded session start and stop;
- device admission and revocation;
- key-epoch transition and recipient-specific key distribution.

Operations carried a stable workspace and transport epoch, author identity,
monotonic author sequence, Hybrid Logical Clock timestamp, operation identity,
key epoch, and typed payload. A deterministic reducer validated authorship,
membership, sequence continuity, target scope, and operation ordering before
publishing an effective policy.

`user-confirmed`: the encrypted bundle, signed operation, validation, and
reduction model remains common to Apple and portable transports. Policy and
session operations are shared. Explicit membership, per-device wrapping,
revocation, and recovery operations are required by portable mode but are not
an Apple MVP enrollment ceremony.

`user-confirmed` (2026-08-28):
[ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md)
accepts the Apple MVP's closed format-1 operation vocabulary, automatic
workspace-key-authorized author registration, canonical compatibility policy,
validation order, and deterministic convergence. Portable membership and its
later compatibility policy remain open outside the MVP format.

`user-confirmed` (2026-08-28): capacity outcomes are derived by reducing the
complete applicable operation set in total order and may be reclassified when
an earlier operation arrives. Observed session expiry is a terminal local fact
keyed to the encrypted session identifier, so restart, reordering, a conflicting
start, or wall-clock rollback does not revive it. Multiple distinct starts for
one identifier deterministically quarantine that session; the marker is not a
synchronized event or diagnostic.

`user-confirmed` (2026-08-28): an Apple author is a process-memory authoring
incarnation scoped to one local replica-writer open, not a persistent device
identity. The first mutation atomically creates registration and business
operations; reopen creates a fresh author, while committed pending bundles
remain immutable and publishable. Ambiguous local commits reconcile exact bytes
or fail closed. The serialized replica state machine advances the open writer's
checkpoint with each exact local or remote transaction. Loss, regression, or
an unexplained change to that author/HLC footprint freezes the writer before it
can create a sequence gap or clear exhaustion, while a verified remote advance
remains valid. Local authoring samples wall time once per batch: a later wall
time resets the logical counter to zero, while an equal or regressed value uses
the bounded HLC successor and logical overflow carries into the next physical
millisecond. A terminal HLC blocks further local authoring without rejecting
later valid remote input, while an out-of-range wall clock is recoverable.

## Persistence and atomicity

The PoC used SQLDelight for an app-private local replica. It kept accepted
operation history, pending publication work, transport cursor state, and the
last valid projected snapshots in one transactional boundary.

Durable properties worth retaining:

- a local mutation and its pending publication record are committed together;
- remote input is completely validated, decrypted, authorized, reduced, and
  projected before the visible state changes;
- cursor advancement and accepted state commit atomically;
- retryable failure keeps pending work and the last valid state;
- duplicate operations and bundles are idempotent;
- schema migration or storage corruption must not silently replace valid state.

`observed` (2026-09-02): `SYNC-002` implemented this core in the production
shared module. Accepted, pending, and staged bundle bytes are immutable;
replica checkpoints, terminal expiry facts, and opaque transport progress
commit in the same SQLDelight transaction, and every store mutation compares
the complete expected checkpoint with the restored snapshot before writing.
Migration verification, duplicate and reordered delivery, bounded capacity,
terminal HLC behavior, and deterministic convergence passed on the JVM, iOS
Simulator, and a physical iPhone. Concrete CloudKit cursor and sync-engine
state remain deferred to their transport task.

`observed` (2026-09-02): reopen fails closed. A restored snapshot must
authenticate every retained bundle, satisfy the live author, sequence,
staging, capacity, and terminal-expiry invariants, keep its durable HLC within
the range that retained history can explain, and pass a same-transaction
SQLite preflight of storage classes, sizes, cardinalities, and identifier
uniqueness before typed rows are materialized. Any inconsistency reports
corruption instead of initializing fresh state. This validates what the
application reads; it does not claim to defend against modification of the
app-private database by a local actor, which the threat model accepts as
`R-02`.

`observed` (2026-09-02): one serialized writer owns the replica. Invalid local
mutations are rejected before authoring resources are reserved; an uncertain
or cancelled commit reconciles against durable state in a non-cancellable read
and otherwise freezes the writer; close completes key retirement and owner
release even under cancellation; open consumes the supplied transport key on
every path and rejects a key that was already retired. Owned key and
plaintext buffers are cleared after use within the `R-05` boundary.

## Transport contract

The platform-neutral mailbox contract can remain small:

- publish one complete encrypted bundle;
- list bounded changes from an opaque cursor;
- fetch a referenced bundle;
- report structured categories such as account unavailable, permission, quota,
  network, corruption, conflict, timeout, storage, bridge failure, and
  unsupported version.

Identifiers, payloads, cursors, counts, and frame sizes must be bounded and
validated. Human-readable diagnostic text is not program control flow.

CloudKit record values, cursors, `CKSyncEngine` state, and native errors remain
inside the Apple adapter. `CKSyncEngine` may schedule and report transport
opportunities; it does not own domain merge or the authoritative pending queue.

## Encryption and metadata

`observed`: the prototype exercised signed, application-encrypted bundles with
cross-target golden vectors. It authenticated routing context and rejected
wrong workspace, transport epoch, key epoch, author, bundle identity, replay,
truncation, oversize data, invalid signature, and invalid ciphertext cases.

The prototype evaluated AEAD encryption, signatures, key agreement, and key
derivation with bounded canonical data. `user-confirmed` (2026-08-28): ADR 0006
selects HKDF-SHA-256, AES-256-GCM, Ed25519, system JCA/JCE and CryptoKit
providers, a closed positional format, per-bundle HKDF keys with a single
implicit nonce use, encrypted author metadata, and no
plaintext or algorithm fallback for Apple MVP format 1. `observed` (2026-08-31):
the production Kotlin codec and JCA provider passed a fixed complete format-1
golden bundle decoded and authenticated through the injected Swift CryptoKit
provider on the iOS Simulator and a physical iPhone, alongside the selected
primitive vectors. Every native result crossing the iOS boundary is checked
against its operation-specific size before Kotlin allocates or reads it, and
provider failures map to nullable cryptographic failures rather than
exceptions. Portable key wrapping and provider selection remain later
decisions.

`user-confirmed`: CloudKit and portable folders use one compatible
application-encrypted and signed payload format. Apple-mode simplification
changes workspace-key delivery and device admission; it does not create a
plaintext CloudKit protocol or remove application-layer E2EE.

Application-layer encryption does not make CloudKit metadata anonymous. The
provider may still observe account, container, timing, sizes, record counts,
and the metadata required for mailbox routing.

## Mode-specific key delivery and device admission

`observed`: the feasibility design separated:

- synchronizable workspace material shared through Keychain;
- device-local signing and key-agreement identity;
- explicit approved membership in the encrypted operation history.

An explicit QR-based enrollment flow proved bidirectional device admission with
replay protection and user approval. An optional offline recovery code flow
proved confirmation, re-entry, cancellation, and redacted UI behavior.

Revocation advances to a fresh key epoch and distributes future material only
to active members. It prevents a removed identity from decrypting or authoring
accepted future operations. It is not remote wipe and cannot erase historical
plaintext, keys, screenshots, or archives already copied by another device.

`superseded` (2026-08-25): the earlier Gate 1 scope required an existing
Blocker installation to approve the second Apple installation. That approval
is no longer part of Apple MVP onboarding.

`user-confirmed` (2026-08-25): Apple and portable workspaces use different
admission policies:

| Workspace | Workspace-key delivery | Device admission |
| --- | --- | --- |
| Apple MVP | Synchronizable Keychain | Apple Account and iCloud Keychain trust; one **Sync with iCloud** action on each Blocker installation |
| Portable, later | Per-device key wrapping | Existing Blocker member approval, QR exchange, and signed membership operations |

In Apple mode, a new device may still need Apple-managed approval or recovery
before iCloud Keychain releases synchronizable items. Blocker exposes that as a
system prerequisite and does not duplicate it. Blocker also gives up
independent admission and prospective revocation of one Apple installation
while it remains trusted by Apple. ADR 0006 preserves operation authenticity
through workspace-key-authorized, self-signed author registration without
reintroducing cross-device Posato approval.

In portable mode, selecting the same Dropbox, OneDrive, iCloud Drive, or other
File Provider folder grants access to bytes, not membership. Independent device
keys, explicit approval, QR replay protection, per-device wrapping, signed
membership operations, key epochs, revocation, and optional recovery remain
required design inputs from the PoC.

Each workspace has one active transport. CloudKit-to-folder migration creates
a new portable workspace and key epoch, makes the exporting Apple device the
first portable member, enrolls later devices explicitly, and retires CloudKit
as the active authority. There is no live bridge or dual-write.

## Accepted Apple bootstrap contract

`user-confirmed` (2026-08-28):
[ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
freezes the Apple MVP bootstrap and provider contract:

- the exact private `PosatoSyncV1` zone is binding-checked, created if absent,
  and read-confirmed before anchor absence is accepted; one create-only
  `PosatoWorkspaceV1` anchor then arbitrates concurrent first runs;
- one versioned generic-password item per workspace carries the workspace,
  transport-epoch, and key-epoch identifiers plus the 32-byte workspace key;
- an existing anchor with a delayed Keychain item waits and never creates a
  replacement key or workspace;
- one local-only opaque account binding is persisted with each bootstrap
  attempt and established workspace, then checked around bootstrap, every
  workspace-key read or deletion, destructive removal, and ongoing mailbox
  access, so account change cannot create a parallel anchor, expose key bytes or
  a fetched bundle to common code, delete across accounts, advance transport
  state, or falsely acknowledge cleanup or publication; exact Keychain
  reconciliation and a fresh sync-engine instance resume only after the
  original binding returns;
- immutable encrypted bundles use one `PosatoEncryptedBundleV1` record with an
  inline payload bounded by ADR 0006; and
- account change, malformed state, unknown outcomes, and cleanup preserve exact
  reconciliation and the last established local binding, while zone absence
  after establishment is action-required and preserves local and pending work.

`user-confirmed`: macOS uses the distinct `app.posato.macos.sync` short-lived
Swift companion rather than the enforcement helper. Kotlin owns bootstrap and
semantic outcomes; the native process owns only CloudKit and Keychain mechanics.
Implementation and physical evidence remain with `SYNC-004` through `SYNC-010`.

`observed` (2026-09-03): `SYNC-004` implemented the one-workspace bootstrap
coordinator in the production shared module (`feature/sync/bootstrap` with a
`3.sqm` singleton table). A serialized coordinator runs the ADR 0007 ten-step
protocol against binding-gated fake CloudKit, Keychain, and account ports; the
workspace key is generated in memory, never persisted, and cleared after use.
Contract tests cover the full ADR 0007 evidence list for `SYNC-004`, including
two coordinators converging on one anchor with loser-only cleanup, crash resume
at each persistence boundary, and account change around indeterminate saves.
An established workspace re-reads its anchor on every bootstrap, so a replaced
or missing anchor reports action-required instead of a false ready. Native
adapters, platform wiring, and physical evidence remain with `SYNC-005`
through `SYNC-009`; explicit workspace removal belongs to `SYNC-010`.

`observed` (2026-09-04): `SYNC-005` implemented the iOS synchronizable-Keychain
adapter. A Swift provider in `iosApp` resolves the 32-byte binding from the
current CloudKit user record, runs each `SecItem` call with the exact format-1
selector between a binding preflight and postflight plus a `.CKAccountChanged`
observation window, and returns only platform-neutral statuses with the 84-byte
value or nothing. A Kotlin `iosMain` adapter maps those onto the frozen
`BootstrapAccountPort` and `BootstrapKeyPort` outcomes, clears owned copies,
and re-checks value lengths. The team prefix is read at runtime from the
`PosatoDevelopmentTeam` `Info.plist` key expanded from `$(DEVELOPMENT_TEAM)`,
so no team value is tracked. The full create, exact-read, duplicate,
conflict, and delete-and-verify-absent cycle passed on a physical iPhone with
a random workspace id and teardown cleanup; Simulator tests cover mapping over
injected account and `SecItem` seams.

`observed` (2026-09-04): `SYNC-006` added the `app.posato.macos.sync` companion
as a nested `PosatoMacOSSync.app` launched over one-shot private pipes. The
companion reads its own iCloud and `keychain-access-groups` entitlements before
any CloudKit or `SecItem` call, resolves the local account binding natively, and
maps exact create/read/delete-and-verify outcomes onto the frozen `SYNC-004`
ports. Credential-free packaging embeds and signs the companion with empty
entitlements; missing entitlements return `unavailable`/`retryable` without
throwing.

`user-confirmed` (2026-09-04): Keychain Sharing is not an App ID capability on
macOS or iOS. `keychain-access-groups` is granted by any profile of the team
for team-prefixed groups. The only portal capability the companion needed was
iCloud/CloudKit, completed in `SYNC-003`. The Apple Development physical gate
is the untracked development profile, not a portal Keychain Sharing toggle.

`observed` (2026-09-04): with that profile, CloudKit `accountStatus` stayed
undetermined until the companion entitlements included
`com.apple.application-identifier`. After that stamp, a synthetic-id create,
identical re-create, exact read, and delete-and-verify-absent round trip
passed and left no item. A live locked-keychain read and iCloud sign-out are
an accepted `SYNC-006` limit: the unit test maps a locked Keychain, and a
real account change is physical evidence for `SYNC-009`.

`observed` (2026-09-05): `SYNC-008` implemented the macOS CloudKit boundary
in production code: the nested `app.posato.macos.sync` companion serves zone
fetch and save, fixed anchor read and create-if-absent, immutable bundle
save, and bounded change fetch from an opaque cursor, each between a binding
preflight and postflight plus an account-change observation window. JVM
`BootstrapCloudPort` and mailbox adapters map every outcome over the v1
framing with per-operation payload bounds. The AC-06 physical round trip on
one Mac passed: zone save and confirm, anchor create plus conflict on
identical re-create, bundle save plus identical re-save, change fetch of that
bundle, different-bytes rejection, then exact zone delete verified absent,
leaving the private database as found. This proves the macOS leg only;
cross-device exchange stays with `SYNC-009` and delayed delivery with
`SYNC-010`.

## Lifecycle and user-visible status

The common orchestration should coalesce overlapping start, resume, native
transport, timer, and manual opportunities. Closing the host cancels owned work;
cancelling one waiting UI caller must not cancel shared synchronization needed
by another caller.

Useful status distinctions are:

- local-only;
- pending local work;
- syncing;
- last completed local attempt;
- retryable failure;
- waiting for the synchronizable workspace key;
- action-required failure.

A local success means the local synchronization transaction completed. It must
not claim that every other device has received the update.

## Established Apple exchange

- `observed` (`SYNC-009`): the shipped consent control and process flight use
  the bootstrap coordinator to establish or adopt one workspace, retain its
  account binding, and report key waiting without inventing a linked outcome.
  See the [execution record](../../tasks/executions/sync-009-apple-bootstrap.md)
  for the original checks and physical evidence.
- `observed` (`SYNC-010`, local tests): exchange, consent, local authoring, and
  removal share the flight mutex. Production graph creation retains one core
  and writer per process. The workspace's 32 bytes become the transport key
  directly; scoped key use clears the owned read buffer.
- Publication confirmation is a writer mutation under its checkpoint and
  revision check. Removing an outbox row directly would invalidate the
  writer's snapshot and freeze subsequent authoring. Saved and identical
  responses acknowledge the exact immutable bundle; uncertain responses
  preserve it for retry.
- Binding, zone, and anchor checks gate both exchange legs and removal. A
  different anchor stops exchange and zone deletion, while explicit removal
  may clear only the old known key and local sync state. Local websites stay.
- Native token expiry is distinct from an unknown outcome. An attempt restarts
  fetch once from the first page, retaining accepted operations and pending
  work; repeated expiry reports retryable. Both native operations disable
  automatic fetching of all pages so each bounded page is accepted before the
  next request. Apple's [fetchAllChanges contract](https://developer.apple.com/documentation/cloudkit/ckfetchrecordzonechangesoperation/fetchallchanges)
  defaults to fetching all pages.
- `observed` (`SYNC-010`, physical iPhone): a valid empty first-page cursor
  originally caused `ArrayIndexOutOfBoundsException` in the Kotlin-to-NSData
  bridge before CloudKit fetch. An explicit empty NSData branch fixes it;
  a native adapter regression reproduces the failure before the guard. Both
  devices now complete exchange and the Mac accepts iPhone-authored operations.
- `observed` (`SYNC-010`, signed Mac/iPhone): offline retry and iPhone
  sign-out/sign-in preserve the pending change through the interruption and
  yield one additional acceptance on Mac after sign-in. Removal from either
  device preserves local websites, blocks the old peer, and permits fresh
  consent. Old-anchor cleanup on either peer leaves the newly created zone
  intact. Overlapping consent produces a winning iPhone and a waiting Mac;
  the Mac then adopts the key, authors successfully, and completes after relaunch.
- `user-confirmed` evidence limit (2026-09-08): the maintainer accepted iPhone
  sign-out evidence and waived sign-out on the working Mac. Mac account
  isolation is unit/adapter-tested; it is not claimed as physically verified.
- `user-confirmed` limits: neither adapter offers exact refetch, so rejection
  pins the cursor. Exact-domain edits commit locally before authoring into the
  outbox; failure of that second step preserves the local save.
- `user-confirmed` (2026-09-09): local saves hand ordered domain diffs to a
  process-owned FIFO without waiting for network work. Each diff retains the
  established workspace captured before its local commit; removal or a new
  workspace invalidates old queued work. The writer opens on demand under the
  shared flight. A failed authoring pass retains its failure status instead
  of running an exchange that could immediately overwrite it with completion.
  The queue is volatile until the outbox commit, so process exit can lose an
  unauthored diff while retaining local policy. Pre-link edits and failed
  authoring are not backfilled, and remote operations
  do not yet change visible policies or sessions (`SYNC-011` / `SYNC-012`).
- `user-confirmed` (2026-09-10 correction): pending domain intent overlays
  the projection in persisted order, so the last saved choice survives an
  in-flight exchange. The onboarding summary follows policy changes while
  visible, subscribing before its initial read and retaining its last valid
  count on failure; website-entry focus remains tied to local submissions.
  D10/D11 and the at-cap D5 recovery limit are accepted in the
  [SYNC-011 brief](../../tasks/specifications/sync-011-policy-convergence.md).
- `observed` (2026-09-10 correction): `SYNC-011` replaces the volatile queue
  with durable intent rows recorded in the save transaction, and connects
  remote operations to the visible exact-domain policy and application group
  name while application selections stay local; sessions still do not
  converge (`SYNC-012`).
- `observed` (2026-09-09 correction): controlled common tests prove local saves
  complete while established checks or fetches are suspended. Signed Mac/iPhone
  edits, persistence after relaunch, removal of the test domains, and exchange
  pass; repeated fetch adds no accepted bundles. These physical runs do not
  inject network delays or replace the existing account-gate evidence.
- `observed` (2026-09-10 physical gate, signed Mac plus iPhone 13 mini on one
  account): exact domains converge in both directions including removals, the
  accepted count stays stable across repeat exchanges, a fresh join receives
  the group name while selections stay local, a pre-link website backfills at
  the first exchange after linking, and an offline save reports retryable and
  then completes on reconnect. Re-linking within minutes of removal plus
  establish adopted a stale key and reported completed inside a ghost zone;
  settling (~8 min here) before re-linking healed it. See the [execution
  record](../../tasks/executions/sync-011-policy-convergence.md).
- `user-confirmed` (2026-09-11): `SYNC-014` records a device-local tombstone
  of removed workspace identifiers and refuses to adopt that workspace again
  on the device that performed the removal. Retryable uses the existing
  unlinked copy; the recipe states the settle rule and the ghost recovery
  (press **Remove workspace** again). A fresh install without a tombstone can
  still join a ghost during the provider purge window. See the [task
  brief](../../tasks/specifications/sync-014-removal-hardening.md).
- `user-confirmed` (2026-09-11): `SYNC-015` replaces zone deletion with
  record deletion (anchor and bundle records inside the exact zone, bundles
  first and the anchor last, enumerated and absence-verified through the
  looped changes traversal, never a query) and keeps the zone, which the app
  never deletes, closing the purge-window class by construction. A linked
  peer that finds the zone without the anchor reports anchor-missing
  action-required and clears only its own key and local state on removal. A
  consume-loop skip drops foreign-context bundles as progress, and a fresh
  attempt with no anchor sweeps leftovers before minting (re-reading the
  anchor first, so a concurrent winner is adopted, never swept). The
  tombstone stays unchanged. See the [task
  brief](../../tasks/specifications/sync-015-record-removal.md).
- `observed` (2026-09-11, repository): the `SYNC-015` removal, peer,
  deletion-entry, foreign-context, sweep, and adapter behaviors above pass
  over fakes and both native targets (`jvmTest` including `commonTest`,
  `iosTest` adapter cases, both Swift suites, `./gradlew quality` green);
  existing removal, different-anchor, and tombstone cases are unchanged and
  green. The timed physical matrix in both shapes and directions (`AC-04`)
  passed 2026-09-12 (Mac-first S1/S2 and iPhone-first R1/R2, each with a
  11–14 minute settle; fixture arrived over sync in every shape; peer
  removals never deleted live records), so the recipe retires the settle
  rule.
- `observed` (2026-09-11 physical gate): re-linking the Mac about two and a
  half minutes after removal, with the iPhone still linked, established a
  fresh identifier that reported completed, yet the website added while
  unlinked never reached the iPhone and the Mac later reported action
  required while keeping its established row. `inferred`: the tombstone
  never fired, because a completed establish cannot have matched a
  tombstoned anchor; the purge-window variant is outside `SYNC-014`. The
  second run (both
  devices removed, about one minute) converged normally. The fix direction
  (unique zone identity per establish, a removal that deletes records and
  keeps the zone, or the settle rule as the MVP limit) is `open` for a later
  roadmap revision; until then the recipe's settle rule applies.

## Open production questions

- `observed` (2026-09-16, `SYNC-017`): the Apple MVP promotes its schema by
  auditing the development container with `cktool` exports and deploying it in
  the CloudKit Console. Just-in-time development schemas add `QUERYABLE
  SORTABLE` to every field, so an index-free import precedes the deploy; the
  production schema then holds only `PosatoWorkspaceV1` and
  `PosatoEncryptedBundleV1` with their four `BYTES` fields and no index.
  Release builds of both platforms link, converge both directions, share a
  session, remove, and re-link against it. Quota is negligible: one edit or
  session record is about 350 bytes against the 65,536-byte cap, and a year of
  heavy use is about 2.3 MB. Deletions cost a record like additions, because
  format 1 has no compaction. Container ownership for forks stays `open`.
- How is a full iCloud account surfaced? `CKError.quotaExceeded` maps to
  retryable on both platforms, so the status is generic while local saves stay
  committed. `user-confirmed` (2026-09-16): disclosure only for the MVP.
- What retry policy is appropriate without a delivery SLA?
- How do portable authenticated completeness metadata and high-water marks
  distinguish rollback or deletion from incomplete first synchronization?
- How are portable enrollment, recovery, revocation, export, import, deletion,
  and transport migration presented and tested?
- When should portable-folder provider experiments begin?

### Session integration and recoverable terminal facts (`SYNC-012`)

- `observed` (2026-09-13, code and deterministic tests): a single session owner
  serializes native effects and checks the desired identity and deadline after
  waits. A generic native APPLIED result cannot establish which session is
  enforced. Successful empty enforcement and confirmed cleanup are distinct
  from an unknown state after reopening; both must converge without a loop.
  Confirmed cleanup applies only to non-active state: a subsequently adopted
  active identity still requires its own apply or explicit Resume outcome. The
  attended sequence exposed this distinction after an earlier session was cleared.
- `user-confirmed`: the PR correction includes future-start reevaluation.
  Host-owned ticks use the authenticated accepted projection, existing reducer,
  pending-intent gates and local terminal markers without waiting for another
  mailbox exchange or a Session-screen subscriber. Restoring this projection
  retains the consent and workspace gates. Expiry banking failure bars applying
  that identity even if the wall clock later moves backwards.
- `observed`: native iOS expiry acknowledgements are per identity. Atomic
  identity-specific files retain an already-attributed late expiry of A while
  B is scheduled or expires; reads also accept the legacy single record.
  Common persists each observed fact before acknowledgement or replacement
  apply. Unreadable displacement state withholds apply and offers retry.
  This does not establish a callback-generation guarantee for the existing
  fixed Device Activity name, or a bounded wakeup/delivery time.
- `observed` driver state and `user-confirmed` browser outcomes (2026-09-13):
  the attended signed Mac/iPhone matrix covers both start/end directions,
  iPhone expiry while Posato stays in background, Mac expiry away from Session,
  and reconnect after a missed session has expired without observed revival.
  The physical run exposed confirmed cleanup hiding the next Mac Resume action;
  the corrected sequence passes with actual blocking and cleanup. Device-local
  selections remain unchanged. Revision-specific evidence and limits are in the
  [execution record](../../tasks/executions/sync-012-session-convergence.md).

### MVP acceptance run (`MVP-001`)

- `observed` (2026-09-14): removing a populated workspace from iPhone needs the
  adapter to resume the bounded native pass; without it each press reported
  "Sync did not finish". The iOS adapter now resumes up to ten calls like the
  macOS adapter, and one physical press ends local-only. Acceptance evidence
  and limits are in the
  [execution record](../../tasks/executions/mvp-001-end-to-end-acceptance.md).
