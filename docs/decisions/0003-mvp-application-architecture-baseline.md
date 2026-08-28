# ADR 0003: Establish the MVP Application Architecture Baseline

## Status

- **Status:** Accepted
- **Date:** 2026-08-25
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`

## SYNC-003 amendment

`user-confirmed` (2026-08-28):
[ADR 0007](0007-apple-workspace-bootstrap-and-native-sync-boundary.md) adds the
deferred `app.posato.macos.sync` Swift companion as the app-owned CloudKit and
Keychain boundary. It is a short-lived normal-user process distinct from the
enforcement helper and root daemon. The original PR #1 graph remains unchanged;
the target, packaging, signing, and IPC implementation belong to `SYNC-006` and
`SYNC-008`.

## SYNC-001 amendment

`user-confirmed` (2026-08-28):
[ADR 0006](0006-apple-mvp-encrypted-operation-and-convergence.md) resolves the
production synchronization primitives, platform-provider ownership, canonical
encoding, versioning, automatic Apple author registration, and deterministic
convergence contract. `SYNC-002` owns its common Kotlin implementation,
platform provider leaves, and cross-target vectors without changing the module
or platform-boundary baseline in this ADR.

## Gate 5 amendment

`user-confirmed` (2026-08-25): the accepted
[engineering quality contract](../development/engineering-quality-contract.md)
defines required quality and CI outcomes while leaving exact versions,
commands, Gradle wiring, CI jobs, and runner images to the shared PR #1
execution cycle. Gate 5 does not configure CI. CI remains a PR #1 milestone
and must complete before PR #1 merges or the first parallel implementation
wave begins, whichever happens first.

This amendment supersedes the original wording that implied Gate 5 would
select exact commands and CI jobs or that a CI image had to be selected before
reviewed generator files could enter the working branch.

## MACOS-001 amendment

`user-confirmed` (2026-08-26):
[ADR 0004](0004-macos-helper-ownership-and-lifecycle.md) resolves the deferred
macOS helper language, privilege, installation, authorization, update,
recovery, and removal boundary. The signed native-helper placeholder is
implemented as a normal-user Swift session helper plus a minimal Swift root
launch daemon limited to SystemConfiguration ownership and recovery. Shared
Kotlin remains the product and policy owner. Concrete IPC schemas, daemon and
Mach identifiers, build wiring, and enforcement mechanisms remain with their
named implementation tasks.

## PREVIEW-001 amendment

`user-confirmed` (2026-08-27): `:shared` adds the
`com.android.kotlin.multiplatform.library` plugin and an `androidMain` source
set solely to make common Compose `@Preview` tooling available. It has no
Android application, Activity, host, bundle identifier, distribution artifact,
runtime support claim, or MVP feature ownership. The target compiles the same
shared UI with a thin `PlatformTheme` actual and supplies no product platform
behavior. Android application work remains deferred until separately accepted.

## TARGETS-001 package-ownership amendment

`user-confirmed` (2026-08-28): production packages inside the initial
`:shared` module are organized feature-first. TARGETS-001 owns
`app.posato.feature.targets`, with `domain`, `data`, and `ui` subpackages only
where those responsibilities already exist. Application-wide capabilities use
named `app.posato.core.<capability>` packages; the current shared capabilities
are `database` and `designsystem`, and no catch-all declarations live directly
under `core`.

This source organization does not introduce pass-through use cases or imply a
module split. TARGETS UI may depend directly on its injected store contract,
while the application shell and Metro composition roots remain under
`app.posato` and `app.posato.di`. A feature or core package becomes a separate
module only when a real production dependency boundary requires it.

## Context

The accepted MVP scope, Posato identity, and design baseline are sufficient to
define the first production application shell. The architecture must now make
the initial module and target boundaries reviewable without importing the
feasibility repository's module graph or treating a project generator as
ongoing authority.

The maintainer selected Kotlin Multiplatform, Compose Multiplatform, and Metro.
The first product release is Apple-first, with an iOS application and an
arm64-only macOS application. The accepted product behavior later requires
system-owned iOS expiry callbacks and native macOS enforcement mechanisms that
cannot live inside the desktop JVM process.

The [Compose Multiplatform Wizard audit](../wiki/sources/compose-multiplatform-wizard.md)
records the exact generator evidence and sanitation boundary used for this
decision. The wizard is a bootstrap input, not an architecture authority.

## Decision

### Initial module and host graph

The first production skeleton uses this graph:

```text
iosApp (Xcode host)
    -> :shared (KMP, Compose, and Metro plugin)
           commonMain
           commonTest
           androidMain (preview tooling only)
           iosMain
           jvmMain
    <- :desktopApp (JVM entry point and macOS packaging)

Later native targets, not PR #1 dependencies:
    iosActivityMonitorExtension
    macosHelper
    macosSyncCompanion
```

`:shared` is initially the only KMP production module. It owns the application
composable, shared product and presentation types, semantic platform
contracts, and test fakes. It is split only when a production dependency
boundary requires it; the skeleton does not create empty domain, data, feature,
or utility modules.

`:desktopApp` owns the JVM entry point, application window, macOS packaging,
and desktop composition root. `iosApp` owns the Swift application lifecycle
and embeds the static framework produced by `:shared`. Both hosts delegate to
shared UI and do not own product policy.

### Source-set, injection, and platform-boundary rules

- `commonMain` owns shared UI, product state, typed outcomes,
  platform-neutral interfaces, and an unannotated canonical Metro graph
  contract.
- `commonTest` owns fakes and contract tests.
- `iosMain` owns the Compose controller factory, iOS adapters, and the final
  `@DependencyGraph` that extends the common graph contract.
- `jvmMain` owns desktop adapters and its final `@DependencyGraph`.
- Each application host creates one platform graph at its composition root and
  exposes only the required entry surface to shared Compose.

Metro is the only dependency-injection framework. Dependencies use
constructor injection. Runtime platform values enter through explicit graph
factory inputs or platform binding containers; product code does not call a
service locator. Both platform graphs must pass compile-time validation.

Use a common interface and injection when a capability has runtime ownership,
permissions, lifecycle, multiple implementations, structured product
outcomes, or a test fake. Use `expect`/`actual` only for a small compile-time
platform difference with exactly one implementation per target, no injected
test substitute, and no platform type in the common signature. Neither
mechanism replaces IPC when another process owns the operation.

Construction remains inert. Activation, cancellation, and cleanup are
explicit, bounded, and safe to repeat. Native framework values, opaque Apple
tokens, native errors, and system-settings objects do not cross into
`commonMain`.

### Platform targets and identifiers

| Target | Identifier | PR #1 | Ownership |
| --- | --- | --- | --- |
| iOS application | `app.posato.ios` | Created | Swift host and shared Compose framework |
| macOS application | `app.posato.macos` | Created | Compose Desktop/JVM application |
| iOS Device Activity monitor extension | `app.posato.ios.activitymonitor` | Deferred | Xcode-owned expiry callback and minimum shared App Group state |
| macOS native helper | `app.posato.macos.helper` | Deferred | Signed native process behind authenticated, versioned local IPC |
| macOS synchronization companion | `app.posato.macos.sync` | Deferred | Short-lived signed Swift process for CloudKit and synchronizable Keychain only |

The first skeleton has no Android application, Web target, custom
shield-action, custom shield-configuration, or Device Activity report target.
`androidMain` exists only for shared Compose preview tooling and has no product
identifier or host. Gate 7 registered the initial accepted Apple identifiers
and capabilities before production implementation. `SYNC-003` registers the
later accepted synchronization-companion identifier; neither deferred process
becomes an empty PR #1 target.

### iOS enforcement boundary

The iOS application owns authorization, selection, and foreground interaction
with Family Controls and Managed Settings. Default system shields are
sufficient for the MVP; custom shield extensions are not required.

An Xcode-owned Device Activity monitor extension handles the normal session-
expiry opportunity when the main application is suspended. It clears only
Posato-owned restrictions and exchanges only the minimum required local state
with the application through an App Group. Apple documents Device Activity
callbacks as occurring when the device is in use, so the product and its tests
must not promise execution at the exact wall-clock end instant.

The application and extension are separate entitlement, signing,
provisioning, and distribution boundaries. Their production implementation,
App Group schema, and entitlement validation belong to the first iOS
enforcement pull request and Apple Task 0, not PR #1.

### macOS enforcement boundary

The macOS application remains a Compose Desktop JVM process. A separate signed
native helper owns system-level or native enforcement mechanics, including
system-proxy mutation and application observation. Shared Kotlin owns
enforcement intent and product policy.

The helper exposes an allowlisted local IPC protocol with bounded and versioned
frames, request identity, timeouts, structured outcomes, peer authentication,
and safe repeatable cleanup. Every request is untrusted: the helper validates
its version, size, schema, operation, caller identity, authorization, and
current state before acting, and it fails closed on unknown or malformed input.
It is not a general shell-command service and it does not own synchronization
or product business logic. Sensitive values do not enter process arguments or
logs, and allowed navigation or application-observation events are not retained
as browsing or usage history.

Any privileged installation uses least privilege and authorizes every
privileged operation rather than trusting a previously authenticated channel.
The helper snapshots and restores only Posato-owned system mutations, rejects
replay or stale ownership, and must leave unrestricted networking recoverable
after application, helper, or IPC failure.

Those language, privilege, installation, authorization, update, recovery, and
removal decisions were deferred when this ADR was accepted and are now governed
by [ADR 0004](0004-macos-helper-ownership-and-lifecycle.md). The implementation
must preserve both accepted process boundaries and re-establish production
security and test coverage rather than copying the feasibility helper
wholesale.

### Persistence, navigation, and synchronization

[ADR 0002](0002-synchronization-trust-and-workspace-modes.md) remains the
authority for CloudKit Private Database transport, synchronizable-Keychain
workspace-key delivery, common application-layer E2EE, and the later portable
workspace mode. Those mechanisms do not imply an HTTP client, so Ktor is not a
baseline dependency.

[ADR 0007](0007-apple-workspace-bootstrap-and-native-sync-boundary.md) owns the
exact Apple mailbox, secure-item, one-workspace bootstrap, and macOS
synchronization-companion contract. It adds no PR #1 dependency or target.

PR #1 adds Compose and Metro only. Navigation 3 is the default candidate for
the first multi-screen flow, AndroidX Multiplatform ViewModel for the first
stateful screen, and SQLDelight for the first local-replica slice. Each is
added only with production code that uses it and after its exact version and
target compatibility are verified in that named pull request. Persistence
schema, migrations, concurrency ownership, and production serialization remain
decisions for the first slice that needs them.

### Platform and toolchain baseline

- The iOS deployment target is 18.0.
- The macOS deployment target is 15.0 on arm64 only. Adding x86-64 requires a
  separate acceptance decision.
- Both deployment targets are rechecked against the release support policy
  before the first release; this decision does not claim future store
  eligibility.
- The Apple MVP product graph has no Android application. `:shared` uses the
  Android Gradle Plugin only for common Compose preview tooling.
- PR #1 uses JDK 21 and emits JVM 17 bytecode.
- PR #1 must select, compatibility-check, and pin one stable Kotlin, Compose,
  Gradle, Metro, and Xcode set before generated files are accepted. The
  separate CI task selects a compatible runner image before its accepted
  deadline.

At the reviewed wizard revision, the generation candidates were Kotlin 2.4.10,
Compose Multiplatform 1.12.0, Gradle 9.7.1, and Metro 1.4.2. These are observed
generator inputs, not independently accepted production pins. The repository's
version catalog and Gradle wrapper become the version authority after PR #1.

### Wizard import boundary

Generate the project in an isolated temporary directory with **Posato**,
`app.posato`, iOS, Desktop, and Metro selected. Leave Android, Web, generated
`AGENTS.MD`, sample tests, and every other optional library unselected. Never
extract the archive over the repository.

Before reviewed files enter PR #1:

- rename `sharedUI` to `shared` and update Gradle and Xcode references;
- replace generated suffixes with the accepted target identifiers;
- retain only DMG/macOS desktop packaging;
- remove sample UI, theme toggle, custom font, external link, generated test,
  generated README and agent instructions, and unused resources;
- remove the generated rocket icon and packaging references until an accepted
  production icon exists;
- implement only the accepted PR #1 shell from `DESIGN.md`;
- adapt retained Kotlin declarations to repository code rules; and
- verify both platform graphs, behavior-focused shared tests when applicable,
  the JVM build, an iOS Simulator build, and a structural diff against the
  generated archive.

The wizard is a one-time generator. Future updates are explicit reviewed
changes, not regeneration.

## Alternatives considered

### Import the wizard archive directly

Rejected. It would import sample behavior, unnecessary dependencies and
resources, generated documentation, and incorrect identifier suffixes. It can
also conflict with existing repository files on a case-insensitive filesystem.

### Select every available platform and library

Rejected as product targets. Android and Web are outside the Apple-first MVP,
and speculative libraries would create ownership and update cost before a
consumer exists. The separately accepted preview-only Android KMP library is
the narrow tooling exception required by common Compose previews.

### Use Koin alongside or instead of Metro

Rejected for the greenfield application graph. The maintainer selected Metro
as the only DI framework, and compile-time platform graphs fit the accepted
composition-root boundary.

### Use broad `expect`/`actual` services

Rejected. Runtime-owned capabilities require injection, test substitutes, and
structured availability or permission outcomes. `expect`/`actual` remains a
narrow compile-time tool.

### Keep native macOS mechanisms inside the JVM process

Rejected as the baseline. JNI or JNA would introduce an in-process native ABI
boundary without removing packaging, signing, privilege, crash, and recovery
requirements. The separate helper makes those responsibilities explicit.

### Omit the iOS Device Activity monitor extension

Rejected. Foreground application lifecycle alone cannot satisfy normal
scheduled expiry when the main application is suspended. The extension is the
minimum accepted background callback boundary, subject to Apple's execution
semantics.

## Consequences

- PR #1 has a small graph: one KMP module, one JVM launcher, and one Xcode host.
- The helper and extension are known Apple resources without becoming empty
  code targets in the skeleton.
- Shared product behavior remains testable without platform frameworks.
- Metro graph errors are caught separately for iOS and desktop.
- Native process and extension lifecycle risks remain visible and must be
  resolved in their named enforcement slices.
- The arm64-only macOS baseline narrows the first compatibility matrix and
  deliberately excludes Intel Macs.
- Tool versions remain unpinned until PR #1 verifies them together; the
  generator's displayed versions are not silently treated as production
  authority.

## Required PR #1 verification

PR #1 must prove, at minimum:

- a clean checkout resolves only the reviewed and pinned dependencies;
- `:shared` common tests pass;
- both Metro platform graphs compile and validate;
- the macOS application builds and renders the accepted minimal shell;
- the iOS Simulator application builds and renders the same shell;
- no Android application or Web target, enforcement implementation,
  synchronization implementation, helper executable, or extension executable
  has entered the skeleton; and
- the imported file set is traceable to the reviewed wizard revision and its
  recorded sanitation diff.

Gate 5 accepts the automated-check outcomes, review protocol, and CI deadline.
Exact commands and CI jobs are selected in the shared PR #1 brief and execution
record. Production code remains blocked until all seven preparation gates and
the ready checkpoint are complete.

## Open implementation decisions

- concrete macOS IPC schemas, daemon and Mach identifiers, launchd policy,
  build wiring, and enforcement implementation under
  [ADR 0004](0004-macos-helper-ownership-and-lifecycle.md);
- iOS App Group schema, extension lifecycle details, and entitlement
  verification in Apple Task 0 and the first iOS enforcement pull request;
- SQLDelight schema, migration policy, and transaction ownership in the first
  local-replica pull request;
- state-holder and coroutine ownership in the first stateful-screen pull
  request;
- cryptographic implementation and cross-target format evidence under
  [ADR 0006](0006-apple-mvp-encrypted-operation-and-convergence.md) in the
  synchronization foundation pull request;
- deterministic CloudKit and Keychain bootstrap in the Apple synchronization
  pull request; and
- signing, notarization, TestFlight, App Store, and release-readiness details
  in their corresponding distribution and release reviews.

## Sources checked on 2026-08-25

- [Apple Device Activity monitor](https://developer.apple.com/documentation/deviceactivity/deviceactivitymonitor)
- [Apple interval-end callback](https://developer.apple.com/documentation/deviceactivity/deviceactivitymonitor/intervaldidend(for:))
- [Apple Family Controls entitlement request](https://developer.apple.com/documentation/familycontrols/requesting-the-family-controls-entitlement)
- [Metro multiplatform dependency graphs](https://zacsweers.github.io/metro/latest/multiplatform/)
- [Metro Kotlin compatibility](https://zacsweers.github.io/metro/latest/compatibility/)
- [Compose Multiplatform compatibility and versioning](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html)
