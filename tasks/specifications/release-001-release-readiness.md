# `RELEASE-001`: Review first-release readiness

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `MVP-001`
- **Stable concurrency constraints:** Release configuration, Apple distribution, durable security/privacy authority, and readiness evidence are serialized.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

A separate first-release review passes or explicitly blocks every licensing, history, clean-clone, CI, security, privacy, signing, distribution, support, and artifact obligation.

## Context

MVP behavior does not imply that the repository, binaries, disclosures, distribution path, or support process are ready for public use. Repository instructions require a distinct pre-release audit.

## In scope

- Review Git history, license, third-party notices, provenance, and source distribution obligations.
- Review clean-clone setup, supported environments, CI, versioning, packaging, artifacts, signing, notarization, entitlements, and distribution eligibility.
- Review security reporting, privacy disclosures, diagnostic behavior, data deletion, support, and known limitations.
- Record pass, blocked, owner, clearing condition, and evidence for every readiness category.

## Out of scope

- Adding missing product features under the guise of readiness.
- Claiming App Store, notarization, security, privacy, or production readiness without evidence.
- Changing the accepted MVP outcome.

## Acceptance criteria

- `AC-01` — Every required readiness category has an evidence-backed pass or explicit blocked verdict with owner and clearing condition.
- `AC-02` — A clean external checkout can follow documented setup and verification without private paths, hidden files, or `.research/blocker`.
- `AC-03` — All shipped dependencies, code provenance, licenses, notices, privacy behavior, security contact, and support expectations are reconciled.
- `AC-04` — Candidate artifacts, identifiers, signing, entitlements, packaging, and distribution path are verified in the environments for which a release claim is proposed.
- `AC-05` — No release is authorized by this task until the maintainer explicitly accepts the complete readiness verdict.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Repository-history, license, notice, provenance, and clean-clone audit.
- CI, build, package, artifact, signing, entitlement, notarization, and distribution evidence as applicable.
- Security, privacy, diagnostics, deletion, support, and disclosure review.
- Independent release-readiness review and maintainer verdict.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Audits shipped interfaces, installers, configuration, support paths, artifacts, and public inputs for release risk. |
| Authentication or authorization | yes | Reviews distribution, update, support, security-reporting, and platform authority boundaries. |
| Secrets, signing, or credentials | yes | Audits Git history, artifacts, signing, notarization, credentials, profiles, and secret-handling procedures. |
| Personal data or diagnostics | yes | Reviews disclosures, diagnostics, data handling, export or deletion, support data, and claims. |
| Storage or migration | yes | Reviews compatibility, migration, backup, cleanup, deletion, installation, and update behavior for release candidates. |
| Cryptography | yes | Reviews the shipped cryptographic design, providers, key handling, claims, and known limitations. |
| Native IPC or entitlements | yes | Reviews helper, extensions, entitlements, signing, packaging, notarization, installation, update, and removal. |
| External services | yes | Reviews App Store or direct distribution, Apple services, support channels, and externally observable claims. |
| Dependencies or licenses | yes | Audits every shipped dependency, transitive obligation, license, notice, maintenance risk, and artifact provenance. |
| PoC reuse or external provenance | yes | Audits source, asset, generator, PoC, and external provenance before any public release claim. |

## Decision gates

- The measurable MVP outcome must already be accepted.
- Every unresolved distribution, signing, security, privacy, license, or support obligation blocks release even if product behavior passes.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Repository instructions](../../AGENTS.md)
