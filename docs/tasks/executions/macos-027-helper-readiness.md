# Execution: `MACOS-027`

- **Brief:** [Mac helper readiness that agrees with what the helper does](../specifications/macos-027-helper-readiness.md)
- **Status:** `active`: reproduced and fixed; waiting for the independent completed-change review
- **Review tier:** `standard`, decided by the coordinator under the maintainer's delegation of 2026-10-09 (see Decisions)
- **Implementer:** Claude
- **Reviewer:** pending
- **Branch:** `task/macos-027-helper-readiness`
- **Updated:** 2026-10-09

## Before the reproduction

Plan: read-only checks of the maintainer's install, then one work session
in Tart (1a under load first), then failing proof, fix, `AC-03`, `AC-04`.
The maintainer's 1.3.0 (28) passed the strict check in 0.07 s and showed
three `PosatoMacOSHelper` background records, all at `/Applications`
(`observed`, 2026-10-09). `user-confirmed`: the symptom cleared on the
unchanged install, so it is transient; which builds ran there is unknown
(`inferred` history: development copies until 2026-09-11, notarized
candidates and in-app updates until 2026-09-24, none after).

## Reproduction (`AC-01`)

Peer-line Tart clone; guest load (eight `yes`, `dd` writes, `find | cat`
over system folders) and a probe that activates Posato every ~11 s and reads
This Mac and Session, both in `build/verification/macos-027/`.

Loaded runs showed "Last Mac setup request did not finish" and "Setup
incomplete. Posato is not blocking websites or apps on this Mac" while the
helper was enabled (table below); idle runs read ready. The daemon, helper,
and readiness code of `main` `ae99a9d` equal 1.3.0; temporary helper logging
(never committed) named the failing call. `codesign` took 0.1 s idle and up
to 6.9 s under load.

## Cause (`AC-02`)

`observed` in the guest's unified log, recorded on the
[macOS enforcement](../../wiki/topics/macos-enforcement.md) page:

1. **Daemon idle exit races a new connection** (1.3.0, 19:32:35): the
   on-demand daemon took 4.2 s to start, armed its exit 1 s after start,
   and exited with `EXIT_SUCCESS` while the helper's connection was still
   in its code-signing check; the helper got
   `XPC_ERROR_CONNECTION_INTERRUPTED`, an unknown outcome, and ended. ADR
   0004 allows an exit only with no connection.
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
these paths; only reads failed. Whether that day's schedule did not start
stays `open` (`ScheduleStartGate` treats an unknown read as transient).

## Change

- `RequestCoordinator.swift`: the daemon exits only after 10 s with no
  connection, request, or disconnect, still only at `Idle` with no
  connection or lease.
- `MacHelperSetupUiState.kt` (`UnfinishedQuietReads`): a quiet read with an
  unknown outcome keeps a shown ready state while such reads last under five
  minutes; any stored answer ends the run, and a check or setup the person
  starts still shows its own answer. Three isolated tests in
  `MacHelperQuietReadRaceTest`, each failing first where it changes
  behavior, because E2E cannot place reads at the five-minute bound.
- `MacOsHelperSigningVerifier.kt`: each `codesign` command may take 30 s
  instead of 5 s; what is verified and every refusal are unchanged.

## Verification (`AC-03`, `AC-04`)

| Build | Loaded runs | False "Setup incomplete" |
| --- | --- | --- |
| 1.3.0 (28) published | 2 | 2 (45-50 s each) |
| `main` development build | 1 | 1 (cause 2) |
| Daemon fix only | 3 | 3 (cause 2, about 2 min into the load) |
| First grace version (age of the last ready read) | 1 after `purge` | 1: the app had been idle 5.5 min |
| Final | 6, two after `purge` | 0 |

- In the final runs no read reached the 120 s timeout (the longest daemon
  start was about 100 s), so the kept-ready path after a timed-out read is
  covered by `MacHelperQuietReadRaceTest` (the read 400 s after the last
  ready one reproduces the 1-after-`purge` failure), not end to end.
- `AC-03`: a manual pause with `example.com` blocked (`observe`: `paused`),
  This Mac "Background helper enabled", also after a login launch; a
  schedule started on its own at 21:16 with the window closed, `observe`
  `paused`, no administrator dialog.
- `AC-04`: with the helper moved out of the bundle, and with its
  `Info.plist` tampered, This Mac showed "Mac setup needs attention" and
  "The background helper could not be checked or enabled" at once; restored,
  ready again. The grace never holds `UNAVAILABLE`.
- After the E2E runs, Detekt moved the grace into `UnfinishedQuietReads`
  (no behavior change; tests rerun). `./gradlew quality` and 227
  `:macosHelper:swiftTest` tests passed. Early 12 GB guest loads filled the
  host disk; later loads were capped at 2 GB.

## Decisions

Under the maintainer's delegation of 2026-10-09:

- Tier `standard`, the coordinator's decision: no contract changes. The
  signature check keeps its meaning and only its per-command timeout rises
  from 5 to 30 s; the XPC peer requirement and the daemon registration are
  unchanged; the daemon's idle exit stays within ADR 0004 ("The daemon
  exits successfully only at `Idle` with no connection or lease"). The
  independent review is security-focused.
- Decided by the agent: unfinished quiet reads keep ready for at most five
  minutes, so a helper that never answers still surfaces; the bound counts
  from the first unfinished read, because the age of the last ready read
  failed after an idle app.
- Hypotheses 1b and 2 were not run: 1a reproduced and explains the
  transient symptom; the stale-record question stays `open`.

## Final

- **Status:** pending review
