# `SYNC-002`: Implement deterministic encrypted-operation processing

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SYNC-001`
- **Stable concurrency constraints:** Shared synchronization model, cryptographic dependency, vectors, and root build ownership are serialized.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

Both application targets create and process compatible signed encrypted operations that converge deterministically and reject invalid input without replacing valid state.

## Context

The accepted synchronization contract needs one shared, production-owned core before Apple adapters can transport or apply operations.

## In scope

- Create, validate, authenticate, encrypt, decrypt, order, and apply bounded operations according to the accepted contract.
- Produce deterministic cross-target compatibility evidence.
- Reject replayed, malformed, tampered, oversized, unsupported, or conflicting input safely.
- Preserve last valid state and expose privacy-safe error categories.

## Out of scope

- CloudKit or Keychain integration.
- Bootstrap, onboarding, or synchronization UI.
- Changing the accepted cryptographic or conflict contract.

## Acceptance criteria

- `AC-01` — Known cross-target vectors are produced and consumed compatibly by macOS/JVM and iOS/native targets.
- `AC-02` — Tampered, unauthenticated, replayed, malformed, oversized, unsupported, and conflicting inputs are rejected without replacing last valid state.
- `AC-03` — Valid operation sets converge deterministically across order, retry, duplicate, and restart scenarios defined by the contract.
- `AC-04` — Secrets and plaintext protected content are absent from transport metadata, logs, diagnostics, fixtures, and failure output.
- `AC-05` — Every production cryptographic dependency passes compatibility, maintenance, provenance, license, and misuse-resistance review.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Cross-target golden vectors and property or contract tests.
- Negative, replay, duplicate, order, restart, and bounds tests.
- Cryptography, secret, dependency, license, and provenance review.
- Aggregate quality gate and independent security-focused review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Rejects malformed, oversized, replayed, stale, unsupported, tampered, and conflicting operations. |
| Authentication or authorization | yes | Implements author authentication, signing verification, context validation, and replay defense from the accepted contract. |
| Secrets, signing, or credentials | yes | Handles production key material with bounded lifetime and prohibits it from logs, diagnostics, fixtures, and transport metadata. |
| Personal data or diagnostics | yes | Protects operation plaintext and enforces the accepted bounded metadata contract. |
| Storage or migration | no | The shared encrypted-operation core outcome does not change durable product storage, compatibility, or migration behavior. |
| Cryptography | yes | Implements compatible cross-target encryption, authentication, signing, canonical validation, and negative vectors. |
| Native IPC or entitlements | no | The shared encrypted-operation core outcome does not change native IPC, entitlement, signing, or platform lifecycle ownership. |
| External services | no | The shared encrypted-operation core outcome does not integrate with an external runtime service or own physical service evidence. |
| Dependencies or licenses | yes | Selects the production cryptographic provider with cross-target compatibility, maintenance, provenance, license, and misuse-resistance review. |
| PoC reuse or external provenance | yes | PoC vectors or code may inform the implementation only after fresh provenance, ownership, and test review. |

## Decision gates

- `SYNC-001` must be accepted without unresolved cryptographic or conflict choices.
- The selected production cryptographic provider must support both targets and pass focused review.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
