# SCHEDULE-005 execution

- **Status:** complete; ready for review and merge

## Diagnosis

The host writes the same permanent terminal key for natural expiry and other
stops. Materialization changes the plan, but the engine rejects its terminal
key. The iOS monitor receives that date as stopped too. Existing terminal rows
contain no reason, original start, or observed end.

## Plan

1. Preserve explicit stop facts and permanent terminal keys.
2. Record natural expiry separately with original start, observed end, and the
   plan's end minute. Resume only after an end-time edit extends that bound,
   while the clock is at or after the observed end. Retain the original cap.
3. Keep shared evaluation and the native monitor's stopped dates consistent.
4. Verify persistence, repeated sync and clock boundaries, then real-device
   enforcement and cleanup. Complete independent review and quality.

## Accepted migration

Legacy terminal rows cannot distinguish natural expiry from other local stops.
The maintainer accepted reevaluating them under current plans while preserving
explicit Skip and End early facts (2026-09-28). Migration removes only legacy
local terminal markers.


## Plan review and regression

Independent High-risk plan review approved the approach with no Critical or
Required findings. It requires monotonic expiry bounds, stale pin/write guards,
and consistent monitor projection from each evaluation.

Before repair, `:shared:jvmTest --tests '*ScheduleExpiryExtensionTest*' fails
with expected one running occurrence but actual zero after materializing the
extended plan. The test uses the real SQLite store and existing calendar seam.


## Review corrections

The independent completed-change review found that a persisted resumed pin
bypassed the observed-expiry lower bound. A new post-persistence rollback
assertion failed before correction; the engine now checks that bound for every
resumed pin. The reviewer accepted the correction.

Inspection of the native monitor found the same risk through derived intervals.
A failing monitor regression preceded its correction: expired dates remain
stopped, and only a running override bounded by the observed expiry can resume
them. The original date and capped end remain unchanged. Independent review
approved this projection without a wire-format change.

## Local checks

Focused JVM, Detekt, and ktlint checks passed. Migration fixtures now remove the
new table when reconstructing older schemas. The existing host test expects a
natural-expiry record rather than a permanent stop. SQLDelight found a column
order mismatch between fresh and migrated schemas; the fresh schema now follows
the appended-column order. Full `./gradlew quality` passed after these corrections.


## Real-device result

Production revision `5eda62d` passed on the dedicated iPhone and Tart VM:

- An occurrence created in the pre-fix iPhone build expired naturally. The
  signed candidate preserved the plan, selection, consent, and workspace.
  Extending it on the Mac from 22:00 to 22:15 resumed the iPhone after sync.
- Both Safari and Calculator were blocked; the active pause and enforcement
  survived relaunch.
- At 22:15 both devices released restrictions. iPhone expiry passed with
  Posato terminated. Mac storage showed zero pins, one natural-expiry record,
  and zero permanent terminal markers.
- A further extension to 23:15 resumed both devices, including enforcement.
  The Mac pin retained its original start and the observed-expiry lower bound.
- End early on iPhone synchronized to Mac. Extending to 23:30 afterward left
  both inactive and both access checks passed.

Aggregate evidence: ignored `build/verification/runs/schedule005-e2e/README.md`,
with exact scenarios and individual run directories. Test/build logs remain
under `build/verification/runs/schedule005-regression/`. Safari's initial blank
loading view and one runner socket disconnect required retries; the assertions
passed afterward. A VM staged before the signed build needed `vm sync` before
setup. No host Posato installation was touched.

## Final checks and review

`./gradlew quality`, signed Mac package verification, and device build passed.
Independent plan review and completed-change review approved the final code;
the Required rollback finding and the subsequent native projection correction
both have failing-before-repair regressions. No Critical or Required findings
remain. No synchronization wire-format change or suppression was introduced.

## Pull request review corrections

The pull request review found three defects, each with a regression that
failed before its repair:

- **Required:** a host evaluation while the clock was before a resumed pin's
  observed-expiry bound stored a permanent stop. Such a pin now waits
  unchanged.
- **Recommended:** moving the end back to or before the observed expiry left
  the resumed pin counted as running, which held back the Mac update. Removal
  now compares with the stored expiry end.
- **Optional (from the correction review):** that removal dropped notice bits
  gained after resumption. They now merge into the expiry record.

The independent review found no Critical or Required issue, and full
`./gradlew quality` passed. On a signed Tart build, the Mac kept the pin with
no stop while its clock was rolled back. It resumed the original occurrence
and blocked again once the clock returned. After the end was moved back, it
held no pin and allowed access. Evidence:
`build/verification/runs/schedule005-review/README.md`.

## Cleanup

The synthetic schedule was removed and the test workspace unlinked on both
devices. The iPhone retained its original one website and one application, with
no schedules. The disposable Tart VM was destroyed after unlinking.
