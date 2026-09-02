# `SYNC-013`: Remove dead synchronization code without changing behavior

- **Review tier:** `standard`
- **Tier reason:** The change deletes unused SQLDelight queries and relocates
  a helper that only tests consume. Wire format, schema, transactions, and
  cryptography stay untouched, and the existing suites detect regressions.
- **Dependencies:** completed `SYNC-002`
- **Integration group:** `PR-SYNC-SIMPLIFY`
- **Authority:** [MVP roadmap revision 5](../mvp-roadmap.md) row `SYNC-013`
  and maintainer activation on 2026-09-02

## Outcome

The synchronization core passes its complete test matrix with no unused
SQLDelight query, no production helper that only tests consume, and an
unchanged wire format, schema, and internal contract.

## Boundaries

- Remove `selectAcceptedBundleById`, `selectStagedBundlesByAuthor`,
  `countStagedBundles`, and `countStagedBundlesByAuthor` from
  `SyncReplica.sq`. Tables, indexes, constraints, and migrations stay
  byte-for-byte unchanged; an index change would be a schema change and is
  out of scope.
- Move `SyncProjectionDigest` to `commonTest` as a test fixture. Keep
  `sha256` on the provider contract; it is golden-vector tested on both
  targets and later synchronization tasks may consume it.
- Keep the iOS runtime wiring (`IosApplicationRuntime`,
  `createIosApplicationRuntime`, `mainViewController(cryptoProvider:)`).
  `SYNC-009` consumes it, and `TARGETS-004` owns those files while the two
  tasks run in parallel. This is a recorded deviation from the roadmap stub's
  "unused iOS wiring" wording.
- Defer collapsing the duplicated validation layers (schema checks, SQL
  preflight, Kotlin restore validation, snapshot validation) to the
  `SYNC-009` integration, where the real transport and restore paths are
  known. That is a High-risk change to security-sensitive code and is not
  part of this brief.
- Write surface: `shared/src/commonMain/kotlin/app/posato/feature/sync/**`,
  the query blocks of `SyncReplica.sq`, sync tests under `commonTest`,
  `jvmTest`, and any iOS test source set, this brief and its record, and one
  wiki-log entry. Do not touch `shared/src/iosMain/kotlin/app/posato/di`,
  `MainViewController.kt`, `iosApp/`, `desktopApp/`, Gradle files, or the
  schema.
- `.research/blocker` is not needed and stays read-only.

## Acceptance

- `AC-01` — Every remaining query in `SyncReplica.sq` has a production
  caller; the diff of that file contains only removed query blocks.
- `AC-02` — `SyncProjectionDigest` no longer exists in production sources,
  and the reducer convergence tests still prove identical digests through a
  test-side helper.
- `AC-03` — Complete JVM and iOS Simulator suites, migration verification,
  and `./gradlew quality` pass with no new suppression.
- `AC-04` — No signature outside the deleted or moved declarations changes.

## Verification

- `./gradlew :shared:jvmTest :shared:iosSimulatorArm64Test`, then
  `./gradlew quality`.
- `grep` over `shared/src` proving zero references to the removed names.
- `git diff --stat origin/main...HEAD` limited to the write surface and
  `git diff --check`.
- One independent completed-change review with evidence, then at most one
  hosted pass under the `AGENTS.md` budget.

## Decisions or blockers

- The iOS wiring stays until `SYNC-009` uses it (recorded deviation).
- Validation-layer collapse is deferred to `SYNC-009`.
- No blocker is known before implementation.
