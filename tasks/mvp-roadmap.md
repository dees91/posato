# Posato Apple MVP Roadmap

## Status and authority

- **Status:** Accepted
- **Revision:** 1
- **Prepared:** 2026-08-25
- **Accepted:** 2026-08-25
- **Accepted by:** Project maintainer
- **Provenance:** `user-confirmed`
- **Gate 6:** complete

This document is the accepted Gate 6 authority for epic membership, phases,
waves, lanes, the derived dependency graph, integration groups, and pull-request
order. It does not authorize implementation. Each linked revision 1 task
specification is accepted; future execution records are created only when an
accepted task is authorized, as required by [the task workflow](README.md).

The accepted [MVP scope](../docs/product/mvp-scope.md),
[design authority](../DESIGN.md),
[architecture baseline](../docs/decisions/0003-mvp-application-architecture-baseline.md),
[synchronization trust boundary](../docs/decisions/0002-synchronization-trust-and-workspace-modes.md),
and [quality contract](../docs/development/engineering-quality-contract.md)
remain higher authorities for their concerns.

## Planning boundaries

- The roadmap covers the Apple MVP on one supported arm64 Mac and one supported
  iPhone, plus the separate readiness review required before a release.
- PR #1 remains exactly the accepted application-skeleton increment. It does
  not implement blocking, synchronization, enrollment, or recovery behavior.
- Foundation work exists only when a named later task consumes its outcome.
- Exact files, APIs, algorithms, versions, migrations, and commands are chosen
  in each authorized execution plan, not in task specifications.
- The disposable interaction prototype at revision
  `a081d4278cf8517c46b4322ed3c098462f153570` is bounded UX evidence. Its Free
  play workbench, synthetic fixtures, normalization behavior, and geometry are
  not product requirements or implementation dependencies. See the
  [prototype source digest](../docs/wiki/sources/mvp-interaction-prototype.md).
- Schedules, portable workspaces, Android, Linux, product accounts, analytics,
  stronger early-end friction, and total-key-loss recovery remain outside the
  MVP task graph.

## Epics and completion checkpoints

| Epic | Outcome | Completion checkpoint |
| --- | --- | --- |
| Preparation | Apple resources and the first production-code PR are safe to start and merge. | Gate 7 preflight passes; all three PR #1 tasks are done; holistic PR #1 review passes. |
| Trust and local data | Sensitive data, diagnostics, and the local replica have accepted boundaries. | Security and diagnostics authorities are accepted; local state survives restart atomically. |
| Target management | Shared domains and semantic application policies have truthful device-local mappings. | Domain and mapping flows pass on the relevant simulators and physical devices. |
| Sessions and enforcement | Local bounded sessions enforce selected domains and applications safely on both platforms. | Physical local-session matrix passes for start, early end, expiry, failure, and cleanup. |
| Apple synchronization | Onboarding, policy, and session intent synchronize through the accepted Apple trust model. | One Mac and one iPhone complete the synchronized primary flow without false delivery claims. |
| Completion | The accepted measurable MVP outcome passes. | `MVP-001` is accepted on the controlled physical-device matrix. |
| Release readiness | Public-release obligations are reviewed separately from MVP behavior. | `RELEASE-001` is done or explicitly blocked with owners and clearing conditions. |

## Conflict classes and concurrency rules

| Token | Serialized ownership |
| --- | --- |
| `G` | Root Gradle, wrapper, or version catalog |
| `X` | Xcode project, identifiers, signing configuration, or entitlements |
| `M` | Shared model or persistence schema |
| `U` | Shared UI, navigation, or composition roots |
| `H` | macOS helper or IPC contract |
| `I` | iOS extension or App Group state |
| `JM` | macOS/JVM platform leaf |
| `IS` | iOS platform leaf |
| `AI` | iOS Apple synchronization adapter |
| `AM` | macOS Apple synchronization adapter |
| `AS` | Shared Apple synchronization integration |
| `D` | Durable decision and shared wiki routing |
| `C` | CI or release configuration |
| `V` | Cross-cutting verification |

A wave lists concurrency candidates, not automatic authorization. Tasks in one
wave have disjoint planning-level conflict tokens. Before parallel execution,
every accepted execution plan must still prove disjoint exact write surfaces
and frozen shared contracts and must name its isolated branch and worktree.
Reviewer assignment and integration order remain roadmap-owned activation
fields below. The initial maximum remains three concurrent tasks, with two
preferred for non-trivial integration risk.

The serialized decision tasks also reserve separate future authority paths so
their documentation work does not collide. These are ownership assignments,
not accepted contents; each file is created and accepted only through its
authorized task:

| Task | Planned primary authority |
| --- | --- |
| `SECURITY-001` | `docs/security/mvp-threat-model.md` |
| `DIAGNOSTICS-001` | `docs/security/diagnostics-and-support-data.md` |
| `MACOS-001` | `docs/decisions/0004-macos-helper-lifecycle-and-security.md` |
| `MACOS-002` | `docs/product/macos-website-support.md` |
| `SYNC-001` | `docs/decisions/0005-synchronization-format-and-cryptography.md` |
| `SYNC-003` | `docs/decisions/0006-apple-workspace-bootstrap.md` |

`SYNC-003` must freeze the shared synchronizable-Keychain item contract and
private-CloudKit mailbox schema contract in its accepted authority. The later
`AI` and `AM` tasks own only their platform implementations of those frozen
contracts; changing either shared contract reopens the decision or forces the
affected adapter tasks to serialize.

## Phase and wave schedule

Phase 1 is fully serialized. No other task starts before `CI-001` is complete
and PR #1 passes its holistic review and merge checkpoint.

| Phase and wave | Lane | Tasks | Barrier or integration rule |
| --- | --- | --- | --- |
| P1/W1.1 | Manual | `APPLE-001` | Gate 7 must complete before production implementation. |
| P1/W1.2 | Baseline | `FOUNDATION-001` | Starts only after Apple preflight. |
| P1/W1.3 | Baseline | `QUALITY-001` | Same PR #1 branch; root build ownership remains serialized. |
| P1/W1.4 | Baseline | `CI-001` | Must finish before PR #1 merge or any later parallel wave. |
| P1 checkpoint | Integration | PR #1 holistic review and merge | All three PR #1 tasks independently satisfy their review and DoD cycles. |
| P2/W2.1 | Governance | `SECURITY-001` | Shared `D` work starts after the PR #1 checkpoint. |
| P2/W2.2a | Governance | `DIAGNOSTICS-001` | Serialized durable authority and wiki routing. |
| P2/W2.2b | macOS decision | `MACOS-001` | Serialized durable authority and wiki routing. |
| P2/W2.2c | macOS decision | `MACOS-002` | Serialized durable authority and wiki routing. |
| P2/W2.3 | Shared KMP | `MODEL-001` | Freezes the first local-replica boundary. |
| P2/W2.4 | Shared UI / sync decision | `TARGETS-001`, `SYNC-001` | Concurrency candidates with `G+M+U` and `D` ownership. |
| P2/W2.5 | Shared UI / macOS / sync decision | `TARGETS-002`, `MACOS-003`, `SYNC-003` | Concurrency candidates with `M+U`, `H+G`, and `D` ownership. |
| P2/W2.6 | Platform leaves / shared sync | `TARGETS-003`, `TARGETS-004`, `SYNC-002` | Frozen target contracts allow disjoint `JM`, `IS+X`, and `M+G` plans. |
| P2/W2.7 | Shared UI | `SESSION-001` | Uses semantic contracts and fakes; it does not wait for native mappings. |
| P2/W2.8 | Shared sync | `SYNC-004` | Serialized shared-model ownership. |
| P3/W3.1 | macOS / iOS | `MACOS-004`, `IOS-001` | Disjoint native platform lanes. |
| P3/W3.2 | macOS / iOS | `MACOS-005`, `IOS-002` | Disjoint native platform lanes. |
| P3/W3.3 | iOS / macOS sync | `SYNC-005`, `SYNC-006` | Per-platform Keychain adapters implement the item contract frozen by `SYNC-003`; a contract change blocks parallel activation. |
| P3/W3.4 | iOS / macOS sync | `SYNC-007`, `SYNC-008` | Per-platform CloudKit adapters implement the mailbox schema frozen by `SYNC-003`; a contract change blocks parallel activation. |
| P3/W3.5 | UX integration | `SESSION-002` | Serializes both platform composition roots. |
| P3/W3.6 | Sync integration | `SYNC-009` | Joins platform adapters through the frozen bootstrap contract. |
| P3/W3.7 | Sync integration | `SYNC-010` | Connects the pending queue and truthful status. |
| P4/W4.1 | UX integration | `ONBOARDING-001` | First-installation flow. |
| P4/W4.2 | UX integration | `ONBOARDING-002` | Second-installation and delayed-key flow. |
| P4/W4.3 | Sync integration | `SYNC-011` | Domain and semantic application-policy convergence. |
| P4/W4.4 | Sync integration | `SYNC-012` | Active-session and stop/expiry convergence. |
| P5/W5.1 | Acceptance | `MVP-001` | Controlled physical Mac-and-iPhone acceptance. |
| Release/R1 | Readiness | `RELEASE-001` | Outside the MVP product-outcome critical path. |

### Parallel-wave activation records

The merge order below is fixed for each multi-task candidate wave. Reviewer
assignments remain `pending` until named independent reviewers confirm
availability; that state blocks parallel activation even when dependencies and
write surfaces otherwise qualify.

| Candidate wave | Merge or integration order | Reviewer availability |
| --- | --- | --- |
| P2/W2.4 | `TARGETS-001` -> `SYNC-001` | pending; blocks wave activation |
| P2/W2.5 | `MACOS-003` -> `TARGETS-002` -> `SYNC-003` | pending; blocks wave activation |
| P2/W2.6 | `SYNC-002` -> `TARGETS-003` -> `TARGETS-004` | pending; blocks wave activation |
| P3/W3.1 | `MACOS-004` -> `IOS-001` | pending; blocks wave activation |
| P3/W3.2 | `MACOS-005` -> `IOS-002` | pending; blocks wave activation |
| P3/W3.3 | `SYNC-005` -> `SYNC-006` | pending; also requires the accepted `SYNC-003` Keychain-item contract |
| P3/W3.4 | `SYNC-007` -> `SYNC-008` | pending; also requires the accepted `SYNC-003` CloudKit-mailbox contract |

## Dependency graph and critical paths

The direct dependencies are authoritative in the linked task specifications
and reproduced completely in the task map. This selected-path summary shows
principal joins and groups platform adapter siblings for readability; it is
not a complete edge rendering.

```text
APPLE-001 -> FOUNDATION-001 -> QUALITY-001 -> CI-001 -> PR #1 merge

{FOUNDATION-001, SECURITY-001 -> DIAGNOSTICS-001} -> MODEL-001
SECURITY-001 -> {MACOS-001 -> MACOS-003, MACOS-002, SYNC-001}
MODEL-001 -> {TARGETS-001, TARGETS-002}
TARGETS-001 + TARGETS-002 -> SESSION-001
TARGETS-002 -> {TARGETS-003, TARGETS-004}

{MACOS-002, MACOS-003, SESSION-001, TARGETS-001} -> MACOS-004
{MACOS-003, SESSION-001, TARGETS-003} -> MACOS-005
{SESSION-001, TARGETS-004, APPLE-001} -> IOS-001 -> IOS-002
{MACOS-004, MACOS-005, IOS-002, SESSION-001} -> SESSION-002

SYNC-001 -> SYNC-002
    |
    +-> SYNC-003 -> {SYNC-004, SYNC-005, SYNC-006, SYNC-007, SYNC-008}
                              \_____________________________________/
                                                |
                                                v
                                            SYNC-009 -> SYNC-010
                                                            |
                                                            v
                         ONBOARDING-001 -> ONBOARDING-002 -> SYNC-011
                                                                  |
SESSION-002 ------------------------------------------------------+
                                                                  v
                                                              SYNC-012
                                                                  |
                                                                  v
                                                               MVP-001
                                                                  |
                                                        release only
                                                                  v
                                                             RELEASE-001
```

Using one unit per task, the five tied longest semantic paths are captured by:

```text
SECURITY-001 -> SYNC-001 -> SYNC-003
  -> { SYNC-004 | SYNC-005 | SYNC-006 | SYNC-007 | SYNC-008 }
  -> SYNC-009 -> SYNC-010 -> ONBOARDING-001 -> ONBOARDING-002
  -> SYNC-011 -> SYNC-012 -> MVP-001
```

The resource-constrained gate path is the ordered wave-barrier chain:

```text
P1/W1.1 -> W1.2 -> W1.3 -> W1.4 -> PR #1 merge
-> P2/W2.1 -> W2.2a -> W2.2b -> W2.2c -> W2.3 -> W2.4
-> W2.5 -> W2.6 -> W2.7 -> W2.8
-> P3/W3.1 -> W3.2 -> W3.3 -> W3.4 -> W3.5 -> W3.6 -> W3.7
-> P4/W4.1 -> W4.2 -> W4.3 -> W4.4 -> P5/W5.1
```

These are dependency and gate paths, not duration estimates.

## Task and integration-group map

Every row has exactly one integration group: one pull request or the explicit
manual Gate 7. PR #1 is the only planned multi-task pull request. Its three
tasks still complete separate plans, reviews, corrections, and DoD evidence,
followed by a holistic PR review.

| Task specification | Epic | Phase/wave | Direct dependencies | Conflict classes | Integration group |
| --- | --- | --- | --- | --- | --- |
| [APPLE-001](specifications/apple-001-register-apple-resources.md) | Preparation | P1/W1.1 | None | `X` | Manual Gate 7 |
| [FOUNDATION-001](specifications/foundation-001-apple-application-skeleton.md) | Preparation | P1/W1.2 | `APPLE-001` | `G`, `X`, `U` | PR #1 |
| [QUALITY-001](specifications/quality-001-local-quality-gate.md) | Preparation | P1/W1.3 | `FOUNDATION-001` | `G` | PR #1 |
| [CI-001](specifications/ci-001-credential-free-ci.md) | Preparation | P1/W1.4 | `FOUNDATION-001`, `QUALITY-001` | `C`, `G`, `X` | PR #1 |
| [SECURITY-001](specifications/security-001-mvp-threat-model.md) | Trust and local data | P2/W2.1 | None | `D` | PR-SECURITY |
| [DIAGNOSTICS-001](specifications/diagnostics-001-diagnostics-policy.md) | Trust and local data | P2/W2.2a | `SECURITY-001` | `D` | PR-DIAGNOSTICS |
| [MACOS-001](specifications/macos-001-helper-lifecycle-contract.md) | Sessions and enforcement | P2/W2.2b | `SECURITY-001` | `D` | PR-MAC-HELPER-CONTRACT |
| [MACOS-002](specifications/macos-002-website-support-contract.md) | Sessions and enforcement | P2/W2.2c | `SECURITY-001` | `D` | PR-MAC-WEB-CONTRACT |
| [MODEL-001](specifications/model-001-local-first-replica.md) | Trust and local data | P2/W2.3 | `FOUNDATION-001`, `SECURITY-001`, `DIAGNOSTICS-001` | `M`, `G` | PR-LOCAL-REPLICA |
| [TARGETS-001](specifications/targets-001-exact-domain-management.md) | Target management | P2/W2.4 | `MODEL-001` | `G`, `M`, `U` | PR-DOMAINS |
| [SYNC-001](specifications/sync-001-encrypted-operation-contract.md) | Apple synchronization | P2/W2.4 | `SECURITY-001` | `D` | PR-SYNC-FORMAT |
| [MACOS-003](specifications/macos-003-authenticated-helper-ipc.md) | Sessions and enforcement | P2/W2.5 | `MACOS-001`, `FOUNDATION-001` | `H`, `G` | PR-MAC-HELPER |
| [TARGETS-002](specifications/targets-002-semantic-application-policy.md) | Target management | P2/W2.5 | `MODEL-001` | `M`, `U` | PR-APP-POLICY |
| [SYNC-003](specifications/sync-003-apple-bootstrap-contract.md) | Apple synchronization | P2/W2.5 | `SYNC-001`, `APPLE-001` | `D` | PR-APPLE-SYNC-CONTRACT |
| [TARGETS-003](specifications/targets-003-macos-application-mapping.md) | Target management | P2/W2.6 | `TARGETS-002` | `JM` | PR-MAC-MAPPING |
| [TARGETS-004](specifications/targets-004-ios-application-mapping.md) | Target management | P2/W2.6 | `TARGETS-002`, `APPLE-001` | `IS`, `X` | PR-IOS-MAPPING |
| [SYNC-002](specifications/sync-002-encrypted-operation-core.md) | Apple synchronization | P2/W2.6 | `SYNC-001` | `M`, `G` | PR-SYNC-CORE |
| [SESSION-001](specifications/session-001-shared-manual-session.md) | Sessions and enforcement | P2/W2.7 | `TARGETS-001`, `TARGETS-002` | `M`, `U` | PR-SESSION-CORE |
| [SYNC-004](specifications/sync-004-bootstrap-state-machine.md) | Apple synchronization | P2/W2.8 | `SYNC-003` | `M` | PR-BOOTSTRAP-CORE |
| [MACOS-004](specifications/macos-004-domain-enforcement.md) | Sessions and enforcement | P3/W3.1 | `MACOS-002`, `MACOS-003`, `SESSION-001`, `TARGETS-001` | `H`, `JM` | PR-MAC-DOMAINS |
| [IOS-001](specifications/ios-001-managed-settings-enforcement.md) | Sessions and enforcement | P3/W3.1 | `SESSION-001`, `TARGETS-004`, `APPLE-001` | `IS`, `X`, `I` | PR-IOS-ENFORCEMENT |
| [MACOS-005](specifications/macos-005-application-enforcement.md) | Sessions and enforcement | P3/W3.2 | `MACOS-003`, `SESSION-001`, `TARGETS-003` | `H`, `JM` | PR-MAC-APPS |
| [IOS-002](specifications/ios-002-activity-monitor-expiry.md) | Sessions and enforcement | P3/W3.2 | `IOS-001` | `IS`, `X`, `I` | PR-IOS-EXPIRY |
| [SYNC-005](specifications/sync-005-ios-keychain-adapter.md) | Apple synchronization | P3/W3.3 | `SYNC-003` | `AI`, `X` | PR-IOS-KEYCHAIN |
| [SYNC-006](specifications/sync-006-macos-keychain-adapter.md) | Apple synchronization | P3/W3.3 | `SYNC-003` | `AM` | PR-MAC-KEYCHAIN |
| [SYNC-007](specifications/sync-007-ios-cloudkit-mailbox.md) | Apple synchronization | P3/W3.4 | `SYNC-003` | `AI`, `X` | PR-IOS-CLOUDKIT |
| [SYNC-008](specifications/sync-008-macos-cloudkit-mailbox.md) | Apple synchronization | P3/W3.4 | `SYNC-003` | `AM` | PR-MAC-CLOUDKIT |
| [SESSION-002](specifications/session-002-local-session-integration.md) | Sessions and enforcement | P3/W3.5 | `MACOS-004`, `MACOS-005`, `IOS-002`, `SESSION-001` | `U`, `JM`, `IS` | PR-LOCAL-SESSION |
| [SYNC-009](specifications/sync-009-apple-bootstrap-integration.md) | Apple synchronization | P3/W3.6 | `SYNC-002`, `SYNC-004`, `SYNC-005`, `SYNC-006`, `SYNC-007`, `SYNC-008` | `AS`, `M`, `U` | PR-APPLE-BOOTSTRAP |
| [SYNC-010](specifications/sync-010-cloudkit-sync-coordinator.md) | Apple synchronization | P3/W3.7 | `SYNC-009`, `MODEL-001` | `AS`, `M`, `U` | PR-CLOUDKIT-SYNC |
| [ONBOARDING-001](specifications/onboarding-001-first-installation.md) | Apple synchronization | P4/W4.1 | `SYNC-010`, `TARGETS-001`, `TARGETS-003`, `TARGETS-004` | `U`, `JM`, `IS` | PR-FIRST-INSTALL |
| [ONBOARDING-002](specifications/onboarding-002-second-installation.md) | Apple synchronization | P4/W4.2 | `ONBOARDING-001` | `U`, `AS` | PR-SECOND-INSTALL |
| [SYNC-011](specifications/sync-011-policy-synchronization.md) | Apple synchronization | P4/W4.3 | `ONBOARDING-002`, `SYNC-010`, `TARGETS-001`, `TARGETS-002` | `M`, `U`, `AS` | PR-POLICY-SYNC |
| [SYNC-012](specifications/sync-012-session-synchronization.md) | Apple synchronization | P4/W4.4 | `SYNC-011`, `SESSION-002` | `M`, `U`, `AS` | PR-SESSION-SYNC |
| [MVP-001](specifications/mvp-001-physical-acceptance.md) | Completion | P5/W5.1 | `SYNC-012` | `V` | PR-MVP-ACCEPTANCE |
| [RELEASE-001](specifications/release-001-release-readiness.md) | Release readiness | Release/R1 | `MVP-001` | `C`, `X`, `D` | PR-RELEASE-READINESS |

Future execution records use the same task ID under `tasks/executions/` and
are created only when the accepted task is authorized.

## Coverage matrix

| Accepted outcome or obligation | Owning tasks | Required terminal evidence |
| --- | --- | --- |
| PR #1 module, target, identifier, shell, Metro, fake-contract, quality, and CI boundary | `APPLE-001`, `FOUNDATION-001`, `QUALITY-001`, `CI-001` | macOS runtime, iOS Simulator build, graph validation, local gate, CI, holistic review |
| Exact website management | `MODEL-001`, `TARGETS-001` | deterministic validation, persistence, Compose UI, visual and accessibility checks |
| Semantic application policy with local native mappings | `TARGETS-002`, `TARGETS-003`, `TARGETS-004` | shared contract tests plus physical Mac and iPhone selection evidence |
| Bounded manual session, review, early end, and normal expiry | `SESSION-001`, `SESSION-002`, `IOS-002` | contract/UI tests and physical local-session matrix |
| macOS website and application blocking | `MACOS-001` through `MACOS-005` | authenticated IPC, browser matrix, app-control matrix, failure and exact cleanup evidence |
| iOS website and application blocking | `TARGETS-004`, `IOS-001`, `IOS-002` | physical authorization, selection, shield, expiry, permission-loss, and cleanup evidence |
| Common E2EE and signed immutable operations | `SYNC-001`, `SYNC-002` | accepted security decision, cross-target vectors, tamper/replay/oversize rejection |
| One Apple workspace with delayed-key waiting | `SYNC-003` through `SYNC-009`, `ONBOARDING-002` | physical CloudKit/Keychain race and account-isolation matrix; no parallel workspace |
| First-installation onboarding | `ONBOARDING-001` | physical flow on both supported platforms without product account or Posato pairing |
| Second-installation onboarding | `ONBOARDING-002` | physical existing-workspace and delayed-key flow with device-local mapping |
| Exact domain and semantic application-policy convergence | `SYNC-010`, `SYNC-011` | physical bidirectional convergence; opaque selections remain local |
| Active-session, early-end, and expiry convergence | `SESSION-002`, `SYNC-012` | physical cross-device start, early stop, normal expiry, offline/retry evidence |
| Ordinary action-required and retry recovery | Owning target, enforcement, Keychain, CloudKit, and coordinator tasks | each owner preserves last valid state and proves its repair/retry path; no late generic recovery task |
| Complete measurable MVP outcome | `MVP-001` | one controlled Mac-and-iPhone pass without manual state repair |
| Required pre-release audit | `RELEASE-001` | explicit pass or blocked verdict for every readiness category |

The measurable outcome rows from `docs/product/mvp-scope.md` map as follows:

| Measurable row | Task evidence |
| --- | --- |
| **Sync with iCloud** on each installation without Posato pairing | `ONBOARDING-001`, `ONBOARDING-002`, `MVP-001` |
| Delayed Keychain delivery waits and never creates another workspace | `SYNC-004` through `SYNC-009`, `ONBOARDING-002`, `MVP-001` |
| Exact domains arrive unchanged | `SYNC-011`, `MVP-001` |
| Semantic application policy synchronizes while native selections stay local | `TARGETS-002` through `TARGETS-004`, `SYNC-011`, `MVP-001` |
| Session intent reaches the second device | `SYNC-012`, `MVP-001` |
| Both devices block the selected domain and locally mapped application | `SESSION-002`, `SYNC-012`, `MVP-001` |
| Early termination synchronizes and clears restrictions | `SYNC-012`, `MVP-001` |
| Normal expiry clears restrictions on both devices | `IOS-002`, `SESSION-002`, `SYNC-012`, `MVP-001` |

## Manual and physical-device gates

| Gate | Earliest owning task | Completion rule |
| --- | --- | --- |
| Apple account, identifiers, groups, container, entitlements, profiles | `APPLE-001` | Read-only preflight passes without tracked secrets or machine identifiers. |
| Family Controls distribution availability | `TARGETS-004` / `IOS-001` | The affected task stays blocked when required entitlement or profile support is absent. |
| iOS suspended expiry opportunity | `IOS-002` | Physical evidence observes cleanup when iOS supplies the interval-end callback; no exact wall-clock or device-wake promise is accepted. |
| macOS helper signing and privilege path | `MACOS-003` | Physical peer-authentication, authorization, failure, and uninstall/recovery evidence passes. |
| Current browser support and proxy coexistence | `MACOS-002` / `MACOS-004` | Maintainer accepts the support contract; physical supported-browser matrix passes. |
| CloudKit and Keychain environments | `SYNC-005` through `SYNC-009` | Physical account, delay, restart, error, and cleanup evidence passes. |
| macOS synchronization native access | `SYNC-003`, then `SYNC-006` and `SYNC-008` | Maintainer accepts app-owned access within ADR 0003, or consumers remain blocked pending architecture and Apple-resource amendments. The enforcement helper never owns synchronization. |
| Complete product flow | `MVP-001` | The exact accepted Mac-and-iPhone matrix passes without manual repair. |
| Public distribution | `RELEASE-001` | Separate readiness verdict; not inferred from MVP behavior. |

No credential, signing identity, provisioning profile, private device ID, raw
capture, opaque application token, domain fixture from a real person, or
account-specific value enters tracked task evidence.

## Task execution and acceptance procedure

1. The maintainer accepted revision 1 of this roadmap and every linked task
   specification on 2026-08-25.
2. That acceptance completes Gate 6 and makes `APPLE-001` the first incomplete
   task. It does not start production implementation automatically.
3. `APPLE-001` completes Gate 7 and the readiness checkpoint is accepted.
4. An authorized task receives an execution record, exact write surface,
   technical plan, and independent plan review before implementation.
5. A different agent reviews the completed change. Corrections repeat with the
   same reviewer until no Critical or Required finding remains; after three
   unsuccessful rounds the review escalates.
6. Final verification records every acceptance criterion and every applicable
   standing Definition of Done item after the last material correction.
7. Parallel work starts only at a declared concurrency-candidate wave and only
   when all worktree, dependency, contract, write-surface, reviewer, and
   integration-order conditions are satisfied.

Gate 6 acceptance makes this roadmap and its linked specifications planning
authorities. It does not authorize application scaffolding, create downstream
execution records, or bypass the dependency, review, evidence, and checkpoint
rules above.
