# ADR 0006: Apple MVP Encrypted Operation and Convergence Contract

## Status

- **Status:** Accepted
- **Date:** 2026-08-28
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`

## Context

ADR 0002 requires one transport-neutral application-encrypted format, signed
immutable operations, deterministic reduction, author sequencing, replay
detection, and preservation of the last valid local state. The accepted Apple
MVP synchronizes exact-domain policy, one semantic application policy, and
active-session intent between one Mac and one iPhone. Apple Account and iCloud
Keychain trust are the complete membership boundary; Posato must not add a QR,
invitation, approval, or independent per-installation revocation ceremony.

The feasibility repository proved that bounded canonical bytes, AES-GCM,
Ed25519, per-author sequences, hybrid logical clocks, deterministic reduction,
and cross-target vectors can satisfy the tested topology. Its format also
included schedules, explicit membership, key agreement, revocation, and key
distribution for a later portable workspace. Its custom wire bytes, split
cryptographic dependencies, deterministic-signature requirement, limits, and
model version are experiment evidence rather than production authority.

This decision freezes only the contract needed by `SYNC-002` and the Apple
bootstrap decision in `SYNC-003`. Database layout, CloudKit records, Keychain
attributes, retry orchestration, UI, and portable-workspace membership or
completeness remain with their named tasks.

## Decision

### One immutable operation per encrypted bundle

Format version 1 stores exactly one operation in each immutable encrypted
bundle. Publishing never edits or re-encrypts an existing bundle. Duplicate
delivery of the same bundle is idempotent. A reused bundle or operation
identifier with different canonical bytes is an integrity failure.

One-operation bundles avoid batching state, partial acceptance, cross-author
bundles, and re-signing. A later format may add batching only after a measured
CloudKit need and a compatibility decision.

The bundle has three byte regions:

1. a fixed 108-byte canonical header used as AES-GCM additional authenticated data;
2. one encrypted canonical operation followed by the 16-byte GCM tag; and
3. one 64-byte Ed25519 signature over a domain-separated concatenation of the
   complete header and ciphertext-with-tag.

The header exposes only:

- four-byte magic and unsigned 16-bit format and algorithm-suite numbers;
- 16-byte bundle, workspace, transport-epoch, and key-epoch identifiers;
- one 32-byte random per-bundle salt; and
- the unsigned 32-bit ciphertext length.

The author identifier, public key, sequence, and operation identifier remain
encrypted. The AES-GCM nonce is implicit and is not transmitted.

The operation identifier stays encrypted. Format 1 requires the bundle and
operation identifiers to be equal UUIDv4 values encoded as 16 raw bytes. This
removes one routing identifier while preserving a stable operation identity.
Workspace, transport-epoch, key-epoch, author, and session identifiers are also
UUIDv4 raw bytes. The all-zero singleton application-policy identifier is the
only exception and is not interpreted as a UUID.
Transport records may expose the bundle identifier and exact byte length, but
must not duplicate protected policy or session fields.

The complete provider-visible format-1 metadata boundary is the format and
suite, per-operation bundle identifier and salt, workspace, transport-epoch,
and key-epoch identifiers, ciphertext size, and transport-level account,
timing, and record-count association. Author identity, public key, sequence,
operation kind, domain, policy, and session data remain encrypted.

### Canonical format

Format 1 uses a small Posato-owned positional binary encoding. It has no maps,
optional fields, reflection, locale-sensitive conversion, platform serializer,
or unknown-field preservation. Integers are unsigned big-endian except signed
epoch milliseconds. Strings are strict UTF-8 with an unsigned 16-bit byte
length. Lists carry an unsigned 16-bit count and are encoded in their specified
canonical order. Boolean values are one byte, `0` or `1`.

Decoding rejects trailing bytes, invalid UTF-8, non-canonical identifiers,
unknown tags or versions, invalid enum values, out-of-order or duplicate list
members, integer overflow, and values outside the limits below. A successful
decoder must re-encode to identical bytes before returning an operation.

The following grammar is normative. `u8`, `u16`, `u32`, and `u64` are unsigned
big-endian integers. `i64` is a big-endian two's-complement signed integer.
`bytes[n]` has exactly `n` bytes. Adjacent fields are concatenated without
padding. Offset zero is the first byte.

| Header offset | Width | Field and required value |
| --- | --- | --- |
| 0 | 4 | ASCII `PSE1` |
| 4 | 2 | envelope format `1` |
| 6 | 2 | algorithm suite `1` |
| 8 | 16 | bundle identifier |
| 24 | 16 | workspace identifier |
| 40 | 16 | transport-epoch identifier |
| 56 | 16 | key-epoch identifier |
| 72 | 32 | random bundle salt |
| 104 | 4 | ciphertext-with-tag length |

The plaintext fields, in exact order, are:

```text
bytes[4]  ASCII "PSO1"
u16       operation format = 1
bytes[16] operation identifier
bytes[16] workspace identifier
bytes[16] transport-epoch identifier
bytes[16] key-epoch identifier
bytes[16] author identifier
bytes[32] Ed25519 public key
u64       author sequence
i64       HLC physical epoch milliseconds
u16       HLC logical counter
u8        operation-kind tag
bytes[n]  kind payload defined below
```

The operation, workspace, and epoch values must equal their header values; the
operation identifier must equal the header bundle identifier.

The AES-GCM additional authenticated data is the complete 108-byte header. The
signature preimage is exactly the UTF-8 bytes of
`app.posato.sync.signature.v1`, then `u32(108)`, the header, then a `u32`
ciphertext-with-tag length and the ciphertext-with-tag. The two length values
must equal the corresponding encoded byte counts.

The version-1 operation kinds are closed:

| Kind | Canonical key and payload | Reduction |
| --- | --- | --- |
| `1` `author-register` | Empty | Registers only the encrypted author and public key at sequence 1. |
| `2` `domain-present` | `u16` domain byte length, then canonical ASCII domain bytes | Marks that exact domain present. |
| `3` `domain-absent` | `u16` domain byte length, then canonical ASCII domain bytes | Marks that exact domain absent. |
| `4` `application-policy-present` | 16-byte singleton policy identifier, `u16` name byte length, then canonical UTF-8 name | Replaces the shared semantic policy; device mappings remain local. |
| `5` `application-policy-absent` | 16-byte singleton policy identifier | Removes the shared semantic policy, never a local mapping. |
| `6` `session-start` | 16-byte session identifier, `i64` start epoch milliseconds, `i64` mandatory-end epoch milliseconds | Proposes one bounded active-session intent. |
| `7` `session-end` | 16-byte session identifier | Permanently ends that session identifier; normal expiry is derived locally. |

The singleton application-policy identifier is the all-zero 16-byte value in
format 1. This encodes the accepted one-policy MVP without inventing a generic
policy collection. Its display name is Unicode NFC, has no control characters,
and is 1 through 80 UTF-8 bytes. `TARGETS-002` owns the user-facing validation
and copy but may not broaden these wire limits without a format decision.

An exact domain is the already accepted lowercase canonical ASCII value from
`ExactDomain`, 3 through 253 bytes. A session starts no earlier than the signed
start instant, ends strictly after it, and lasts at most 24 hours. `SESSION-001`
may select a smaller product maximum. Policy resolution during a session remains
current-state based in format 1: a session operation does not embed or freeze a
policy snapshot. This is the smallest contract consistent with synchronized
active-session intent and avoids a second policy representation.

### Bounds

Implementations reject before allocation beyond these limits:

| Value | Format-1 limit |
| --- | --- |
| Complete bundle | 64 KiB |
| Header | exactly 108 bytes |
| Plaintext operation | 32 KiB |
| Ciphertext and tag | plaintext length plus 16 bytes |
| Operations per bundle | exactly 1 |
| Identifier | exactly 16 bytes |
| Ed25519 public key / signature | RFC 8032 compressed 32-byte public key, not X.509 SubjectPublicKeyInfo / 64 raw signature bytes |
| AES-GCM key / nonce / tag | exactly 32 / 12 / 16 bytes |
| Domain | 3 through 253 ASCII bytes |
| Effective synchronized domain set | at most 2,048 present domains |
| Application-policy name | 1 through 80 UTF-8 bytes after NFC normalization |
| Author sequence | 1 through signed 64-bit maximum |
| HLC physical time and session instants | 0 through 4,102,444,800,000 epoch milliseconds |
| HLC logical counter | 0 through 65,535 |
| Session duration | greater than 0 and at most 24 hours |

The deliberately loose byte ceilings leave room for versioned framing while
remaining far below CloudKit asset-scale data. `SYNC-002` may lower an internal
allocation threshold if every valid format-1 value remains accepted.

### Cryptography and provider ownership

Algorithm suite 1 is HKDF-SHA-256, AES-256-GCM with a 128-bit tag, and
Ed25519:

- each bundle content-encryption key is derived with HKDF-SHA-256 from the
  32-byte workspace key and the header's 32 cryptographically random salt
  bytes. The 32-byte output uses exact info bytes comprising the UTF-8 domain
  `app.posato.sync.bundle-key.v1`, workspace identifier, transport-epoch
  identifier, key-epoch identifier, and bundle identifier in that order;
- the 96-bit nonce is exactly 12 zero bytes and is implicit. Exactly one AES-GCM
  seal is permitted for each derived bundle key. A retry reuses the persisted,
  immutable complete bundle bytes and never seals again. Reusing a bundle
  identifier with different canonical bytes fails closed;
- bundle identifiers are UUIDv4 values and bundle salts are generated with the
  platform CSPRNG before derivation. The salt is authenticated as part of the
  header and is not secret;
- AES-GCM authenticates the complete canonical header as additional data;
- the signature input uses the distinct domain
  `app.posato.sync.signature.v1` and length-prefixes the header and complete
  ciphertext-with-tag;
- signature verification accepts any standards-valid Ed25519 signature and
  never requires byte-identical signature generation; and
- there is no plaintext, unsigned, unauthenticated, or algorithm-fallback path.

Common Kotlin owns HKDF, the bytes, validation order, typed outcomes, and vectors
behind one narrow cryptographic interface. The macOS JVM implementation uses
the standard JCA/JCE `HmacSHA256`, `AES/GCM/NoPadding`, `Ed25519`, and `SecureRandom`
facilities supplied by the selected JDK. The iOS implementation uses CryptoKit
`HKDF`, `AES.GCM`, `Curve25519.Signing`, and system randomness behind a Kotlin/Native or
thin Swift leaf selected by `SYNC-002`. Provider exceptions and native types do
not cross the interface.

No third-party runtime cryptography dependency is accepted by this ADR. Both
selected platform providers expose the required standard algorithms, avoid the
PoC's experimental libsodium wrapper, and remove the false requirement for
deterministic native signatures. If implementation vectors reveal incompatible
standard encodings or provider behavior, `SYNC-002` must stop and propose a
reviewed contract amendment rather than silently add a provider or change wire
bytes.

An Apple authoring Ed25519 private key exists only in process memory for one
local replica-writer incarnation. Posato never persists, synchronizes, exports,
or diagnoses it. `SYNC-002` owns that lifecycle behind the narrow provider
interface; `SYNC-003`, `SYNC-005`, and `SYNC-006` do not create a Keychain item
for it. Managed runtimes and CryptoKit may retain implementation-owned copies;
Posato clears mutable byte arrays it owns where supported and makes no
guaranteed-memory-erasure claim.

### Automatic Apple author registration

Workspace-key possession is the Apple MVP admission proof. Each successful
local replica-writer open starts without an author. Its first local mutation
lazily creates a random author identifier and Ed25519 key pair in memory, then
prepares two immutable bundles: `author-register` at sequence 1 and the
requested business operation at sequence 2. One local transaction accepts and
projects both operations, stores both pending bundles, and advances local HLC
state before either bundle may be published. An open that creates no mutation
creates no author or registration operation.

The active authoring incarnation lives no longer than that writer open. Its
private key and next sequence stay in memory, and the sequence never decreases
from a storage observation during the open. Close, crash, or reopen retires the
incarnation; the next mutation creates a fresh author. Previously committed
pending bundles remain publishable byte-for-byte without the private key.
Transport should publish registration first when possible, but reordered
delivery remains valid and uses the bounded unknown-author staging rule below.

For an unknown author, validation first derives the bundle key, authenticates
and decrypts the bundle, then verifies the signature with the public key in the
canonical plaintext. Complete AEAD authentication proves workspace-key
possession; the signature binds authorship to that encrypted key. A sequence-1
`author-register` registers the author and accepted operation atomically.

A fully authenticated, canonical unknown-author operation with sequence greater
than 1 is deferred, not rejected. The replica retains at most 32 such bundles
per unknown author and 128 total, ordered by sequence and identifier. Staging it
may advance an opaque transport cursor because the exact bytes are durable and
will be reconsidered after registration. A bundle outside those caps produces
a retryable capacity outcome and must not consume transport progress unless the
adapter can request the exact bundle again. An invalid signature, AEAD, context,
key/identifier relation, or canonical operation is rejected and never staged.

For a known author, the stored public key must equal the decrypted key and
verify the signature. A second registration, changed key, changed
identifier, sequence-1 conflict, or author-key mismatch is rejected. Format 1 has no Posato approval,
key-agreement key, membership list, revocation, or key-epoch transition
operation. An Apple-trusted installation with the workspace key can register
authors or decrypt data; that is accepted residual risk `R-01`, not a weakness
that a second ceremony may silently narrow.

### Validation and atomic apply

Remote bytes are untrusted. Validation uses this order and never mutates the
replica before every applicable step succeeds:

1. enforce the complete-bundle limit and parse only the fixed bounded header;
2. reject unknown format or suite and invalid lengths before allocation;
3. confirm expected workspace, transport epoch, and key epoch;
4. reject an already accepted or staged bundle identifier with different bytes;
5. derive the exact bundle content-encryption key from the header context and
   bundle salt;
6. authenticate and decrypt AES-GCM using the header as additional data;
7. strictly decode, validate, and canonicalize the operation and extract its
   author identifier, public key, and sequence;
8. verify all duplicated context, identifier equality, signature, author rule,
   sequence rule, HLC bounds, operation kind, and payload invariants;
9. reduce the complete accepted operation set deterministically; and
10. in one transaction, store the accepted immutable operation, update retained
    author sequence state, project visible state, mark the bundle accepted, and
    only then advance transport progress.

Failure returns one bounded category: `unsupported-version`, `malformed`,
`oversized`, `wrong-context`, `unknown-author`, `invalid-signature`,
`authentication-failed`, `replay-conflict`, `sequence-conflict`,
`invalid-operation`, or `deferred-capacity`. It returns no provider text, protected value, key,
ciphertext, identifier, or plaintext operation. Duplicate identical input is a
successful no-op. Cancellation is propagated and never mapped to a failure.

### Sequence, time, and convergence

Each author allocates a strictly increasing unsigned sequence starting at 1.
Publication retry reuses the same immutable operation, bundle, and sequence. A
sequence already accepted with different bytes is an integrity failure.

`SYNC-002` owns one active authoring incarnation at a time and serializes local
creation and remote acceptance through the same replica state machine. Before a
commit attempt it prepares stable identifiers, canonical bytes, signatures,
and the complete required HLC reservation. The first mutation prepares
registration and business operations together; later mutations prepare one
operation. No prepared bundle is publishable before its local transaction
commits.

Every commit attempt retains its exact prepared identifiers and bytes until the
result is resolved. After an ambiguous result, the store must inspect the exact
transactional footprint. A complete byte-identical operation or first-operation
pair, pending bundles, projection, author state, and HLC state means success. If
complete absence is proven, only the same prepared bytes may be retried.
Partial state, different bytes, or inability to prove presence or absence
returns action-required `local-commit-uncertain`, retires and freezes that
writer, and forbids an automatic retry or replacement incarnation. A
preparation failure after sequence assignment likewise retires the incarnation
unless the exact prepared operation remains available. Crash recovery must
therefore expose either the complete committed transaction or no part of it.

The in-memory sequence never decreases, so storage rollback while the writer
remains open cannot reuse a sequence. Every successful serialized local or
remote transaction atomically advances the writer's in-memory checkpoint to
the exact retained accepted-operation bytes, author sequence state, and durable
HLC/exhaustion state that transaction committed. A greater observed state may
be adopted later only after its exact accepted remote-operation transaction
footprint is verified.

Before preparing any later mutation, the writer compares durable state with
that checkpoint. Any missing, regressed, different, or otherwise unexplained
element returns `local-commit-uncertain`, freezes and retires the writer, and
emits no next-sequence bundle. This prevents a missing unpublished operation
from creating an unfillable local gap and prevents same-open rollback from
lowering HLC or clearing exhaustion without falsely rejecting a legitimate
remote advance. Only a consistent reopen may resume local creation, with a
fresh author.

A restore followed by reopen cannot restore the private identity and uses a
fresh author. The local database still retains accepted per-author sequence
state for replay, equivocation, gap, and reordered-delivery handling; there is
no secure author record or signing-key Keychain item.

Delivery may be duplicated, reordered, or contain gaps. A higher sequence does
not require rejecting the bundle solely because lower sequences are not yet
visible. It is stored as validated but remains unapplied behind the gap; the
last valid projection remains visible. Reduction resumes when missing
operations arrive. This avoids the PoC reducer's fresh-history assumption while
retaining per-author equivocation and gap detection.

Every operation carries a Hybrid Logical Clock value of signed 64-bit epoch
milliseconds plus the bounded logical counter. A fresh replica initializes its
durable HLC state as `active((0, 0))`; afterward the state is either
`active(last)` or terminal `hlc-exhausted(last)`.

The bounded successor of `(physical, logical)` is `(physical, logical + 1)`
when the logical counter is below 65,535, `(physical + 1, 0)` when the logical
counter is 65,535 and physical time is below 4,102,444,800,000, and absent for
the terminal tuple `(4,102,444,800,000, 65,535)`.

A local commit attempt samples the wall clock exactly once before reservation
and validates it. A physical value outside the format-1 range returns
recoverable action-required `clock-out-of-range` without creating an author,
reserving HLC values, sealing bytes, or mutating state. For a valid sample, the
first reserved value is `(wallClock, 0)` when `wallClock > last.physical` and is
the bounded successor of `last` otherwise. Every further value in the same
batch is the bounded successor of the previously reserved value. Preparation,
ambiguous-commit reconciliation, and retry of the same prepared bytes neither
resample the wall clock nor reallocate the batch.

Local creation reserves the complete HLC batch before sealing: two reserved
values for registration plus the first business operation, and one reserved
value for a later operation. If two values remain, the first mutation may use
the penultimate and terminal tuples and commits `hlc-exhausted` atomically with
the two operations. If the complete batch cannot be represented, local creation
commits no operation, records terminal `hlc-exhausted` in an otherwise
state-only transaction, and returns that outcome; it never wraps, clamps,
reuses a tuple, or partially registers an author.

Accepted remote input compares the retained local and remote HLC values in HLC
order and advances to the bounded successor of the greater value; remote
acceptance does not sample the wall clock. If that successor is the terminal
tuple, or no successor exists because the remote or retained value is already
terminal, the same acceptance and projection transaction records
`hlc-exhausted`. Remote operations remain valid, accepted, and projectable
while the local clock is exhausted; only future local operation creation is
refused. Startup treats a retained or accepted terminal HLC as exhausted and
fails closed on inconsistent clock state. Reopen, author rotation, or clock
repair does not clear genuine exhaustion. Recovery requires a separately
accepted workspace-reset or format/epoch migration decision.

Wall-clock skew within the valid range changes conflict winners but cannot
bypass session bounds or extend a mandatory end after it has passed locally.

Each replica stores a terminal local expiry marker keyed by the encrypted
session identifier, without an observed timestamp. When an evaluation first
observes `t >= mandatoryEnd`, it commits that marker before exposing the session
as inactive or clearing enforcement. That session remains inactive after
restart, wall-clock rollback, or discovery of another start for the same
identifier. The marker is local, never synchronized or diagnosed, and is
retained only while its referenced session-start operations remain. `SYNC-002`
exposes the session identity, `SESSION-001` owns the terminal expiry rule, and
`SESSION-002` owns its atomic enforcement integration.

All replicas with the same valid operation set derive the same synchronized
projection. Replicas with that projection, the same evaluation instant `t`,
and the same local terminal-expiry facts derive the same effective state:

- applicable domain operations are reduced from scratch in ascending global
  total order whenever the complete applicable operation set changes. Every
  authenticated operation remains accepted in immutable history; `applied`,
  `no-op`, and `domain-capacity` are derived projection outcomes, not permanent
  validation decisions. A `domain-present` for an existing domain is a no-op;
  for an absent domain below the 2,048-domain cap it adds the domain; at the cap
  it has a `domain-capacity` outcome without state change or eviction. A
  `domain-absent` removes a present domain and is otherwise a no-op. Arrival of
  an earlier-total-order operation may therefore reclassify later outcomes. A
  later removal does not retroactively apply an earlier capacity outcome, while
  a distinct still-later presence may use the freed capacity. The current
  projection exposes every capacity outcome as a truthful conflict requiring
  action;
- the singleton application policy uses the greatest total-order key for its
  singleton identifier;
- session starts are grouped by their encrypted session identifier. Exactly one
  distinct immutable start is eligible; two or more produce a derived
  `session-conflict` outcome for every start in the group and no start for that
  identifier is eligible. This quarantine is recomputed from the complete
  applicable set and is independent of delivery order. Any valid matching
  `session-end`, regardless of relative order, permanently ends that identifier;
- among eligible session starts whose signed start is not after `t`, the greatest
  total-order start is the sole current candidate; future starts do not suppress
  it, while an ended or expired current candidate yields no active session and
  never falls back to an older candidate;
- a current candidate is active only when it has neither a matching end nor a
  terminal marker and `start <= t < mandatoryEnd`; and
- total order is `(HLC physical, HLC logical, author identifier, operation
  identifier)`, comparing raw bytes unsigned where applicable.

Unknown versions and kinds are rejected rather than ignored, so a format-1
replica never projects partial semantics. Format or algorithm changes require a
new suite or format, cross-target vectors, an explicit compatibility decision,
and a migration path. The Apple MVP does not negotiate the highest common
version and does not downgrade.

## Consequences

- `SYNC-002` can implement one small closed model with deterministic JVM/iOS
  golden vectors and tamper, wrong-context, replay, gap, skew, registration,
  and convergence tests.
- CloudKit remains a mailbox. It cannot choose winners, supply identity, or
  replace the local accepted-operation history.
- Apple author registration remains automatic and truthfully no stronger than
  workspace-key possession, while independent author signatures still detect
  forged operations from a transport-only attacker.
- Ephemeral authoring incarnations prevent a restored local store from reusing
  an author sequence without requiring online reconciliation or a persistent
  signing identity. They create more encrypted author registrations over time;
  format 1 adds no speculative author cap, garbage collection, or compaction.
- A terminal HLC preserves inbound convergence but permanently blocks local
  authoring for that workspace until a separately accepted recovery decision.
- Per-operation bundles trade additional record overhead for simpler atomicity,
  signing, retries, deduplication, and review. Batching is deferred until there
  is evidence that it is needed.
- The custom positional codec is intentionally small but security-sensitive;
  it requires cross-target golden vectors and strict decoder tests rather than
  a general serialization framework.
- Portable membership, revocation, key wrapping, rollback completeness, and
  migration require a later format decision and are not hidden extensions of
  format 1.
- The current local-only 1,024-domain implementation limit is not format-1
  product authority. `SYNC-002`, as the first synchronized model consumer, must
  support the 2,048-domain projection without changing that unrelated code in
  this documentation-only task.

## Alternatives considered

### Adopt the PoC format and dependencies

Rejected. They contain portable membership and schedule scope, an experimental
libsodium wrapper, a deterministic-signature test constraint, and prototype
identifiers and limits that the MVP does not need.

### Add a KMP cryptography dependency now

Rejected for format 1. Current cryptography-kotlin exposes the required
algorithms and providers, but the Apple MVP already has two supported platform
providers and no non-Apple runtime consumer. A new runtime dependency and its
provider-selection surface have no present consumer that system APIs cannot
serve. Reconsider only with a concrete target or interoperability failure.

### Use HMAC instead of device signatures

Rejected. A shared MAC authenticates workspace-key possession but cannot bind
an operation to one authoring incarnation, so it cannot satisfy the accepted
signed-author and per-author sequence contract.

### Persist the Apple signing identity or reconcile before every mutation

Rejected. A `ThisDeviceOnly` Keychain item may be restored to the same device
that created its backup, so it cannot distinguish coordinated rollback of the
database and secure record. A mandatory CloudKit reconciliation would make
every writer open depend on service availability and contradict local-first
offline mutation. A Secure Enclave anchor, hardware counter, new wire operation,
or server generation has no minimum format-1 consumer and does not replace the
required atomic local state machine.

### Require an existing Posato installation to approve an author

Rejected by ADR 0002. It would recreate the second membership ceremony removed
from the Apple MVP.

### Batch operations or encode generic extensible maps

Rejected until measured need. Both add partial-acceptance, canonicalization,
unknown-field, and compatibility behavior without improving the two-device MVP
contract.

## Required implementation evidence

Before `SYNC-002` is complete:

- byte-identical canonical header, operation, AAD, and ciphertext vectors pass
  on the macOS JVM and physical iPhone provider implementations;
- the 108-byte header is byte-identical, author metadata is absent from it,
  HKDF vectors bind every specified context field, immutable retry reuses exact
  bytes, and no bundle key is used for more than one seal;
- an idle writer creates no author; its first mutation atomically commits
  registration at sequence 1 and the requested operation at sequence 2; later
  mutations increase the same in-memory sequence, while close, crash, reopen,
  coordinated restore, and same-open storage rollback cannot reuse an accepted
  `(author, sequence)` pair;
- after a committed but unpublished operation, same-open loss or regression of
  its accepted bytes, author sequence, HLC, or exhaustion state freezes the
  writer with no next bundle; consistent reopen uses a fresh author and never
  leaves a newly emitted operation behind an unfillable local sequence gap;
- nonterminal remote acceptance between local mutations atomically advances the
  writer checkpoint and permits the next correct HLC successor; unexplained HLC
  change freezes the writer, while terminal remote acceptance advances the
  checkpoint to exhaustion and refuses later local creation;
- commit-before, commit-after, ambiguous-result, partial-state, preparation
  failure, and crash boundaries either recover the exact immutable prepared
  operation or pair, prove absence and retry those same bytes, or fail closed
  with `local-commit-uncertain` without duplicating the business mutation;
- reordered business-before-registration delivery stages within the existing
  caps and converges after registration, while pending bundles from a retired
  incarnation retry without the private key;
- HLC tests cover the fresh `(0, 0)` baseline; future, equal, and regressed wall
  samples; one wall sample for the registration-plus-business batch; logical
  carry into the next physical millisecond; two, one, and zero remaining
  values; local and remote advancement to the terminal tuple; direct remote
  terminal input; restart and a new author incarnation; inbound projection
  after exhaustion; no wrap or clamp; and recoverable `clock-out-of-range`
  after wall-clock correction;
- the 2,048th and 2,049th domain, an earlier removal arriving after a later
  capacity outcome, a later removal followed by a distinct presence, and
  randomized delivery permutations produce equal state and derived audit;
- expiry commits a marker for the session identifier before exposing
  inactivity; restart, wall-clock rollback, or a competing start cannot
  reactivate it. Competing starts in either delivery order produce the same
  quarantined projection and derived audit;
- signatures verify across both targets, without requiring identical signature
  bytes;
- nonce, key, size, version, unknown-kind, truncation, trailing-byte,
  non-canonical, wrong-context, wrong-key, tamper, signature, author-registration,
  replay, sequence-conflict, gap, duplicate, reordering, and clock-skew cases
  preserve the last valid state;
- randomized delivery orders and duplicate sets converge to equal projected
  state and an equal canonical digest;
- a local mutation and pending immutable bundle commit atomically, and remote
  acceptance, projection, deduplication, and progress commit atomically; and
- dependency and source scans prove there is no plaintext fallback, fixed
  production key or nonce, provider exception leakage, secret logging, or
  unreviewed runtime cryptography dependency.

Physical CloudKit and Keychain bootstrap evidence belongs to `SYNC-003` through
`SYNC-009`; ongoing mailbox publication, consumption, and account-isolation
evidence additionally belongs to `SYNC-010`.

## Evidence and sources

- `observed`: `.research/blocker` revision
  `bcdc8ce9b91ecb7569c2d98b568d5fd64c25455c`, especially
  `docs/experiments/apple-sync-poc-wire-and-crypto.md`, `BundleCodec.kt`,
  `Reducer.kt`, and the cross-target golden vectors, proves only the bounded PoC
  behavior summarized above.
- [Apple CryptoKit](https://developer.apple.com/documentation/cryptokit) exposes
  AES-GCM, Ed25519, and system key generation and recommends the higher-level
  framework over lower-level interfaces.
- [Apple AES.GCM.Nonce](https://developer.apple.com/documentation/cryptokit/aes/gcm/nonce)
  requires nonce uniqueness for encryption calls.
- [Apple Keychain accessibility](https://developer.apple.com/documentation/security/restricting-keychain-item-accessibility)
  states that `ThisDeviceOnly` items may be restored to the same device that
  created the backup, so that attribute is not a coordinated-rollback anchor.
- [Java `Cipher`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html)
  requires AES/GCM support and distinct IVs; [Java `SecureRandom`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandom.html)
  supplies cryptographically strong random bytes.
- [NIST SP 800-38D](https://nvlpubs.nist.gov/nistpubs/legacy/sp/nistspecialpublication800-38d.pdf)
  requires IV uniqueness for distinct authenticated-encryption inputs under a
  given key; suite 1 derives a new key for each single-seal bundle.
- [RFC 5869](https://www.rfc-editor.org/rfc/rfc5869.html) defines HKDF and the
  context-binding `info` input used by suite 1.
- [RFC 8032](https://www.rfc-editor.org/rfc/rfc8032.html) defines Ed25519.
- [cryptography-kotlin 0.6.0](https://github.com/whyoleg/cryptography-kotlin/releases/tag/0.6.0)
  was reviewed as the narrow third-party alternative: Apache-2.0, current at
  review time, and compatible with JDK and CryptoKit providers, but unnecessary
  for the two selected Apple MVP targets.
