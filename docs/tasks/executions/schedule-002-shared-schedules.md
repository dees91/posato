# Execution: `SCHEDULE-002`

- **Brief:** [Shared recurring schedules](../specifications/schedule-002-shared-schedules.md)
- **Status:** `active` (slice 2 ready for review; slices 1 and 3-6 open)
- **Review tier:** `high-risk`
- **Implementer:** Claude, under the maintainer's delegated night mandate (2026-09-26)
- **Reviewer:** independent agent (completed change)
- **Branch:** `feature/schedule-002-engine` (slice 2), stacked on `docs/schedule-001-decision`
- **Updated:** 2026-09-27

## Plan

- **Slice 2, the occurrence engine** (`shared/.../feature/schedules/domain`).
  - `ScheduleDate`: a zone-free calendar date (epoch days, weekday, day arithmetic).
  - `SchedulePlan.problem()`: name bytes, weekdays, minute range, equal times, and the 15-minute circular floor.
  - `ScheduleZone`: local wall time to instant and back. A skipped wall time resolves to the change itself; a repeated one to its first instance.
  - `ScheduleOccurrences`: `active`, `pause` (earliest-started name, latest end), and `next` (first later start that is not skipped or ended, up to 400 days ahead).
  - `JavaScheduleZone` (jvmMain) reads the system zone at every call.
- **Why this slice first.** It is the only slice independent of the amendments PR #93 proposes; slices 1, 4 and 6 wait for the maintainer to accept them.

## Result

- **Delivered:** slice 2 as planned.
- **Tests.** 13 engine tests were written first and failed against `TODO()` stubs. One vector was wrong (a fall-back case where the first-instance rule keeps the occurrence under 24 hours); it was corrected to a case where the 24-hour cap binds before the implementation was written. `JavaScheduleZoneTest` checks Europe/Warsaw's 2026 changes against the same rules.
- **Deviation (needs the maintainer's acceptance).** No separate high-risk plan review ran for slice 2. Its plan is the slice plan in the rules document, which the `SCHEDULE-001` review covered; the completed-change review below checked the engine against the rules.

## Completed-change review

- **Verdict:** `changes-required`, then resolved.
- **R1** (the JVM caches its default zone at first use, so a time-zone change while Posato runs was ignored): `currentSystemZone()` reads the `/etc/localtime` link on each call and falls back to the JVM default; a test swaps the link between Warsaw and New York.
- **C1** (the pause name depended on list order for occurrences starting together): ties break by schedule identifier (test with both orders).
- **C2** (skipped plan review): recorded as a deviation above.
- **O1** (a plan wholly inside a spring-forward gap produced a zero-length next run): such an occurrence does not exist that day (test).

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `:shared:jvmTest` schedules suites | pass | 16 tests |
| `:shared:compileTestKotlinIosSimulatorArm64` | pass | common code only |
| Mutation checks | pass | 11 mutations (lookback, midnight, 24-hour cap, facts, latest end, next after a running occurrence, 15-minute floor, enabled, tie-break, empty gap occurrence, zone link) each fail a test |
| `quality` | pass | after the last correction |

## Blockers and accepted risks

- **Slices 1, 4 and 6** wait for the maintainer to accept the ADR 0004, 0006 and 0009 amendments in PR #93.
- **Slice 3** (storage and UI) needs slice 1's operations to persist and share plans.
- **Slice 5** needs the test iPhone unlocked for XCTest (`DEVICE_AUTOMATION_LOCKED` on 2026-09-27).

## Final

- **Status:** `active`
- **Outcome:** slice 2 delivered; the row stays open for slices 1 and 3-6
