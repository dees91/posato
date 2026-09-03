# `SYNC-004`: Implement the one-workspace bootstrap coordinator

- **Review tier:** `high-risk`
- **Tier reason:** The task generates the workspace key in memory, arbitrates
  the sole workspace across two devices, persists account-binding and candidate
  state, and freezes the Kotlin port contract that the Keychain and CloudKit
  adapters implement; a defect can create a parallel workspace or an
  unrecoverable key loss.
- **Dependencies:** completed `SYNC-003`; `SYNC-002` supplies the identifier
  types, the crypto provider's random source, and the replica store
- **Integration group:** `PR-BOOTSTRAP-CORE`
- **Authority:** `SYNC-004` in MVP roadmap revision 7,
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md),
  ADR 0006, ADR 0002, and the threat model (`A-04`, `TB-02`, `TB-06`, `T-03`,
  `T-04`, `T-06`)

## Outcome

Common Kotlin runs the ADR 0007 bootstrap protocol against deterministic fake
CloudKit, Keychain, and account ports and, through delay, conflict, failure,
account change, and restart, ends with exactly one established workspace or a
truthful waiting, retryable, or action-required status, never a replacement
key or a second workspace.

## Boundaries

- Implement a new `feature/sync/bootstrap` package in the shared module: the
  serialized coordinator, the none, candidate, and established state machine,
  the semantic status, and platform-neutral ports for exact zone fetch and
  save, anchor read and create, workspace-key create-if-absent, exact read,
  and delete-and-verify-absent, account-binding resolution, and random bytes.
  Ports expose only the ADR 0007 outcomes; Apple types, raw account
  identifiers, and error text never cross them.
- Persist the candidate identifiers, the established workspace, and the
  32-byte opaque account binding atomically in the app-private SQLDelight
  database through a new migration. The workspace key is never persisted and
  its buffer is cleared after use. Existing schema data survives unchanged.
- The established workspace is the only `SyncContext` the replica store may
  open; the coordinator exposes it and a binding-gated key read for
  `SYNC-009` to hand to the operation core.
- Deterministic fakes and contract tests are the evidence. No native adapter,
  DI wiring, UI action, retry timing, CloudKit schema deployment, or physical
  claim; the **Sync with iCloud** action and both platforms' wiring stay with
  `SYNC-009`.
- Exact losing-candidate cleanup is in scope because the protocol requires
  it. Explicit workspace removal, disable and re-enable, mailbox exchange,
  `CKSyncEngine` state, key rotation, and recovery are not.
- `.research/blocker` `WorkspaceBootstrap.kt` is read-only behavioral
  evidence; re-derive the shape, ports, and tests here.
- Exclusive write surface while `TARGETS-005` runs in parallel:
  `shared/src/{commonMain,commonTest}/**/feature/sync/**`,
  `shared/src/commonMain/sqldelight/**` (new migration only), and
  `docs/wiki/topics/cross-device-synchronization.md`. Do not touch
  `feature/targets/**`, `iosApp/**`, the DI graphs, or the iOS entry point.

## Acceptance

- `AC-01` — A fresh database without the zone, an existing zone, concurrent
  zone creation, a crash after zone save, a zone-save timeout or lost
  response, and zone absence after establishment follow ADR 0007 exactly,
  including the action-required rule.
- `AC-02` — Two coordinators over one shared fake provider converge on one
  anchor; the loser deletes and verifies absence of only its own candidate
  item and adopts the winner's item.
- `AC-03` — An existing anchor with a missing item waits; delayed delivery,
  exact duplicate, different duplicate, unknown provider outcome, corrupt
  length, checksum, or context, and a persisted candidate whose item becomes
  unavailable never generate or replace a key or anchor.
- `AC-04` — A crash at every persistence boundary resumes with the same
  candidate and bytes; account change before and after an indeterminate zone
  or anchor save, a postflight switch whose event arrives after the provider
  result, and return to the original account leave exactly one binding and
  no cross-account create, cleanup, or exposed key bytes.
- `AC-05` — The migration keeps existing target-policy and replica data; no
  bootstrap state, binding, or key appears in any `toString()`, log, or
  diagnostic; `./gradlew quality` passes on JVM and iOS Simulator.

## Verification

- Contract tests with deterministic fakes in `commonTest` on JVM and iOS
  Simulator, naming each ADR 0007 "required downstream evidence" case for
  `SYNC-004` as a test, plus the enumerated redaction test for new carriers.
- Real-database SQLDelight tests for the migration and the atomic candidate
  and established commits; `./gradlew quality`; `git diff --check`;
  suppression and private-data scans.
- Independent plan review before implementation and independent
  completed-change review with evidence.

## Decisions or blockers

- Recommended, needs maintainer confirmation: explicit workspace removal stays
  outside `SYNC-004` and joins `SYNC-009`, where its physical proof lives; the
  delete-and-verify-absent port required for losing-candidate cleanup is the
  primitive removal will reuse.
- Recommended: bootstrap state lives in a new singleton table rather than in
  `sync_replica_state`, so the replica row keeps meaning "established" and
  `open(context)` needs no change.
- No physical device is required; adapters and physical evidence belong to
  `SYNC-005` through `SYNC-009`.
