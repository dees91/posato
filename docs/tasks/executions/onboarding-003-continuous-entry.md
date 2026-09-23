# Execution: `ONBOARDING-003`

- **Brief:** [Continuous onboarding entry](../specifications/onboarding-003-continuous-entry.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Claude; Codex prepared the handoff
- **Reviewer:** independent Claude agents (standards and spec axes); handoff reviewed by an independent Codex agent
- **Branch:** `feature/onboarding-003-continuous-entry`
- **Updated:** 2026-09-23

## Plan

1. Remove the website step's focus clearing so `WebsiteEntry` keeps focus after each accepted submission.
2. Scroll expanded onboarding content on its own, with actions directly after it, so **Continue** stays above the keyboard.
3. State the saved total below the field from policy state, observed while the step is visible, separate from the last submission's feedback.
4. Place expanded permission and iCloud actions in one `PosatoActionRow`; keep compact layouts unchanged.
5. Test saved-count behavior, drive iPhone, iPad, and Mac, then review, run the quality gate, and close the record.

## Parallel ownership

- Start from `a8bc2c5`. Own `OnboardingSteps.kt`, `OnboardingPage.kt`, `OnboardingPermission.kt`, and related onboarding state/tests and resource entries as needed.
- Keep `MacHelperPort`, helper readiness/lifecycle contracts, session enforcement, Gradle/Xcode configuration, and navigation ownership unchanged. Escalate a necessary shared-contract change before editing it.
- Integrate onboarding first. Its implementer owns the first changes to `DESIGN.md`, `strings.xml`, verification recipes, and the wiki log. `MACOS-011` rebases after this merge before editing those shared files.
- Run native verification one task at a time on each target. A worktree does not isolate the installed app, helper, or desktop database from another run.

## Result

- `WebsiteStep` no longer clears focus after a saved addition; the entry field's own focus request keeps it ready for the next website.
- The website step shows `1 website saved` / `N websites saved` below the field as a polite live region, from `savedWebsites`; the WEBSITE step now observes policy changes like Summary. Field feedback still describes only the last submission. No resource entries were added.
- `OnboardingPage` scrolls expanded content with `weight(1f, fill = false)` and keeps actions outside the scroll. `OnboardingActions` is one `FlowRow`: expanded pages wrap actions with the action-row spacing (12 horizontal, 8 vertical); compact pages place one centered action per row, so the full-width primary action is unchanged. The iCloud actions moved into `IcloudActions`.
- `DESIGN.md`, the onboarding verification recipe, and the wiki open-question entries record the accepted behavior. Helper ports, lifecycle, persistence, and navigation are unchanged.

## Completed-change review

- Handoff documents: approved by independent Codex agent `review_handoff`; no findings.
- Standards axis (independent agent, full diff against `a8bc2c5`): no Critical or Required findings. Recommended: a large-text check of Summary's actions on a short Mac window, and the first save's live-region announcement, because the caption node appears on that save. Both are recorded below as accepted risks.
- Spec axis (independent agent, diff plus run evidence): one Required finding. The expanded-scroll change carried a `user-confirmed` label that the brief did not record. Resolved by recording its acceptance in the approved implementation plan in the brief. Recommended: state the uncaptured approval-required Mac row, and note that the total scrolls out of view in iPad landscape while typing. Both are recorded below.
- Correction review (independent agent, action-container and `IcloudActions` delta): no Critical, Required, or Recommended findings; compact centering, full-width primary, 12/8 expanded spacing, and every branch's `enabled` rules match the base.

## Verification

- `./gradlew :shared:jvmTest` for onboarding and targets UI tests passed; `OnboardingUiStateTest` ran 26 tests, including three new saved-total tests.
- First `./gradlew quality` failed on two Detekt findings, reused content slots in `OnboardingActions` and `IcloudStep` length, fixed at the source without suppressions. It also failed on `:desktopApp:verifyMacOsDevelopmentPackaging`, because the driver's desktop build had left a development-signed package; restaging made it pass. The final `./gradlew quality` passed.
- Native runs were repeated after the action-container correction: iPad mini `20260923-084906-cf13` (identical row frames, focus kept, Continue above the keyboard in both orientations), iPhone 17 `20260923-085009-f6fc` (compact full-width primary with a centered quiet action, focus and totals as before), and Mac `20260923-085052-e5e1` (iCloud and permission rows). The Mac databases were restored from the `reset` backup in run `20260923-085052-4eb1`, and the development package was restaged ad-hoc.
- Earlier runs, on the first action-container implementation:
  - iPhone 17 Simulator, run `20260923-083045-0157`: two separate additions via Return; the snapshot reports the field focused after each; totals 1 and 2; a duplicate and invalid input leave 2, and invalid text stays; Continue above the keyboard without scrolling; Summary `2 websites saved`; Session after finishing and after relaunch; two rows in the database.
  - iPad mini Simulator, run `20260923-083204-3612`: iCloud and permission actions in one row in portrait and landscape; focus kept after additions in both orientations; Continue pinned above the keyboard in landscape; Summary; Session after relaunch; restored to portrait.
  - Mac desktop, runs `20260923-083356-ef55` and `20260923-083556-d189`: the iCloud row and the **Enable on this Mac** / **Not now** row; two additions with totals 1 and 2 and a visible caret and focus border; Summary; Session after relaunch. The desktop databases were backed up by `reset` (run `20260923-083355-5e11`), restored byte-identical afterwards, and hold only the maintainer's original rows.

## Blockers and accepted risks

- The desktop accessibility bridge reports `focused: false` for the Compose text field, so Mac focus evidence is visual only.
- The approval-required Mac row (**Open System Settings**, **Check again**, **Not now**) was not driven, because it needs attended helper approval. It uses the same `OnboardingActions` container as the driven rows, and its hierarchy and enablement code are unchanged.
- On iPad mini landscape with the keyboard up, the content viewport is only a few lines tall: the field's lower edge is clipped and the saved-total caption scrolls out of view below it. Both remain reachable by scrolling, the caption is a live region, and **Continue** stays visible.
- The first save's live-region announcement and Summary's actions at large text on a short Mac window were not checked separately. Compact pages already had the same fixed-actions limit.
