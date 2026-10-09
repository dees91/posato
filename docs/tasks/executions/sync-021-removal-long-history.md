# Execution: `SYNC-021`

- **Brief:** [One press removes a Mac workspace with a long zone history](../specifications/sync-021-removal-long-history.md)
- **Status:** `active`: brief only. Decisions `D1`–`D4` are accepted
  (2026-10-09); implementation waits for the plan review and the
  maintainer's go.
- **Review tier:** `high-risk`: the accepted development-only seams (`D1`,
  `D4`) delete CloudKit records or the zone; see the brief
- **Implementer:** Claude
- **Reviewer:** pending
- **Branch:** `task/sync-021-removal-long-history`
- **Updated:** 2026-10-09 (decisions recorded)

## Evidence before the row

`observed` (2026-10-09, recheck on `main` `572e341` after PR #147, fresh
`primary` Tart clones, development package, test Apple Account; raw runs
under the recheck worktree's ignored `build/verification/`):

- Linking took 195 to 228 s with 11 or 12 **Check again** presses. The
  first sync after the link ran for about 3.3 minutes, or ended in "Sync did
  not finish".
- The first **Remove workspace** press failed in 6 of 6 runs, sometimes
  after about 59 s and sometimes after about 4 minutes of Syncing.
  Removal took 3 or 4 presses and up to about 7 minutes. Every first press
  came after the row had left Syncing.
- During a removal, the guest ran about 110 CloudKit change fetches a
  minute in short-lived `PosatoMacOSSync` processes, most with exactly 16
  fetches.

## Hypotheses

1. `inferred` (code reading): **a per-press work cap.** One press runs at
   most `MAX_DELETE_ATTEMPTS` (10) companion passes
   (`MacOsMailboxAdapter`). Each pass reads at most
   `SyncLimits.recordDeletePageBudget` (16) change pages within a 30 s
   deadline (`RecordDeletion.swift`). One press therefore covers at most
   160 pages.

   The drain must traverse the zone's change feed from an empty token. Since
   `SYNC-015` the zone is never deleted, so every bundle record ever
   published and removed on the account may still occupy that feed. After
   enough link and removal cycles, 160 pages are not enough. The press then
   reports retryable, and the in-memory continuation lets the next press go
   on, which matches 3 or 4 presses.
2. `hypothesis`: **change pages that hold few records.** Pages full of
   deletion entries carry few names each, so the page count grows with the
   account's history rather than with the live workspace. Each fetch takes
   about 0.5 s.
3. `hypothesis`: **the first sync after the link** traverses the same
   history from an empty cursor. It explains the slow link and the disabled
   row, but not the failed first press, which came after Syncing ended.
4. `inferred` (code reading): the continuation lives only in the adapter's
   memory. A quit between presses restarts removal from an empty token,
   which is safe because deletes are idempotent, but it repeats the whole
   traversal.

## Plan

1. Measure the fixture: pages and records per pass from the companion log,
   on the current long-history account, before anything is cleaned.
2. Build the fixture under `D1` and show the pre-fix failure on it.
3. Fix the smallest cause: keep resuming within one press while the cursor
   advances, stop on no progress, account change, or unknown outcome, and
   show the `D2` state. Re-check the tier if the fix moves beyond this.
4. Verify `AC-01` to `AC-03` in fresh clones.
5. Clean the test account's zone under `AC-04` and `D4`, then measure a
   routine link and removal again.
6. Completed-change review, then closeout.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Recheck before the row (6 linked clones) | fail 6/6 on the first press | listed above |

## Final

- **Status:** `active`
