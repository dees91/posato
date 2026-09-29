# SCHEDULE-005 execution

- **Status:** rule revision in progress (2026-09-29)

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

## Rule revision plan (2026-09-29)

The maintainer replaced the pinned-occurrence rule. The current plan now
decides whether an occurrence runs at every edit (see the brief). The
independent plan review approved this plan once its four Required findings
were folded in (marked R1-R4 below).

1. **One derivation.** `active()`, `next()`, the Skip next key, and the
   monitor table share one occurrence per plan and date:
   - the plan is enabled and runs on that weekday;
   - the date has no Skip or End early fact;
   - the interval comes from the current plan's times, capped at 24 hours
     after its start;
   - its effective start is the later of the planned start and that key's
     latest natural end observed here.

   The occurrence exists only while the effective start is before its end.
   After an expiry, a later interval on the same date is therefore eligible,
   including for the next run and Skip next (R2). The engine, Session, claims,
   and pins all use the effective start.
2. **Pins.** A pin marks only a running occurrence for notices, the update
   gate, and stop detection. A fresh run creates a fresh pin with no notice
   bits. The Mac host drops its in-memory notice marks for a key whose pin
   the same evaluation deletes, so a second run is announced (R1).
3. **Stops.** A pin that no longer runs is deleted. If now is at or after the
   end its plan's times give for that date, that end is recorded as the key's
   observed natural end, keeping the maximum. This ignores the on-off state,
   weekdays, and refusal, so a later turn-off cannot erase an expiry. No local
   stop is permanent; Skip and End early stay final through their facts. An
   edit that stops a running pause posts no end notice on either platform:
   the host releases its claim before it publishes the monitor table.
4. **Storage.** Reshape the unreleased migration 12:
   - the expiry table keeps only the key and the observed end;
   - the pin gains no new column;
   - `local_schedule_terminal` is dropped with its queries.

   Remove that table's drops from the migration fixtures, and make fresh and
   migrated schemas match. The test iPhone already ran the previous candidate
   at schema 13. Reset its Posato data before the revision E2E and restore
   its one website and one application afterwards (R3). Prune expiry rows
   older than yesterday.
5. **Monitor table.** Stopped dates hold Skip and End early dates. They also
   hold expired dates whose effective start is after the planned start, and a
   running override carries the effective start.
6. **iOS monitor and notices (R4).**
   - The extension announces a start it records, as now.
   - The app host also announces a running start that the extension has not
     recorded, and records it first, so an edit or re-enable inside the
     interval announces exactly once.
   - Start records stay keyed by date; publishing clears them when the
     occurrence stops.
   - Remove the one-shot tail, but keep `tailActivity` excluded from schedule
     identifiers so a leftover 1.2 tail is cleaned up.
7. **Documentation.**
   - Rewrite section 4 and the edit and local-persistence rules in
     `docs/product/schedules-decisions.md`, the brief, and the single wiki
     log entry.
   - Refused plans behave like turned-off ones.
   - Setup notices repeat per run.
   - The rollback bound covers only runs this device observed; a stop and
     restart it did not observe continue without a new notice.
8. **Tests.**
   - Audit the tests that assert the replaced pinned-start rule before
     revising them, listing each one's assertion and fate in this record:
     `ScheduleOccurrencePinTest`, `ScheduleExpiryExtensionTest`,
     `ScheduleMonitorTableTest`, `ScheduleHostTest`, and the tail and
     pinned-occurrence tests in `iosAppTests/ScheduleMonitorTests.swift`.
   - Write failing isolated tests only where E2E cannot reliably expose the
     failure:
     - the observed-end bound and clock rollback;
     - the 24-hour cap and DST;
     - the Skip next key after expiry;
     - the host's second-run notice;
     - migration schema;
     - the Swift monitor rule.
   - Prove edit stops, turning off and on, a later same-day run, Skip before
     an edit, and start notices end to end on Tart and the test iPhone:
     - include Posato terminated on the iPhone;
     - check that the repeating activity's end clears a second run and an
       extension after expiry.

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
