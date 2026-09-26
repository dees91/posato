# Execution: `IOS-006`

- **Brief:** [ios-006-relaunch-session.md](../specifications/ios-006-relaunch-session.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code
- **Reviewer:** independent general-purpose agent (plan review)
- **Branch:** `feature/ios-006-relaunch-session`
- **Updated:** 2026-09-26

## Cause from code

`inferred`, to be confirmed on the device in step 1. After a relaunch,
`SessionTransitionOwner.reconcile` sees `status() == APPLIED` but has lost the
in-memory `enforcedIdentity`, so it calls `reapplyCurrent`. That path runs
`prepareApply(clear = true)` → `IosSessionEnforcement.clear()`, which calls
`SuspendedExpiryScheduler.cancel()` (`stopMonitoring` and `removePending`) and
clears the named store, then `apply()` re-applies the restrictions and
`schedule()` writes a new pending record for the same session and calls
`startMonitoring` again. Stopping or replacing the monitored activity delivers
`intervalDidEnd` to the monitor extension asynchronously.
`SuspendedExpiryClear.handleIntervalEnd` clears the store unconditionally, and
when it reads the new pending record it writes a cleared record for the
running session. The next `reconcile` reads that record through
`peekSuspendedExpiry` and banks the session as expired. The race explains
4 of 6 and every fast relaunch. When the callback lands before the new pending
record, restrictions are lifted while the session still shows as active. The
status poll (`onTickSecond` → `handlePollLoss`) and `retry()` reach the same
`reapplyCurrent`; step 3 covers them. `IOS-002` stopped a window that had not
started and saw no clear, so the cause needs a window already running.

## Plan

0. Run `/skill-advisor` after plan approval, before any code.
1. **Reproduce (AC-01).** On the test iPhone with the unchanged branch, run
   `session-relaunch-ios.json` (expected to fail). Then add a device probe in
   `SuspendedExpiryDeviceTests`, skipped on the Simulator, run unattended with
   `xcodebuild test-without-building` on the device. It refuses to run when a
   pending record or active monitoring exists, uses a synthetic UUIDv4 in the
   32-hex reconciliation form, schedules a window already running (start
   10 minutes ago, end in 6 minutes) through the real scheduler and
   extension, performs the relaunch sequence (`cancel`, store clear, apply,
   `schedule` for the same session), and polls at least 120 s for the store
   state and a cleared record. A cleared record for the running session
   confirms the cause; nothing observed is inconclusive, not a refutation.
   Any other result is recorded and the plan review repeats before code. It
   removes the pending and synthetic cleared records and asserts none remains
   before any scenario runs. The probe stays as the device regression check.
2. **No restart on relaunch (root fix).** Add
   `EnforcementPort.holdsSession(sessionId): Boolean` with a `false` default
   in `commonMain`. `reconcile` adopts the session (`enforcedIdentity = tag`,
   state active, no clear or re-apply) when `status()` is `APPLIED` and the
   port holds the session, asked under the same `portMutex` as `status()`.
   Adoption sets what a re-apply sets: `enforced` from the frozen start set
   or the loaded targets, `unknownStreak = 0`, `actionTag = null`, and
   `Active(false)`. The iOS adapter answers through a new
   `IosSuspendedExpiryProvider.isScheduled(sessionId)`: `true` only when the
   pending record names this session and `DeviceActivityCenter.activities`
   still contains the Posato activity (added to the monitoring seam). A
   session below the 15-minute minimum has no pending record and keeps
   today's re-apply, which starts no monitoring. macOS keeps the default, so
   its Resume path is unchanged.
3. **Early-callback guard (safety net).** The pending record gains the
   interval-end date components (year to minute) exactly as passed to
   `DeviceActivitySchedule` and the absolute end in epoch seconds, as pending
   schema version 2 in the same file with its own version constant; cleared
   records stay at version 1. A new pending read returns absent, present
   (version 1, or version 2 with both end fields), or unreadable (anything
   else, including a newer version). `handleIntervalEnd` then:
   - skips entirely (no clear, pending kept, no record) when now is earlier
     than `min(components resolved with the current calendar, absolute end)`
     minus 60 s; components that fail to resolve mean clear;
   - clears and records as today for a version 2 record at or past its end
     and for a version 1 record;
   - no longer clears when no pending record exists, because every remover
     of the pending record also stops monitoring or has already cleared;
   - still clears, without a record, when the pending record exists but
     cannot be read (for example before first unlock), preserving the
     `IOS-002` fail-safe.
   Two existing tests change on purpose: clearing without a pending record,
   and the late previous-expiry test (`SuspendedExpiryTests.swift:410`),
   which gains an in-window variant that must skip.
4. **Tests.** Swift `SuspendedExpiryTests`: early, on-time, and late
   callbacks with an injected calendar and clock; eastward and westward
   time-zone changes and a calendar change; version 1 compatibility and a
   1.1 cleared record still readable; absent versus unreadable pending; the
   version 2 write; `isScheduled` true only for a matching pending record
   with active monitoring. Kotlin: iOS adapter mapping in `iosTest`; in
   `commonTest`, `reconcile` adopts without calling `clear` or `apply` when
   the port holds the session, including a variant of the displayed-set test
   (`SessionEnforcementTest.kt:350`), and falls back to re-apply when not.
5. **Device verification (AC-03, AC-04).** On the test iPhone through
   `posato-control -t device`: `session-relaunch-ios.json` three times (nine
   relaunches, fast, 5 s, and 120 s windows, shield and Safari checked after
   the callback window); the step 1 probe after the fix (restrictions stay,
   no cleared record within the callback window, then the real end still
   clears the store and records the synthetic session); suspended expiry
   with a 25-minute session, one relaunch, the app terminated through the
   driver, and `observe-unblocked-ios.json` after the
   end; `session-early-end.json` with `observe-unblocked-ios.json`.
6. Independent completed-change review, `./gradlew quality`, wiki updates
   of the iOS enforcement topic (the open question answered) and the
   `cross-device-synchronization` note that a native `APPLIED` cannot name
   the session (now a session-bound proof on iOS), one wiki-log
   entry, and record closeout. The availability-page limit stays for
   `RELEASE-004`.

Alternatives rejected: deferring `cancel()` inside the iOS adapter until the
next `apply()` hides a lifecycle decision in the adapter and keeps the
clear-then-apply gap. The guard alone fixes the symptom but keeps a restart
and a momentary clear on every relaunch, against the roadmap outcome.

Brief deviation: step 2 touches `commonMain` (`EnforcementPort`,
`SessionTransitionOwner.reconcile`), which the brief's write surface did not
list. It adds a defaulted capability and no session model change; macOS
keeps the default through `JvmSessionEnforcement` and `GatedEnforcementPort`.
Behavior change: a relaunch on iOS no longer applies Paused-items edits made
during the session; the session keeps the set it started with. Both accepted
by the maintainer (`user-confirmed`, 2026-09-26).

## High-risk plan review

- **Verdict:** approved with corrections
- **Critical or Required findings:** R1 time-zone and calendar resolution can
  skip the only real end; R2 one shared schema version would break 1.1
  cleared records and displacement; R3 adoption must set the re-apply view
  state; R4 no device proof that the real end fires after a skipped early
  callback; R5 the probe could leave the iPhone unable to start sessions;
  R6 the `commonMain` touch needs maintainer acceptance.
- **Resolution:** R1-R5 folded into steps 1-5 above; R6 accepted by the
  maintainer (`user-confirmed`, 2026-09-26). Recommended: Paused-items behavior change stated above; second
  changed test listed in step 3; accepted risk below.

## Result

- Pending.

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |

## Blockers and accepted risks

- Accepted risk (proposed): with "absent pending, no clear", a cancel that
  succeeds while the store clear fails is no longer rescued by the
  extension; the `CLEAR_FAILED` retry covers it.

## Final

- **Status:** pending
- **Outcome:** pending
