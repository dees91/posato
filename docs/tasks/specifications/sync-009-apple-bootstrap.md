# `SYNC-009`: Integrate Keychain and CloudKit bootstrap on both apps without creating a parallel workspace

- **Review tier:** `high-risk`
- **Tier reason:** First change in which a real Apple account, the
  synchronizable Keychain, and the private CloudKit database are driven by the
  shipped applications rather than by tests. A wrong composition creates a
  second workspace, deletes another device's key item, touches iCloud without
  the person's consent, or exposes key bytes through a graph or a log; it also
  owns both dependency-injection graphs and both entry points, and it runs in
  parallel with `QUALITY-005` and `SESSION-003`.
- **Dependencies:** completed `SYNC-002` (operation core), `SYNC-004`
  (`BootstrapCoordinator`, `SqlBootstrapStore`, phases), `SYNC-005`
  (`IosBootstrapKeychainAdapter`), `SYNC-006`
  (`MacOsBootstrapKeychainAdapter`), `SYNC-007` (`IosBootstrapCloudAdapter`),
  `SYNC-008` (`MacOsBootstrapCloudAdapter`), `APPLE-002` (development
  provisioning), `QUALITY-002` and `QUALITY-004` (driver)
- **Integration group:** `PR-APPLE-BOOTSTRAP`
- **Authority:** `SYNC-009` in MVP roadmap revision 10 (wave P3/W3.6; this task
  does not amend the roadmap),
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
  ("Bootstrap runs only after the explicit **Sync with iCloud** action and an
  available account outcome"; the eight-step order; a missing item is
  `waiting-for-workspace-key` and "never generates a key, replaces the anchor,
  interprets the workspace as empty, or creates another workspace"; "Removing a
  workspace is a separate explicit destructive action"; `SYNC-009` proves one
  physical Mac-and-iPhone bootstrap "in both directions … without manual
  repair, cross-account deletion, or a second workspace"),
  [ADR 0002](../../decisions/0002-synchronization-trust-and-workspace-modes.md)
  (one **Sync with iCloud** action per installation is the consent gate; one
  workspace; no product account),
  [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md)
  (injection, no platform type in `commonMain`),
  [`DESIGN.md`](../../../DESIGN.md) (today the UI states that synchronization is
  not connected; any consent surface added here is a design change), and the
  diagnostics and privacy policies

## Outcome

On the development-signed Mac and the development-signed iPhone, the person's
explicit consent action runs the existing `BootstrapCoordinator` over the real
account, key, and cloud adapters, and the two devices end on exactly one
workspace: the second device joins the workspace the first established, or
waits truthfully for the key, in either device order, without ever creating a
second anchor, deleting the other device's item, or touching iCloud before the
action.

## Boundaries

- Compose only what exists: `MacOsSyncCompanionClient.verified(...)`, the two
  macOS adapters, `JdkSyncCryptoProvider`, and `SqlBootstrapStore` in the
  desktop graph; the two iOS adapters and `IosSyncCryptoProvider` in the iOS
  graph. No new bootstrap phase, port method, wire operation, or persistence
  shape.
- Nothing reaches CloudKit or the synchronizable Keychain before the explicit
  action resolved in `D1`. Construction is allowed at graph time; provider
  access is not.
- The companion client is constructed lazily and never fatally.
  `MacOsSyncCompanionVerifier.verify` is a chain of `check(...)` and throws
  whenever the desktop application is not a verifiable signed package, which is
  the normal case for a plain Gradle run and for a driver launch of an ad-hoc
  staged package. An unverifiable companion yields a truthful non-`Ready`
  outcome; it never blocks or crashes startup.
- Single-flight is process-scoped, not graph-scoped.
  `ComposeRoot.makeUIViewController` can build a second runtime in the same iOS
  process, and two coordinators hold two independent mutexes, which is exactly
  the second-candidate failure this task must prevent.
- Adopt the `SYNC-007` deferral only: production provider construction and the
  off-main-thread rule, which writes to
  `shared/src/iosMain/kotlin/app/posato/feature/sync/data/**`. Do not adopt the
  `SYNC-008` preflight and postflight helper refactor; it rewrites the paths
  that carry the binding security semantics, adds nothing to this outcome, and
  enlarges a High-risk diff. Record it as still deferred.
- Write surface: `shared/src/{jvmMain,iosMain}/**/di/**`, the narrow
  `commonMain` composition entry point, the iOS sync adapter directory above,
  `desktopApp/**/Main.kt`, `iosApp/iosApp/iosApp.swift`, and
  `MainViewController.kt`. `QUALITY-005` may edit the window-property block of
  the same `Main.kt`; the regions are disjoint and that task merges first, so
  this branch rebases onto it. No replica-store wiring: it has no consumer
  until `SYNC-010`.
- Non-goal: publishing or consuming bundles, sync status copy, retry
  affordances, and onboarding presentation. `SYNC-010` and `ONBOARDING-001` own
  those. This task exposes the established context to the graph only.
- Non-goal: `feature/session/**`, `feature/enforcement/**`, the session schema,
  and `tools/posato-control/**`, which belong to `SESSION-003` and
  `QUALITY-005` in this wave.
- The workspace key never reaches the local database, a graph accessor that
  outlives the read, a `toString()`, a log, or driver evidence.

## Acceptance

- `AC-01` — Before the explicit action, no CloudKit or synchronizable-Keychain
  access occurs on either platform, and no bootstrap state is written. After
  it, one attempt establishes one zone, one anchor, and one Keychain item; a
  relaunch adopts the established workspace and creates nothing.
- `AC-02` — With the anchor already established by the other device and the key
  item not yet delivered, the app reports `waiting-for-workspace-key`, creates
  no anchor and no key, and adopts the delivered item on a later attempt. This
  holds in both device orders, Mac first and iPhone first.
- `AC-03` — Simultaneous opt-in converges on one anchor, observed as exactly
  one workspace record, exactly one surviving Keychain account, the losing
  candidate's own item absent, and the winner's item still readable on the
  winner's device after that cleanup.
- `AC-04` — Bootstrap never runs on the main thread and never runs twice
  concurrently in one process, including when a second runtime is built. An
  account that is unavailable, restricted, or switched mid-flight yields a
  truthful non-`Ready` outcome with no partial establishment.
- `AC-05` — An unpackaged or unverifiable macOS companion yields a truthful
  non-`Ready` outcome and never blocks startup, so a plain Gradle run and a
  driver launch of an ad-hoc staged package both keep working.
- `AC-06` — `./gradlew quality` passes with no new suppression; no account
  binding, anchor identifier, key byte, or account record name appears in any
  `toString()`, log, diagnostic, or committed evidence.

## Verification

- `commonTest` over fakes for the composition entry point: process-scoped
  single-flight under concurrent callers including a second runtime, the
  established-context read, and no provider access before the action.
- `jvmTest` and `iosTest` for graph composition: adapters built once, the
  companion client verified lazily, an unverifiable companion degrading to a
  truthful outcome, and an assertion on the dispatcher the real graph injects,
  not only on an injected fake.
- Physical run on the provisioned Mac and iPhone under the maintainer's iCloud
  account covering `AC-01` through `AC-04` in both device orders, with the user
  interface interactive while an adapter blocks, because both native providers
  bridge on a bounded semaphore and a main-thread call would deadlock rather
  than stutter. Evidence under the ignored `build/verification/`.
- `./gradlew quality`, `git diff --check`, and the scoped secret and path scan.

## Decisions or blockers

- Blocking (`D1`, maintainer): how the explicit **Sync with iCloud** action is
  represented. ADR 0007 and ADR 0002 make it the consent gate, and `DESIGN.md`
  currently states that synchronization is not connected, so no surface exists
  and `ONBOARDING-001` owns the full flow but depends on this task.
  Recommendation: add the minimal explicit action in the accepted design
  language and amend `DESIGN.md` for that one control, rather than amending
  ADR 0007 to let bootstrap run unprompted. Implementation does not start until
  this is answered; running bootstrap at graph construction or on first screen
  entry would silently amend an accepted decision.
- Blocking (`D2`, maintainer): destructive removal. ADR 0007 requires `SYNC-009`
  to prove "destructive removal under the established binding" and also calls it
  "a separate explicit destructive action", but `BootstrapCloudPort` has no zone
  delete and the existing `deleteZoneAndVerifyAbsent` sits on the mailbox
  adapters outside the coordinator. Recommendation: record a maintainer
  deferral of the removal flow to `SYNC-010` and drop it from this task's
  evidence, because building it here needs a new port method and a consent
  surface of its own. Calling the mailbox adapter directly from a test or the
  driver would not exercise the product path and is not acceptable evidence.
- Open (`D3`): what the graph exposes. Recommendation: the coordinator behind a
  narrow facade plus a suspending established-context read. `BootstrapCoordinator`,
  `WorkspaceKeyValue`, and `SyncContext` are `internal` today; making
  `WorkspaceKeyValue` public would create exactly the key exposure this
  decision exists to prevent.
- Physical gate: one iPhone and one Mac under the maintainer's iCloud account,
  sequential with any other physical work in this wave.
