# Execution: `MACOS-027`

- **Brief:** [Mac helper readiness that agrees with what the helper does](../specifications/macos-027-helper-readiness.md)
- **Status:** `done`: reproduced and fixed; review approved
- **Review tier:** `standard`, decided by the coordinator under the maintainer's delegation of 2026-10-09 (see Decisions)
- **Implementer:** Claude
- **Reviewer:** independent security-focused completed-change review and focused re-review
- **Branch:** `task/macos-027-helper-readiness`
- **Updated:** 2026-10-09

## Before the reproduction

The maintainer's 1.3.0 (28) passed the strict check in 0.07 s and showed
three `PosatoMacOSHelper` background records, all at `/Applications`
(`observed`). `user-confirmed`: the symptom cleared on the unchanged
install (transient); which builds ran there is unknown. Hypothesis 1a under
load ran first in Tart, within one work session.

## Reproduction (`AC-01`)

Peer-line Tart clone; guest load (eight `yes`, `dd` writes, `find | cat`
over system folders) and a probe that activates Posato every ~11 s and reads
This Mac and Session, both in `build/verification/macos-027/`.

Loaded runs showed "Last Mac setup request did not finish" and "Setup
incomplete. Posato is not blocking websites or apps on this Mac" while the
helper was enabled (table below); idle runs read ready. `main` `ae99a9d`
equals 1.3.0 in this code; temporary helper logging (never committed) named
the failing call.

## Cause (`AC-02`)

`observed` in the guest's unified log, recorded on the
[macOS enforcement](../../wiki/topics/macos-enforcement.md) page:

1. **Daemon idle exit races a new connection** (1.3.0): the daemon took
   4.2 s to start, armed its exit 1 s after start, and exited while the
   helper's connection was in its code-signing check; the helper got
   `XPC_ERROR_CONNECTION_INTERRUPTED`, an unknown outcome.
2. **Slow daemon start under saturated disk I/O**: launchd started the
   daemon about two minutes after the helper connected; the helper's Status
   waited its full 120 s ("daemon send timed out after 119985 ms"), failed
   as an unknown outcome, and ended. The next quiet read stored
   `UNCERTAIN`, which Session shows as incomplete setup.
3. A strict deep `codesign` check took 6.9 s once, over the readiness
   check's 5 s limit per command, which maps to `UNAVAILABLE` ("could not
   be checked or enabled", the reported copy). Not seen in the interface:
   0 of 3 cold-cache storm cycles (`probe2-control.log`).

`inferred`: the maintainer's report during Tart and Gradle work matches
these paths. Whether that day's schedule did not start stays `open`.

## Change

- Daemon (`RequestCoordinator.swift`, `main.swift`): a connection is
  counted synchronously at the start of the listener's accept and dropped
  when invalidated, including by a failed code-signing requirement; the
  idle exit waits 10 s only before the first connection after start, and
  about 1 s after a disconnect, as before. `DaemonIdleExitTests` (3 Swift
  tests; the late first connection and the busy-queue accept fail first).
- `UnfinishedQuietReads` in `MacHelperSetupUiState.kt`: an unknown quiet
  read keeps a shown ready state while such reads last under five minutes
  and is retried every 30 s; any stored answer restarts the bound; checks
  the person starts and every other answer show at once. The clock is JVM
  monotonic time, which does not count sleep. Five isolated tests in
  `MacHelperQuietReadRaceTest`, each failing first (the reset by mutation).
- `MacOsHelperSigningVerifier.kt`: 30 s per `codesign` command and 45 s for
  the whole check; the 6.9 s measurement, not a UI reproduction, is why.

## Verification (`AC-03`, `AC-04`)

| Build | Loaded runs | False "Setup incomplete" |
| --- | --- | --- |
| 1.3.0 (28) published | 2 | 2 (45-50 s each) |
| `main` development build | 1 | 1 (cause 2) |
| Daemon fix only | 3 | 3 (cause 2, about 2 min into the load) |
| First grace version (age of the last ready read) | 1 after `purge` | 1: the app had been idle 5.5 min |
| `355dae6` (10 s quiet exit) | 6, two after `purge` | 0 |
| Final `c8d9d5b` (code of `fa5f11f`) | 1 after `purge`, host load 96 | 0 |

- On `c8d9d5b`, one session under the maintainer's revised order
  (2026-10-09): "ogranicz do minimum weryfikacje vm i iphone - raz przed PR
  gdy to ma sens": the loaded probe above, `AC-04` with a tampered helper
  `Info.plist` (unavailable at once), and a manual pause (`observe`
  `paused`). No read reached the 120 s timeout, so keeping ready after it
  is covered only by `MacHelperQuietReadRaceTest`.
- `AC-03` and `AC-04` on `355dae6`: a manual pause with `example.com` blocked (`observe`: `paused`),
  This Mac "Background helper enabled", also after a login launch; a
  schedule started on its own at 21:16 with the window closed, `observe`
  `paused`, no administrator dialog.
- `AC-04`: with the helper moved out of the bundle, and with its
  `Info.plist` tampered, This Mac showed "Mac setup needs attention" and
  "The background helper could not be checked or enabled" at once; restored,
  ready again. The grace never holds `UNAVAILABLE`.
- `quality` and 230 Swift tests passed on the final tip. Early 12 GB guest
  loads filled the host disk; later ones were capped at 2 GB.

## Decisions

Under the maintainer's delegation of 2026-10-09, by the coordinator: tier
`standard` (no contract changes; the signature check keeps its meaning with
longer limits; the XPC peer requirement, the registration, and ADR 0004's
exit rule hold); after the review of `355dae6`, the accept-time count, the
startup-only 10 s grace, the 30 s retry, the 45 s check deadline, and the
AC-01 amendment. By the agent: the five-minute bound counts from the first
unfinished read, because counting from the last ready read failed after an
idle app. Hypotheses 1b and 2 were not run, because 1a reproduced and
explains the transient symptom; the stale-record question stays `open`.

## Review

- `355dae6`: `changes-required` (Required: evidence predated the refactor;
  four items taken), addressed in `fa5f11f`; reduced evidence on `c8d9d5b`.
- `7879c1c`: approved. Taken: the count is read last in the idle check and
  the daemon tests no longer depend on timing (mutations still fail them);
  Swift tests cover this reorder, so no further VM run.

## Final

- **Status:** `done`; PR ready for review, handed to babysit-pr.
