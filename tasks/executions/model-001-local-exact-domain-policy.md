# Execution: `MODEL-001`

- **Brief:**
  [`../specifications/model-001-local-exact-domain-policy.md`](../specifications/model-001-local-exact-domain-policy.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** `Codex`
- **Reviewer:** `independent Codex reviewer; plan and completed change approved`
- **Branch:** `model-001`
- **Updated:** `2026-08-26`

## Plan

1. Define the smallest redacted shared policy and typed storage contract, then
   add a SQLDelight schema containing only revision metadata and exact domains.
2. Implement one transactional replace/read repository and thin JVM and iOS
   driver factories that preserve corrupt or unsupported existing files.
3. Prove restart, optimistic revision, rollback, bounds, redaction, and
   fail-closed open behavior with real SQLite on both selected targets.
4. Review the added dependency and run focused plus aggregate verification,
   then obtain an independent completed-change review.
5. Apply the maintainer's accepted correction: generate suspending SQLDelight
   APIs, keep complete transactions on an injected database dispatcher, and
   bind the platform store factory into both app-scoped Metro graphs.

## High-risk plan review

- **Verdict:** `approved-after-correction`
- **Critical or Required findings:** `2 Required`: the plan introduced an
  unaccepted non-diagnostic retention and deletion rule, and the initial
  migration wording did not clearly distinguish a fresh v1 schema from a real
  later migration.
- **Resolution:** The v1 contract now has no dummy or PoC migration, preserves
  and rejects unknown existing versions, and defers a checked migration until
  a real later schema exists. The maintainer accepted a no-TTL local lifecycle,
  atomic empty replacement, and the explicit reset, secure-erasure, and backup
  exclusions; the threat model and privacy synthesis now record that decision.
  The independent follow-up confirmed both findings resolved with no new
  Critical or Required finding.

## Result

- Added one redacted canonical exact-domain policy model and one typed common
  store contract inside `:shared`.
- Added a fresh SQLDelight v1 schema containing only schema version, policy
  revision, and bounded canonical domain rows. Expected-revision replacement,
  including an empty policy, is one transaction.
- Added thin JVM and iOS driver factories. Existing corrupt or unsupported
  files are rejected and preserved; no PoC schema or dummy migration exists.
- Enabled SQLDelight async generation and made factory open plus every store
  operation suspending. Fresh schema creation and generated query reads use
  their async APIs directly without `runBlocking` or a synchronous adapter.
- Added one injected, serialized database dispatcher per platform graph. JVM
  uses an IO-dispatcher view; iOS uses a background Default-dispatcher view
  because Kotlin/Native does not expose `Dispatchers.IO` publicly. A delegating
  SQLDelight driver keeps each complete replacement transaction on that
  context.
- Bound one scoped platform store factory in both app-scoped Metro graphs.
  Graph construction remains inert; opening and explicit close ownership stay
  with the caller.
- Linked the system SQLite library from both iOS host configurations, as
  required when the native SQLDelight driver is packaged in a static Kotlin
  framework.
- Bounded restoration to one row beyond the accepted maximum before policy
  materialization, and disabled SQLiter error and verbose output at the iOS
  production driver boundary.
- Distinguished validation-query execution failures from confirmed SQLite
  corruption through narrow platform error-code classifiers. Transient or
  unclassified failures now remain `STORAGE_FAILURE`.
- Made fresh schema creation one transaction. A failed first creation rolls
  back every user-defined object, and a later open may initialize an existing
  integrity-valid file only when it still contains no user-defined table,
  index, trigger, or view. Non-empty unknown schemas remain preserved and
  rejected.
- Require an existing v1 database to contain exactly the three expected tables.
  Any additional table, view, trigger, or index is preserved and rejected as
  an unsupported schema.
- Applied the same execution-failure classification to post-open reads and the
  complete replacement transaction. Confirmed SQLite corruption maps to
  `CORRUPTION`; unclassified write failures remain `STORAGE_FAILURE` and roll
  back. Singleton metadata reads are bounded to two rows and validate SQLite's
  storage class explicitly, while nullable values from weakened schemas become
  invalid sentinels instead of escaping through generated nullability or
  numeric coercion.
- Preserved a completed transaction result across the cancellable dispatcher
  handoff. A `CancellationException` observed before the transaction produces
  a result still propagates after rollback; once the transaction produces its
  committed result, that result wins prompt handoff cancellation.
- Tightened Detekt so anonymous empty catch blocks cannot bypass analysis and
  swallowed exceptions require an explicit `expected...` boundary name. All
  MODEL-001 production functions use block bodies with explicit returns.
- Kept TARGETS-001 input and IDNA work, UI, synchronization, reset, secure-
  erasure claims, and custom backup policy out of this slice.
- Behaviorally reimplemented transaction, restart, rollback, and preserve-on-
  corruption properties after reviewing the read-only feasibility checkout at
  `d48bdfbd`; no feasibility source or fixture was copied.

## Completed-change review

- **Verdict:** `approved-after-correction`
- **Critical or Required findings:** `3 Required`: default iOS SQLiter output
  bypassed the redaction boundary, restored domains were bounded only after
  full materialization, and physical SQLite corruption had no regression test.
- **Resolution:** The iOS production configuration now uses a silent logger
  whose disabled channels are exercised by the same factory in tests. The
  restore query fetches at most 1,025 rows and fails closed before constructing
  the policy. Real-SQLite tests on both runtimes now exceed the raw row bound
  and overwrite the exact-domain table's B-tree page, then verify typed
  failure, no driver output, and preservation of the corruption marker. The
  independent follow-up approved all corrections with no remaining Critical,
  Required, Recommended, or Optional finding.

## Accepted correction

- **Status:** `implemented and approved`
- **Reason:** The maintainer explicitly corrected the earlier no-consumer
  interpretation. MODEL-001 now owns graph reachability, async query
  generation, database I/O dispatching, and whole-transaction context.
- **Planned shape:** Both internal platform graphs are app-scoped and expose
  one scoped `LocalExactDomainPolicyStoreFactory`. Its suspend `open` retains
  typed failure and explicit close ownership without graph-construction I/O.
  Store operations use an injected database dispatcher, and a delegating
  driver implements SQLDelight's transaction dispatcher contract. Fresh schema
  creation calls the generated suspending schema API after raw driver creation;
  no blocking schema adapter is used. Cancellation propagates after rollback,
  open cancellation and explicit close clean up under `NonCancellable`, and a
  mandatory dispatcher/driver probe covers open, read, write, close, commit,
  and cancellation rollback on both runtimes.
- **Plan-review follow-up:** `approved`; no remaining Critical or Required plan
  finding.

## Correction completed-change re-review

- **Verdict:** `changes-required`; no Critical and `2 Required` findings.
- **Findings:** Prompt cancellation during the dispatcher result handoff could
  discard a successfully opened store outside the original cleanup guard. The
  cross-runtime probe observed SQL calls and transaction start but not the raw
  driver-open boundary, transaction completion, or real cancellation during
  that result handoff.
- **Resolution:** Driver ownership now remains guarded outside the dispatcher
  handoff and is released only after the result reaches the caller. A typed
  context observer records the driver-open boundary, completed transaction
  dispatch, and successful-open handoff. A controlled child-job cancellation
  at that handoff proves the undisclosed store is closed on JVM and iOS, while
  transaction-completion observations cover both commit and cancellation
  rollback paths.
- **Follow-up:** `approved`; both Required findings are resolved, with no
  remaining Critical, Required, Recommended, or Optional finding. The reviewer
  independently reproduced `./gradlew quality` and 14 passing tests on each
  runtime.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Baseline `./gradlew quality` | `pass` | The accepted skeleton was green before MODEL-001 changes. |
| Focused PoC persistence review | `pass` | The read-only PoC demonstrates transaction, restart, rollback, and preserve-on-corruption test ideas; no production source is copied. |
| JVM and iOS SQLite contract plus device-target compile | `pass` | `./gradlew :shared:jvmTest :shared:iosSimulatorArm64Test :shared:compileKotlinIosArm64`; ten real-SQLite contract tests passed on each runtime. |
| Formatting and static analysis | `pass` | `./gradlew :shared:ktlintCheck :shared:detekt`. |
| Exception-analysis regression | `pass` | With only the stricter Detekt configuration applied, analysis reported 2 `EmptyCatchBlock` and 8 `SwallowedException` findings. After replacing the empty cleanup catches with typed failure mapping and explicit expected-boundary catches, the same analysis passed. |
| Dependency graph, license, and advisory review | `pass` | SQLDelight runtime, drivers, and async extensions use `2.3.2`; coroutines use the async extension's aligned `1.10.2`; JVM resolves sqlite-jdbc `3.51.3.0`; iOS resolves Stately `2.1.0` and SQLiter `1.3.3`. Published POMs declare Apache-2.0, and the resolved sqlite-jdbc is newer than the affected versions in `GHSA-6phf-6h5g-97j2`. |
| Aggregate `./gradlew quality` | `pass` | Formatting, Detekt, JVM tests, iOS Simulator tests, both iOS compiles, desktop tests, and macOS distribution completed in 9 seconds after resolving the completed-change findings. |
| Async migration red proof | `pass` | After enabling `generateAsync`, `:shared:compileKotlinJvm` failed on the former synchronous transaction, mutator `.value`, and synchronous schema constructor, proving that the store and platform factories still required migration. |
| Suspending persistence and dispatcher contract | `pass` | `:shared:jvmTest` and `:shared:iosSimulatorArm64Test` pass 14 real-SQLite contract tests per runtime, including driver-open and transaction-completion context probes, cancellation rollback, cancellation during open-result handoff, cancelled-operation cleanup, and close from an already-cancelled caller. |
| Platform graph and async API compile | `pass` | `:shared:compileKotlinJvm`, `:shared:compileKotlinIosArm64`, and `:shared:compileKotlinIosSimulatorArm64` compile async schema/query use and both Metro factory bindings. |
| Correction aggregate `./gradlew quality` | `pass` | Ktlint, Detekt, JVM tests, 14 iOS Simulator tests, both iOS target compiles, both Metro graphs, desktop tests, runtime checks, and the macOS distribution completed in 9 seconds after resolving the re-review findings. |
| Static-framework SQLite host linkage | `pass` | The signing-disabled iOS Simulator host initially failed with undefined `_sqlite3_*` symbols. The same CI invocation reproduced locally, then passed for Debug and Release after both host configurations inherited `-lsqlite3`; `./gradlew quality` also remained green. |
| Validation failure classification | `pass` | The new contract test first failed because validation-query exceptions were reported as `CORRUPTION`. Injected quick-check, schema-lookup, schema-version, revision, and domain-query execution failures now return `STORAGE_FAILURE`, while physical SQLite corruption still returns `CORRUPTION`. |
| Atomic schema recovery and bounded metadata validation | `pass` | The interrupted-DDL and post-open-query tests failed before the correction. Failed fresh creation leaves zero user-defined objects and the next open recovers; invalid fresh schema metadata is a storage failure; an unknown view-only database is preserved and rejected; missing or duplicate revision metadata, a nullable domain, and over-limit rows fail closed. |
| Commit handoff and SQLite storage classes | `pass` | The three hosted-finding regressions first failed. JVM and iOS now pass 25 tests each: cancellation after transaction completion still receives the committed replacement, nullable completed results remain distinguishable from no result, and an injected `CancellationException` observed before a result still rolls back; weakened schema-version and revision tables containing text or real metadata fail as corruption instead of being numerically coerced. |
| Complete schema inventory and replacement classification | `pass` | The two hosted-finding regressions first failed on JVM. JVM and iOS now pass 27 tests each: an otherwise valid v1 database with an additional table, view, trigger, or index is preserved and rejected, and confirmed corruption during replacement maps to `CORRUPTION` after transaction rollback. |
| Corrected aggregate and iOS host build | `pass` | `./gradlew quality` passed Ktlint, Detekt, both runtime contracts, both iOS compiles, desktop checks, and distribution. The CI-equivalent signing-disabled iOS Simulator host build also passed with SQLite linked. |

## CI linkage correction review

- **Verdict:** `approved`; no Critical, Required, Recommended, or Optional
  finding.
- **Evidence:** The independent reviewer confirmed target-scoped inherited
  flags in both host configurations and reproduced both signing-disabled iOS
  Simulator builds, `./gradlew quality`, and `git diff --check`.

## Hosted review correction

- **Findings:** `2 P2`: the execution status used values outside the canonical
  vocabulary, and validation-query exceptions for an existing database were
  reported as corruption even when the database had not been proven corrupt.
- **Resolution:** Both execution statuses now use `done`. Validation exceptions
  are classified by the platform SQLite primary error code; only corruption or
  not-a-database errors on an existing file map to `CORRUPTION`, while lock,
  I/O, and unclassified execution failures map to `STORAGE_FAILURE`. A common
  regression test covers all three opening queries and file preservation on
  both runtimes.
- **Follow-up:** `approved`; no Critical, Required, Recommended, or Optional
  finding. The independent reviewer reproduced 15 passing tests per runtime,
  the aggregate quality gate, and the diff check.

## Second hosted review correction

- **Findings:** `2 P2`: interrupted fresh schema creation could leave a
  partially initialized file that every later open rejected, and post-open
  read exceptions were still reported as corruption without proof.
- **Resolution:** Fresh schema creation is now transactional. Recovery is
  limited to integrity-valid existing files with no user-defined SQLite schema
  objects, so even a view-only unknown database is preserved and rejected.
  Post-open query execution uses the platform corruption classifier; logical
  cardinality, sentinel, policy, and row-bound violations remain corruption,
  while transient and unclassified failures remain storage failures and roll
  back replacement.
- **Regression evidence:** The shared contract covers interrupted DDL and
  recovery, invalid fresh metadata classification, view-only preservation,
  post-open read failure, replacement rollback, invalid revision cardinality,
  and nullable stored domains on both runtimes.
- **Independent completed-change follow-up:** `approved`; no remaining
  Critical, Required, Recommended, or Optional finding. The reviewer
  reproduced 21 passing tests on both runtimes and confirmed the aggregate
  quality and diff checks.

## Third hosted review correction

- **Findings:** `2 P2`: prompt cancellation during the dispatcher handoff
  could discard a successful replacement result after commit, and SQLite text
  or real revision values could be coerced to a fabricated `Long`.
- **Plan review:** `approved`; no Critical or Required plan defect. The reviewer
  required an explicit completed-result holder so nullable generic transaction
  values remain distinguishable, and independent schema-version and revision
  storage-class regressions.
- **Resolution:** Transaction dispatch and replacement preserve a result only
  after their block has produced it. A `CancellationException` observed before
  any result propagates after rollback; once a committed result exists, it wins
  prompt handoff cancellation. Both singleton metadata queries remain bounded
  to two rows and now read `typeof(...)`; anything other than SQLite `integer`
  storage fails closed. Fresh schema checks enforce the same storage class.
- **Regression evidence:** Separate cross-runtime tests cover committed-result
  handoff cancellation, a nullable completed result, text schema versions, and
  text plus real revisions. The pre-commit cancellation test continues to
  prove propagation and rollback.
- **Independent completed-change follow-up:** `approved`; no Critical,
  Required, Recommended, or Optional finding remains. The reviewer reproduced
  25 passing tests on both runtimes, the aggregate quality gate, and the diff
  check, then approved the corrected cancellation wording.

## Fourth hosted review correction

- **Findings:** `2 P2`: an otherwise valid v1 database was accepted even when
  it contained an additional table, view, trigger, or index, and exceptions
  from replacement execution always mapped to storage failure even when the
  platform classifier confirmed SQLite corruption.
- **Plan review:** `approved`; no Critical or Required plan defect. The reviewer
  approved a bounded four-row schema inventory against the exact three expected
  tables and an internal test-only classifier seam without changing the public
  or Metro API.
- **Resolution:** Existing databases now recover only from an empty
  user-defined schema or match the complete v1 object inventory exactly. Any
  mismatch is preserved and rejected as `UNSUPPORTED_SCHEMA`. Replacement
  execution uses the same narrow platform corruption classifier as reads and
  opening validation while retaining transaction rollback.
- **Regression evidence:** Separate cross-runtime coverage creates and
  preserves each additional SQLite object kind. An injected confirmed-
  corruption exception during replacement returns `CORRUPTION` and leaves the
  revision and policy unchanged. Both regressions failed before the production
  correction; 27 tests now pass on each runtime together with the aggregate
  quality gate and signing-disabled iOS host build.
- **Independent completed-change review:** `approved-after-correction`; one
  Required finding identified that `_` in the initial `LIKE 'sqlite_%'`
  predicate was a wildcard, allowing a user object such as `sqlitextable` to
  escape the inventory. The query now compares the literal `sqlite_` prefix,
  and the cross-runtime regression includes that former bypass. The follow-up
  found no remaining Critical, Required, Recommended, or Optional finding and
  reproduced 27 passing tests per runtime without cache, the aggregate quality
  gate, and the diff check.

## Blockers and accepted risks

- No current blocker. Separate reset, guaranteed secure erasure, custom backup
  behavior, and user-facing domain canonicalization remain with future owners.

## Final

- **Status:** `done`
- **Outcome:** MODEL-001 persists the bounded exact-domain policy atomically on
  JVM and iOS, exposes one inert scoped factory from both Metro graphs, and
  performs suspending open, read, replace, transaction, and close work on the
  injected serialized database dispatcher. All required plan and completed-
  change findings are resolved.
