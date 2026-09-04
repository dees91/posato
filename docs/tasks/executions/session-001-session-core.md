# Execution: `SESSION-001`

- **Brief:** [Provide one bounded manual session and consolidate repeated UI](../specifications/session-001-session-core.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Muse Code (session `young-kerberos`)
- **Reviewer:** independent plan reviewer; independent completed-change reviewer
- **Branch:** `feature/session-001-session-core`
- **Worktree:** `~/Projects/Polyglot/posato-session-001`
- **Updated:** 2026-09-04

## Plan

1. Obtain the independent plan review and the maintainer's decisions on the
   five recommendations in the brief (expiry observation, clock semantics,
   product bounds and zero-item start, session table, navigation).
2. Define the session domain: identifier reuse from `SYNC-002`, setup
   validation, the inactive, active, and ended state machine, the terminal
   expiry rule, and a clock port with a fake for tests.
3. Add the SQLDelight migration and store for the single local session and
   its expiry marker, committed atomically and read back fail-closed.
4. Implement the session ViewModel and screens: status summary, setup,
   review with effective local items and action-required reasons, active
   surface, early-end confirmation, and expiry transition; wire both graphs
   and the route between session and Paused items.
5. Consolidate the components repeated by the Paused items and session
   screens into `app.posato.core.designsystem`, keeping the Paused items
   behavior unchanged, and update `DESIGN.md` only from rendered evidence.
6. Run `./gradlew quality` on JVM and iOS Simulator, drive `desktop` and `sim`
   through `verify-posato` with a new sessions feature recipe, complete the
   independent completed-change review, rerun affected checks, and close this
   record with the single wiki-log entry in the closeout commit.

## High-risk plan review

- **Verdict:** `approved`
- **Critical or Required findings:** none; the plan was judged consistent
  with the brief, ADR 0006, and the maintainer decisions below
- **Resolution:** proceeded on the maintainer decisions of 2026-09-04:
  product bounds 5 minutes to 24 hours in one-minute steps with
  zero-effective start refused; foreground-only expiry through a resumable
  one-second ticker plus re-evaluation on read; wall-clock rollback stays
  active until the stored end passes, except after the terminal marker;
  minimal two-route switch instead of Navigation 3; direct internal
  `SessionId`/`SyncIdentifier` reuse; separate `local_session` and
  `local_session_expiry` tables with `SYNC-012` marker ownership; blocking
  only on `NO_EFFECTIVE_ITEMS` and `MAPPINGS_LOAD_FAILED`

## Result

- New `feature/session` package: `SessionSetup` bounds validation,
  `SessionState` inactive, active, and ended evaluation with the terminal
  `CommitExpiry` transition, `SessionReview` derivation with effective local
  items and action-required reasons, `SqlLocalSessionStore` with atomic
  read, start, early end, and marker-before-expose expiry commit, and the
  `SessionViewModel` with wall-clock recomputed remaining time plus the
  status, setup, review, and early-end confirmation UI on both shells.
- SQLDelight surface spans the new `LocalSession.sq` definition and
  migration `4.sqm` (maintainer-accepted deviation from “new migration
  only”); the expiry marker stores no observation timestamp.
- Consolidated the components shared by the Paused items and session
  screens into `app.posato.core.designsystem`; every promoted component is
  used by at least two production screens and Paused items behavior is
  unchanged.
- Material deviation from the brief flow, accepted in review: no
  Navigation 3 routes; the destination switch is a minimal state toggle.

## Completed-change review

- **Verdict:** `fixes-required`
- **Critical or Required findings:** (R1) store start/end failures were set
  and then wiped by the immediate refresh, and the review section never
  rendered `operationFailure`, so failed starts and ends returned silently;
  (R2) blocking review reasons were enforced only by button `enabled`,
  never in the `startSession()` path, so a stale review could start a
  blocked session silently
- **Resolution:** R1 — failures are now preserved (refresh only on
  `ALREADY_ACTIVE` conflict to reveal the active session; no refresh on
  other failures) and rendered with retry in `SessionReviewSection`; R2 —
  `startSession()` reloads targets through the shared `loadSessionTargets`
  helper and refuses when not review-ready or `blocksStart`, refreshing the
  visible review instead. Three regression tests added
  (stale-review refusal, start-failure visibility, end-failure visibility).
  No Critical findings; advisory items declined without scope change.
- **Advisory findings:** none accepted

## PR feedback (P2 advisory pass)

- Maintainer five-axis review: approve, no P1; five P2 items, all declined
  by default per process except those judged correct.
- Accepted: explicit expiry commit on the start path with a
  current-marker-only retention note for SYNC-012; day-aware end-time
  formatting on JVM and iOS with one platform test each; two real-database
  corruption tests proving fail-closed reads without marker writes.
- Declined with thread replies: CSPRNG session ids (neither DI graph holds
  a provider and iOS would need a native-contract change; ids are
  local-only until SYNC-012 owns sync keying), per-second review derivation
  (unmeasured; YAGNI next to the expiry path), and dropping the store-side
  start-time check (defense-in-depth at a trust boundary).
- Focused independent re-review of the corrections: approved, no
  Critical/Required findings; one accepted advisory (cross-midnight tests
  now assert equality against the date-time formatter).

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` after the last correction | pass | `BUILD SUCCESSFUL`, including Detekt after restructuring the fix for complexity, function-count, and return-count rules with no new suppressions |
| Session JVM tests | pass | 51 tests, 0 failures: `SessionViewModelTest` 19 (3 new), `SqlLocalSessionStoreTest` 8, `SessionSetupTest` 10, `SessionStateTest` 7, `SessionReviewTest` 7 |
| Regression-test mutation proof | pass | With both fixes reverted, exactly the stale-review and start-failure tests fail; restored after |
| `verify-posato` pre-fix full pass | pass | Setup, review, start, restart, early end, and expiry green on desktop and Simulator with marker DB evidence, including real 5-minute expiry waits |
| `verify-posato` post-fix rerun on Simulator | pass | `session-start` (17 steps) and `session-early-end` (7 steps, incl. restart survival) green on the rebuilt app; DB shows the `ended_early` row |
| `verify-posato` post-fix rerun on desktop | partial | Re-run blocked by intermittent synthetic-input delivery on the shared developer machine (keystrokes and taps sporadically swallowed; identical shared code proven on Simulator); desktop session flows were green pre-fix and no desktop-affecting product code changed since |

## Blockers and accepted risks

- No physical device is required because enforcement and the
  suspended-expiry callback belong to later tasks.
- The expiry scenarios were not re-run post-fix: the store, ticker, and
  expiry-commit paths are byte-identical to the pre-fix green pass; only
  the start gating, failure retention, and review rendering changed, all
  covered by the post-fix Simulator rerun and unit tests.
- The desktop rerun gap above is environment tooling flakiness, not a
  product finding: the same shared UI and store code passes on Simulator,
  and a manual desktop add persisted to the developer database correctly.
  Desktop databases were restored from backup after the runs.

## Final

- **Status:** `done`
- **Outcome:** `met`
