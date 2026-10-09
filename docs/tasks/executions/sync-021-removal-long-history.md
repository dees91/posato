# Execution: `SYNC-021`

- **Brief:** [One press removes a Mac workspace with a long zone history](../specifications/sync-021-removal-long-history.md)
- **Status:** `active`: implementation started 2026-10-09 after plan
  review round 3 (Required items folded; decided by the coordinator under
  the maintainer's delegation of 2026-10-09).
- **Review tier:** `high-risk`: the accepted development-only seams (`D1`,
  `D4`) delete CloudKit records or the zone; see the brief
- **Implementer:** Claude
- **Reviewer:** plan reviews of `7abfa5c2` and `64ab9efc`, both
  `changes-required` and folded below (decisions in them are the
  coordinator's under the maintainer's delegation of 2026-10-09);
  re-review pending
- **Branch:** `task/sync-021-removal-long-history`
- **Updated:** 2026-10-09 (decisions recorded)

## Evidence before the row

`observed` (2026-10-09, recheck on `main` `572e341`, fresh `primary`
clones, development package, test Apple Account): linking took 195 to
228 s; the first press failed 6 of 6 times after about 59 s or 4 minutes;
removal took 3 or 4 presses and up to about 7 minutes, with about 110
change fetches a minute in companion processes of 16 fetches.

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
   and the `removing` re-entry guard. The cancellation test is a regression
   guard written before the loop change. The
   cap tests at `MacOsMailboxAdapterTest:159-205` are rewritten for the
   removal budget; the bootstrap sweep keeps a cap-of-10 assertion;
   `:252-280` stay.
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
6. **Verification-only seams**, under the controls of the ADR 0007
   amendment of 2026-10-09; implementation specifics:
   - `posatoMacOsVerificationSeams` adds `-Xswiftc -DPOSATO_VERIFICATION`
     as an input of `:macosSyncCompanion:buildSwiftRelease` and fails
     configuration on a non-blank `posatoMacOsReleaseSigningIdentity`, a
     release channel, or a named release task in `taskGraph.whenReady`.
     `posato-control build --verification-seams` sets it; `swiftTest`
     builds with the condition and tests the companion refusals.
   - One scan for the `PosatoVerificationSeams` key and the live marker
     `posato-verification-seams-v1`, used by `VerifyMacOsDevelopmentPackaging`
     (`verificationSeams` input, `found == verificationSeams.get()`) and by
     `GenerateMacOsUpdateFeed.publishedApplication` before the staged
     `Info.plist` comparison, on every channel. Control: the development
     check run with `-x :desktopApp:stageMacOsDevelopmentPackage` on a
     flag-built package without the property, and the feed task on a plain
     `hdiutil create` image of it, each failing with the seam message; the
     routine development package run shows the pass.
   - Companion `seedHistory` (writes, then deletes; canonical UUID names,
     non-empty payloads of at most 64 KiB passing
     `RecordCodec.validateBundle`, no new record type) and
     `deleteZoneForVerification`. Kotlin: a separate verification client,
     enum, and decoder in `feature/sync/macos/verification/`; a
     launch-argument handler holding `AppleBootstrap.flight`, requiring
     `LOCAL_ONLY` and `FileInstanceLock.tryUpgradeForAdmission()`; the
     tool quits Posato first, and `vm sync-fixture` checks `AC-04` itself.
7. **Verify** the brief's matrix with `flow icloud remove --timeout-seconds
   1500`, then the `AC-04` order; completed-change review, `qualityLint`,
   `quality`, closeout. The iOS cap is idea 34, backlog row `IOS-008` and
   Projects item `PVTI_lAHOAB0Ak84Bj5GQzg_ttMo`, an agent proposal under
   the maintainer's delegation of 2026-10-09.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Recheck before the row (6 linked clones) | fail 6/6 on the first press | listed above |

## Final

- **Status:** `active`
