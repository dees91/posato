# Execution: `MODEL-001`

- **Brief:**
  [`../specifications/model-001-local-exact-domain-policy.md`](../specifications/model-001-local-exact-domain-policy.md)
- **Status:** `complete`
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

## CI linkage correction review

- **Verdict:** `approved`; no Critical, Required, Recommended, or Optional
  finding.
- **Evidence:** The independent reviewer confirmed target-scoped inherited
  flags in both host configurations and reproduced both signing-disabled iOS
  Simulator builds, `./gradlew quality`, and `git diff --check`.

## Blockers and accepted risks

- No current blocker. Separate reset, guaranteed secure erasure, custom backup
  behavior, and user-facing domain canonicalization remain with future owners.

## Final

- **Status:** `complete`
- **Outcome:** MODEL-001 persists the bounded exact-domain policy atomically on
  JVM and iOS, exposes one inert scoped factory from both Metro graphs, and
  performs suspending open, read, replace, transaction, and close work on the
  injected serialized database dispatcher. All required plan and completed-
  change findings are resolved.
