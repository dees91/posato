# Execution: `MACOS-009`

- **Brief:** [macos-009-in-app-removal.md](../specifications/macos-009-in-app-removal.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code
- **Reviewer:** independent plan-review agent
- **Branch:** `feature/macos-009-in-app-removal`
- **Updated:** 2026-09-15

## Decisions

`user-confirmed` 2026-09-15:

- **D1.** Removal is refused during an active session, with a note to end the session first.
- **D2.** The removal action lives inside the expanded This Mac row.

## Starting observations

`observed` at `9e7e677`:

- **Helper routing.** The helper forwards Remove to the root daemon only when the service is enabled. Otherwise it answers locally with `unreconciledServiceResponse`: `NotRegistered`/`ManualRecovery`, `ApprovalRequired`/`BackgroundApproval`, or `Unavailable`/`ManualRecovery`. It never touches the right.
- **Daemon.** `performRemove` restores ownership, requires `Idle`, then removes the right and verifies it is absent. Possible failures:
  - a restore that cannot reach `Idle`, or a corrupt or unavailable store, returns `RecoveryRequired` with failure `Integrity` or `Storage`;
  - a change between snapshot and replace returns `Conflict`/`ProxyRecovery`;
  - a rule failure returns `RuleRepair`/`Integrity`.
- **After success.** The helper calls `service.unregister()`. If that throws, the helper exits without replying, and the client sees `UnknownOutcome`.
- **No prompt.** No administrator prompt is involved in removal.
- **Client reconciliation.**
  - `shouldReconcileUnknownRequest` diverts only Enable and Status.
  - `reconcileUnknown()` replays whatever request is pending, so a pending Apply can re-take ownership.
  - Status also reconciles a pending Remove.
- **Shared client.** Session enforcers use the same `MacOsHelperClient`. After removal, a session start still calls `acquireApplyGrant` and then fails as `APPLY_FAILED`. App-only enforcement lives in the helper, so its ownership stays `Idle`.
- **UI.**
  - `MacHelperPort` has `enable`, `recheck`, and `openApprovalSettings`; fakes live in three tests.
  - `MacHelperSetupUiState` runs one activity at a time.
  - `SessionOverviewContent` knows `active`.
  - The Remove workspace action pairs a quiet error-colored button with an `AlertDialog`.

## Plan

1. **Client.** `MacOsHelperClient.remove()` returns the `HelperResult` together with whether it concluded a Remove:
   - If a non-Remove request is pending, reconcile it first. If that is still inconclusive, return it marked as not a Remove. Otherwise continue.
   - Send Remove. `shouldReconcileUnknownRequest` also diverts a pending Remove, so a retried press finishes the original request.
   - Tests: a pending Remove, a pending Apply or Enable, and a reconcile that stays unknown.
2. **Port.** Add `MacHelperRemoval` and `suspend fun remove()` to `MacHelperPort` and `MacHelperCommands`; the fakes are updated. The desktop mapping is ordered, and the first match wins:

   | Tuple | Result | Next action in the row |
   | --- | --- | --- |
   | Remove concluded: `Success` + `NotRegistered` + `Idle` | `REMOVED` | helper removed; Posato can move to the Trash |
   | any other `Success` | `REMOVE_AGAIN` | Remove from this Mac again |
   | `UnknownOutcome` of a Remove | `UNCERTAIN` | Remove again finishes that request |
   | still-unknown earlier request, verification or transport failure | `CHECK_AGAIN` | Check again |
   | unlaunchable registration tuple | `CANNOT_START` | the existing registered-but-cannot-start copy |
   | `ApprovalRequired` or `BackgroundApproval` | `APPROVAL_REQUIRED` | allow Posato in System Settings, then remove again |
   | `NotRegistered` or `Unavailable` local answer | `NOT_ENABLED` | Enable on this Mac, then remove again |
   | `RecoveryRequired` with `Integrity`/`Storage`, or `Conflict` | `PROXY_ATTENTION` | proxy settings need attention; remove again |
   | any other daemon answer (`RuleRepair`, `Failure`) | `REMOVE_AGAIN` | Remove from this Mac again, never Check again |

   Tests cover every tuple above.
3. **State.** `MacHelperSetupUiState` gets:
   - a `REMOVING` activity and a `removal` result in `MacSetupPresentation`;
   - `remove(sessionBlocked)`, which is refused, with no call, while a session is active, starting, or enforcement is busy;
   - `REMOVED` sets readiness to `NOT_ENABLED`, and any later check or enable clears the result.

   Tests cover the refusal, the single flight, the readiness after each result, and that `REMOVE_AGAIN` never leads to a check that re-enables.
4. **UI.**
   - **Where Remove appears:** the quiet error-colored **Remove from this Mac** shows in the expanded This Mac options when readiness is `READY`, `APPROVAL_REQUIRED`, `UNAVAILABLE`, or `UNCERTAIN`. It does not show when unchecked, `NOT_ENABLED`, or `RECOVERY_REQUIRED`.
   - **Session blocking:** while a session blocks removal, the action is disabled and a caption says removal is available after the session ends.
   - **Confirmation dialog:** it says the helper restores proxy settings, removes its administrator rule, and turns off; website and app pauses stop until the helper is enabled again; paused items stay saved. The dialog closes, and confirming is refused, if a session becomes active.
   - **Feedback:** progress reads "Removing the background helper…", each result has its own notice and announcement, and the leaf preview gains the new states.
   - **Wiring:** `SessionOverviewContent` and `SessionScreen` pass the session-blocked flag and the callback.
5. **DESIGN.md.** Amend the This Mac bullet: removal action, availability, confirmation, progress, refusal during a session, and each result's copy.
6. **Physical acceptance**, attended on a Developer ID candidate built from this branch (build number 5).
   - **Install.** Install over build 4 by quit, replace, and open.
   - **Baseline before removal:** `security authorizationdb read app.posato.macos.proxy.apply` shows the definition; `launchctl print system/app.posato.macos.proxy-settings` finds the daemon; `scutil --proxy`; and the Posato entries in `sfltool dumpbtm`, run by the maintainer with sudo.
   - **AC-01 and D1:** start a session and confirm Remove is disabled; end the session; remove, confirming there is no administrator prompt, with confirmation and progress shown.
   - **AC-02:** the right is absent, the daemon is not found, proxy is back at baseline, and there is no enabled or allowed Posato daemon entry compared with the baseline.
   - **After removal:** a session start fails truthfully; then quit and relaunch.
   - **AC-03 subset:** turn the background item off, then remove, and see `APPROVAL_REQUIRED`.
   - **AC-04:** move Posato to the Trash, confirm there is no Posato item in Login Items and no enabled daemon entry, reinstall, enable, and block a website.
   - **Rerun rule:** if a completed-change review fix touches removal, mapping, or client wiring, rebuild and rerun this step.
7. **Closeout.** Update the wiki macOS enforcement topic, add one wiki-log entry, run `./gradlew quality`, and get an independent completed-change review.

The write surface is the port, setup state, `MacSetupSection`, session wiring, strings, `DesktopMacHelperState`, `MacHelperCommands`, the `MacOsHelperClient` remove and reconciliation, tests and fakes, and DESIGN.md. No helper, daemon, or wire protocol change is made.

## High-risk plan review

- **First pass (`86eabf3`): `changes-required`.**
  - **R1:** the mapping did not match the real Remove tuples. Fixed by the tuple-ordered table.
  - **R2:** "Check again" re-installed a removed right. Now daemon-reached failures point to Remove again.
  - **R3:** Remove could reconcile another pending request. It now reconciles that request first, and only a concluded Remove maps to `REMOVED`.
  - **R4:** the physical checks had no baseline. The baseline, background-item pass criteria, no-prompt observation, and rerun rule are now in step 6.
  - **Recommendations adopted:** confirm-time refusal including a starting session or busy enforcement, no Remove in `RECOVERY_REQUIRED`, observing a post-removal session start, and reusing the existing button style.

## Blockers and accepted risks

- **`open`: Remove retry with the right already absent.** A retried Remove relies on `AuthorizationRightRemove` returning `errAuthorizationDenied` when the right is already absent. That is unverified; the helper and daemon are out of scope.
- **Evidence limit:** physically, removal only restores from `Idle`, because the session ends first. Restoring a non-`Idle` proxy is covered by unit tests only.
- **Accepted:** a pending Remove finished by a later Status shows `NOT_ENABLED` without the removal message.
