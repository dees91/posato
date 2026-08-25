# `SYNC-004`: Enforce the one-workspace bootstrap invariant

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SYNC-003`
- **Stable concurrency constraints:** Shared bootstrap state, persistence contract, and fake adapter ownership are serialized.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

A deterministic shared bootstrap state machine preserves the one-workspace invariant across delayed, conflicting, failed, and restarted Apple-service outcomes.

## Context

Platform adapters need a shared consumer that turns CloudKit and synchronizable Keychain outcomes into truthful first-installation, waiting, retry, and action-required state.

## In scope

- Implement every accepted bootstrap state and transition against deterministic native-adapter fakes.
- Persist enough state to resume safely after process interruption without inventing a second workspace.
- Reject impossible, stale, conflicting, or invalid adapter outcomes without replacing valid state.
- Expose truthful status suitable for later onboarding and support.

## Out of scope

- Real Keychain or CloudKit access.
- User-facing onboarding implementation.
- Changing creation eligibility or account-change policy.

## Acceptance criteria

- `AC-01` — The complete accepted transition table is covered by deterministic tests, including first discovery, safe creation eligibility, delayed key, retry, restart, conflict, and account change.
- `AC-02` — No event sequence can create a second local workspace after evidence of an existing Apple workspace.
- `AC-03` — Stale, duplicate, reordered, conflicting, invalid, and interrupted outcomes preserve the last valid state and expose the accepted next action.
- `AC-04` — State resumes safely after restart in every persistent waiting or action-required condition.
- `AC-05` — Fakes and test fixtures contain no credentials, real account identifiers, or private user data.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- State-transition, invariant, model, and property tests.
- Restart and corrupted-state tests.
- Storage, privacy, and diagnostics review.
- Aggregate quality gate and independent review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates stale, duplicate, reordered, conflicting, corrupt, and impossible semantic adapter outcomes. |
| Authentication or authorization | yes | Enforces accepted workspace-creation eligibility and account-isolation transitions without performing platform authorization. |
| Secrets, signing, or credentials | yes | Represents key availability states without storing, logging, or exposing workspace key material in shared status. |
| Personal data or diagnostics | yes | Exposes only bounded bootstrap and action-required status through shared state and fixtures. |
| Storage or migration | yes | Owns persistent bootstrap transitions, restart recovery, one-workspace invariants, and last-valid-state behavior. |
| Cryptography | no | The shared bootstrap state machine outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | no | The shared bootstrap state machine outcome does not change native IPC, entitlement, signing, or platform lifecycle ownership. |
| External services | yes | Models Keychain and CloudKit outcomes through deterministic fakes; real service access and physical evidence belong to `SYNC-005` through `SYNC-009`. |
| Dependencies or licenses | no | The shared bootstrap state machine outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | Feasibility bootstrap races are bounded evidence; the production state machine requires fresh ownership and invariant tests. |

## Decision gates

- The `SYNC-003` bootstrap contract must be accepted and complete.
- Exact persistence and coroutine choices belong to the execution plan.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
