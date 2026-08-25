# `IOS-001`: Enforce local sessions on iOS

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SESSION-001`, `TARGETS-004`, `APPLE-001`
- **Stable concurrency constraints:** iOS app, entitlement, Managed Settings ownership, App Group state, and Xcode project are serialized against other iOS enforcement work.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

During a valid local session, iOS applies and clears only Posato-owned website-domain and application restrictions using the accepted system presentation.

## Context

The shared session and local opaque mapping need a physical iOS enforcement adapter before end-to-end local session behavior can be accepted.

## In scope

- Apply selected exact-domain and local opaque application restrictions for a committed session.
- Use the accepted default system shield and truthful local status.
- Handle authorization denial or revocation, unsupported selections, restart, and cleanup safely.
- Persist only the minimum extension-visible state needed for owned enforcement lifecycle.

## Out of scope

- Normal-expiry cleanup while the application is suspended.
- Custom shield branding or cross-device synchronization.
- Interpreting, exporting, logging, or synchronizing opaque selections.

## Acceptance criteria

- `AC-01` — On a physical iPhone, a valid committed session applies the selected domain and application restrictions and leaves unselected controls unaffected.
- `AC-02` — Early end, authorization loss, invalid mapping, application restart, and recovery clear or preserve only the state required by the accepted session contract without false protection claims.
- `AC-03` — Only Posato-owned restrictions are changed; unrelated system or third-party controls remain untouched.
- `AC-04` — Opaque selections, credentials, and browsing activity do not enter shared state, synchronization, diagnostics, or tracked evidence.
- `AC-05` — Default system presentation and application status pass applicable accessibility and truthful-state review.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- iOS adapter and ownership contract tests where deterministic.
- Physical iPhone authorization, shield, control-target, restart, revocation, early-end, and cleanup matrix.
- Entitlement, App Group, privacy, security, and secret review.
- Aggregate quality gate and independent review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates session, exact-domain, opaque-selection, authorization, restart, and partial-enforcement outcomes. |
| Authentication or authorization | yes | Owns Family Controls authorization and applies restrictions only for a valid committed local session. |
| Secrets, signing, or credentials | yes | Signing, profiles, entitlements, and App Group configuration require review without entering tracked secrets. |
| Personal data or diagnostics | yes | Opaque selections and browsing activity remain outside shared state, synchronization, diagnostics, and evidence. |
| Storage or migration | yes | Owns minimum app and App Group enforcement state needed for apply, early end, restart, and extension reconciliation. |
| Cryptography | no | The iOS enforcement slice outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Implements Managed Settings, Family Controls, App Group, entitlement, and application lifecycle ownership. |
| External services | yes | Authorization, entitlement, shields, and lifecycle behavior require physical iPhone evidence. |
| Dependencies or licenses | no | The iOS enforcement slice outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | Feasibility enforcement observations require fresh production ownership and physical verification. |

## Decision gates

- Required Family Controls and Managed Settings entitlement/profile support must be available.
- The App Group ownership contract must be frozen before extension work in `IOS-002`.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [Design authority](../../DESIGN.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [iOS enforcement topic](../../docs/wiki/topics/ios-enforcement.md)
