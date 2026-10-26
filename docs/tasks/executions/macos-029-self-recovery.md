# Execution: `MACOS-029`

- **Brief:** [The Mac applies restrictions again by itself and asks the person only when it must](../specifications/macos-029-self-recovery.md)
- **Status:** `planned`: brief only; implementation waits for the maintainer
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent agents (plan review, completed-change review)
- **Branch:** `macos-029-self-recovery`
- **Updated:** 2026-10-25

## Cause

`observed` in the code (`SessionTransitionOwner`, `JvmSessionEnforcement`):
on the Mac `reapplyRequiresPrompt` is `true`, a constant from before the
standing grant. So a pause found without its restrictions (received from
another device, found at a login launch or relaunch) goes to
`RESUME_REQUIRED`, and a pause whose helper is lost (a failed Status poll,
sleep, `MACOS-031`'s stall past the lease) goes to `APPLY_FAILED`; polling
stops and only a press applies again. With the grant on, that press applies
without a prompt (`grantOnly`), as a scheduled start does through
`MacScheduleStartGate`. `observed` in Tart on 2026-10-26: after Posato was
ended during a pause and opened again, Session showed "Restrictions not
active on this Mac." with **Resume restrictions**.

## Plan

1. Failing first, in Tart on the package from `main`: (a) a pause whose
   helper is killed (`vm kill --process helper`) shows the red notice; (b)
   Posato ended and opened again during a pause shows "Restrictions not
   active on this Mac."; (c) `SESSION-009`: an isolated
   `SessionTransitionOwner` regression in which a received session ends
   unresumed and the view keeps the notice and loops.
2. `SessionTransitionOwner` takes an optional automatic-apply gate (the
   Mac's `MacScheduleStartGate`, the same one a scheduled start uses; none
   on other hosts, which already re-apply silently). Where the Mac would
   ask the person for an active pause (the `RESUME_REQUIRED` branch of
   `reconcile`, `handlePollLoss`, a Status that throws, a failed
   reconcile), it starts one recovery for that session instead: up to three
   attempts at 0, 15, and 45 s, each asking the gate, then re-applying with
   `grantOnly` through the existing `reapplyCurrent`. `READY` applies;
   `TRANSIENT` waits for the next attempt; `SETUP_REQUIRED` or
   `OTHER_ACCOUNT` stops. A changed session, End early, Retry, or close
   cancels it. When it stops without success, the notice and its button
   appear as today.
3. While a recovery runs, `EnforcementViewState.recovering` is true: Session
   shows the caption "Applying restrictions again…" instead of the notice or
   "Restrictions active."; the menu bar keeps "Restrictions not active on
   this Mac" with no action. The internal state stays the action the person
   would otherwise see, so the transition drain treats it as converged.
4. `SESSION-009`: when `clearAfterEnd` returns early because nothing is
   applied, it also clears the pending action and returns the view to
   inactive.
5. ADR 0004 and `DESIGN.md` with the accepted wording; the Quit dialog's
   "until you open Posato and resume it" stays (with the switch off it is
   still true).
6. Verify in Tart (`AC-01` helper killed, `AC-02` relaunch and login launch,
   a pause from a linked peer VM, sleep and wake if the guest can sleep,
   `AC-03` with the switch off), `./gradlew quality`, review, record, wiki.

## Plan decisions

- `user-confirmed` (2026-10-26): ADR 0004 amendment, "The resident
  application may also request Apply with grant without a person's action
  to restore a pause that is already active in this Mac's record (started
  here, received from a linked device, or found at launch or wake), under
  the same conditions as a scheduled start: a Status confirms the grant, the
  automatic-start consent is valid, and this account has the console. Each
  loss gets at most three attempts, spaced over about a minute; when they
  fail, or the conditions do not hold, the Mac shows Resume restrictions as
  before. This replaces the SCHEDULE-001 'Received sessions' rule and the
  sentence that a login launch or relaunch never re-applies without a
  foreground Apply. The reach is that of a schedule: Posato's own proxy, for
  a pause the person or a linked device started, with End early always
  available."
- `user-confirmed` (2026-10-26): while Posato applies again by itself,
  Session shows a quiet caption "Applying restrictions again…" with no red
  notice and no button; the notice appears only when the attempts fail or
  the switch is off.
