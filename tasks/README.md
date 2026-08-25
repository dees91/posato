# Posato Task Workflow

## Status and authority

- **Status:** Accepted
- **Accepted:** 2026-08-25
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`

This file is the process authority for planning and executing work in Posato.
Repository Markdown is the durable source of truth. External issues or pull
requests may mirror and link to these records, but they do not silently replace
them.

The standing quality bar is defined in the
[engineering quality contract](../docs/development/engineering-quality-contract.md).

## Source-of-truth hierarchy

Each concern has one authority:

| Concern | Authority |
| --- | --- |
| Work process, states, review protocol, and artifact roles | This file |
| Standing engineering quality and Definition of Done | `docs/development/engineering-quality-contract.md` |
| Preparation-gate state before PR #1 | `first-mvp-pr-preparation-todo.md` |
| MVP epic membership, phase, wave, parallel-lane, derived dependency graph, and pull-request ordering | Gate 6 roadmap, once created |
| Task outcome, scope, non-goals, direct blocking dependencies, stable concurrency constraints, and acceptance criteria | The accepted task specification under `tasks/specifications/` |
| Current task execution state, exact write surface, technical plan, reviews, corrections, and evidence | The task execution record under `tasks/executions/` |
| Accepted product, design, architecture, security, or development decision | Its named authority under `docs/` |
| Maintained synthesis and evidence routing | `docs/wiki/` |

The roadmap may summarize task state for readability, but the linked execution
record is authoritative. A task specification has a specification lifecycle;
it does not duplicate execution status.

The roadmap derives its graph from task-owned direct dependencies and owns
epic membership plus changeable scheduling decisions such as phase, wave,
lane, integration order, and pull-request grouping. A task specification may
link to its current roadmap location for navigation, but that reference is
non-authoritative. Exact files and write surfaces are discovered in technical
planning and belong only in the execution record.

## Planning hierarchy

- An **epic** groups tasks that produce one durable product or engineering
  capability.
- A **phase** is a dependency-ordered segment ending in an explicit checkpoint.
- A **wave** contains tasks eligible to run concurrently after their
  dependencies and shared contracts are complete.
- A **task** produces one independently verifiable outcome.
- A **pull request** is the review and integration boundary. It normally maps
  to one implementation task.

The Gate 6 roadmap must show the dependency graph, critical path, phases,
waves, parallel lanes, manual gates, and pull-request grouping. Sequence comes
from real dependencies, not from document order or epic numbering alone.

## Required task artifacts

Creating a draft task specification or Gate 6 roadmap under an explicitly
authorized parent gate is planning and does not require a recursive parent task
specification. The draft cannot authorize its own implementation or promote a
durable product, architecture, security, or governance decision. The maintainer
must accept the specification before execution can enter `ready`.

The Gate 5 workflow documentation is the bootstrap governance task for this
system. Its accepted specification and actual review history are preserved in
`tasks/specifications/governance-001-engineering-workflow.md` and
`tasks/executions/governance-001-engineering-workflow.md`.
`user-confirmed` (2026-08-25): GOVERNANCE-001 has one recorded transition exception
because its initial documentation drafts preceded formal approval of the
corrected plan. The exception does not apply to any subsequent task; the full
plan gate is mandatory after GOVERNANCE-001.

### Task specification

Create `tasks/specifications/<task-id>.md` from
[the task specification template](templates/task-specification.md).

The specification is an outcome contract. It contains context, scope,
non-goals, dependencies, acceptance criteria, and required evidence categories.
It must not prescribe exact files, class names, APIs, dependency versions,
algorithms, or implementation steps unless an already accepted authority makes
that detail a constraint.

Specification states are `draft`, `accepted`, and `superseded`. The file
records a numbered revision, acceptance date, accepting owner, and provenance.
Only an accepted revision may become ready for implementation. A scope,
dependency, concurrency-constraint, or acceptance change increments the
revision and returns it to `draft` until the maintainer accepts it. Acceptance
results are never checked off in the specification; they live in the execution
record.

### Execution record

Create `tasks/executions/<task-id>.md` from
[the execution record template](templates/execution-record.md) when work on an
accepted task is authorized.

The execution record is the only home for:

- the current execution state;
- repository findings and assumptions;
- exact technical implementation plan;
- plan-review findings, corrections, and verdict;
- implementation summary and deviations from the approved plan;
- code-review findings and resolution rounds;
- fresh acceptance-criteria and Definition of Done evidence; and
- the final verdict.

Keep it concise and auditable. Record verdicts, actionable findings,
corrections, commands, and results rather than raw agent transcripts or hidden
reasoning.

## Task readiness

A task may enter `ready` only when:

- its specification is accepted;
- its outcome is small enough for one focused implementation session;
- acceptance criteria are observable and testable;
- product, architecture, security, privacy, or design decisions needed to
  define the outcome are already accepted;
- all blocking dependencies are done;
- the affected verification categories are known; and
- its write surface does not conflict with another authorized parallel task.

An unresolved implementation choice belongs in the technical plan. An
unresolved product or durable architecture choice blocks readiness and is
escalated to the maintainer.

## Execution state machine

```text
ready → planning ↔ plan-review → implementation → code-review ↔ corrections
                                                           ↓
                                                final-verification → done

any active state → blocked
blocked → the prior state, or ready, after its clearing condition is met
```

The execution record uses exactly these states:

- `blocked`
- `ready`
- `planning`
- `plan-review`
- `implementation`
- `code-review`
- `corrections`
- `final-verification`
- `done`

Any state may become `blocked` when a missing decision, dependency, authority,
environment, or external resource prevents safe progress. The record names the
blocker and the condition that clears it.

## Plan gate

The assigned implementation agent prepares the technical plan in the execution
record. The plan includes:

- current repository evidence and applicable authorities;
- assumptions and unresolved questions;
- exact files, contracts, dependencies, and migrations expected to change;
- test-first or other verification strategy;
- exact local verification commands available at that point;
- security, privacy, compatibility, and provenance considerations; and
- risk, rollback, recovery, or migration handling when applicable.

A different agent reviews the plan before implementation. The reviewer checks
specification compliance, dependency order, scope, architecture, testability,
risk, and unnecessary implementation prescription. Implementation begins only
after all Critical and Required plan findings are resolved and the reviewer
records approval.

## Implementation and code-review gate

The implementing agent follows the approved plan, keeps the change scoped, and
records material deviations. A different agent then reviews the completed
change against the task specification, approved plan, repository authorities,
tests, and the five quality axes: correctness, readability, architecture,
security, and performance.

Review findings use the severities defined in the
[quality contract](../docs/development/engineering-quality-contract.md):
Critical, Required, Recommended, and Optional. Critical and Required findings
block completion. Recommended and Optional findings are recorded but do not
silently expand the task.

The implementer resolves blocking findings and the same reviewer normally
checks the corrections. Every correction invalidates earlier final-verification
evidence for affected checks. After three unsuccessful review rounds, work is
escalated to a fresh reviewer or maintainer adjudication instead of looping
indefinitely. An override is valid only when the maintainer records the reason.

## Final verification and completion

After the last material correction, the implementing agent runs fresh
verification and records:

- one evidence entry for every task acceptance criterion;
- `pass` evidence for every unconditional Definition of Done item and a
  justified `N/A` only for an applicability-dependent test or runtime item;
- applicable tests, builds, static analysis, formatting, compiler-warning,
  runtime, simulator, device, visual, accessibility, and manual results;
- unresolved non-blocking findings and accepted risks; and
- the independent code-review verdict.

The agent must not infer success from an earlier run, an unchanged-looking
diff, or another agent's statement. A task becomes `done` only when no Critical
or Required finding remains and all acceptance criteria and applicable
Definition of Done items pass.

## Parallel work

Parallel implementation is allowed only when:

- blocking dependencies are done;
- shared contracts are accepted and frozen for the wave;
- tasks have disjoint write surfaces or explicit file ownership;
- each implementation uses an isolated branch and Git worktree; and
- integration order and reviewer availability are recorded in the roadmap.

Root Gradle configuration, the version catalog, Xcode project files, shared
schemas, navigation or dependency-injection composition roots, and cross-task
public contracts normally serialize work. Review-only agents may inspect a
working tree concurrently because they do not write.

The initial maximum is three concurrent implementation tasks, with two
preferred when integration risk is non-trivial. Increasing the limit requires
evidence that review latency and merge conflicts remain controlled.

## Pull-request mapping

One task normally produces one pull request. Several tasks may share a pull
request only when the Gate 6 roadmap explicitly groups them into one coherent
increment and each task independently reaches `done` before the pull request's
holistic review.

The pull request description links the task specification and execution
record, summarizes user-visible or engineering impact, and lists final
verification. Pull-request review does not replace task-level plan and code
review.

CI configuration is a separate Gate 6 task. Under the accepted quality
contract it must complete before PR #1 merges or before the first parallel
implementation wave starts, whichever happens first.

## Agent and repository authority

Applicable agent skills may guide execution, including
`android-compose-engineering` for Kotlin and Compose Multiplatform work. Skills,
generator defaults, and external workflow tools are advisory. This repository's
accepted task specification, quality contract, design authority, ADRs, and
security or product documents always take precedence.
