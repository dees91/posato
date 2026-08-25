# Engineering Quality Contract

## Status and authority

- **Status:** Accepted
- **Revision:** 2
- **Accepted:** 2026-08-25
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`

This document defines the standing quality bar for Posato. The
[task workflow](../../tasks/README.md) applies it proportionally: verification
must be capable of finding a defect in the actual change, not fill a generic
matrix.

Production implementation remains blocked by the readiness checkpoint in the
[first MVP PR checklist](../../tasks/first-mvp-pr-preparation-todo.md).

## Kotlin and Compose tools

- **ktlint** owns Kotlin formatting and mechanical style for repository-owned
  Kotlin and Kotlin build scripts.
- **Detekt** owns Kotlin static analysis.
- **Compose Rules** from `io.nlopez.compose.rules` run through Detekt, not
  through a duplicate ktlint integration.
- **The Kotlin compiler** owns warning reporting. Repository-owned source is
  warning-free where the selected toolchain exposes reliable enforcement.

PR #1 selects one compatible stable Kotlin, Compose Multiplatform, Gradle,
Metro, ktlint, Detekt, and Compose Rules set. Generated or third-party source
may receive narrow path exclusions. New production code starts without lint
baselines, global suppressions, or broad warning exceptions.

The build must expose one documented aggregate local quality entry point.
Focused commands remain useful during iteration; the aggregate check runs when
the current change can affect its surface.

## Tests and runtime checks

Use the layers that can reveal a failure introduced by the task:

| Layer | Use when |
| --- | --- |
| Unit | Product, presentation, parsing, validation, state, or policy behavior changes. |
| Contract | A platform, persistence, transport, IPC, or cryptographic boundary changes. |
| Integration | Several owned components, processes, stores, or adapters must cooperate. |
| Compose UI | Visible state, interaction, semantics, focus, or accessibility changes. |
| Platform build or simulator | A host, native target, extension, helper, entitlement, packaging, or source-set boundary changes. |
| Physical device | A simulator cannot represent the relevant entitlement, lifecycle, enforcement, or cross-device behavior. |
| Manual inspection | Visual, accessibility, recovery, installation, or operating-system integration needs human observation. |

Tests assert observable behavior rather than private structure. New behavior
gets a regression-capable automated test when practical. A bug fix gets a
regression test unless the execution record explains the concrete automation
limit. Record only checks actually applicable and run; do not enumerate
irrelevant categories as `N/A`.

## Review

The review tier comes from [the task workflow](../../tasks/README.md):

- Trivial changes receive an author self-check.
- Standard changes receive one independent completed-change review.
- High-risk changes receive an independent brief plan review and an independent
  completed-change review.

Review covers correctness, simplicity, architecture, security, privacy,
performance, scope, tests, and the verification story. It explicitly asks
whether a script, abstraction, generalized configuration layer, or additional
artifact has a real consumer.

| Severity | Meaning | Completion effect |
| --- | --- | --- |
| Critical | Data loss, security or privacy exposure, broken behavior, or a fundamental contract violation. | Blocks completion. |
| Required | A correctness, maintainability, architecture, scope, or verification defect. | Blocks completion. |
| Recommended | Valuable but unnecessary for this task's accepted outcome. | Advisory. |
| Optional | Preference, information, or low-value polish. | Advisory. |

Only a recorded maintainer decision may override a Critical or Required
finding. Recommended and Optional findings do not silently become work.

## Dependencies and provenance

Before adding or upgrading a dependency, review the production need,
compatibility, maintenance, release notes, license, security posture,
transitive changes, and a verification capable of detecting incompatibility.
Add dependencies with their first real consumer; split unrelated upgrades.

PoC and spike material remains evidence. Any reused idea or fragment receives
fresh ownership, API, architecture, security, privacy, license, test, and
portability review. Do not import machine paths, traces, credentials, signing
state, captures, or experiment-only runners.

## Security and privacy

Apply focused review whenever work touches untrusted input, authentication,
authorization, secrets, personal data, diagnostics, storage, cryptography,
native IPC, entitlements, signing, or an external service. Update a durable
security, privacy, or architecture authority only after maintainer acceptance.

Posato still has no production-readiness claim. The repository rule against
browsing-history and allowed-navigation diagnostics remains in force.

## Continuous integration

CI is implemented with PR #1 and must work before PR #1 merges or before the
first parallel implementation wave, whichever comes first. It runs the
repository-owned aggregate quality gate and the credential-free JVM, iOS, and
macOS surfaces introduced by that increment.

Routine CI must not require personal signing identities, provisioning profiles,
application credentials, or private device data. Exact jobs and commands are
chosen in the shared PR #1 execution cycle.

## Definition of Done

A change is done when these five principles hold:

1. **Outcome:** The accepted outcome and boundaries are met; any material scope
   or durable-decision change has explicit maintainer acceptance.
2. **Evidence:** Applicable tests and quality or runtime checks pass after the
   last material correction and can detect the changed behavior or contract.
3. **Review:** The required review tier is complete and no unresolved Critical
   or Required finding remains.
4. **Authorities:** Affected architecture, design, security, privacy,
   dependency, provenance, documentation, and wiki authorities are consistent.
5. **Change health:** The diff is scoped, portable, secret-free, reviewable,
   and its execution record truthfully states the result, blockers, and
   material accepted risks.

Do not copy these principles into every execution record. Record the evidence
that demonstrates them for the task.

## Release-readiness boundary

Before the first release, Posato requires the separate readiness review in
`AGENTS.md`, including Git history, license and notices, clean-clone setup,
CI, security reporting, privacy, distribution, signing, support, and artifacts.
Daily quality does not imply release readiness.
