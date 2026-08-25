# `SYNC-011`: Synchronize target policy intent

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `ONBOARDING-002`, `SYNC-010`, `TARGETS-001`, `TARGETS-002`
- **Stable concurrency constraints:** Shared target model, replica, synchronization operations, UI status, and both composition roots are serialized.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

Exact domains and semantic application policies converge between the Mac and iPhone while opaque local selections stay local and synchronization status remains truthful.

## Context

The MVP's shared policy consists of exact domains and semantic application intent. Enforcement identity and authorization remain local to each installation.

## In scope

- Create and apply policy operations for domain and semantic application-policy changes.
- Handle bidirectional edits, duplicates, conflicts, retries, offline work, restart, and account state through the accepted coordinator.
- Keep per-device mapping availability and repair status local while presenting shared-policy convergence truthfully.
- Verify controlled physical convergence and failure recovery.

## Out of scope

- Synchronizing opaque application tokens or treating labels as enforcement identity.
- Session synchronization.
- Delivery-time guarantees, schedules, or portable workspaces.

## Acceptance criteria

- `AC-01` — A valid exact-domain or semantic application-policy change made on either physical device converges to the other through the accepted coordinator.
- `AC-02` — Opaque iOS selections, macOS native identities, permissions, and local mapping state never enter synchronized operations or remote diagnostics.
- `AC-03` — Concurrent, duplicate, reordered, stale, offline, restarted, and retried edits resolve according to the accepted contract without losing the last valid policy.
- `AC-04` — Each installation distinguishes shared-policy currency from local enforceability and offers mapping repair when needed.
- `AC-05` — The physical bidirectional matrix passes without manual data repair and without claiming a delivery deadline.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Policy operation, conflict, replica, and Compose status tests.
- Physical Mac-and-iPhone bidirectional/offline/conflict/restart/retry/local-mapping matrix.
- Privacy, security, storage, bounds, and accessibility review.
- Aggregate quality gate and independent review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates policy operations under duplicate, reordered, stale, conflicting, offline, restarted, and retried delivery. |
| Authentication or authorization | yes | Applies only authenticated operations for the active workspace and accepted author context. |
| Secrets, signing, or credentials | yes | Keeps credentials, keys, and platform-native mapping identities outside operations and diagnostics. |
| Personal data or diagnostics | yes | Synchronizes only exact domains and semantic policy; opaque selections and local mapping state remain local. |
| Storage or migration | yes | Owns replicated policy application, conflict, retry, restart, pending status, and last-valid-state behavior. |
| Cryptography | yes | Uses the accepted signed encrypted-operation contract for every policy change. |
| Native IPC or entitlements | no | The policy-synchronization slice outcome does not change native IPC, entitlement, signing, or platform lifecycle ownership. |
| External services | yes | Bidirectional CloudKit convergence and offline or retry behavior require the physical device pair without a delivery SLA. |
| Dependencies or licenses | no | The policy-synchronization slice outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | Feasibility operation and merge evidence is bounded and cannot replace fresh policy-convergence ownership and tests. |

## Decision gates

- The accepted conflict contract must cover every policy edit represented by the task.
- Local mapping and synchronization status vocabulary must remain consistent with the accepted design.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Design authority](../../DESIGN.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
