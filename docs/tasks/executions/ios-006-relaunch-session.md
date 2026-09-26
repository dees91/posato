# Execution: `IOS-006`

- **Brief:** [ios-006-relaunch-session.md](../specifications/ios-006-relaunch-session.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code
- **Reviewer:** independent general-purpose agent (plan and completed change)
- **Branch:** `feature/ios-006-relaunch-session`
- **Updated:** 2026-09-26

## Cause

`observed` on the test iPhone (iOS 26.5), confirming the code analysis. After
a relaunch `SessionTransitionOwner.reconcile` has lost the in-memory
`enforcedIdentity`, so `reapplyCurrent` clears (`cancel`: `stopMonitoring`,
`removePending`, store clear), re-applies, and schedules the same session
again. Stopping monitoring inside a running window delivers `intervalDidEnd`
to the extension within about 2 s; the extension cleared the store
unconditionally and, reading the new pending record, recorded the running
session as expired, which the next `reconcile` banked. The status poll and
`retry()` reach the same path.

## Plan

0. `/skill-advisor` after plan approval.
1. Reproduce with `session-relaunch-ios.json` and a device probe
   (`SuspendedExpiryDeviceTests`, synthetic UUIDv4 id, refuses to run over
   existing monitoring, window already running, 120 s restart window, then
   the real end, cleanup in `defer`).
2. Relaunch adoption: `EnforcementPort.holdsSession` (default `false`);
   `reconcile` adopts a session whose status is `APPLIED`, whose version 2
   pending record names it, and whose installed schedule ends where that
   record says, setting what a re-apply sets. A version 1 record from 1.1 is
   re-applied once instead.
3. Extension guard: pending record version 2 with the end components and the
   absolute end; skip a callback more than 60 s before
   `min(resolved components, absolute end)`; no clear without a pending
   record; clear without a record when it is unreadable; version 1 readable.
4. Swift, `commonTest`, and `iosTest` tests; device runs; review; `quality`;
   wiki.

Maintainer decisions (`user-confirmed`, 2026-09-26): the `commonMain` touch
outside the brief's write surface, and a relaunch keeping the set the session
started with, so Paused-items edits made during a session no longer apply on
relaunch.

## High-risk plan review

- **Verdict:** approved with corrections.
- **Required findings:** R1 time-zone and calendar resolution could skip the
  real end; R2 a shared schema version would break 1.1 cleared records; R3
  adoption must set the re-apply view state; R4 no device proof of the real
  end after a skipped callback; R5 the probe could block later sessions; R6
  the `commonMain` deviation.
- **Resolution:** R1-R5 folded into the plan before code; R6 accepted by the
  maintainer.

## Result

- Adoption on relaunch and the extension guard as planned; the relaunch path
  no longer restarts monitoring for a held session, and a restart elsewhere
  (poll loss, Retry) no longer ends the session.
- Two Swift tests changed on purpose (no pending record clears nothing; the
  store-isolation test writes one). verify-posato: the relaunch scenario must
  pass, Safari black-page note, probe named. Wiki: iOS enforcement, sync note.
- Deviation: `readStatusAndHeld` keeps `reconcile` under Detekt's limit.

## Completed-change review

- **Verdict:** approved at `8d67a18` after one correction.
- **Required findings:** a 26-hour calendar-change branch in `isEarly`,
  outside the reviewed plan, could skip the real end.
- **Resolution:** removed; plain `min` with a two-direction calendar test.
- **Advisory findings:** the wiki claim about an interval that had not
  started is now `inferred`; the probe precheck requires an absent pending
  record. Declined: `isScheduled` checking `isCapable`, resetting
  `confirmedClear` on adoption (both harmless).
- **PR review P2 (accepted by the maintainer):** a death between the pending
  write and `startMonitoring` could adopt a session whose schedule was never
  installed. `f679d80` compares the installed schedule's end with the record;
  its review approved the code and corrected this record and the wiki.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Baseline `session-relaunch-ios.json` | fail (expected) | `20260926-091517-2b4f`: failed at `active-after-second-relaunch`, "NO SESSION ACTIVE" |
| Baseline device probe | fail (expected) | cleared record for the running session within 2 s of the restart |
| Device probe with the fix | pass | restriction kept through 120 s; real end cleared and recorded (322 s) |
| `session-relaunch-ios.json` with the fix, runs 1, 4, 5 | pass | `20260926-093443-37d4`, `-094327-f02c`, `-094554-5007` (9 relaunches) |
| Run 2 | fail, not a regression | `20260926-093710-352e`: Safari hung on a black page at `site-blocked-after` after the Calculator shield passed; `observe-blocking-ios.json` in the same session passed (`-094250-cf7d`) |
| Run 3 | setup failure | failed at `home-inactive` because run 2 left its session active |
| Early end and `observe-unblocked-ios.json` | pass | `20260926-094318-9560` |
| Suspended expiry after one relaunch and termination | pass | 25-minute session (`20260926-094838-d8fd`), relaunched, terminated; after the end Calculator had no shield, Safari hung once on a black page (`-101701-fd87`), the repeat loaded Example Domain (`-101752-deef`), Posato showed no session |
| Kotlin `jvmTest`, `iosSimulatorArm64Test` | pass | after the last correction |
| `iosSwiftTest` | pass | 147 tests, 7 device-only skipped |
| After `f679d80`: device probe, relaunch scenario | pass | probe also asserts `isScheduled` on the real center; `20260926-105917-aa83` |
| `./gradlew quality` | pass | at `8d67a18` (one Simulator install failure passed on rerun) and at the final head |

`8d67a18` changes only calendar-change behavior, covered by unit tests. Safari
hung on a black page twice in about ten loads; the same store's Calculator
result was observed each time. Run ids are under the ignored
`build/verification/runs/`.

## Blockers and accepted risks

- Accepted risk: the pending record is written before monitoring starts. If
  the process dies in between while the previous session's schedule survives
  and Posato is not reopened, that schedule's end is skipped and the new one
  has no callback; a relaunch re-applies. Before `IOS-006` the same death
  ended the new session as expired.
- Accepted risk: without a pending record the extension no longer clears, so
  a cancel that succeeds while the store clear fails is left to the
  `CLEAR_FAILED` Retry.
- The availability-page limit stays until `RELEASE-004` publishes the fix.

## Final

- **Status:** `done`
- **Outcome:** met; AC-01 to AC-04 evidenced above.
