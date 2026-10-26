# Execution: `MACOS-031`

- **Brief:** [A pause keeps its restrictions when the Mac is busy for a few seconds](../specifications/macos-031-helper-resilience.md)
- **Status:** `done`: fixed and verified in Tart; reviews resolved
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent agents (plan review, completed-change review)
- **Branch:** `macos-031-helper-resilience`
- **Updated:** 2026-10-26

## Cause

`observed` on the maintainer's Mac (1.5.1, 2026-10-25, unified log read
only) and in the code:

- Under load from code signing and Swift tests, the daemon answered one
  Status in 5.4 s. `LeaseRenewer` renews every 5 s with a 5 s deadline and
  ends the helper (`exit(EXIT_FAILURE)`) on the first failure, including a
  timeout, although the daemon's lease runs 15 s from the last renewal.
- The daemon saw its owner disconnect and restored the proxy. The idle
  checks scheduled 1 s after the other connections closed waited on the
  daemon's queue behind that slow restore and ran right after it: the daemon
  was `Idle` with no counted connection and exited, while a new helper's
  connection had not reached its listener yet. That helper got
  `XPC_ERROR_CONNECTION_INTERRUPTED` and ended too.
- Session then showed "Restrictions need attention" with the proxy off, and
  polling stopped until a press (the latter belongs to `MACOS-029`).

## Plan

1. `AC-01` first: `posato-control vm stall --process daemon --seconds N`
   (SIGSTOP, wait, SIGCONT with the guest administrator password, next to
   `vm kill`). A gated harness, `MacOsHelperDaemonStallHarnessTest`, applies
   with the standing grant, waits while the host stalls the daemon for 8 s,
   then asserts the same helper still answers Status `Applied`, the proxy is
   still on, and restrictions clear. Shown failing on the released 1.5.1
   helper.
2. `AC-02` first: a `DaemonIdleExitTests` case where an idle check is due
   while a restore runs and a connection arrives after the short delay; it
   must not exit. E2E cannot hit that window reliably, so the race is tested
   in isolation on `RequestCoordinator` with its existing seams.
3. Helper: a timed-out renewal is retried on the next period; the helper
   gives up only when no renewal succeeded for the lease length (15 s), or
   at once for a rejected, malformed, or unhealthy answer or a lost
   connection. ADR 0004 amended with the accepted wording.
4. Daemon: an idle exit waits for a quiet period measured from the latest
   event; after a restore that period is the 10 s startup grace, so a helper
   that reconnects after losing its owner is not cut off; a plain
   disconnect keeps about 1 s for an update.
5. `AC-03` in Tart through `posato-control`: a pause with the daemon stalled
   for 8 s keeps "Restrictions active"; Session with a daemon stalled past
   the lease shows the notice, not "Restrictions active".
6. `./gradlew quality`, completed-change review, record, wiki.

## Plan decisions

- `user-confirmed` (2026-10-25): ADR 0004 amendment, "A rejected, malformed,
  or failed renewal invalidates the ownership connection and terminates the
  helper nonzero. A renewal that only times out is retried on the next
  period; the helper gives up the same way only when no renewal has
  succeeded for the daemon's lease length (15 s). The daemon's lease is
  unchanged, so restrictions still end within the same bound when the
  helper is gone."

## High-risk plan review

- **Verdict:** changes-required, resolved before implementation; no
  further ADR change.
- **Required findings and resolutions:**
  - (R1) One 8 s stall breaks a 5 s renewal period only when a renewal is
    sent in its first 3 s. The host now stalls the daemon twice for 8 s,
    4.5 s apart, which covers every timer phase; with the fix the daemon's
    lease check never sees more than 13 s without a renewal.
  - (R2) The daemon fix is a re-armed check: an idle check that runs well
    after it fell due, because the queue was busy, waits its delay again
    instead of exiting, which is what the busy-queue test exercises.
  - (R3) A deadline that expires becomes its own `PipeFailure.timedOut`;
    an interrupted or invalid connection keeps ending the helper at once.
  - (R4) The 10 s wait applies only to restores the daemon starts itself
    (lost owner, lease expiry, power transition). A Restore the helper
    sends when a pause ends or Posato quits keeps the short exit, so an
    update never reaches the old daemon.
  - (R5) Left risk, recorded: a plain disconnect still allows an exit after
    about 1 s, and a launchd start can take minutes on a saturated disk; a
    Status that meets it fails as before. `MACOS-029` makes the
    application apply again by itself after such a loss.
- **Recommended, taken:** the give-up window counts from the send time of
  the last successful renewal, from one shared lease constant; one renewal
  per timer tick, never a loop under the gate; the harness also changes the
  domain list after the stalls and checks that the daemon runs again; the
  cleanup path cancels the renewer explicitly. Session shows the existing
  notice once the helper ends; while the daemon stays frozen past its
  lease nothing can restore, which is a known limit.

## Result

- As planned. The renewal moved into `LeaseRenewal.swift` to keep
  `HelperTransport.swift` within SwiftLint's 400 lines; the lease length is
  `WireLimits.leaseMilliseconds`, shared by the daemon and the helper.
- A late idle check waits again once, when it ran more than a tenth of its
  delay plus 250 ms late; timer slack alone can therefore delay an exit by at
  most one more period.
- Not taken (Optional R11): `main.swift` is at SwiftLint's 400 lines, so the
  cleanup path still drops the renewer without `cancelAndWait()`; a renewal
  that times out there no longer ends anything that matters.
- Known effects: while the daemon is stalled, each renewal holds the gate for
  up to 5 s, so a Clear or Restore can wait that long before it runs, for up
  to 15 s in total. A stall longer than the lease still ends the pause with
  "Restrictions need attention" and the proxy off.

## Completed-change review

- **Verdict:** changes-required, resolved.
- **Required finding:** (R1) the late-check re-arm fired on ordinary timer
  slack (libdispatch may fire about 10 % late; a `Background` daemon
  coalesces more), so an idle daemon could keep running and an update could
  reach the old one. Fixed with the slack-relative threshold and a single
  re-arm, and a test that an idle daemon still exits after a late check.
- **Advisory findings taken:** the record closeout, the known 5 s gate
  effect above, and a clearer harness message when no daemon runs.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `AC-01` harness on the package before the fix (`ffc0c1a1`, helper and daemon as on `main`), two 8 s stalls | fail as expected after the first stall: the second `vm stall` found no daemon, Status read `phase=Idle`, `proxy=false` | `build/verification/macos-031/before/harness.json` |
| `AC-01` harness on the final package | pass: ten Status `Applied` with the proxy on past the lease, change, clear; the daemon exited afterwards | `build/verification/macos-031/after/harness-final.json` |
| `AC-02` `DaemonIdleExitTests` | the two race cases failed on `main`, then all seven pass; the two-connection lost-owner case (both close orders) failed before the shared restore wait, then all nine pass | `swift test --filter DaemonIdleExitTests` |
| `AC-03` application, pause with two 8 s daemon stalls | **Restrictions active**, `observe --expect blocked` passes | `build/verification/runs/20261026-070031-1d52`, `20261026-070035-8c88` |
| Shared restore wait, application: helper killed during a pause, then Retry, then an 8 s daemon stall | **Restrictions active**, `observe --expect blocked` passes after the Retry and after the stall | `build/verification/runs/20261026-072824-7a57`, `20261026-072841-7361`, `20261026-072844-de4e` |
| `AC-03` application, 25 s daemon stall | "Restrictions need attention", proxy off | `build/verification/runs/20261026-070142-d3cb` |
| `./gradlew quality` on the final tip | pass | `build/verification/macos-031/quality.log` |
