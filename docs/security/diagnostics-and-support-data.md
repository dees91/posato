# Apple MVP Diagnostics and Support-Data Policy

## Status and authority

- **Status:** Accepted
- **Proposed:** 2026-08-26
- **Accepted:** 2026-08-26
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`, constrained by accepted Posato authorities
  and bounded feasibility evidence
- **Task:**
  [`DIAGNOSTICS-001`](../tasks/specifications/diagnostics-001-diagnostics-and-support-data.md)

The maintainer explicitly accepted this policy and its capture, retention,
deletion, export, and no-remote-collection boundaries on 2026-08-26. It
governs every Apple MVP diagnostic producer. Acceptance authorizes no
implementation by itself; each producer remains responsible for the controls
and verification below.

## Scope and purpose

This policy covers diagnostic data owned by the accepted Apple MVP on one
supported Mac and one supported iPhone. It does not authorize an analytics
service, support backend, product account, remote telemetry, crash-reporting
SDK, or implementation.

Diagnostics may answer only these support questions:

1. Which Posato component and bounded operation category failed?
2. Is the outcome retryable, action-required, or terminal for that attempt?
3. Did a safety operation such as restore or cleanup complete successfully?
4. Which application, component, and contract versions produced the outcome?

Diagnostics must not measure engagement, count blocking or bypass attempts,
reconstruct browsing or application use, identify a person or device, or
recreate policy and session history.

## Diagnostic surfaces

| Surface | Default and consent | Storage and exposure | Transmission |
| --- | --- | --- | --- |
| Current user-visible status | Always available; no separate consent because it is product state, not an event history | One stable category, safe explanation, and next action; replaced when the current state changes | None |
| Local diagnostic capture | Off by default; enabled by an explicit user action after a plain-language preview of purpose, fields, limits, and expiry | Allowlisted structured records in Posato-owned protected, no-backup local storage; unavailable for Posato diagnostic use after at most 24 hours and physically removed at the first available execution opportunity | None |
| Support export | Never automatic; a separate user action previews the actual export before sharing | One temporary app-owned derivative containing only allowlisted records | The user chooses the destination through the platform sharing flow |
| Automatic telemetry, analytics, or crash upload | Disabled for the MVP | No Posato-owned collection or retention | No processor or Posato endpoint exists |
| Development, test, and public evidence | Synthetic inputs only; private raw evidence is not tracked | Categorical results and sanitized derivatives only | No real-person or device evidence is published |

An operating system or distribution service may have diagnostics outside
Posato's process and storage control. Posato does not retrieve, add, or upload
those reports under this policy, and the first-release privacy review must
describe the verified platform boundary without turning it into a Posato
collection claim.

Current diagnostic status is distinct from normal product UI that lets a
person review policy they entered. It does not authorize a status timeline or
put those policy values into errors, logs, captures, or exports.

## Field allowlist

Every captured record is constructed from an allowlist. A producer may omit a
field but may not add another field without updating this authority.

| Field | Permitted value |
| --- | --- |
| `schemaVersion` | Bounded diagnostic-record schema version |
| `component` | Closed product-component category owned by the producing task |
| `operation` | Closed operational category such as open, validate, publish, fetch, apply, verify, restore, or cleanup |
| `outcome` | Closed category: succeeded, retryable, action-required, failed, or cancelled |
| `failureCategory` | Closed category such as invalid input, unavailable, permission, quota, network, timeout, conflict, integrity or corruption, unsupported version, storage, IPC, or lifecycle |
| `stableCode` | Reviewed fixed code with no dynamic or user-derived segment |
| `stateTransition` | Optional closed operational transition that contains no target, policy, session, navigation, or application-use state |
| `attemptBucket` | Optional bounded retry bucket defined by the producing task |
| `durationBucket` | Optional bounded operation-duration bucket defined by the producing task; never a session or usage duration |
| `workCountBucket` | Optional bounded internal-work bucket defined by the producing task; never a target, policy, session, navigation, or application-use count |
| `appVersion` | Public Posato version and build number |
| `componentVersion` | Public helper, extension, or adapter version when it differs from the application |
| `contractVersion` | Bounded database, IPC, support-record, or transport schema version |
| `platformFamily` | `macOS` or `iOS` only |
| `osMajorVersion` | Major operating-system version only |

There is no free-form message, wall-clock event timestamp, stack trace, raw
error, correlation identifier, or generic metadata map. Retained records
necessarily reveal their number and order within one explicitly enabled
capture. They do not report a total emitted or dropped event count, and no
value provides stable cross-capture, cross-device, person, installation, or
workspace correlation.

The local store may keep only the control metadata needed to enforce this
policy: one absolute expiry instant and cleanup-pending state. Those values are
not diagnostic records, are never displayed or exported, contain no event
time, and are deleted with the capture store.

## Prohibited data

The following values are prohibited in current status details, captured
records, support exports, process arguments or environment, tracked evidence,
and public issue templates:

- domains, host-policy identifiers, IP addresses, full URLs, paths, query
  strings, page titles or content, cookies, headers, TLS plaintext, and any
  allowed or blocked navigation event;
- application names, bundle identifiers, opaque selections, platform tokens,
  process identifiers, process lists, launch observations, and application-use
  events or timelines;
- policy values, target counts, session start or end times, session durations,
  usage counters, bypass attempts, immutable-operation contents, and any
  workspace, policy, session, operation, record, account, or device identifier;
- names, email addresses, Apple Account data, device names or models, serial or
  hardware identifiers, IP or MAC addresses, local usernames, and hostnames;
- keys, nonces, signatures, credentials, authorization material, recovery
  values, Keychain selectors, CloudKit identifiers, signing identities,
  entitlement values, and private configuration;
- filesystem paths, filenames derived from user or private state, native error
  descriptions, exception messages, stack traces, memory dumps, raw crash
  reports, screenshots, screen recordings, packet captures, and support
  conversations.

Hashing, truncating, encrypting, tokenizing, or otherwise pseudonymizing a
prohibited value does not make it allowed. Exact counts, sizes, timestamps, and
durations are prohibited unless this policy names the bounded bucket that
replaces them.

## Redaction and construction rules

- Construct records from typed closed categories and allowlisted fields. Do
  not collect a larger object and attempt to scrub it afterward.
- Convert native, storage, network, cryptographic, IPC, and framework errors to
  a stable category and fixed code at the boundary, then discard their raw text.
- Sensitive domain types must have redacted default string representations;
  production diagnostics must not depend on `toString()` as serialization.
- Unknown fields, unknown category values, and dynamic stable-code segments
  fail closed by dropping the diagnostic record without changing product
  behavior or the last valid product state.
- Release builds must not enable verbose framework or dependency logging that
  bypasses this policy. A producing task must verify the relevant configuration
  when it adds the dependency or platform adapter.
- Release builds do not emit Posato diagnostic records to operating-system
  consoles because Posato cannot enforce this policy's retention and deletion
  rules there. Explicit capture uses only Posato-owned protected storage.

## Consent, retention, deletion, and export

### Local capture

The enable action must state that capture is local, optional, limited to the
allowlist, and subject to logical expiry plus lifecycle-bounded physical
cleanup. Capture becomes unavailable for diagnostic use, stops accepting
records, and cannot be previewed or exported at the earliest of:

- the user choosing **Delete diagnostics**;
- 24 hours after capture was enabled; or
- a storage-integrity failure that prevents safe bounded retention.

When Posato is executing and can access the store, it deletes the expired data
immediately and verifies absence. If the application, helper, or extension is
not running at the expiry instant, physical deletion occurs at the first later
Posato execution opportunity that can access the store. This policy does not
promise exact background execution or physical deletion at the 24-hour wall-
clock boundary.

If deletion, protection, backup exclusion, or absence verification fails,
capture remains disabled, the store remains unavailable to preview and export,
and cleanup is retried at each applicable startup or execution opportunity. A
safe current status may ask the person to remove Posato-owned application data;
it must not expose the raw storage failure.

The app-owned capture is limited to 500 records and 512 KiB in total across
all Posato-owned diagnostic storage on one device; a producer must not
multiply either cap per process. When either cap is reached, the oldest
complete record is discarded. Capture never blocks safety, cleanup, or primary
product behavior. Startup and capture enablement attempt removal of logically
expired capture and abandoned export staging before allowing a new capture.
Capture storage must use the applicable platform protection and remain
excluded from device backup after creation and every rewrite or rotation.

### Support export

Before sharing, Posato renders the exact structured derivative that will leave
the application. The person may cancel, delete the capture, or choose a
destination. Posato does not silently address, upload, or retain a copy.

The temporary app-owned export inherits the capture's absolute expiry. It
becomes unavailable to preview or share when the sharing flow returns, when
the capture is deleted or expires, or when storage controls fail. Posato then
deletes it and verifies absence. If termination or suspension prevents
immediate physical deletion, it remains logically inaccessible and cleanup is
retried at the first available execution opportunity before another preview,
share, or capture. Staging uses the same platform protection and backup
exclusion as capture storage and cannot exceed the capture byte cap. The
underlying local capture follows its original expiry and may be deleted
separately.

After the platform hands a copy to a destination chosen by the user, that copy
is user-controlled and outside Posato's deletion guarantee. The preview must
say so. No support request or conversation grants continuing consent or
authorizes additional collection.

### Remote collection

The Apple MVP has no automatic diagnostic, analytics, or crash-report upload,
no product-operated telemetry or support store, and no third-party diagnostics
processor. Adding any of them requires a new accepted privacy change defining
purpose, exact fields, user choice, network destination, processor, retention,
deletion, access, security, and public notice before data is sent.

## Producer and verification contract

The first real producer adds the smallest implementation with its consumer; it
does not create a speculative cross-platform logging framework. Every
producing task must:

1. name the support question each record answers and use only the fields above;
2. define closed component, operation, state, code, and bucket values with hard
   input bounds;
3. keep user-visible product status usable when capture is disabled;
4. test the production record encoder with synthetic allowed values and
   prohibited canaries, including a derived or hashed canary;
5. verify platform protection and backup exclusion after store creation and
   representative rewrite or rotation, with capture disabled or stopped when
   either property cannot be established;
6. test the applicable cap, logical expiry, explicit deletion, first-
   opportunity physical cleanup, cleanup-failure quarantine, staging cleanup,
   and raw-native-error boundary; and
7. record only categorical or synthetic evidence in Git and remove private raw
   evidence after validation.

An implementation task must update this authority before introducing a new
field, longer retention, another diagnostic surface, automatic transmission,
or a processor. A new fixed category or code that remains within the accepted
fields and purpose is owned and reviewed by its producing task.

## Release and change boundary

`RELEASE-001` must recheck the shipped diagnostic defaults, backup exclusion,
platform and distribution diagnostics, privacy notice, public support path,
and the absence or declared behavior of every processor. Passing this policy
does not claim release readiness, legal compliance, or verified deletion from
operating-system or destination-provider backups.

## Feasibility provenance and limits

This policy was informed by the read-only `.research/blocker` checkout at
`d48bdfb0315ec8bae84bb447dc13bc99c5a92099`. Its final synchronization and
enforcement privacy artifacts were unchanged from the final feasibility
revision `bcdc8ce9b91ecb7569c2d98b568d5fd64c25455c`.

`observed`: the PoC used synthetic canaries, categorical failure results,
allowlisted capture checks, redacted domain objects, ignored raw evidence, and
exact cleanup. The enforcement spike kept opaque selections and raw screenshots
outside tracked evidence. Those observations supply abuse cases and test
ideas only. They do not establish this policy, production redaction, privacy,
retention, export safety, or release eligibility. No PoC schema, logger, event
name, dependency, code, private value, or production claim is imported.

## Authoritative inputs

- [MVP scope](../product/mvp-scope.md)
- [Apple MVP threat model](apple-mvp-threat-model.md)
- [ADR 0002: synchronization trust and workspace modes](../decisions/0002-synchronization-trust-and-workspace-modes.md)
- [ADR 0003: MVP application architecture baseline](../decisions/0003-mvp-application-architecture-baseline.md)
- [Engineering quality contract](../development/engineering-quality-contract.md)
- [MVP roadmap](../tasks/mvp-roadmap.md)
- [Maintained privacy and trust synthesis](../wiki/topics/privacy-and-trust-model.md)
- [PoC reuse inventory](../wiki/topics/poc-reuse-inventory.md)
