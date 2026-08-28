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

1. a fixed 144-byte canonical header used as AES-GCM additional authenticated data;
2. one encrypted canonical operation followed by the 16-byte GCM tag; and
3. one 64-byte Ed25519 signature over a domain-separated concatenation of the
   complete header and ciphertext-with-tag.

The header exposes only:

- four-byte magic and unsigned 16-bit format and algorithm-suite numbers;
- 16-byte bundle, workspace, transport-epoch, key-epoch, and author identifiers;
- the author's 32-byte Ed25519 public key and unsigned 64-bit sequence;
- one 12-byte nonce; and
- the unsigned 32-bit ciphertext length.

The operation identifier stays encrypted. Format 1 requires the bundle and
operation identifiers to be equal UUIDv4 values encoded as 16 raw bytes. This
removes one routing identifier while preserving a stable operation identity.
Workspace, transport-epoch, key-epoch, author, and session identifiers are also
UUIDv4 raw bytes. The all-zero singleton application-policy identifier is the
only exception and is not interpreted as a UUID.
Transport records may expose the bundle identifier and exact byte length, but
must not duplicate protected policy or session fields.

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
| 72 | 16 | author identifier |
| 88 | 32 | Ed25519 public key |
| 120 | 8 | author sequence |
| 128 | 12 | AES-GCM nonce |
| 140 | 4 | ciphertext-with-tag length |

The plaintext fields, in exact order, are:

```text
bytes[4]  ASCII "PSO1"
u16       operation format = 1
bytes[16] operation identifier
bytes[16] workspace identifier
bytes[16] transport-epoch identifier
bytes[16] key-epoch identifier
bytes[16] author identifier
u64       author sequence
i64       HLC physical epoch milliseconds
u16       HLC logical counter
u8        operation-kind tag
bytes[n]  kind payload defined below
```

The operation, workspace, epoch, author, and sequence values must equal their
header values; the operation identifier must equal the header bundle identifier.
The public key is not duplicated in plaintext.

The AES-GCM additional authenticated data is the complete 144-byte header. The
signature preimage is exactly the UTF-8 bytes of
`app.posato.sync.signature.v1`, then `u32(144)`, the header, then a `u32`
ciphertext-with-tag length and the ciphertext-with-tag. The two length values
must equal the corresponding encoded byte counts.

The version-1 operation kinds are closed:

| Kind | Canonical key and payload | Reduction |
| --- | --- | --- |
| `1` `author-register` | Empty | Registers only the header author and public key at sequence 1. |
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
| Header | exactly 144 bytes |
| Plaintext operation | 32 KiB |
| Ciphertext and tag | plaintext length plus 16 bytes |
| Operations per bundle | exactly 1 |
| Identifier | exactly 16 bytes |
| Ed25519 public key / signature | RFC 8032 compressed 32-byte public key, not X.509 SubjectPublicKeyInfo / 64 raw signature bytes |
| AES-GCM key / nonce / tag | exactly 32 / 12 / 16 bytes |
| Domain | 3 through 253 ASCII bytes |
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

- each author content-encryption key is derived with HKDF-SHA-256 from the
  32-byte workspace key, RFC 5869's absent-salt value of 32 zero bytes, a
  32-byte output, and exact info bytes comprising the
  UTF-8 domain `app.posato.sync.author-key.v1`, workspace identifier,
  key-epoch identifier, author identifier, and Ed25519 public key in that order;
- the 96-bit nonce is exactly four zero bytes followed by the author's `u64`
  sequence, so every author sequence uses a distinct nonce with its derived
  key; the header nonce must equal this construction and callers cannot provide
  another production nonce;
- sequence exhaustion fails closed before sealing and requires an explicit new
  format or key-epoch decision; it never wraps or reuses a nonce;
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

The device-local Ed25519 private key is non-synchronizable and non-exportable
through Posato behavior. Exact Keychain storage selectors and accessibility are
owned by `SYNC-003`, `SYNC-005`, and `SYNC-006`. Managed runtimes and CryptoKit
may retain implementation-owned copies; Posato clears mutable byte arrays it
owns where supported and makes no guaranteed-memory-erasure claim.

### Automatic Apple author registration

Workspace-key possession is the Apple MVP admission proof. Every installation
creates one device-local Ed25519 key pair and random author identifier. Its
first operation is `author-register`, at author sequence 1, encrypted with the
derived author key and self-signed by the header public key.

For an unknown author, validation first verifies the signature with the header
public key, derives the author content-encryption key, authenticates and decrypts
the bundle, and verifies that every header and operation field agrees. Complete
AEAD authentication proves workspace-key possession; the signature binds
authorship to the header key. A sequence-1 `author-register` registers the author
and accepted operation atomically.

A fully authenticated, canonical unknown-author operation with sequence greater
than 1 is deferred, not rejected. The replica retains at most 32 such bundles
per unknown author and 128 total, ordered by sequence and identifier. Staging it
may advance an opaque transport cursor because the exact bytes are durable and
will be reconsidered after registration. A bundle outside those caps produces
a retryable capacity outcome and must not consume transport progress unless the
adapter can request the exact bundle again. An invalid signature, AEAD, context,
key/identifier relation, or canonical operation is rejected and never staged.

For a known author, the stored public key must equal the header key and verifies
the signature before decryption. A second registration, changed key, changed
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
5. verify the signature with the stored matching key or the unknown author's
   header key, then derive the exact author content-encryption key;
6. authenticate and decrypt AES-GCM using the header as additional data;
7. strictly decode, validate, and canonicalize the operation;
8. verify all duplicated context, identifier equality, signature, author rule,
   sequence rule, HLC bounds, operation kind, and payload invariants;
9. reduce the complete accepted operation set deterministically; and
10. in one transaction, store the accepted immutable operation, update author
    high-water state, project visible state, mark the bundle accepted, and only
    then advance transport progress.

Failure returns one bounded category: `unsupported-version`, `malformed`,
`oversized`, `wrong-context`, `unknown-author`, `invalid-signature`,
`authentication-failed`, `replay-conflict`, `sequence-conflict`,
`invalid-operation`, or `deferred-capacity`. It returns no provider text, protected value, key,
ciphertext, identifier, or plaintext operation. Duplicate identical input is a
successful no-op. Cancellation is propagated and never mapped to a failure.

### Sequence, time, and convergence

Each author durably allocates a strictly increasing unsigned sequence starting
at 1. Publication retry reuses the same immutable operation and sequence. A
sequence already accepted with different bytes is an integrity failure.

Delivery may be duplicated, reordered, or contain gaps. A higher sequence does
not require rejecting the bundle solely because lower sequences are not yet
visible. It is stored as validated but remains unapplied behind the gap; the
last valid projection remains visible. Reduction resumes when missing
operations arrive. This avoids the PoC reducer's fresh-history assumption while
retaining per-author equivocation and gap detection.

Every operation carries a Hybrid Logical Clock value of signed 64-bit epoch
milliseconds plus the bounded logical counter. On local creation and accepted
remote input the durable local HLC advances by the standard maximum-plus-one
rule. Wall-clock skew changes conflict winners but cannot bypass session bounds
or extend a mandatory end after it has passed locally.

All replicas with the same valid operation set and the same evaluation instant
`t` derive the same state:

- domain presence uses the greatest total-order key for that canonical domain;
- the singleton application policy uses the greatest total-order key for its
  singleton identifier;
- for each session identifier, the lowest-total-order `session-start` is its
  single canonical start and every later start reusing that identifier is
  shadowed; any valid matching `session-end`, regardless of relative order,
  permanently ends that identifier;
- among canonical starts whose signed start is not after `t`, the greatest
  total-order start is the sole current candidate; future starts do not suppress
  it, while an ended or expired current candidate yields no active session and
  never falls back to an older candidate;
- a current candidate is active only when it has no matching end and
  `start <= t < mandatoryEnd`; and
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
  workspace-key possession, while independent device signatures still detect
  forged operations from a transport-only attacker.
- Per-operation bundles trade additional record overhead for simpler atomicity,
  signing, retries, deduplication, and review. Batching is deferred until there
  is evidence that it is needed.
- The custom positional codec is intentionally small but security-sensitive;
  it requires cross-target golden vectors and strict decoder tests rather than
  a general serialization framework.
- Portable membership, revocation, key wrapping, rollback completeness, and
  migration require a later format decision and are not hidden extensions of
  format 1.

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
an operation to one device-local author, so it cannot satisfy the accepted
signed-author and per-author sequence contract.

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

Physical CloudKit, Keychain propagation, account isolation, and one-workspace
bootstrap evidence belongs to `SYNC-003` through `SYNC-009`.

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
- [Java `Cipher`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html)
  requires AES/GCM support and distinct IVs; [Java `SecureRandom`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandom.html)
  supplies cryptographically strong random bytes.
- [NIST SP 800-38D](https://nvlpubs.nist.gov/nistpubs/legacy/sp/nistspecialpublication800-38d.pdf)
  recommends 96-bit GCM IVs for interoperability and simplicity.
- [RFC 5869](https://www.rfc-editor.org/rfc/rfc5869.html) defines HKDF and the
  absent-salt value used by suite 1.
- [RFC 8032](https://www.rfc-editor.org/rfc/rfc8032.html) defines Ed25519.
- [cryptography-kotlin 0.6.0](https://github.com/whyoleg/cryptography-kotlin/releases/tag/0.6.0)
  was reviewed as the narrow third-party alternative: Apache-2.0, current at
  review time, and compatible with JDK and CryptoKit providers, but unnecessary
  for the two selected Apple MVP targets.
