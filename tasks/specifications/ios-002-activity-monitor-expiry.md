# `IOS-002`: Clear iOS enforcement after normal expiry

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `IOS-001`
- **Stable concurrency constraints:** iOS extension, App Group state, entitlement, and Xcode project ownership are serialized.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

The iOS activity-monitor extension removes only Posato-owned restrictions after normal session expiry even when the application is suspended.

## Context

Application-owned timers cannot be trusted to run while iOS suspends the app. Normal expiry therefore needs a narrow extension that follows the frozen session and App Group ownership contract.

## In scope

- Observe the committed local session's normal-expiry boundary while the main application is suspended.
- Clear only the corresponding Posato-owned restrictions idempotently.
- Handle duplicate, late, missing, corrupt, or restarted extension events safely.
- Expose enough privacy-safe state for the application to reconcile after resume.

## Out of scope

- Starting sessions, ending them early, or applying the initial restrictions.
- Custom shields, remote notifications, or cross-device synchronization.
- Clearing unrelated restrictions.

## Acceptance criteria

- `AC-01` — On a physical iPhone with the application suspended, when iOS
  provides the normal interval-end callback opportunity, the extension clears
  the selected Posato-owned restrictions without waiting for the application
  to resume.
- `AC-02` — Duplicate, late, reordered, missing, corrupt, and extension-restart events are idempotent and cannot clear a newer session or unrelated controls.
- `AC-03` — Application resume reconciles to truthful expired or action-required state without manual repair.
- `AC-04` — The extension reads only the minimum App Group data required for expiry and emits no opaque tokens, browsing activity, secrets, or personal identifiers.
- `AC-05` — Entitlement, lifecycle, battery, and cleanup behavior pass the controlled physical-device matrix.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Extension contract and idempotency tests where deterministic.
- Physical iPhone suspended-expiry, newer-session, duplicate-event, restart, and cleanup matrix.
- App Group, entitlement, privacy, performance, and secret review.
- Aggregate quality gate and independent review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates duplicate, late, reordered, missing, corrupt, restarted, and newer-session extension events. |
| Authentication or authorization | yes | Confirms session ownership before clearing restrictions and cannot clear a newer or unrelated control. |
| Secrets, signing, or credentials | yes | Extension signing, entitlement, and App Group configuration are reviewed without tracking private material. |
| Personal data or diagnostics | yes | Reads only minimum expiry state and emits no opaque selection, browsing, key, or private identifier data. |
| Storage or migration | yes | Owns the minimum App Group schema and idempotent reconciliation needed for suspended expiry. |
| Cryptography | no | The iOS activity-monitor expiry slice outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Implements the Device Activity monitor extension and its application lifecycle contract. |
| External services | yes | Apple callback opportunity, suspension, entitlement, and battery behavior require physical iPhone evidence without a wall-clock promise. |
| Dependencies or licenses | no | The iOS activity-monitor expiry slice outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | Feasibility expiry and lifecycle observations are bounded evidence and require fresh extension ownership and physical verification. |

## Decision gates

- The App Group session-state ownership established by `IOS-001` must be frozen.
- Required Device Activity entitlement and physical-device behavior must be available.
- The maintainer must accept callback-opportunity evidence that makes no exact
  wall-clock, device-wake, or delivery-time promise.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [iOS enforcement topic](../../docs/wiki/topics/ios-enforcement.md)
