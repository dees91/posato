# `ONBOARDING-001`: Complete first-installation onboarding

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SYNC-010`, `TARGETS-001`, `TARGETS-003`, `TARGETS-004`
- **Stable concurrency constraints:** Shared onboarding UI, navigation, and both platform composition roots are serialized.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

A first Posato installation explains purpose and privacy, establishes the one Apple workspace, requests platform authorization contextually, and completes initial target setup without a product account.

## Context

The accepted MVP requires a low-ceremony first-installation path that uses Apple-managed trust and remains honest about permissions, local mappings, and synchronization state.

## In scope

- Present purpose, privacy, Apple-service, workspace, authorization, and target-setup states in the accepted order.
- Create or adopt a workspace only through the accepted bootstrap eligibility.
- Request platform permissions at the moment their related setup action is chosen.
- Complete a usable initial exact-domain or semantic-application policy setup with truthful local mapping state.

## Out of scope

- Second-installation delayed-key behavior.
- Policy or session convergence between devices.
- Product accounts, Posato pairing, recovery codes, analytics, or prototype Free play tools.

## Acceptance criteria

- `AC-01` — On each physical platform, a fresh installation reaches a usable target state without a product account or Posato-specific pairing ceremony.
- `AC-02` — Workspace creation or adoption follows the one-workspace contract and never bypasses waiting, retry, or action-required states.
- `AC-03` — Every platform permission is requested contextually and denial or unavailability leaves a truthful, recoverable path.
- `AC-04` — The person can understand privacy, local-versus-shared data, current sync state, and next action without hidden technical assumptions.
- `AC-05` — The complete flow passes visual, keyboard/touch, screen-reader, text-scaling, focus, reduced-motion, and interruption/resume checks.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Onboarding state and Compose UI tests.
- Physical Mac and iPhone fresh-install, permission-denial, offline, interruption, retry, and target-setup matrix.
- Visual, content, accessibility, privacy, and security review.
- Aggregate quality gate and independent review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates user choices, permission outcomes, target setup, interruptions, retries, and Apple-service states. |
| Authentication or authorization | yes | Owns contextual platform permission requests and uses only accepted workspace-creation eligibility. |
| Secrets, signing, or credentials | yes | Must not expose Apple credentials, workspace keys, signing material, or private account data in UI or evidence. |
| Personal data or diagnostics | yes | Explains local-versus-shared data and preserves the accepted no-account, no-surveillance, and diagnostics boundaries. |
| Storage or migration | yes | Owns resumable first-installation state across interruption, restart, retry, and action-required outcomes. |
| Cryptography | no | The first-installation onboarding flow outcome does not change cryptographic contracts, providers, keys, or secret processing. |
| Native IPC or entitlements | yes | Integrates accepted platform authorization and target-mapping outcomes without leaking native types into shared UI. |
| External services | yes | Apple account, CloudKit, Keychain, permission, offline, and retry behavior require physical evidence on both platforms. |
| Dependencies or licenses | no | The first-installation onboarding flow outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | The disposable UX prototype is bounded external evidence and must not be copied or treated as final interaction authority. |

## Decision gates

- Apple bootstrap, target management, and local mapping outcomes must be complete.
- No new identity or recovery ceremony may be introduced without maintainer acceptance.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Design authority](../../DESIGN.md)
- [Interaction prototype source digest](../../docs/wiki/sources/mvp-interaction-prototype.md)
