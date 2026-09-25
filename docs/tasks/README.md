# Posato Task Workflow

## Status and authority

- **Status:** Accepted
- **Revision:** 6
- **Accepted:** 2026-09-04
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`

This file defines the smallest process needed to produce reliable Posato
software. Evidence should support an engineering decision, not exist merely to
confirm that another record exists.

The standing quality bar is the
[engineering quality contract](../development/engineering-quality-contract.md).

## Sources of truth

| Concern | Authority |
| --- | --- |
| Workflow, record paths, review tiers, and artifact roles | This file |
| Standing quality and Definition of Done | Engineering quality contract |
| Preparation-gate state before PR #1 | `first-mvp-pr-preparation-todo.md` |
| MVP ordering, dependencies, waves, and integration groups (complete; history) | `mvp-roadmap.md` |
| Releases after 1.0.0: themes, rows, waves, backlog, and idea intake | `release-roadmap.md` |
| Lightweight change outcome | One concise entry in `docs/wiki/log.md` |
| Recorded task outcome and boundaries | Its brief under `specifications/` |
| Recorded task plan, result, checks, and blockers | Its execution record under `executions/` |
| Product, design, architecture, security, or privacy decision | Its accepted authority under `docs/` |
| Maintained synthesis and evidence routing | `docs/wiki/` |

The roadmap is enough to retain future work. A full brief is created just in
time only when the recorded-task path below requires one. Do not write
speculative briefs for every roadmap row.

## Record paths

Choose how work is recorded separately from how deeply it is reviewed. A task
ID, dependency, build target, or configuration change does not by itself
justify a brief and execution record.

### Lightweight change

Use this path when the changed sources, focused verification, any affected
authority, and one concise wiki-log entry fully explain the work. It may cover
local and reversible implementation, dependency, build-tooling, CI,
documentation, or configuration changes that do not need a separate
coordination or risk narrative.

- Do not create a task brief or execution record.
- Append one outcome-focused entry to `docs/wiki/log.md`; do not copy command
  output or review bookkeeping into it.
- Update an accepted authority when the work changes a durable decision. That
  authority remains distinct from a task record.
- Pure typo, formatting, link, and bookkeeping corrections may omit the wiki
  entry.

Use the recorded-task path when the work introduces important product, state,
policy, validation, persistence, synchronization, platform-boundary, or public
contract behavior; is High-risk; needs multi-step or parallel coordination;
has material blockers, accepted risks, or deviations that the diff and
authorities cannot explain; or the maintainer explicitly requests a formal
record. When uncertain, use the recorded-task path.

### Recorded task

Create one concise brief and execution record for work that meets the threshold
above. Several coherent milestones may share them. The records must add useful
task-specific context rather than duplicate the diff, an accepted authority,
the wiki, CI output, or a pull-request conversation.

## Review tiers

Choose one tier before work starts. **Standard is the default.** A higher tier
needs a named reason; a lower tier must genuinely fit its definition.

### Trivial

Use for a typo, an unambiguous link fix, mechanical formatting, or a narrow,
local, reversible change whose correctness is established by a focused
self-check.

- The author performs the relevant self-check.
- Escalate to Standard as soon as judgment, broader scope, or risk appears.

### Standard

Use when a completed change benefits from an independent correctness,
integration, or maintainability check. Dependency integration, CI behavior,
new build targets, and changes spanning several targets normally qualify even
when they use the lightweight record path.

- Implement after the plan is understood; a separate plan-review ceremony is
  not required.
- A different agent reviews the completed change once.

### High-risk

Use when a named risk justifies review before action: authentication or
authorization, cryptography, privacy or sensitive data, destructive migration,
signing or account-level resources, release operations, an exposed trust
boundary, or another explicitly identified irreversible or costly failure.

- Use the recorded-task path.
- A different agent reviews the brief plan before implementation.
- A different agent reviews the completed change. The same reviewer may perform
  both reviews.

The implementer resolves Critical and Required findings. Recommended and
Optional findings are advisory and never expand the task automatically.
Reviewers should challenge needless scripts, abstraction layers, generalized
configuration, and evidence created only for its own sake.

## Task brief

For recorded tasks, create `docs/tasks/specifications/<task-id>.md` from the
[brief template](templates/task-specification.md) only when the task is about
to start. Lightweight changes never create one.

A normal brief contains 20–40 lines of unique content:

- task ID, review tier and reason, dependencies, and integration group;
- one observable outcome;
- boundaries and non-goals;
- two to five acceptance criteria;
- only the verification that is expected to matter, planned as unattended
  runs (macOS in a Tart VM, iOS on the test iPhone; `AGENTS.md`); and
- a real decision or blocker, if one exists.

Do not copy the global quality contract, a generic risk matrix, the roadmap, or
implementation detail into the brief. A roadmap stub may be clarified without
maintainer re-acceptance when the outcome and boundaries do not change.
Escalate product, architecture, privacy, scope, or irreversible-action choices
to the maintainer.

## Execution record

For recorded tasks, create `docs/tasks/executions/<task-id>.md` from the
[execution template](templates/execution-record.md). Keep only:

- status: `active`, `blocked`, or `done`;
- a short actual plan;
- the result and material deviations;
- Critical or Required review findings and their resolutions;
- checks actually run and their results; and
- blockers, accepted risks, or follow-up work that materially affects use.

Do not copy every Definition of Done item or test category. Do not add an
`N/A` matrix. A missing irrelevant row communicates nothing.

Keep the record at or under 120 lines. Summarize hosted-review findings as one
table of finding classes with counts, commit ranges, and decisions instead of a
paragraph per correction. Update the record once at closeout, not once per
commit.

Several roadmap tasks in one pull request use one shared brief, execution
record, and completed-change review when they form one coherent increment.
For PR #1, `FOUNDATION-001`, `QUALITY-001`, and `CI-001` are milestones in
one execution and review cycle stored as `pr-1-production-skeleton.md` under
both artifact directories.

## Execution

Before implementation:

1. Confirm dependencies and accepted authorities.
2. Choose the record path and review tier independently.
3. For a recorded task, write only the brief and short execution plan needed
   now.
4. For High-risk work, obtain plan approval from a different agent.

During implementation, keep the change scoped and record only material
deviations. If a missing human action, external resource, or durable decision
blocks progress, escalate lightweight work to the recorded-task path when that
context needs a durable task record. Set a recorded task to `blocked`, state
the clearing condition, and stop. Do not manufacture tooling to remain busy.

After implementation:

1. Run the checks that can detect a defect in this change.
2. Complete the selected review tier.
3. Resolve Critical and Required findings.
4. Rerun checks affected by the last correction.
5. Close the selected record: update the execution record for a recorded task,
   or append the lightweight outcome to the wiki log.

## Human and one-off work

Use a concise chat sequence or Markdown checklist for actions that only the
maintainer can perform. Guide one resource or decision at a time and wait for
the result.

Create a script, wizard, parser, or reusable configuration surface only when:

- the maintainer explicitly requests that artifact; or
- a named repeated consumer will use it often enough to justify maintenance.

An imagined future consumer is not enough.

## Pull request feedback loop

After a pull-request push, hosted CI and review may run in parallel. Human
inline comments are the primary feedback channel. The local implementation
agent reads unresolved GitHub threads, evaluates each finding against the task
and repository authorities, applies accepted Critical and Required fixes,
runs affected verification, commits and pushes the correction, and replies
with concise evidence. The reviewer decides whether its thread is resolved.

Use a manually requested `@codex review` as an additional independent pass
when the repository is connected to Codex Cloud. Request it at most twice per
pull request. The first pass follows implementation, applicable local
verification, any required independent completed-change review, and any
required versioned task records. The second pass follows correction of every
accepted finding and its whole class across the diff. A third pass requires a
recorded maintainer decision in the execution record. Do not request hosted
review for documentation-only changes.

Map hosted P0 and P1 findings to Critical and Required. After each hosted
pass, the implementing agent returns a triage table with one row per finding:
finding, class, decision (accept, decline, or defer), rule reference, and
cost, plus a recommendation to merge or run one more pass. The maintainer
decides on that table, not on individual comments, and nothing is implemented
before that decision. Apply accepted fixes together with their whole class,
run affected verification, obtain any focused local re-review needed by the
tier, and reply with concise evidence. P2 and lower findings are advisory and
declined by default; accepting one is an explicit maintainer scope decision.
Declined findings and findings in the classes excluded by `AGENTS.md` receive
a one-sentence reply citing the rule.

Batch accepted corrections into one push where practical. Complete the
selected record path, durable wiki updates, and other versioned closeout before
the final substantive push. Later hosted-review replies and routine bookkeeping
stay in the pull-request conversation; do not create a repository commit solely
to record them. A substantive correction still updates any affected versioned
authority or required task record in the same correction push. A correction
inside an open pull request takes the review tier of its own risk, Standard by
default; a plan review applies only when the correction itself is High-risk.

Documentation still follows the proportional review tiers: routine status and
bookkeeping use a Trivial self-check, while meaningful documentation receives
its required human or local independent review without a hosted GitHub Codex
pass. Automatic AI reviews remain disabled until representative pull requests
show that manual substantive reviews add useful signal without recurring
noise. Cloud review remains read-only for this workflow; the local
implementation agent owns corrections.

Neither AI review nor green CI authorizes merge. The required proportional
review tier still applies, no unresolved Critical or Required finding may
remain, and the maintainer owns the final merge decision.

## Parallel work and pull requests

Parallel implementation requires complete blocking dependencies, frozen shared
contracts, disjoint write surfaces, and an isolated branch and worktree for
each writer. Review-only agents may inspect concurrently. Shared Gradle, Xcode,
schema, navigation, dependency-injection, and public-contract ownership
normally serializes work.

A new worktree is not usable until it is provisioned. `local.properties` is
ignored, so `git worktree add` does not carry it over, and without it the
driver cannot reach a device or stage a signed package. Copy the whole file
from the main checkout, never a subset, and build the driver once:

```shell
cp ../posato/local.properties .
./gradlew :posato-control:installDist
```

The main checkout is the canonical copy and carries every `posato.` key,
including `posato.macos.signingIdentity` and
`posato.macos.syncProvisioningProfile`. A partial copy yields a worktree that
works until a task reaches the macOS application picker or the CloudKit and
Keychain paths, which is late; `doctor -t desktop` names what is missing.
Revision 6 adds this requirement after `QUALITY-004` found that a fresh
worktree fails deep inside a feature recipe rather than at setup.

The initial maximum is three implementation tasks, with two preferred when
integration risk is non-trivial. The roadmap records waves and integration
groups; reviewer availability is arranged when a wave is activated, not months
in advance.

One pull request should be one coherent review boundary. It may satisfy several
roadmap milestones through one brief, execution record, and review. CI must be
working before PR #1 merges or before the first parallel implementation wave,
whichever happens first.

## Wiki threshold

Every substantive lightweight change receives one concise wiki-log entry.
Update a topic or source page only for an accepted durable conclusion, a
material reusable correction or experiment result, or an open question that
changes future decisions. Task status, routine review comments, command output,
and ordinary verification stay in the execution record when one exists, or in
the pull-request conversation otherwise.

## Historical records

`GOVERNANCE-001` and `PLANNING-001` preserve how the original workflow and
Gate 6 roadmap were introduced. Their longer records are history, not templates
for new work. The proportional workflow was implemented by
[`GOVERNANCE-002`](specifications/governance-002-streamline-engineering-workflow.md),
with its pull-request feedback loop bounded by
[`GOVERNANCE-003`](specifications/governance-003-bound-review-and-ci-repetition.md).
The `PREVIEW-001` and `CI-002` briefs and execution records are also retained as
history. Under revision 5, equivalent work uses the lightweight record path,
with independent completed-change review selected separately when warranted by
its build, dependency, or CI risk.
