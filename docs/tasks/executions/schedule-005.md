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
explicit Skip and End early facts (2026-09-28). Since the 2026-09-29 revision,
migration 12 drops that table: no local stop is permanent any more.

## First implementation (superseded 2026-09-29)

The first implementation resumed an expired occurrence from its pinned
original start after an end-time extension. It passed plan review,
completed-change review, quality, and the iPhone and Tart matrix at `5eda62d`
(`build/verification/runs/schedule005-e2e/README.md`). Pull request review then
found a clock-rollback defect and an undo defect, which were corrected in
`44c0f9a` (`build/verification/runs/schedule005-review/README.md`). The same
review asked whether the kept original start was intended. The maintainer
replaced the rule instead, which removes pinned starts, resumed-pin bounds, and
permanent local stops altogether.

## Revision test audit

| Test | Assertion before | Fate |
| --- | --- | --- |
| `ScheduleOccurrencePinTest` (8 tests) | A pin keeps its original start through start, weekday, and remote edits; its end follows the plan. | Deleted. The edit contracts moved to `ScheduleEditRuleTest`. Overnight, fall-back cap, Skip/End/off, and the pause name and end stay covered by `ScheduleOccurrencesTest`. |
| `ScheduleExpiryExtensionTest` | An extension resumes from the original start; notice bits merge into the expiry. | Revised: a synchronized extension resumes from the observed end. Replay and rollback stay covered. The notice-merge part was removed, because every fresh run now announces. |
| `ScheduleMonitorTableTest` (resumed, stopped dates, running) | The table clamps a running start to the expiry, and terminal markers stop dates. | Revised: an expired date whose interval starts before its observed end is stopped, and the running entry carries the engine's start. |
| `ScheduleHostTest` expiry assertion | Natural expiry is recorded by key. | Revised to assert the observed end. |
| `SqlScheduleStoreTest` host records | Finished runs become terminal. | Revised: released runs keep only their latest observed end, and old ends are pruned. |
| `ScheduleOccurrencesTest` terminal case | Terminal markers stop an occurrence. | The terminal case was removed with the concept. |
| Six migration fixtures | They drop the terminal table from the fresh schema. | Those lines were removed, because the fresh schema no longer has it. |
| `testAPinnedOccurrenceKeepsRunningAfterItsPlanMoved` (Swift) | A pinned entry outlives a moved plan. | Revised: a listed entry runs on its stopped date only from its start. |
| `testARunningOccurrenceTheEditedPlanNoLongerEndsGetsATail` (Swift) | An edited running occurrence registers a tail. | Replaced: no tail is registered, and a leftover tail is stopped. |

## Revision regressions

Before the implementation, the new tests failed for their intended reasons:

- **`ScheduleEditRuleTest`:** five tests failed, covering the start moved
  later, the weekday removed, off and on, a later run after expiry, and the
  resume from the observed end. The rollback and Skip/End controls passed, as
  intended.
- **Host second-run test:** the old host kept the run.
- **Version-12 migration test:** failed in a temporary worktree at `2d18ae6`,
  because the old migration kept the terminal table.
- **Swift tail and app-announcement tests:** failed behaviourally once only
  the poster seam was added.

The logs are `revision-red.log`, `migration-red.log`, and `swift-red.log` under
`build/verification/runs/schedule005-review/`. All of these tests passed after
the implementation: 897 JVM tests and 172 Swift tests.

## Revision review

The independent plan review had no Critical findings and four Required
findings, R1-R4, all folded into the plan above. The independent
completed-change review found no Critical or Required code issue. Its two
Recommended items were fixed:

- an iPhone start is announced only after its start record is written;
- the product document now describes the app's start notice and per-run
  notices.

Its Optional wording and rollback-window notes were also applied. Its three
Required closeout items are this record, the log entry, and the E2E below.

## Revision real-device result

Revision `4c107de` ran on a disposable Tart primary clone and the dedicated
test iPhone.

**Tart:**

- **Start moved later.** A running plan's start was moved three minutes later.
  The pause stopped at once, with 0 pins, no expiry record, and example.com
  allowed. At the new start it ran again, blocked, and announced.
- **Off and on.** Turning the plan off inside its interval allowed access and
  released the pin. Turning it on blocked again, and the start was announced
  again.
- **Later run after expiry.** After a natural expiry at 17:59, the plan was
  moved to 18:02. The row showed "Next: Tue, 6:02 PM", and at 18:02 it blocked
  with a fresh announcement.
- **Skip, then a later move.** After Skip next for today, the move kept today
  skipped, and access stayed allowed at the new time.

**Test iPhone:**

- **Reset and restore.** Posato was reset, and Screen Time consent,
  example.com, and Calculator were restored.
- **Start moved later.** A running plan's start moved later lifted both
  shields. With Posato terminated, the new start shielded Calculator and
  Safari.
- **Off and on.** Turning the plan off and on inside its interval lifted and
  reapplied both shields. The app's "Scheduled pause started" banner appeared
  after the re-enable.
- **Later run after expiry, Posato closed.** Natural expiry lifted both
  shields. A same-day later interval showed "Next: Tue, 18:33". At 18:33 the
  extension shielded both, and the banner "Scheduled pause started / Phone
  check, until 18:53" appeared for this second run of the date.

Safari's blank first loading view needed one retry twice; both retries passed
without opening Posato.

**Evidence:** the scenarios and times are in
`build/verification/runs/schedule005-revision/`, and the individual runs are
the `s005v-*` directories. No host Posato installation was touched.

## Checks

The final revision passed:

- `./gradlew quality iosSwiftTest`;
- signed Mac package verification;
- the device build.

It introduced no wire-format change and no suppression.

## Cleanup

- **Tart:** both disposable VMs were destroyed, and no iCloud link was made.
- **iPhone:** the test schedule was deleted, and access was verified as
  restored. The phone keeps its one website (example.com) and one application
  (Calculator) with Screen Time consent, and has no schedules.
- **Earlier runs:** the synthetic schedules and the test workspace were removed
  on both devices.
