# `SESSION-001`: Provide shared manual-session behavior

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `TARGETS-001`, `TARGETS-002`
- **Stable concurrency constraints:** Shared session model, state holder, UI, navigation, and semantic enforcer contract ownership are serialized.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

Both applications provide shared setup, review, start, early-end, and normal-expiry behavior for one bounded manual session through a semantic enforcement boundary.

## Context

The MVP needs one deliberate manual session model before platform enforcement implementations attach. Shared behavior can use fakes and must reject invalid effective local mappings.

## In scope

- Provide session setup, duration, target selection, review, confirmation, active, early-end, expiry, and recoverable failure states.
- Validate effective local target availability before committing a start.
- Drive enforcement through a narrow semantic boundary testable with deterministic fakes.
- Meet the accepted design and accessibility baseline on both application shells.

## Out of scope

- Native macOS or iOS enforcement.
- Cross-device synchronization or delivery guarantees.
- Schedules, overlapping sessions, stronger early-end friction, or prototype Free play tools.

## Acceptance criteria

- `AC-01` — A person can configure, review, start, end early, and observe normal expiry for one bounded manual session in deterministic shared behavior.
- `AC-02` — Start is rejected before committing state when no effective local target is enforceable, with exact action-required reasons.
- `AC-03` — Partial semantic-enforcer failure preserves a consistent last valid session state and provides bounded retry or repair behavior.
- `AC-04` — Session state survives supported restart according to the accepted local contract and never implies remote delivery.
- `AC-05` — The shared UI passes behavior, visual, keyboard/touch, screen-reader, text-scaling, focus, and reduced-motion checks where applicable.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Session state-machine and semantic-enforcer contract tests.
- Compose UI tests for primary and failure flows.
- macOS runtime and iOS Simulator visual/accessibility checks.
- Persistence and diagnostics-policy review.
- Aggregate quality gate.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates duration, selected targets, effective local mappings, stale state, and semantic-enforcer outcomes before committing a session. |
| Authentication or authorization | yes | Commits a session only after accepted local authorization and enforceability checks succeed. |
| Secrets, signing, or credentials | no | The shared manual-session slice outcome does not handle secrets, signing material, provisioning assets, or credentials. |
| Personal data or diagnostics | yes | Persists policy and session intent without browsing, application-usage, or sensitive diagnostic history. |
| Storage or migration | yes | Owns shared local session persistence, restart reconciliation, early-end, expiry, and last-valid-state behavior. |
| Cryptography | no | The shared manual-session slice outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Defines and tests the semantic enforcer seam with fakes; concrete platform enforcement remains out of scope. |
| External services | no | The shared manual-session slice outcome does not integrate with an external runtime service or own physical service evidence. |
| Dependencies or licenses | no | The shared manual-session slice outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | The disposable UX prototype is bounded state-flow evidence and must not become the production reducer, timing model, or geometry. |

## Decision gates

- Exact state-holder and enforcer shapes belong to the execution plan.
- Any unresolved duration or early-end product choice that contradicts accepted scope requires maintainer adjudication.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Design authority](../../DESIGN.md)
- [Interaction prototype source digest](../../docs/wiki/sources/mvp-interaction-prototype.md)
