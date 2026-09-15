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

- **D1.** Removal is refused during an active session; the row explains that the session must end first.
- **D2.** The removal action lives inside the expanded This Mac row.

## Starting observations

`observed` at `9e7e677`:

- **Helper and daemon.**
  - The helper forwards Remove to the root daemon only when Service Management reports the daemon enabled. Otherwise it answers locally with `unreconciledServiceResponse` (`manualRecovery`, or `backgroundApproval`), without touching the right.
  - The daemon's `performRemove` restores ownership, requires `Idle`, runs `AuthorizationRightRemove`, and verifies the right is absent.
  - On a successful Remove at `Idle`, the helper calls `service.unregister()` (`shouldUnregister`) and reports the post-unregister service state.
  - A lost reply is reconciled through `serviceRecoveryOperation`, which re-registers once if needed and then unregisters again.
  - No administrator prompt is involved.
- **Client.** `MacOsHelperClient.remove()` exists without a caller. `shouldReconcileUnknownRequest` diverts only Enable and Status to a pending unknown request. A Remove issued while a request is pending fails the `check` outside the request's `try`.
- **Port and UI.**
  - `MacHelperPort` has `enable`, `recheck`, and `openApprovalSettings`. Fakes live in `MacHelperSetupUiStateTest`, `OnboardingUiStateTest`, and `DesktopBootstrapCompositionTest`.
  - `MacHelperSetupUiState` runs one activity at a time (`CHECKING`, `ENABLING`).
  - `MacSetupSection` renders one action set per readiness.
  - `SessionOverviewContent` knows `active` from `LocalSessionStatus.Active`.
  - The Remove workspace confirmation is an `AlertDialog` with a destructive confirm and a quiet cancel.

## Plan

1. **Client reconciliation.** Add Remove to `shouldReconcileUnknownRequest`, so a retried Remove finishes the original pending request instead of failing. Add a client test for the Remove case; none exists for this function today.
2. **Port and result.** Add `MacHelperRemoval`, with one value for each outcome in the table below, and `suspend fun remove(): MacHelperRemoval` to `MacHelperPort` and `MacHelperCommands`. `UnavailableMacHelper` returns `FAILED`.

   | Result | Meaning | Row copy (next action) |
   | --- | --- | --- |
   | `REMOVED` | success, not registered, `Idle` | helper removed; Posato can be moved to the Trash |
   | `NOT_ENABLED` | helper not running, cleanup unconfirmed | Enable on this Mac, then remove again |
   | `APPROVAL_REQUIRED` | background item not allowed | allow Posato in System Settings, then remove again |
   | `UNCERTAIN` | reply lost | Remove again finishes that request |
   | `PROXY_RECOVERY` | restore conflict | proxy settings changed outside Posato; check them in System Settings |
   | `FAILED` | anything else, including success with the service still registered | removal did not finish; Check again, never claim success |

3. **Desktop mapping.** `DesktopMacHelperState.remove()` verifies the helper as the other operations do, sends `commands.remove()` on the IO dispatcher, and maps the result:
   - `Success` + `NotRegistered` + `Idle` → `REMOVED`;
   - `UnknownOutcome` → `UNCERTAIN`;
   - `ApprovalRequired` or `BackgroundApproval` → `APPROVAL_REQUIRED`;
   - a local not-enabled answer → `NOT_ENABLED`;
   - `Conflict` or `ProxyRecovery` → `PROXY_RECOVERY`;
   - anything else, including exceptions → `FAILED`.

   Tests cover every mapping and the pending-unknown retry.
4. **Setup state.** `MacHelperSetupUiState` gets:
   - `REMOVING` activity and a `removal` result in `MacSetupPresentation`;
   - `remove(sessionActive)`, which issues no call while a session is active;
   - `REMOVED` sets readiness to `NOT_ENABLED`, so the row offers Enable again;
   - any other check or enable clears the last removal result.

   Tests cover the refusal, the single-flight run, and the readiness after each result.
5. **UI** (DESIGN.md patterns).
   - **Action:** in every checked state except `NOT_ENABLED`, the expanded This Mac options add a quiet error-colored **Remove from this Mac** action.
   - **During a session:** the action is disabled, with the caption that removal is available after the session ends.
   - **Confirmation:** an `AlertDialog` states what happens:
     - proxy settings restored, the administrator rule removed, and the background helper turned off;
     - website and app pauses stop on this Mac until the helper is enabled again;
     - paused items stay saved.
   - **Progress and results:** "Removing the background helper…" while running, the result notice for each outcome, and native announcements.
   - **Wiring:** `SessionOverviewContent` passes `active` and the callback through `SessionScreen`.
   - **Previews:** add the new states.
6. **DESIGN.md.** Amend the This Mac bullet with the removal action, confirmation, progress, refusal during a session, and each result.
7. **Physical acceptance** (verify-posato, attended, Developer ID).
   - **Build.** Build and notarize a candidate from this branch (build number 5) with the MACOS-008 release chain. Install it over build 4 by quit, replace, and open, then check This Mac.
   - **AC-01 and D1.** Start a session and see Remove disabled; end the session, remove, and see the confirmation and progress.
   - **AC-02.** Check the system side independently of the app: `scutil --proxy` at baseline, `security authorizationdb read app.posato.macos.proxy.apply` fails, `launchctl print system/app.posato.macos.proxy-settings` not found, and the Posato items in `sfltool dumpbtm` (maintainer runs it with sudo).
   - **AC-03 physical subset.** Switch the background item off and remove to get `APPROVAL_REQUIRED`. The lost reply, restore conflict, and rule failure are covered by unit tests.
   - **AC-04.** Move Posato to the Trash, find no Posato background item, reinstall the candidate, enable, and block one website.
8. **Closeout.** Wiki macOS enforcement topic, one wiki-log entry, and the ADR 0004 cross-reference if the removal wording needs it. Run `./gradlew quality` and get an independent completed-change review.

The write surface is `MacHelperPort`, `MacHelperSetupUiState`, `MacSetupSection`, `SessionOverviewContent`/`SessionScreen`, strings, `DesktopMacHelperState`, `MacHelperCommands`, `MacOsHelperClient` (reconciliation only), their tests and fakes, and DESIGN.md (This Mac). No helper, daemon, or wire protocol change is planned.

## High-risk plan review

- **Verdict:** pending
