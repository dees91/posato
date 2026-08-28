# Execution: `PLANNING-001`

- **Task specification:** [PLANNING-001](../specifications/planning-001-mvp-roadmap.md)
- **Execution status:** `done`
- **Implementer:** `/root`
- **Plan reviewer:** `/root/plan_review`
- **Code reviewer:** `/root/documentation_review`
- **Branch/worktree:** `main`
- **Started:** 2026-08-25
- **Last updated:** 2026-08-25

Identifiers beginning with `/root` below name collaboration agents. They are
not filesystem paths.

`PLANNING-001` began as a pre-acceptance Gate 6 drafting record under the
explicit parent-gate exception in `docs/tasks/README.md`. The plan and
completed-change reviews below therefore occurred while the outputs were
candidates. The maintainer accepted this specification, roadmap revision 1,
and all 36 linked task specifications on 2026-08-25; final acceptance evidence
received the independent Round 4 acceptance-state approval required for this
record to enter `done`. Gate 6 acceptance does not authorize downstream
implementation.

## Technical implementation plan

### Repository findings and authorities

- Gates 1 through 5 are accepted, and Gate 6 is the first incomplete gate.
- `docs/tasks/README.md` makes the Gate 6 roadmap authoritative for epic membership,
  phases, waves, lanes, the derived dependency graph, and pull-request order.
- PR #1 must create only the reviewed Apple application skeleton, shared shell,
  semantic platform contracts and fakes, baseline tests and quality checks,
  and working CI for its introduced surfaces. It must not implement enforcement
  or synchronization.
- CI is a separate implementation task due before PR #1 merges or the first
  parallel implementation wave starts, whichever comes first.
- Gate 7 registers Apple resources before PR #1 production implementation.
- The accepted MVP ends in a controlled physical Mac-and-iPhone flow covering
  configuration, session activation, enforcement, synchronization, expiry,
  intentional early termination, and safe delayed-key behavior.
- Browser inspection of prototype revision
  `a081d4278cf8517c46b4322ed3c098462f153570` reproduced the first-session,
  delayed-key, and action-required surfaces. Its Free play prerequisite setup,
  synthetic application names, exact domain normalization, and layout geometry
  remain non-production experiment details.

### Assumptions and open questions

- The maintainer's instruction to proceed to Gate 6 authorized drafting under
  the parent-gate exception in `docs/tasks/README.md`; it did not accept the output
  in advance. Explicit acceptance followed on 2026-08-25 after the completed
  review loop.
- Roadmap revision 1 and all 36 downstream task specifications are accepted.
- Execution records for downstream tasks are created only when an accepted
  task is authorized, as required by `docs/tasks/README.md`; the roadmap reserves
  their canonical paths without fabricating future plans or reviews.
- Exact dependency versions, files, APIs, algorithms, native-helper choices,
  Apple schema details, cryptographic primitives, and CI jobs remain technical
  plan or named decision-task work, not task-specification content.
- Open product or durable architecture questions block only the named task that
  needs their answer; unrelated ready work may continue when its dependency and
  write-surface conditions are satisfied.

### Candidate decomposition for plan review

The conflict classes below are planning-level ownership warnings, not exact
write surfaces: `G` is root Gradle or version-catalog ownership, `X` is the
Xcode project or entitlements, `M` is shared model or persistence schema, `U`
is shared UI, navigation, or composition roots, `H` is macOS helper or IPC,
`I` is the iOS extension or App Group, `JM` and `IS` are macOS/JVM and iOS
platform leaves, `AI`, `AM`, and `AS` are iOS, macOS, and shared Apple sync
ownership, `D` is durable decision or wiki authority, `C` is CI or release
configuration, and `V` is cross-cutting verification. Tokens are normalized;
prose qualifications belong in the decision-gate column. A wave identifies concurrency
candidates only; each authorized execution plan must still prove disjoint
exact write surfaces against every active task.

| Task | Epic | One observable outcome | Direct dependencies | Schedule and lane | Decision or manual gate | Conflict class | Integration group | Device evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `APPLE-001` | Preparation | Required Apple identifiers, capabilities, profiles, and portable local configuration are registered and preflighted. | None | P1/W1.1 · manual | Apple account, 2FA, Family Controls path, signing ownership | `X` | Manual Gate 7 | Read-only local preflight |
| `SECURITY-001` | Trust and local data | An accepted MVP threat model and sensitive-data classification govern implementation. | None | P2/W2.1 · governance | Maintainer security and privacy acceptance | `D` | PR-SECURITY | N/A |
| `FOUNDATION-001` | Preparation | The reviewed Apple application skeleton renders the accepted shell on macOS and iOS. | `APPLE-001` | P1/W1.2 · baseline | Toolchain compatibility selection | `G`, `X`, `U` | PR #1 | macOS runtime and iOS Simulator |
| `DIAGNOSTICS-001` | Trust and local data | An accepted diagnostic and support-data policy defines consent, allowlisted fields, redaction, retention, and export boundaries. | `SECURITY-001` | P2/W2.2a · governance | Maintainer privacy acceptance | `D` | PR-DIAGNOSTICS | N/A |
| `MACOS-001` | Sessions and enforcement | An accepted native-helper ownership, privilege, installation, update, uninstall, and IPC security contract is ready for implementation. | `SECURITY-001` | P2/W2.2b · macOS decision | Maintainer architecture and security acceptance | `D` | PR-MAC-HELPER-CONTRACT | Targeted helper preflight if needed |
| `MACOS-002` | Sessions and enforcement | An accepted macOS website-support, proxy-coexistence, privacy-safe presentation, and recovery contract is ready for implementation. | `SECURITY-001` | P2/W2.2c · macOS decision | Maintainer product and architecture acceptance | `D` | PR-MAC-WEB-CONTRACT | Targeted browser recheck if needed |
| `QUALITY-001` | Preparation | One local gate enforces formatting, Detekt with Compose Rules, warnings, and introduced tests. | `FOUNDATION-001` | P1/W1.3 · baseline | Tool compatibility review | `G` | PR #1 | N/A beyond builds |
| `CI-001` | Preparation | Credential-free CI runs the repository quality gate for every surface introduced by PR #1. | `FOUNDATION-001`, `QUALITY-001` | P1/W1.4 · baseline | Runner compatibility selection | `C`, `G`, `X` | PR #1 | CI build evidence |
| `MODEL-001` | Trust and local data | The first local-replica slice preserves exact-domain policy atomically across restart. | `FOUNDATION-001`, `SECURITY-001`, `DIAGNOSTICS-001` | P2/W2.3 · shared KMP | Storage and migration contract | `M`, `G` | PR-LOCAL-REPLICA | macOS runtime and iOS Simulator |
| `TARGETS-001` | Target management | A person can add, edit, remove, and review exact domains locally on both applications, with the first UI state-holder boundary accepted. | `MODEL-001` | P2/W2.4 · shared UI | Domain validation plus first navigation, state-holder, coroutine-ownership, compatibility, and license decisions | `G`, `M`, `U` | PR-DOMAINS | macOS runtime and iOS Simulator |
| `MACOS-003` | Sessions and enforcement | The macOS application communicates with a signed helper through authenticated, bounded, recoverable IPC. | `MACOS-001`, `FOUNDATION-001` | P2/W2.5 · macOS native | Signing and privilege availability | `H`, `G` | PR-MAC-HELPER | Physical Mac |
| `SYNC-001` | Apple synchronization | An accepted encrypted-operation, signing, encoding, versioning, and validation contract governs every transport. | `SECURITY-001` | P2/W2.4 · sync decision | Maintainer security and architecture acceptance | `D` | PR-SYNC-FORMAT | Cross-target vector plan |
| `TARGETS-002` | Target management | Shared UI can create semantic application policies and represent device-local mapping requirements without platform tokens. | `MODEL-001` | P2/W2.5 · shared UI | Semantic policy and mapping contract | `M`, `U` | PR-APP-POLICY | macOS runtime and iOS Simulator |
| `SYNC-003` | Apple synchronization | An accepted Apple bootstrap and native-adapter contract defines one-workspace behavior, app-owned macOS synchronization access, one interoperable Keychain item contract, and one CloudKit mailbox schema contract. | `SYNC-001`, `APPLE-001` | P2/W2.5 · sync decision | Maintainer architecture and security acceptance; any new macOS target or process requires ADR and Apple-resource amendment | `D` | PR-APPLE-SYNC-CONTRACT | Physical test design |
| `TARGETS-003` | Target management | A Mac can associate device-local application selections with an existing semantic application policy. | `TARGETS-002` | P2/W2.6 · macOS leaf | macOS selection boundary after frozen shared contracts | `JM` | PR-MAC-MAPPING | Physical Mac |
| `TARGETS-004` | Target management | An iPhone can authorize and associate opaque local application selections without exposing or synchronizing the tokens. | `TARGETS-002`, `APPLE-001` | P2/W2.6 · iOS leaf | Family Controls availability after frozen shared contracts | `IS`, `X` | PR-IOS-MAPPING | Physical iPhone |
| `SYNC-002` | Apple synchronization | Signed encrypted operations converge deterministically and reject invalid input without replacing valid state. | `SYNC-001` | P2/W2.6 · shared sync | Production crypto provider and license review | `M`, `G` | PR-SYNC-CORE | Cross-target golden vectors |
| `SESSION-001` | Sessions and enforcement | Shared session setup, review, start, early-end, and expiry behavior rejects invalid effective local state through a semantic enforcer boundary. | `TARGETS-001`, `TARGETS-002` | P2/W2.7 · shared UI | Stateful UI ownership | `M`, `U` | PR-SESSION-CORE | macOS runtime and iOS Simulator |
| `SYNC-004` | Apple synchronization | A deterministic bootstrap state machine preserves the one-workspace invariant against delayed and conflicting service outcomes. | `SYNC-003` | P2/W2.8 · shared sync | None beyond accepted bootstrap contract | `M` | PR-BOOTSTRAP-CORE | Contract tests with fakes |
| `MACOS-004` | Sessions and enforcement | A selected exact domain is denied on supported macOS browsers with recoverable proxy state and privacy-safe presentation. | `MACOS-002`, `MACOS-003`, `SESSION-001`, `TARGETS-001` | P3/W3.1 · macOS native | Accepted browser and coexistence contract | `H`, `JM` | PR-MAC-DOMAINS | Physical Mac/browser matrix |
| `IOS-001` | Sessions and enforcement | iOS applies and clears Posato-owned domain and application restrictions for a valid local session using default system shields. | `SESSION-001`, `TARGETS-004`, `APPLE-001` | P3/W3.1 · iOS native | Family Controls distribution availability | `IS`, `X`, `I` | PR-IOS-ENFORCEMENT | Physical iPhone |
| `MACOS-005` | Sessions and enforcement | Locally mapped macOS applications are restricted during a session without affecting unselected controls. | `MACOS-003`, `SESSION-001`, `TARGETS-003` | P3/W3.2 · macOS native | Application identity safety contract | `H`, `JM` | PR-MAC-APPS | Physical Mac |
| `IOS-002` | Sessions and enforcement | The iOS activity-monitor extension removes only Posato-owned restrictions after normal expiry when the application is suspended. | `IOS-001` | P3/W3.2 · iOS native | App Group schema, extension lifecycle, and callback-opportunity evidence without a wall-clock promise | `IS`, `X`, `I` | PR-IOS-EXPIRY | Physical iPhone |
| `SYNC-005` | Apple synchronization | The iOS application reads and writes the synchronizable workspace key with accepted confidentiality and account isolation. | `SYNC-003` | P3/W3.3 · iOS Apple adapter | Frozen cross-platform item contract plus access-group availability | `AI`, `X` | PR-IOS-KEYCHAIN | Physical iPhone |
| `SYNC-006` | Apple synchronization | The macOS application reads and writes the synchronizable workspace key through the accepted app-owned native boundary. | `SYNC-003` | P3/W3.3 · macOS Apple adapter | Frozen cross-platform item contract and accepted app-owned native-access boundary | `AM` | PR-MAC-KEYCHAIN | Physical Mac |
| `SYNC-007` | Apple synchronization | The iOS application exchanges bounded encrypted mailbox data with its private CloudKit database. | `SYNC-003` | P3/W3.4 · iOS Apple adapter | Frozen cross-platform mailbox schema and environment availability | `AI`, `X` | PR-IOS-CLOUDKIT | Physical iPhone |
| `SYNC-008` | Apple synchronization | The macOS application exchanges bounded encrypted mailbox data with its private CloudKit database through the accepted app-owned native boundary. | `SYNC-003` | P3/W3.4 · macOS Apple adapter | Frozen cross-platform mailbox schema and accepted app-owned native-access boundary | `AM` | PR-MAC-CLOUDKIT | Physical Mac |
| `SESSION-002` | Sessions and enforcement | One local manual session starts, blocks selected targets, ends early, and expires safely on each supported platform. | `MACOS-004`, `MACOS-005`, `IOS-002`, `SESSION-001` | P3/W3.5 · UX integration | None beyond accepted platform contracts | `U`, `JM`, `IS` | PR-LOCAL-SESSION | Physical Mac and iPhone |
| `SYNC-009` | Apple synchronization | Both applications integrate CloudKit and Keychain outcomes through the bootstrap state machine without creating a parallel workspace. | `SYNC-002`, `SYNC-004`, `SYNC-005`, `SYNC-006`, `SYNC-007`, `SYNC-008` | P3/W3.6 · sync integration | Apple account and environment availability | `AS`, `M`, `U` | PR-APPLE-BOOTSTRAP | Physical Mac and iPhone |
| `SYNC-010` | Apple synchronization | The local pending queue publishes and consumes bounded bundles with truthful retryable status. | `SYNC-009`, `MODEL-001` | P3/W3.7 · sync integration | CloudKit quota and lifecycle behavior | `AS`, `M`, `U` | PR-CLOUDKIT-SYNC | Physical Mac and iPhone |
| `ONBOARDING-001` | Apple synchronization | A first installation completes purpose, privacy, iCloud, contextual permission, and target setup without a product account. | `SYNC-010`, `TARGETS-001`, `TARGETS-003`, `TARGETS-004` | P4/W4.1 · UX integration | No new ceremony beyond accepted Apple trust | `U`, `JM`, `IS` | PR-FIRST-INSTALL | Physical Mac and iPhone |
| `ONBOARDING-002` | Apple synchronization | A second installation joins the existing workspace, waits safely for a delayed key, and completes only its local mapping. | `ONBOARDING-001` | P4/W4.2 · UX integration | Apple-managed Keychain approval may remain external | `U`, `AS` | PR-SECOND-INSTALL | Physical Mac and iPhone |
| `SYNC-011` | Apple synchronization | Exact domains and semantic application policy converge while opaque selections remain local and status stays truthful. | `ONBOARDING-002`, `SYNC-010`, `TARGETS-001`, `TARGETS-002` | P4/W4.3 · sync integration | Conflict semantics accepted by `SYNC-001` | `M`, `U`, `AS` | PR-POLICY-SYNC | Physical Mac and iPhone |
| `SYNC-012` | Apple synchronization | Active-session intent, intentional early termination, and normal expiry converge without a delivery-time promise. | `SYNC-011`, `SESSION-002` | P4/W4.4 · sync integration | Offline and stale-session resolution | `M`, `U`, `AS` | PR-SESSION-SYNC | Physical Mac and iPhone |
| `MVP-001` | Completion | The controlled physical Mac-and-iPhone MVP acceptance flow passes without manual state repair. | `SYNC-012` | P5/W5.1 · acceptance | Maintainer acceptance of measured outcome | `V` | PR-MVP-ACCEPTANCE | Physical Mac and iPhone |
| `RELEASE-001` | Release readiness | A separate first-release review closes or explicitly blocks licensing, history, clean-clone, CI, security, privacy, signing, distribution, and support readiness. | `MVP-001` | Release/R1 · readiness | Maintainer release and distribution decision | `C`, `X`, `D` | PR-RELEASE-READINESS | Installed release candidates as applicable |

Phase 1 is fully serialized and ends with a mandatory holistic PR #1 review and
merge checkpoint. No other task starts before `CI-001` and that checkpoint,
even where a semantic dependency does not require CI. Tasks sharing a wave are
only concurrency candidates; rows sharing a conflict class serialize unless
their accepted execution plans prove disjoint exact files and frozen contracts.

Using unit task weights, the five provisional semantic dependency-critical
chains through MVP acceptance are represented compactly as:

```text
SECURITY-001 -> SYNC-001 -> SYNC-003
  -> { SYNC-004 | SYNC-005 | SYNC-006 | SYNC-007 | SYNC-008 }
  -> SYNC-009 -> SYNC-010 -> ONBOARDING-001 -> ONBOARDING-002
  -> SYNC-011 -> SYNC-012 -> MVP-001
```

The provisional resource-constrained gate path is the ordered wave barrier
chain below. It includes scheduling constraints without inventing semantic
task dependencies and is reproducible without duration estimates:

```text
P1/W1.1 -> W1.2 -> W1.3 -> W1.4 -> PR #1 merge
-> P2/W2.1 -> W2.2a -> W2.2b -> W2.2c -> W2.3 -> W2.4
-> W2.5 -> W2.6 -> W2.7 -> W2.8
-> P3/W3.1 -> W3.2 -> W3.3 -> W3.4 -> W3.5 -> W3.6 -> W3.7
-> P4/W4.1 -> W4.2 -> W4.3 -> W4.4 -> P5/W5.1
```

Final verification recomputes both representations from the declared rows and
reports every tied semantic branch. The release-only path starts at
`MVP-001 -> RELEASE-001`; `RELEASE-001` is required before a release but is not
part of the accepted MVP product-outcome critical path.

The candidate contains 36 work items: one manual gate, three individually
reviewed tasks grouped into PR #1, 31 later one-task pull requests through MVP
acceptance, and one separate release-readiness task. Named tasks own every ADR
0003 deferred choice before an implementation consumer. Each enabling task
names an immediate consumer, and recovery behavior stays with the task that
owns the failure instead of a late cross-cutting cleanup task. No portable
workspace, schedule, Android, Linux, product-account, analytics, stronger
early-end-friction, or total-key-loss-recovery task is present.

### Proposed changes

1. Add `docs/wiki/sources/mvp-interaction-prototype.md` as a focused source
   digest for the disposable prototype, including its pinned revision,
   maintainer-reviewed and browser-observed flow evidence, usability-evidence
   limits, non-dependency status, and link to the existing design topic. Route
   it from `docs/wiki/index.md` without creating a second design authority.
2. Add `docs/tasks/mvp-roadmap.md` as a `draft` Gate 6 candidate with epics, phases,
   waves, lanes, dependency graph, critical path, PR groups, shared write
   surfaces, manual gates, device checks, and acceptance procedure.
3. Add draft specifications for the 36 task IDs listed above under
   `docs/tasks/specifications/`. Keep each specification outcome-only and include
   direct dependencies, concurrency constraints, acceptance criteria, evidence
   categories, review applicability, decision gates, and accepted-authority
   references. Reserve, but do not pre-create, each future execution path under
   `docs/tasks/executions/` until its accepted task is authorized.
4. Give the serialized `D` decision tasks separate primary authorities:
   `docs/security/mvp-threat-model.md`,
   `docs/security/diagnostics-and-support-data.md`,
   `docs/decisions/0004-macos-helper-lifecycle-and-security.md`,
   `docs/product/macos-website-support.md`,
   `docs/decisions/0005-synchronization-format-and-cryptography.md`, and
   `docs/decisions/0006-apple-workspace-bootstrap.md`. These paths are planning
   write-surface assignments, not accepted decision contents.
5. Update `docs/tasks/first-mvp-pr-preparation-plan.md`,
   `docs/tasks/first-mvp-pr-preparation-todo.md`, and
   `docs/wiki/topics/mvp-open-questions.md` to route to the candidate roadmap
   while leaving Gate 6 incomplete and PR #1 blocked pending maintainer
   acceptance and Gate 7 completion.
6. Append one parseable entry to `docs/wiki/log.md`. Do not rewrite the
   prototype's existing experiment history or change
   `docs/wiki/topics/brand-and-design-baseline.md` unless review finds a broken
   source route.
7. Record plan review, completed-change review, corrections, and final evidence
   in this execution record.

### Test and verification strategy

- Validate every repository-local Markdown link after the last correction.
- Check that every roadmap task ID has exactly one draft specification and that
  every direct dependency resolves to a declared task.
- Check that the dependency graph is acyclic; every dependency lands in an
  earlier phase or wave; every task belongs to exactly one epic, phase, wave,
  lane, and integration group; every integration group is exactly one pull
  request or one explicit manual gate; and all declared parallel tasks avoid
  known serialized conflict classes. Recompute every tied unit-weight semantic
  critical chain from direct dependencies and the resource-constrained wave
  barrier chain from normalized conflict tokens and mandatory checkpoints.
- Check a roadmap coverage matrix against every accepted MVP capability, every
  row of the measurable physical-device outcome, ADR 0002 and ADR 0003
  obligations, and the PR #1 contract. Confirm each deferred ADR decision has
  one owning task before every consumer.
- Check that schedules, Android, Linux, portable workspaces, product accounts,
  analytics, stronger early-end friction, and total-key-loss recovery remain
  absent as implementing tasks and are stated as non-goals.
- Confirm `APPLE-001` completes before `FOUNDATION-001`, and `CI-001` completes
  before both PR #1 merge and the first concurrency-candidate wave.
- Review every task for one observable outcome achievable in one focused
  implementation session, every PR group for one coherent increment, and every
  specification for implementation-detail leakage.
- Search active authorities for stale Gate 6, PR #1, CI, prototype, and Gate 7
  statements.
- Run `git diff --check` after the last correction.
- Review the final diff for English-only authored content, secrets, personal
  paths, accidental prototype or `DESIGN.md` changes, and implementation detail
  leakage into task specifications.
- Obtain independent completed-change approval and rerun affected checks after
  every material correction.

### Risk and recovery

- Primary risk: a dependency graph that appears parallel but hides shared
  Gradle, Xcode, composition-root, schema, IPC, or public-contract writes.
  Mitigation: name conflict classes and serialize contract-establishing tasks
  before adapter consumers.
- Product risk: treating the prototype's reducer or fixtures as requirements.
  Mitigation: preserve `DESIGN.md` and accepted product authorities as primary,
  with a separate bounded prototype source digest.
- Planning risk: oversized tasks or foundation work without a named consumer.
  Mitigation: require one observable outcome and explicit downstream consumer
  for every enabling task; independent review checks task and PR sizing.
- Recovery: because this task changes documentation only, revert its scoped
  documentation commit if the maintainer rejects the candidate decomposition.

## Plan review

| Round | Reviewer | Verdict | Critical/Required findings | Resolution |
| --- | --- | --- | --- | --- |
| 1 | `/root/plan_review` | `request-changes` | Keep `PLANNING-001` draft; expose the candidate task graph; add coverage, scheduling, critical-path, CI, Gate 7, sizing, grouping, and implementation-detail invariants. | Status and acceptance provenance corrected; a 28-task candidate inventory, exact planned write surfaces, conflict classes, evidence needs, and expanded verification invariants were added. |
| 2 | `/root/plan_review` | `request-changes` | Expose phases, waves, lanes, and critical path; split four oversized tasks; remove artificial CI and macOS-domain dependencies; allow a manual integration group; keep release readiness outside the MVP critical path. | Added a provisional schedule and derived critical chain; split security/diagnostics, shared/platform bootstrap, first/second onboarding, and owner-local recovery; corrected semantic dependencies and integration-group rules; separated the release track. |
| 3 | `/root/plan_review` | `request-changes` | Do not run any task before CI except serialized PR #1 work; split macOS helper and browser/proxy decisions; make shared session behavior depend on semantic contracts rather than native mapping implementations. | Serialized all pre-CI work; moved governance and decision work after the PR #1 checkpoint; split the two macOS decision outcomes; corrected session dependencies; split Apple service adapters per platform; prepared escalation to a fresh reviewer. |
| 4 | `/root/fresh_governance_review` | `request-changes` | Normalize conflict tokens and make the serialized documentation order and semantic versus resource-constrained critical paths reproducible. | Replaced prose conflict values with normalized tokens; serialized all shared `D` decisions into subwaves; named their separate primary authorities; and defined reproducible semantic and wave-barrier critical-path representations. |
| 5 | `/root/fresh_governance_review` | `approve` | None. | Corrected plan approved on 2026-08-25. |

### Non-blocking plan findings

None recorded yet.

- **Approved before implementation:** yes; candidate document implementation
  proceeded under the accepted parent-gate exception after Round 5 approval
- **Approval evidence:** Round 5 fresh review approved the corrected plan on
  2026-08-25 with no Critical, Required, Recommended, or Optional findings.

## Implementation summary

- Added the accepted Gate 6 roadmap with seven epics, 36 uniquely scheduled work
  items, direct dependencies, conflict classes, integration groups, semantic
  critical paths, a resource-constrained barrier path, coverage, physical
  gates, and an explicit maintainer-acceptance procedure.
- Added 36 accepted revision 1 downstream task specifications. Every
  specification describes one outcome and records dependencies, stable
  concurrency constraints, acceptance criteria, evidence categories, review
  applicability, decision gates, and authority references without creating a
  future execution record.
- Added a pinned wiki source digest for the disposable MVP interaction
  prototype and routed it as bounded UX evidence without changing `DESIGN.md`
  or the prototype.
- Updated the active preparation plan, checklist, open-question synthesis,
  wiki index, and wiki log; Gate 6 is complete, Gate 7 is active, and
  production implementation remains blocked.
- Updated root `README.md` to route the accepted roadmap and root `AGENTS.md` to
  use the accepted Posato identity while retaining Blocker only as historical
  feasibility provenance.

## Deviations from the approved plan

- Completed-change review exposed that the approved candidate still assigned
  premature session and synchronization state to `MODEL-001`. The task was
  narrowed to the first exact-domain replica slice, `SYNC-001` no longer
  depends on it, and the semantic critical paths were recomputed. The 36-item
  count and accepted MVP coverage did not change.
- `TARGETS-001` gained the first UI dependency, state-holder, coroutine-
  ownership, and root-build conflict responsibility required by ADR 0003.
- `SYNC-003` gained explicit ownership of the macOS synchronization native-
  access decision, including the blocking ADR and Apple-resource amendment
  path if the accepted application graph is insufficient.
- Round 2 review also made `SYNC-003` freeze the interoperable Keychain-item
  and CloudKit-mailbox contracts before the iOS and macOS adapters may run in
  parallel. `SYNC-005` through `SYNC-008` now own platform implementation only;
  a shared-contract change reopens the decision or serializes the adapters.
- Roadmap-owned reviewer availability and integration order fields were added
  for every multi-task candidate wave.
- The final roadmap normalized the provisional `Production baseline` epic into
  `Preparation` and the provisional `Release track/R1` schedule label into
  `Release/R1`; the corrected candidate table above now matches the roadmap
  authority and this entry preserves the chronology.
- Round 1 review found stale active root guidance outside the approved write
  surface. `README.md` now routes review and acceptance of the existing Gate 6
  draft, and `AGENTS.md` describes Posato as the product home while preserving
  Blocker as historical provenance. Both documentation-only additions are
  included in final link, scope, and diff review.

## Code-review rounds

| Round | Reviewer | Verdict | Critical/Required findings | Resolution |
| --- | --- | --- | --- | --- |
| 1 | `/root/documentation_review` | `request-changes` | Refresh root routing; replace contradictory generic applicability matrices; assign the first UI dependency and state-holder decisions; include untracked files in whitespace checks; narrow `MODEL-001`; decide macOS synchronization native ownership without the enforcement helper; make iOS expiry evidence callback-opportunity based; and keep reviewer availability plus integration order in the roadmap. | Root and agent routing were corrected; all 36 matrices were rewritten task by task; `TARGETS-001`, `MODEL-001`, `SYNC-001`, `SYNC-003`, `SYNC-006`, `SYNC-008`, and `IOS-002` were corrected; all seven multi-task waves gained activation records; terminal blank lines were normalized; graph and critical paths were recomputed. |
| 2 | `/root/documentation_review` | `approve` | Re-review first found provisional epic and schedule labels that diverged from the roadmap, then found `README.md` and `AGENTS.md` changes outside the approved write surface, ambiguous pre-acceptance status and stale final-verification wording, and finally unfrozen shared Keychain-item and CloudKit-mailbox contracts across parallel adapters. No Critical or Required finding remained after correction. | The candidate table and recorded deviations were normalized; the write surface and implementation summary include root routing; the status note and final-verification introduction preserve candidate-review semantics; `SYNC-003` freezes both interoperable Apple contracts while `SYNC-005` through `SYNC-008` implement but cannot redefine them. The reviewer approved the corrected candidate snapshot on 2026-08-25. |
| 3 | `/root/documentation_review` | `approve` | The final execution-record audit found that the Round 2 row omitted earlier blocking chronology and that `AC-01` evidence conflated roadmap non-goals with capability coverage matrices. | The Round 2 chronology and the distinct planning-boundary and coverage-matrix evidence locations were restored; the same reviewer approved the corrected record on 2026-08-25. |
| 4 | `/root/documentation_review` | `approve` | The material Gate 6 acceptance-state update was not yet represented by an independent review before `done`, and `DoD-11` still described maintainer acceptance as a blocker after it was cleared. | The execution returned to `code-review`, this acceptance-state review was added, and `DoD-11` now records the cleared transition. The same reviewer approved the corrected acceptance state on 2026-08-25. |

### Non-blocking code-review findings

- Recommended stale `AGENTS.md` working-name language was corrected to the
  accepted Posato identity.
- Recommended prototype evidence routing was extended to `TARGETS-001`
  through `TARGETS-004`.
- Recommended dependency-graph wording now identifies the ASCII rendering as
  a selected-path summary while the task map remains the complete edge list.
- Recommended design-authority links were added to `MACOS-004` and `IOS-001`.
- Recommended historical working-name wording was replaced with current Posato
  terminology in the maintained open-question synthesis.

After three unsuccessful rounds, record escalation to a fresh reviewer or the
maintainer.

## Final verification

Fresh candidate verification passed after the last material correction, Round
2 completed-change approval, and Round 3 final-record approval. The maintainer
then accepted `PLANNING-001`, roadmap revision 1, and all 36 linked
specifications on 2026-08-25. Round 4 approved the material acceptance-state
update with no unresolved finding, permitting task completion.

### Acceptance-criteria evidence

| Criterion | Result | Evidence |
| --- | --- | --- |
| `AC-01` | pass | The roadmap planning boundaries preserve the accepted MVP non-goals; its capability and measurable-outcome matrices cover the accepted MVP, while the task map and schedule preserve the PR #1 boundary, `CI-001` deadline, manual Gate 7, physical acceptance, and separate release-readiness review. |
| `AC-02` | pass | All 36 roadmap items have unique accepted revision 1 specifications with matching dependencies, task-specific concurrency constraints, acceptance criteria, evidence categories, review applicability, and decision gates. |
| `AC-03` | pass | The roadmap records all seven epics, ordered phases and waves, lanes, the complete direct-dependency map, five tied semantic critical paths, the wave-barrier path, conflict tokens, activation records, integration groups, manual gates, and device evidence. |
| `AC-04` | pass | The pinned prototype digest records browser-observed flows and explicit evidence limits; the prototype and `DESIGN.md` have no task diff. |
| `AC-05` | pass | Active root, preparation, workflow, design, architecture, quality, and wiki routes agree that Gate 6 is accepted while downstream implementation remains unauthorized; Round 4 acceptance-state review approved with no unresolved finding. |

### Definition of Done evidence

| DoD item | Result | Evidence or justified N/A |
| --- | --- | --- |
| `DoD-01` | pass | The maintainer accepted unchanged revision 1 of `PLANNING-001`, the roadmap, and all 36 downstream specifications on 2026-08-25. |
| `DoD-02` | pass | `AC-01` through `AC-05` have fresh evidence above. |
| `DoD-03` | pass | Independent plan review reached approval in Round 5 before candidate document implementation began. |
| `DoD-04` | pass | Rounds 2 and 3 approved the candidate and its evidence; Round 4 approved the final acceptance-state update with no unresolved Critical or Required finding. |
| `DoD-05` | N/A | Documentation-only planning work introduces no runtime behavior or testable production code. |
| `DoD-06` | pass | All applicable documentation formatting, link, roadmap-schema, and graph checks pass; no repository aggregate code-quality entry point exists yet. |
| `DoD-07` | N/A | This change introduces no production runtime, simulator, device, visual, or accessibility surface; prototype inspection is provenance evidence, not product verification. |
| `DoD-08` | pass | The accepted roadmap preserves product, design, architecture, synchronization, quality, privacy, security, and provenance authority boundaries. |
| `DoD-09` | pass | Root routing, preparation state, execution record, wiki source, index, synthesis, and append-only log consistently record Gate 6 complete and Gate 7 active. |
| `DoD-10` | pass | The documentation-only diff is independently reviewable; tracked and untracked whitespace, secrets, personal-path, machine-state, prototype, and design-authority checks pass. |
| `DoD-11` | pass | Risks, justified `N/A` entries, deviations, review corrections, frozen-contract rule, rollback, cleared maintainer-acceptance transition, and unauthorized Gate 7 state are recorded. |
| `DoD-12` | pass | This record contains the approved acceptance-state review and completion verdict after the last material correction. |

### Verification applicability and evidence

| Check | Result | Evidence |
| --- | --- | --- |
| Aggregate local quality gate | N/A | No aggregate code-quality entry point exists before `QUALITY-001`; applicable documentation checks below pass. |
| Formatting | pass | Fresh `git diff --check` plus equivalent checks for every untracked file report no whitespace error. |
| ktlint | N/A | No Kotlin source or ktlint configuration changes. |
| Detekt and Compose Rules | N/A | No Kotlin source or Detekt configuration changes. |
| Compiler warnings | N/A | No compiled source or build configuration changes. |
| Unit tests | N/A | No runtime behavior introduced. |
| Contract tests | N/A | No executable contract introduced. |
| Integration tests | N/A | No production integration introduced. |
| Compose UI tests | N/A | No Compose implementation introduced. |
| Platform builds or simulator | N/A | Documentation-only planning change. |
| Physical-device checks | N/A | Physical evidence is assigned to future tasks; none is claimed by this planning change. |
| Visual checks | N/A | No design or production visual artifact changed. |
| Accessibility checks | N/A | No production interaction surface changed. |
| Manual checks | pass | Gate state, authority hierarchy, PR #1 boundary, prototype provenance, frozen Apple contracts, task sizing, and implementation-detail boundaries were inspected. |
| Documentation links and routing | pass | All repository Markdown file and anchor links resolve after the final correction; the required wiki route and append-only parseable log entry are present. |
| Roadmap graph and task-schema checks | pass | 36 unique task rows and specifications, 360 task-specific applicability rows, exact dependency agreement, an acyclic earlier-dependency graph, five tied 11-task MVP paths, seven conflict-free multi-task waves, seven activation records, and matching roadmap/execution tables pass. |

## Final verdict

- **Status:** done
- **Blocking findings remaining:** none
- **Non-blocking findings or accepted risks:** none unresolved
- **Completed:** 2026-08-25
