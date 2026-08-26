# Execution: PR #1 production skeleton

- **Brief:**
  [`../specifications/pr-1-production-skeleton.md`](../specifications/pr-1-production-skeleton.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** `Codex`
- **Reviewer:** `pending integrated PR #1 review`
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
- Compose and Metro are the only application dependencies. Their exact stable
  versions have real consumers, Apache-2.0 metadata, reviewed release and
  target compatibility, and no security-sensitive role in this shell.
- The hosts retain the Wizard's mechanical `1.0.0` build metadata. It is not a
  release or production-readiness claim.
- `QUALITY-001` is complete. `./gradlew quality` is the aggregate local entry
  point for ktlint formatting checks, Detekt and Compose Rules analysis,
  warning-free JVM and iOS compilation, JVM and desktop tests, macOS packaging,
  and machine-readable and human-readable reports.
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

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** `none recorded`
- **Resolution:** `pending integrated PR #1 review`

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Wizard archive inventory and sanitation review | `pass` | Archive inspected in an isolated temporary directory; accepted subset and exclusions identified. |
| Compose and iOS 18.0 compatibility boundary | `pass` | Compose 1.11.1 and 1.12.0 Skiko ICU objects declared simulator `minos 18.5`; selected stable Compose 1.10.3 uses Skiko 0.9.37.4 with `minos 17.2` and links cleanly at iOS 18.0. |
| Clean tests, target compilation, and macOS image | `pass` | `./gradlew clean :shared:jvmTest :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 :desktopApp:createDistributable` completed on JDK 21 with warnings as errors. |
| Android Studio JBR and daemon JVM separation | `pass` | With `JAVA_HOME` set to Android Studio's JBR 25, `./gradlew --version` selected an Eclipse Temurin 21 daemon and the shared tests, both iOS compilations, and macOS application-image build passed. |
| Credential-free iOS Simulator build | `pass` | Xcode 26.6 `xcodebuild` completed with signing disabled, no build warning, bundle ID `app.posato.ios`, and `MinimumOSVersion` 18.0. |
| iOS Simulator launch and visual inspection | `pass` | The built application installed and launched on the available iOS 26.5 iPhone 17 Pro simulator and rendered the accepted shared shell. |
| macOS distributable and visual inspection | `pass` | `./gradlew :desktopApp:createDistributable` produced and launched the arm64 application image with bundle ID `app.posato.macos`, minimum macOS 15.0, and the accepted shared shell. |
| Portability and integrity checks | `pass` | Wrapper distribution and JAR checksums match Gradle 9.5.0; plist, XML, and asset JSON parse; diff whitespace and new-file secret/path scans pass. |
| PoC quality-tool inventory | `pass` | Explicit `.research/blocker` source and history search found compiler warnings-as-errors but no ktlint, Detekt, Compose Rules, baseline, or aggregate quality configuration to import. |
| Quality dependency review | `pass` | Official compatibility, release, maintenance, and license material was reviewed for ktlint Gradle plugin 14.2.0, ktlint 1.8.0, Detekt 2.0.0-alpha.6, and Compose Rules 0.6.4; the Detekt exception and removal trigger are recorded above. |
| Quality dependency graph | `pass` | Gradle `detekt`, `detektPlugins`, and `ktlint` configuration reports show the expected build-only tool graphs, Detekt's strict Kotlin 2.4.10 compiler alignment, and no application runtime dependency change. |
| Controlled Compose Rules rejection | `pass` | A temporary synthetic public content-emitting composable without `Modifier` made `:shared:detekt` fail with `ModifierMissing`; the probe was removed before final verification. |
| Clean aggregate local quality gate | `pass` | `./gradlew clean quality` passed formatting, analysis, report generation, warnings-as-errors compilation, shared JVM tests, desktop tests, both iOS target compilations, and macOS distributable creation. |
| Aggregate gate repeatability | `pass` | Two consecutive `./gradlew quality` runs passed after the probe was removed; the second reused Gradle's configuration cache. Expected Detekt, ktlint, and shared test reports exist, and no lint baseline is tracked. |

## Blockers and accepted risks

- Xcode 26.6 is newer than Kotlin 2.4.10's documented Xcode 26.4 ceiling. The
  credential-free build and Simulator launch passed, which is a bounded local
  observation rather than a broader support claim.
- Compose Desktop rejects Homebrew's JDK distribution for packaging. The
  daemon vendor criterion therefore pins Eclipse Temurin rather than disabling
  the packaging safeguard.
- `CI-001` and the integrated review remain after the local quality milestone.
