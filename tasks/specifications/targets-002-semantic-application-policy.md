# `TARGETS-002`: Manage semantic application policies

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `MODEL-001`
- **Stable concurrency constraints:** Shared model and shared UI ownership must be isolated from other active tasks in the wave.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** medium

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

A person can create and manage a semantic application policy while each installation truthfully shows whether that policy has a usable device-local mapping.

## Context

Application identity differs by platform and opaque selections must remain local. The shared product model therefore synchronizes semantic intent, not native tokens.

## In scope

- Create, edit, remove, and review semantic application policies.
- Represent mapped, unmapped, unavailable, and action-required local states without exposing platform tokens.
- Persist semantic policy and local mapping status through their accepted ownership boundaries.
- Meet the applicable design and accessibility baseline on both applications.

## Out of scope

- Selecting native applications on macOS or iOS.
- Synchronizing opaque platform tokens or treating labels as enforcement identity.
- Application enforcement or policy synchronization.

## Acceptance criteria

- `AC-01` — A semantic application policy can be created, edited, removed, and restored after restart on both application targets.
- `AC-02` — Each installation presents mapped, unmapped, unavailable, and action-required states truthfully and offers the relevant local next action.
- `AC-03` — Shared policy data contains no opaque platform token, credential, or platform-specific enforcement identity.
- `AC-04` — The UI remains usable with long labels, text scaling, keyboard or switch access, reduced motion, and non-color state cues where applicable.
- `AC-05` — Invalid changes preserve the last valid policy and expose a recoverable explanation.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Shared model and persistence tests.
- Compose UI behavior tests.
- macOS runtime and iOS Simulator visual/accessibility checks.
- Privacy boundary inspection.
- Aggregate local quality gate.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates policy edits, labels, duplicates, and unavailable mapping state without treating labels as native identity. |
| Authentication or authorization | no | The semantic application-policy UI slice outcome does not change identity, admission, permission, or authorization decisions. |
| Secrets, signing, or credentials | no | The semantic application-policy UI slice outcome does not handle secrets, signing material, provisioning assets, or credentials. |
| Personal data or diagnostics | yes | Keeps opaque platform selections, credentials, and native enforcement identity outside shared policy and diagnostics. |
| Storage or migration | yes | Owns persistence and restart behavior for semantic policy plus local mapping status. |
| Cryptography | no | The semantic application-policy UI slice outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Defines the semantic mapping seam and truthful local states; platform API ownership stays with `TARGETS-003` and `TARGETS-004`. |
| External services | no | The semantic application-policy UI slice outcome does not integrate with an external runtime service or own physical service evidence. |
| Dependencies or licenses | no | The semantic application-policy UI slice outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | The disposable UX prototype is bounded flow evidence and must not define production selection or mapping behavior. |

## Decision gates

- The semantic policy and local-mapping boundary must remain compatible with the accepted architecture; platform APIs are deferred to leaf tasks.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Design authority](../../DESIGN.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
- [Interaction prototype source digest](../../docs/wiki/sources/mvp-interaction-prototype.md)
