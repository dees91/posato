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

These constraints are accepted directions. They do not select a complete
module graph, dependency set, build layout, or production process model.

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
- membership, enrollment, recovery, and revocation policy once accepted;
- truthful presentation state and shared Compose UI;
- semantic platform contracts and fakes.

### Platform leaves

Platform code should own mechanics that cannot be made portable without
leaking implementation details:

- Apple lifecycle and application host integration;
- entitlements, signing, provisioning, and extension targets;
- CloudKit and Keychain calls;
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

## Decisions required before dependent implementation

- source-set and Gradle module graph;
- desktop application target and packaging model;
- macOS native/helper language, privilege model, installation, update, and
  recovery lifecycle;
- iOS host and required extension targets;
- dependency injection and lifecycle ownership;
- persistence schema and migration policy;
- production cryptographic encoding and pinned providers;
- minimum operating-system and architecture support;
- CI, signing, notarization, TestFlight, and App Store boundaries;
- versioning of local database, sync data, IPC, and exported recovery material.

Each decision should be made at the smallest point where downstream code needs
it and then recorded as an ADR before parallel consumers depend on it.
