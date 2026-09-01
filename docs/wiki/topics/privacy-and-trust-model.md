# Privacy and Trust Model

## Current direction

- `user-confirmed`: the application operates no product account,
  product-operated shared user-data backend, analytics backend, or
  synchronization relay.
- `user-confirmed`: provider authentication occurs in system settings or a
  provider client, not through credentials entered into the application.
- `user-confirmed`: browsing history, allowed navigation events, usage
  counters, and content are outside the synchronized model.
- `user-confirmed`: application-layer encryption remains required for
  synchronized payloads in both Apple and portable workspace modes.
- `user-confirmed` (2026-08-25): Apple Account and iCloud Keychain trust define
  Apple-workspace membership. Portable workspaces use explicit Blocker device
  identity, membership, approval, wrapping, key epochs, and revocation.
- `user-confirmed` (2026-08-26): the
  [Apple MVP threat model](../../security/apple-mvp-threat-model.md) is the
  accepted authority for assets, data classification, trust boundaries,
  threats, required controls, downstream owners, and residual risks.
- `user-confirmed` (2026-08-26): the
  [Apple MVP diagnostics and support-data policy](../../security/diagnostics-and-support-data.md)
  accepts safe current status, explicit bounded local capture, previewed user-
  controlled export, and no automatic remote collection.
- `user-confirmed` (2026-08-26):
  [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md)
  accepts the macOS session-helper and root proxy-settings-daemon ownership,
  privilege, IPC, authorization, recovery, update, and removal boundary.
- `user-confirmed` (2026-08-26):
  [ADR 0005](../../decisions/0005-macos-browser-enforcement-and-coexistence.md)
  accepts the narrow Safari and Chrome browser, exact-domain, proxy
  coexistence, transient-data, fixed-presentation, failure, and network-
  transition boundary.
- `user-confirmed` (2026-08-26): local exact-domain policy has no TTL and
  remains app-private until edited or removed; replacing it with an empty set
  atomically removes every domain row. A separate reset flow, guaranteed secure
  erasure, and custom backup policy remain outside MODEL-001.
- `open`: the public privacy notice, production cryptographic design, and
  lifecycle rules for other non-diagnostic data are not accepted.

## Accepted Apple MVP threat model

`user-confirmed` (2026-08-26): the model classifies ten asset and data groups,
maps eight trust boundaries and fifteen abuse cases to existing roadmap owners,
and keeps every production control unverified until its owning task supplies
proportionate evidence and review. The feasibility PoC and enforcement spike
provide bounded abuse cases and test ideas, not production status or an
implementation prescription.

The accepted residuals are Apple Account and iCloud Keychain as the complete
Apple-workspace membership boundary; administrator, debugger, compromised-OS,
and equivalent-process access; provider-visible metadata and service denial;
no remote wipe or total-key-loss recovery; and no guarantee of complete secret
erasure from managed-runtime or platform memory. MACOS-002 additionally accepts
the bounded system-proxy and browser-coverage limits, lack of content erasure,
and the disclosed Apple Events presentation race recorded as `R-06`.

MACOS-002 now accepts browser coverage and coexistence, but MACOS-003 and
MACOS-004 still own implementation and physical evidence for the helper,
recovery, proxy, presentation, and privacy controls. `user-confirmed`
(2026-08-28): ADR 0006 accepts the cryptographic format and signed-author
registration contract; `SYNC-002` still owns implementation evidence. iOS
entitlement and App Group details, lifecycle rules for other non-diagnostic
data, and public-release claims remain with their named roadmap tasks.
Diagnostic producers remain unimplemented and must satisfy the accepted
diagnostic policy. Acceptance of these authorities does not make a
production-readiness claim.

## Data the product may need

Candidate local and synchronized data includes:

- workspace, device, policy, schedule, session, transport, and key-epoch
  identifiers;
- explicit membership identifiers and operations for portable workspaces;
- domain and application policy intent;
- one timestamp-free terminal local expiry marker bound to an encrypted session
  identifier, retained only with its start operations and excluded from
  synchronization and diagnostics;
- local opaque platform selections where the operating system requires them;
- encrypted immutable operations, including author identity, public key, and
  sequence, plus bounded routing metadata consisting of format and suite,
  per-operation bundle identifier and salt, workspace and epoch identifiers,
  ciphertext size, and transport-required account, timing, and record data;
- pending publication work and opaque transport cursor state;
- synchronizable Apple workspace key material;
- device-local signing and key-agreement identity and recipient-specific
  wrapping material required by the portable workspace;
- explicit portable recovery material when the person enables that later
  optional capability;
- local status, failure category, and last completed synchronization time.

Every field needs a stated purpose, storage location, synchronization scope,
retention rule, deletion behavior, and diagnostic policy before production.

### Local exact-domain policy lifecycle

`user-confirmed` (2026-08-26): canonical exact-domain rows are active product
configuration, not expiring history. They have no TTL and remain only in the
app-private local replica until the person edits or removes them. Replacing the
policy with an empty set removes every domain row in the same atomic
transaction. This decision does not claim secure physical erasure and does not
add a separate reset or custom backup policy.

## Data outside the default model

- browsing history and full URLs;
- allowed navigation events;
- page titles, content, cookies, headers, query parameters, and TLS plaintext;
- application usage timelines and behavioral analytics;
- account credentials for Apple or portable-folder providers;
- unrelated device inventory, process list, network endpoints, or local files;
- raw opaque platform tokens in logs, crash reports, or public issue templates;
- support conversations or automatic capture of user screens.

These classes remain outside every stored, synchronized, diagnostic, and public
model. ADR 0005 permits two volatile processing exceptions in the normal-user
macOS helper: bounded cleartext HTTP transit buffers required to relay a
request safely, and one current top-level Safari or Chrome URL after a host-free
blocked signal required to guard fixed same-tab presentation. The helper uses
them only for framing, authority, relay, or exact current-tab confirmation,
releases them promptly, and never sends them to the root daemon, diagnostics,
or durable state. Browser-owned history and runtime copies remain outside
Posato control.

## Trust boundaries

### Application processes

The desktop and mobile application processes hold plaintext policy while it is
being evaluated. Application-layer encryption does not protect data from a
compromised application process, debugger, device administrator, or malicious
code running with equivalent privileges.

### Native helpers and extensions

Native helpers and iOS extensions are separate trust and lifecycle boundaries.
They receive only the minimum semantic values required for their capability.
Local IPC validates peer identity, protocol version, lengths, sequence,
timeouts, and errors. Sensitive values do not enter process arguments,
environment variables, or persistent traces.

### Operating-system services

CloudKit, Keychain, Screen Time APIs, system proxy settings, and application
lifecycle are platform services. Their security and availability guarantees
must be described accurately rather than treated as application guarantees.

`user-confirmed` (2026-08-25): in Apple mode, Apple Account and iCloud Keychain
trust are an authorization boundary, not only availability dependencies. Every
correctly entitled Blocker installation that Apple admits to that Keychain trust
domain may obtain the workspace key. Blocker does not add per-installation
approval or revocation in the MVP.

### Cloud storage

CloudKit stores encrypted application bundles in the user's private database,
but the provider still handles account and routing metadata. Application-layer
encryption hides protected payload fields when implemented correctly; it does
not provide traffic-analysis anonymity or hide all record metadata.

The later portable-folder path will expose encrypted filenames, sizes, timing,
and provider metadata unless explicitly designed otherwise. Folder access is
not a Blocker identity or membership grant. Folder writers are untrusted and
may delete, replace, duplicate, reorder, or replay encrypted objects.

## Mode-specific key classes

The accepted direction separates:

- one Apple workspace key delivered through synchronizable Keychain;
- one ephemeral Apple authoring key per local replica-writer incarnation, held
  only in process memory and registered automatically with the first mutation;
- portable device-local signing and key-agreement identities;
- portable per-device wrapping and future key epochs after membership changes;
  and
- optional human-held portable recovery material.

Production must implement the accepted Apple generation, in-memory lifetime,
automatic registration, rotation, ambiguous-commit, and destruction behavior.
Storage accessibility, backup behavior, portable enrollment, revocation,
recovery, and fork/self-build configuration remain to be defined for the key
classes that persist or belong to portable mode.

## Failure and integrity

Untrusted input includes CloudKit records, portable files, IPC frames, opaque
platform tokens, local database bytes, and native errors. Portable recovery
text and QR payloads are also untrusted. Validation occurs before state
mutation.

Required safety properties:

- no plaintext fallback;
- wrong workspace, author, epoch, version, signature, or encryption context is
  rejected;
- invalid remote input does not replace the last valid local policy;
- a CloudKit workspace without an available synchronizable key produces a
  waiting state and never automatic creation of a parallel workspace;
- pending local work survives retryable failures;
- errors are stable categories with redacted diagnostics;
- sensitive byte types avoid accidental string conversion and make defensive
  copies;
- temporary secrets and plaintext buffers are minimized and cleared where the
  runtime permits, without overstating memory-erasure guarantees.

## Admission, revocation, deletion, and recovery limits

Apple mode admits installations through Apple Account and iCloud Keychain
trust. Blocker cannot independently exclude one Apple-trusted installation from
future workspace-key access under this model. Account sign-out, account switch,
Keychain reset, and removal from Apple trust are platform lifecycle events that
the production design must isolate safely.

Portable membership revocation can exclude a device from future key epochs. It
cannot erase historical data, keys, exports, screenshots, backups, or plaintext
that the device already possessed.

Workspace deletion can remove the exact local namespace, exact secure items,
and exact private-cloud zone controlled by the product. It must preserve data
outside that scope, verify absence, and remain safe when repeated. Cloud and
backup retention may outlive the immediate application operation and must be
documented from authoritative platform behavior.

Apple may provide system-level iCloud Keychain recovery, but Blocker does not
add an independent total-key-loss recovery flow to the Apple MVP. Optional
portable recovery remains later work. Recovery improves availability but
weakens the claim that losing every authorized device necessarily destroys
access; any accepted design must present that trade-off honestly.

## Diagnostics and support

`user-confirmed` (2026-08-26): the accepted
[diagnostics and support-data policy](../../security/diagnostics-and-support-data.md)
answers only four bounded support questions. Current status contains no event
history. Local capture is off by default, explicitly enabled, unavailable for
diagnostic use after at most 24 hours, and capped at 500 records and 512 KiB.
Support export previews the exact derivative and uses a user-chosen platform
destination.

The closed allowlist permits stable operational categories, bounded buckets,
and public component or contract versions. It prohibits free-form errors,
event timestamps, correlation identifiers, domains, applications, policy,
identity, secrets, content, browsing and application-use events, and derived
or pseudonymous forms of those values.

`observed` (2026-08-31): `SYNC-002` gives local, serialized, reduced, and
effective session timing carriers fixed redacted default string
representations. Common JVM and iOS Simulator regression tests cover the
boundary. This prevents accidental interpolation from exposing exact session
times; it does not authorize production diagnostics to serialize with
`toString()`.

`observed` (2026-08-31): the `SYNC-002` hybrid logical clock also has a fixed
redacted default string. Containing data-class representations therefore do not
expose its exact physical operation time, while comparison, persistence, and
canonical format behavior remain unchanged.

`observed` (2026-09-01): the `SYNC-002` authoring incarnation and prepared local
mutation also have fixed redacted default strings. Their representations expose
neither the next author sequence nor prepared-operation cardinality or durable
state shape, while authoring, equality, persistence, and canonical serialization
remain unchanged.

`observed` (2026-09-01): the durable `SYNC-002` replica snapshot also has a
fixed redacted default string. It exposes neither the replica revision nor
collection cardinalities or durable state shape, while equality, persistence,
and canonical serialization remain unchanged.

`observed` (2026-09-01): successful local-mutation results use a fixed redacted
default string. Their representations no longer expose pending-bundle
cardinality or nested projection state, while result fields and behavior remain
unchanged.

`observed` (2026-09-01): private persisted-state and decoded-operation carriers
use fixed redacted default strings. Their representations expose neither the
replica revision nor exact author-sequence and HLC fields, without changing
persistence, decoding, equality, or canonical serialization.

The MVP has no automatic telemetry, analytics, crash upload, support store, or
diagnostics processor. A future producing task must add only its real consumer,
prove redaction and storage controls with synthetic canaries, and update the
authority before introducing a new field, surface, retention rule, automatic
transmission, or processor.

## Repository knowledge boundary

This repository contains maintained synthesis and synthetic examples. A
maintainer may attach the feasibility research checkout under the ignored
`.research/` directory for exact PoC and spike reference. It is read-only and
never a dependency of commits, builds, tests, or documentation.

## Remaining security and privacy questions

- Which MACOS-003 and MACOS-004 implementation evidence proves the accepted
  helper, browser, no-fallback, privacy-canary, and recovery contracts?
- What authenticated completeness claim can portable mode make about deletions
  to a fresh replica versus rollback below an existing local high-water mark?
- What exact retention, deletion, export, and public-notice rules apply to
  accepted non-diagnostic data classes other than the local exact-domain
  policy?
- Which IPC, signing, and distribution choices and which `SYNC-002`
  cryptographic implementation evidence satisfy the accepted threat controls
  and release recheck?
