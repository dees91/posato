# Agent Instructions

## Scope and language

These instructions apply to the entire repository. This repository is the
product home for the Posato Apple MVP. **Blocker** identifies the former
working name and preserved feasibility provenance only. Completed feasibility
work lives in a separate reference repository and is not production code.

Write all agent-authored repository content in English, including source,
tests, plans, documentation, wiki pages, identifiers, comments, and commit
messages. Preserve another language only in a user-supplied immutable artifact
when its inclusion has been explicitly approved.

## Mandatory wiki routing

Before reusable product, platform, privacy, architecture, security, or
experiment work:

1. Read [`docs/wiki/README.md`](docs/wiki/README.md).
2. Read [`docs/wiki/index.md`](docs/wiki/index.md).
3. Open the relevant topic and source pages before drawing a conclusion.

Lightweight developer tooling and maintenance, as defined by
[`docs/tasks/README.md`](docs/tasks/README.md), read only the changed sources
and directly relevant authorities unless they also produce one of the reusable
conclusions above.

Before brand, product-design, UI, or application-shell work, also read
[`DESIGN.md`](DESIGN.md). It is the accepted design authority; the related wiki
pages retain synthesis, evidence, flow diagrams, and proposal history rather
than a competing design contract.

After work produces an accepted durable conclusion, a material reusable
correction or experiment result, or an open question that changes future
decisions, update the relevant page under `docs/wiki/topics/` or
`docs/wiki/sources/`. Update `docs/wiki/index.md` only when page routing
changes, and append one parseable entry to `docs/wiki/log.md` using
`## [YYYY-MM-DD] type | Short title`. Routine task status, review bookkeeping,
and verification output do not require wiki updates.

A substantive lightweight change with no durable conclusion appends only one
concise entry to `docs/wiki/log.md`. Pure typo, formatting, link, and
bookkeeping corrections may omit it. A pull request appends at most one
wiki-log entry, in its closeout commit, never one per correction commit.

The wiki is maintained synthesis, not decision authority. Do not promote an
inference or PoC choice into an ADR, product requirement, plan, or
source file without explicit user acceptance. Durable accepted decisions live
under `docs/decisions/`, `docs/product/`, or `docs/security/` as appropriate.

## Mandatory work execution

Before work starts, choose the record path and review tier independently using
[`docs/tasks/README.md`](docs/tasks/README.md).

For a lightweight change, inspect the changed sources and directly relevant
authorities. Do not create a task brief or execution record. For a recorded
task, read:

1. [`docs/development/engineering-quality-contract.md`](docs/development/engineering-quality-contract.md);
2. [`docs/tasks/README.md`](docs/tasks/README.md); and
3. the active task brief, or its accepted roadmap stub before the brief is
   created, and the linked authorities.

Gate 6 keeps short roadmap stubs. When an item uses the recorded-task path,
create its brief just before work starts; do not pre-expand every future task.
A brief records only the outcome, boundaries, dependencies, acceptance
criteria, and applicable verification. Its execution record contains only the
actual plan, result, blockers, review, and checks used for that change.

Review depth does not determine whether task records exist. Trivial changes use
a self-check. Standard changes require one independent completed-change review,
including lightweight dependency, build-target, or CI changes when their risk
warrants it. High-risk changes use the recorded-task path and additionally
require a brief independent plan review before implementation. Resolve all
Critical and Required findings and rerun affected verification after the last
correction. Recommended and Optional findings do not expand scope
automatically.

A correction inside an open pull request takes the review tier of its own
risk, Standard by default; a plan review applies only when the correction
itself is High-risk. It adds no per-commit execution-record paragraph or
wiki-log entry; the record receives one closeout update before the final
substantive push.

For a one-off manual task, guide the maintainer with a short checklist or chat
instructions. Do not create a script, parser, wizard, or configuration layer
unless the maintainer explicitly requests it or a named repeated consumer
needs it. When human action is the blocker, stop and explain the next action.

Parallel implementing agents require isolated Git worktrees and the dependency
and write-surface conditions in `docs/tasks/README.md`.

## Application verification

Before claiming that a change to either application works, drive the real
application through the repository's verification driver
(`tools/posato-control`, documented in `tools/posato-control/README.md`)
following the tool-neutral skill at
[`.agents/skills/verify-posato/SKILL.md`](.agents/skills/verify-posato/SKILL.md)
and its feature map. The skill lives under `.agents/skills/` so that any
agent can use it; `.claude/skills/verify-posato` is a symbolic link to it for
tools that only scan their own directory, and the copy under
`.agents/skills/` is the maintained one. Cite
the run directory under the ignored `build/verification/` as evidence and
keep screenshots, logs, and identifiers out of tracked files.

Verification is unattended by default (`user-confirmed` 2026-09-24). Every
task verifies its change without the maintainer's help:

- the macOS application runs only in Tart virtual machines, through
  `posato-control` with `--vm primary|peer`, and every system dialog is
  answered with `posato-control vm prompt` (administrator password, helper
  approval, privacy panes, Gatekeeper, iCloud renewal);
- iOS changes run on the dedicated test iPhone with `-t device`, including
  Screen Time consent (`fixtures/scenarios/screen-time-consent.json`) and the
  application picker;
- the one-time environment is described in
  [`docs/development/unattended-verification.md`](docs/development/unattended-verification.md).

Never run, install, update, reset, uninstall, or replace a Posato build on
the maintainer's own Mac, not even as an exception: the Posato installed
there is the maintainer's real copy and its data, helper, Keychain items,
and iCloud state are not test fixtures. Build on the host, then run
the build in a VM; `posato-control` refuses desktop commands outside a
virtual machine except `build`, `doctor`, and `artifacts`.

Do not ask the maintainer to click, type, or approve anything during
verification. An attended iOS step is allowed only for an extraordinary
reason the environment cannot cover, such as an iOS version or hardware
feature the test iPhone lacks. Name that reason in the task's execution
record or pull request before asking. When a missing piece of the
environment blocks a run, report it as a blocker with the exact failing
command instead of switching to an attended step. Extend `posato-control`
when a new dialog or flow cannot be driven yet.

## Apple provisioning and signing

Development certificates, device registrations (Macs, iPhones, and the
verification VMs), and development provisioning profiles come from
`tools/posato-provisioning`, documented in
[`docs/development/apple-provisioning.md`](docs/development/apple-provisioning.md).
It uses the App Store Connect team key configured in the ignored
`local.properties`. Use it instead of the Apple Developer portal or ad hoc
App Store Connect API calls; when it lacks an operation a task needs, extend
the tool rather than scripting around it.

## Suppression policy

Fix the underlying source of Detekt, ktlint, compiler, and other quality-tool
findings by default. Do not add, broaden, or modify a suppression annotation,
lint baseline, rule exclusion, disabled rule, or compiler-warning exception to
make a check pass without explicit prior maintainer approval.

Before requesting an exception, explain the finding's cause, why correcting it
is less appropriate than suppression in that specific case, and the narrowest
possible scope. Approval is case-specific and does not authorize similar
exceptions elsewhere. The aggregate quality gate enforces the exact approved
Kotlin suppression allowlist. To keep that gate syntax-independent, do not put
the `Suppress` token in Kotlin comments, strings, aliases, or examples.

## Code Review Rules

- Report only actionable defects introduced by the reviewed diff. Map P0 to
  `Critical` and P1 to `Required`; do not turn advisory preferences or
  pre-existing out-of-scope work into blocking findings.
- Require automated tests only for important business, state, policy,
  validation, parsing, and boundary behavior. Do not request tests for static
  UI rendering, copy, theme mapping, or framework wiring.
- Flag credentials, personal paths, wholesale PoC reuse, and violations of the
  accepted product, architecture, security, privacy, or process boundaries.
  `.research/blocker` must remain read-only evidence and an optional checkout.
- Request hosted `@codex review` at most twice per pull request. The first
  pass follows implementation, applicable local verification, any required
  independent completed-change review, and any required versioned task
  records. The second pass follows correction of every accepted finding and
  its whole class across the diff. A third pass requires a recorded maintainer
  decision. Do not request it for documentation-only changes.
- After each hosted pass, return a triage table (finding, class, decision,
  rule, cost) with a merge or one-more-pass recommendation, and implement
  nothing until the maintainer decides on that table.
- Treat hosted P2 or lower findings as advisory and decline them by default.
  Accepting one is an explicit maintainer scope decision; it never expands the
  task, blocks merge, or triggers another pass on its own.
- Decline findings that need an actor with write access to the app-private
  database file or schema or a compromised operating system (accepted limit
  `R-02`), defensive checks that duplicate schema constraints, memory zeroing
  beyond owned key material and plaintext buffers whose clearing removes the
  last in-memory copy (`R-05`), and per-type `toString()` redaction reports
  for a family already covered by the enumerated redaction test. Reply with
  the rule reference.

## Feasibility research reference

Maintainer checkouts may provide the ignored path `.research/blocker`, pointing
to the repository used for the synchronization PoC and enforcement spike. When
present, agents may read its code, tests, reports, maintained wiki, and Git
history to verify an exact feasibility claim or evaluate reuse. Search that
path explicitly because tools may not follow links automatically.

Treat `.research/blocker` as read-only evidence. Do not import experiment
traces, one-off runners, credentials, signing configuration, local paths,
captures, or machine-specific state into product sources or documentation. The
repository and its wiki must remain understandable and buildable when
`.research/blocker` is absent.

PoC code may inform a new implementation, test, or contract, but it may not be
imported wholesale. Re-establish ownership, API shape, tests, security, and
production quality in this repository and record meaningful provenance in the
change description.

## Provenance labels

Use these labels when provenance changes meaning:

- `observed`: directly verified in current code or a controlled experiment;
- `user-confirmed`: explicitly accepted or corrected by the user;
- `source-claim`: asserted by a source but not independently verified here;
- `inferred`: reasoned from evidence;
- `hypothesis`: proposed and awaiting a test;
- `open`: unresolved;
- `superseded`: retained history replaced by a newer conclusion.

Prefer current user corrections, current reproducible observations,
authoritative platform documentation, final PoC and spike result artifacts,
and then older synthesis. Keep contradictions and evidence limits visible.

## Repository safety and privacy

- Do not add credentials, tokens, private keys, provisioning profiles, device
  identifiers, personal paths, private URLs, raw captures, or unrelated
  conversations.
- Use synthetic fixtures and portable configuration examples.
- Do not record browsing history or allowed navigation events in product
  diagnostics unless a later explicit privacy decision changes that boundary.
- Do not claim production readiness, platform coverage, privacy, security, or
  distribution eligibility beyond verified evidence.
- Before the first release, require a separate readiness review covering Git
  history, license, notices, clean-clone setup, CI, security reporting, and
  privacy.

## MVP implementation boundary

Production code starts only after the MVP scope, product identity, minimal
design baseline, architecture baseline, quality gates, and required Apple
identifiers are explicitly accepted. The intended direction is Kotlin-first
Kotlin Multiplatform with Compose Multiplatform and narrow semantic platform
boundaries. The accepted initial modules, targets, Metro ownership, helper and
extension process boundaries, and deployment baseline are authoritative in
[ADR 0003](docs/decisions/0003-mvp-application-architecture-baseline.md).
Exact deferred dependency versions and helper implementation details remain
decisions for their named pull requests rather than assumptions inherited from
the PoC.

The MVP preparation route in
[`docs/tasks/first-mvp-pr-preparation-plan.md`](docs/tasks/first-mvp-pr-preparation-plan.md)
and the [MVP roadmap](docs/tasks/mvp-roadmap.md) are complete and retained
as history. Releases after 1.0.0 are planned in
[`docs/tasks/release-roadmap.md`](docs/tasks/release-roadmap.md). Start a row
only when the maintainer names it and its release is composed, following that
document's intake and activation rules; new ideas enter the wiki idea queue
and the roadmap backlog rather than a release. The roadmap has a public
GitHub Projects mirror described in that document; update the mirror in the
same step as any row change.

Use the `android-compose-engineering` skill when it is available for
agent-authored Kotlin or Compose Multiplatform implementation. The repository's
accepted architecture, design, quality, and task contracts remain authoritative
when the skill is absent or conflicts with repository-specific decisions.
