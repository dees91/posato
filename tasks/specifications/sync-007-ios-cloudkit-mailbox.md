# `SYNC-007`: Exchange encrypted mailbox data on iOS

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SYNC-003`
- **Stable concurrency constraints:** iOS CloudKit, entitlement, signing, and
  Xcode project ownership are serialized against other iOS Apple-adapter work.
  The shared container and mailbox-schema contract is frozen by `SYNC-003` and
  is not owned by this task.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

The iOS application exchanges bounded encrypted mailbox bundles with its private CloudKit database through the accepted semantic adapter and truthful service outcomes.

## Context

The bootstrap and sync coordinator require an iOS-native transport that treats CloudKit as an untrusted opaque mailbox rather than a source of plaintext authority.

## In scope

- Create, fetch, update, and retire bounded opaque mailbox data within the
  accepted private-database and mailbox-schema contract.
- Map unavailable, unauthenticated, quota, conflict, retry, account-change, and partial outcomes semantically.
- Preserve idempotency and bounded resource use across restart and retry.
- Keep plaintext protected content, workspace keys, and sensitive diagnostics outside CloudKit.

## Out of scope

- Decrypting or applying operations.
- Bootstrap or synchronization orchestration and user-facing onboarding.
- Public database, sharing, product accounts, or transport delivery guarantees.

## Acceptance criteria

- `AC-01` — On a physical iPhone, the adapter publishes and retrieves bounded opaque mailbox data from the intended private container.
- `AC-02` — Unauthenticated, unavailable, quota, conflict, duplicate, partial, delayed, account-changed, and restart outcomes map truthfully to the accepted contract.
- `AC-03` — Retry and cleanup behavior is idempotent and does not create unbounded records, payloads, or local work.
- `AC-04` — CloudKit records and diagnostics contain no workspace key or plaintext protected content.
- `AC-05` — Container, environment, account isolation, entitlement, and schema
  behavior conforms to the frozen cross-platform mailbox contract and passes
  the controlled physical matrix.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Semantic adapter tests with deterministic fakes.
- Physical iPhone CloudKit publish/fetch/retry/account/quota-like/error/restart/cleanup matrix.
- Privacy, secret, entitlement, schema, bounds, and performance review.
- Aggregate quality gate and independent review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Treats CloudKit records, conflicts, retries, duplicates, partial results, and size metadata as untrusted. |
| Authentication or authorization | yes | Enforces the intended private-container, environment, and Apple-account boundary. |
| Secrets, signing, or credentials | yes | Prohibits workspace keys, credentials, and plaintext protected content from records and errors. |
| Personal data or diagnostics | yes | Restricts mailbox metadata and diagnostics to the accepted bounded routing contract. |
| Storage or migration | yes | Implements iOS record, retry, restart, idempotency, and cleanup behavior against the mailbox schema frozen by `SYNC-003`; it does not redefine that schema. |
| Cryptography | yes | Transports only accepted encrypted bundles and must not introduce a plaintext fallback. |
| Native IPC or entitlements | yes | Implements the iOS CloudKit entitlement and lifecycle boundary against the shared container and schema contract frozen by `SYNC-003`. |
| External services | yes | CloudKit account, environment, quota-like, delay, conflict, and cleanup behavior require physical iPhone evidence. |
| Dependencies or licenses | no | The iOS CloudKit mailbox adapter outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | PoC CloudKit evidence remains bounded and requires fresh production ownership and physical validation. |

## Decision gates

- The required private CloudKit container, environment, entitlement, and account access must be available.
- The cross-platform container, mailbox schema, bounds, versioning, retention,
  and cleanup rules accepted by `SYNC-003` must remain frozen. Any required
  contract change returns to that decision task or serializes the affected
  adapter work; platform retry mechanics belong to this task's reviewed plan.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
