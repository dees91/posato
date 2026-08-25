# `SYNC-010`: Coordinate bounded CloudKit synchronization

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SYNC-009`, `MODEL-001`
- **Stable concurrency constraints:** Shared replica, pending queue, synchronization status, UI composition, and Apple integration ownership are serialized.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

The local pending queue publishes and consumes bounded encrypted bundles with deterministic application and truthful waiting, retry, conflict, and failure status.

## Context

Bootstrap establishes a workspace, but policy and session tasks need a reusable coordinator that moves immutable operations between the local replica and opaque CloudKit mailboxes.

## In scope

- Publish eligible pending operations and consume validated remote operations through the accepted contracts.
- Track bounded progress, retry, duplicate, conflict, account, offline, restart, and cleanup outcomes.
- Preserve last valid local state and expose truthful status without a delivery-time promise.
- Provide deterministic fakes and integration seams for later policy and session synchronization.

## Out of scope

- Defining policy-specific or session-specific operation semantics.
- Onboarding screens.
- Background-delivery guarantees, analytics, or server infrastructure.

## Acceptance criteria

- `AC-01` — Eligible local operations become bounded opaque mailbox bundles and valid remote bundles update the replica deterministically.
- `AC-02` — Duplicate, reordered, stale, tampered, oversized, unsupported, conflicting, offline, account-changed, and restarted flows preserve last valid state and remain bounded.
- `AC-03` — Pending, waiting, retrying, action-required, and current states are distinguishable without implying immediate remote delivery.
- `AC-04` — Retry and cleanup cannot grow local or remote work without the accepted bounds.
- `AC-05` — Protected plaintext, keys, opaque application selections, browsing activity, and credentials remain outside transport metadata and diagnostics.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Coordinator unit, contract, integration, bounds, restart, and property tests.
- Physical Mac-and-iPhone publish/consume/offline/retry/account scenarios.
- Privacy, security, storage, performance, and resource-bound review.
- Aggregate quality gate and independent review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates remote bundles plus duplicate, reordered, stale, conflicting, oversized, offline, retry, and restarted work. |
| Authentication or authorization | yes | Consumes only authenticated accepted operations for the active Apple workspace and account context. |
| Secrets, signing, or credentials | yes | Keeps keys, credentials, opaque selections, and protected plaintext out of queue metadata and diagnostics. |
| Personal data or diagnostics | yes | Owns truthful bounded synchronization status and the accepted metadata and diagnostics boundary. |
| Storage or migration | yes | Owns pending-queue durability, bounds, restart, retry, application, cleanup, and last-valid-state behavior. |
| Cryptography | yes | Uses the accepted encrypted-operation core and rejects any plaintext or unauthenticated transport path. |
| Native IPC or entitlements | no | The CloudKit synchronization coordinator outcome does not change native IPC, entitlement, signing, or platform lifecycle ownership. |
| External services | yes | Coordinates physical CloudKit publish and consume outcomes without promising wake-up or delivery time. |
| Dependencies or licenses | no | The CloudKit synchronization coordinator outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | Feasibility synchronization coordination is bounded evidence and any reused idea requires fresh production ownership, bounds, and tests. |

## Decision gates

- Bootstrap integration and local replica contracts must be stable.
- Background execution limits remain observable constraints rather than delivery promises.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
