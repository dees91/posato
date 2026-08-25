# `MACOS-004`: Enforce exact domains on macOS

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `MACOS-002`, `MACOS-003`, `SESSION-001`, `TARGETS-001`
- **Stable concurrency constraints:** macOS helper and application platform-leaf ownership must be isolated from other macOS enforcement work.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

During a valid local session, selected exact domains are denied on the accepted macOS browser matrix with recoverable proxy state and privacy-safe presentation.

## Context

This task is the first website-enforcement consumer of the accepted browser-support contract, helper channel, target list, and shared session boundary.

## In scope

- Apply and remove the current session's exact-domain policy on supported browser traffic.
- Present the accepted denial experience without recording navigation history.
- Handle helper, proxy, browser, restart, coexistence, and cleanup failures truthfully.
- Preserve unrelated system or user controls and last valid product state.

## Out of scope

- Application blocking on macOS.
- Unsupported browsers or traffic outside the accepted contract.
- Cross-device session synchronization or URL analytics.

## Acceptance criteria

- `AC-01` — Selected exact domains are denied for the active session across every browser and traffic case in the accepted physical Mac matrix, while unselected control domains remain reachable.
- `AC-02` — Early end, normal expiry, application restart, helper restart, and uninstall or repair paths remove only Posato-owned website enforcement.
- `AC-03` — Helper, proxy, browser, or coexistence failure cannot produce a false protected status and exposes the accepted recovery action.
- `AC-04` — Denied and allowed traffic is not recorded as browsing history or emitted in diagnostics.
- `AC-05` — The denial presentation and application status satisfy the applicable design, accessibility, and privacy baseline.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Unit and contract tests for policy translation and cleanup.
- Physical Mac supported-browser, control-domain, restart, coexistence, failure, and cleanup matrix.
- Visual/accessibility and privacy inspection.
- Security, performance, static analysis, and independent review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates exact-domain policy plus untrusted browser, network, helper, proxy, and restart outcomes. |
| Authentication or authorization | yes | Uses only the authenticated and authorized helper channel to change Posato-owned proxy state. |
| Secrets, signing, or credentials | no | The macOS domain-enforcement slice outcome does not handle secrets, signing material, provisioning assets, or credentials. |
| Personal data or diagnostics | yes | Must deny selected domains without retaining denied or allowed navigation history. |
| Storage or migration | yes | Owns recoverable snapshot, restore, restart, coexistence, and cleanup state for Posato-owned website enforcement. |
| Cryptography | no | The macOS domain-enforcement slice outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Implements helper-backed system proxy and browser enforcement under the accepted support contract. |
| External services | yes | Supported browsers, networks, proxy coexistence, VPN, and control-domain behavior require the physical Mac matrix. |
| Dependencies or licenses | yes | The technical plan must review any first native, proxy, certificate, or browser-support dependency and license. |
| PoC reuse or external provenance | yes | Feasibility proxy behavior is bounded evidence and cannot be copied wholesale. |

## Decision gates

- `MACOS-002` support contract and `MACOS-001` helper contract must be accepted.
- Required helper, proxy, signing, and coexistence behavior must be available on the physical Mac.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [Design authority](../../DESIGN.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [macOS enforcement topic](../../docs/wiki/topics/macos-enforcement.md)
