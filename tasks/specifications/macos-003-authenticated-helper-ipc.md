# `MACOS-003`: Provide authenticated macOS helper communication

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `MACOS-001`, `FOUNDATION-001`
- **Stable concurrency constraints:** macOS helper, IPC contract, root build, signing, and packaging ownership are serialized.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

The macOS application can issue bounded enforcement commands to its helper through an authenticated, authorized, version-compatible, and recoverable native boundary.

## Context

Domain and application enforcement both require a production-owned helper channel that satisfies the accepted helper lifecycle and security contract before it receives enforcement capabilities.

## In scope

- Establish application-to-helper availability, identity, authorization, compatibility, and health outcomes.
- Accept only bounded semantic requests from the intended application peer.
- Expose truthful unavailable, unauthorized, incompatible, interrupted, and repair-required states.
- Support controlled installation, restart, upgrade compatibility, and removal evidence required by the accepted contract.

## Out of scope

- Website or application blocking behavior.
- Choosing or expanding the accepted helper lifecycle contract.
- Public distribution packaging.

## Acceptance criteria

- `AC-01` — A physically installed application and helper mutually establish the accepted peer and authorization boundary before commands are accepted.
- `AC-02` — Unauthorized, malformed, oversized, stale, or incompatible requests are denied without changing enforcement state.
- `AC-03` — Application, helper, and interruption failures surface a bounded repair or retry path without leaking sensitive data.
- `AC-04` — Restart, upgrade-compatibility, and removal checks leave no unintended active helper state.
- `AC-05` — Only semantic enforcement intent crosses the shared boundary; browsing history and unnecessary platform payloads do not.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Unit or contract tests for request validation and state transitions.
- Physical Mac peer-authentication, authorization, interruption, restart, and removal matrix.
- Security, privacy, signing, dependency, and provenance review.
- Aggregate quality gate and independent review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates malformed, oversized, stale, incompatible, and interrupted helper requests and responses. |
| Authentication or authorization | yes | Implements intended-peer authentication and per-operation authorization before helper commands change state. |
| Secrets, signing, or credentials | yes | Reviews signing and sensitive-value handling while keeping credentials and private artifacts out of Git and diagnostics. |
| Personal data or diagnostics | yes | Allows only semantic enforcement intent and privacy-safe errors across IPC. |
| Storage or migration | yes | Owns version, replay, lifecycle, restart, and cleanup state required by the accepted helper contract. |
| Cryptography | no | The macOS helper IPC implementation outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Implements the accepted helper process and IPC lifecycle on the physical Mac. |
| External services | no | The macOS helper IPC implementation outcome does not integrate with an external runtime service or own physical service evidence. |
| Dependencies or licenses | yes | Any helper, IPC, packaging, or native binding dependency needs compatibility, provenance, maintenance, and license review. |
| PoC reuse or external provenance | yes | Feasibility helper code may inform tests or contracts but requires selective re-ownership. |

## Decision gates

- `MACOS-001` must be accepted and required signing or privilege mechanisms must be available.
- Exact IPC and helper technologies belong to the reviewed execution plan.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Feasibility results and limits](../../docs/wiki/topics/feasibility-results-and-limits.md)
