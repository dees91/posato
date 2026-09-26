# Execution: `ONBOARDING-004`

- **Brief:** [One guided Mac setup](../specifications/onboarding-004-unified-setup.md)
- **Status:** `active`
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

## High-risk plan review

- **Verdict:** `pending`

## Result

- Pending.

## Completed-change review

- **Verdict:** `pending`

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Pending | | |

## Blockers and accepted risks

- Pending.

## Final

- **Status:** `active`
- **Outcome:** pending
