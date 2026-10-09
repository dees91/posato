# Execution: `SYNC-021`

- **Brief:** [One press removes a Mac workspace with a long zone history](../specifications/sync-021-removal-long-history.md)
- **Status:** `active`: implementation started 2026-10-09 after plan
  review round 3 (Required items folded; decided by the coordinator under
  the maintainer's delegation of 2026-10-09).
- **Review tier:** `high-risk`: the accepted development-only seams (`D1`,
  `D4`) delete CloudKit records or the zone; see the brief
- **Implementer:** Claude
- **Reviewer:** plan reviews of `7abfa5c2`, `64ab9efc`, and `a07de846`,
  each `changes-required` and folded (their decisions are the coordinator's
  under the maintainer's delegation of 2026-10-09)
- **Branch:** `task/sync-021-removal-long-history`
- **Updated:** 2026-10-09

## Evidence before the row

`observed` (2026-10-09, `main` `572e341`, fresh `primary` clones, test
Apple Account): the first press failed 6 of 6 times; removal took 3 or 4
presses, up to about 7 minutes, at about 110 change fetches a minute.

## Hypotheses

1. `inferred`: the per-press cap, `MAX_DELETE_ATTEMPTS` (10) passes of at
   most 16 pages in 30 s, against a never-deleted zone whose history from
   an empty token grows every cycle; an `Unknown` exchange spends a pass.
2. `hypothesis`: pages carry few records; 3. `hypothesis`: a press waits
   behind `AppleBootstrap.flight`; 4. `inferred`: a quit drops it (`D3`).

## Plan

1. **Authority first.** Commit the accepted ADR 0007 amendment and `T-14`
   text, with the clarifications noted in the pull request.
2. **Measure** in one `primary` clone before anything is cleaned: one link,
   one press, guest `log stream` of `PosatoMacOSSync` and `cloudd`: passes
   per press, pages and records per pass, deletions or live records, the
   press-to-first-delete delay, and the pages of a full drain; then what a
   real cycle adds. Fallback: pages from fetch-log lines per companion
   process, passes and presses per drain.
3. **Failure inventory**, isolated tests written failing first, each one
   E2E cannot produce: a cursor that never advances and repeated `Unknown`
   exchanges (not drivable), an advancing cursor that never ends (more than
   20 minutes of history), cancellation keeping the continuation in memory,
   and the `removing` re-entry guard; the cancellation test is a regression
   guard written first. The cap tests at `MacOsMailboxAdapterTest:159-205`
   become removal-budget tests, the link sweep keeps its cap-of-10 test.
4. **Fix.** `RemovalBudget` in shared `feature/sync/mailbox`, a parameter of
   `sweepBundlesIfAnchorMissing` and `deleteWorkspaceRecords` that only
   `AppleWorkspaceRemoval` passes; the default keeps 10 passes. Overrides
   that change: `MacOsMailboxAdapter`, `IosMailboxAdapter` (ignores it),
   `AppleSyncTestHarness.kt:156,161`, `AppleSyncConvergenceTest.kt:395,399`.
   With the budget a press continues while each `Incomplete` cursor is new
   against a digest of the cursors seen in the press; 3 passes in a row
   with a repeated cursor or an `Unknown` exchange end it `Retryable`, as
   does a 20-minute ceiling from an injectable monotonic `TimeSource`
   started at the first pass, after the lock. A companion `Retryable` or
   `UnknownOutcome` ends it at once; a second token expiry already returns
   `Retryable` in the companion. Cancellation propagates with the last
   banked cursor kept in memory. The companion does not change.
5. **Progress state.** Inside `AppleSync`'s `scope.async`,
   `removeWorkspace` checks and sets `removing` atomically (`getAndUpdate`),
   returns when it was set, and clears it in `finally`; it precedes
   `SYNCING`. A platform-composition
   boolean, true only in the macOS composition, is passed into the row:
   with it the row shows "Removing workspace…", "An older workspace can
   take a few minutes.", and a `PosatoActivityIndicator` with actions
   disabled; without it iOS shows `removing` as today's running state.
   `DESIGN.md` gains the macOS row state; `posato-control`'s `ICloudRow`
   learns `removing`; the verify-posato sync page is updated.
6. **Verification-only seams** under the ADR 0007 amendment: the
   `posatoMacOsVerificationSeams` property (Swift condition, `Info.plist`
   key, configuration and task-graph refusals), one scan of the key and the
   marker `posato-verification-seams-v1` in the packaging check and first in
   the DMG reader, the companion `deleteZoneForVerification` (seeding only
   if step 2 needs it), a separate verification client and launch-argument
   entry (`AppleBootstrap.flight`, `LOCAL_ONLY`, the admission lock), and
   `posato-control build --verification-seams` and `vm sync-fixture`.
7. **Verify** the brief's matrix with `flow icloud remove --timeout-seconds
   1500`, then the `AC-04` order; completed-change review, `qualityLint`,
   `quality`, closeout. The iOS cap is idea 34, backlog row `IOS-008` and
   Projects item `PVTI_lAHOAB0Ak84Bj5GQzg_ttMo`, an agent proposal under
   the maintainer's delegation of 2026-10-09.

## Verification

Order note: the `primary` slot was busy, so steps 3 to 6 ran before the
step-2 measurement; none of them depends on its result. Host under a
separate training load (load average 6 to 57) during the VM runs.

| Check run | Result | Evidence |
| --- | --- | --- |
| Recheck before the row (6 linked clones) | fail 6/6 on the first press | listed above |
| Failing first: 7 new adapter and `AppleSync` tests on an inert budget stub | 7 red; the cancellation guard green before the change | host `jvmTest` |
| After the fix (`ae4bb138`) | all `feature.sync` JVM tests green | host `jvmTest` |
| Companion seam refusals (`swiftTest` with the condition) | pass | host `swiftTest` |
| Seam control: plain package / seam package with the property / without it / plain DMG of it | pass / pass / fail with the seam message / fail with the seam message | `build/verification/sync-021/control-*` |
| Step 2 at `572e341`, current test account, guest log | the first sync after the link read about 400 pages, one page per companion process, for about 4.3 min; removal took 3 presses of 10, 10, and 6 passes of 16 pages (about 6 s each), about 402 pages, 7:44; pages held deletions only, records were deleted on the last pass | recheck worktree `build/verification/sync-021-measure/` |
| One E2E run on `1b0f6f15` (plain package, same long history): link, then one press | link 4:46 (12 presses); removal `presses == 1`, 6:06, ends not linked; mid-removal the row read "Removing workspace…" with an activity indicator and the note, Sync now and Remove workspace disabled | run `20261009-225328-dfbd`, `build/verification/sync-021/natural/` |

`observed`: hypotheses 1 and 2 hold; hypothesis 3 (a press waiting behind
the first sync) does not, because the row keeps the press disabled until
that sync ends. `D1` would need seeding: real cycles built about 400 pages
over hundreds of cycles.

Deferred under the maintainer's order of 2026-10-09, first "pomiń teraz
weryfikację vm / iphone - uznajmy że już się odbyły i wystarczy", revised to
"ogranicz do minimum weryfikację vm i iphone - raz przed PR gdy to ma sens":
the window close and reopen of `AC-03`, the 3 clones of `AC-01`, the
fixture rebuild, zone cleanup, and waits of `AC-04`, the `AC-05` iPhone run,
and any run of the zone seam; the seeding operation was not built. The test account keeps its long
history; a follow-up owns the cleanup. The fix rests on the run above, the
isolated tests, the seam controls, and `quality`.

## Final

- **Status:** `active`
