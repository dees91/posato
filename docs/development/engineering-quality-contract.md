# Engineering Quality Contract

## Status and authority

- **Status:** Accepted
- **Accepted:** 2026-08-25
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`

This document is the standing engineering quality authority for Posato. It
defines the minimum bar for every production change. Task decomposition and
execution follow [the repository task workflow](../../tasks/README.md).
Task-specific acceptance criteria supplement this contract; they do not replace
it.

The contract defines required outcomes. Exact plugin versions, Gradle wiring,
commands, test-framework APIs, and target-specific implementation details are
selected in the reviewed plan for the first task that needs them.

## Scope

The contract applies to application code, tests, build logic, configuration,
documentation, generated-project imports, native targets, helper processes,
extensions, and reviewed reuse from feasibility work. A requirement may be
marked `N/A` only when the execution record explains why it cannot affect the
change.

Production implementation remains blocked by the preparation checkpoint in
[the first MVP PR checklist](../../tasks/first-mvp-pr-preparation-todo.md).

## Kotlin and Compose quality tools

Posato uses these non-overlapping responsibilities:

- **ktlint** is the Kotlin formatting and mechanical style authority. The
  Gradle integration must cover repository-owned Kotlin and Kotlin build
  scripts for every introduced Kotlin target.
- **Detekt** is the Kotlin static-analysis authority. Its configured analysis
  must cover every owned source set that the selected Detekt and Kotlin
  versions can analyze reliably.
- **Compose Rules** from `io.nlopez.compose.rules` are loaded through Detekt.
  The same Compose rules are not also loaded through ktlint because duplicate
  findings create two competing configuration surfaces.
- **The Kotlin compiler** is the warning authority. Repository-owned source
  must be warning-free. Supported warning-as-error controls are enabled where
  they behave consistently for the selected toolchain.

The first production-skeleton task must select and compatibility-check one
coherent set of stable Kotlin, Compose Multiplatform, Gradle, Metro, ktlint,
Detekt, and Compose Rules versions. A tool is not upgraded in isolation when
its parser, compiler, Gradle, or ruleset compatibility can change the result.

Generated and third-party source may be excluded only with the narrowest
path-based rule that identifies its ownership. Repository-owned generated
outputs are still reviewed for provenance and reproducibility. Unavoidable
toolchain warnings require a documented, narrowly scoped exception with its
cause, owner, and removal condition.

New production code starts without ktlint or Detekt baselines. A baseline,
global suppression, disabled rule, or warning exception requires an explicit
task-level justification and review. Suppressions in source must identify the
exact rule and be limited to the smallest declaration that requires them.

The build must expose one documented local quality entry point that aggregates
the checks applicable to the current repository. Focused commands may exist
for fast iteration, but they do not replace the final aggregate verification.
Exact task names are decided with the production toolchain in PR #1.

Primary integration references:

- [ktlint Gradle plugin](https://github.com/JLLeitschuh/ktlint-gradle)
- [Detekt Gradle integration](https://detekt.dev/docs/gettingstarted/gradle/)
- [Detekt Kotlin Multiplatform analysis](https://detekt.dev/docs/gettingstarted/type-resolution/)
- [Compose Rules with Detekt](https://mrmans0n.github.io/compose-rules/detekt/)

## Compiler-warning policy

- A change must not introduce a warning in repository-owned source.
- The final local verification fails on owned-source warnings wherever the
  selected compiler and target expose a reliable warning-as-error mechanism.
- A warning emitted only by generated code, an external tool, or a platform
  toolchain is not silently ignored. The execution record identifies it,
  establishes that the change did not introduce an owned-source defect, and
  records the narrow exception or upstream tracking reference.
- Warning output is reviewed after the final change, not inferred from a prior
  successful build.

## Test and runtime-verification layers

Each task declares which layers apply before implementation. Every layer is
either evidenced as `pass` or marked `N/A` with a reason in the execution
record.

| Layer | Required when |
| --- | --- |
| Unit | Product, presentation, parsing, validation, state, or policy behavior changes. |
| Contract | A semantic platform, persistence, transport, IPC, or cryptographic boundary changes. |
| Integration | Multiple owned components, processes, storage layers, or platform adapters must cooperate. |
| Compose UI | User-visible state, interaction, semantics, focus, or accessibility behavior changes and can be exercised deterministically. |
| Platform build or simulator | An iOS, macOS, JVM, extension, helper, packaging, entitlement, or host boundary changes. |
| Physical device | Platform behavior cannot be represented faithfully by local tests or a simulator, including relevant enforcement, entitlement, lifecycle, or cross-device behavior. |
| Manual inspection | Visual, accessibility, recovery, operating-system integration, or installation behavior needs human observation. |

Tests assert observable behavior rather than private implementation structure.
New behavior is covered by a test that would fail without the change whenever
the behavior can be automated. Bug fixes require a regression test unless the
execution record demonstrates why automation is not currently possible.

Documentation-only work may mark Kotlin analysis, compilation, runtime, and
application tests `N/A`; its link, consistency, formatting, and independent
review checks remain mandatory.

## Change and pull-request boundaries

- One implementation task normally maps to one pull request.
- A pull request may contain multiple tasks only when they form one coherent
  reviewable increment, each task completes its own plan, review, acceptance,
  and Definition of Done cycle, and the pull request receives an additional
  holistic review.
- A task should deliver one observable outcome in one focused implementation
  session. Split work that combines independent outcomes, crosses unrelated
  subsystems, or cannot be reviewed confidently as one change.
- Refactoring, dependency upgrades, generated-project import, and new product
  behavior remain separate changes unless their coupling is necessary and
  explained in the reviewed plan.
- Every commit is scoped, reviewable, and written in English. Generated or
  mechanical formatting changes are not mixed with unrelated behavior.

## Review gates

Every non-trivial task follows the plan and implementation reviews defined in
[the task workflow](../../tasks/README.md). Generated wizard output, build
logic, documentation contracts, and dependency changes are not exceptions.

Review covers correctness, readability, architecture, security, performance,
scope, tests, and the verification story. Findings use these severities:

| Severity | Meaning | Completion effect |
| --- | --- | --- |
| Critical | Data loss, security, privacy, broken behavior, or fundamental contract violation. | Blocks completion. |
| Required | A correctness, maintainability, architecture, scope, or verification defect. | Blocks completion. |
| Recommended | A valuable improvement that is not required for this task to meet its contract. | Does not block when recorded. |
| Optional | Preference, information, or low-value polish. | Does not block. |

Only an explicit maintainer decision may override a Critical or Required
finding. The reason and resulting follow-up obligation must be recorded rather
than silently reclassifying the finding.

## Dependency and provenance review

Before adding or upgrading a dependency, the task plan records:

- the named production need and why the current stack is insufficient;
- authoritative compatibility evidence for the selected Kotlin and targets;
- maintenance status, release notes, license, and known security concerns;
- relevant transitive and lockfile or version-catalog changes; and
- the verification that would detect an incompatible upgrade.

Dependencies enter with the smallest task that has a production consumer.
Related compatibility-locked tooling may be reviewed as one set; unrelated
bulk upgrades are split.

PoC or spike material is evidence, not production ownership. Reuse requires a
fresh review of provenance, license, API shape, architecture, security,
privacy, tests, and repository portability. Machine-specific paths, traces,
credentials, signing state, and experiment-only runners never enter product
artifacts.

## Security and privacy applicability

Every task declares whether it affects untrusted input, authentication,
authorization, secrets, personal data, diagnostics, storage, cryptography,
native IPC, entitlements, or external services. Affected work receives focused
security and privacy review and updates the relevant authority under
`docs/security/` or `docs/decisions/` after maintainer acceptance.

This contract does not claim that a production threat model, privacy policy,
public license audit, or release-readiness review is complete. Those remain
separate accepted gates. Existing repository rules prohibiting browsing-history
and allowed-navigation diagnostics remain in force.

## Continuous-integration boundary

`user-confirmed` (2026-08-25): Gate 5 records the CI contract but does not
configure CI. Until CI exists, every task must produce fresh local evidence for
the complete applicable verification matrix.

CI is a separate Gate 6 implementation task. It must be complete before the
earlier of:

1. merging PR #1; or
2. starting the first parallel implementation wave.

CI must run the same repository-owned aggregate quality gate used locally and
cover the JVM/desktop and iOS/macOS build or test surfaces introduced by the
change. Routine CI must not require personal signing identities, provisioning
profiles, application credentials, or private device data. Exact jobs,
commands, runner images, caches, and credential-free build variants are
implementation details selected in the reviewed CI task plan.

## Definition of Done

This checklist is defined once here. Each execution record contains task-local
evidence for every item. Unconditional governance and review items must pass;
only applicability-dependent automated-test and runtime-verification items may
use a justified `N/A`. A maintainer override is recorded explicitly and is not
represented as `N/A`.

| ID | Standing requirement |
| --- | --- |
| DoD-01 | The accepted task specification is unchanged or every scope change has explicit maintainer acceptance. |
| DoD-02 | Every task-specific acceptance criterion has fresh, traceable evidence. |
| DoD-03 | An independent agent approved the implementation plan before implementation began. |
| DoD-04 | An independent agent reviewed the completed change and no unresolved Critical or Required finding remains. |
| DoD-05 | Applicable automated tests pass after the final correction, and new behavior has appropriate regression coverage. |
| DoD-06 | Every applicable local quality check passes after the final correction; once the repository aggregate quality entry point exists, it also passes. |
| DoD-07 | Applicable runtime, simulator, physical-device, visual, accessibility, and manual checks pass. |
| DoD-08 | The change follows accepted architecture, design, security, privacy, dependency, and provenance authorities. |
| DoD-09 | Documentation, task records, wiki synthesis, and durable decisions are current without contradictory active guidance. |
| DoD-10 | The diff is scoped, contains no secrets or machine-specific state, passes repository formatting checks, and is independently reviewable. |
| DoD-11 | Risks, intentional `N/A` entries, non-blocking findings, plan deviations, and rollback or recovery needs are recorded. |
| DoD-12 | The execution record contains the final verdict and evidence obtained after the last material change. |

A task is `done` only when all acceptance criteria and all applicable Definition
of Done items pass. Passing tests alone is not completion.

## Release-readiness boundary

Before the first release, Posato still requires the separate readiness review
defined in `AGENTS.md`, including Git history, license and notices, clean-clone
setup, CI, security reporting, privacy, distribution, signing, and support
claims. Gate 5 establishes daily engineering discipline; it does not grant a
production- or release-readiness claim.
