# Cross-Device Synchronization

## Direction

- `user-confirmed`: the first workspace is Apple-only and uses CloudKit Private
  Database across a person's Macs and iPhones using the same Apple Account.
- `user-confirmed`: the application performs no provider login and operates no
  shared user-data backend.
- `user-confirmed`: a later portable workspace may use one user-selected
  synchronized directory across Apple, Android, and Linux.
- `user-confirmed`: each workspace has one active transport. Moving to a
  portable transport requires explicit migration to a new transport epoch;
  there is no live CloudKit-to-folder bridge.
- `user-confirmed`: synchronization is eventual, exposes a manual **Sync now**
  action, and promises neither device wake-up nor a delivery SLA.

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

`inferred`: the operation vocabulary and tests are strong design inputs, but
the MVP must confirm policy and membership semantics before freezing a wire
format.

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

The production database schema and migration policy remain open.

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
derivation with bounded canonical data. Exact providers, primitives, canonical
encoding, format versions, nonce policy, key wrapping, and dependency versions
must be selected through the production threat model. No plaintext fallback is
acceptable.

Application-layer encryption does not make CloudKit metadata anonymous. The
provider may still observe account, container, timing, sizes, record counts,
and the metadata required for mailbox routing.

## Keys, enrollment, recovery, and revocation

The feasibility design separated:

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

The MVP must decide whether multi-device enrollment, recovery, and membership
management belong in its first scope.

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
- action-required failure.

A local success means the local synchronization transaction completed. It must
not claim that every other device has received the update.

## Open production questions

- Is synchronization in the first MVP slice or a later vertical slice?
- What exact policy operations and conflict semantics become format version 1?
- Which cryptographic providers and encodings satisfy all selected targets?
- How are production CloudKit schema, environment promotion, quota, and
  container ownership managed for official builds and forks?
- What onboarding and recovery promises can be explained safely to users?
- What retry policy is appropriate without a delivery SLA?
- How are archive export, import, workspace deletion, and transport migration
  presented and tested?
- When should portable-folder provider experiments begin?
