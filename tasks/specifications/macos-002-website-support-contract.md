# `MACOS-002`: Accept the macOS website-support contract

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SECURITY-001`
- **Stable concurrency constraints:** Durable product, security, and wiki authority remains serialized with other decision tasks.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

The maintainer has accepted the MVP macOS browser-support, exact-domain enforcement, coexistence, privacy, failure, and recovery contract.

## Context

The MVP promises exact-domain blocking on macOS, while supported browsers, proxy coexistence, local-network behavior, unsupported cases, and failure presentation require a truthful product contract before implementation.

## In scope

- Define the supported browser and traffic boundary for MVP acceptance.
- Define exact-domain behavior, exclusions, coexistence expectations, and unsupported cases.
- Define privacy-safe denial, failure, repair, and cleanup outcomes.
- Map physical verification and residual risk to the implementing task.

## Out of scope

- Implementing the helper, proxy, browser interception, or denial UI.
- Capturing browsing history or allowed navigation events.
- Expanding to schedules, portable workspaces, Android, or Linux.

## Acceptance criteria

- `AC-01` — The accepted product contract names the browser and traffic matrix on which MVP claims may be made.
- `AC-02` — Exact-domain semantics, unsupported cases, local-network considerations, and coexistence limits are explicit.
- `AC-03` — Failure and repair behavior does not silently disable protection or claim coverage that is not observed.
- `AC-04` — The privacy boundary forbids browsing-history and allowed-navigation diagnostics.
- `AC-05` — The maintainer accepts the contract before macOS domain enforcement starts.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Product, security, and privacy decision review.
- Supported-browser and coexistence test design.
- Roadmap dependency and physical-evidence validation.
- Independent completed-change review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Defines exact-domain and traffic cases, including malformed, unsupported, local-network, and coexistence outcomes. |
| Authentication or authorization | no | The macOS website-support decision outcome does not change identity, admission, permission, or authorization decisions. |
| Secrets, signing, or credentials | no | The macOS website-support decision outcome does not handle secrets, signing material, provisioning assets, or credentials. |
| Personal data or diagnostics | yes | Owns the no-browsing-history boundary and privacy-safe denial, failure, and support presentation. |
| Storage or migration | no | The macOS website-support decision outcome does not change durable product storage, compatibility, or migration behavior. |
| Cryptography | no | The macOS website-support decision outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Defines the product contract consumed by native proxy and browser enforcement without selecting implementation mechanics. |
| External services | yes | Current browser, network, VPN, proxy, and captive-portal behavior requires a maintained support contract and later physical matrix. |
| Dependencies or licenses | no | The macOS website-support decision outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | Feasibility browser and proxy observations are evidence only and require fresh production verification. |

## Decision gates

- Maintainer acceptance of supported browsers and coexistence expectations is required.
- Unknown platform or distribution constraints remain visible blockers for the implementing task.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [MVP open questions](../../docs/wiki/topics/mvp-open-questions.md)
- [macOS enforcement topic](../../docs/wiki/topics/macos-enforcement.md)
