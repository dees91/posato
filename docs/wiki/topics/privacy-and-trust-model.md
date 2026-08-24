# Privacy and Trust Model

## Current direction

- `user-confirmed`: the application operates no product account, shared
  user-data backend, analytics backend, or synchronization relay.
- `user-confirmed`: provider authentication occurs in system settings or a
  provider client, not through credentials entered into the application.
- `user-confirmed`: browsing history, allowed navigation events, usage
  counters, and content are outside the synchronized model.
- `user-confirmed`: application-layer encryption remains required for
  synchronized policy and membership state.
- `open`: the complete production threat model, telemetry policy, retention
  schedule, support-data workflow, and public privacy notice are not accepted.

## Data the product may need

Candidate local and synchronized data includes:

- workspace, device, policy, schedule, session, membership, and key-epoch
  identifiers;
- domain and application policy intent;
- local opaque platform selections where the operating system requires them;
- encrypted immutable operations and bounded routing metadata;
- pending publication work and opaque transport cursor state;
- synchronizable workspace key material and device-local identity material;
- explicit recovery material when the user chooses to create it;
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

### Cloud storage

CloudKit stores encrypted application bundles in the user's private database,
but the provider still handles account and routing metadata. Application-layer
encryption hides protected payload fields when implemented correctly; it does
not provide traffic-analysis anonymity or hide all record metadata.

The later portable-folder path will expose encrypted filenames, sizes, timing,
and provider metadata unless explicitly designed otherwise.

## Key classes

The feasibility design separated:

- synchronizable workspace material used by participating Apple devices;
- device-local signing and key-agreement identity;
- optional human-held recovery material;
- future key epochs after membership changes.

Production must define generation, storage accessibility, backup behavior,
rotation, enrollment, revocation, recovery, destruction, and fork/self-build
configuration for each class.

## Failure and integrity

Untrusted input includes cloud records, portable files, IPC frames, opaque
platform tokens, recovery text, QR payloads, local database bytes, and native
errors. Validation occurs before state mutation.

Required safety properties:

- no plaintext fallback;
- wrong workspace, author, epoch, version, signature, or encryption context is
  rejected;
- invalid remote input does not replace the last valid local policy;
- pending local work survives retryable failures;
- errors are stable categories with redacted diagnostics;
- sensitive byte types avoid accidental string conversion and make defensive
  copies;
- temporary secrets and plaintext buffers are minimized and cleared where the
  runtime permits, without overstating memory-erasure guarantees.

## Revocation, deletion, and recovery limits

Membership revocation can exclude a device from future key epochs. It cannot
erase historical data, keys, exports, screenshots, backups, or plaintext that
the device already possessed.

Workspace deletion can remove the exact local namespace, exact secure items,
and exact private-cloud zone controlled by the product. It must preserve data
outside that scope, verify absence, and remain safe when repeated. Cloud and
backup retention may outlive the immediate application operation and must be
documented from authoritative platform behavior.

Recovery improves availability but weakens the claim that losing every device
necessarily destroys access. The product must present this trade-off and an
irreversible-loss warning honestly.

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

## Threat model questions

- Is the primary adversary an automatic habit, a briefly motivated user, a
  local unprivileged process, another enrolled device, or the device owner with
  administrator access?
- Which bypass methods are in scope and what delay or deliberate action is
  considered sufficient?
- What data is public routing metadata versus encrypted payload?
- What metadata can Apple or a portable provider observe?
- Are policy targets sensitive data for privacy documentation and export or
  deletion rights?
- What diagnostics are necessary to support proxy, entitlement, and sync
  failures without collecting behavior?
- Which cryptographic and IPC properties receive independent review before
  release?
