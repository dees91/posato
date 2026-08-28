# ADR 0002: Separate Synchronization Trust and Workspace Modes

## Status

- **Status:** Accepted
- **Date:** 2026-08-25
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`

## SYNC-001 amendment

`user-confirmed` (2026-08-28):
[ADR 0006](0006-apple-mvp-encrypted-operation-and-convergence.md) resolves the
deferred Apple MVP operation vocabulary, cryptographic primitives and provider
ownership, canonical format and versioning, automatic signed-author
registration, validation order, replay and sequence handling, and deterministic
convergence contract. Production implementation and cross-target evidence remain
with `SYNC-002`; Apple workspace bootstrap remains with `SYNC-003`.

For Apple mode, retained accepted-author sequence state detects replay and
equivocation, while a fresh process-memory authoring incarnation on every local
replica-writer open prevents sequence reuse after coordinated local restore.
This supersedes any broader wording below that could imply a persistent Apple
signing identity or a local high-water mark capable of detecting rollback after
all evidence of the later state has been erased. Portable completeness and
high-water behavior remain a later decision.

## Relationship to feasibility ADR 0001

The feasibility repository retains ADR 0001, *Apple-First Synchronization in
Kotlin Multiplatform and Compose Multiplatform*, at source revision
`bcdc8ce9b91ecb7569c2d98b568d5fd64c25455c`.

For the production direction, this ADR supersedes only ADR 0001's Apple-mode
workspace-key distribution, application membership, device admission,
enrollment, and revocation choices. It does not supersede the KMP and Compose
direction, the local-first replica, CloudKit Private Database transport,
synchronizable Keychain feasibility, shared encrypted bundles, signed
operations, deterministic validation and merge, or the one-active-transport
rule. ADR 0001 and the PoC results remain historical evidence rather than
product authority.

The maintained
[feasibility source digest](../wiki/sources/feasibility-research-seed.md)
preserves the evidence boundary without making the research checkout a
dependency of this repository.

## Context

The feasibility work proved an explicit application-managed enrollment model:
independent device keys, QR exchange, membership operations, per-device key
wrapping, key epochs, revocation, and optional recovery. That model remains
appropriate when a storage provider only supplies shared bytes and cannot
authenticate a Blocker device.

The Apple-first MVP has a narrower personal-workspace boundary. Its Mac and
iPhone use the same Apple Account, CloudKit Private Database, and iCloud
Keychain trust. Requiring a second Blocker approval ceremony would duplicate
Apple's system-level admission for this accepted threat model and would make
the first-run experience more complex than necessary.

Transport, payload encryption, workspace-key delivery, and device admission
are independent concerns. Simplifying one of them must not silently weaken the
others.

## Platform evidence boundary

- `source-claim`: CloudKit Private Database is the current iCloud user's
  private database, requires an available iCloud account, and is accessible
  only to that user by default.
- `source-claim`: a Keychain item marked with `kSecAttrSynchronizable` can
  synchronize through iCloud to other devices, subject to compatible item
  attributes and access groups.
- `source-claim`: Apple may require a new device to be approved through an
  existing trusted device, device passcode, or Apple recovery flow before
  iCloud Keychain data becomes available.
- `inferred`: after Apple admits both devices to the same account and Keychain
  trust domain, Blocker can omit its own pairing ceremony while using CloudKit
  and a synchronizable workspace key.
- `open`: Apple documentation provides no Blocker-controllable maximum Keychain
  propagation time. The product must expose waiting and retry behavior and
  verify it on physical devices.

## Decision

Blocker has one transport-neutral encrypted operation format and two
workspace modes with different key-delivery and admission policies.

| Concern | Apple workspace | Portable workspace |
| --- | --- | --- |
| Transport | CloudKit Private Database | One user-selected synchronized folder |
| Payload protection | Blocker-encrypted, authenticated, and signed bundles | The same Blocker-encrypted, authenticated, and signed bundle format |
| Workspace-key delivery | Synchronizable Keychain | Per-device key wrapping |
| Device admission | Apple Account and iCloud Keychain trust | Explicit Blocker membership approved by an existing member |

### Apple workspace

Each installation exposes one **Sync with iCloud** action. After the person
chooses it, Blocker uses:

- the current Apple Account's CloudKit Private Database as its byte transport;
- a synchronizable Keychain item to deliver the workspace key; and
- Apple Account and iCloud Keychain trust as the membership boundary.

Blocker does not require a QR code, a Blocker invitation, approval on another
Blocker installation, per-device key wrapping, or a Blocker membership
operation in this mode. Apple may still require system-level approval or
recovery before a new device joins iCloud Keychain. Blocker reports that
prerequisite and does not duplicate or bypass it.

If CloudKit contains a workspace but the synchronizable workspace key is not
yet available, the installation enters a waiting-for-workspace-key state. It
must not interpret the remote workspace as empty, generate a replacement key,
or create a parallel workspace. The exact bootstrap and conflict protocol must
make this invariant deterministic under concurrent first runs, delayed
Keychain delivery, account changes, restarts, and retries.

Apple-mode admission grants every correctly entitled Blocker installation that
Apple admits to the same iCloud Keychain trust domain access to the shared
workspace key. Blocker therefore does not independently select or revoke one
Apple installation while that installation remains trusted by Apple. Adding
such control later would be a new membership decision rather than a minor UI
change.

### Common application-layer E2EE

CloudKit and portable-folder providers receive opaque application-encrypted
payloads plus only the bounded routing metadata required by the transport and
format. The common layer retains:

- the encrypted and authenticated bundle envelope;
- signed immutable operations;
- versioning, canonical validation, and deterministic reduction;
- workspace, transport, and key epochs;
- author sequencing, replay detection, and local high-water marks; and
- rejection of tampered, malformed, duplicated, stale, or wrong-context input
  without replacing the last valid local state.

Apple mode does not use a separate plaintext or "simple CloudKit" data
protocol. The exact production primitives, providers, encoding, automatic
author registration, key rotation triggers, and metadata fields remain
security and architecture decisions, but every transport must use one
compatible encrypted operation model. There is no plaintext fallback.

### Portable workspace

Portable mode is later work for Apple, Android, and Linux. Its transport is one
folder selected by the person through Dropbox, OneDrive, iCloud Drive, or
another File Provider or synchronized-filesystem implementation.

Folder access is neither a Blocker identity nor authorization to join the
workspace. Portable mode therefore retains the feasibility model's:

- independent device signing and key-agreement identities;
- explicit workspace membership;
- approval of a new device by an existing member;
- QR exchange;
- per-device workspace-key wrapping;
- signed membership operations;
- key epochs and prospective device revocation; and
- an optional recovery path whose exact design is deferred with portable mode.

The provider sees encrypted objects and observable storage metadata. Writers
with folder access may delete, replace, duplicate, reorder, or replay objects.
The common cryptographic layer and local high-water marks must detect tampering,
replay, and rollback within their stated observation boundary. A fresh replica
cannot prove that unseen objects were deleted without authenticated
completeness metadata; the portable design must define that boundary and its
tests before claiming deletion detection. Detection is not recovery: a folder
writer can deny availability, and deleted ciphertext can be restored only from
a surviving trusted replica or an explicitly accepted backup or recovery path.

### Migration between modes

Each workspace has exactly one active transport. Moving from CloudKit to a
folder creates a new portable workspace and new key epoch:

1. one existing Apple device explicitly exports and imports the selected data;
2. that device becomes the first portable member;
3. later devices select the same folder and complete explicit enrollment; and
4. the old CloudKit workspace is retired as the active authority.

Blocker does not implement a live bridge, dual-write, or concurrent
CloudKit-folder synchronization. Migration must have explicit completion,
failure, rollback, and cleanup semantics before implementation.

## Trust-boundary summary

| Boundary | Principal risk | Required property |
| --- | --- | --- |
| Apple Account and iCloud Keychain | An Apple-trusted device obtains the Blocker workspace key | Treat Apple trust as Apple-mode membership; never imply independent Blocker admission or revocation |
| CloudKit Private Database | Payload disclosure, tampering, replay, account change, or metadata exposure | Common E2EE, signatures, context validation, account isolation, bounded metadata, and last-valid-state preservation |
| Portable synchronized folder | An unauthenticated folder writer replaces, deletes, duplicates, reorders, or replays objects | Explicit Blocker membership, authenticated bundles, high-water marks, completeness rules, and no availability claim |
| Application process | A compromised process reads plaintext policy and keys | Minimize plaintext lifetime and do not claim protection from an equivalent process, debugger, or administrator |
| Mode migration | Both transports act as authority or partial migration forks state | New workspace and key epoch, one active authority, explicit completion, rollback, and cleanup |

## Security and privacy consequences

- Apple Account and iCloud Keychain compromise are membership-boundary threats
  for Apple mode; Blocker-level QR approval no longer limits that exposure.
- Application-layer E2EE still protects payload contents from the transport
  when the production cryptographic design is correct, but it does not hide
  account, container, record, timing, size, or folder metadata.
- Possession of the Apple workspace key authorizes decryption. The production
  design must state how signed Apple-mode authors are registered and validated
  without reintroducing cross-device Blocker approval.
- A remote workspace with a delayed or unavailable key is an action-required
  or waiting state, never evidence that no workspace exists.
- Account sign-out, account switching, iCloud Keychain reset, lost Keychain
  access, and entitlement mismatch must preserve account isolation and the
  last valid local state. Total-key-loss recovery remains outside the MVP.
- Cloud and folder input remains untrusted. Size limits, schema and context
  validation, signatures, authenticated encryption, replay defense, atomic
  apply, and redacted diagnostics remain mandatory.
- Browsing history, allowed navigation events, usage counters, opaque platform
  selections, workspace keys, QR payloads, and provider credentials remain
  outside diagnostics and public artifacts.

## Alternatives considered

### Use explicit Blocker enrollment in Apple mode

Rejected for the Apple-first MVP. It adds a second trust ceremony after Apple
has already admitted the device to the same account and iCloud Keychain. The
mechanism is retained for portable mode and may return to Apple mode only if a
future threat model requires independent per-installation admission or
revocation.

### Remove application-layer encryption from CloudKit

Rejected. CloudKit access control and platform encryption do not replace the
accepted common Blocker ciphertext boundary, and a plaintext Apple protocol
would fork the data model.

### Treat folder access as portable membership

Rejected. A synchronized folder is an untrusted byte mailbox and does not
authenticate a Blocker installation or establish key authority.

### Run CloudKit and folder transports together

Rejected. A live bridge or dual-write would create competing authorities,
cross-transport failure modes, and a substantially larger consistency and key
lifecycle contract.

## Required verification

Before Apple synchronization is accepted as implemented:

- a physical Mac and iPhone using the same Apple Account each complete one
  **Sync with iCloud** action without a Blocker QR or cross-device approval;
- a workspace created on either platform is discovered by the other;
- delayed synchronizable-Keychain delivery produces the waiting state and
  never a second workspace;
- encrypted CloudKit records reveal no protected payload fields;
- wrong-key, tampered, replayed, stale, malformed, and oversized bundles do not
  replace the last valid state;
- account change, no-account, restricted-account, disabled-Keychain, offline,
  restart, and retry paths have distinct safe outcomes; and
- the test retains the existing no-delivery-SLA and no-device-wake boundary.

Portable-mode and migration verification belong to their later implementation
plans. They must cover explicit membership, QR replay protection, per-device
wrapping, revocation, provider rollback and deletion boundaries, and migration
failure recovery.

## Open implementation decisions

- synchronizable-Keychain item attributes, accessibility, access groups,
  rotation, reset, and account-change behavior;
- deterministic bootstrap when CloudKit and Keychain propagation race;
- CloudKit schema, environments, quotas, subscription lifecycle, and container
  ownership for official builds and forks;
- portable-folder provider semantics, authenticated completeness metadata,
  local high-water marks, and fresh-replica rollback detection;
- portable membership, revocation, recovery, export, deletion, and migration
  UX; and
- the exact data selected for migration and retirement of the CloudKit
  authority.

## Sources checked on 2026-08-25

- [CloudKit private database](https://developer.apple.com/documentation/cloudkit/ckcontainer/privateclouddatabase)
- [CloudKit container and account boundary](https://developer.apple.com/documentation/cloudkit/ckcontainer)
- [Synchronizable Keychain items](https://developer.apple.com/documentation/security/ksecattrsynchronizable)
- [iCloud Keychain security overview](https://support.apple.com/guide/security/sec1c89c6f3b/web)
- [Apple-device iCloud Keychain approval](https://support.apple.com/en-us/120758)
- [Keychain data protection](https://support.apple.com/guide/security/secb0694df1a/web)
- [CloudKit user-data encryption](https://developer.apple.com/documentation/cloudkit/encrypting-user-data)
