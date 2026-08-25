# `SECURITY-001`: Accept the MVP threat model

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** none
- **Stable concurrency constraints:** Durable security and wiki authority has one writer; this task starts only after the PR #1 merge checkpoint.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

The maintainer has accepted an MVP threat model that names protected assets, trust boundaries, attackers, abuse cases, mitigations, residual risks, and task owners.

## Context

The product handles enforcement authority, opaque application selections, synchronization keys, signed operations, Apple services, and a privileged macOS boundary. Later implementation needs one security authority before consuming those risks.

## In scope

- Classify MVP data and security assets.
- Describe trust boundaries across shared code, applications, extensions, helper processes, storage, Apple services, and local users.
- Prioritize realistic threats and map required mitigations and verification to roadmap tasks.
- Record residual risk and explicit non-goals without claiming production readiness.

## Out of scope

- Choosing exact cryptographic primitives or transport encodings.
- Implementing controls, platform services, or diagnostics.
- The separate pre-release security and disclosure review.

## Acceptance criteria

- `AC-01` — The accepted threat model covers confidentiality, integrity, availability, authorization, replay, rollback, tampering, denial, local privilege, account change, and data-remanence risks relevant to the MVP.
- `AC-02` — Every required mitigation has an owning roadmap task before its first implementation consumer.
- `AC-03` — Opaque app selections, browsing-history omission, workspace keys, signing keys, helper authority, and Apple account boundaries are explicitly classified.
- `AC-04` — Residual risks and out-of-scope recovery behavior are visible and do not overstate security or readiness.
- `AC-05` — The maintainer records acceptance of the durable security authority.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Security-document completeness and cross-reference review.
- Roadmap owner and consumer-order validation.
- Privacy-boundary consistency review.
- Independent security-focused completed-change review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Classifies malformed, stale, replayed, conflicting, and oversized inputs across every MVP trust boundary. |
| Authentication or authorization | yes | Defines identity, permission, admission, peer-authentication, and least-privilege threats and required owners. |
| Secrets, signing, or credentials | yes | Classifies workspace keys, signing keys, credentials, profiles, and other sensitive assets. |
| Personal data or diagnostics | yes | Classifies policy, opaque selections, diagnostics, browsing-history exclusions, and metadata exposure. |
| Storage or migration | yes | Covers local replica, helper state, App Group state, Keychain, CloudKit, deletion, and remanence threats. |
| Cryptography | yes | Defines the threats and residual risks that the later cryptographic contract must mitigate. |
| Native IPC or entitlements | yes | Covers the macOS helper, IPC, iOS extensions, entitlements, and native lifecycle boundaries. |
| External services | yes | Covers Apple account, Keychain, CloudKit, browser, network, and distribution trust assumptions at the decision level. |
| Dependencies or licenses | yes | Defines security and provenance obligations for later dependency consumers without adding a dependency itself. |
| PoC reuse or external provenance | yes | Separates observed feasibility evidence from production claims and assigns fresh validation to owning tasks. |

## Decision gates

- Maintainer acceptance of the threat priorities, residual risks, and MVP boundary is required before the task is done.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
