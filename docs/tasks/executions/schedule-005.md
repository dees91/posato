# SCHEDULE-005 execution

- **Status:** investigation and regression design

## Diagnosis

The host writes the same permanent terminal key for natural expiry and other
stops. Materialization changes the plan, but the engine rejects its terminal
key. The iOS monitor receives that date as stopped too. Existing terminal rows
contain no reason, original start, or observed end.

## Plan under review

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
