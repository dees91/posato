# `TARGETS-001`: Manage exact website domains

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `MODEL-001`
- **Stable concurrency constraints:** Root build, first UI dependency,
  state-holder ownership, shared model, and shared UI changes must be isolated
  from other active tasks in the wave.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** medium

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

A person can add, edit, remove, and review exact website domains locally in both applications with truthful validation and persistence.

## Context

Exact domains are a shared MVP target type and are required before local sessions and policy synchronization can be meaningful.

## In scope

- Provide the accepted domain-list empty, populated, invalid, and recoverable states.
- Validate and present exact-domain entries consistently across both application targets.
- Persist successful changes through the local-first replica.
- Select and compatibility-check the first multi-screen and state-holder UI
  dependencies, and document state-holder and coroutine ownership for this
  slice as required by the architecture baseline.
- Meet the applicable design, accessibility, keyboard, and reduced-motion baseline.

## Out of scope

- URL browsing history, allow-list behavior, path-level rules, schedules, or wildcard policy.
- Native blocking or synchronization.
- Semantic application policies and device-local application selection.

## Acceptance criteria

- `AC-01` — A valid exact domain can be added, edited, removed, and restored after restart on macOS and iOS.
- `AC-02` — Invalid, duplicate, or unsupported input is rejected without
  corrupting the last valid list, creating browsing history, or recording
  an allowed-navigation event, and it has an actionable explanation.
- `AC-03` — The UI distinguishes empty, populated, editing, and recoverable-failure states without relying on color alone.
- `AC-04` — Keyboard, focus, touch-target, screen-reader, text-scaling, and reduced-motion expectations pass where applicable.
- `AC-05` — The first UI dependency set and state-holder and coroutine
  ownership are documented, compatible across both application targets,
  license-reviewed, and do not introduce competing navigation or state-holder
  frameworks.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Validation and persistence tests.
- Compose UI behavior tests.
- macOS runtime and iOS Simulator visual/accessibility checks.
- First UI dependency compatibility, maintenance, provenance, and license
  review.
- Formatting, static analysis, compiler warnings, and aggregate gate.
- Independent completed-change review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates user-entered exact domains, duplicates, edits, and unsupported values without corrupting the accepted list. |
| Authentication or authorization | no | The first stateful target-management UI slice outcome does not change identity, admission, permission, or authorization decisions. |
| Secrets, signing, or credentials | no | The first stateful target-management UI slice outcome does not handle secrets, signing material, provisioning assets, or credentials. |
| Personal data or diagnostics | yes | Stores configured policy only and must not create browsing-history or allowed-navigation diagnostics. |
| Storage or migration | yes | Consumes the first local replica through observable create, edit, remove, restart, and failure outcomes. |
| Cryptography | no | The first stateful target-management UI slice outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | no | The first stateful target-management UI slice outcome does not change native IPC, entitlement, signing, or platform lifecycle ownership. |
| External services | no | The first stateful target-management UI slice outcome does not integrate with an external runtime service or own physical service evidence. |
| Dependencies or licenses | yes | Owns compatibility and license review for the first multi-screen and state-holder UI dependencies required by ADR 0003. |
| PoC reuse or external provenance | yes | The disposable UX prototype is bounded flow evidence and must not define production domain validation, persistence, or interaction behavior. |

## Decision gates

- Exact-domain product semantics must remain consistent with the accepted
  scope and `MACOS-002`; implementation mechanics are chosen in the task plan.
- The reviewed execution plan must resolve the ADR 0003 Navigation 3,
  AndroidX Multiplatform ViewModel, state-holder, and coroutine-ownership
  candidates before implementation.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Design authority](../../DESIGN.md)
- [Interaction prototype source digest](../../docs/wiki/sources/mvp-interaction-prototype.md)
