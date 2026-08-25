# Posato Task Workflow

## Status and authority

- **Status:** Accepted
- **Revision:** 2
- **Accepted:** 2026-08-25
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`

This file defines the smallest process needed to produce reliable Posato
software. Evidence should support an engineering decision, not exist merely to
confirm that another record exists.

The standing quality bar is the
[engineering quality contract](../docs/development/engineering-quality-contract.md).

## Sources of truth

| Concern | Authority |
| --- | --- |
| Workflow, review tiers, and artifact roles | This file |
| Standing quality and Definition of Done | Engineering quality contract |
| Preparation-gate state before PR #1 | `first-mvp-pr-preparation-todo.md` |
| MVP ordering, dependencies, waves, and integration groups | `mvp-roadmap.md` |
| Active task outcome and boundaries | Its brief under `specifications/` |
| Actual plan, result, review, checks, and blockers | Its execution record under `executions/` |
| Product, design, architecture, security, or privacy decision | Its accepted authority under `docs/` |
| Maintained synthesis and evidence routing | `docs/wiki/` |

The roadmap is enough to retain future work. A full brief is created just in
time, when a task becomes active. Do not write speculative briefs for every
roadmap row.

## Review tiers

Choose one tier before work starts. **Standard is the default.** A higher tier
needs a named reason; a lower tier must genuinely fit its definition.

### Trivial

Use for a typo, an unambiguous link fix, mechanical formatting, or another
change with no behavioral or durable-governance effect.

- No task brief or execution record.
- The author performs the relevant self-check.
- Escalate to Standard as soon as judgment, broader scope, or risk appears.

### Standard

Use for ordinary implementation, configuration, tests, and meaningful
documentation.

- Create a concise task brief and execution record.
- Implement after the plan is understood; a separate plan-review ceremony is
  not required.
- A different agent reviews the completed change once.

### High-risk

Use when a named risk justifies review before action: authentication or
authorization, cryptography, privacy or sensitive data, destructive migration,
signing or account-level resources, release operations, an exposed trust
boundary, or another explicitly identified irreversible or costly failure.

- Use the Standard artifacts.
- A different agent reviews the brief plan before implementation.
- A different agent reviews the completed change. The same reviewer may perform
  both reviews.

The implementer resolves Critical and Required findings. Recommended and
Optional findings are advisory and never expand the task automatically.
Reviewers should challenge needless scripts, abstraction layers, generalized
configuration, and evidence created only for its own sake.

## Task brief

Create `tasks/specifications/<task-id>.md` from the
[brief template](templates/task-specification.md) only when the task is about
to start.

A normal brief contains 20–40 lines of unique content:

- task ID, review tier and reason, dependencies, and integration group;
- one observable outcome;
- boundaries and non-goals;
- two to five acceptance criteria;
- only the verification that is expected to matter; and
- a real decision or blocker, if one exists.

Do not copy the global quality contract, a generic risk matrix, the roadmap, or
implementation detail into the brief. A roadmap stub may be clarified without
maintainer re-acceptance when the outcome and boundaries do not change.
Escalate product, architecture, privacy, scope, or irreversible-action choices
to the maintainer.

## Execution record

Create `tasks/executions/<task-id>.md` from the
[execution template](templates/execution-record.md) for Standard and High-risk
work. Keep only:

- status: `active`, `blocked`, or `done`;
- a short actual plan;
- the result and material deviations;
- Critical or Required review findings and their resolutions;
- checks actually run and their results; and
- blockers, accepted risks, or follow-up work that materially affects use.

Do not copy every Definition of Done item or test category. Do not add an
`N/A` matrix. A missing irrelevant row communicates nothing.

Several roadmap tasks in one pull request use one shared brief, execution
record, and completed-change review when they form one coherent increment.
For PR #1, `FOUNDATION-001`, `QUALITY-001`, and `CI-001` are milestones in
one execution and review cycle stored as `pr-1-production-skeleton.md` under
both artifact directories.

## Execution

Before implementation:

1. Confirm dependencies and accepted authorities.
2. Choose the review tier and write only the brief needed now.
3. Record the short plan and relevant checks.
4. For High-risk work, obtain plan approval from a different agent.

During implementation, keep the change scoped and record only material
deviations. If a missing human action, external resource, or durable decision
blocks progress, set the task to `blocked`, state the clearing condition, and
stop. Do not manufacture tooling to remain busy.

After implementation:

1. Run the checks that can detect a defect in this change.
2. Obtain the tier's completed-change review.
3. Resolve Critical and Required findings.
4. Rerun checks affected by the last correction.
5. Record the result, remaining blocker, or final evidence and mark the task
   `done` only when the outcome is met.

## Human and one-off work

Use a concise chat sequence or Markdown checklist for actions that only the
maintainer can perform. Guide one resource or decision at a time and wait for
the result.

Create a script, wizard, parser, or reusable configuration surface only when:

- the maintainer explicitly requests that artifact; or
- a named repeated consumer will use it often enough to justify maintenance.

An imagined future consumer is not enough.

## Parallel work and pull requests

Parallel implementation requires complete blocking dependencies, frozen shared
contracts, disjoint write surfaces, and an isolated branch and worktree for
each writer. Review-only agents may inspect concurrently. Shared Gradle, Xcode,
schema, navigation, dependency-injection, and public-contract ownership
normally serializes work.

The initial maximum is three implementation tasks, with two preferred when
integration risk is non-trivial. The roadmap records waves and integration
groups; reviewer availability is arranged when a wave is activated, not months
in advance.

One pull request should be one coherent review boundary. It may satisfy several
roadmap milestones through one brief, execution record, and review. CI must be
working before PR #1 merges or before the first parallel implementation wave,
whichever happens first.

## Wiki threshold

Update the wiki only for an accepted durable conclusion, a material reusable
correction or experiment result, or an open question that changes future
decisions. Task status, routine review comments, command output, and ordinary
verification stay in the execution record.

## Historical records

`GOVERNANCE-001` and `PLANNING-001` preserve how the original workflow and
Gate 6 roadmap were introduced. Their longer records are history, not templates
for new work. This revision is implemented by
[`GOVERNANCE-002`](specifications/governance-002-streamline-engineering-workflow.md).
