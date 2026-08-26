# Development Baseline

The accepted [engineering quality contract](engineering-quality-contract.md)
is the standing authority for code quality, tests, review, dependencies,
security and privacy applicability, local verification, CI timing, and the
Definition of Done. Task planning and evidence follow the
[repository task workflow](../../tasks/README.md).

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
- small semantic platform contracts with test fakes;
- baseline tests, formatting, static checks, and CI for the introduced targets.

Gate 5 defines the required CI outcome but does not configure a pipeline.
`FOUNDATION-001`, `QUALITY-001`, and `CI-001` share one PR #1 brief,
execution record, and completed-change review. CI must complete before PR #1
merges or the first parallel implementation wave begins, whichever happens
first.

`FOUNDATION-001` is complete. The active next milestone is `QUALITY-001`.

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
[first MVP PR preparation checklist](../../tasks/first-mvp-pr-preparation-todo.md)
are complete and explicitly accepted.
