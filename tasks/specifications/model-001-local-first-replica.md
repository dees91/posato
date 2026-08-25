# `MODEL-001`: Persist the first exact-domain replica slice

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `FOUNDATION-001`, `SECURITY-001`, `DIAGNOSTICS-001`
- **Stable concurrency constraints:** Shared model, persistence schema, root build, and migration ownership are serialized.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

Each application has an atomic first local-replica slice for exact-domain
policy that survives restart without storing forbidden platform data.

## Context

Exact-domain management is the first production consumer of local persistence.
It establishes the smallest reusable storage boundary; semantic application
policy, session, and synchronization state enter later with their owning
vertical tasks.

## In scope

- Represent exact-domain policy and its persistence failure or recovery status.
- Persist and restore the last valid exact-domain policy atomically across
  supported application restarts.
- Expose observable storage failure and recovery outcomes consistent with the
  diagnostics policy.
- Define compatibility and migration expectations for this first production
  schema slice.

## Out of scope

- Platform-specific opaque application selections or Apple credentials.
- CloudKit, Keychain, helper, or activity-monitor integration.
- Target-management user interfaces.
- Semantic application policy, local mapping, session, operation,
  synchronization, or onboarding state.

## Acceptance criteria

- `AC-01` — Valid exact-domain policy can be created, persisted, reloaded, and
  observed through the shared replica on both application targets.
- `AC-02` — Interrupted or invalid writes do not replace the last valid state and expose a truthful recoverable outcome.
- `AC-03` — Forbidden secrets, opaque platform tokens, browsing history, and allowed navigation events are absent from the shared replica.
- `AC-04` — Schema compatibility and first-migration expectations are documented and tested at the appropriate boundary.
- `AC-05` — The storage boundary has named immediate consumers in
  `TARGETS-001` and `TARGETS-002`; later tasks extend it only for their own
  product outcomes.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Shared model and persistence unit or contract tests.
- Restart, invalid-state, and atomicity integration tests.
- Storage/privacy schema review.
- macOS runtime and iOS Simulator evidence.
- Dependency, license, and provenance review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates persisted exact-domain state and preserves the last valid replica when stored input is corrupt or incompatible. |
| Authentication or authorization | no | The first local-replica slice outcome does not change identity, admission, permission, or authorization decisions. |
| Secrets, signing, or credentials | yes | The first replica schema must exclude credentials, workspace keys, opaque selections, and signing material. |
| Personal data or diagnostics | yes | The schema review enforces the no-browsing-history and no-allowed-navigation boundary. |
| Storage or migration | yes | Owns atomic exact-domain persistence, restart recovery, first-schema compatibility, and migration expectations for this slice. |
| Cryptography | no | The first local-replica slice outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | no | The first local-replica slice outcome does not change native IPC, entitlement, signing, or platform lifecycle ownership. |
| External services | no | The first local-replica slice outcome does not integrate with an external runtime service or own physical service evidence. |
| Dependencies or licenses | yes | Any first persistence dependency is selected with target compatibility, maintenance, provenance, and license review. |
| PoC reuse or external provenance | yes | Persistence ideas from feasibility work may be selectively re-established but not copied wholesale. |

## Decision gates

- The accepted threat model and diagnostics boundary must define what may be persisted and reported.
- Exact storage technology and schema design belong to the authorized execution plan.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
