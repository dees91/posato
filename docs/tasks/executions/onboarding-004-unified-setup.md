# Execution: `ONBOARDING-004`

- **Brief:** [One guided Mac setup](../specifications/onboarding-004-unified-setup.md)
- **Status:** `ready-for-review`
- **Review tier:** `high-risk`
- **Implementer:** Claude, under the maintainer's delegated night mandate (2026-09-26)
- **Reviewer:** independent agents (plan and completed change)
- **Branch:** `feature/onboarding-004-unified-setup`
- **Updated:** 2026-09-27

## Plan

1. **Setup run** (shared, in `MacHelperSetupUiState`). `setUp(sessionBlocked)` runs three steps in order.
   - **Blocking.**
     1. `recheck`.
     2. `enable` when the answer is not enabled or unavailable.
     3. When approval is required: `openApprovalSettings`, then a `recheck` every 2 s for at most 3 minutes. The step shows "waiting for System Settings". An unapproved end leaves the step waiting, with **Try again**.
   - **Login.** `loginItem.setEnabled(true)` when it is off, then verified through `enabled`.
   - **Password.** `standingGrant.read()`. When it is not on: `setEnabled(true)`, then verified. `UNSUPPORTED` skips the step.

   While it runs, a step names what the person does next: approve in System Settings, or enter the Mac password. Complete means blocking is ready, the login item is on, and the grant is on or unsupported. A session-blocked call does nothing. The run and the quiet read never overlap.
2. **Offer.** `MacHelperPort` gains `setupOfferDismissed()` and `dismissSetupOffer()`. The desktop stores the marker in its `UserDefaults` through the leaf's `readFlag` and `writeFlag` from `NOTIFY-001`. Session shows the offer once when the helper is ready, setup is incomplete, it has not been dismissed, and no session is active.
3. **UI.**
   - `MacSetupAction` replaces the disabled preview in onboarding, Finish setup, Schedules, and the offer: it shows **Set up Posato**, progress, **Try again**, or **This Mac is ready**.
   - The overview lines show each step's status while setup runs.
   - This Mac gets **Set up Posato** at the top of its options when setup is incomplete. Its advanced switches and Remove stay.
   - The preview notices and copy are removed.
4. **Tests, failing first:**
   - the order and skipping, and verified completion;
   - the approval wait bound;
   - the session guard;
   - a cancelled password leaving the step incomplete;
   - offer visibility.
5. **Docs:** `DESIGN.md` (the setup is now wired and the preview text goes), the product scope, the recipes, and the wiki log.

### Plan corrections from the review

- **Live session guard.** The holder takes a live `sessionBusy` check built from the session owner: active, starting, or enforcement busy. The check runs on press, before `enable`, and before the grant request. A session starting mid-run stops the run before the password step, with "You can finish setup after the session ends." Start stays disabled while setup runs. A session started on another device during a password prompt is the existing `MACOS-014` residual.
- **One busy rule.** Check, enable, remove, the grant switch, and setup all refuse to run while any of them runs. This Mac's controls are disabled during setup.
- **Old daemon.** `UNSUPPORTED` is not complete. The password step then needs attention.
- **Offer.**
  - It is a compact card below the Start action.
  - It shows only when the helper is ready, the login item or grant is known to be missing (not unknown or unread), it has not been dismissed, and no session is active.
  - The marker is written on dismissal and on verified completion.
  - Other incomplete states keep the existing notice and **Finish setup**.
- **Main thread.** `MacLoginItem.setEnabled` runs off the main thread.
- **Copy.** The approval step says "Turn on Posato under Allow in the Background. macOS may ask for your password."
- **Closing the window** cancels the run. A blocking helper call that is already running finishes first, and **Set up Posato** then resumes the missing steps.
- **Additional isolated tests:**
  - run exclusivity;
  - a session starting mid-run;
  - `UNSUPPORTED` and unknown grants are never complete and show no offer;
  - a login item that stays off needs attention;
  - a quiet read after a run keeps the run's result.
- **E2E steps:**
  - Declined approval: leave the toggle off, wait out the bound, then **Try again**.
  - Interruption: `close-window` during the approval wait, reopen with `menu --choose`, then **Set up Posato** runs only the missing steps.
  - Cancelled password: `vm click --text Cancel` on the administrator dialog.
  - Background approval may itself ask for a password, so run `vm prompt admin` in either order.
  - Upgrade offer: a fresh clone with only the helper enabled through This Mac, then relaunch, dismiss, and relaunch.
  - No password at Start: `vm text` shows no dialog, and `observe --expect blocked` passes.

## High-risk plan review

- **Verdict:** `changes-required`, folded above.
- **Required findings:** R1 live session guard; R2 run exclusivity; R3 `UNSUPPORTED` counted as ready; R4 offer placement and migration; R5 named E2E steps; R6 missing isolated tests; R7 process.
- **Process deviation (R7):** a first draft of the run and its first six tests was written before the verdict. The six tests were seen failing against stubs before that draft, and the new corrections are test-first.

## Result

- **Delivered.**
  - `MacHelperSetupUiState.setUp` runs blocking, login, then the password step, skipping finished steps, with a 3-minute approval bound. One busy rule covers check, enable, remove, the grant switch and setup. A live `sessionBusy` check (active session or enforcement busy) runs on press, before `enable` and before the grant request.
  - Complete means blocking ready, login item on, and the grant `ON`; `UNSUPPORTED` and unknown are incomplete.
  - The offer shows when blocking is ready and the login item or grant is known to be missing, and it is not dismissed. It stays through its own run. Dismissal and verified completion write `setupOfferDismissed` to the app's user defaults through the leaf flags from `NOTIFY-001`.
  - `MacSetupAction` in onboarding, **Finish setup**, Schedules (`SchedulesMacSetup`), This Mac and the Session offer card. Session keeps its setup route open while a run is in progress, so **Start** cannot race it.
  - The login item is enabled on `Dispatchers.Default`.
  - Onboarding's individual helper controls start collapsed under **Blocking settings**; the tracked `onboarding-helper-finish-desktop` recipe opens them first.
- **Tests.** `MacUnifiedSetupTest` (6) and `MacUnifiedSetupGuardTest` (6). Mutations that drop the session guard, the shared busy rule, the `ON`-only completion, the completion marker, or the quiet-read guard each fail one test.
- **Decisions under the delegated mandate.**
  - Onboarding shows **This Mac is ready.** above a primary **Continue** after completion.
  - The approval caption reads "Turn on Posato under Allow in the Background in the System Settings window that opened. macOS may ask for your password."
  - After a session ends, This Mac collapses again as before; its setup action is one tap away.

## Completed-change review

- **Verdict:** `changes-required`, then resolved.
- **R1** (Schedules and the offer card showed an enabled **Set up Posato** during a session that did nothing): the holder now carries `sessionBlocked`, fed by `SessionTransitionOwner.blockingSetup()` from `PosatoApplication`, and every `MacSetupAction` disables itself with "You can finish setup after the session ends."
- **Rec1** ("ready" before the run re-read the grant): complete is false while a run is in progress; a test holds the run's grant read and a mutation that drops the check fails it.
- **Rec2**: the login switch is disabled while setup runs.
- **Rec3**: onboarding keeps its quiet **Not now** during the approval wait; the run continues if the person moves on.
- **Rec4** (the live guard omits "starting"): deviation recorded. `SessionTransitionOwner` has no starting state; a start already in flight shows as enforcement busy, and `showsMacSetup` keeps Start off the screen while a run is in progress.
- **Rec5**: `DESIGN.md` now says This Mac carries the action once blocking works.
- **Rec6** (closing the window mid-run not driven): left for the maintainer to accept or request; see the risks below.
- **Optional**: the offer card's button is secondary, so **Start a session** stays the one primary action; **Try again** appears only after a run that did not finish every step.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `:shared:jvmTest` onboarding and session suites | pass | including the two new classes and the mutations above |
| Mac, fresh Tart clone, onboarding (`AC-01`) | pass | `mac-unified-onboarding-desktop` run `20260927-012515-c018`: **Set up Posato**, the helper toggle in Login Items, two administrator dialogs, then **This Mac is ready.**, and Session without **Finish setup**. |
| Mac, start with no password (`AC-01`) | pass | On a clone set up the same way, `session-start-desktop` run `20260927-011246-a1c9`: no password dialog in 60 s of polling; `observe --website http://example.com --expect blocked` run `20260927-011431-e6c9` reported `paused`. |
| Mac, declined approval (`AC-02`) | pass | The first attempt left the helper toggle off; after the 3-minute bound the step read "Waiting for your approval in System Settings" with "Setup did not finish." and **Try again**. **Try again** completed it once the toggle was on. |
| Mac, cancelled password (`AC-02`) | pass | **Set up Posato** in This Mac: the login step finished, the administrator dialog was cancelled with `vm click --text Cancel`, and This Mac showed "Setup did not finish." with **Try again**. **Try again** asked once more and completed; both switches read on. |
| Mac, upgrade offer (`AC-03`) | pass | With the helper ready, the login item and grant turned off in This Mac, and `setupOfferDismissed` deleted: after relaunch the card appeared below **Start a session**. **Not now**, then a relaunch: no card. Verified completion later wrote the marker (`defaults read` = 1). |
| Mac, during a session (`AC-04`) | pass | With a session active, This Mac's **Set up Posato** reported `enabled: false` with "You can finish setup after the session ends." |
| Mac, after the review fixes (R1) | pass | Rebuilt and synced clone: with the login item off and the marker deleted, the offer card showed a secondary **Set up Posato**; a session then started with no password dialog, the card was gone during it, and Schedules' **Set up Posato** read `enabled: false` with "You can finish setup after the session ends." The login item had also opened Posato at boot. |
| `quality` | pass | after the last correction, without the ignored `local.properties` (a dev-signed package fails the packaging check) |

## Blockers and accepted risks

- **Interruption by closing the window** was not driven (needs the maintainer's acceptance or a follow-up run). The run lives in the window's composition scope and is cancelled with it; the next **Set up Posato** resumes the missing steps through the path proven above.
- **Upgrade migration was simulated** by turning the two steps off on a set-up clone and deleting the marker, not by installing 1.1.0 first.
- **A session started on another device during the password dialog** stays the `MACOS-014` residual.

## Final

- **Status:** `ready-for-review`
- **Outcome:** `AC-01` to `AC-05` met on Mac; closing the window mid-run awaits the maintainer's acceptance
