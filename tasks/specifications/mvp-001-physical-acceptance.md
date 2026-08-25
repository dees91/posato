# `MVP-001`: Accept the complete physical-device MVP outcome

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SYNC-012`
- **Stable concurrency constraints:** Cross-cutting acceptance evidence, controlled accounts, devices, and final product authorities are serialized.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

The accepted MVP flow passes once on one supported arm64 Mac and one supported iPhone without manual state repair or unsupported readiness claims.

## Context

Individual tasks establish components and focused matrices. This task is the terminal product-outcome check against the measurable MVP scope on the exact supported physical topology.

## In scope

- Run the accepted first-installation and second-installation flows on the controlled device pair.
- Verify domain and semantic application-policy convergence with device-local mappings.
- Verify cross-device session start, local enforcement, early end, normal expiry, offline/retry, action-required, and cleanup outcomes.
- Audit the complete result against product, design, architecture, security, privacy, accessibility, and quality authorities.

## Out of scope

- Public release approval or distribution readiness.
- New features, broadened platform/browser coverage, schedules, analytics, or stronger friction.
- Manual data, Keychain, CloudKit, helper, or device-state repair during the measured pass.

## Acceptance criteria

- `AC-01` — The exact measurable rows in the accepted MVP scope pass on the controlled Mac-and-iPhone topology with recorded versions and synthetic fixtures.
- `AC-02` — First and second installation use one Apple workspace, including the delayed-key waiting invariant, without Posato pairing or a parallel workspace.
- `AC-03` — Exact domains and semantic application policy converge while opaque mappings remain local, and one session blocks selected targets on both platforms.
- `AC-04` — Intentional early end, normal expiry, offline/retry, permission or mapping action-required, restart, control-target, and cleanup outcomes remain truthful and safe.
- `AC-05` — The measured pass requires no manual state repair and the report makes no claim about release readiness or untested coverage.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Controlled physical-device acceptance matrix and reproducible environment record.
- Fresh aggregate quality gate and applicable clean-build evidence.
- Complete visual, accessibility, security, privacy, performance, and cleanup audit.
- Independent completed-change and product-outcome review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Exercises representative valid, invalid, offline, delayed, action-required, restart, early-end, expiry, and cleanup outcomes. |
| Authentication or authorization | yes | Audits Apple membership, local permission, helper peer, and enforcement authorization claims across the measured flow. |
| Secrets, signing, or credentials | yes | Confirms credentials, workspace keys, opaque selections, profiles, and private device data remain outside tracked evidence. |
| Personal data or diagnostics | yes | Audits browsing-history omission, opaque mapping locality, diagnostics, and visible local-versus-shared state. |
| Storage or migration | yes | Exercises restart, last-valid-state, pending work, cleanup, and no-manual-repair outcomes. |
| Cryptography | yes | Confirms all measured synchronization uses the accepted signed encrypted-operation boundary without making a new primitive decision. |
| Native IPC or entitlements | yes | Exercises helper, IPC, Family Controls, Managed Settings, Device Activity, App Group, and platform lifecycle outcomes. |
| External services | yes | Uses the controlled physical Mac, iPhone, Apple account, CloudKit, Keychain, browser, and application matrix. |
| Dependencies or licenses | yes | Audits that every dependency used by the measured candidate completed task-local compatibility and license review. |
| PoC reuse or external provenance | yes | Confirms the measured implementation is production-owned and makes no claim inherited only from PoC or prototype evidence. |

## Decision gates

- All dependency tasks must be done with fresh evidence.
- The maintainer must accept the measured MVP outcome; release readiness remains a separate task.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Design authority](../../DESIGN.md)
- [First MVP PR preparation checklist](../first-mvp-pr-preparation-todo.md)
