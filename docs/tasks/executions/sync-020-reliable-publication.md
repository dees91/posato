# Execution: `SYNC-020`

- **Brief:** [`sync-020-reliable-publication.md`](../specifications/sync-020-reliable-publication.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Claude Code
- **Reviewer:** independent Claude Code agent
- **Branch:** `feat/sync-020-reliable-publication`
- **Updated:** 2026-09-29

## Plan

1. Reproduce `AC-01` on the brief's base (`e2d5943` code) with the test
   iPhone and a Tart peer, then find the cause before changing code.
2. Retry a retryable exchange automatically with bounded backoff on both
   platforms, and hold iOS background time while a pass or a retry runs.
3. Add a guest network toggle to `posato-control` for the offline run.
4. Verify `AC-02` to `AC-04` unattended, then review and close.

## Diagnosis

- `observed`: an iPhone start followed by leaving Posato stayed absent from
  the peer for 4.5 minutes of peer exchanges; **Sync now** on the iPhone
  delivered it at once.
- `observed` with temporary console diagnostics (not committed): the pass
  that follows a session start or early end fetches the zone about three
  seconds later. During that fetch the system posts `CKAccountChanged`. The
  mailbox account guard therefore reports an unknown outcome, although the
  account status and binding are unchanged. The pass ends retryable, and
  nothing retried it. The same happens with Posato kept in the foreground,
  so the cause is not an interrupted upload.
- `inferred`: Posato's own Screen Time restriction change at start and end
  triggers the notification. A launch pass without a session change is not
  affected.
- `observed`: a Mac start on the base build published at once. The Mac
  shares the retry because its own failures had no automatic route either.
- The maintainer chose to keep the account guard and add the retry
  (`user-confirmed`, 2026-09-29). Relaxing the guard would change the ADR 0007
  account boundary.

## Result

- `ExchangeLoop`, confined to the `AppleSync` worker, waits for the next
  opportunity with a timeout after a retryable pass: 5 s, 15 s, 1 min,
  5 min, and 15 min, then waits without one. Any ordinary opportunity (local
  change, foreground, **Sync now**, or the Mac's periodic exchange) runs a
  pass and starts a new series; another outcome ends it. Publication still
  reuses the same immutable bundle (ADR 0006).
- `SyncBackgroundTime` holds a named iOS background task while a pass runs
  or waits up to 15 s for a retry. Longer waits take no hold: iOS grants a
  backgrounded app about 30 s in total (`source-claim`), so a hold could
  not reach them (`inferred`; no device run covers this limit). The expiration handler releases
  the hold. The Mac uses no hold.
- `posato-control vm network --line <line> --state off|on` disables or
  enables every guest network service through the guest administrator
  password; the host is never touched.
- Deviation: the brief's inferred interrupted-upload cause was replaced by
  the observed account-notification cause; the scope did not change.

## Completed-change review

- **Verdict:** changes-required, then resolved
- **Critical or Required findings:** a request during a retry wait abandoned
  the pending retry job, which later forced extra passes and background
  holds; ktlint import order in two files.
- **Resolution:** the retry moved into `ExchangeLoop`, a timed wait inside
  the worker, with a regression test that failed first (3 passes instead of
  2); imports sorted. The same change settles the advisory findings on
  reading the outcome outside the lock and on channel conflation, and
  `vm network` now fails unless every service reaches the requested state.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| New `AppleSyncSessionConvergenceTest` retry and bound cases on the base | fail as expected | peer `Inactive`; one save attempt |
| Review regression: request during a retry wait, before the correction | fail as expected | 3 save attempts instead of 2 |
| `:shared:jvmTest --tests '*AppleSync*'` after the correction | pass | 85 tests |
| `./gradlew quality` after the last source change | pass | rerun after the 2026-09-30 hold limit; one earlier run hit the unrelated timing test `MaintenanceCompanionTransportTest`, which passed three isolated reruns and the full rerun |
| `AC-01`: iPhone start, leave, peer exchanges (base build) | reproduced | runs `20260929-201358-af96`, `-201605-c845`, `-201839-b7c5` |
| Diagnosis: start with Posato in the foreground | account guard fired | console logs in runs `20260929-203300-ab88`, `-203557-a6e7` |
| `AC-02`: iPhone start, leave, peer's own exchange (menu open) | pass | `20260929-213616-654e`; peer adopted, `observe` paused `example.com` (`-214022-8663`) |
| `AC-03`: iPhone early end, leave, peer's own exchange | pass | `20260929-214032-903d`; peer ended early, website loaded (`-214227-3a18`) |
| `AC-03`: Mac start, iPhone's own exchange | pass | `20260929-214942-2f01`; iPhone blocked Calculator and the website (`-215237-69e3`) |
| `AC-04`: Mac early end offline, reconnect without **Sync now** | pass | intent kept offline, published about 20 s after `vm network --state on`; iPhone ended and released both |
| `AC-04`: iPhone terminated right after start, relaunch | pass | `20260929-215930-2075`; peer gained one registration and one start, one active session |
| `AC-04` after an outage longer than the retry series | not run | outside the narrowed criterion; delivery then follows the next ordinary opportunity |
| After the correction: `AC-02` and the `AC-04` Mac offline end | pass | `20260929-222212-cd7f`, `-222407-c9cd`; end published 20 s after reconnect, iPhone released both (`-222607-b254` retry after a Safari timeout) |

## Blockers and accepted risks

- `AC-04` narrowed (`user-confirmed`, 2026-09-30): reconnection is not an
  opportunity. After an outage longer than the retry series (about
  21 minutes), the next ordinary opportunity publishes: on an untouched Mac
  its periodic exchange, up to 30 minutes after reconnecting; on an iPhone
  its next foreground or local change. Only a Mac reconnection inside the
  series was verified.
- In the background, iOS may suspend Posato before even the 5 s or 15 s
  retry fires; the next foreground then starts a new series.

## Final

- **Status:** `done`
- **Outcome:** `AC-01` reproduced; `AC-02` to `AC-04` met unattended on the
  test iPhone and a Tart peer.
