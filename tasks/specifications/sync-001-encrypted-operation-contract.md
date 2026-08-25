# `SYNC-001`: Accept the encrypted-operation contract

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SECURITY-001`
- **Stable concurrency constraints:** Durable synchronization, security, and wiki authority remains serialized with other decision tasks.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

The maintainer has accepted the bounded, versioned, signed, encrypted, immutable-operation and convergence contract used by every Apple synchronization adapter.

## Context

The accepted trust boundary requires common end-to-end encryption and signed immutable operations while intentionally deferring exact format, cryptography, limits, conflict semantics, and evolution rules.

## In scope

- Define operation identity, authorship, versioning, ordering, validation, conflict, replay, and compatibility semantics.
- Define encryption, signing, key-purpose, encoding, and size-limit requirements at the contract level.
- Define rejection, quarantine, retry, and last-valid-state behavior for invalid or unsupported input.
- Specify cross-target interoperability evidence and ownership of future evolution.

## Out of scope

- Implementing cryptography, persistence, CloudKit, Keychain, or UI.
- Portable workspaces, manual recovery codes, product accounts, or total-key-loss recovery.
- Promising transport delivery time.

## Acceptance criteria

- `AC-01` — One accepted ADR defines the complete wire and semantic contract needed by all synchronization consumers.
- `AC-02` — The contract prevents unauthenticated, replayed, malformed, oversized, unsupported, or conflicting input from replacing valid local state.
- `AC-03` — Key purposes, identity, rotation assumptions, version negotiation, and algorithm agility are explicit without leaking secrets into transport or diagnostics.
- `AC-04` — Deterministic cross-target test-vector and compatibility requirements are defined.
- `AC-05` — The maintainer accepts all durable cryptographic and conflict choices before `SYNC-002` or `SYNC-003` starts.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Architecture and security decision review.
- Threat-to-control and consumer mapping.
- Cross-target vector and negative-test design.
- Privacy and compatibility review.
- Independent cryptography-focused completed-change review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Defines rejection of malformed, oversized, replayed, stale, unsupported, and conflicting transport input. |
| Authentication or authorization | yes | Owns author identity, signing authority, replay defense, and operation-admission semantics. |
| Secrets, signing, or credentials | yes | Defines key purposes, exposure boundaries, rotation assumptions, and prohibited metadata. |
| Personal data or diagnostics | yes | Defines bounded routing metadata and excludes protected plaintext, opaque selections, and sensitive diagnostics. |
| Storage or migration | yes | Defines sequence, high-water, compatibility, last-valid-state, and evolution obligations consumed by persistence owners. |
| Cryptography | yes | Selects the durable encryption, authentication, signing, encoding, versioning, and agility contract. |
| Native IPC or entitlements | no | The encrypted-operation decision outcome does not change native IPC, entitlement, signing, or platform lifecycle ownership. |
| External services | yes | Treats every transport as an untrusted mailbox and defines semantic outcomes without implementing or physically testing a service. |
| Dependencies or licenses | yes | Sets compatibility, maintenance, misuse-resistance, provenance, and license criteria for the provider selected by `SYNC-002`. |
| PoC reuse or external provenance | yes | Feasibility cryptographic evidence is bounded and any production reuse needs fresh ownership and vectors. |

## Decision gates

- Maintainer acceptance of cryptographic, versioning, validation, conflict, and evolution choices is required.
- Any production cryptographic provider requires separate dependency, license, platform, and misuse-resistance review in `SYNC-002`.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
