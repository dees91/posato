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
GitHub Actions run and the manual Codex review passed; PR #1 remains open for
the maintainer's merge decision.

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

PR #1 does not implement website blocking, application blocking,
synchronization, enrollment, recovery, or production helpers. Implementation
starts only after all seven gates and the ready checkpoint in the
[first MVP PR preparation checklist](../tasks/first-mvp-pr-preparation-todo.md)
are complete and explicitly accepted.

## Local quality gate

Run the repository-owned aggregate gate from the repository root:

```shell
./gradlew quality
```

It checks root and module Kotlin formatting with ktlint, analyzes both
application modules with Detekt and Compose Rules, compiles warning-free JVM,
iOS, and preview-only Android source, runs the shared JVM and desktop test
tasks when test sources exist, and creates the macOS distributable. The current
static shell has no behavior-focused tests. Detekt writes Checkstyle, HTML,
Markdown, and SARIF reports under each module's `build/reports/detekt/`;
ktlint writes plain-text and Checkstyle reports under `build/reports/ktlint/`
in each checked project.

Draft pull requests allocate no GitHub Actions runner; moving one to ready for
review triggers CI, while returning one to draft cancels its in-progress run.
GitHub Actions then runs the full gate on macOS for every pull request
containing a non-Markdown change and for every push to `main`. A Markdown-only
pull request runs the lightweight scope job and reports the macOS `Quality` job
as skipped; any classification failure falls back to running `Quality`.

Automatic GitHub Actions triggers are temporarily paused through 2026-09-05
because the account exhausted its included runner minutes. Until they are
restored, run `./gradlew quality` locally after the last material correction
and treat that result as the merge gate. The CI workflow remains available for
manual dispatch when spending permits.

The pinned quality set is ktlint Gradle plugin 14.2.0, ktlint 1.8.0, Detekt
2.0.0-alpha.6, and Compose Rules 0.6.4. The Detekt prerelease is the narrow
maintainer-accepted exception recorded in the quality contract and must be
replaced by the first compatible stable Detekt 2 release. Generated Compose
Resources source is excluded from ktlint; repository-owned Kotlin has no lint
baseline.
