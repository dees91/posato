# Execution: `MACOS-029`

- **Brief:** [The Mac applies restrictions again by itself and asks the person only when it must](../specifications/macos-029-self-recovery.md)
- **Status:** `in-review`: implemented and verified in Tart and on the test iPhone
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent agents (plan review, completed-change review)
- **Branch:** `macos-029-self-recovery`
- **Updated:** 2026-10-26

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

## Result

- `SessionAutomaticRecovery` holds the attempt loop, the per-pause cap, and
  the view while it runs; the owner only routes its four starting points to
  it and runs `reconcile(automatic = true)` as one attempt, whose prompt
  branch re-applies with `grantOnly`. `SessionTransitionOwner` was at
  Detekt's `LargeClass` limit already, so one equivalent block in
  `applyAfterStart` was written shorter to stay within it.
- `recovering` is cleared only when a recovery stops; Session and the menu
  bar read it together with an action state, so a stale flag never hides an
  active state.
- Deviations (`agent-delegated`): an update admission that is closing reads
  as a transient gate answer and so counts as an attempt, rather than
  waiting outside the count; End early does not cancel a waiting recovery,
  whose next attempt finds the pause ended and stops (Retry still cancels
  it).
- Implementation detail (`agent-delegated`, narrowing the accepted ADR 0004
  amendment, not part of it): the application asks the same gate as a
  scheduled start (`MacScheduleStartGate`), and every such request is
  grant-only, so it never raises the administrator prompt. A pause gets at
  most three such recoveries in ten minutes; then the notice stays.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `SessionAutomaticRecoveryTest` | five of seven failed before the fix; all seven pass | `:shared:jvmTest` |
| `AC-02` Tart, Posato ended and opened again during a pause (switch on, consent on) | **Restrictions active** within seconds, no notice; `observe --expect blocked` | `build/verification/runs/20261026-073254-012f`, `20261026-073304-99b9` |
| `AC-01` Tart, `vm kill --process helper` during the pause | a new helper started 12 s later and applied; **Restrictions active** throughout; `observe --expect blocked`; automatic-start consent still on | `20261026-073311-db7d` to `20261026-073346-3905`, `20261026-073348-5ab7` |
| Every attempt fails: `vm network --state off` during a new pause | "Applying restrictions again…" for about 45 s, then "Restrictions need attention" with **Retry** | `20261026-073919-5c91` to `20261026-074007-f0e3` |
| Cap: a fourth loss of one pause within ten minutes | the notice at once | `20261026-073656-4ad6` |
| `AC-03` switch off, `vm kill --process helper` | the notice at once with **Retry**, `observe --expect allowed`; **Retry** with `vm prompt admin` applies again | `20261026-074318-29f9`, `20261026-074349-ee8a`, `20261026-074423-ebcb`, `20261026-074426-46f8` |
| `AC-02` a pause started on the test iPhone (linked to the primary clone through the test Apple Account) | it reached the Mac at 08:21 and showed **Restrictions active** at once, never Resume restrictions; `observe --expect blocked` | `build/verification/runs/20261026-082108-0c74`, `20261026-082122-8eeb` |
| `AC-02` sleep and wake | evidence limit: a Tart guest cannot sleep (`pmset sleepnow`: `Unable to sleep system: error 0xe00002e2`); after a wake a loss reaches recovery through the same poll as the helper kill | none |
| `AC-02` login launch | not run separately: a login launch opens Posato through the same launch path as the relaunch run | none |

Afterwards the iPhone's **Remove workspace** ended twice in "Sync did not
finish" after the Mac had removed the workspace, the test account's known
long-history case (the sync recipe's Development zone reset); the Mac clone
was removed cleanly.

## Completed-change review

- **Verdict:** changes-required, resolved.
- **Required finding:** (R-1) a recovery could overwrite a state it did not
  own: a pause that ended during the wait with a failed clear showed
  "needs attention" instead of the clear-failure notice, and a recovery for
  one pause could rewrite another pause's notice. Each attempt and the end
  of a recovery now write only while the owner's desired row is still that
  active pause; a new test covers the ended-with-failed-clear case and
  failed first.
- **Recommended, taken:** one recovery per loss even across threads (an
  atomic start); Retry clears the caption with the recovery; tests for the
  per-pause cap and for Retry cancelling a waiting recovery.
- **Optional:** the `SESSION-009` branch runs on every host, with no visible
  change outside the Mac (traced by the reviewer); the ADR paragraph beyond
  the accepted wording now lives under Result as `agent-delegated`; `recovering` clears when
  the recovery stops rather than in the update that sets `Active`, which the
  UI tolerates because the caption needs an action state.

| Check after the review | Result | Evidence |
| --- | --- | --- |
| `SessionAutomaticRecoveryTest` | ten of ten pass; the clear-failure case failed first | `:shared:jvmTest` |
| Tart, fresh clone, `vm kill --process helper` | a new helper at 08:53:16 applied; **Restrictions active**; `observe --expect blocked` | `build/verification/runs/20261026-085316-80ae` to `20261026-085347-56f0`, `20261026-085348-0260` |
| Tart, Posato ended and opened again | **Restrictions active**, `observe --expect blocked` | `20261026-085415-3398`, `20261026-085416-af43` |
| Tart, network off | "Applying restrictions again…" from 08:54:42 to about 08:55:20, then the notice with **Retry** | `20261026-085442-0fe0` to `20261026-085529-5cdb` |
