# `TARGETS-004`: Map semantic application policies on iOS

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `TARGETS-002`, `APPLE-001`
- **Stable concurrency constraints:** iOS app, entitlement, opaque-selection storage, and Xcode project ownership are serialized against other iOS platform tasks.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

A person can authorize and associate an opaque device-local iOS application selection with an existing semantic policy without exposing or synchronizing that selection.

## Context

iOS Family Controls selections are platform-owned and opaque. The shared product can synchronize intent only while each iPhone retains its own mapping.

## In scope

- Request authorization contextually when local mapping is chosen.
- Present local iOS application selection for an existing semantic policy.
- Store and restore the opaque selection only within the accepted local platform boundary.
- Represent denied, revoked, unavailable, empty, and repaired mappings truthfully.

## Out of scope

- iOS enforcement or expiry behavior.
- Synchronizing, logging, exporting, or interpreting opaque selections.
- Changing shared semantic policy or requesting permission during unrelated onboarding.

## Acceptance criteria

- `AC-01` — On a physical iPhone, a person can grant available authorization and select, replace, or remove a local mapping for an existing semantic policy.
- `AC-02` — The opaque selection survives supported restart where permitted and never enters shared model, sync payloads, diagnostics, or exports.
- `AC-03` — Denied, revoked, unavailable, empty, or invalid selection state prevents false enforceability claims and offers the appropriate local action.
- `AC-04` — The flow passes applicable screen-reader, text-scaling, switch-control, touch-target, and non-color state checks.
- `AC-05` — Entitlement or distribution unavailability blocks the task with a recorded clearing condition.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- iOS platform contract tests where deterministic.
- Physical iPhone authorization, selection, restart, revocation, and repair matrix.
- Visual and accessibility checks.
- Entitlement, privacy, and secret review.
- Aggregate quality gate.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Handles empty, invalid, revoked, unavailable, and opaque selection outcomes without exposing or corrupting the last valid mapping. |
| Authentication or authorization | yes | Owns contextual Family Controls authorization and truthful denied or revoked state. |
| Secrets, signing, or credentials | no | The iOS application-mapping slice outcome does not handle secrets, signing material, provisioning assets, or credentials. |
| Personal data or diagnostics | yes | Opaque selections remain device-local and outside shared state, sync, diagnostics, exports, and tracked evidence. |
| Storage or migration | yes | Owns permitted local opaque-selection persistence, restart, invalidation, repair, and removal. |
| Cryptography | no | The iOS application-mapping slice outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Implements the iOS Family Controls selection and entitlement boundary. |
| External services | yes | Family Controls authorization, entitlement, and physical-device behavior are external platform prerequisites owned by this task. |
| Dependencies or licenses | no | The iOS application-mapping slice outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | Feasibility and prototype selection evidence remains bounded and requires fresh production verification. |

## Decision gates

- Required Family Controls entitlement and profile support must be available for the physical evidence.
- Exact platform APIs and opaque storage mechanics belong to the reviewed execution plan.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Design authority](../../DESIGN.md)
- [iOS enforcement topic](../../docs/wiki/topics/ios-enforcement.md)
- [Interaction prototype source digest](../../docs/wiki/sources/mvp-interaction-prototype.md)
