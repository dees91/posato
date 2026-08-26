# Execution: PR #1 production skeleton

- **Brief:**
  [`../specifications/pr-1-production-skeleton.md`](../specifications/pr-1-production-skeleton.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** `Codex`
- **Reviewer:** `independent Codex reviewer; completed-change review approved`
- **Branch:** `foundation-001`
- **Updated:** `2026-08-26`

## Plan

1. Sanitize the reviewed Wizard archive into the accepted Apple-only module,
   target, identifier, dependency, and design boundaries.
2. Select compatible stable toolchain pins, implement the shared shell and
   platform Metro graphs, and verify macOS and iOS Simulator behavior.
3. Add the aggregate local quality boundary, then reproduce it in
   credential-free CI.
4. Obtain one independent completed-change review of the integrated PR #1
   increment and resolve all blocking findings.

## Result

- `FOUNDATION-001` is complete. The source archive SHA-256 is
  `640c003612495bad53d17807dbb8bc1eb4a3ef320dcda113cb4510ad4f9e92f1`.
- The generated Android, Web, sample, optional dependency, icon, and local
  configuration surfaces are excluded from the import.
- The retained topology is `:shared`, `:desktopApp`, and the `iosApp` Xcode
  host with the accepted identifiers and deployment targets. No PoC source was
  copied.
- The pinned foundation is Gradle 9.5.0, Kotlin 2.4.10, Compose Multiplatform
  1.10.3, Metro 1.4.2, JDK 21, and JVM 17 bytecode.
- The checked-in Gradle daemon criteria pins Eclipse Temurin 21 and uses the
  Gradle-owned Foojay resolver convention plugin 1.0.0 for portable
  provisioning. Android Studio may launch Gradle with its JBR 25 without
  changing the daemon runtime.
- The shared Metro contract and final iOS and desktop graphs compile. Both
  hosts render the exact four-line shell from `DESIGN.md` without controls,
  navigation, sample branding, or deferred behavior.
- The shell obtains its background, label color, and semantic text sizes from
  the owning platform. UIKit colors are resolved through its RGB conversion
  API so monochrome and RGB color spaces are both safe. The desktop actual
  reads Compose's system appearance and adapts the JDK's semantic AWT window
  and label colors for light or dark presentation.
- Compose and Metro are the only application dependencies. Their exact stable
  versions have real consumers, Apache-2.0 metadata, reviewed release and
  target compatibility, and no security-sensitive role in this shell.
- The hosts retain the Wizard's mechanical `1.0.0` build metadata. It is not a
  release or production-readiness claim.
- `QUALITY-001` is complete. `./gradlew quality` is the aggregate local entry
  point for ktlint formatting checks, Detekt and Compose Rules analysis,
  warning-free JVM and iOS compilation, behavior-focused tests when present,
  macOS packaging, and machine-readable and human-readable reports. The
  current static shell contains no business behavior that warrants a test.
- The pinned quality set is ktlint Gradle plugin 14.2.0, ktlint 1.8.0, Detekt
  2.0.0-alpha.6, and Compose Rules 0.6.4. The ktlint components use MIT terms;
  Detekt and Compose Rules use Apache-2.0 terms. All are build-time-only
  dependencies with no application runtime or security-boundary role.
- `user-confirmed` (2026-08-26): Detekt 2.0.0-alpha.6 is a narrow temporary
  exception because stable Detekt 1.23.8 cannot analyze Kotlin 2.4.10 metadata
  and no stable Detekt 2 release exists. The first compatible stable Detekt 2
  release removes the exception after the aggregate gate passes.
- Generated Compose Resources Kotlin is excluded from formatting analysis.
  Generic naming rules ignore `@Composable` functions while the active Compose
  Rules `ComposableNaming` check owns that convention. The Swift-facing
  `MainViewController` function retains one local naming suppression required
  by the existing Xcode host API. No baseline, global suppression, or
  failure-tolerant mode is configured.
- `CI-001` is implemented as one GitHub Actions job on the arm64 `macos-15`
  runner. Pull requests and pushes to `main` run `./gradlew quality`, then build
  the iOS Simulator host with signing disabled.
- `CI-001` is complete. The first hosted pull-request job passed the aggregate
  quality gate, the signing-disabled iOS host build, and report upload on the
  selected runner in 6 minutes 52 seconds.
- CI selects Temurin 21 and Xcode 26.3 explicitly. The latter is present on the
  selected runner and stays within Kotlin 2.4.10's documented Xcode support
  ceiling instead of inheriting the runner's default Xcode.
- The GitHub-maintained checkout, Java setup, and report-upload actions are
  pinned to immutable release commits, have MIT license metadata, and run with
  read-only repository permissions. Checkout credential persistence is
  disabled, no private signing material is used, and quality reports are kept
  for 14 days.
- An explicit `.research/blocker` source and history search found no CI
  workflow to reuse. The retained PoC evidence is limited to disabling code
  signing explicitly and not treating a successful Xcode host build as future
  test evidence.
- `user-confirmed` (2026-08-26): automated tests are reserved for important
  business, state, policy, validation, parsing, and boundary behavior. Static
  shell rendering, copy, theme mapping, and Compose wiring are not test
  targets. Golden UI testing remains a separate post-MVP decision.
- `observed` (2026-08-26): a manually requested hosted Codex review examined
  commit `e4eb8f4`, reported no major issue, and added no inline finding.
  Automatic AI review remained disabled.

## Completed-change review

- **Verdict:** `approve`
- **Critical or Required findings:** The initial pass reported `2 Required`.
  Its follow-up confirmed the Xcode warning correction, then reported `1
  Critical` iOS launch failure and `1 Required` desktop appearance and
  contrast defect in the first platform-theme correction.
- **Resolution:** The shell now consumes platform semantic colors and
  typography through a narrow KMP theme boundary. UIKit semantic colors use
  `getRed` conversion rather than the color-space-limited `CIColor` accessor;
  the rebuilt app launches on the Simulator. Desktop reads Compose's current
  appearance, produces matching light and dark surfaces, and uses one opaque
  semantic label color while size, weight, and spacing carry hierarchy. The
  Xcode warning is recorded accurately. Maintainer feedback removed shell and
  theme tests because they asserted static UI wiring rather than important
  behavior. The same independent reviewer verified the final corrections and
  reported no remaining Critical or Required finding.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Wizard archive inventory and sanitation review | `pass` | Archive inspected in an isolated temporary directory; accepted subset and exclusions identified. |
| Compose and iOS 18.0 compatibility boundary | `pass` | Compose 1.11.1 and 1.12.0 Skiko ICU objects declared simulator `minos 18.5`; selected stable Compose 1.10.3 uses Skiko 0.9.37.4 with `minos 17.2` and links cleanly at iOS 18.0. |
| Clean target compilation and macOS image | `pass` | `./gradlew clean :shared:compileKotlinJvm :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 :desktopApp:createDistributable` completed on JDK 21 with warnings as errors. The static shell has no test sources. |
| Android Studio JBR and daemon JVM separation | `pass` | With `JAVA_HOME` set to Android Studio's JBR 25, `./gradlew --version` selected an Eclipse Temurin 21 daemon; both iOS compilations and the macOS application-image build passed. |
| Credential-free iOS Simulator build | `pass` | Xcode 26.6 `xcodebuild` completed with signing disabled, bundle ID `app.posato.ios`, and `MinimumOSVersion` 18.0. Xcode emitted its App Intents metadata warning because this shell has no `AppIntents.framework` dependency; no repository-owned source warning was emitted. |
| iOS Simulator launch and visual inspection | `pass` | After replacing `CIColor` conversion, the rebuilt application installed, remained running, and rendered the accepted shared shell in both Light and Dark appearances on the available iOS 26.5 iPhone 17 Pro simulator. |
| macOS distributable and appearance inspection | `pass` | `./gradlew :desktopApp:createDistributable` produced the arm64 image with bundle ID `app.posato.macos` and minimum macOS 15.0. The final Dark appearance and a temporary forced-Light source probe both rendered the accepted shell; the probe was reverted. The observed light and dark label/background pairs calculate to 18.10:1 and 18.88:1. |
| Portability and integrity checks | `pass` | Wrapper distribution and JAR checksums match Gradle 9.5.0; plist, XML, and asset JSON parse; diff whitespace and new-file secret/path scans pass. |
| PoC quality-tool inventory | `pass` | Explicit `.research/blocker` source and history search found compiler warnings-as-errors but no ktlint, Detekt, Compose Rules, baseline, or aggregate quality configuration to import. |
| Quality dependency review | `pass` | Official compatibility, release, maintenance, and license material was reviewed for ktlint Gradle plugin 14.2.0, ktlint 1.8.0, Detekt 2.0.0-alpha.6, and Compose Rules 0.6.4; the Detekt exception and removal trigger are recorded above. |
| Quality dependency graph | `pass` | Gradle `detekt`, `detektPlugins`, and `ktlint` configuration reports show the expected build-only tool graphs, Detekt's strict Kotlin 2.4.10 compiler alignment, and no application runtime dependency change. |
| Controlled Compose Rules rejection | `pass` | A temporary synthetic public content-emitting composable without `Modifier` made `:shared:detekt` fail with `ModifierMissing`; the probe was removed before final verification. |
| Clean aggregate local quality gate | `pass` | `./gradlew clean quality` passed formatting, analysis, report generation, warnings-as-errors compilation, both iOS target compilations, and macOS distributable creation. The configured shared JVM and desktop test tasks correctly completed with no source. |
| Aggregate gate repeatability | `pass` | Two consecutive `./gradlew quality` runs passed after the probe was removed; the second reused Gradle's configuration cache. Expected Detekt and ktlint reports exist, and no lint baseline is tracked. |
| CI workflow validation | `pass` | The workflow parses as YAML, `actionlint` 1.7.12 reports no finding, `git diff --check` passes, and every external action resolves to the recorded immutable release commit. |
| CI dependency and permission review | `pass` | The three GitHub-maintained actions are active, use MIT terms, are pinned by commit SHA, and receive only read-only repository access; checkout credential persistence and Apple signing are disabled. |
| Local CI command parity | `pass` | From a clean build state, `./gradlew clean quality` and the workflow's signing-disabled `xcodebuild` command completed locally. Local Xcode is 26.6; the workflow selects the compatible Xcode 26.3 installed on the current arm64 `macos-15` runner image. |
| Hosted CI execution | `pass` | PR #1's first GitHub-hosted `Quality` job completed successfully in 6 minutes 52 seconds on the selected arm64 `macos-15` runner; the documentation and review-loop update passed again in 4 minutes 39 seconds. |
| Manual hosted Codex review | `pass` | `@codex review` examined commit `e4eb8f4`, reported no major issue, and added no inline finding. |

## Blockers and accepted risks

- Xcode 26.6 is newer than Kotlin 2.4.10's documented Xcode 26.4 ceiling. The
  credential-free build and Simulator launch passed, which is a bounded local
  observation rather than a broader support claim.
- Local Xcode 26.6 emits an App Intents metadata extraction warning because the
  static shell has no `AppIntents.framework` dependency. This is Xcode tool
  output, not a repository-owned compiler warning; the Xcode 26.3 CI run must
  show whether the selected supported toolchain emits the same warning before
  any suppression or project-setting decision is considered.
- Compose Desktop rejects Homebrew's JDK distribution for packaging. The
  daemon vendor criterion therefore pins Eclipse Temurin rather than disabling
  the packaging safeguard.
- PR #1 is connected to its private GitHub repository and its first hosted CI
  run and manual Codex review passed. The remaining handoff is the maintainer's
  merge decision; automatic AI review remains disabled.
- Independent review found two Required issues initially and one Critical plus
  one Required issue in the first correction. All were resolved, and the final
  follow-up verdict is `approve` with no remaining Critical or Required
  finding.
