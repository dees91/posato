# `CI-001`: Run the quality gate in credential-free CI

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `FOUNDATION-001`, `QUALITY-001`
- **Stable concurrency constraints:** CI, root build, Xcode project, and PR #1 integration ownership are serialized until the holistic PR review.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** medium

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

Every proposed change runs the accepted credential-free quality boundary in CI before PR #1 can merge or later parallel work can start.

## Context

The accepted workflow requires CI to complete before PR #1 merge or the first parallel implementation wave, whichever comes first.

## In scope

- Run the aggregate local quality gate in a clean CI environment.
- Exercise the credential-free Apple build coverage available without private signing assets.
- Publish a truthful pass or fail signal and useful diagnostic artifacts that respect repository privacy rules.
- Document the boundary between mandatory CI checks and later physical or signing-dependent evidence.

## Out of scope

- Storing Apple credentials or private signing assets in CI.
- Public distribution, release automation, or physical-device automation.
- Replacing task-local final verification with CI status alone.

## Acceptance criteria

- `AC-01` — The CI workflow runs on the repository's review path and blocks integration when the aggregate gate fails.
- `AC-02` — A clean environment passes the same credential-free quality boundary documented for local use.
- `AC-03` — Available macOS and iOS Simulator build coverage runs without repository secrets or private signing material.
- `AC-04` — CI output and retained artifacts do not expose sensitive, personal, or machine-specific values.
- `AC-05` — PR #1 cannot merge and no concurrency-candidate wave can start until the holistic review includes a passing CI result.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- CI runs for passing and controlled failing changes.
- Clean-environment build and aggregate-gate logs.
- Secret, artifact-retention, and permission review.
- PR #1 holistic review evidence.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Pull-request content and workflow inputs are untrusted; review covers command injection, unsafe interpolation, and controlled failure output. |
| Authentication or authorization | yes | CI token permissions and integration authority must be least-privilege and unable to mutate unrelated repository state. |
| Secrets, signing, or credentials | yes | The workflow must not require or expose signing identities, profiles, application credentials, or private device data. |
| Personal data or diagnostics | yes | Logs, caches, and retained artifacts must exclude personal, secret, and machine-specific values. |
| Storage or migration | no | The credential-free CI workflow outcome does not change durable product storage, compatibility, or migration behavior. |
| Cryptography | no | The credential-free CI workflow outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Credential-free Apple build variants must not silently grant signing or entitlement authority. |
| External services | yes | Hosted runner and CI-provider behavior is verified through passing and controlled failing runs; no physical-device evidence belongs to this task. |
| Dependencies or licenses | yes | Third-party workflow actions, runner tooling, and build integrations require provenance, pinning, compatibility, and license review. |
| PoC reuse or external provenance | no | The credential-free CI workflow outcome does not reuse PoC code or consume external implementation evidence. |

## Decision gates

- Hosted runner availability and permissions must support the credential-free Apple checks selected in the execution plan.
- Signing-dependent checks remain explicit manual evidence until separately authorized.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Task workflow](../README.md)
- [First MVP PR preparation plan](../first-mvp-pr-preparation-plan.md)
