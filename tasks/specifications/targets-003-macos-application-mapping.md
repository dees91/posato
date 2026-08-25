# `TARGETS-003`: Map semantic application policies on macOS

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `TARGETS-002`
- **Stable concurrency constraints:** The macOS platform leaf must keep shared model, UI contracts, and other platform leaves frozen during its wave.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

A person can associate and remove device-local macOS application selections for an existing semantic policy, and the application reports mapping availability truthfully.

## Context

Shared application policy cannot enforce a platform token. Each Mac owns its own selection and mapping while shared policy remains semantic.

## In scope

- Present local macOS application selection for an existing semantic policy.
- Store the local mapping without adding native selection identity to shared or synchronized policy.
- Represent missing, invalidated, unavailable, and repaired local mappings.
- Meet macOS design, keyboard, accessibility, privacy, and lifecycle expectations.

## Out of scope

- macOS application enforcement.
- iOS selection, cross-device transfer of selections, or label-as-identity behavior.
- Changing shared semantic policy.

## Acceptance criteria

- `AC-01` — A person can select, replace, and remove a local macOS application mapping for an existing semantic policy.
- `AC-02` — The mapping survives restart on that Mac and is never exposed through shared policy or synchronization state.
- `AC-03` — Missing, unavailable, moved, or invalidated selections produce truthful action-required state and cannot be mistaken for enforceable coverage.
- `AC-04` — The macOS flow passes keyboard, focus, screen-reader, text-scaling, and non-color state checks.
- `AC-05` — Only the minimum local application identity required for enforcement is retained and diagnosed.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- macOS adapter and persistence tests.
- Physical Mac selection, restart, invalidation, and repair matrix.
- Visual and accessibility checks.
- Privacy and provenance inspection.
- Aggregate quality gate.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates selected, moved, missing, ambiguous, unavailable, and invalidated native application identities. |
| Authentication or authorization | yes | Reviews the local authority required to select and retain application identity for later enforcement. |
| Secrets, signing, or credentials | no | The macOS application-mapping slice outcome does not handle secrets, signing material, provisioning assets, or credentials. |
| Personal data or diagnostics | yes | Retains only minimum local application identity and never synchronizes or over-diagnoses it. |
| Storage or migration | yes | Owns device-local mapping persistence, restart, invalidation, repair, and removal. |
| Cryptography | no | The macOS application-mapping slice outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Implements the macOS selection boundary behind the frozen semantic mapping contract. |
| External services | no | The macOS application-mapping slice outcome does not integrate with an external runtime service or own physical service evidence. |
| Dependencies or licenses | yes | The technical plan must determine whether a native binding adds a dependency and, if so, review compatibility and license. |
| PoC reuse or external provenance | yes | Feasibility and prototype selection evidence requires fresh production ownership and physical validation. |

## Decision gates

- The selected native identity and invalidation policy must satisfy the accepted threat model; exact APIs belong to the task plan.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Design authority](../../DESIGN.md)
- [Interaction prototype source digest](../../docs/wiki/sources/mvp-interaction-prototype.md)
