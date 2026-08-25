# `MACOS-001`: Accept the macOS helper lifecycle and security contract

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SECURITY-001`
- **Stable concurrency constraints:** Durable architecture, security, and wiki authority remains serialized with other decision tasks.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

The maintainer has accepted the ownership, lifecycle, privilege, authentication, authorization, update, recovery, and removal contract for the macOS helper boundary.

## Context

The accepted architecture requires a separate macOS helper and IPC boundary but intentionally leaves its exact native ownership and lifecycle as a decision.

## In scope

- Assign responsibilities between the Compose application, native bridge, and helper.
- Define installation, upgrade, launch, peer identity, authorization, failure, repair, and uninstall outcomes.
- Define the minimum privileged surface and evidence required before enforcement consumes it.
- Record distribution and signing assumptions as explicit gates rather than readiness claims.

## Out of scope

- Implementing the helper or IPC transport.
- Choosing browser/proxy support behavior.
- Public distribution approval or packaging.

## Acceptance criteria

- `AC-01` — One accepted ADR defines helper ownership and the complete lifecycle from installation through removal.
- `AC-02` — The contract requires authenticated peers, least privilege, bounded requests, fail-safe behavior, and recovery from version or availability mismatch.
- `AC-03` — No browsing history, opaque app-selection payload, credential, or unnecessary user data crosses the boundary.
- `AC-04` — Every unresolved signing, privilege, distribution, or coexistence assumption has a clearing condition and downstream owner.
- `AC-05` — The maintainer accepts the contract before `MACOS-003` starts.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Architecture and security decision review.
- Lifecycle and failure-state completeness matrix.
- Roadmap dependency and consumer validation.
- Independent platform-security review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Defines bounded validation and rejection requirements for every future helper request and lifecycle message. |
| Authentication or authorization | yes | Owns peer identity, per-operation authorization, least privilege, denial, and repair authority. |
| Secrets, signing, or credentials | yes | Defines signing and sensitive-value boundaries without recording credentials or implementation secrets. |
| Personal data or diagnostics | yes | Restricts helper and IPC data to semantic intent and forbids browsing or application-usage history. |
| Storage or migration | yes | Defines ownership, compatibility, recovery, and cleanup expectations for helper lifecycle and Posato-owned mutations. |
| Cryptography | no | The macOS helper lifecycle decision outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | This task is the durable authority for helper process, IPC, privilege, installation, update, failure, repair, and removal behavior. |
| External services | no | The macOS helper lifecycle decision outcome does not integrate with an external runtime service or own physical service evidence. |
| Dependencies or licenses | no | The macOS helper lifecycle decision outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | Any feasibility-helper evidence must remain bounded and cannot establish production ownership. |

## Decision gates

- Maintainer acceptance of helper ownership, privilege, lifecycle, and recovery is required.
- Unavailable signing or privilege mechanisms block the implementing consumer rather than being silently replaced.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Feasibility results and limits](../../docs/wiki/topics/feasibility-results-and-limits.md)
