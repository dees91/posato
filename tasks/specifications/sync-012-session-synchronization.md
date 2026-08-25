# `SYNC-012`: Synchronize active-session intent

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SYNC-011`, `SESSION-002`
- **Stable concurrency constraints:** Shared session model, replica, synchronization operations, UI status, and both composition roots are serialized.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

Active-session start intent, intentional early termination, and normal expiry converge between the Mac and iPhone without a delivery-time promise or unsafe stale enforcement.

## Context

After policy convergence and complete local enforcement, the final synchronized behavior is a single bounded session whose intent can originate on either installation.

## In scope

- Create and apply immutable session start, intentional stop, and normal-expiry operations.
- Validate effective local target availability before applying remote session intent.
- Handle offline, delayed, duplicate, reordered, stale, conflicting, restarted, partially enforceable, and retry outcomes.
- Present local enforcement and remote synchronization status truthfully.

## Out of scope

- Guaranteed real-time delivery, remote command/control promises, or server infrastructure.
- Schedules, overlapping sessions, stronger early-end friction, or total-key-loss recovery.
- Changing local enforcement contracts.

## Acceptance criteria

- `AC-01` — A valid session started on either physical device converges to the other and enforces every locally available selected target without false coverage claims.
- `AC-02` — Intentional early termination and normal expiry converge idempotently and cannot clear a newer session or unrelated controls.
- `AC-03` — Offline, delayed, duplicate, reordered, stale, conflicting, partially enforceable, restarted, and retried events preserve safe local enforcement and accepted last-valid state.
- `AC-04` — The UI clearly separates committed local enforcement from pending or failed remote convergence and never promises a delivery time.
- `AC-05` — The controlled cross-device start, early-end, expiry, control-target, retry, and cleanup matrix passes without manual state repair.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Session operation, ordering, idempotency, replica, and UI status tests.
- Physical Mac-and-iPhone cross-origin/offline/delay/restart/partial/retry/early-end/expiry matrix.
- Security, privacy, storage, bounds, accessibility, and performance review.
- Aggregate quality gate and independent review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates start, stop, expiry, stale, conflicting, duplicate, reordered, delayed, partial, restarted, and retried session operations. |
| Authentication or authorization | yes | Applies only authenticated session intent and validates effective local enforcement authority before commitment. |
| Secrets, signing, or credentials | yes | Keeps keys, credentials, opaque selections, and protected session payloads outside metadata and diagnostics. |
| Personal data or diagnostics | yes | Synchronizes bounded session intent without browsing or application-usage history. |
| Storage or migration | yes | Owns replicated session ordering, idempotency, restart, pending status, last-valid-state, and stale-session resolution. |
| Cryptography | yes | Uses the accepted signed encrypted-operation contract for start, intentional stop, and expiry operations. |
| Native IPC or entitlements | yes | Reconciles remote intent with both frozen local enforcement lifecycles without changing their platform contracts. |
| External services | yes | Cross-device offline, delay, retry, early-end, expiry, and cleanup behavior require the controlled physical pair. |
| Dependencies or licenses | no | The session-synchronization slice outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | Feasibility operation and lifecycle evidence is bounded and cannot replace fresh session-convergence ownership and physical verification. |

## Decision gates

- Local session integration and policy synchronization must be done.
- Any unresolved stale-session or partial-enforcement semantic requires maintainer acceptance before implementation.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Design authority](../../DESIGN.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
