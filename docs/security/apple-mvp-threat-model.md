# Apple MVP Threat Model

## Status and authority

- **Status:** Accepted
- **Proposed:** 2026-08-26
- **Accepted:** 2026-08-26
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`, informed by accepted Posato authorities and
  bounded feasibility evidence
- **Task:** [`SECURITY-001`](../tasks/specifications/security-001-mvp-threat-model.md)

The maintainer explicitly accepted this authority and its original five
residual risks on 2026-08-26. The later user-confirmed MACOS-002 amendment adds
`R-06`. This authority governs Apple MVP security work together with the
accepted product and architecture authorities.

`user-confirmed` (2026-08-26): exact-domain policy is active product
configuration and has no time-to-live. Its canonical domain rows remain in the
app-private local replica until the person edits or removes them. Replacing the
policy with an empty set removes every domain row atomically. MODEL-001 does
not add a separate reset flow, guarantee secure physical erasure, or define a
custom backup policy; those lifecycle claims remain with their future owners.

## Scope

The model covers the accepted Apple MVP on one supported arm64 Mac and one
supported iPhone. The person uses the same Apple Account on both devices and
chooses **Sync with iCloud** on each Posato installation.

The security outcomes are:

1. keep policy, session, selection, and key material out of cloud plaintext,
   diagnostics, and public artifacts;
2. authenticate and validate external input completely before it can replace
   the last valid local state;
3. constrain helpers, extensions, secure storage, cloud storage, and cleanup
   to exact Posato-owned resources;
4. preserve local safety and truthful status when storage, enforcement, IPC,
   CloudKit, Keychain, or application lifecycle fails; and
5. state residual risk honestly instead of presenting feasibility as
   production security.

This model does not claim resistance to the device owner with administrator or
equivalent access, a compromised operating system or Posato process, metadata
anonymity, guaranteed cloud availability or delivery, remote wipe, total-key-
loss recovery, arbitrary-browser coverage, or public-distribution readiness.

## Control and evidence policy

Every external byte, native response, opaque platform value, local database
value, IPC frame, domain input, and dependency artifact is untrusted at its
boundary. The threat register applies the STRIDE lens: spoofing (`S`),
tampering (`T`), repudiation (`R`), information disclosure (`I`), denial of
service (`D`), and elevation of privilege (`E`). It uses no numeric score that
would imply unsupported precision.

A **required control** is a downstream acceptance condition, not an
implemented or verified feature. Its named owner keeps the capability disabled
or incomplete until proportionate tests and review prove the control. An
**accepted residual** may remain only after maintainer acceptance and truthful
product disclosure. A PoC `PASS` supplies test ideas and feasibility evidence;
it never changes a production control to verified.

## Actors and trust assumptions

- The person who owns the devices is authorized to change policy, end a
  session early, disable synchronization, or remove Posato. The product adds
  friction against habitual use, not administrator-resistant control.
- Every correctly entitled Posato installation admitted by Apple to the same
  Apple Account and iCloud Keychain trust domain is an Apple-workspace member.
  Posato adds no per-installation approval or revocation in the MVP.
- CloudKit, Keychain, local storage, native APIs, browsers, and operating-
  system lifecycle services may delay, fail, or return malformed state. They
  are not domain authorities, and their raw errors are not product control
  flow or diagnostic output.
- CloudKit and network observers are not trusted with payload plaintext. Apple
  remains trusted for Apple Account, private-database, Keychain, entitlement,
  signing, and operating-system enforcement boundaries selected by the MVP.
- A local unprivileged process may attempt to call the helper, replay IPC, read
  weakly protected files, exhaust parsers, or race system-state changes.
- A process with Posato-equivalent privileges, a debugger, administrator/root,
  or a compromised operating system can read plaintext in use or bypass
  enforcement. That capability is an explicit residual, not a control target.
- Installed Posato code and approved dependencies are trusted only after their
  owning build, review, signing, and release gates pass.

## Assets and data classification

| ID | Asset or data | Purpose | Location and movement | Class and required protection | Lifecycle owner |
| --- | --- | --- | --- | --- | --- |
| `A-01` | Exact domain and semantic application policy | Express shared blocking intent | App-private local replica; only inside authenticated encrypted CloudKit bundles off-device | Sensitive configuration: validate, minimize, encrypt off-device, exclude from diagnostics; exact-domain rows have no TTL and remain until edited or removed | `MODEL-001`, `TARGETS-001`, `TARGETS-002`, `SYNC-001` |
| `A-02` | Active-session intent, bounded timing, and terminal local expiry marker | Apply and converge the current manual session without revival after observed expiry | App-private local replica and encrypted synchronization operations; the marker stays local | Sensitive configuration: bind a marker to the encrypted session identifier, store no observation timestamp, exclude it from synchronization and diagnostics, retain it only with referenced start operations, and never turn it into usage history | `SESSION-001`, `SESSION-002`, `SYNC-001`, `SYNC-012` |
| `A-03` | macOS application mapping and opaque iOS selection | Bind semantic policy to a local platform target | App-private or platform-protected local storage only; never synchronized | Sensitive platform capability: no portable conversion, logging, public evidence, or silent replacement | `TARGETS-003`, `TARGETS-004`, `IOS-001`, `MACOS-005` |
| `A-04` | Immutable operations, projected policy, pending publication, cursor, and bootstrap state | Preserve the local-first replica and retry work | App-private database; complete encrypted bundles cross the cloud boundary | Security state: bounded, authenticated, atomic, idempotent, migration-safe, and never silently reset after corruption | `MODEL-001`, `SYNC-002`, `SYNC-004`, `SYNC-010` |
| `A-05` | Workspace key, ephemeral authoring key, Keychain selectors, and ephemeral IPC authentication material | Decrypt/authenticate the workspace, sign one local writer incarnation, and protect local channels | Exact workspace-key secure item selected by the owning task; the key crosses only the private signed macOS sync-companion boundary when needed; authoring and IPC keys exist only in their owning process memory; only the workspace key may use synchronizable Keychain | Secret: least-accessible compatible storage, no plaintext fallback, persistence of the authoring key, arguments, environment, logs, or public artifacts | `SYNC-001`–`SYNC-006`, `SYNC-008`, `MACOS-003` |
| `A-06` | Encrypted mailbox bundles and bounded routing fields | Exchange immutable operations through CloudKit Private Database | Private CloudKit records and local transport state | Protected payload plus provider-visible metadata: authenticate context, bound fields and size, minimize metadata, reject unknown versions | `SYNC-001`, `SYNC-007`, `SYNC-008` |
| `A-07` | macOS proxy snapshot, helper ownership state, selected process identity, and iOS Managed Settings/App Group state | Apply and safely remove only Posato-owned enforcement | Local helper, protected application storage, named Managed Settings store, and minimum App Group state | Security state: exact scope, least privilege, versioning where shared, independent apply/restore verification, repeatable cleanup | `MACOS-001`–`MACOS-005`, `IOS-001`, `IOS-002` |
| `A-08` | Diagnostic, crash, support, and test evidence | Explain failures without reconstructing behavior | Permitted surfaces only: current safe status, explicit bounded local capture, previewed user export, and synthetic tracked evidence | Accepted allowlisted support data with bounded local expiry and cleanup; no automatic remote collection or domains, applications, accounts, devices, keys, tokens, URLs, content, browsing, or usage events | `DIAGNOSTICS-001`, every producing task, `RELEASE-001` |
| `A-09` | User networking, transient macOS enforcement traffic, unselected applications/domains, and unrelated Apple or local data | Make bounded on-device deny and presentation decisions while preserving system availability and non-Posato resources | Operating-system settings plus volatile normal-user helper buffers for HTTP relay, HTTPS tunneling, and one current-tab check; never root durable state or diagnostics | Operational and sensitive transient data: bound processing, avoid semantic inspection beyond routing and presentation, release promptly, mutate exact owned fields, preserve concurrent unrelated changes, and restore safely | `MACOS-001`–`MACOS-005`, `IOS-001`, `IOS-002` |
| `A-10` | Application, helper, synchronization companion, extension, build graph, signing relationship, and dependency graph | Ensure installed code matches reviewed Posato code and privileges | Source, CI, signed artifacts, installed processes, Apple provisioning and distribution | Integrity asset: minimal dependencies and entitlements, pinned reviewed inputs, reproducible checks, no tracked private signing values | `QUALITY-001`, `CI-001`, every dependency or target owner, `RELEASE-001` |

Browsing history, allowed navigation events, full URLs, paths, query strings,
page content, cookies, headers, TLS plaintext, application-use timelines,
behavioral analytics, provider credentials, and unrelated process or device
inventory are outside every stored, synchronized, diagnostic, and public
product model. ADR 0005 permits only two volatile normal-user helper processing
exceptions: bounded cleartext HTTP transit buffers required for safe relay and
one current top-level URL after a host-free blocked signal required for guarded
Safari or Chrome presentation. Those values are never retained as records,
sent to the root daemon, or exposed through IPC outcomes or diagnostics.
Browser-owned history and runtime copies remain outside Posato control. Raw
opaque platform values are outside the shared, synchronized, diagnostic, and
public models; `A-03` permits only the minimum local protected selection or
mapping required for enforcement. An owning task must update this authority
before collecting, retaining, or adding another processing exception for any
excluded class.

## Trust boundaries

| Boundary | Data or capability crossing | Required boundary behavior | Primary owners |
| --- | --- | --- | --- |
| `TB-01` Person or system picker → application | Domains, semantic policy, session intent, opaque platform selection | Bound and validate input; keep canceled, partial, or invalid input from replacing prior valid state | `TARGETS-001`–`TARGETS-004`, `SESSION-001` |
| `TB-02` Application → local database and Keychain | Plaintext policy, replica state, keys, identities, selectors | Use app-private/exact storage, atomic transactions, explicit corruption and account outcomes, no plaintext secret fallback | `MODEL-001`, `SYNC-002`–`SYNC-006` |
| `TB-03` Shared application → iOS native APIs and activity-monitor extension | Semantic commands, opaque selections, minimum expiry state, native outcomes | Keep Apple types native; use a versioned minimum App Group contract; authenticate ownership by entitlement; clear only Posato state | `TARGETS-004`, `IOS-001`, `IOS-002` |
| `TB-04` Desktop JVM application → signed native helper | Enforcement commands and structured results over local IPC | Fixed helper, peer authentication and authorization, bounded versioned allowlist, freshness/replay defense, timeouts, redacted failures | `MACOS-001`, `MACOS-003` |
| `TB-05` macOS helper/proxy → operating system, browsers, and applications | Proxy settings, bounded host decisions, bounded HTTP transit and opaque tunnel chunks, one transient current-tab URL, fixed browser presentation, and process actions | Loopback only; no TLS interception; transient data never becomes a record or diagnostic; exact owned mutation and identity; safe coexistence, rollback, cleanup, and no general command surface | `MACOS-001`, `MACOS-002`, `MACOS-004`, `MACOS-005` |
| `TB-06` Applications and macOS sync companion → CloudKit and synchronizable Keychain | Encrypted bundles, bounded metadata, workspace key | Treat transport input as hostile; isolate accounts and exact resources; represent unavailable/delayed services truthfully | `SYNC-003`, `SYNC-005`–`SYNC-010` |
| `TB-07` Apple-trusted installation → shared workspace | Workspace-key access and signed operations | Treat Apple trust as membership, validate automatic authors, and never imply independent Posato admission or revocation | `SYNC-001`, `SYNC-003`, `SYNC-009` |
| `TB-08` Source/dependency/signing chain → installed process | Code, build tools, dependencies, entitlements, signatures, updates | Review and pin inputs, minimize entitlements, verify identities and artifacts, keep credentials outside source and routine CI | `QUALITY-001`, `CI-001`, target owners, `RELEASE-001` |
| `TB-09` Desktop JVM application → signed synchronization companion | Workspace-key records, opaque mailbox bytes, bounded routing metadata, and semantic outcomes over private IPC | Fixed embedded peers, mutual signed-relationship checks, bounded versioned allowlist, request identity, timeouts, redacted outcomes, and no enforcement or general command surface | `SYNC-003`, `SYNC-006`, `SYNC-008` |

## Threat and control register

| ID | STRIDE | Threat or abuse case | Required control outcome | Owner | Residual after control |
| --- | --- | --- | --- | --- | --- |
| `T-01` | `T/D` | Malformed or ambiguous domain input, Unicode/host normalization, or request parsing broadens or bypasses an exact rule | One bounded canonical exact-domain contract; fail closed on invalid or ambiguous input; test blocked and unselected controls on each backend | `TARGETS-001`, `MACOS-004`, `IOS-001` | A client that does not use the accepted backend is outside that backend's support claim |
| `T-02` | `S/T/D/E` | A record, peer, or transport supplies malformed, oversized, wrongly signed, wrong-context, unknown-version, or unauthorized operations | Bounded canonical decoding, authenticated encryption, signatures, author/context/version validation, and no state construction before complete validation | `SYNC-001`, `SYNC-002`, `SYNC-007`, `SYNC-008` | Cryptography does not restore ciphertext that the provider deletes |
| `T-03` | `T/R` | Duplicate, replayed, reordered, stale, rolled-back, corrupt, or partially stored data regresses policy or loses pending work, reuses a local author sequence, overflows HLC, or lets wall-clock rollback revive an observed-expired session | Fresh process-memory authoring incarnations, retained replay/sequence state, exact ambiguous-commit reconciliation, serialized local/remote checkpoint advancement and same-open footprint checks, fail-closed HLC exhaustion, deterministic full reduction, conflicting-start quarantine, terminal local expiry keyed to the session identifier, one atomic apply, durable retry, and no silent empty initialization | `MODEL-001`, `SESSION-001`, `SESSION-002`, `SYNC-002`, `SYNC-004`, `SYNC-010` | A restored or fresh replica cannot infer data or a local expiry observation absent from every retained source; a terminal HLC blocks local authoring, and Apple MVP availability still depends on surviving data |
| `T-04` | `S/T/D` | CloudKit workspace state races delayed Keychain delivery, account change, reset, entitlement mismatch, or concurrent first run | Deterministic one-workspace bootstrap, exact Keychain item, waiting state, account isolation, restart-safe retry, and never a replacement key or parallel workspace | `SYNC-003`–`SYNC-006`, `SYNC-009` | Losing all workspace-key access is irreversible in the MVP |
| `T-05` | `S/T/I/E` | A malicious or compromised Apple-trusted installation reads or changes the workspace | Authenticate signed authors and operations without weakening Apple admission; state that workspace-key possession authorizes decryption | `SYNC-001`, `SYNC-003`, `SYNC-009` | Accepted: Posato cannot independently exclude or revoke one Apple-trusted installation in the MVP |
| `T-06` | `I/D` | Apple or the network observes metadata, or CloudKit/account/network/quota/background lifecycle delays or denies synchronization | Encrypt author identifiers, public keys, sequences, operations, policy, and session data; expose only format and suite, per-operation bundle identifier and salt, workspace and epoch identifiers, ciphertext size, and transport-required metadata; keep durable local work; expose retry/wait/action-required states; make local enforcement independent of delivery | `SYNC-003`, `SYNC-004`, `SYNC-007`–`SYNC-010`, `SYNC-012` | Accepted: account association, timing, sizes, record counts, stable workspace and epoch routing identifiers, per-operation random identifiers and salts, and service availability remain observable or provider-controlled; no author-level metadata, anonymity, delivery SLA, or wake promise |
| `T-07` | `S/T/I/D/E` | A local process substitutes a helper or synchronization companion, or sends malformed, replayed, stale, oversized, unknown, unauthorized, or hanging IPC | Fixed signed process relationships; authenticate and authorize every request; bounded/versioned allowlists, freshness, request identity, timeout, structured redacted outcomes, and bounded cancellation or recovery of only the authenticated Posato-owned endpoint or process selected by its owning ADR | `MACOS-001`, `MACOS-003`, `SYNC-003`, `SYNC-006`, `SYNC-008` | A compromised correctly authorized native process can exercise its granted capabilities and read plaintext entrusted to it |
| `T-08` | `T/D/E` | Helper privilege, a crash, or concurrent system changes leave a proxy active, overwrite unrelated settings, kill the wrong process, or become an arbitrary command path | Least privilege per operation; no shell surface; exact identities and fields; snapshot/apply/verify/restore ownership; preserve unrelated edits; repeatable recovery and removal | `MACOS-001`, `MACOS-003`–`MACOS-005` | Administrator/root can bypass or alter enforcement; abrupt termination of a selected app may still affect unsaved work and needs truthful product treatment |
| `T-09` | `I/E` | The macOS proxy or browser presentation becomes browsing surveillance, decrypts TLS, exposes private URLs, or permits traffic when presentation fails | Loopback-only bounded parsing and relay; no TLS interception; promptly discard HTTP transit and current-tab URL state; no navigation records, counters, or logs; host-free signal and fixed target-free presentation separated from denial | `MACOS-002`, `MACOS-004`, `DIAGNOSTICS-001` | Cleartext HTTP necessarily exposes bounded request transit data to the normal-user helper, the guarded adapter sees one current top-level URL, browser/runtime copies cannot be completely erased, and presentation retains a narrow Apple Events race |
| `T-10` | `T/I/E` | Opaque iOS selection or App Group state is synchronized, logged, exposed to the wrong target, partially replaced, or cleared outside Posato ownership | Keep opaque values local and protected; minimum versioned App Group schema; entitlement-bound access; named Managed Settings ownership; preserve prior valid selection; idempotent exact cleanup | `TARGETS-004`, `IOS-001`, `IOS-002` | Apple owns framework authorization and callback availability; exact wall-clock expiry execution is not guaranteed |
| `T-11` | `T/D/E` | Enforcement affects an unselected domain/application, conflicts with another proxy/VPN, or cannot restore unrestricted behavior | Explicit support/coexistence contract, exact allowlists, complete no-fallback proxy-chain checks, independent controls, fail-before-mutation preflight, truthful degradation, and cleanup after every failure path | `MACOS-001`, `MACOS-002`, `MACOS-004`, `MACOS-005`, `IOS-001`, `IOS-002` | ADR 0005 limits browser, port, proxy, VPN, captive-portal, cache, and lifecycle coverage; MACOS-004 must prove each positive claim |
| `T-12` | `R/I` | Secrets, policy, domains, applications, account/device identity, opaque tokens, or behavior leak through logs, crash reports, support bundles, process metadata, tests, or public evidence | No secret arguments/environment; stable redacted error categories; explicit field allowlist, purpose, consent, retention, deletion, processor review, and user preview before any support export | `DIAGNOSTICS-001`, every producing task, `RELEASE-001` | The accepted policy permits only safe current status, explicit bounded local capture, and previewed user-controlled export; automatic telemetry, analytics, crash upload, support storage, and processors remain disabled |
| `T-13` | `S/T/I/E` | A compromised dependency, build input, signing path, update, or over-entitled binary defeats application checks | Minimal reviewed dependencies; pinned reproducible inputs; credential-free CI; least entitlements; native-process/parent identity checks; signing, update, license, and security review before release | `QUALITY-001`, `CI-001`, every dependency/target owner, `RELEASE-001` | Compromised installed code has the plaintext and privileges of its process; Posato does not claim protection from itself |
| `T-14` | `T/I/D/E` | Reset, deletion, uninstall, account change, or recovery removes unrelated resources, leaves protected data behind, or is described as remote wipe | Exact namespaces and selectors; explicit destructive intent; idempotent deletion; independent absence and outside-scope preservation checks; safe account isolation and uninstall recovery | `MODEL-001`, `SYNC-003`, `SYNC-005`–`SYNC-010`, `MACOS-001`, `IOS-001`, `IOS-002`, `RELEASE-001` | Provider or backup retention may outlive immediate deletion; data already copied to another device cannot be remotely erased |
| `T-15` | `S/T/I/D/E` | The device owner, administrator/root, debugger, compromised OS, or equivalent-privilege process reads plaintext, disables controls, or removes Posato | Minimize plaintext and privilege, clean up owned mutations, and keep product language aligned with the intentional early-end and removal contract | `MACOS-001`, `MACOS-003`, `IOS-001`, `IOS-002`, `SYNC-005`–`SYNC-009`, `MVP-001`, `RELEASE-001` | Accepted: this MVP interrupts habitual use and is not administrator-resistant or unremovable |

Repudiation controls apply to security-relevant state changes through signed
operations and request identity. Posato must not create a browsing or
application-use audit trail merely to satisfy generic logging guidance.

## Accepted residual risks

The maintainer accepts these MVP limits:

| Residual | MVP disposition | Owner and recheck |
| --- | --- | --- |
| `R-01` Apple Account, iCloud Keychain, or an Apple-trusted installation is compromised | Apple trust remains the complete MVP membership boundary; no per-installation Posato revocation | `SYNC-001`, `SYNC-003`, and `SYNC-009` preserve the boundary; `RELEASE-001` rechecks disclosure |
| `R-02` Administrator/root, debugger, compromised OS, or equivalent Posato process reads plaintext or bypasses enforcement | Accepted product limit; Posato remains removable and early end remains intentional | `MVP-001` verifies truthful behavior; `RELEASE-001` rechecks public claims |
| `R-03` Apple or network infrastructure observes bounded metadata or denies/delays service | No anonymity, delivery, wake, or provider-availability guarantee; author identifier, public key, and sequence remain encrypted | `SYNC-001`, `SYNC-003`, `SYNC-007`–`SYNC-010`, and `SYNC-012` minimize exposure and preserve local safety; `RELEASE-001` rechecks disclosure |
| `R-04` Ciphertext or plaintext already copied elsewhere cannot be remotely erased, and every workspace key may be lost | No remote-wipe or total-key-loss recovery claim in the MVP | `SYNC-003`, `SYNC-005`, `SYNC-006`, `SYNC-009`, `ONBOARDING-001`, and `ONBOARDING-002` preserve truthful states; `RELEASE-001` rechecks disclosure |
| `R-05` Managed runtimes or platform frameworks retain secret copies outside an application's complete erasure control | Minimize lifetime and clear owned mutable buffers where supported; do not claim guaranteed memory erasure | `SYNC-002`, `SYNC-005`, `SYNC-006`, `SYNC-008`, `MACOS-003`, and `MACOS-004` apply the rule; `RELEASE-001` rechecks the claim |
| `R-06` System-proxy coverage, browser state, cached content, or Apple Events behavior escapes the bounded macOS claim | Accept only Safari and Chrome on verified versions, ports 80 and 443, no content-erasure or administrator-resistance promise, and one disclosed presentation race | `MACOS-004` proves the exact matrix and failure paths; `RELEASE-001` rechecks versions and public disclosure |

These are not accepted blanket support claims. `user-confirmed` (2026-08-26):
[ADR 0004](../decisions/0004-macos-helper-ownership-and-lifecycle.md) accepts
the macOS helper ownership, privilege, authorization, lifecycle, and recovery
architecture, while
[ADR 0005](../decisions/0005-macos-browser-enforcement-and-coexistence.md)
accepts the narrow Safari and Chrome browser, exact-domain, coexistence,
transient-data, presentation, and network-transition contract. Their production
controls and positive browser claims remain unverified until MACOS-003,
MACOS-004, and the named downstream enforcement tasks supply their required
evidence. Application termination, iOS entitlement/distribution, App Group
schema, and non-diagnostic lifecycle rules other than the accepted local
exact-domain policy remain blocked on their named tasks. ADR 0006 accepts the
cryptographic format and automatic signed-author contract, but `SYNC-002` must
still prove its implementation and cross-target behavior before the controls
are verified. [ADR 0007](../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
accepts the Apple bootstrap and macOS synchronization-companion boundary, but
`SYNC-004` through `SYNC-009` must still implement and prove it. Diagnostic producers remain unimplemented and must satisfy the
accepted diagnostic policy in their own tasks. `RELEASE-001` must recheck the
residuals and feature gates before any public-release claim.

## Change and review rule

An owning task must update or explicitly remain within this model when it adds
a data class, trust boundary, entitlement, privileged operation, external
service, diagnostic field, persistent store, cryptographic key, or public
security claim. A newly discovered threat that lacks a bounded owner and
verification keeps the affected capability disabled. Security-relevant
implementation requires the task's focused tests and proportional independent
review; this document alone verifies no control.

## Feasibility provenance and limits

The model consulted the read-only `.research/blocker` checkout at final
feasibility revision `bcdc8ce9b91ecb7569c2d98b568d5fd64c25455c`, including:

- `docs/security/apple-sync-poc-threat-model.md` for bounded remote-input,
  atomic-apply, helper-IPC, exact-cleanup, and privacy abuse cases;
- `evidence/apple-sync-poc/report.md` and `privacy-contract.md` for the final
  one-Mac/one-iPhone synchronization and evidence limits; and
- `evidence/apple-enforcement-spike/report.md` for the final macOS/iOS
  mechanism, control, cleanup, privacy, and distribution limits.

`observed`: those experiments passed their synthetic development matrices and
exact cleanup. They did not establish the production cryptographic format,
helper protocol, privilege model, browser support, entitlement eligibility,
bypass resistance, distribution readiness, or this threat model. No PoC code,
protocol version, dependency choice, private identifier, signing value, raw
evidence, or `VERIFIED` status is imported by this model.

## Authoritative inputs

- [MVP scope](../product/mvp-scope.md)
- [ADR 0002: synchronization trust and workspace modes](../decisions/0002-synchronization-trust-and-workspace-modes.md)
- [ADR 0003: MVP application architecture baseline](../decisions/0003-mvp-application-architecture-baseline.md)
- [ADR 0006: Apple MVP encrypted operation and convergence contract](../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md)
- [ADR 0007: Apple workspace bootstrap and macOS native sync boundary](../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
- [Engineering quality contract](../development/engineering-quality-contract.md)
- [MVP roadmap](../tasks/mvp-roadmap.md)
- [Diagnostics and support-data policy](diagnostics-and-support-data.md)
- [Maintained privacy and trust synthesis](../wiki/topics/privacy-and-trust-model.md)
- [Synchronization synthesis](../wiki/topics/cross-device-synchronization.md)
- [macOS enforcement synthesis](../wiki/topics/macos-enforcement.md)
- [iOS enforcement synthesis](../wiki/topics/ios-enforcement.md)
