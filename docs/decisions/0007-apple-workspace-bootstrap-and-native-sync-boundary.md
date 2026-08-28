# ADR 0007: Define Apple Workspace Bootstrap and the macOS Native Sync Boundary

## Status

- **Status:** Accepted
- **Date:** 2026-08-28
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`

## Context

[ADR 0002](0002-synchronization-trust-and-workspace-modes.md) selects one
personal Apple workspace, CloudKit Private Database transport, synchronizable-
Keychain workspace-key delivery, and Apple Account plus iCloud Keychain trust.
[ADR 0006](0006-apple-mvp-encrypted-operation-and-convergence.md) freezes the
opaque encrypted bundle and its workspace, transport-epoch, and key-epoch
context. Neither decision defines the exact CloudKit mailbox, Keychain item,
concurrent bootstrap arbitration, or macOS access path.

The macOS product host is a JVM process and cannot call CloudKit or Keychain
directly. The existing `app.posato.macos.helper` is an enforcement process with
proxy, application-observation, and later browser-presentation responsibilities.
Giving it synchronization entitlements would expose the workspace key to a
larger parser and enforcement attack surface and would contradict its accepted
ownership.

`observed`: the read-only feasibility checkout at revision
`bcdc8ce9b91ecb7569c2d98b568d5fd64c25455c` proved synchronizable-Keychain and
private-CloudKit exchange on one physical Mac and iPhone, create-if-absent
secure-item behavior, delayed-key outcomes, bounded native errors, and exact
cleanup. Its combined helper, persistent device identity, schema, identifiers,
and runtime remain prototype choices rather than product authority.

The MVP needs the smallest contract that lets `SYNC-004` through `SYNC-010`
implement independently without inventing another workspace, replacing a
missing key, or exposing Apple framework types to common Kotlin.

## Decision

### Native ownership

The iOS application calls its narrow native CloudKit and Keychain adapters
directly. The macOS JVM application uses a separate signed Swift synchronization
companion with bundle identifier `app.posato.macos.sync`.

The companion is embedded at one fixed application-owned path, runs with normal
user privilege only for a bounded synchronization request, and exits afterward.
It owns only native CloudKit and Keychain mechanics. It has no enforcement,
product-policy, merge, root, installation, background-agent, listener, shell,
or general command responsibility.

The JVM launches the companion with private inherited pipes. The application
verifies the expected embedded companion before use, and the companion verifies
the signed parent and package relationship before accepting a frame. The
protocol uses one bounded major version, allowlisted operations, bounded frame
and field sizes, request identity, deadlines, cancellation, and structured
outcomes. Secrets never enter arguments, environment variables, logs, native
error text, or diagnostics. No custom pipe encryption is added to the fixed
signed process relationship.

Shared Kotlin owns bootstrap state, validation, candidate persistence,
reconciliation, and user-visible semantic status. Native adapters return only
platform-neutral values and outcomes. Apple objects, raw account identifiers,
provider exceptions, and raw error descriptions stay inside the native edge.
The only account-derived value that crosses this boundary is the local opaque
binding defined below.

The new companion requires one explicit App ID. `SYNC-003` registers
`app.posato.macos.sync`, enables iCloud/CloudKit, and associates only the
existing `iCloud.app.posato.sync` container. It creates no App Group or second
container. `SYNC-006` and `SYNC-008` own the target, packaging, entitlements,
provisioning, signing, signed-artifact verification, and concrete IPC schema.

### CloudKit mailbox

Apple synchronization uses only the current account's private database in the
existing `iCloud.app.posato.sync` container. Format 1 owns one custom record
zone named `PosatoSyncV1` with the current-user owner.

Zone existence and anchor existence are separate provider facts. Before any
anchor read, the native adapter fetches that exact zone under the expected
account binding. If it is definitively absent and no local workspace is
established, bootstrap may save only that same zone and must confirm it with an
exact fetch before continuing. A successful save, duplicate response, timeout,
or lost response is reconciled by fetching the same zone; retry may save only
that fixed identity while its absence is proven under the expected binding. No
additional bootstrap zone state is persisted. A crash resumes by fetching the
fixed zone again, and concurrent creators converge on that zone while the
anchor remains the sole workspace arbiter.

If the exact zone is definitively absent after a local workspace is established,
the common coordinator reports `action-required`. It does not recreate the zone,
treat the anchor as absent, create a candidate, or clear local or pending work.

The zone contains two record types:

| Purpose | Record type and identity | Exact fields |
| --- | --- | --- |
| Workspace anchor | `PosatoWorkspaceV1`, fixed record name `workspace` | `workspaceId`, `transportEpochId`, and `keyEpochId`, each `BYTES` of exactly 16 bytes |
| Encrypted mailbox bundle | `PosatoEncryptedBundleV1`, record name equal to the canonical lowercase UUID text of the 16-byte ADR 0006 bundle identifier | `payload`, `BYTES` containing the complete immutable ADR 0006 bundle, at most 65,536 bytes |

Both record types are immutable and create-only. Creating an absent record uses
the server-record-unchanged save policy. An existing byte-identical record is an
idempotent success; the same record identity with different fields or bytes is
an integrity conflict. Every field set, type, length, record name, record type,
zone, and owner is validated before common code constructs state.

The schema has no duplicated bundle identifier, format field, queryable index,
plaintext operation or policy field, `CKAsset`, shared/public database record,
general metadata map, or transport-owned merge rule. Record-type names carry
the version. Production schema deployment, subscriptions, engine-state
persistence, retry timing, quota policy, and mailbox retention remain with
their named implementation and release tasks.

### Synchronizable-Keychain item

The iOS application and macOS synchronization companion share one versioned
generic-password item contract:

| Attribute | Format 1 value |
| --- | --- |
| class | `kSecClassGenericPassword` |
| service | `app.posato.sync.workspace-key.v1` |
| account | canonical lowercase UUID text of `workspaceId` |
| access group | signed value whose public suffix is `app.posato.sync`; the resolved team prefix is never recorded |
| synchronizable | `kSecAttrSynchronizable = true` |
| accessibility | `kSecAttrAccessibleAfterFirstUnlock` |
| macOS keychain | `kSecUseDataProtectionKeychain = true` on every add, read, and delete query |

The value is exactly 84 bytes:

```text
workspaceId[16]
transportEpochId[16]
keyEpochId[16]
workspaceKey[32]
crc32[4]
```

The identifiers use the raw UUIDv4 bytes accepted by ADR 0006. The final four
bytes are CRC-32/ISO-HDLC over the preceding 80 bytes: width 32, polynomial
`0x04C11DB7` represented as reflected `0xEDB88320`, initial value
`0xFFFFFFFF`, input and output reflected, and final XOR `0xFFFFFFFF`. The
unsigned result is stored in network byte order. The checksum detects accidental
corruption; it is not authentication and adds no security claim beyond
fail-closed parsing. Keychain and the accepted Apple trust boundary protect the
item, while authenticated ADR 0006 bundles validate use of the workspace key.

Every query includes the exact class, service, account, access group, and
synchronizable selector, plus the macOS data-protection selector where
applicable. The adapters expose only exact read, create-if-absent, and exact
delete-and-verify-absent:

- absent followed by a successful add is `created`;
- an existing byte-identical value is `identical` and succeeds;
- an existing different value is an integrity conflict and is never replaced;
- `SecItemUpdate`, persistent references, broad deletion, and a synchronized or
  persistent authoring key are forbidden.

### Local account binding

Before workspace-provider access, the native adapter fetches the current
CloudKit user record ID and derives this exact 32-byte value:

```text
SHA-256(
  UTF8("iCloud.app.posato.sync|account-binding|v1")
  || 0x00
  || UTF8(currentUserRecordID.recordName)
)
```

The value is a local equality token, not authentication or product identity.
The raw CloudKit record ID remains native. Shared Kotlin may persist the opaque
binding only in the app-private local database with a bootstrap attempt or the
established local workspace binding. It is never stored in CloudKit or
Keychain, synchronized as application data, displayed, logged, or included in
diagnostics.

Every private-CloudKit operation and every bootstrap synchronizable-Keychain
operation accepts the expected binding. Bootstrap uses the candidate binding;
after establishment, all mailbox access uses the established binding. The
native edge resolves and compares the current binding immediately before and
after provider access. An unavailable preflight value keeps its unavailable or
restricted outcome, while a different value returns `account-changed`; neither
invokes the requested operation. Before returning any definitive `found`,
`missing`, `created`, `identical`, or `conflict` result, the adapter requires an
exact postflight match. An unavailable or different postflight value, or an
account-change signal observed during the operation, returns `unknown-outcome`,
which common code may reconcile only after the expected binding is current
again.

For ongoing mailbox exchange, the native edge performs the preflight before it
starts an explicit fetch or send or supplies an outgoing `CKSyncEngine` batch.
It performs the postflight before it exposes fetched records, accepts engine or
cursor state, acknowledges a sent record, or lets common code clear pending
work. A failed check leaves pending work and the last accepted cursor and engine
state unchanged.

Each `CKSyncEngine` instance belongs to one established binding. Every automatic
delegate event, including a state update, is accepted only while that instance
remains valid and passes the same account check. A failed check or account-change
event cancels and invalidates the instance and discards its buffered events; the
instance is never reused. After the original binding returns, a fresh instance
starts from the last accepted serialization and common code restages the
unchanged pending work.

### Deterministic bootstrap

Bootstrap runs only after the explicit **Sync with iCloud** action and an
available account outcome. One serialized coordinator follows this protocol:

1. Resolve the current account binding. If a local attempt or established
   binding exists, require an exact match before any workspace-provider access;
   otherwise fix the resolved value as the binding for this new attempt.
2. Fetch the exact CloudKit zone under that expected binding. If it is absent
   and no local workspace is established, save only that same zone and require
   an exact binding-checked fetch to confirm it. Reconcile every duplicate,
   timeout, lost response, or unknown save outcome with that fetch, and retry
   only the fixed zone while absence is proven. Never classify the anchor as
   absent before the zone is confirmed. Definitive zone absence after local
   establishment is `action-required`.
3. Read and validate the fixed CloudKit anchor under that expected binding
   before inspecting or creating a candidate key.
4. If the anchor exists, read only its exact Keychain account under the same
   binding. A missing item produces `waiting-for-workspace-key`; it never
   generates a key, replaces the anchor, interprets the workspace as empty, or
   creates another workspace.
5. If the anchor is absent and no local attempt exists, generate one workspace
   identifier, transport-epoch identifier, key-epoch identifier, and 32 random
   workspace-key bytes in memory. The key is never stored in the local database.
6. Create the candidate's exact Keychain item under the expected binding and
   reconcile duplicate or indeterminate results by reading that same selector.
   After identical bytes are confirmed, atomically persist the three non-secret
   candidate identifiers and the opaque binding. Anchor creation is forbidden
   until that local candidate commit succeeds.
7. A crash before candidate persistence may leave only an inert, unanchored
   Keychain item. Restart does not enumerate or adopt such items and may create
   a fresh candidate. A persisted candidate whose exact item later becomes
   unavailable waits or fails action-required; it never regenerates the key.
8. Reconcile every anchor timeout or lost response by reading the fixed
   anchor under the persisted binding. Retry only the same candidate and bytes
   while absence is proven under that binding; never mint a replacement because
   an outcome is unknown.
9. If another valid anchor won, retain the winner, delete and verify absence of
   only the locally recorded losing candidate item under the same binding, then
   read the winner's exact Keychain item. A failed losing-item cleanup remains
   retryable and does not authorize a broad query or deletion.
10. Commit the established local workspace and account binding only when anchor
   fields, Keychain account, decoded item identifiers, item length, and checksum
   all match. That binding is the only workspace that the local replica may
   open.

This conditional fixed-record creation is the sole concurrent-first-run
arbiter. CloudKit does not merge policy or choose operation winners; after
bootstrap it remains the ADR 0006 opaque mailbox.

### Failure, account, and cleanup semantics

Provider edges distinguish at least `found`, `missing`, `created`, `identical`,
`conflict`, `retryable`, `action-required`, `account-changed`,
`unknown-outcome`, and `integrity-failure`. Product state collapses those only
into truthful ready, waiting, retryable, or action-required behavior. Raw Apple
status values and error text are not control flow outside the adapter.

A definitive malformed or unsupported anchor/item, context mismatch, different
duplicate, or inconsistent local binding is an integrity failure. No invalid
input replaces the last established binding. Account unavailable, restricted,
or changed; entitlement or signing mismatch; and definitive anchor difference,
anchor absence, or zone absence after establishment stop synchronization
without deleting or merging local pending work. An account-binding failure is
never interpreted as provider absence and authorizes no create, replacement,
cleanup, deletion, fetched-bundle acceptance, cursor advancement, engine-state
acceptance, or publication acknowledgement. Re-entering the original account
may resume only after its opaque binding and the exact existing anchor and item
match again.

Disabling synchronization preserves the local replica, anchor, mailbox, and
Keychain item. Removing a workspace is a separate explicit destructive action:
it may delete only the exact Posato zone and exact known workspace-key items,
must verify absence independently, and must preserve unrelated CloudKit and
Keychain data. Automatic orphan enumeration, garbage collection, rotation,
recovery codes, remote wipe, and total-key-loss recovery are absent from the
Apple MVP.

## Consequences

- `SYNC-004` can implement one restart-safe state machine with deterministic
  fakes before either Apple adapter exists.
- `SYNC-005` through `SYNC-008` can share exact persisted identifiers, bytes,
  and semantic outcomes while keeping platform mechanisms independent.
- `SYNC-010` can preserve pending work and transport progress across account
  changes by reusing the established binding rather than adding transport state.
- The macOS application gains one additional signed and provisioned process,
  but the workspace key stays outside the enforcement helper and root daemon.
- Concurrent creation needs one small plaintext routing anchor and temporarily
  may create one losing Keychain item. Exact losing-item cleanup is part of the
  protocol; a crash before candidate persistence or uninstall before cleanup
  may leave inert provider-retained data and does not justify automatic broad
  deletion.
- CloudKit can observe the accepted routing metadata, record identities, sizes,
  counts, timing, and service use. It cannot read the encrypted operations.
- The contract adds no dependency, server, pairing flow, background agent,
  second cloud container, generic serialization layer, or production schema
  deployment.

## Alternatives considered

### Reuse `app.posato.macos.helper`

Rejected. Enforcement parsing, browser processing, application observation,
and root-daemon communication do not need the workspace key or CloudKit
entitlements. Combining them enlarges both compromise impact and lifecycle
coupling for no MVP capability.

### Call Apple frameworks in the JVM or replace the macOS host

Rejected. JNI/JNA adds an in-process native ABI and does not remove signing or
packaging, while replacing the accepted Compose Desktop host expands the
product architecture. One narrow Swift companion is the existing native-
boundary pattern with the smallest new surface.

### Use one fixed Keychain account without a CloudKit anchor

Rejected. Two devices can create different values for the same synchronizable
selector before propagation, and Keychain conflict behavior would become the
workspace arbiter. Per-workspace selectors plus one conditional CloudKit anchor
make the winner explicit and let the loser delete only its own candidate.

### Copy the PoC schema or add a general schema layer

Rejected. Duplicate metadata, indexes, batching, device identity, portable
membership, configuration, and schema tooling have no format-1 consumer. Two
record types and one secure-item format are sufficient.

## Required downstream evidence

Before the Apple provider contract is implemented and claimed:

- `SYNC-004` contract tests cover both creator platforms, concurrent first run,
  a fresh database with no custom zone, an existing zone, concurrent zone
  creation, a crash after zone save, zone-save timeout or lost response, zone
  absence after establishment, every persistent crash boundary, delayed key,
  exact duplicate, different duplicate, unknown provider outcome, corruption,
  account change before and after an indeterminate zone or anchor save, a
  postflight switch whose account-change event arrives after the provider
  result, return to the original account, and no replacement or parallel
  workspace;
- `SYNC-005` and `SYNC-006` prove identical item bytes and selectors, delayed
  propagation, exact cleanup, locked/unavailable behavior, entitlements, and
  signed target access on a physical iPhone and Mac;
- `SYNC-007` and `SYNC-008` prove identical exact-zone fetch, save, and
  confirmation semantics, record types, fields, immutable retries, change
  exchange, malformed/oversized rejection, and the established-binding
  preflight and postflight around every mailbox operation, including automatic
  delegate events and an account switch whose notification arrives after a
  fetched or sent batch; a failed check invalidates the engine and a fresh
  instance starts from only the last accepted serialization;
- `SYNC-006` and `SYNC-008` prove the fixed companion/parent relationship,
  bounded IPC, wrong-peer and malformed-frame rejection, timeout reconciliation,
  cancellation, and absence of secrets in arguments, environment, logs, and
  diagnostics;
- `SYNC-009` proves one controlled physical Mac-and-iPhone bootstrap in both
  directions from a private database without the custom zone, simultaneous
  opt-in, restart, delayed Keychain, account failure, and cleanup without manual
  repair or a second workspace; and
- `SYNC-010` proves that an account switch exposes no fetched bundle to common
  code, advances no cursor or accepted engine state, acknowledges no sent
  bundle, and preserves and restages unchanged pending work only after the
  established binding returns.

This ADR and the `SYNC-003` resource check verify only the contract and resource
availability. They do not claim those controls are implemented.

## Evidence and sources

- `observed`: `.research/blocker` revision
  `bcdc8ce9b91ecb7569c2d98b568d5fd64c25455c`, especially the final Apple sync
  report, `WorkspaceBootstrap.kt`, `AppleDirectCloudDatabase.swift`,
  `KeychainStore.swift`,
  `SyncEngineDriver.swift`, and their tests, supports physical feasibility,
  exact-zone fetch and save, exact create/read/delete behavior, bounded failure
  mapping, delay handling, and the checksum-as-corruption-check pattern. It does
  not prove this contract's zone-save reconciliation or persisted-account gating
  when an account-change event is delayed.
- [Synchronizable Keychain items](https://developer.apple.com/documentation/security/ksecattrsynchronizable)
  define the cross-device secure-item attribute.
- [Keychain accessibility](https://developer.apple.com/documentation/security/ksecattraccessibleafterfirstunlock)
  defines the selected background-compatible accessibility class.
- [CloudKit record zones](https://developer.apple.com/documentation/cloudkit/ckrecordzone)
  define the private custom-zone boundary and require a custom zone to be saved
  before records can be saved in it.
- [CloudKit save policy](https://developer.apple.com/documentation/cloudkit/ckmodifyrecordsoperation/recordsavepolicy/ifserverrecordunchanged)
  defines conditional save against unchanged server state.
- [CloudKit record value limits](https://developer.apple.com/documentation/cloudkit/ckrecord)
  permit the bounded inline byte fields selected here.
- [CloudKit current-user record ID](https://developer.apple.com/documentation/cloudkit/ckcontainer/fetchuserrecordid%28completionhandler%3A%29)
  supplies the native input for the current-account binding.
- [CloudKit account-change events](https://developer.apple.com/documentation/cloudkit/cksyncengine-5sie5/event/accountchange)
  require the application to reconcile local persistence after account changes.
