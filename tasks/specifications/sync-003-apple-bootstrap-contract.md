# `SYNC-003`: Accept the Apple workspace bootstrap contract

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SYNC-001`, `APPLE-001`
- **Stable concurrency constraints:** Durable architecture, security,
  Apple-resource, shared Keychain-item, shared CloudKit-mailbox, and wiki
  authority remains serialized with other decision tasks.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

The maintainer has accepted an interoperable one-workspace bootstrap and
native-adapter contract across CloudKit discovery, synchronizable Keychain
delay, account changes, conflicts, retries, and cleanup.

## Context

The trust model permits exactly one Apple-managed workspace and requires a second installation to wait for delayed key delivery rather than create a parallel workspace.

## In scope

- Define discovery, creation eligibility, key availability, waiting, conflict, account-change, retry, restart, and cleanup states.
- Define narrow per-platform Keychain and CloudKit adapter outcomes without
  leaking framework types into shared logic.
- Freeze one cross-platform synchronizable-Keychain item contract, including
  identity, attributes, mutation, deletion, account-isolation, and versioning
  responsibilities, before either platform adapter starts.
- Freeze one private-CloudKit mailbox contract, including container and
  database ownership, record identity, schema compatibility, bounds,
  versioning, retention, and cleanup responsibilities, before either platform
  adapter starts.
- Decide macOS Keychain and CloudKit ownership within the accepted application
  target graph, without giving synchronization responsibility to the
  enforcement helper.
- Define one-workspace invariants and truthful user-visible status.
- Assign physical race, account, and failure evidence to later adapters and integration.

## Out of scope

- Implementing state machines, Keychain, CloudKit, cryptography, or onboarding UI.
- Portable workspaces, product accounts, manual recovery codes, or total-key-loss recovery.
- Guaranteeing Apple service delivery time.

## Acceptance criteria

- `AC-01` — One accepted ADR defines bootstrap states and transitions for first and later installations without any path that silently creates a parallel workspace after prior workspace evidence.
- `AC-02` — Delayed, unavailable, conflicting, stale, corrupt, and account-changed outcomes preserve valid local state and have explicit retry or action-required behavior.
- `AC-03` — Native adapter contracts are semantic, bounded, privacy-safe, and
  testable with deterministic fakes; macOS synchronization ownership either
  stays within the accepted application target graph or identifies the exact
  architecture and Apple-resource amendment required before any consumer can
  start.
- `AC-04` — Creation eligibility, operation idempotency, restart recovery, and
  cleanup responsibilities are explicit, and one interoperable Keychain-item
  contract plus one CloudKit-mailbox schema contract are frozen for both
  platform implementations.
- `AC-05` — The maintainer accepts the contract before state-machine or Apple-adapter implementation starts.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Architecture, security, and privacy decision review.
- State-transition and invariant model review.
- Cross-platform adapter-contract, consumer, and roadmap dependency
  validation.
- Physical race/account test design.
- Independent completed-change review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Defines handling for stale, duplicate, conflicting, corrupt, reordered, and impossible adapter outcomes. |
| Authentication or authorization | yes | Owns creation eligibility, Apple-account membership assumptions, account isolation, and action-required semantics. |
| Secrets, signing, or credentials | yes | Defines workspace-key availability and exposure outcomes without transporting or logging key material. |
| Personal data or diagnostics | yes | Restricts adapter outcomes and user-visible status to bounded privacy-safe data. |
| Storage or migration | yes | Defines persistent bootstrap state, restart, idempotency, conflict, and cleanup responsibilities and freezes the interoperable Keychain-item and CloudKit-mailbox contracts. |
| Cryptography | yes | Preserves the accepted encrypted-operation and workspace-key boundaries while assigning implementation elsewhere. |
| Native IPC or entitlements | yes | Decides iOS adapters and app-owned macOS Keychain and CloudKit access without assigning synchronization to the enforcement helper. |
| External services | yes | Models Apple service availability, delay, account, and conflict outcomes and assigns later physical evidence; this decision task performs no service integration. |
| Dependencies or licenses | no | The Apple workspace-bootstrap decision outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | Separates PoC race observations from the accepted production bootstrap contract. |

## Decision gates

- Maintainer acceptance of one-workspace creation eligibility, delayed-key
  behavior, account-change behavior, adapter ownership, the shared Keychain
  item contract, and the shared CloudKit mailbox contract is required.
- Apple resource availability established by `APPLE-001` must support the accepted contract.
- The macOS enforcement helper must not own synchronization. If the decision
  requires another process, target, identifier, capability, or signing
  boundary, this task remains blocked until the maintainer accepts an ADR 0003
  amendment and the Apple resource record is updated.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
- [Cross-device synchronization topic](../../docs/wiki/topics/cross-device-synchronization.md)
