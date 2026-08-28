# `MODEL-001`: Persist the local exact-domain policy atomically

- **Review tier:** `high-risk`
- **Tier reason:** The change stores a sensitive enforcement policy and defines
  database transaction and schema behavior whose failure could lose the last
  valid local policy.
- **Dependencies:** `FOUNDATION-001`, `SECURITY-001`, `DIAGNOSTICS-001`
- **Integration group:** `PR-LOCAL-REPLICA`
- **Authority:** `MODEL-001` in the accepted MVP roadmap

## Outcome

The app-scoped Metro graph provides a shared store that persists one bounded
exact-domain policy atomically in app-private SQLite and restores it after an
app restart on macOS/JVM and iOS.

## Boundaries

- Store only a revision and canonical exact-domain values. An empty replacement
  atomically removes every domain row; the policy otherwise has no TTL.
- Use SQLDelight's generated schema, standard platform drivers, schema version,
  and migration mechanism. Do not add a custom driver, lifecycle manager,
  schema attestation, sidecar version, or recovery protocol.
- Generate suspending queries. Run every store read and replacement on an
  injected named database dispatcher, with replacement in one SQLDelight
  transaction. Use `Dispatchers.IO` where public; do not create a custom
  executor solely to imitate it on Kotlin/Native.
- Bind the platform `SqlDriver`, generated database, and store directly in each
  app-scoped Metro graph. The graph owns them for app lifetime; the store API
  does not expose open, close, or factory lifecycle operations.
- TARGETS-001 owns user-input validation and IDNA canonicalization. MODEL-001
  revalidates stored canonical values at the database boundary.
- Do not add reset UI, secure-erasure claims, custom backup behavior,
  synchronization, diagnostics, account data, sessions, or a module split.
- Reimplement only the PoC's useful atomic replacement and restart properties;
  do not copy its schema or custom database lifecycle.

## Acceptance

- `AC-01` — A fresh store reads revision zero and an empty policy; a valid
  expected-revision replacement commits the complete bounded set and increments
  the revision, including an empty replacement.
- `AC-02` — Committed state survives recreation of the standard platform driver,
  while stale revisions and failed inserts leave the previous state unchanged.
- `AC-03` — Invalid stored values and over-limit rows return a redacted typed
  corruption failure. An invalid existing SQLite file makes the standard driver
  initialization fail and remains unchanged rather than being replaced.
- `AC-04` — The v1 schema contains only policy revision metadata and canonical
  domains, has a committed `1.db` baseline, and adds an `.sqm` migration only
  when a real v2 schema exists.
- `AC-05` — Both platform graphs compile with app-scoped driver, generated
  database, and store bindings; reads and writes execute on the injected
  database dispatcher.

## Verification

- Run the focused real-SQLite store contract on JVM and the iOS Simulator.
- Run SQLDelight migration verification, both platform graph compiles, the
  aggregate quality gate, the CI-equivalent iOS host build, and `git diff --check`.

## Decisions or blockers

- `user-confirmed` (2026-08-27): remove the custom database-management layer and
  use SQLDelight's standard drivers, schema initialization, versioning, and
  migrations in the same production-oriented direction as Snap.
- `user-confirmed` (2026-08-27): existing databases created only by the unmerged
  draft implementation are not a compatibility target and may require manual
  deletion during development.
