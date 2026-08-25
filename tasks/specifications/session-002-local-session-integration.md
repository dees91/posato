# `SESSION-002`: Complete the local session on both Apple platforms

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `MACOS-004`, `MACOS-005`, `IOS-002`, `SESSION-001`
- **Stable concurrency constraints:** Shared UI and both platform composition roots are serialized for this integration task.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

One local manual session starts, enforces selected targets, ends early, and expires safely on each supported Apple platform with truthful failure and recovery behavior.

## Context

Shared session behavior and platform enforcement are independently testable, but their complete local lifecycle must be integrated before synchronization can carry session intent.

## In scope

- Connect shared session behavior to the accepted macOS and iOS enforcement outcomes.
- Reconcile application, helper, extension, restart, authorization, mapping, and cleanup state into one truthful local status.
- Complete the primary setup, review, active, early-end, expiry, action-required, and retry UX.
- Verify the controlled physical local-session matrix on both platforms.

## Out of scope

- Cross-device synchronization or onboarding.
- Schedules, overlapping sessions, stronger early-end friction, or custom iOS shields.
- Expanding accepted platform coverage.

## Acceptance criteria

- `AC-01` — On the physical Mac and iPhone, one valid session starts and restricts every selected locally enforceable target while unselected controls remain unaffected.
- `AC-02` — Intentional early end and normal expiry remove only Posato-owned controls and leave consistent persisted state on both platforms.
- `AC-03` — Application, helper, extension, authorization, mapping, partial-enforcement, and restart failures never claim false protection and expose bounded recovery.
- `AC-04` — The complete local flow satisfies the accepted visual, keyboard/touch, screen-reader, text-scaling, focus, reduced-motion, and state-communication baseline.
- `AC-05` — The local session lifecycle completes without manual state repair and without any remote-delivery claim.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Cross-layer integration and Compose UI tests.
- Physical Mac and iPhone happy, early-end, expiry, restart, action-required, retry, control-target, and cleanup matrix.
- Visual and accessibility review on both platforms.
- Privacy, security, performance, static analysis, and independent review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Reconciles untrusted helper, extension, authorization, mapping, restart, partial-enforcement, and UI outcomes. |
| Authentication or authorization | yes | Must never commit or claim protection without accepted local platform authority and effective target validation. |
| Secrets, signing, or credentials | no | The cross-platform local-session integration outcome does not handle secrets, signing material, provisioning assets, or credentials. |
| Personal data or diagnostics | yes | Integrates platform status without adding browsing, application-usage, opaque-token, or sensitive diagnostic history. |
| Storage or migration | yes | Reconciles shared, helper, App Group, mapping, restart, expiry, and cleanup state into one truthful lifecycle. |
| Cryptography | no | The cross-platform local-session integration outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Joins both frozen native enforcement contracts through their semantic application boundaries. |
| External services | yes | Browser, application, permission, extension, and lifecycle behavior require the controlled physical Mac-and-iPhone matrix. |
| Dependencies or licenses | no | The cross-platform local-session integration outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | The disposable UX prototype and feasibility platform results are bounded evidence; integration requires fresh production ownership and physical verification. |

## Decision gates

- All platform enforcement dependencies must pass their own physical evidence.
- Any integration change to an accepted product or platform contract requires maintainer acceptance before implementation continues.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Design authority](../../DESIGN.md)
- [Interaction prototype source digest](../../docs/wiki/sources/mvp-interaction-prototype.md)
