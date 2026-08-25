# `QUALITY-001`: Enforce the local engineering quality gate

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `FOUNDATION-001`
- **Stable concurrency constraints:** Root build and dependency-catalog ownership remain on the serialized PR #1 branch.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** medium

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

Contributors have one local aggregate verification gate that enforces the accepted formatting, Kotlin analysis, Compose analysis, warning, test, and reporting baseline.

## Context

The accepted quality contract names ktlint, Detekt, Compose Rules, warning discipline, test layers, and one aggregate local command as standing requirements.

## In scope

- Provide repository-wide formatting checks and a safe formatter.
- Run Kotlin static analysis including the accepted Compose-specific rules.
- Make compiler-warning and applicable test failures visible through one aggregate local gate.
- Document supported local use and actionable failure output.

## Out of scope

- CI orchestration, signing-dependent verification, or device labs.
- Weakening or replacing the accepted engineering quality contract.
- Feature-specific tests belonging to later tasks.

## Acceptance criteria

- `AC-01` — One documented local command runs every credential-free check required by the accepted PR #1 quality boundary.
- `AC-02` — A clean PR #1 skeleton passes formatting, ktlint, Detekt with Compose Rules, compiler-warning, and applicable test checks.
- `AC-03` — Purposefully introduced representative violations make the aggregate gate fail with actionable output.
- `AC-04` — Formatting and analysis scopes cover all production Kotlin and Compose source sets created by the skeleton.
- `AC-05` — Dependency and license review is recorded for every added quality tool.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Aggregate local-gate pass and controlled failure probes.
- Formatting, ktlint, Detekt, Compose Rules, compiler warnings, and test reports.
- Dependency and license review.
- Documentation link and clean-checkout verification.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | no | The local quality-gate configuration outcome does not accept runtime, persisted, remote, or user-controlled input. |
| Authentication or authorization | no | The local quality-gate configuration outcome does not change identity, admission, permission, or authorization decisions. |
| Secrets, signing, or credentials | no | The local quality-gate configuration outcome does not handle secrets, signing material, provisioning assets, or credentials. |
| Personal data or diagnostics | no | The local quality-gate configuration outcome does not change personal-data collection, diagnostics, retention, or export behavior. |
| Storage or migration | no | The local quality-gate configuration outcome does not change durable product storage, compatibility, or migration behavior. |
| Cryptography | no | The local quality-gate configuration outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | no | The local quality-gate configuration outcome does not change native IPC, entitlement, signing, or platform lifecycle ownership. |
| External services | no | The local quality-gate configuration outcome does not integrate with an external runtime service or own physical service evidence. |
| Dependencies or licenses | yes | Adds and compatibility-checks the formatting, Detekt, Compose Rules, and related build-tool integrations and licenses. |
| PoC reuse or external provenance | no | The local quality-gate configuration outcome does not reuse PoC code or consume external implementation evidence. |

## Decision gates

- Exact plugin versions and configuration belong to the authorized execution plan.
- A check may be excluded only when the quality contract permits a reasoned `N/A`.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Task workflow](../README.md)
