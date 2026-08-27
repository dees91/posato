# Execution: `TARGETS-001`

- **Brief:**
  [`../specifications/targets-001-exact-domain-management.md`](../specifications/targets-001-exact-domain-management.md)
- **Status:** `verification`
- **Review tier:** `high-risk`
- **Implementer:** `Codex`
- **Reviewer:** `independent Codex reviewer`
- **Branch:** `targets-001-exact-domains`
- **Updated:** `2026-08-27`

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

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Dependency and primary-source review | `pass` | Kuri 0.1.0 publishes JVM, iOS arm64, and iOS Simulator arm64 variants, uses MIT, has no runtime dependency beyond Kotlin stdlib, and has no OSV entry; AndroidX 2.10.0 and coroutines Swing follow current JetBrains ViewModel guidance. Stable Compose Multiplatform Material 3 1.9.0 is Apache-2.0, has no OSV entry, supplies the state-based API, and resolves shared Compose core artifacts to the existing 1.10.3 graph. |
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
