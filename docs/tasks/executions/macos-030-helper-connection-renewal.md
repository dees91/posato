# Execution: `MACOS-030`

- **Brief:** [The Mac keeps talking to its helper for as long as Posato runs](../specifications/macos-030-helper-connection-renewal.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent agents (plan review, completed-change review)
- **Branch:** `macos-030-update-admission`
- **Updated:** 2026-10-23

## Cause

`observed` in Tart on 1.5.0 (26.6.2 guest), confirming the reading of the
code:

- `MacOsHelperClient` checks `nextSequence <= 256` before its `try`, so the
  257th request throws without sending, the helper process stays, and every
  later request fails the same way until Posato relaunches. The helper lives
  as long as the application, so pauses, schedule minutes, and This Mac
  checks add up over days.
- A fresh helper started at 20:03:14; the pause polled Status every 15 s and
  showed "Restrictions need attention" at 21:07:51, 64 minutes later
  (256 x 15 s). **Retry** failed with the same helper process.
- At the pause's end the proxy stayed enabled for more than 3 minutes with
  "No session active" and "Restrictions may still apply". Ending only the
  helper process restored the proxy at once (the daemon saw its owning
  connection drop), and **Retry** on the new helper succeeded.
- `inferred` from the code: during a pause the helper forwards each Status
  over the daemon connection that owns the applied mutation, which has the
  same 256 bound and is never rotated, so it would fail next.
- The update refusal: `SqlUpdateMaintenanceStore.close` refuses while any
  schedule pin started within 24 hours, even when the scheduled pause holds
  nothing because the helper failed.

## Plan

1. Regression first (`AC-01`): an environment-gated JUnit harness in
   `desktopApp` tests, packed into one jar with a host-signed parent process
   and run in the guest with `JUnitCore`. With the standing grant on, it
   applies a domain, sends 600 Status requests, re-applies, and clears,
   asserting each answer and the proxy state. Shown failing against the
   installed 1.5.0 helper.
2. Application side: before a request would take sequence 256 (the last
   slot stays for a Cancel), `MacOsHelperClient` sends a new Hello on the
   same pipe and continues on the new connection. All four request paths
   share it; a failed renewal ends the process as a broken pipe does today.
3. Helper side: a Hello on an established pipe with the same session
   identifier and capabilities opens a new connection (new identifier,
   sequence restarts at 1) and keeps the process state. `DaemonConnection`
   sends every message with the connection and session identifiers its
   first message carried, so a parent renewal never changes what the daemon
   sees on the owning connection. During a pause, Status and grant requests
   travel on a separate connection per request, as when no pause runs, so
   the owning connection keeps its operations for apply and restore.
4. Update admission: a running schedule refuses only while
   `GatedEnforcementPort` holds applied restrictions; the refusal says
   "pause" instead of "session".
5. Verify `AC-01` to `AC-03` in Tart, run `quality`, record, review.

## High-risk plan review

- **Verdict:** changes-required, resolved before implementation; no ADR
  change needed (a connection is renewed, never extended).
- **Required findings:** (R1) no failure path may invalidate the owning
  connection during a pause, which a failed Status resubmission did; (R2) a
  schedule's clear-then-apply could let an update close between the two;
  (R3) the held flag must turn off on a cleared status too; (R4) a failed
  renewal must not become an unknown outcome; (R5) the before run must use
  the client from `main`.
- **Resolution:** Status, grant operations, and Enable use a connection of
  their own during a pause; `EnforcementPort.replace` runs both steps under
  one permit and a closed gate clears nothing; the flag follows `APPLIED`
  and `CLEARED` from apply, clear, and status; renewal happens before the
  message is built and a failure ends the helper without a Cancel; the
  before jar was built before any client change.

## Result

- Pending.

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Tart 1.5.0, 120-minute pause, helper started 17:55 | fail as reported | `build/verification/runs/20261023-194254-cbfe`, `20261023-200254-5a1f` |
| Tart 1.5.0, fresh helper 20:03:14, pause from 20:03 | fail at 21:07:51 | `build/verification/runs/20261023-210752-41ea` |
