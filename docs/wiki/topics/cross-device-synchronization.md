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

`observed` (2026-08-31): `SYNC-002` implemented this core in the production
shared module with immutable accepted, pending, and staged bundle bytes,
durable replica checkpoints and terminal expiry facts, and opaque transport
progress committed in the same SQLDelight transaction. Migration verification,
reopen validation, ambiguous-commit reconciliation, duplicate and reordered
delivery, bounded capacity, terminal HLC behavior, and deterministic convergence
passed on the JVM, iOS Simulator, and a physical iPhone. Concrete CloudKit
cursor and sync-engine state remain deferred to their transport task.

`observed` (2026-08-31): a hosted review correction made reopen fail closed
when durable HLC state is below any retained accepted operation clock. Rejected
and deferred-capacity bundles now share the same rule: opaque transport progress
advances only when exact refetch remains available, using the existing exact
commit-reconciliation boundary.

`observed` (2026-08-31): a later hosted review correction rejects invalid local
session mutations before reserving authoring resources, so the open writer
remains usable. Reopen also revalidates each accepted author's unique sequence-1
registration, stable signing key, unique sequences, and absence of later
registration while continuing to permit sequence gaps.

`observed` (2026-08-31): reopen now also revalidates staged state against the
live acceptance boundary. It rejects sequence 1, accepted-author or bundle-ID
overlap, duplicate author sequences, and per-author or global capacity overflow
while preserving valid staged gaps and competing pre-registration keys.

`observed` (2026-08-31): remote acceptance now checks the complete-bundle limit
on raw transport bytes before retaining an immutable copy. Exactly 64 KiB
continues to normal parsing, larger input returns the bounded `OVERSIZED`
outcome, and rejection preserves the existing exact-refetch progress rule.

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
primitive vectors. Portable key wrapping and provider selection remain later
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

## Open production questions

- How are production CloudKit schema, environment promotion, quota, and
  container ownership managed for official builds and forks?
- What retry policy is appropriate without a delivery SLA?
- How do portable authenticated completeness metadata and high-water marks
  distinguish rollback or deletion from incomplete first synchronization?
- How are portable enrollment, recovery, revocation, export, import, deletion,
  and transport migration presented and tested?
- When should portable-folder provider experiments begin?
