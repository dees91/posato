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

## Blockers and accepted risks

- Xcode 26.6 is newer than Kotlin 2.4.10's documented Xcode 26.4 ceiling. The
  credential-free build and Simulator launch passed, which is a bounded local
  observation rather than a broader support claim.
- Compose Desktop rejects Homebrew's JDK distribution for packaging. The
  daemon vendor criterion therefore pins Eclipse Temurin rather than disabling
  the packaging safeguard.
- `QUALITY-001`, `CI-001`, and the integrated review remain after the
  foundation milestone.
