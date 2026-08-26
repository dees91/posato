# `MODEL-001`: Persist the local exact-domain policy atomically

- **Review tier:** `high-risk`
- **Tier reason:** The change introduces sensitive policy storage, schema and
  migration behavior, and failure handling whose defects could lose or expose
  the last valid local policy.
- **Dependencies:** `FOUNDATION-001`, `SECURITY-001`, `DIAGNOSTICS-001`
- **Integration group:** `PR-LOCAL-REPLICA`
- **Authority:** `MODEL-001` in the accepted MVP roadmap

## Outcome

The shared KMP layer persists one bounded exact-domain policy atomically and
restores the same last valid policy after the database is closed and reopened
on macOS/JVM and iOS. Both platform Metro graphs provide the persistence
factory, and its database operations use suspending SQLDelight APIs on a
database I/O dispatcher.

## Boundaries

- Store only a revision and canonical exact-domain values in app-private
  SQLite. The policy has no TTL and remains until edited or removed; replacing
  it with an empty set atomically removes every domain row.
- Do not add a separate reset flow, guarantee secure physical erasure, or
  define a custom backup policy.
- TARGETS-001 still owns user-input validation and IDNA canonicalization.
  MODEL-001 revalidates stored canonical values at the untrusted database
  boundary without adding UI or target-management behavior.
- Do not add application selections, platform tokens, sessions, immutable
  operations, pending publication, synchronization, diagnostics, navigation,
  or a speculative module split.
- Generate suspending SQLDelight queries. Opening, reading, replacing, and
  closing storage run on an injected database dispatcher, and the complete
  replacement transaction remains on that context without per-statement
  dispatcher changes.
- Bind one platform store factory in each app-scoped Metro graph. Keep opening
  suspend and explicit so graph construction performs no filesystem I/O and
  the caller retains typed failure and close ownership.
- Create a fresh async-generated schema explicitly through its suspending API
  after opening the raw synchronous platform driver. Do not adapt it through a
  blocking schema bridge or use `runBlocking`.
- Propagate coroutine cancellation after transaction rollback. Close a driver
  created during a cancelled open and make explicit store close safe when the
  caller is already cancelled.
- Reimplement the narrow atomicity and restart properties informed by the PoC;
  do not copy its schema, local-replica API, operation model, or source.

## Acceptance

- `AC-01` — A fresh store is revision zero with an empty policy; replacing the
  expected revision commits the complete bounded set and increments revision.
- `AC-02` — The committed state survives close and reopen on JVM and iOS, and
  a stale revision or interrupted write never partially changes it.
- `AC-03` — Corrupt or unsupported existing storage returns a closed typed
  failure and leaves the authoritative database in place rather than silently
  creating an empty policy.
- `AC-04` — The production v1 schema is a versioned fresh-schema baseline with
  no dummy or PoC migration. Unknown existing versions are rejected and
  preserved; a checked migration is added only with a real later schema.
- `AC-05` — The schema contains only local-replica metadata and canonical
  domains, with no forbidden platform, diagnostic, account, device, session,
  or usage data.
- `AC-06` — Both platform graphs compile with one app-scoped local-policy store
  factory, and every public store operation is suspending and dispatched away
  from the caller context while preserving one-context transaction ownership.

## Verification

- Run real-SQLite contract tests for fresh creation, replacement, restart,
  stale revision, rollback, bounds, redaction, corruption, and unsupported
  schema behavior on JVM and the iOS Simulator target.
- Use a deterministic dispatcher and driver probe to verify open, read,
  replace, close, commit, and cancellation rollback remain on the injected
  database context on both runtimes.
- Compile both platform graphs, inspect the runtime dependency change, and run
  the aggregate local quality gate.

## Decisions or blockers

- `user-confirmed` (2026-08-26): the minimum local retention and deletion
  lifecycle in the boundaries above is accepted.
- `user-confirmed` (2026-08-26): MODEL-001 includes Metro persistence wiring,
  SQLDelight async generation, suspending query APIs, database I/O dispatching,
  and transaction context ownership rather than deferring them to a consumer.
- Pin SQLDelight only after current release, target, license, security, and
  transitive-dependency review; future schema changes require a fresh-schema
  update and an explicit checked migration from a real predecessor.
