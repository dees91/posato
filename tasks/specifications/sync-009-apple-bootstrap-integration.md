# `SYNC-009`: Integrate Apple workspace bootstrap

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SYNC-002`, `SYNC-004`, `SYNC-005`, `SYNC-006`, `SYNC-007`, `SYNC-008`
- **Stable concurrency constraints:** Shared bootstrap model, UI, both Apple adapter composition roots, and integration fixtures are serialized.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

Both applications integrate Keychain and CloudKit outcomes through the accepted bootstrap state machine without ever creating a parallel workspace.

## Context

The shared state machine, operation core, and four platform adapters must be joined and verified against real Apple service timing before onboarding can depend on them.

## In scope

- Connect per-platform Keychain and CloudKit adapters to the shared bootstrap state machine.
- Reconcile discovery, creation eligibility, delayed key, conflict, account change, retry, restart, and cleanup outcomes.
- Expose truthful bootstrap status suitable for later onboarding.
- Verify the one-workspace invariant in a controlled Mac-and-iPhone physical matrix.

## Out of scope

- Complete onboarding presentation.
- Policy or session synchronization.
- Portable workspaces, manual recovery codes, or total-key-loss recovery.

## Acceptance criteria

- `AC-01` — A controlled first installation creates or adopts exactly one workspace under the accepted eligibility rules, and a later installation never silently creates another.
- `AC-02` — CloudKit-first, Keychain-first, delayed-key, unavailable-service, account-change, conflict, restart, and retry scenarios preserve valid state and truthful status across the physical Mac and iPhone.
- `AC-03` — Both platforms use compatible encrypted-operation, mailbox, and workspace-key contracts without exposing protected plaintext or secrets.
- `AC-04` — Retries and cleanup are idempotent and bounded; no scenario requires manual database or key repair to resume an ordinary recoverable flow.
- `AC-05` — The integrated status distinguishes waiting, retryable, action-required, ready, and unrecoverable-within-MVP states.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Cross-layer integration and invariant tests.
- Physical Mac-and-iPhone Apple-service ordering, delay, restart, account, conflict, retry, and cleanup matrix.
- Secret, privacy, entitlement, resource-bound, and performance review.
- Aggregate quality gate and independent security review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Reconciles untrusted, stale, conflicting, delayed, corrupt, partial, duplicate, reordered, and restarted adapter outcomes. |
| Authentication or authorization | yes | Enforces accepted Apple-account membership, account isolation, and one-workspace creation eligibility across both platforms. |
| Secrets, signing, or credentials | yes | Integrates workspace-key outcomes without exposing key material through UI, logs, fixtures, errors, or transport. |
| Personal data or diagnostics | yes | Keeps account, mailbox, key, and bootstrap status bounded and redacted. |
| Storage or migration | yes | Owns integrated bootstrap persistence, retry, restart, idempotency, conflict, and cleanup behavior. |
| Cryptography | yes | Joins the encrypted-operation core to key and mailbox outcomes without changing the accepted cryptographic contract. |
| Native IPC or entitlements | yes | Composes the four native Apple adapters behind the shared bootstrap state machine. |
| External services | yes | Real Keychain and CloudKit ordering, delay, account, restart, and failure evidence is owned by the physical Mac-and-iPhone matrix. |
| Dependencies or licenses | no | The Apple bootstrap integration outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | PoC bootstrap evidence is bounded and requires fresh production integration verification. |

## Decision gates

- All Apple adapters and the shared bootstrap state machine must be done with their physical evidence.
- Apple-managed delivery delays or account restrictions may block specific matrix rows with explicit clearing conditions.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
