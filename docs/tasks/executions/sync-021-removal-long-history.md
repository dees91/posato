# Execution: `SYNC-021`

- **Brief:** [One press removes a Mac workspace with a long zone history](../specifications/sync-021-removal-long-history.md)
- **Status:** `active`: brief only. Decisions `D1`–`D4` are accepted
  (2026-10-09); implementation waits for the plan review and the
  maintainer's go.
- **Review tier:** `high-risk`: the accepted development-only seams (`D1`,
  `D4`) delete CloudKit records or the zone; see the brief
- **Implementer:** Claude
- **Reviewer:** plan review of `7abfa5c2`: `changes-required` (7 Required, folded below); re-review pending
- **Branch:** `task/sync-021-removal-long-history`
- **Updated:** 2026-10-09 (decisions recorded)

## Evidence before the row

`observed` (2026-10-09, recheck on `main` `572e341`, fresh `primary`
clones, development package, test Apple Account): linking took 195 to
228 s; the first press failed in 6 of 6 runs after about 59 s or about
4 minutes; removal took 3 or 4 presses and up to about 7 minutes, with
about 110 change fetches a minute in companion processes of 16 fetches.

## Hypotheses

1. `inferred`: the per-press cap. One press runs at most
   `MAX_DELETE_ATTEMPTS` (10) passes of at most 16 pages within 30 s; the
   never-deleted zone's change history from an empty token grows with every
   cycle. An `Unknown` exchange also spends an attempt.
2. `hypothesis`: pages carry few records, so pages track history.
3. `hypothesis`: a press waits behind the exchange loop's
   `AppleBootstrap.flight` lock, explaining the 4-minute presses.
4. `inferred`: a quit drops the continuation; accepted under `D3`.

## Plan

1. **Measure** in one `primary` clone before anything is cleaned: one link,
   one press, guest `log stream` of `PosatoMacOSSync` and `cloudd`. Record
   passes per press, pages and records per pass, whether pages hold
   deletions or live records, the delay from the press to the first delete
   request, and the pages of a full drain; then what one real cycle adds.
   Fallback if the logs lack record counts: pages per pass from fetch-log
   lines per companion process, passes and presses per drain.
2. **Failure inventory** for isolated tests, each one E2E cannot produce:
   a cursor that never advances (CloudKit cannot be made to stall); repeated
   `Unknown` exchanges (killing the companion mid-request is not drivable);
   an advancing cursor that never ends (needs more than 20 minutes of real
   history); a restart after an expired token (expiry cannot be forced);
   cancellation keeping the banked continuation (internal state). Tests in
   `MacOsMailboxAdapterTest` with a fake transport, written failing first.
   The cap tests at `:159-205` are rewritten: the delete and removal-sweep
   variants assert the removal budget continues past ten advancing passes
   (this replaces the asserted cap, not a new case), and the bootstrap
   sweep keeps a cap-of-10 assertion. `:252-280` stay unchanged.
3. **Fix, Kotlin only.** A `RemovalBudget` parameter on both port methods,
   passed only by `AppleWorkspaceRemoval`; the default is the cap of 10.
   With the removal budget, a press continues while progress is monotonic:
   each `Incomplete` cursor must be new against a digest of the cursors
   seen in this press. A repeated cursor, or a restart after an expired
   token, counts as no progress, as does an `Unknown` exchange. The press
   ends `Retryable` after 3 such passes in a row, or at a 20-minute ceiling
   read from an injectable monotonic `TimeSource` and started at the first
   pass, after the lock. A companion `Retryable` or `UnknownOutcome` still
   ends the press at once, and cancellation propagates with the last banked
   cursor stored. ADR 0007's removal procedure is unchanged.
4. **Progress state, macOS only.** `AppleSync.removeWorkspace` returns at
   once while `removing` is set, sets it before waiting for the lock, and
   clears it in `finally`; `removing` takes precedence over `SYNCING`. The
   row shows "Removing workspace…", "An older workspace can take a few
   minutes.", and a `PosatoActivityIndicator`, with actions disabled, only
   when the platform reports a resuming removal (macOS); the iOS UI is
   unchanged. `DESIGN.md` gains the row state. `posato-control`'s
   `ICloudRow` learns `removing`, and the verify-posato sync page is updated.
5. **Verification-only seams.**
   - Build: one Gradle property, `posatoMacOsVerificationSeams`, adds
     `-Xswiftc -DPOSATO_VERIFICATION` and is a declared input of
     `:macosSyncCompanion:buildSwiftRelease`. Configuration fails when it is
     combined with Developer ID signing or the Production environment, on
     any channel. The development package then sets the `Info.plist` key
     `PosatoVerificationSeams`.
   - Proof: `verifyMacOsReleasePackaging` and the DMG check refuse that key
     and the seam's marker string in the companion binary. A positive
     control shows the check failing on a verification-flag package and
     passing on a release-configuration one.
   - Companion: `seedHistory` and `deleteZoneForVerification` exist only
     under the condition, so a release companion fails to parse them as
     unknown operations. Both refuse unless the
     `icloud-container-environment` entitlement is absent and the signing
     leaf is Apple Development, not Developer ID, and both refuse while a
     local workspace is established. `seedHistory` also refuses unless the
     anchor reads missing. It writes and then deletes bundle records with
     canonical UUID names and non-empty payloads of at most 64 KiB that pass
     `CloudRecords.validateBundle`, with no new record type.
     `deleteZoneForVerification` refuses while the anchor is present.
   - Kotlin: the two operations live in a separate verification-only enum
     in `feature/sync/macos/verification/`, outside `SyncCompanionOperation`.
     Only a launch-argument handler that runs when the `Info.plist` key is
     present reaches them. It runs inside the single running instance or
     refuses while another runs. `posato-control` gains `vm sync-fixture
     seed|delete-zone`.
   - Authority: the ADR 0007 amendment and `T-14` text from the pull
     request, accepted 2026-10-09, land as the first implementation commit.
6. **Verify** the brief's matrix, with `AC-03` using `vm network --state
   off` during a removal. That exercises the companion's retryable path
   and the retry, not the no-progress counter, which step 2 covers.
   Then the `AC-04` order.
7. Completed-change review, `qualityLint`, `quality`, closeout. The iOS
   cap is idea 34 and backlog row `IOS-008`, added in this pull request.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Recheck before the row (6 linked clones) | fail 6/6 on the first press | listed above |

## Final

- **Status:** `active`
