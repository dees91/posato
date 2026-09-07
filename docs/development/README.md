# Development Baseline

The accepted [engineering quality contract](engineering-quality-contract.md)
is the standing authority for code quality, tests, review, dependencies,
security and privacy applicability, local verification, CI timing, and the
Definition of Done. Task planning and evidence follow the
[repository task workflow](../tasks/README.md).

PoC tool versions and module boundaries are evidence, not automatic MVP
requirements. The first production build has been scaffolded under the
accepted PR #1 boundaries.

The accepted module, target, Metro, native-helper, extension, deployment, and
generator-import boundaries are in
[ADR 0003](../decisions/0003-mvp-application-architecture-baseline.md). All
seven preparation gates and the ready checkpoint are complete.

## First production pull request

`user-confirmed` (2026-08-24): PR #1 is a small production skeleton. It adds:

- fresh KMP application modules independent of the PoC module graph;
- accepted application and target identifiers;
- one minimal shared Compose screen running in the macOS and iOS applications;
- small semantic platform contracts, with test fakes when behavior requires
  them;
- behavior-focused tests when applicable, formatting, static checks, and CI
  for the introduced targets.

Gate 5 defines the required CI outcome but does not configure a pipeline.
`FOUNDATION-001`, `QUALITY-001`, and `CI-001` share one PR #1 brief,
execution record, and completed-change review. CI must complete before PR #1
merges or the first parallel implementation wave begins, whichever happens
first.

`FOUNDATION-001`, `QUALITY-001`, and `CI-001` are complete. The first hosted
GitHub Actions run and the manual Codex review passed, and the foundation was
merged. This section preserves its original boundary; the root
[README](../../README.md#current-mvp-implementation) describes today's MVP.

## Foundation local use

The Gradle daemon runs on Eclipse Temurin JDK 21, while the desktop target
emits JVM 17 bytecode. The checked-in daemon JVM criteria takes precedence
over the launcher JVM used by Android Studio, `JAVA_HOME`, or the wrapper and
uses the Foojay resolver to provision Temurin 21 when it is not installed.
The wrapper still needs a Gradle-compatible launcher JVM; Xcode build phases
therefore require Java to be available in their development shell.

Inspect the effective launcher and daemon JVMs with:

```shell
./gradlew --version
```

The `Daemon JVM` line must report Java 21 and Eclipse Temurin. Android Studio
may continue to report its bundled JBR as the `Launcher JVM`.

## Compose previews

The shared module has an Android KMP library target only because common Compose
previews require Android tooling. It is not an Android application or MVP
platform target. Install Android SDK Platform 36 and Build Tools 36.0.0, sync
the project in Android Studio, then open a screen file to inspect its phone and
desktop preview functions. Their shared named cases live in the adjacent
`*PreviewDataProvider.kt` file.

Run the current desktop shell with:

```shell
./gradlew :desktopApp:run
```

Build the credential-free iOS Simulator host with:

```shell
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  build
```

The original PR #1 omitted blocking, synchronization, and native helpers.
Later increments added behavior and native foundations; use the
[MVP roadmap](../tasks/mvp-roadmap.md) for the remaining integration work.

## Local quality gate

Run the repository-owned aggregate gate from the repository root:

```shell
./gradlew quality
```

It checks root and module Kotlin formatting with ktlint, analyzes both
application modules with Detekt and Compose Rules, compiles warning-free JVM,
iOS, and preview-only Android source, runs shared JVM/iOS, desktop, tooling,
and native helper tests, and creates and checks the macOS distributable.
It also runs the Swift XCTest suites through `iosSwiftTest`.
Detekt writes Checkstyle, HTML,
Markdown, and SARIF reports under each module's `build/reports/detekt/`;
ktlint writes plain-text and Checkstyle reports under `build/reports/ktlint/`
in each checked project.

Run native Swift tests alone with `./gradlew iosSwiftTest`. This requires an
Apple Silicon Mac, selected Xcode, and an installed iOS Simulator runtime
supporting iPhone 17. The gate creates and deletes its own Simulator without
using the maintainer's existing Simulator or device data. It builds Kotlin
first, then invokes Xcode with the prebuilt framework and Compose resources,
avoiding nested Gradle builds. Every invocation runs tests again. Reports and
`.xcresult` bundles remain under ignored `build/ios-swift-tests.*/`; CI uploads
them even on failure. Interrupted runs retain their reports.

The shared Xcode scheme includes `iosAppTests`. Native simulator-compatible
mapping-store, CryptoKit, Keychain-boundary, enforcement, and expiry tests run
without signing credentials. Device-only tests keep their existing Simulator
skip conditions; their physical-device checklists remain required for those
capabilities. This is not an automated UI or release-readiness claim.
The scheme's all-tests action is intended for Simulator use. On a physical
iPhone, select only the named cases from the relevant device checklist:
suspended-expiry setup and verification require a human force-quit/wait
interval and must not run back-to-back through a blanket Test action.

The pinned quality set is ktlint Gradle plugin 14.2.0, ktlint 1.8.0, Detekt
2.0.0-alpha.6, and Compose Rules 0.6.4. The Detekt prerelease is the narrow
maintainer-accepted exception recorded in the quality contract and must be
replaced by the first compatible stable Detekt 2 release. Generated Compose
Resources source is excluded from ktlint; repository-owned Kotlin has no lint
baseline.

## Manual CI and merge check

CI uses only `workflow_dispatch`: no commit, PR, ready-for-review, or `main`
push automatically starts it. Run it explicitly once the branch is ready:

```shell
gh pr view <pr-number> --json headRefName,headRefOid
gh workflow run ci.yml --ref <pr-head-branch>
gh run list --workflow ci.yml --branch <pr-head-branch> --event workflow_dispatch
gh run watch <run-id> --exit-status
gh run view <run-id> --json headSha,status,conclusion,url,jobs
gh pr view <pr-number> --json headRefOid
```

Immediately before merge, require `status: completed`, `conclusion: success`,
and a successful `Quality` job, with run `headSha` exactly matching the freshly
read PR `headRefOid`. Do not accept an older green run or a skipped job. Any
new commit needs another explicit dispatch, including documentation changes.
The Actions UI's **Run workflow** branch selector is an equivalent entry point.

This is a maintainer/agent process requirement, not a protected-branch check:
the current private-repository plan cannot enforce it. Account upgrades,
branch protection, and rulesets are out of scope. If CI cannot run, stop before
merge and request a maintainer decision. Local `./gradlew quality` after the
last material correction and the selected review tier still apply.

## Verification driver

`posato-control` is the agent-facing driver that builds, launches, drives,
inspects, screenshots, and resets the macOS application and the iOS
application on the Simulator and on a connected iPhone. It is a platform-build
and manual-inspection driver, not an automated UI test suite: nothing it
drives runs in `./gradlew quality` or in CI, and the aggregate gate only runs
the module's ktlint, Detekt, unit tests, and Swift format check.

```shell
./gradlew :posato-control:installDist
tools/posato-control/build/install/posato-control/bin/posato-control doctor
```

Every command prints one JSON envelope and takes `--target desktop|simulator|device`.
The desktop backend needs macOS Accessibility and Screen Recording access for
the terminal or IDE process that runs it; the physical iPhone needs
`posato.apple.developmentTeam` in the ignored `local.properties` file. All
evidence stays under the ignored `build/verification/` directory. The command
reference, query syntax, scenario format, and per-target notes live in
[`tools/posato-control/README.md`](../../tools/posato-control/README.md).

## Apple development provisioning

`posato-provisioning` obtains the Apple development certificate, device
registrations, and provisioning profiles the Posato targets need, so no
developer-portal step blocks an agent. It needs an App Store Connect API team
key the maintainer creates once and keeps outside Git, and like the
verification driver it never runs in CI. Setup, the command reference, the
profile destinations, and the privacy boundary are in
[`apple-provisioning.md`](apple-provisioning.md).
