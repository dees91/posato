# Execution: `MACOS-030`

- **Brief:** [The Mac keeps talking to its helper for as long as Posato runs](../specifications/macos-030-helper-connection-renewal.md)
- **Status:** `done`: fixed and verified in Tart; reviews resolved
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent agents (plan review, completed-change review)
- **Branch:** `macos-030-update-admission`
- **Updated:** 2026-10-24

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

- As planned. The pipe renewal lives in `ParentConnection.swift` to keep
  `main.swift` within SwiftLint's 400 lines. The domain harness now reads
  the shared launcher source; the application harness keeps its own variant.
- Deviation (`agent-delegated`): `AC-03` is proven by the store and
  admission tests instead of a candidate-feed update, as the brief records.
- Already installed copies keep the old client. A Mac that shows the
  symptom recovers when Posato is quit and opened again, which also lets the
  update through; the next release notes should say so.

## Completed-change review

- **Verdict:** approved after one correction.
- **Required findings:** five test lines over the 150-character limit,
  wrapped.
- **Advisory findings taken:** the skill's failing-index sentence, the
  shared launcher source, and two stale comments.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Tart 1.5.0, 120-minute pause, helper started 17:55 | fail as reported | `build/verification/runs/20261023-194254-cbfe`, `20261023-200254-5a1f` |
| Tart 1.5.0, fresh helper 20:03:14, pause from 20:03 | fail at 21:07:51 | `build/verification/runs/20261023-210752-41ea` |
| `AC-01` harness, client from `main` 3ce85a99 (harness without grant reads), 1.5.0 helper | fail as expected: `status 252 threw IllegalStateException`, the 257th operation | `build/verification/macos-030/before-1.5.0/harness.txt` |
| `AC-01` harness at 669525fb, development package | pass: 600 and 300 Status, 60 grant reads, two clears, proxy cleared | `build/verification/macos-030/after-fix/harness-final.txt` |
| `AC-02` 120-minute pause, development package | pass: one helper process, no attention notice, `observe` paused at 78 minutes, proxy off and example.com loaded after the end | `build/verification/macos-030/ac02-session.log`, `runs/20261024-010024-5b0d`, `runs/20261024-015344-f93c` |
| Final build: start, `observe`, end early, `observe` | pass | `build/verification/runs/20261024-015601-8b1e` |
| `AC-03` `SqlUpdateMaintenanceStoreTest`, `MaintenanceAdmissionTest` | three new tests failed first, then pass | `:shared:jvmTest` |
| `./gradlew quality` | pass | `build/verification/macos-030/quality.log` |
