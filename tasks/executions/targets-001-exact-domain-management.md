# Execution: `TARGETS-001`

- **Brief:**
  [`../specifications/targets-001-exact-domain-management.md`](../specifications/targets-001-exact-domain-management.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** `Codex`
- **Reviewer:** `independent Codex reviewer`
- **Branch:** `targets-001-exact-domains`
- **Updated:** `2026-08-28`

## Plan

1. Add the minimum compatible IDNA and ViewModel dependencies, then make
   `ExactDomain` own bounded user-input canonicalization and strict stored
   A-label restoration with common cross-runtime tests.
2. Add one shared ViewModel that combines private state flows, reads the store
   while its UI state is consumed, and owns add, edit, and remove commands
   without a use-case wrapper, hidden scope, or navigation framework.
3. Keep the editable domain in one composable-owned `rememberTextFieldState`,
   pass its current text only at submission, migrate the screen to state-based
   Material 3 components, establish one root reusable `PosatoTheme`, and enable
   the existing static Material 2 ban.
4. Replace the static shell with one state-driven exact-domain screen using
   platform semantics and resources, then verify persistence and interaction
   on macOS and the iOS Simulator.
5. Run focused and aggregate checks, complete the independent security and
   quality review, resolve blocking findings, and record only actual evidence.
6. Add the maintainer-requested ktlint rule for the accepted assignment layout
   after repeated authored drift, using the public ruleset provider as the
   test seam and no autocorrect or general formatting framework.
7. Address PR review by moving accepted opt-ins into Gradle, separating UI
   state and submission preparation from ViewModel orchestration, and enforcing
   the accepted `when` entry arrow layout with a focused ktlint rule.
8. Migrate the completed slice from repository-wide layer packages to the
   accepted feature-layered TARGETS package, move shared database and theme
   ownership to named core packages, and preserve behavior and module shape.
9. Address accepted hosted review findings test-first by rejecting WHATWG
   number-ending hosts and reconciling an active editor with the snapshot
   reloaded after a revision conflict.

## Material 3 and text-state correction

- **Provenance:** `user-confirmed` on 2026-08-27.
- The mutable domain draft is not aggregate render state. It is one
  `rememberTextFieldState` owned inside the text-field composable; the ViewModel
  receives its current value only on submission. Validation, editing,
  persistence, failure, and editor-session facts remain in the combined
  immutable `uiState`.
- TARGETS-001 is the first reviewed product screen and therefore establishes
  the minimum reusable UI foundation now. The boundary is deliberately limited
  to the root theme, Material 3, and semantic platform colors and typography;
  reusable feature components and new geometry tokens require repeated need.
- The maintainer accepted a narrow privacy clarification after the reviewer
  verified that the final framework `TextFieldState.toString()` includes its
  live text: repository-owned carriers and diagnostics remain redacted, and
  the composable-owned state must never be logged, diagnosed, persisted, or
  passed to the ViewModel.
- The maintainer then corrected the layer boundary: Compose Foundation text
  input types are prohibited in the ViewModel even though Compose Runtime
  state annotations and observation APIs remain permitted there.
- **Follow-up high-risk plan review:** approved after the maintainer's
  composable-ownership correction; no Critical or Required findings remain.

## High-risk plan review

- **Verdict:** `approved after correction`
- **Critical or Required findings:** Raw Unicode input was not explicitly
  capped before UTS-46 processing, so mapping or ignored characters could
  collapse an unbounded input below the canonical DNS output limit.
- **Resolution:** The brief now rejects input over 1,024 UTF-16 code units
  before trimming or UTS-46 and requires a collapsing-input regression test;
  the reviewer confirmed that no Critical or Required findings remain.
- **Package-migration follow-up:** The first review required explicit
  maintainer acceptance of the package map and direct UI-to-store dependency.
  The maintainer selected feature-layered packages, the
  `app.posato.feature.targets` feature root, named `app.posato.core` shared
  capabilities, migration in this pull request, and retention of direct store
  injection. The independent re-review then approved the plan with no remaining
  Critical or Required findings.

## Result

- Exact-domain parsing now applies the pre-UTS-46 input bound, deterministic
  Unicode canonicalization, strict DNS/A-label validation, and redacted value
  rendering.
- The shared ViewModel reads storage only while `uiState` is collected,
  combines private policy, editor, and submission flows into persistent
  aggregate state, and preserves the last valid snapshot across validation,
  storage, conflict, and cancellation paths. Live field text remains solely in
  composable-owned `rememberTextFieldState`; an immutable editor-session value
  controls intentional recreation after edit, cancel, or successful storage.
- The shared screen now exposes loading, unavailable, empty, content, editing,
  saving, and typed failure states on both hosts without adding navigation or
  another application layer.
- One application-root `PosatoTheme` adapts platform semantic colors and text
  styles to stable Material 3 `1.9.0`. The direct Material 2 dependency and all
  Material 2 source imports are removed, and Compose Rules rejects their
  reintroduction through Detekt.
- The packaged macOS application now includes the JDK `java.sql` runtime module
  required by SQLDelight's JDBC driver. This is a package-only correction based
  on both the reproduced launch failure and the preserved PoC evidence; it does
  not add a database abstraction or change the store contract.
- One repository-owned ktlint rule now rejects a right-hand-side expression
  starting below `=` when its first physical line fits the configured limit.
  It uses ktlint's syntax tree, has no autocorrect, and is wired into the
  aggregate quality gate, including its own module, without introducing a
  general formatting framework. Multiline raw strings retain the only layout
  compatible with standard ktlint.
- PR review follow-up keeps experimental API opt-ins in the `shared` module's
  compiler configuration and removes source annotations. UI state and pure
  submission preparation now live in focused adjacent files while the
  ViewModel retains lifecycle-aware Flow and persistence orchestration.
- `posato:when-entry-arrow-on-condition-line` rejects an arrow below the final
  `when` condition when it fits, without autocorrect. Comments and over-limit
  conditions remain valid. The conflicting standard declaration-site trailing-
  comma rule is disabled while call-site trailing-comma enforcement remains.
- TARGETS production and test code now mirrors
  `app.posato.feature.targets.{domain,data,ui}`. SQLDelight generation and
  platform drivers live in `app.posato.core.database`, while the application
  theme and its platform adaptations live in `app.posato.core.designsystem`.
  The migration leaves the store contract, direct ViewModel injection, schema,
  application shell, Metro roots, and single-module boundary unchanged.
- Exact-domain validation now rejects a final decimal or lowercase `0x`
  hexadecimal label under WHATWG's number-ending rule, including mixed legacy
  IPv4 spellings, while preserving hexadecimal-looking non-final labels.
- A successful policy reload now clears an edit only when its target is absent
  from the loaded snapshot. It increments the editor session so the
  composable-owned field is recreated; an edit whose target remains is kept.

## Completed-change review

- **Verdict:** `changes required; code finding resolved, verification finding open`
- **Critical or Required findings:** A private data carrier for a pending policy
  replacement generated a default string containing canonical domains. AC-04
  also remains incomplete because this environment cannot inject the required
  macOS keyboard and iOS touch interactions.
- **Resolution:** The carrier is now a non-data class with an explicitly
  redacted string representation, and affected lint, static analysis, JVM, and
  iOS Simulator tests pass. The task remains in verification until the
  maintainer completes the short host interaction checks or explicitly accepts
  the limitation. No other Critical, Required, Recommended, or Optional
  findings were reported.
- **Material 3 and text-state follow-up verdict:** `changes required for
  verification only`. The reviewer found no code defects and no other Critical,
  Required, Recommended, or Optional findings. The only Required finding is the
  final post-migration macOS/iOS keyboard, touch, and accessibility check; the
  task remains in verification until the maintainer completes it or explicitly
  accepts the limitation.
- **Desktop packaging follow-up verdict:** `approved`. The reviewer found no
  Critical, Required, Recommended, or Optional defect in the minimal
  `java.sql` module correction or its task and wiki evidence. Overall
  TARGETS-001 remains in verification only for the physical-iPhone interaction
  and accessibility check.
- **Final verification resolution:** `user-confirmed` on 2026-08-27. The
  maintainer completed the final add, edit, cancel, save, remove, scrolling,
  and large-text interaction check on the relaunched physical iPhone. The last
  verification-only Required finding is resolved.
- **Assignment-formatting follow-up verdict:** `approved after correction`.
  The reviewer found that excluding `:quality-rules` from its own ruleset left
  a repository-owned enforcement gap and that this execution record omitted
  the follow-up evidence. The module now loads its built ruleset without a
  Gradle cycle, its owned source follows the rule, and the evidence below is
  current. Self-enforcement also exposed standard ktlint's required multiline
  raw-string layout; a narrow tested exception resolves that tool conflict
  without weakening ordinary assignments. The reviewer confirmed that no
  Critical or Required finding remains.
- **PR-feedback follow-up verdict:** `approved`. The independent local review
  found no actionable correctness, readability, architecture, security, or
  performance defect. Its sandbox could not reacquire the Gradle wrapper cache
  lock, so the implementer's successful focused and aggregate reruns below are
  the verification evidence.
- **Feature-package follow-up verdict:** `approved`. The independent completed-
  change review found the package declarations, imports, tests, SQLDelight
  generation, platform composition roots, and durable records consistent, with
  no stale references or actionable defects. Its sandbox could not access the
  Gradle wrapper cache; the successful focused and aggregate implementer runs
  below remain the verification evidence.
- **Hosted-feedback plan review:** `approved after correction`. The independent
  reviewer accepted the WHATWG final-label predicate and paired conflict tests.
  Its sole Required process finding was that the unresolved advisory thread
  must be answered with evidence but left for its reviewer to resolve.
- **Hosted review findings:** The Required IPv4 finding is addressed by the
  WHATWG number-ending rejection. The maintainer-accepted advisory stale-editor
  finding is addressed by snapshot reconciliation without adding a new stream,
  use case, or public API.
- **Hosted-feedback completed-change verdict:** `approved`. The independent
  review found no actionable correctness, readability, architecture, security,
  or performance defect. Its sandbox could not access the Gradle wrapper cache;
  the successful implementer runs below remain the verification evidence.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Hosted-feedback red/green regression | `pass` | The focused JVM run first failed only for accepted numeric-ending hosts and the stale editor after conflict reload. After the minimal fixes, the complete JVM and iOS Simulator policy and ViewModel suites passed. |
| Hosted-feedback aggregate gate | `pass` | `./gradlew quality --rerun-tasks` executed all 92 tasks successfully, including lint, Detekt, JVM and iOS Simulator tests, both iOS compilation targets, migration verification, and the desktop distributable. |
| Hosted-feedback iOS host build | `pass` | The credential-free generic iOS Simulator `xcodebuild` completed with `BUILD SUCCEEDED`. |
| Dependency and primary-source review | `pass` | Kuri 0.1.0 publishes JVM, iOS arm64, and iOS Simulator arm64 variants, uses MIT, has no runtime dependency beyond Kotlin stdlib, and has no OSV entry; AndroidX 2.10.0 and coroutines Swing follow current JetBrains ViewModel guidance. Stable Compose Multiplatform Material 3 1.9.0 is Apache-2.0, has no OSV entry, supplies the state-based API, and resolves shared Compose core artifacts to the existing 1.10.3 graph. |
| Custom-rule dependency and source review | `pass` | The repository-owned ruleset uses the existing ktlint 1.8.0 APIs. Test-only SLF4J Simple 2.0.17 matches ktlint's pin, is MIT-licensed, and returned no OSV result. The resolved ruleset and test runtime graphs were inspected. |
| `./gradlew :quality-rules:ktlintCheck :quality-rules:test :quality-rules:detekt --rerun-tasks` | `pass` | Ten public-rule-engine tests cover declarations, assignments, named arguments, defaults, expression bodies, same-line values, comments, over-limit values, standard-formatted multiline raw strings, and fitting single-line raw strings. The module statically enforces its own rule through its built JAR without a task cycle. |
| Assignment-formatting negative probe | `pass` | A temporary repository-owned shared source with its value below `=` made `:shared:ktlintCommonMainSourceSetCheck` fail with `posato:rhs-on-assignment-line`; removing the probe restored the passing check. |
| When-entry formatting negative probe | `pass` | A temporary shared source with `->` below a fitting final condition made `:shared:ktlintCommonMainSourceSetCheck` fail with `posato:when-entry-arrow-on-condition-line`; removing the probe restored the passing check. |
| PR-feedback focused verification | `pass` | Repository-owned rule tests, ktlint, Detekt, JVM tests, iOS Simulator tests, and Android main compilation all reran successfully after the opt-in, file-ownership, and formatting corrections. |
| PR-feedback aggregate gate | `pass` | `./gradlew quality --rerun-tasks` executed and passed all 92 tasks. |
| Feature-package focused verification | `pass` | Ktlint, Detekt, JVM tests, iOS Simulator tests, SQLDelight migration verification, and Android preview-target compilation all passed after the package migration. |
| Feature-package aggregate gate | `pass` | `./gradlew quality --rerun-tasks` executed all 92 tasks successfully, including both iOS target compilations and the desktop distributable. |
| Feature-package iOS host build | `pass` | The credential-free generic iOS Simulator `xcodebuild` succeeded after relinking the shared framework from the migrated packages. |
| Feature-package structural checks | `pass` | The former top-level layer package declarations and imports are absent, production tests mirror the TARGETS feature, `git diff --check` passes, and the SQLDelight `1.db` baseline is unchanged. |
| PR-feedback independent review | `pass` | `codex review --uncommitted` reported no actionable defects across the opt-in configuration, ViewModel file split, ktlint rule, CI correction, and task evidence. |
| PR-feedback hosted CI | `pass` | Follow-up run `33145856613` passed change classification, aggregate quality, the credential-free iOS Simulator host build, and quality-report upload after removal of the redundant SDK installation step. |
| Final `./gradlew quality --rerun-tasks` | `pass` | All 83 aggregate tasks executed and passed after self-enforcement and the multiline raw-string compatibility correction. |
| `git diff --check` and repository-safety scan | `pass` | The final diff has no whitespace errors and contains no personal path, development-team value, or signing configuration. The temporary negative-probe source is absent. |
| `./gradlew :shared:jvmTest :shared:iosSimulatorArm64Test` | `pass` | Focused common policy and ViewModel behavior passes on JVM and the iOS Simulator. |
| `./gradlew quality` | `pass` | All 70 tasks completed after the final text-state ownership correction, including JVM and iOS Simulator tests, both iOS compilation targets, lint, ktlint, Detekt, and desktop distributable checks. |
| Packaged macOS runtime | `pass` | The first final `.app` launch reproduced `NoClassDefFoundError: java/sql/DriverManager`. After adding only `java.sql` to the Compose native-distribution modules, `:desktopApp:createDistributable` passed, JDK image inspection found `Module: java.sql`, the packaged process remained healthy, and the maintainer completed add, edit, cancel, save, and remove interactions. |
| Post-package-fix `./gradlew quality` | `pass` | All 70 aggregate tasks passed after the `java.sql` runtime correction. |
| Material 2 static-analysis probe | `pass` | A temporary Material 2 import made `:shared:detekt` fail with the Compose Rules `Material2` finding; removing the probe restored a passing check, and no Material 2 source import or direct dependency remains. |
| Post-review affected verification | `pass` | `:shared:ktlintCheck`, `:shared:detekt`, `:shared:jvmTest`, and `:shared:iosSimulatorArm64Test` passed after the redaction correction. |
| Credential-free iOS host build | `pass` | `xcodebuild` completed the final Material 3 `iosApp` scheme for the generic iOS Simulator destination with code signing disabled. |
| Physical iPhone build, launch, and interaction | `pass` | A locally supplied development team was passed only to `xcodebuild`; the final Material 3 device bundle passed strict signature verification, installed through CoreDevice, and launched in the foreground after the maintainer unlocked the device. After a requested relaunch, the maintainer passed add, edit, cancel, save, remove, scrolling, and large-text interaction. No signing or device identifier was written to the repository. |
| macOS and iOS rendering | `pass` | The final Material 3 screen rendered readably in the packaged macOS application and on the compact iOS Simulator at the standard content size and after relaunch at the accessibility-large content size; the latter remains vertically scrollable as designed. |
| Manual keyboard and touch interaction | `pass` | The maintainer passed add, edit, cancel, save, and remove interactions in the corrected packaged macOS application and passed the equivalent touch, scrolling, and large-text flow on the relaunched physical iPhone. |

## Blockers and accepted risks

- None.

## Final

- **Status:** `done`
- **Outcome:** `accepted exact-domain management implemented and verified on both hosts`
