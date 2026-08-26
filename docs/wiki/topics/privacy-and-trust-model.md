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
- `open`: the telemetry policy, exact retention and deletion rules,
  support-data workflow, public privacy notice, and production cryptographic
  design are not accepted.

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
erasure from managed-runtime or platform memory.

Browser coverage and coexistence, helper privilege and recovery, iOS
entitlement and App Group details, cryptographic primitives and signed-author
registration, diagnostics, retention, deletion, and public-release claims
remain with their named roadmap tasks. Acceptance of the threat model does not
accept those implementations or make a production-readiness claim.

## Data the product may need

Candidate local and synchronized data includes:

- workspace, device, policy, schedule, session, transport, and key-epoch
  identifiers;
- explicit membership identifiers and operations for portable workspaces;
- domain and application policy intent;
- local opaque platform selections where the operating system requires them;
- encrypted immutable operations and bounded routing metadata;
- pending publication work and opaque transport cursor state;
- synchronizable Apple workspace key material;
- device-local signing and key-agreement identity and recipient-specific
  wrapping material required by the portable workspace;
- explicit portable recovery material when the person enables that later
  optional capability;
- local status, failure category, and last completed synchronization time.

Every field needs a stated purpose, storage location, synchronization scope,
retention rule, deletion behavior, and diagnostic policy before production.

## Data outside the default model

- browsing history and full URLs;
- allowed navigation events;
- page titles, content, cookies, headers, query parameters, and TLS plaintext;
- application usage timelines and behavioral analytics;
- account credentials for Apple or portable-folder providers;
- unrelated device inventory, process list, network endpoints, or local files;
- raw opaque platform tokens in logs, crash reports, or public issue templates;
- support conversations or automatic capture of user screens.

The macOS proxy should decide from the minimum host information available and
discard request-level state after the bounded decision. Browser presentation
should receive a fixed product destination, not the attempted private URL.

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
- signed-operation author identity whose automatic Apple-mode registration is
  still to be designed;
- portable device-local signing and key-agreement identities;
- portable per-device wrapping and future key epochs after membership changes;
  and
- optional human-held portable recovery material.

Production must define generation, storage accessibility, backup behavior,
rotation, automatic Apple author registration, portable enrollment, revocation,
recovery, destruction, and fork/self-build configuration for each class.

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

Default diagnostics should contain:

- stable operation and failure categories;
- bounded counts and durations;
- component and schema versions;
- redacted state transitions;
- no domain, application, account, device, key, opaque token, or content value.

Before adding crash reporting, telemetry, or user-exported support bundles,
define consent, collection purpose, field allowlist, retention, deletion,
third-party processors, and a preview that lets users inspect exported data.

## Repository knowledge boundary

This repository contains maintained synthesis and synthetic examples. A
maintainer may attach the feasibility research checkout under the ignored
`.research/` directory for exact PoC and spike reference. It is read-only and
never a dependency of commits, builds, tests, or documentation.

## Remaining security and privacy questions

- Which exact browser, helper, privilege, lifecycle, bypass, and coexistence
  controls satisfy the accepted outcomes on each supported platform?
- What authenticated completeness claim can portable mode make about deletions
  to a fresh replica versus rollback below an existing local high-water mark?
- What diagnostics are necessary to support proxy, entitlement, and sync
  failures without collecting behavior?
- What exact retention, deletion, export, support-data, and public-notice rules
  apply to each accepted data class?
- Which production cryptographic, signed-author, IPC, signing, and distribution
  choices satisfy the accepted threat controls and release recheck?
