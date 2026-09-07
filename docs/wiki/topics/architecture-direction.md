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

`user-confirmed` (2026-09-07): native prototype design adoption is a
presentation migration, not an architecture replacement. Reusable components
belong to the existing `shared/core/designsystem`; real screens keep the
existing ViewModels, Metro ownership, persistence, and semantic platform
services. The prototype reducer, fixtures, and workbench stay isolated.
See the [adoption brief](../../tasks/specifications/design-001-mvp-design-adoption.md)
and [current design contract](../../../DESIGN.md). Native macOS window chrome
belongs to the desktop host, not the application-selection helper protocol.

`user-confirmed` (2026-09-07): the maintainer explicitly accepts the narrow
in-process AppKit/JNI window-presentation exception in
[ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md#design-001-window-presentation-amendment).
The signed, verified desktop leaf owns window chrome and contrast queries,
not privilege, enforcement, IPC, networking, or product policy. A native crash
can terminate the application; helper and synchronization boundaries stay intact.

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

`user-confirmed` (2026-08-28): source packages inside `:shared` are
feature-first. A feature uses `app.posato.feature.<feature>` and adds layer
subpackages only for responsibilities it actually has. TARGETS therefore owns
`feature.targets.domain`, `feature.targets.data`, and `feature.targets.ui`.
Reusable application-wide capabilities use named `app.posato.core.<capability>`
packages, currently `core.database` and `core.designsystem`; `core` itself is
not a miscellaneous bucket. The application shell and Metro composition roots
stay at `app.posato` and `app.posato.di`. Package structure neither mandates a
pass-through use case nor changes the rule that modules split only at a real
dependency boundary.

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
| macOS session helper | `app.posato.macos.helper` | Deferred | Normal-user Swift process for loopback proxy and native enforcement mechanics |
| macOS synchronization companion | `app.posato.macos.sync` | Deferred | Short-lived normal-user Swift process for CloudKit and synchronizable Keychain only |
| macOS proxy-settings daemon | Fixed `app.posato.macos`-namespaced identifier selected by MACOS-003 | Deferred | Minimal Swift root launch daemon for atomic SystemConfiguration ownership and recovery |

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

`user-confirmed` (2026-08-26):
[ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md)
selects a normal-user Swift session helper for the loopback proxy and native
enforcement mechanics plus a minimal Swift root launch daemon limited to
atomic SystemConfiguration ownership and recovery. Shared Kotlin owns
enforcement intent and policy. Both IPC boundaries authenticate fixed signed
peers, validate bounded versioned operations, and reconcile unknown outcomes
under stable request identity. Service Management and launchd own daemon
lifecycle; no custom watchdog, shell, `sudoers` or other persistent grant, or
general installer is part of the contract.

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

### MODEL-001 local policy persistence

`user-confirmed` (2026-08-27): MODEL-001 uses SQLDelight's standard database
lifecycle instead of a Posato-specific one. Desktop constructs
`JdbcSqliteDriver` and iOS constructs `NativeSqliteDriver` with the generated
synchronous schema bridge. Those drivers own fresh creation, `PRAGMA
user_version`, and later `.sqm` migrations. Posato does not add a sidecar schema
version, schema inventory, integrity preflight, recovery protocol, driver
decorator, store factory, or explicit store close API.

Both app-scoped Metro graphs bind the platform `SqlDriver`, generated
`PosatoDatabase`, and ready `LocalExactDomainPolicyStore` directly. SQLDelight
still generates suspending queries. Store reads and replacements use an
injected named database dispatcher and a standard SQLDelight transaction so a
read returns one committed revision-and-policy snapshot and a replacement
commits as one unit. After the revision compare-and-set acquires the write, a
replacement validates the previous logical state before deleting it; detected
corruption therefore aborts and rolls back the complete transaction instead of
silently resetting the replica. Desktop uses `Dispatchers.IO`. Kotlin/Native in
the pinned kotlinx.coroutines 1.10.2 build does not expose `Dispatchers.IO`
publicly, so iOS uses a single-parallelism `Dispatchers.Default` view without
introducing an owned executor.

The v1 schema contains only one revision row and canonical exact-domain rows.
Its generated `1.db` is the migration-verification baseline; the first `.sqm`
file is added only for a real v2. The revision constraint requires SQLite's
physical `integer` storage class so generated `Long` reads cannot coerce text or
real values into trusted revision state. The canonical-domain constraint
likewise requires the physical `text` storage class so generated `String` reads
cannot coerce BLOB values into trusted domains, and it applies the 253-byte
budget through BLOB-length semantics so an embedded NUL cannot hide an
oversized suffix. Restored domains are revalidated and bounded before becoming
trusted policy state, while ordinary query failures remain typed storage
failures. An invalid file is left to standard driver initialization and is not
silently replaced. The iOS configuration changes only its app-private base path
and installs a silent SQLiter logger while retaining the driver's default WAL
mode. The static iOS host continues to link system SQLite.

`observed` (2026-08-27): the focused real-SQLite contract passes on desktop JVM
and the iOS Simulator for fresh creation, atomic replacement, restart, empty
replacement, revision conflicts, rollback, stored-data bounds, redaction,
invalid-file preservation, malformed A-label rejection, non-integer revision
rejection, BLOB-domain rejection, oversized NUL-suffixed text rejection, and
injected dispatcher use. `inferred`: MODEL-001 fails closed on every reserved
`??--` label, including `xn--`, until TARGETS-001 supplies reviewed IDNA
validation and round-tripping rather than trusting the prefix alone.

### TARGETS-001 exact-domain management

`user-confirmed` (2026-08-27): the first stateful screen exposes one aggregate
immutable `uiState` produced by combining private policy, editor, and submission
flows. Its cold policy read starts only while the UI state is collected and is
shared with a five-second `WhileSubscribed` timeout. Retry is explicit; there
is no imperative initial-load call, pass-through use case, custom scope,
service locator, or navigation framework for this single-screen slice.

`user-confirmed` (2026-08-27): mutable text input is held in one
composable-owned `rememberTextFieldState`, not duplicated in the aggregate
immutable UI state or a ViewModel flow. The ViewModel receives the current text
only on submission and may expose an immutable editor-session revision for
field recreation. Compose Runtime state APIs remain permitted in the
ViewModel, but Compose Foundation text-input types do not. The first reviewed
screen establishes one application-root `PosatoTheme`, uses only Material 3
components, and enables the Compose Rules Detekt Material 2 prohibition. New
generic UI components and geometry tokens remain evidence-driven rather than
being created speculatively.

`user-confirmed` (2026-08-27): each product screen keeps exactly two preview
functions in its screen file and one separate preview data provider. Both
preview functions consume the same complete deterministic state sequence and
render through the state-and-callback overload without a ViewModel or platform
I/O.

`user-confirmed` (2026-08-27): the final framework `TextFieldState` exposes its
live text from `toString()`, so the redacted-default-string rule applies to
repository-owned carriers and diagnostics. The screen-owned framework state is
a narrow exception: keep it inside the text-field composable and never log,
diagnose, persist, or pass it to the ViewModel.

`observed` (2026-08-28): TARGETS-001 bounds raw input to 1,024 UTF-16 code units
before trimming or Unicode processing, converts accepted Unicode through the
pinned Kuri 0.1.0 Unicode 17 UTS-46 implementation, and then applies Posato's
stricter lowercase ASCII DNS, WHATWG
[ends-in-a-number](https://url.spec.whatwg.org/#ends-in-a-number) rejection,
and A-label round-trip checks. The resulting exact domains remain sorted,
bounded, redacted outside explicit UI rendering, and
are replaced directly through the MODEL-001 revision compare-and-set store.
Focused JVM and iOS Simulator tests cover canonicalization, malformed and
collapsing input, duplicates, edits, removal, conflicts, corruption,
cancellation, and subscription-driven loading.

`observed` (2026-08-27): the application root now owns one `PosatoTheme` backed
by stable Compose Multiplatform Material 3 1.9.0. The direct Material 2
dependency and source imports are absent. A controlled temporary Material 2
import failed Detekt through the enabled Compose Rules `Material2` check, and
the final graph passed JVM, both iOS compilation targets, iOS Simulator tests,
the desktop distributable, a credential-free iOS host build, and signed launch
on a physical iPhone. The domain `TextFieldState` is created only inside the
editor composable; the ViewModel receives a `String` on submission and exposes
only aggregate business and editor-session facts.

### TARGETS-002 semantic application group

`observed` (2026-08-28): the local target aggregate now contains sorted exact
domains and one optional semantic application-policy name. Raw names are
bounded and rejected for common C0/C1 controls before trimming, then normalized
to NFC through a narrow platform leaf and validated against a strict 80-byte
UTF-8 limit. Canonical values retain the control-character check and remain
redacted from repository-owned default strings. Stored names must already be
canonical; invalid persisted text fails as corruption rather than being
repaired silently.

`observed` (2026-08-28): SQLDelight schema v2 adds only the optional singleton
semantic name. The `1.sqm` migration preserves v1 revision and exact-domain
rows, while aggregate replacement keeps the existing revision compare-and-set
transaction and rolls back domains, name, and revision together. No platform
application identity, selection token, mapping state, or navigation event is
stored in the shared database.

`observed` (2026-08-28): the shared **Paused items** screen presents Websites
and Applications in one state holder. **Application group** is the visible
term, and an existing group truthfully reports that apps still need selection
on the current device without exposing a fake picker action. Independent
composable-owned text fields retain the last valid aggregate during validation,
storage, cancellation, and revision-conflict recovery. Load, conflict, and
corruption failures keep policy controls disabled until a successful reload,
while an ordinary save failure still permits another submission.

### Platform and toolchain baseline

- iOS deployment target: 18.0, rechecked against the current-and-previous-major
  product policy before release.
- macOS deployment target: 15.0 on arm64 for the first MVP, with x86-64 support
  requiring a separate acceptance decision.
- observed generation-time candidates: Kotlin 2.4.10, Compose Multiplatform
  1.12.0, Gradle 9.7.1, and Metro 1.4.2; these are not production pins.
- PR #1 uses JDK 21 and JVM bytecode target 17.
- `user-confirmed` (2026-08-27): `:shared` has an Android KMP library target
  only for common Compose preview tooling. It has no Android application,
  host, identifier, distribution artifact, or MVP feature claim; Android
  product work remains deferred.
- the exact stable Xcode version is selected in the skeleton task after a clean
  compatibility check against the pinned Kotlin and Compose versions; the
  compatible CI image is selected in the separate PR #1 CI task.

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
- verify the two platform graphs, behavior-focused shared tests when
  applicable, JVM build, iOS simulator build, and a structural diff against
  the generated archive.

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

- concrete macOS IPC schemas, fixed daemon and Mach identifiers, launchd
  policy, build wiring, packaging, and recovery tests under
  [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md);
- App Group schema, extension lifecycle details, and entitlement validation in
  Apple Task 0 and the first iOS enforcement pull request;
- persistence schema, migration policy, and transaction ownership in the first
  local-replica pull request;
- cryptographic implementation and cross-target vectors under
  [ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md)
  in the synchronization foundation pull request;
- deterministic Apple bootstrap under the accepted automatic signed-author
  contract in the Apple synchronization pull request;
- portable membership, folder-integrity, high-water-mark, and migration
  contracts in the portable-synchronization pull requests;
- exact CI jobs and runner details in the Gate 6 CI implementation task under
  the accepted Gate 5 deadline; and
- signing, notarization, TestFlight, App Store, and release-readiness details
  in the corresponding distribution and release reviews.

Each decision should be made at the smallest point where downstream code needs
it and then recorded as an ADR before parallel consumers depend on it.

## Accepted engineering execution boundary

`user-confirmed` (2026-08-25): the
[engineering quality contract](../../development/engineering-quality-contract.md)
defines ktlint formatting, Detekt analysis, Compose Rules through Detekt,
warning-free owned source, applicable test layers, proportional review, concise
task-local evidence, and the standing Definition of Done. The
[task workflow](../../tasks/README.md) keeps future work as roadmap stubs,
creates briefs just in time, and requires a pre-implementation review only for
named High-risk work.

`user-confirmed` (2026-08-27): Kotlin formatting uses one 150-character limit
across ktlint, Detekt, and Android Studio. Existing formatter controls enforce
the accepted expression-body layout. After repeated assignment-layout drift,
the repository-owned `posato:rhs-on-assignment-line` rule now requires the
first right-hand-side expression to start after `=` when it fits. The rule has
one named repository consumer, uses ktlint's syntax tree and line-length
configuration, and intentionally does not autocorrect. Multiline raw strings
retain the line break required by standard ktlint. Call-chain continuation
remains authored guidance because no equivalent repeated drift was established.

`user-confirmed` (2026-08-28): `when` entry arrows remain on the final
condition line when they fit. The repository-owned
`posato:when-entry-arrow-on-condition-line` ktlint rule enforces that narrow
layout without autocorrect and accepts intervening comments and over-limit
conditions. Ktlint's conflicting declaration-site trailing-comma rule is
disabled; call-site trailing commas remain enforced.

Gate 5 records the CI outcome but does not configure a pipeline. The Gate 6
roadmap groups the foundation, local quality, and CI milestones into one PR #1
execution and review cycle completed before PR #1 merges or the first parallel
implementation wave starts, whichever occurs first.

`user-confirmed` (2026-09-07, latest decision): disable GitHub CI and remove its
workflow source and required status check. Local `./gradlew quality` plus
proportional review is the procedural merge gate; GitHub cannot verify its
result. Preserve the PR requirement, administrator enforcement, and force-push/
deletion restrictions. This supersedes the same day's manual-CI and required
hosted-check decisions, with no automatic restoration date. The quality contract
owns the rule and the development guide owns the local merge checklist.

`observed` (2026-09-07): the aggregate gate includes native Swift XCTest on
an isolated temporary iOS Simulator as well as Kotlin tests. Physical-device
cases retain explicit skips and do not become Simulator coverage claims.
The local gate also compiles unsigned Debug device and Release Simulator hosts,
preserving device-only Swift branch coverage after hosted CI removal. Matching
Kotlin frameworks are built first; these are compilation checks, not device runs.

`observed` (2026-09-07): a macOS 15/Xcode 26.3 CI reproduction isolated native
test thread-pool starvation. Three parallel synchronous proxy tests blocked in
socket reads while the upstream utility-queue work did not start until their
deadlines released workers. The same runner passed single/serialized controls
and all 151 native tests serialized, without changing deadlines or assertions.
The parallel suite also exposed the same class of wait in lease-renewal tests.
Blocking test orchestration must not occupy Swift concurrency workers while
waiting for queued future work. The maintainer authorized an asynchronous test
boundary: dedicated test threads perform blocking operations and checked
continuations resume the test task, which retains assertion attribution.
The native suite keeps parallel execution and its existing deadlines;
serialization remains a diagnostic control, not a permanent reduction in
concurrency coverage. This matches the
[Swift runtime explanation](https://forums.swift.org/t/deadlock-when-using-dispatchqueue-from-swift-task/66058/25).
No runtime proxy fix or timeout increase follows from this experiment.
