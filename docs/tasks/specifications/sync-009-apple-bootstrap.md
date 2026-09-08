# `SYNC-009`: Integrate Keychain and CloudKit bootstrap on both apps without creating a parallel workspace

- **Review tier:** `high-risk`
- **Tier reason:** First change in which a real Apple account, the
  synchronizable Keychain, and the private CloudKit database are driven by the
  shipped applications rather than by tests. A wrong composition creates a
  second workspace, deletes another device's key item, or exposes key bytes
  through a graph or a log; it also owns both dependency-injection graphs and
  both entry points, and it runs in parallel with `QUALITY-005` and
  `SESSION-003`.
- **Dependencies:** completed `SYNC-002` (operation core), `SYNC-004`
  (`BootstrapCoordinator`, `SqlBootstrapStore`, phases), `SYNC-005`
  (`IosBootstrapKeychainAdapter`), `SYNC-006`
  (`MacOsBootstrapKeychainAdapter`), `SYNC-007` (`IosBootstrapCloudAdapter`),
  `SYNC-008` (`MacOsBootstrapCloudAdapter`), `APPLE-002` (development
  provisioning), `QUALITY-002` and `QUALITY-004` (driver)
- **Integration group:** `PR-APPLE-BOOTSTRAP`
- **Authority:** `SYNC-009` in MVP roadmap revision 10 (wave P3/W3.6),
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
  (the eight-step bootstrap order; a missing item is `waiting-for-workspace-key`
  and "never generates a key, replaces the anchor, interprets the workspace as
  empty, or creates another workspace"; `SYNC-009` proves one physical
  Mac-and-iPhone bootstrap "without manual repair, cross-account deletion, or a
  second workspace"),
  [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md)
  (injection, no platform type in `commonMain`),
  [ADR 0002](../../decisions/0002-synchronization-trust-and-workspace-modes.md)
  (one workspace, no product account), `DESIGN.md` "Future Product Contracts"
  (no synchronization surface is promised in this MVP screen set), and the
  diagnostics and privacy policies

## Outcome

On the development-signed Mac and the development-signed iPhone, each
application composes its real account, key, and cloud adapters into the
existing `BootstrapCoordinator`, runs the bootstrap once per launch off the
main thread, and persists exactly one workspace: the second device joins the
workspace the first device established, or waits truthfully for the key,
without ever creating a second anchor or deleting the other device's item.

## Boundaries

- Compose only what exists: construct `MacOsSyncCompanionClient.verified(...)`,
  the two macOS adapters, `JdkSyncCryptoProvider`, and `SqlBootstrapStore` in
  the desktop graph, and the iOS adapters plus `IosSyncCryptoProvider` in the
  iOS graph. No new bootstrap phase, port method, wire operation, or
  persistence shape.
- Own the composition surface: `shared/src/{jvmMain,iosMain}/**/di/**`, the
  narrow bootstrap entry point in `commonMain` that owns single-flight and the
  background dispatcher, `desktopApp/**/Main.kt`, `iosApp/iosApp/iosApp.swift`,
  and `MainViewController.kt`. Adopt the two deferred items recorded by earlier
  tasks: coordinator threading and production provider construction
  (`SYNC-007`) and the repeated preflight and postflight helper (`SYNC-008`).
- Non-goal: any user-visible synchronization state, onboarding copy, retry
  affordance, or bundle exchange. `SYNC-010` owns publishing and consuming
  bundles and the truthful status surface; `ONBOARDING-001` owns first-install
  presentation. This task exposes the established context to the graph only.
- Non-goal: touching `feature/session/**`, `feature/enforcement/**`, the
  verification driver's own sources, or the session schema. Those belong to
  `SESSION-003` and `QUALITY-005` in this wave.
- The workspace key never reaches the local database, a graph accessor that
  outlives the read, a `toString()`, a log, or driver evidence.

## Acceptance

- `AC-01` — On a fresh private database on both platforms, one launch
  establishes one zone, one anchor, and one Keychain item; a second launch and
  an app restart adopt the established workspace and create nothing.
- `AC-02` — With the anchor already established by the other device and the key
  item not yet delivered, the app reports `waiting-for-workspace-key`, creates
  no anchor and no key, and adopts the delivered item on a later attempt.
- `AC-03` — Simultaneous opt-in on both devices converges on one anchor; the
  losing candidate deletes and verifies absence of only its own item, and no
  cross-account or cross-device deletion occurs.
- `AC-04` — Bootstrap never runs on the main thread on either platform, never
  runs twice concurrently in one process, and an account that is unavailable,
  restricted, or switched mid-flight yields a truthful non-`Ready` outcome with
  no partial establishment.
- `AC-05` — `./gradlew quality` passes with no new suppression; no binding,
  anchor identifier, key byte, account record name, or container identifier
  appears in any `toString()`, log, diagnostic, or committed evidence.

## Verification

- `commonTest` over fakes for the composition entry point: single-flight under
  concurrent callers, dispatcher choice, and the established-context read after
  a completed bootstrap.
- `jvmTest` and `iosTest` for the graph-level composition: the adapters are
  built once, the companion client is verified before use, and a failed
  companion verification degrades to a truthful outcome instead of throwing
  into the UI.
- Physical run on the provisioned Mac and iPhone under the maintainer's iCloud
  account, covering `AC-01` through `AC-04`, including one destructive removal
  under the established binding, with the private database left as found.
  Evidence under the ignored `build/verification/`.
- `./gradlew quality`, `git diff --check`, and the scoped secret and path scan.

## Decisions or blockers

- Open (maintainer decision before implementation): when bootstrap runs. Two
  candidates: at graph construction on every launch, or lazily on first screen
  entry. Recommendation: at graph construction, off the main thread, with a
  single-flight guard, because `SYNC-010` needs an established context before
  any bundle exchange and no screen owns synchronization yet.
- Open: what the graph exposes. Recommendation: the coordinator plus a
  suspending established-context read, never a stored `WorkspaceKeyValue`, so
  no key outlives its use.
- Physical gate: one iPhone and one Mac under the maintainer's iCloud account.
  The device rows are sequential with any other physical work in this wave.
