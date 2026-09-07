# Posato

Posato is an open-source application intended to interrupt habitual access to
selected websites and applications across personal devices while minimizing
collected data and maintenance burden.

## Status

This repository contains the accepted product knowledge base and an Apple MVP
in progress, with working macOS and iOS applications. It is not a complete MVP,
release, or production-readiness claim. The accepted public name is **Posato**,
`posato.app` is the maintainer-controlled canonical domain, and `app.posato` is
the accepted stable technical root. The accepted contracts are recorded under
`docs/product/`.

Two completed feasibility efforts inform the project:

- an Apple-first synchronization PoC showed that a Kotlin Multiplatform and
  Compose Multiplatform application can synchronize encrypted local-first state
  through narrow CloudKit and Keychain adapters on one physical Mac and one
  physical iPhone;
- an Apple enforcement spike showed bounded domain and application blocking on
  the same platform categories using a local macOS proxy and native application
  controls, plus iOS Family Controls and Managed Settings.

Both results were bounded experiments, not production architecture or release
qualification. Their exact limits are documented in
[`docs/wiki/`](docs/wiki/index.md).

## Intended direction

- Apple-first MVP sequencing for macOS and iOS.
- Kotlin Multiplatform and Compose Multiplatform.
- Kotlin-first domain, orchestration, and shared UI.
- Narrow semantic boundaries around native Apple APIs.
- No product-operated user-data backend or in-app provider credentials.
- Apple MVP synchronization uses one **Sync with iCloud** action per
  installation and no application-level QR or cross-device approval.
- Application-layer encryption remains common to CloudKit and the later
  portable-folder transport.
- No browsing-history collection as a default product boundary.
- Android, Linux, and a user-selected portable synchronization folder remain
  later directions rather than MVP parity gates.

These are direction constraints, not a complete architecture.

## Start here

- [Wiki operating contract](docs/wiki/README.md)
- [Wiki index](docs/wiki/index.md)
- [Product framing](docs/wiki/topics/product-framing.md)
- [Accepted MVP scope](docs/product/mvp-scope.md)
- [Accepted product identity](docs/product/product-identity.md)
- [Accepted design system](DESIGN.md)
- [Native interaction prototype](prototypes/mvp-interaction-flow/README.md)
- [Engineering quality contract](docs/development/engineering-quality-contract.md)
- [Task workflow](docs/tasks/README.md)
- [Feasibility results and limits](docs/wiki/topics/feasibility-results-and-limits.md)
- [MVP open questions](docs/wiki/topics/mvp-open-questions.md)
- [First MVP PR preparation plan](docs/tasks/first-mvp-pr-preparation-plan.md)
- [First MVP PR preparation checklist](docs/tasks/first-mvp-pr-preparation-todo.md)

## Current MVP implementation

The preparation gates and foundation increment are complete. The applications
now use the accepted Compose design system, iPhone bottom navigation, and a
native macOS sidebar/window. Session and Paused items use real ViewModels and
local persistence, including batch website entry, searchable lists,
device-local application selection, and a manual session timer.

Shared and native tests cover behavior-bearing code, including persistence,
session state, cryptographic boundaries, and native enforcement logic.
Synchronization and enforcement foundations exist, but the user-facing sync
flow and session-driven enforcement are not yet connected. A running timer
does not establish that blocking is active.

The [MVP roadmap](docs/tasks/mvp-roadmap.md) owns remaining implementation
ordering. [DESIGN.md](DESIGN.md) is the current visual authority; the separate
mock prototype is a frozen reference, not the application under test.

## Development and verification

See the [development guide](docs/development/README.md) for JDK, Android SDK,
Xcode, host launch, native provisioning, and verification-driver setup.

```shell
./gradlew :desktopApp:run
./gradlew quality
./gradlew iosSwiftTest
```

`quality` includes the native Swift XCTest gate on a temporary iOS Simulator,
alongside Kotlin tests, static checks, target compilation, and macOS packaging.
Physical-device checks remain separate where Simulator coverage is insufficient.

GitHub CI is disabled for now. Before merge, run local `./gradlew quality`
after the last correction and complete the required review. Branch protection
retains the PR requirement, administrator enforcement, and force-push/deletion
restrictions, but cannot verify a local test result. See the
[local merge checklist](docs/development/README.md#local-quality-and-merge-check).
