# `SCHEDULE-006`: Keep the iPhone schedule table within its bound

- **Review tier:** Standard
- **Tier reason:** A minimal correction to the iPhone schedule table's size
  and error path; no new format kind, migration, or privilege.
- **Dependencies:** `SCHEDULE-004` (merged in #119 and #123 to #125); release
  1.3, wave 4. `DOCS-004` and `RELEASE-005` wait for this row.
- **Integration group:** PR-SCHEDULE-TABLE-BOUND
- **Authority:** [Release roadmap](../release-roadmap.md) revision 16, row
  `SCHEDULE-006`; maintainer named the row on 2026-10-02 ("minimal fix").

## Outcome

Every supported pause set configuration fits the iPhone's version-2 schedule
table, and a table that cannot be written is reported instead of leaving the
previous table in force without notice.

## Boundaries

- Review finding on #125 (advisory P2): with 1,024 unique websites of the
  longest valid length shared by ten sets, the encoded table is about 269 KB
  before metadata, above `ScheduleMonitorFileStore`'s 256 KiB limit
  (`maximumTableBytes` in `ScheduleMonitorShared.swift`). After a failed
  write, `publish()` returns, and a later `applySchedule()` still reads the
  previous table without saying the requested configuration failed.
- Make the bound hold for the complete supported representation, either by a
  more compact encoding of the website list and per-set indexes or by a
  limit sized for it, whichever is the smaller change.
- Report a failed publication to the app so that Session and Schedules show
  that the schedule could not be applied on this iPhone.
- Non-goals: new user-visible limits, a format-1 or synchronization change,
  and the Mac.

## Acceptance

- `AC-01` — A failing-first Swift test writes the review's worst case (1,024
  hosts of 221 characters shared by ten sets, with app tokens) and fails on
  `main`; it passes with the change.
- `AC-02` — A forced write failure reaches the app as an error instead of a
  silent return.
- `AC-03` — On the test iPhone, a schedule with a pause set still starts and
  ends with Posato closed after the change.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- `iosSwiftTest` for `AC-01` and `AC-02`; one schedule run on the test iPhone
  (`-t device`) for `AC-03`.

## Decisions or blockers

- None.
