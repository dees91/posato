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

## High-risk plan review

- **Verdict:** changes-required, resolved before implementation; the
  authorization stays within the accepted wording.
- **Required findings and resolutions:**
  - (R1) Every automatic request carries `grantOnly`, so it can never fall
    back to the administrator prompt; `retry()` and a person's start keep
    today's request.
  - (R2) One recovery per session tag: a recovery already running for that
    tag absorbs any later trigger.
  - (R3) After a failed attempt and when the attempts run out, the owner
    restores the exact action the person would have seen, kind and
    `repeatsSystemPrompt` included; `recovering` turns off in the same update
    that sets `Active`.
  - (R4) Starting points: the `RESUME_REQUIRED` branch of `reconcile`,
    `handlePollLoss`, a Status that throws, and the catch of `runReconcile`.
    Never: a person's start or Retry, a halted reconcile, the
    `pendingNativeExpiry` bank failure, any `CLEAR_FAILED`, an ended row.
  - (R5) The ADR 0004 "Silent apply" row and its lifecycle sentence, and the
    `T-07` residual in the threat model, name the new delta: a linked device
    can make this Mac apply a manual pause again, not only a schedule.
  - (R6) The "every attempt fails" path is shown in Tart by stopping the
    daemon past the attempts (`vm stall`), so the gate or the apply never
    succeeds; the switch off shows the `SETUP_REQUIRED` path.
- **Recommended, taken:** an update admission that is closed waits without
  counting an attempt; each attempt first reads whether the helper still
  holds the pause and adopts it; cancellation only between attempts; at most
  three recoveries per session in ten minutes, then the notice; `AC-01`
  checks that the automatic-start consent survives a helper kill; the menu
  bar has a recovering case; the `SESSION-009` fix also clears `actionTag`.
- **Testing:** `SessionAutomaticRecoveryTest` pins the attempt bound, the
  cancellation on End early, the restored notice, the person's own failed
  start, and one recovery under overlapping polls, plus the `SESSION-009`
  regression; before the fix five of its seven cases fail (the End early
  case runs out of memory in the `SESSION-009` loop). A pause from another
  device is verified with the test iPhone; a guest that cannot sleep is
  recorded as an evidence limit, since wake reaches recovery through the
  same poll as the helper kill.

## Before the fix

| Check | Result | Evidence |
| --- | --- | --- |
| Tart, package from `main`, pause, `vm kill --process helper` | "Restrictions need attention" with **Retry**; `observe --expect allowed` passes | `build/verification/runs/20261026-072015-a1b3`, `20261026-072019-a6c9` |
| Tart, Posato ended and opened again during the pause | "Restrictions not active on this Mac." with **Resume restrictions** | `build/verification/runs/20261026-072057-c24f` |
| `SessionAutomaticRecoveryTest` on the owner from `main` | five of seven fail | `:shared:jvmTest` |
