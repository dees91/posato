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

1. **Authority first:** the accepted ADR 0007 amendment and `T-14` text.
2. **Measure** one link and one press in a `primary` clone from the guest
   log before anything is cleaned: passes, pages, and their content.
3. **Failure inventory**, isolated tests written failing first for what E2E
   cannot produce: a stalled or resumed-but-stalled cursor, repeated
   `Unknown` exchanges, an endless advancing cursor (ceiling), cancellation
   (a regression guard written first), and the `removing` re-entry guard;
   the cap tests at `MacOsMailboxAdapterTest:159-205` become removal-budget
   tests and the link sweep keeps its cap of 10.
4. **Fix:** `RemovalBudget` (shared `feature/sync/mailbox`), passed only by
   `AppleWorkspaceRemoval`; the macOS adapter's `PassGovernor` continues
   while each checkpoint is new to the press (the resumed cursor counts as
   seen), ends `Retryable` after 3 passes without progress or a 20-minute
   monotonic ceiling from the first pass, and lets companion outcomes and
   cancellation end it as before; `IosMailboxAdapter` ignores the budget.
5. **Progress state:** an atomic `removing` guard inside `AppleSync`, ahead
   of `SYNCING`; the macOS row's "Removing workspace…", note, and activity
   indicator behind a platform-composition flag; `DESIGN.md`, `ICloudRow`,
   and the verify-posato sync page.
6. **Verification-only seams** under the amendment: one Gradle property
   (Swift condition, `Info.plist` key, refusals at configuration including a
   Developer ID development identity, and in the task graph), one scan in
   the packaging check and first in the DMG reader, an Apple Development or
   ad-hoc companion leaf required with the seams, the companion zone
   deletion, a separate client and launch-argument entry, and
   `posato-control build --verification-seams` and `vm sync-fixture`.
7. **Verify** the matrix, review, `quality`, closeout. The iOS cap is idea
   34 and `IOS-008` (Projects `PVTI_lAHOAB0Ak84Bj5GQzg_ttMo`), an agent
   proposal under the maintainer's delegation of 2026-10-09.

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
| Seam control, repeated after review: plain package / seam package with the property / without it / plain DMG of it / the property with a Developer ID name, a SHA-1 hash, an `Apple Development:` name, `-` | pass / pass / fail with the seam message / fail with the seam message / refused, refused, accepted, accepted | `build/verification/sync-021/control2/`, `control3/` |
| Step 2 at `572e341`, current test account, guest log | the first sync after the link read about 400 pages, one page per companion process, for about 4.3 min; removal took 3 presses of 10, 10, and 6 passes of 16 pages (about 6 s each), about 402 pages, 7:44; pages held deletions only, records were deleted on the last pass | recheck worktree `build/verification/sync-021-measure/` |
| One E2E run on `1b0f6f15` (plain package, same long history) | first link (run `20261009-221425-0969`) `WAIT_TIMEOUT` after 25 min and 83 presses: the guest log shows the companion refusing its parent with `cdhash mismatch`, because `vm sync` had replaced the bundle under the app `vm onboard` started (a procedure error, not the product); after a relaunch the link (`20261009-224529-9bea`) took 4:46 and 12 presses and settled at "sync did not finish"; one press (`20261009-225025-3aa7`) removed the workspace, `presses == 1`, 6:06, ending not linked; mid-removal (`20261009-225328-dfbd`) the row read "Removing workspace…" with an activity indicator and the note, Sync now and Remove workspace disabled | `build/verification/sync-021/natural/` |
| `./gradlew quality` on `1fede3de`, the last code commit | pass (6:47, host under the training load; native iOS Swift tests ran) | host |

`observed`: hypotheses 1 and 2 hold; hypothesis 3 (a press waiting behind
the first sync) does not, because the row keeps the press disabled until
that sync ends. `AC-02` therefore rests on a link whose first sync ended
"sync did not finish"; on this history the first sync after a link reads
every page one per companion process and did not finish (idea 35,
`SYNC-022`). `D1` needs seeding: real cycles built about 400 pages over
hundreds of cycles. On iOS, `running` now includes `removing`, which
disables both actions from the press on; the copy is unchanged.

Reduced verification, translation of the maintainer's order (2026-10-09):
first "skip the VM and iPhone verification now; let's consider them done
and sufficient", then revised to "keep VM and iPhone verification to a
minimum: once before the PR, where it makes sense". The fix therefore rests
on the run above, the isolated tests, the seam controls, and `quality`. Unit
tests alone cover the review fold made after that run, because
`PassGovernor.resumeFrom` affects only a press that resumes a kept cursor
and the `Main.kt` change runs only with the verification argument.

| Acceptance | State | Owner |
| --- | --- | --- |
| `AC-01` | partly met: one press on the real long history; 3 clones and the pre-fix run on a rebuilt fixture deferred | `SYNC-022` |
| `AC-02` | partly met: one press right after a link whose first sync did not finish | `SYNC-022` |
| `AC-03` | partly met: removing state seen and actions disabled; window close and reopen deferred; re-entry guard tested in isolation | `SYNC-022` |
| `AC-04` | deferred: seeding not built, no zone cleanup, the test account keeps its history | `SYNC-022` |
| `AC-05` | deferred: no iPhone run | `SYNC-022` |

GitHub Projects: backlog item `SYNC-022` added with this change.

## Final

- **Status:** `active`
