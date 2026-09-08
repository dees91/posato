# Execution: `SYNC-009`

- **Brief:** [Integrate Keychain and CloudKit bootstrap on both apps without creating a parallel workspace](../specifications/sync-009-apple-bootstrap.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** implementation agent (2026-09-08)
- **Reviewer:** independent plan review complete (changes-required, resolved); completed-change review pass (2 Required, resolved); P2 correction reviewed, pass
- **Branch:** `feature/sync-009-apple-bootstrap`
- **Worktree:** `~/Projects/Polyglot/posato-sync-009`
- **Updated:** 2026-09-08

## Plan

1. `D1` and `D2` answered by the maintainer on 2026-09-08: add the minimal
   explicit consent control and amend `DESIGN.md` for it; defer destructive
   removal to `SYNC-010`.
2. Add the narrow `commonMain` composition entry point: process-scoped
   single-flight, background dispatcher, established-context read, and no
   provider access before the action; `commonTest` over fakes.
3. Desktop graph: lazy non-fatal companion client, both macOS adapters,
   `JdkSyncCryptoProvider`, `SqlBootstrapStore`; the composition root in
   `Main.kt`.
4. iOS graph: both iOS adapters and `IosSyncCryptoProvider`; adopt the deferred
   `SYNC-007` production provider construction and off-main-thread rule.
5. The minimal consent control and its `DESIGN.md` amendment, then physical
   Mac and iPhone rows in both device orders under the maintainer's iCloud
   account, `./gradlew quality`, independent completed-change review,
   closeout and PR.

## High-risk plan review

- **Verdict:** `changes-required` (independent review, 2026-09-08; 2 Critical,
  6 Required, 4 Recommended), corrections applied to the brief before
  implementation.
- **Critical or Required findings:** `C-1` both candidate bootstrap triggers
  bypassed the explicit **Sync with iCloud** action that ADR 0007 and ADR 0002
  require, silently amending an accepted decision; `C-2` the ADR 0007
  destructive-removal evidence was unbuildable inside the stated boundaries,
  because `BootstrapCloudPort` has no zone delete; `R-1` the write surface
  omitted the platform sync adapter directories the deferred items touch;
  `R-2` graph-scoped single-flight cannot satisfy the "never twice
  concurrently" rule when a second iOS runtime is built; `R-3` eager
  `MacOsSyncCompanionClient.verified` construction would crash every plain
  Gradle run and driver launch; `R-4` the convergence criterion named no
  observable; `R-5` shared documentation and `Main.kt` surfaces were not
  declared against the other two wave tasks; `R-6` the plan added replica-store
  wiring that the brief did not carry and that has no consumer until
  `SYNC-010`.
- **Resolution:** `C-1` and `C-2` became blocking maintainer decisions `D1` and
  `D2` in the brief, with recommendations, and the outcome no longer assumes a
  per-launch bootstrap. `R-1` declared the adapter directories and dropped the
  `SYNC-008` helper refactor, which stays deferred. `R-2` made single-flight
  process-scoped with a test. `R-3` made companion construction lazy and
  non-fatal and promoted it to `AC-05`. `R-4` named the four convergence
  observables including the winner's item still being readable. `R-5` declared
  that this task does not amend the roadmap and that `QUALITY-005` merges first
  for the disjoint `Main.kt` region. `R-6` removed the replica store. Accepted
  Recommended items: `AC-06` narrowed to values that are actually secret, both
  device orders added to `AC-02`, the real graph's dispatcher asserted, and the
  visibility trap recorded in `D3`.

## Result

- Implemented on `feature/sync-009-apple-bootstrap`. New: `AppleBootstrap`
  facade (`commonMain`), `MacOsSyncTransport` lazy non-fatal transport
  (`jvmMain`), `IosBootstrapKeychainAdapter` off-main-thread hop plus
  `UnavailableIosKeychainProvider` (`iosMain`), `DeferredCloudKitMailboxBackend`
  (`iosApp`), `SyncBootstrapSection` consent control with strings (`commonMain`
  sync ui), single-function `provideAppleBootstrap` on both Metro graphs,
  `IosApplicationGraph` factory provider params, `MainViewController` provider
  params, `iosApp.swift` production providers, `PosatoApplication` bootstrap
  param, and the `D1` `DESIGN.md` amendment.
- Deliberate deviations from the plan, all inside the brief boundaries:
  `Main.kt` needed no change (the database-path default keeps the call site
  stable, so the `QUALITY-005` region is untouched); `SessionScreen` and
  `SessionOverviewContent` carry additive optional bootstrap params as the
  `D1`-mandated control host (prop-drilling only, no session logic; no file
  overlap with the `SESSION-003` or `QUALITY-005` branches); a `databasePath`
  factory seam keeps the real-graph `jvmTest` off the developer database;
  single-function graph composition replaces granular bindings after Detekt
  `TooManyFunctions`; `DeferredCloudKitMailboxBackend` replaces eager
  `CKContainer` construction after it trapped the Swift test host at launch.
- The staged-Mac consent tap established a real workspace (zone, anchor,
  synchronizable keychain item) on the development iCloud account and the
  local `Established` row; relaunch adopts it. The account is therefore no
  longer pristine: the remaining iPhone rows start from the established state.

## Completed-change review

- **Verdict:** `pass` (two independent reviews, 2026-09-08; first report
  truncated in transit, its one visible finding fixed; second compact review
  completed).
- **Critical or Required findings:** `R-a` the consent button set its guard
  inside the launched coroutine, so a rapid double-tap queued two attempts;
  fixed with a synchronous main-thread guard. `R-b` a mid-flight provider
  failure escaped the facade and crashed the tap coroutine instead of
  reporting; fixed with a cancellation-safe catch-all mapping to `Retryable`
  plus a red-to-green test. The catch-all passes the Detekt gate as written,
  so no suppression or allowlist change was needed.
- **Resolution:** both fixed, re-verified, full `./gradlew quality` green.
  One `Optional` declined as factually incorrect (`defaultDesktopPolicyDatabasePath`
  is consumed in-diff by the graph default argument).
- **P2 correction (post-PR review):** the consent attempt and outcome moved
  from composition-bound state to a `Content`-owned holder so ordinary
  navigation no longer cancels the attempt; correction review pass with 2
  nits applied (suspend refresh, `@Stable`) and affected gates re-verified.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | `pass` | 197 tasks green after review fixes |
| `commonTest` facade (`AppleBootstrapTest`, 7 tests) | `pass` | single-flight across two runtimes, dispatcher honored, failure degrades |
| `jvmTest` real desktop graph (3 tests) | `pass` | singleton, `Dispatchers.IO`, unverifiable companion degrades |
| `iosTest` real iOS graph on simulator (4 tests) | `pass` | singleton, IO dispatcher, undetermined binding degrades |
| Swift tests incl. 2 deferral tests | `pass` | `TEST EXECUTE SUCCEEDED` |
| Staged Mac: consent press establishes, relaunch adopts | `pass` | `build/verification/runs/20260908-124319-658f`, `-124437-21b8`; one DB row |
| Fresh sim: consent control renders, attempt degrades live | `pass` | `build/verification/runs/20260908-124738-2266`, `-124954-9881` |
| `git diff --check`, secret and path scans | `pass` | no findings; no `Suppress` added |
| Physical iPhone join row (fresh install, same account) | `pass` | consent press joined the Mac workspace; relaunch adopts; `build/verification/runs/20260908-141036-708f` |
| Navigation during the device attempt | `pass` | outcome observed after return; holder ownership unit-covered |
| Physical from-empty reruns (simultaneous opt-in, reverse order) | `pending` | maintainer keep-vs-reset decision |
| CloudKit Console one-zone one-anchor confirmation | `pending` | maintainer console access |

## Blockers and accepted risks

- `D1` and `D2` are decided; implementation is unblocked. The ADR 0007
  destructive-removal evidence is deferred to `SYNC-010` by maintainer
  decision and is not claimed here.
- The iPhone join row ran on the maintainer's cabled iPhone (same iCloud
  account): fresh install, consent press, `Ready`, relaunch adopts. A
  from-empty simultaneous opt-in or reverse-order rerun needs a maintainer
  decision (keep as join fixture or reset the private database manually,
  since removal is `SYNC-010` work).
- The `DeferredCloudKitMailboxBackend` residual: a first bootstrap use on a
  build without the container entitlement would trap instead of degrading;
  signed artifacts carry the entitlement, so this stays a build-configuration
  property, not a runtime branch.
- Wave 7 surfaces stayed disjoint: no `feature/enforcement/**`,
  `tools/posato-control/**`, or session-schema changes; the two session-ui
  files carry additive optional params only.

## Final

- **Status:** `active`
- **Outcome:** implementation, automated verification, independent review,
  and the iPhone join row complete; PR opened; Console check and the
  from-empty rerun decision pending maintainer.
