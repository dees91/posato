# Execution: `SCHEDULE-002` slice 4

- **Brief:** [Schedules start on their own on a Mac](../specifications/schedule-002-slice-4-mac-host.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude, under the maintainer's delegated goal (2026-09-27)
- **Reviewer:** independent agents (plan and completed change)
- **Branch:** `feature/schedule-002-mac-host`, stacked on #100
- **Updated:** 2026-09-27

## Plan

1. **Engine pins** (`ScheduleOccurrences`). `active` and `pause` take the pins. A pinned key starts at the pin's start; it ends at the plan's current end time on the key's date (the next day when that end is at or before the pinned start's time), capped at 24 hours; it runs while the plan exists, is enabled, and no fact stops it. Weekday and start-time edits are ignored for it. `OccurrencePin` moves to the domain.
2. **Store** (no migration). `pin(pins)` inserts missing pins without resetting their notice bits; `markNotices(key, bits)`; `finish(keys)` writes terminal markers and drops pins; `end(keys, authorDate, workspaceId)` writes the end fact, its intent, the terminal marker and drops the pin in one transaction (through the sync decorator).
3. **Grant-only apply.** `EnforcementRequest.grantOnly` travels through `JvmSessionEnforcement` and `BrowserEnforcementLink.start` to `MacOsBrowserDomainEnforcer`, which then applies only with the grant and never falls back to the prompting apply; a missing grant maps to `AUTHORIZATION_REQUIRED`.
4. **Claims** (`PauseClaims`, common). It wraps the gated port with one mutex. The manual view records a manual claim on apply; its clear releases only its own claim and clears the helper only when no schedule claim is held; its status reads `CLEARED` without a manual claim. `claimSchedule` joins an applied manual session without re-applying, otherwise applies grant-only; `releaseSchedule` clears only when no manual claim is held. The session owner gets the manual view.
5. **Start gate.** Console not ours: wait, read nothing. No consent: setup required, no helper call. Otherwise read status and grant; a grant other than `ON` withdraws the consent and means setup required; `UNAVAILABLE`/`UNCERTAIN` are transient. Read only when an attempt is due. The console check is a small JNI call (`CGSessionCopyCurrentDictionary`, `kCGSessionOnConsoleKey`).
6. **Host** (`schedules/host`). A pure policy turns plans, facts, pins, now, the claimed keys and the attempt memory into: running occurrences, new pins, finished keys, whether to attempt, and whether to release. `ScheduleHost` runs from `DesktopPresence.runWhileResident` and evaluates at launch, on each wall-clock minute (with a jump check against monotonic time), and on store changes; it exposes the scheduled pause (name, latest end, state applied / setup required / waiting / failed) and `endEarly()`.
7. **Notices.** The notifier follows the combined pause: one end notice at the latest end, rescheduled when it moves, never cancelled by a manual end inside a schedule; "Scheduled pause started: <name>, until <time>" once per occurrence; "A scheduled pause couldn't start on this Mac. Open Posato to finish setup." once per occurrence. Pin notice bits keep a relaunch from reposting.
8. **UI.** Session shows the scheduled pause with its latest end and **End early** (confirmation caption with the other-date limit); Start is not offered during it; "Setup required on this Mac" with **Finish setup** when it could not start. The menu shows the pause and **End early**, and Quit asks for confirmation while a schedule is enabled ("Quit stops new scheduled starts until Posato opens again.").
9. **Tests first:** engine pins, host policy, store operations, claims, grant-only chain, gate, notices.
10. **E2E (Tart):** as in the brief, with a scenario generated from the guest's clock.

**Deferred** (the minute tick covers them within a minute, or they are follow-ups): native wake, clock and time-zone observers; re-applying when paused items change during a scheduled pause; pruning old facts and terminals; the Schedules row label.

## High-risk plan review

- **Verdict:** `changes-required`, folded here.
- **R1 Neither claim lifts the other.** A manual apply while the schedule's restrictions are applied records the manual claim without calling the helper, and a schedule that starts during applied manual restrictions joins them without a call; so no failed apply can restore the proxy under the other claim. When the manual session ends inside a schedule, the schedule's own request and end are applied again through the grant; when the schedule ends inside a longer manual session, the manual request is applied again through the grant. Session hides **Resume** while a scheduled pause is applied.
- **R2 The updater waits for a scheduled pause.** Maintenance close refuses (`SESSION_ACTIVE`) while an occurrence is claimed or pinned and running; the host treats a closed maintenance gate as waiting, never as failed or setup required.
- **R3 No answer is not an answer.** The start gate reads the grant only after the helper reads `READY`; it withdraws the consent only on `OFF` or `UNSUPPORTED` from a Status, or `NOT_ENABLED`; `UNKNOWN` and failed reads are transient.
- **R4 Classified results.** The grant-only chain returns applied, `AUTHORIZATION_REQUIRED` (grant unavailable; the host then checks the console: another account means waiting), `UNAVAILABLE` (retry) or `FAILED`. The scheduled request's session identifier is `schedule-<id>-<date>` of the earliest running occurrence and its end is the pause's latest end.
- **R5 Setup waits for a scheduled pause, and the host waits for setup.** The setup state's `sessionBlocked` also follows an applied scheduled pause. The helper port counts its operations in flight (enable, check, remove, grant change); the host makes no attempt while one runs.
- **R6 The host watches the helper.** Each minute, a held claim reads the helper's status; `CLEARED`, or `UNKNOWN` twice, drops the claim, shows the loss and attempts again without a prompt.
- **R7 Triggers.** Launch, the minute tick (sleeping at most 60 s of monotonic time, so a wake or a clock jump is seen within a minute) and plan or fact changes from people and sync. The host's own pins, notices and terminal markers do not emit changes. Evaluations run one at a time from a conflated trigger; a change that lands during an apply triggers the next evaluation at once.
- **R8 Terminal stays terminal.** A pin is inserted only when the key has no terminal marker, in the same statement.
- **R9 Login launch.** The E2E includes a login launch inside the interval, or the record says why it could not run.
- **Recommended, taken.** Deferring the native wake, clock and time-zone observers is a bounded deviation (at most a minute late), recorded here and tested with a clock jump; App Nap lateness is measured in the E2E with the window closed for five minutes. The console check applies only to attempts, and an unreadable console proceeds. **End early** is offered only while a pause restricts this Mac, and Start stays available when nothing is applied. The setup-required notice is posted once per occurrence only on a Mac that had the consent, so a Mac that was never set up is not reminded every day; Session and Schedules still say it.
- **Optional, noted.** The blocked page keeps the joined manual session's end time until that session ends.

## Result

- **Delivered.** A resident host starts and ends scheduled pauses on a Mac that is ready for schedules, with the window closed, through the standing grant only; pins keep a running occurrence through edits and relaunches; manual sessions and schedules share the helper through claims; Session, the menu and notices show one combined pause; End early ends both; a Mac that is not ready says "Setup required on this Mac" and never asks for a password; Quit asks first while a schedule is enabled.
- **Deviations.**
  - The store API is two operations, `stop(keys, SKIP or END)` and `recordHost(pins, notices, finished)`, instead of four, to stay within the function limit; `skip` became `stop`.
  - Native wake, clock and time-zone observers are deferred: the minute tick sleeps at most a minute of monotonic time, so a wake or a clock change is seen within a minute (a bounded deviation from "on wake").
  - The consent is read from the helper port, so the host works without a window; the console check is a small JNI call.
- **Completed-change review:** `changes-required` with four Required findings, all fixed with tests: the claims now remember whose request the helper holds, so a claim that only joined never touches it (R1); a failed hand-back at a manual end clears the helper and lets the host apply again (R2); a failed clear at a schedule's end is retried each minute (R3); an exception in one evaluation no longer stops the resident process (R4). Recommended, taken: a grant refusal whose recheck has no answer retries instead of asking for setup; announcements are remembered in memory so a start notice is never posted twice; two unanswered status reads drop the claim without clearing.

## Checks

- `./gradlew quality` (see the closing run below).
- Mutations killed: the holder check on release, the clear after a failed hand-back, the pending clear, the evaluation loop's catch, the in-memory announcement set.
