# `SYNC-020`: Publish a session change reliably from the device that made it

- **Review tier:** Standard
- **Tier reason:** A reliability repair inside the accepted best-effort
  synchronization; it changes no wire format, key handling, or trust boundary.
- **Dependencies:** None (release 1.3, wave 1)
- **Integration group:** PR-SESSION-PUBLISH
- **Authority:** [Release roadmap](../release-roadmap.md) revision 13, row
  `SYNC-020`; maintainer named the row on 2026-09-29.

## Outcome

A session start or early end made on one device reaches iCloud without a
manual **Sync now** on that device, so the peer adopts it at its own next
sync.

## Boundaries

- First reproduce, without the maintainer, the `QUALITY-010` observation
  (`ac05-repro`): an iPhone-started session reached CloudKit only after
  **Sync now** on the iPhone. The fix follows the reproduced cause; the
  interrupted-upload cause is `inferred`.
- Retry an interrupted or failed publication automatically with bounded
  backoff, and ask iOS for background time to finish it when Posato leaves
  the foreground. The same retry applies on the Mac.
- A publication that is still pending survives relaunch and is not sent
  twice as two different changes (ADR 0006 convergence).
- Non-goals: remote push or any server that wakes the peer; a shorter pull
  interval on the peer; changes to the format-1 operation vocabulary.
- Accepted authorities: ADR 0006, ADR 0007, the best-effort sync limit in the
  availability page, and `PRIVACY.md` (no new data leaves the device).

## Acceptance

- `AC-01` — The reproduction fails on `main`: a start made on the test iPhone
  and followed by leaving Posato is absent from CloudKit until **Sync now**.
- `AC-02` — With the change, the same start reaches iCloud without **Sync
  now**, and the Tart peer adopts and enforces it at its next own sync.
- `AC-03` — An early end made on the iPhone and a start made on the Mac
  behave the same way.
- `AC-04` — A publication interrupted by loss of network or by termination
  is retried after relaunch or reconnection and converges to one session.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Driver runs on the test iPhone (`-t device`) and a Tart peer (`--vm peer`)
  on the test Apple Account: start, leave the application, then observe the
  peer's next sync without **Sync now**, before and after the change.
- An interruption run: network loss or termination during publication,
  then recovery. The VM network toggle is still `open` in
  `unattended-verification`; extend `posato-control` if the run needs it.

## Decisions or blockers

- None known. If the reproduction shows a cause other than an interrupted
  upload, record it and ask before widening the scope.
