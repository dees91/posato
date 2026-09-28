# SCHEDULE-005 — Resume a naturally expired schedule after extension

- **Status:** Implemented and verified; separate fix authorized on 2026-09-28.
- **Review tier:** High-risk: persistent occurrence stopping and migration.
- **Dependencies:** Accepted schedule rules; reproduced report in PR #106.
- **Integration:** Separate fix PR based on PR #106; release unassigned.

## Outcome

When synchronization extends a naturally expired occurrence into the current
interval, the receiving device activates it as the editing device does.

## Boundaries and acceptance

Keep Skip and End early permanent, preserve the original start and 24-hour cap,
and prevent unchanged plans or clock rollback from reviving expired intervals.
Keep the wire format and device-local application choices unchanged. Persist
the distinction across relaunch. The maintainer accepted reevaluating legacy terminal rows under current plans
while preserving explicit Skip and End early facts (2026-09-28).

## Verification

The failing iPhone/Tart reproduction in PR #106 is the E2E regression baseline.
Repeat natural expiry, remote extension, synchronization, enforcement, relaunch,
and explicit-stop controls on the connected test iPhone and Tart.

Before implementation, add failing database/policy regression coverage for
expiry persistence, repeated materialization, clock rollback, and the original
start cap. Controlled clocks and stale writes cannot be reliably exercised
through real-device E2E. Use the existing SQLDelight test driver and domain
clock seam; add no test-only production seam. Run aggregate quality and obtain
independent plan and completed-change reviews.
