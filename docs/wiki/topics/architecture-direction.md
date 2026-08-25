# Architecture Direction

## Accepted direction

- `user-confirmed`: the target stack is Kotlin Multiplatform and Compose
  Multiplatform. Apple-first means platform and storage priority, not a
  Swift-only implementation.
- `user-confirmed`: product logic and orchestration are Kotlin-first.
- `user-confirmed`: Apple APIs sit behind small semantic interfaces.
- `user-confirmed`: Swift remains only where Kotlin/Native access or ownership
  is impractical.
- `user-confirmed`: native Apple types must not cross into `commonMain`.
- `user-confirmed`: no separate architecture spike will precede MVP work. The
  first relevant vertical MVP PR may settle a native boundary before dependent
  PRs build on it.
- `user-confirmed` (2026-08-25):
  [ADR 0002](../../decisions/0002-synchronization-trust-and-workspace-modes.md)
  separates transport, payload encryption, workspace-key delivery, and device
  admission. Apple and portable transports share one encrypted operation model
  but use mode-specific key delivery and admission.

These constraints and the Gate 4 baseline select the initial production graph.
They do not preselect every later dependency, persistence detail, native-helper
implementation, or distribution process.

## Accepted Gate 4 baseline

- **Status:** Accepted
- **Accepted:** 2026-08-25
- **Decision authority:**
  [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md)

### User-confirmed inputs

- The initial skeleton should be generated with the
  [Compose Multiplatform Wizard](https://terrakok.github.io/Compose-Multiplatform-Wizard/)
  and then adapted under repository review.
- Metro is the only dependency-injection framework for the greenfield graph.
  Koin and other DI containers must not enter the same application graph.
- Agents creating Android or Compose Multiplatform code should use the named
  `android-compose-engineering` skill when it is available. The repository must
  still contain every durable architecture and quality rule needed when that
  local skill is unavailable.

The reviewed generator evidence and import limits are recorded in the
[wizard source audit](../sources/compose-multiplatform-wizard.md).

### Initial module graph

```text
iosApp (Xcode host)
    -> :shared (KMP, Compose, and Metro plugin)
           commonMain
           commonTest
           iosMain
           jvmMain
    <- :desktopApp (JVM entry point and macOS packaging)

Later native targets, not PR #1 dependencies:
    iosActivityMonitorExtension
    macosHelper
```

`:shared` begins as the only KMP production module. It owns the application
composable, shared product and presentation types, semantic platform contracts,
and test fakes. It may be split only when a real dependency boundary appears;
the skeleton does not create empty domain, data, feature, or utility modules.

`:desktopApp` owns the JVM `main`, application window, macOS packaging, and the
desktop composition root. `iosApp` owns the Swift application lifecycle and
embeds the static framework produced by `:shared`. Both hosts delegate
immediately to shared UI and do not own product decisions.

### Source-set and Metro ownership

- `commonMain` owns shared UI, product state, typed outcomes, platform-neutral
  interfaces, and an unannotated canonical graph contract.
- `commonTest` owns fakes and contract tests. Generated click-counter tests are
  not retained.
- `iosMain` owns the Compose controller factory, iOS adapters, and the final
  `@DependencyGraph` that extends the common graph contract.
- `jvmMain` owns desktop adapters and its final `@DependencyGraph`.
- Platform hosts create one graph at their composition root and pass only the
  required entry surface into shared Compose.

Metro uses constructor injection. Platform values enter through explicit graph
factory inputs or platform binding containers. UI, domain, and data code do not
call a service locator. Compile-time graph validation is required for both
`iosMain` and `jvmMain`.

### Platform target graph

| Target | Identifier | PR #1 | Boundary |
| --- | --- | --- | --- |
| iOS application | `app.posato.ios` | Created | Swift host and shared Compose framework |
| macOS application | `app.posato.macos` | Created | Compose Desktop/JVM application |
| iOS activity monitor extension | `app.posato.ios.activitymonitor` | Deferred | Swift/Xcode-owned expiry callback and minimum shared app-group state |
| macOS native helper | `app.posato.macos.helper` | Deferred | Signed native process behind authenticated, versioned local IPC |

No Android, Web, custom shield-action, custom shield-configuration, or Device
Activity report target enters the MVP skeleton. The activity-monitor extension
is required because Apple documents `DeviceActivityMonitor` as the entry point
for scheduled interval callbacks, while the accepted MVP requires restrictions
to clear after normal expiry even when the main application is not running.
Apple also states that callbacks occur when the device is in use, so UI and
tests must not claim wall-clock background execution at the exact end instant.

The iOS application and activity-monitor extension require separate Family
Controls distribution approval. Their minimum shared local state uses an App
Group whose exact schema and identifier are settled in Apple Task 0 and the
first iOS enforcement pull request. Gate 7 registers identifiers and
capabilities. Default system shields remain sufficient; custom shield
extensions are not an MVP requirement.

The macOS helper owns only native mechanisms such as system-proxy mutation and
application observation. Shared Kotlin owns enforcement intent. IPC uses
bounded versioned frames, request identity, timeouts, structured outcomes,
peer authentication, and safe repeatable cleanup. Helper language, privilege
installation, update, and recovery details are deferred to the first macOS
enforcement pull request.

### Wizard and dependency selection

Generate in an isolated temporary directory with **Posato**, `app.posato`, iOS,
Desktop, and Metro selected. Leave Android, Web, generated `AGENTS.MD`, sample
tests, and every other optional library unselected. Import reviewed files only.

PR #1 uses Compose and Metro because it creates the real shared application
entry and two compile-time platform graphs. It does not add Navigation 3,
AndroidX ViewModel, coroutines, serialization, Ktor, SQLDelight, image loading,
settings, date-time, logging, or build-config libraries without production code
that uses them.

The accepted later defaults are Navigation 3 for the first multi-screen flow,
AndroidX Multiplatform ViewModel for the first stateful screen, and SQLDelight
for the first local-replica slice. Exact coordinates are selected and verified
in those named pull requests. Ktor is not implied by CloudKit and has no current
Apple MVP consumer.

### Platform and toolchain baseline

- iOS deployment target: 18.0, rechecked against the current-and-previous-major
  product policy before release.
- macOS deployment target: 15.0 on arm64 for the first MVP, with x86-64 support
  requiring a separate acceptance decision.
- observed generation-time candidates: Kotlin 2.4.10, Compose Multiplatform
  1.12.0, Gradle 9.7.1, and Metro 1.4.2; these are not production pins.
- PR #1 uses JDK 21 and JVM bytecode target 17.
- no Android Gradle Plugin in the Apple-only graph.
- the exact stable Xcode version and CI image are selected in PR #1 after a
  clean compatibility check against the pinned Kotlin and Compose versions.

The wizard is a one-time source generator, not an update mechanism. PR #1
records the reviewed wizard revision or archive hash and then owns every
generated file. Versions live in the Gradle version catalog and wrapper;
updates are explicit reviewed changes rather than regeneration.

### PR #1 sanitation

Before any generated file enters the repository:

- rename `sharedUI` to `shared` and correct all Gradle and Xcode references;
- replace `.iosApp` and `.desktopApp` identifiers with the accepted candidates;
- keep only DMG/macOS desktop packaging;
- remove generated sample UI, theme toggle, custom font, external link, sample
  test, generated README, generated agent instructions, and unused resources;
- remove the generated rocket icon and packaging references until an accepted
  production icon exists;
- implement only the four accepted shell text elements from `DESIGN.md`;
- adapt every retained Kotlin declaration to repository code rules; and
- verify the two platform graphs, shared tests, JVM build, iOS simulator build,
  and a structural diff against the generated archive.

`user-confirmed` (2026-08-25): the maintainer accepted the module and source-set
graph, Metro ownership, target identifiers, Device Activity monitor extension,
separate macOS helper and IPC boundary, arm64-only macOS support, deployment
targets, wizard-import limits, and toolchain-selection policy. Gate 4 is
complete; [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md)
is the durable authority.

## PoC feasibility observation

`observed`: the synchronization PoC compiled and ran shared Kotlin domain,
SQLDelight persistence, cryptographic orchestration, synchronization,
lifecycle, state presentation, and Compose UI on desktop JVM and iOS
Kotlin/Native. The iOS host injected Swift implementations of Kotlin-declared
Apple service interfaces. The desktop JVM process reached Apple services
through a separately signed Swift child process and a bounded local protocol.

`observed`: this arrangement passed the accepted one-Mac/one-iPhone feasibility
matrix. It establishes that the broad shape can work; it does not establish
that the Swift helper, module names, IPC protocol, or PoC runtime should be
copied into the MVP.

## Intended ownership

### Shared Kotlin

Shared code should own behavior that expresses product meaning:

- policy, schedule, and bounded-session semantics;
- identifiers, validation, deterministic ordering, merge, and conflict rules;
- local-first state, pending work, and transport-independent sync orchestration;
- encryption workflow and canonical product data formats;
- Apple automatic author registration and portable membership, enrollment,
  recovery, and revocation policy;
- truthful presentation state and shared Compose UI;
- semantic platform contracts and fakes.

### Platform leaves

Platform code should own mechanics that cannot be made portable without
leaking implementation details:

- Apple lifecycle and application host integration;
- entitlements, signing, provisioning, and extension targets;
- CloudKit and Keychain calls;
- user-selected folder access and provider-specific filesystem mechanics;
- Family Controls, Managed Settings, system proxy settings, application
  observation, and browser-specific presentation;
- conversion between Apple framework values and platform-neutral values;
- process ownership and packaging for privileged or native helpers.

Business decisions must not migrate into platform adapters merely because an
SDK call is nearby.

## Boundary rules

- Public shared contracts use product language, opaque bytes, stable IDs,
  bounded collections, structured outcomes, and versioned payloads.
- Apple errors and framework objects are translated at the platform edge.
- Inputs from cloud storage, native processes, files, opaque platform tokens,
  and IPC are untrusted and validated at entry.
- Platform adapters are inert on construction; activation and cleanup are
  explicit, bounded, and safe to repeat.
- Local state remains authoritative. A cloud transport or native helper must
  not become a second hidden domain queue.
- Transport, payload protection, workspace-key delivery, and device admission
  remain separate semantic capabilities. A platform adapter must not silently
  define another workspace authority or cryptographic format.
- A failed sync or enforcement update must preserve the last valid local state
  and expose a truthful failure category.
- Process boundaries require bounded frames, timeouts, request identity,
  cancellation behavior, redacted errors, and safe crash recovery.

## Clean implementation policy

MVP code is written in this repository. PoC code may supply a test
case, algorithm, contract idea, or narrowly reviewed fragment, but every reuse
must be reconsidered against the accepted MVP architecture and quality bar.

The product must not begin by renaming the PoC modules or gradually deleting
probe surfaces. Experimental runners, synthetic policies, command-driven app
entry points, development signing assumptions, and cleanup infrastructure are
not an application skeleton.

## Decisions deferred to named implementation work

- macOS helper language, privilege model, installation, update, recovery, and
  uninstall lifecycle in the first macOS enforcement pull request;
- App Group schema, extension lifecycle details, and entitlement validation in
  Apple Task 0 and the first iOS enforcement pull request;
- persistence schema, migration policy, and transaction ownership in the first
  local-replica pull request;
- production cryptographic encoding and pinned providers in the
  synchronization foundation pull request;
- deterministic Apple bootstrap and signed-author registration in the Apple
  synchronization pull request;
- portable membership, folder-integrity, high-water-mark, and migration
  contracts in the portable-synchronization pull requests;
- CI details in Gate 5; and
- signing, notarization, TestFlight, App Store, and release-readiness details
  in the corresponding distribution and release reviews.

Each decision should be made at the smallest point where downstream code needs
it and then recorded as an ADR before parallel consumers depend on it.
