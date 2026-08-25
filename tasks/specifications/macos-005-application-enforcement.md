# `MACOS-005`: Enforce mapped applications on macOS

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `MACOS-003`, `SESSION-001`, `TARGETS-003`
- **Stable concurrency constraints:** macOS helper and application platform-leaf ownership must be isolated from other macOS enforcement work.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

During a valid local session, locally mapped macOS applications are restricted safely without affecting unselected applications or relying on synchronized platform identity.

## Context

Semantic application policy becomes enforceable on a Mac only after the installation owns a valid local mapping and the helper accepts bounded session commands.

## In scope

- Apply and remove restrictions for the effective local application mappings in the session.
- Detect invalidated, missing, unavailable, or changed local identities before and during enforcement.
- Expose truthful action-required, partial-failure, repair, and cleanup behavior.
- Preserve unrelated applications and controls.

## Out of scope

- Website blocking.
- iOS application enforcement or synchronization of local identities.
- Schedules, overlapping sessions, or stronger early-end friction.

## Acceptance criteria

- `AC-01` — Mapped selected applications are restricted during the active session on the physical Mac, while unselected control applications remain unaffected.
- `AC-02` — Missing, invalidated, ambiguous, or unavailable mappings prevent false coverage and expose the accepted local repair action.
- `AC-03` — Early end, normal expiry, application or helper restart, and uninstall or repair remove only Posato-owned application enforcement.
- `AC-04` — No native application identity is synchronized, exported, or logged beyond the accepted privacy-safe local boundary.
- `AC-05` — Failure and cleanup evidence covers partial application sets without leaving unintended restrictions.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Policy translation, identity-validation, and cleanup tests.
- Physical Mac selected/control application, invalidation, restart, failure, expiry, and cleanup matrix.
- Privacy, authorization, performance, and provenance review.
- Aggregate quality gate and independent review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates missing, moved, ambiguous, invalidated, unavailable, and partial native application mappings and events. |
| Authentication or authorization | yes | Uses only authenticated helper authority and accepted local mappings to restrict applications. |
| Secrets, signing, or credentials | no | The macOS application-enforcement slice outcome does not handle secrets, signing material, provisioning assets, or credentials. |
| Personal data or diagnostics | yes | Keeps native identities and application events within the minimum local enforcement boundary without usage history. |
| Storage or migration | yes | Owns Posato application-control state needed for restart, expiry, early end, repair, and exact cleanup. |
| Cryptography | no | The macOS application-enforcement slice outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Implements helper-backed macOS application control under the accepted identity safety contract. |
| External services | yes | Selected and unselected application behavior requires a physical Mac control matrix. |
| Dependencies or licenses | yes | The technical plan must review any first application-observation or control dependency and license. |
| PoC reuse or external provenance | yes | Feasibility application-control evidence remains bounded and needs fresh production verification. |

## Decision gates

- The accepted helper lifecycle contract must permit safe application enforcement.
- The native identity and control mechanism selected in the execution plan must pass security and physical-device review.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [macOS enforcement topic](../../docs/wiki/topics/macos-enforcement.md)
