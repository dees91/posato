# SCHEDULE-005 — Resume a naturally expired schedule after extension

- **Status:** Implemented and verified; rule revised on 2026-09-29. The separate fix was authorized on 2026-09-28.
- **Review tier:** High-risk: persistent occurrence stopping and migration.
- **Dependencies:** Accepted schedule rules; reproduced report in PR #106.
- **Integration:** Separate fix PR based on PR #106; release unassigned.

## Outcome

When synchronization extends a naturally expired occurrence into the current
interval, the receiving device activates it as the editing device does.

Revised on 2026-09-29 (`user-confirmed`): a device evaluates a schedule
against its current plan at every edit. A running occurrence stops when the
edited plan's days, start, end, or on-off state no longer cover the current
time. That stop is not permanent, and the plan runs again at its new
interval, even later the same day. An occurrence that did not run starts at
once when an edit makes its interval cover the current time.

## Boundaries and acceptance

- Skip and End early remain final for their date, including after an edit
  that moves the interval later the same day.
- Turning a schedule off is an ordinary edit. Turning it back on within the
  current interval runs the occurrence again.
- Natural expiry records the observed end on this device. An occurrence of
  that date runs again only when its current interval covers the current
  time and the time is at or after that end. Unchanged plans, repeated sync,
  and clock rollback therefore do not revive an expired interval.
- An occurrence's start and 24-hour cap follow the current plan; no original
  start is retained.
- Every fresh start announces the pause, including a second run on the same
  date. An edit that stops a running pause posts no end notice.
- The rollback bound covers runs this device observed. A refused plan behaves
  like a turned-off one.
- Keep the wire format and device-local application choices unchanged.
  Persist the distinction across relaunch. The maintainer accepted
  reevaluating legacy terminal rows under current plans while preserving
  explicit Skip and End early facts (2026-09-28).

## Verification

The failing iPhone/Tart reproduction in PR #106 is the E2E regression baseline.
Repeat natural expiry, remote extension, synchronization, enforcement, relaunch,
and explicit-stop controls on the connected test iPhone and Tart.
For the revision, also show on both targets that an edit moving a running
occurrence later stops it and starts it at the new time, with Posato closed
on the iPhone, and that turning a plan off and on within its interval
resumes it.

Before implementation, add failing database/policy regression coverage for
expiry persistence, repeated materialization, clock rollback, and the 24-hour
cap. Revision regressions cover edits that stop a running occurrence
and move it later, turning a plan off and on, a later same-day run after
expiry, Skip or End early before a later edit, and repeated start notices.
Controlled clocks and stale writes cannot be reliably exercised
through real-device E2E. Use the existing SQLDelight test driver and domain
clock seam; add no test-only production seam. Run aggregate quality and obtain
independent plan and completed-change reviews.
