# Execution: `SYNC-021`

- **Brief:** [One press removes a Mac workspace with a long zone history](../specifications/sync-021-removal-long-history.md)
- **Status:** `active`: brief only. Decisions `D1`–`D4` are accepted
  (2026-10-09); implementation waits for the plan review and the
  maintainer's go.
- **Review tier:** `high-risk`: the accepted development-only seams (`D1`,
  `D4`) delete CloudKit records or the zone; see the brief
- **Implementer:** Claude
- **Reviewer:** plan review pending
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

1. `inferred`: a per-press cap. `MacOsMailboxAdapter.deleteWorkspaceRecords`
   runs at most `MAX_DELETE_ATTEMPTS` (10) companion passes, each at most
   `recordDeletePageBudget` (16) change pages within 30 s, so 160 pages a
   press. The zone is never deleted (`SYNC-015`), so the traversal from an
   empty token grows with the account's history. 10 passes of about 6 s
   match the 59 s first press; the in-memory continuation explains 3 or 4
   presses. An `Unknown` exchange also spends one of the 10 attempts.
2. `hypothesis`: pages carry few records, so pages track history, not the
   live workspace.
3. `hypothesis`: a press made while the exchange loop still holds
   `AppleBootstrap.flight` waits for it, which would explain the ~4 min
   first presses.
4. `inferred`: a quit drops the continuation; the next press restarts
   from an empty token, safely but repeating the traversal (accepted, `D3`).

## Plan

1. **Measure in one `primary` clone, before anything is cleaned.** Link,
   press **Remove workspace** once, and record from the guest log (`log
   stream` of `PosatoMacOSSync` and `cloudd`, no product change): passes per
   press, pages and records per pass, whether pages hold deletions or live
   records, the time between the press and the first delete request
   (hypothesis 3), and the pages a full drain needs. Then measure what one
   real link, publish, and removal cycle adds. If cycles rebuild the
   measured history in about 30 min, `D1` uses them; otherwise the seeding
   seam below.
2. **Failing-first proof.** On the fixture, `flow icloud remove
   --presses 1` (or its envelope's `presses`) fails before the fix. A
   Kotlin adapter test with a fake transport that answers `Incomplete` with
   an advancing cursor for 11 or more passes fails today (returns
   `Retryable`) and passes after the fix; a second fake that repeats the
   same cursor must still stop.
3. **Fix, Kotlin adapter only.** In `deleteWorkspaceRecords` and
   `sweepBundlesIfAnchorMissing`, replace `repeat(MAX_DELETE_ATTEMPTS)` with
   a loop that continues while each `Incomplete` cursor differs from the
   last, and ends with `Retryable` after 3 consecutive passes without
   progress (an `Unknown` exchange or an unchanged cursor) or a wall-clock
   ceiling of 20 minutes. Cancellation still propagates, and the stored
   continuation keeps the last banked cursor.
   - Unchanged: the companion, its 16-page and 30 s pass bounds, the token
     format and phases, the verify gate, bundles first and anchor last,
     binding checks per request, the terminal outcome mapping, and
     `clearRemovalResumeState`. The ADR 0007 procedure does not change.
     A one-line note records that one press resumes passes while they
     advance.
4. **Progress state.** `AppleSync.removeWorkspace` sets `removing` in
   `AppleSyncState` before it waits for the flight lock and clears it in
   `finally`. While it is set, `SyncBootstrapSection` shows "Removing
   workspace…", the note "An older workspace can take a few minutes.", and
   a `PosatoActivityIndicator`, and keeps both actions disabled, following
   the `DESIGN.md` removal-progress pattern. `DESIGN.md` gains that row
   state. iOS shares the copy; its cap stays (new idea if the same).
5. **Development-only seams** (only what step 1 needs; delete-zone always):
   - Companion operations `seedHistory` (save then delete N empty-payload
     bundle records, leaving only history) and `deleteZoneForVerification`,
     compiled under a Swift `POSATO_VERIFICATION` condition that only the
     development package's companion build passes. Release and candidate
     builds compile without it, so the operation codes do not exist and
     parse fails as for any unknown operation.
   - At runtime both refuse unless the companion's
     `icloud-container-environment` entitlement is absent (Development),
     and delete-zone also refuses while a local workspace is established.
   - Entry: a verification-only launch argument of the Posato app, honored
     only with a development-package `Info.plist` key, following the
     `MACOS-015` Rosetta switch. `posato-control` gains `vm sync-fixture
     seed|delete-zone`.
   - Release proof: the release packaging and DMG checks refuse the key and
     fail if the companion binary contains the seam's marker string,
     shown on a release-configuration build.
   - Recorded as a scoped exception to ADR 0007's "never the zone" for
     verification builds only, with `T-14` noted.
6. **Verify** the matrix below, then **clean up** (`AC-04`): fixture
   procedure recorded and shown failing pre-fix; workspace removed; every
   clone destroyed and the test iPhone local-only; `delete-zone` from a
   development package in a `primary` clone; no re-link inside the
   `SYNC-014` purge window; then one routine link and removal timed.
7. Completed-change review, `qualityLint`, `quality`, closeout.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Recheck before the row (6 linked clones) | fail 6/6 on the first press | listed above |
| Planned: step 1 measurement; pre-fix failure on the fixture; adapter tests red then green; one press in 3 fresh clones and right after a link (`AC-01`, `AC-02`); progress state and a no-progress stop (`AC-03`); release artifact without the seams; cleanup and a routine run (`AC-04`) | - | - |

## Final

- **Status:** `active`
