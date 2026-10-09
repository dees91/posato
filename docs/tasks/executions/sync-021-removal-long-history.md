# Execution: `SYNC-021`

- **Brief:** [One press removes a Mac workspace with a long zone history](../specifications/sync-021-removal-long-history.md)
- **Status:** `active`: brief only. Decisions `D1`–`D4` are accepted
  (2026-10-09); implementation waits for the plan review and the
  maintainer's go.
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
   and the `removing` re-entry guard (a second call during a removal). The
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
5. **Progress state.** `AppleSync.removeWorkspace` checks and sets
   `removing` atomically (`getAndUpdate`), returns at once when it was set,
   and clears it in `finally`; it precedes `SYNCING`. A platform-composition
   boolean, true only in the macOS composition, is passed into the row:
   with it the row shows "Removing workspace…", "An older workspace can
   take a few minutes.", and a `PosatoActivityIndicator` with actions
   disabled; without it iOS shows `removing` as today's running state.
   `DESIGN.md` gains the macOS row state; `posato-control`'s `ICloudRow`
   learns `removing`; the verify-posato sync page is updated.
6. **Verification-only seams.**
   - Build: `posatoMacOsVerificationSeams` adds `-Xswiftc
     -DPOSATO_VERIFICATION`, is an input of
     `:macosSyncCompanion:buildSwiftRelease`, and fails configuration when
     `posatoMacOsReleaseSigningIdentity` is non-blank, the update channel
     is release, or a release task is in the task graph. With it, the
     development package sets `PosatoVerificationSeams` in `Info.plist`.
     `posato-control build --verification-seams` passes it for fixture
     runs only. `swiftTest` builds with the condition and tests the
     refusals (anchor present, non-Development leaf, entitlement present).
   - Proof: `VerifyMacOsDevelopmentPackaging` gains a `verificationSeams`
     input (false on release, the property on development) and checks
     `found == verificationSeams.get()`, as the Rosetta switch does. One
     function scans the key and the marker literal
     `posato-verification-seams-v1`, used live under the condition; both the
     package check and `publishedApplication` use it. Positive control: the
     development task on a flag-built package with the input forced off,
     and the DMG reader on a plain `hdiutil create` image of that package,
     each failing with the seam check's own message.
   - Companion: `seedHistory` and `deleteZoneForVerification` exist only
     under the condition. They check the anchor (seeding needs it missing;
     deletion refuses it present), the absent environment entitlement, and
     an Apple Development leaf. Seeds use canonical UUID names and
     non-empty payloads of at most 64 KiB that pass
     `RecordCodec.validateBundle`, with no new record type.
   - Kotlin: a separate verification client with its own operation enum and
     decoder in `feature/sync/macos/verification/`; product adapters and
     decoding stay product-codes-only. A launch-argument handler, inert
     without the key, takes `AppleBootstrap.flight` for the whole operation
     and runs only while `coordinator.checkEstablished()` is `LOCAL_ONLY`,
     inside the single running instance. `vm sync-fixture seed|delete-zone`
     itself checks that the anchor is absent, that its clone is the only
     running Tart VM, and that the guest's account is the test account.
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
