# `ONBOARDING-002`: Complete second-installation onboarding

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `ONBOARDING-001`
- **Stable concurrency constraints:** Shared onboarding UI, Apple bootstrap integration, and both platform composition roots are serialized.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

A second installation joins the existing Apple workspace, waits safely for a delayed synchronizable key, and completes only the local mappings it needs.

## Context

The defining trust-model race is that CloudKit workspace evidence may arrive before the synchronizable Keychain item. The UI must wait rather than offer a parallel workspace.

## In scope

- Present existing-workspace discovery, key waiting, retry, action-required, account-change, and ready states.
- Prevent workspace creation whenever existing-workspace evidence requires waiting.
- Restore shared semantic target intent and guide only the device-local mapping work needed on this installation.
- Support interruption, restart, offline, and delayed-service recovery.

## Out of scope

- Manual pairing, recovery codes, product accounts, workspace choice, or total-key-loss recovery.
- Policy or session synchronization implementation.
- Copying opaque application selections between devices.

## Acceptance criteria

- `AC-01` — On the controlled second physical installation, existing-workspace discovery leads to adoption and local setup without Posato pairing.
- `AC-02` — When workspace evidence arrives before the key, the flow remains in a clear waiting or retry state and never exposes a create-another-workspace action.
- `AC-03` — Restart, offline, Apple-service delay, account change, permission denial, and retry preserve valid state and the accepted next action.
- `AC-04` — Shared exact domains and semantic application intent are distinguishable from device-local mapping work, and opaque selections are never transferred.
- `AC-05` — The complete flow passes visual, accessibility, content, interruption, long-wait, and non-color state checks.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Onboarding and invariant Compose UI tests.
- Physical Mac-and-iPhone existing-workspace, CloudKit-first, Keychain-first, delayed-key, restart, offline, account, retry, and mapping matrix.
- Visual, content, accessibility, privacy, and security review.
- Aggregate quality gate and independent review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates existing-workspace, delayed-key, conflict, account, offline, restart, retry, and mapping outcomes. |
| Authentication or authorization | yes | Enforces Apple-account membership and prevents workspace creation whenever existing-workspace evidence requires waiting. |
| Secrets, signing, or credentials | yes | Workspace-key delay is presented without exposing key, account, credential, or signing material. |
| Personal data or diagnostics | yes | Separates shared intent from local opaque mapping work and keeps account details redacted. |
| Storage or migration | yes | Owns resumable waiting, retry, action-required, restart, and ready onboarding state. |
| Cryptography | yes | Preserves the accepted workspace-key and encrypted-workspace invariant without changing cryptographic algorithms. |
| Native IPC or entitlements | yes | Integrates Keychain, CloudKit, permission, and local-mapping outcomes through semantic platform boundaries. |
| External services | yes | Keychain-first, CloudKit-first, delayed delivery, account, offline, and retry behavior require the physical device pair. |
| Dependencies or licenses | no | The second-installation onboarding flow outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | The disposable UX prototype is bounded external evidence and must not become the production reducer or geometry. |

## Decision gates

- The first-installation vocabulary and Apple bootstrap states must be stable.
- Apple-managed delayed delivery may block a test row until it can be reproduced under controlled conditions.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Design authority](../../DESIGN.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
- [Interaction prototype source digest](../../docs/wiki/sources/mvp-interaction-prototype.md)
