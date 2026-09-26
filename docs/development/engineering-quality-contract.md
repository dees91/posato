# Engineering Quality Contract

## Status and authority

- **Status:** Accepted
- **Revision:** 15
- **Accepted:** 2026-09-26
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`

This document defines the standing quality bar for Posato. The
[task workflow](../tasks/README.md) applies it proportionally: verification
must be capable of finding a defect in the actual change, not fill a generic
matrix.

Every increment requires applicable local verification, the local merge check
below, and its selected review tier before merge.

## Kotlin and Compose tools

- **ktlint** owns Kotlin formatting and mechanical style for repository-owned
  Kotlin and Kotlin build scripts.
- **Detekt** owns Kotlin static analysis.
- **Compose Rules** from `io.nlopez.compose.rules` run through Detekt, not
  through a duplicate ktlint integration.
- **The Kotlin compiler** owns warning reporting. Repository-owned source is
  warning-free where the selected toolchain exposes reliable enforcement.

`user-confirmed` (2026-08-31): correct the underlying source of a Detekt,
ktlint, compiler, or other quality-tool finding by default. Adding, broadening,
or modifying a suppression annotation, lint baseline, rule exclusion, disabled
rule, or compiler-warning exception requires explicit prior maintainer approval
after the cause, the case against correcting it, and the narrowest possible
scope are explained. Approval is case-specific. The aggregate quality gate
rejects Kotlin suppression annotations outside its exact allowlist of approved
pre-existing exceptions. It conservatively rejects the `Suppress` token in
Kotlin comments, strings, aliases, and examples as well, keeping the check
independent of annotation syntax and preventing alternate spellings from
bypassing approval.

`user-confirmed` (2026-08-27): repository-owned Compose UI uses Material 3
components exclusively. The Compose Rules Detekt `Material2` check is active
with no allowlist, so the aggregate quality gate rejects Material 2 source use.
The version catalog and production source do not retain a direct Material 2
dependency or import.

State-based Material 3 text fields use a composable-owned
`rememberTextFieldState`. Mutable live text is not a field in immutable
aggregate `UiState` and is not mirrored in a ViewModel flow; pass its current
value to the ViewModel only for submission. Business, validation, persistence,
failure, and editor-session facts remain in the combined screen state. Compose
Runtime state annotations and observation APIs may be used in the ViewModel,
but Compose Foundation text-input types may not.

`user-confirmed` (2026-08-27): the framework `TextFieldState` is a narrow
exception to the repository-owned redacted-default-string rule because its
final implementation includes live text in `toString()`. Keep it inside the
text-field composable; it must not be logged, diagnosed, persisted, passed to
the ViewModel, or included in another carrier's string representation.

`user-confirmed` (2026-08-27): each product screen has named, deterministic
common-code Compose previews backed by one `PreviewParameterProvider`. Keep the
provider and its synthetic states in a separate `*PreviewDataProvider.kt` file,
and keep exactly two preview functions in the screen file. Both preview
functions consume the same complete provider sequence and call the
state-and-callback rendering overload rather than a ViewModel, store, DI graph,
platform service, clock, random source, or live text state. Samples cover each
visually distinct screen branch. This is a review and task-acceptance
convention, not a naming-based static rule; leaf composables need previews only
when independently reused or visually complex.

`user-confirmed` (2026-08-27): repository-owned Kotlin and Kotlin build scripts
use a 150-character limit in ktlint, Detekt, and the Android Studio settings
published through `.editorconfig`. Expression bodies keep their first
expression on the declaration line when it fits. The repository-owned ktlint
rule `posato:rhs-on-assignment-line` applies the same layout to declarations,
assignments, named arguments, default values, and expression bodies when the
first right-hand-side line fits. It reports without autocorrect; comments
between `=` and the expression, values that need the next line, and multiline
raw strings remain valid. Raw strings follow ktlint's standard required line
break. The inverse standard ktlint multiline-expression rule stays disabled.
The repository-owned `posato:when-entry-arrow-on-condition-line` rule keeps a
`when` entry arrow on the final condition line when it fits. It reports without
autocorrect and accepts an intervening comment or a condition line that would
exceed the configured limit. Ktlint's conflicting declaration-site trailing-
comma rule is disabled; the call-site trailing-comma rule remains active.
Call-chain continuation remains formatter-compatible authored style; no
separate custom rule is justified for it.

PR #1 selects one compatible Kotlin, Compose Multiplatform, Gradle, Metro,
ktlint, Detekt, and Compose Rules set. Stable releases are required by default.
A prerelease build tool requires explicit maintainer acceptance after a
documented compatibility review when no stable release supports the accepted
compiler. Any exception must be pinned, limited to build-time quality tooling,
and include a removal trigger. Generated or third-party source may receive
narrow path exclusions. New production code starts without lint baselines,
global suppressions, or broad warning exceptions.

`user-confirmed` (2026-08-26): PR #1 may use Detekt 2.0.0-alpha.6 with Compose
Rules 0.6.4 because Detekt 1.23.8 is not compatible with Kotlin 2.4.10 metadata
and no stable Detekt 2 release exists. Replace the prerelease with the first
compatible stable Detekt 2 release and rerun the aggregate gate. This exception
does not authorize prerelease application or runtime dependencies.

The build must expose one documented aggregate local quality entry point.
Focused commands remain useful during iteration; the aggregate check runs when
the current change can affect its surface.

`user-confirmed` (2026-09-02): Detekt `ReturnCount` allows three returns and
excludes guard clauses so security-sensitive parsers fail closed with early
returns instead of nullable accumulators. This is a repository-wide threshold
change, not a suppression.

## Tests and runtime checks

`user-confirmed` (2026-09-26): highly prefer E2E tests as the sole testing
mechanism when they expose the relevant failures. Complex features use real
user flows through `posato-control`, with macOS in Tart VMs and iOS on the test
iPhone. This replaces the earlier default of adding unit tests whenever
product or presentation logic changes.

Never write unit tests after writing the implementation they cover. If
isolation is needed, first list the credible failure modes and explain why
E2E cannot reliably exercise them. Write and demonstrate the failing isolated
test before implementing the behavior or fixing the bug. Existing code can
receive a regression test before its repair; do not backfill tests that mirror
an already-written implementation.

Choose proof by the failure it can detect:

| Layer | Use when |
| --- | --- |
| E2E | Default for complex features and user-visible behavior; exercise the real flow and assert its effects. |
| Isolated unit or contract | A named important failure in state, policy, parsing, persistence, transport, IPC, cryptography, or another boundary cannot be reliably exposed by E2E. Record the failure inventory before coding. |
| Integration | Cooperation between components, processes, stores, or adapters has a distinct failure that existing E2E proof misses. |
| Static UI or golden | Do not add tests for static rendering, copy, theme mapping, or framework wiring. Golden testing requires a separate tool and target decision. |
| Platform build or simulator | A host, native target, extension, helper, entitlement, packaging, or source-set boundary changes. |
| Physical device | A simulator cannot represent the relevant entitlement, lifecycle, enforcement, or cross-device behavior. The test iPhone and Tart VMs cover it unattended; the host Mac is never a test device. |
| Manual inspection | Visual, accessibility, recovery, installation, or operating-system integration needs observation. An agent performs it with `posato-control` screenshots and snapshots, unattended by default (`AGENTS.md`). |

Tests protect important business, state, policy, validation, parsing, and
boundary behavior rather than private structure or framework wiring.
`user-confirmed` (2026-08-26): before post-MVP interface stabilization, do not
add Compose UI tests for static rendering, copy presence, theme-token mapping,
or application-shell wiring. UI changes use platform builds and proportionate
manual inspection. Golden testing, with Paparazzi named as a candidate, remains
a post-MVP decision and is not a current dependency or coverage claim.

End each E2E run with a verifiable, repeatable artifact. Record the tested
revision, target, initial state, exact command or scenario, expected and actual
outcome, and evidence directory. Capture the observable action and resulting
state, check the side effect, and verify cleanup as required by `verify-posato`.
Raw screenshots, logs, database evidence, and identifiers remain under ignored
`build/verification/`; tracked records contain only portable commands and
categorical results.

New important behavior needs regression-capable proof at the strongest useful
boundary. A business-logic bug fix demonstrates failure before the repair and
success after it unless the selected record path explains the concrete
automation limit. One contract has one primary test owner; an additional layer
must protect a distinct failure. Reject tests that merely restate source,
compare copied constants, or verify behavior implemented by the mock itself.

Before deleting an existing test, inspect its full assertions, production
owner and callers, history, and remaining coverage. Name the stronger keeper
and failure it detects, or explain why the deleted test protects no independent
contract. Preserve security, protocol, migration, and other boundary proof
that current E2E scenarios miss. Existing failures are possible product bugs,
not evidence that a test should be deleted.

Record only checks actually applicable and run; do not enumerate irrelevant
categories as `N/A`.

Run focused tests for each correction and the complete `./gradlew quality`
once after the last correction before pushing. Use `--rerun-tasks` only after
a build-configuration change.

## Review

The review tier is selected independently from the record path in the
[task workflow](../tasks/README.md):

- Trivial changes receive an author self-check.
- Standard changes receive one independent completed-change review.
- High-risk changes receive an independent brief plan review and an independent
  completed-change review.

Review covers correctness, simplicity, architecture, security, privacy,
performance, scope, tests, and the verification story. It explicitly asks
whether a script, abstraction, generalized configuration layer, or additional
artifact has a real consumer.

An independent review counts only with evidence: the diff lines it examined,
the tests it ran for the touched behavior, and the paths it checked. An
approval without those three elements does not complete the review tier.

| Severity | Meaning | Completion effect |
| --- | --- | --- |
| Critical | Data loss, security or privacy exposure, broken behavior, or a fundamental contract violation. | Blocks completion. |
| Required | A correctness, maintainability, architecture, scope, or verification defect. | Blocks completion. |
| Recommended | Valuable but unnecessary for this task's accepted outcome. | Advisory. |
| Optional | Preference, information, or low-value polish. | Advisory. |

Only a recorded maintainer decision may override a Critical or Required
finding. Recommended and Optional findings do not silently become work.

`user-confirmed` (2026-09-02): hosted `@codex review` is an optional final
signal, not a recursive gate. Request at most two passes per pull request; a
third needs a recorded maintainer decision. Before a repeat pass, fix the
whole class of each accepted finding across the diff. Accepted Critical or
Required findings receive local correction and affected verification; P2 and
lower findings are declined unless the maintainer explicitly accepts them.

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

The local aggregate quality gate covers repository-owned JVM, iOS, and macOS
surfaces. It includes `iosSwiftTest`,
which builds the Debug iOS host and executes the existing native Swift XCTest
suites on an isolated temporary Simulator. Physical-device-only cases remain
explicitly skipped there; Simulator success does not prove iCloud Keychain,
Screen Time authorization, or suspended-device expiry behavior.

`user-confirmed` (2026-09-07): local `quality` also includes `iosHostBuildCheck`
for unsigned Debug `iphoneos` and Release Simulator host compilation, preserving
the configuration coverage of the removed workflow. Kotlin device compilation
alone does not check device-only Swift branches. These checks require no physical
device and do not establish physical-device behavior.

Routine CI must not require personal signing identities, provisioning profiles,
application credentials, or private device data. The preview-only Android KMP
library uses Android SDK Platform 36 and Build Tools 36.0.0 to compile shared
preview code; it does not add an Android product host or emulator job.

`user-confirmed` (2026-09-07, latest decision): GitHub CI is disabled for now.
Remove the CI workflow from source and its required `Quality` status check
from `main`. This supersedes the earlier automatic, paused, manual-only, and
required-hosted-check policies. Restoration needs an explicit maintainer
decision; there is no automatic-restoration date.

Before merging, require successful local `./gradlew quality` after the last
correction, the selected review tier, and applicable native/device verification.
Record the tested revision and result in the PR and confirm that it still
represents the change being merged. Follow the local verification rules above
after later corrections. See the [local checklist](README.md#local-quality-and-merge-check).

Keep `main` protected by a PR requirement with zero mandatory approving reviews,
administrator enforcement, no bypass allowances, and force-push/deletion
restrictions. Required status checks and their strict up-to-date setting are
removed. GitHub cannot enforce local quality results; the maintainer or merging
agent owns that procedural gate. Administrators can still edit protection itself.
Disabled CI does not waive local failures or the independent-review process.

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
5. **Change health:** The diff is scoped, portable, secret-free, and
   reviewable. The selected record path truthfully states any result, blocker,
   or material accepted risk that is not evident from the sources and checks.

Do not copy these principles into every execution record or wiki-log entry.
Record only evidence that adds useful context for the task.

## Release-readiness boundary

Before the first release, Posato requires the separate readiness review in
`AGENTS.md`, including Git history, license and notices, clean-clone setup,
CI, security reporting, privacy, distribution, signing, support, and artifacts.
Daily quality does not imply release readiness.
