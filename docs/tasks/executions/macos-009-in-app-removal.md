# Execution: `MACOS-009`

- **Brief:** [macos-009-in-app-removal.md](../specifications/macos-009-in-app-removal.md)
- **Status:** `complete`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code
- **Reviewer:** independent plan-review agent
- **Branch:** `feature/macos-009-in-app-removal`
- **Updated:** 2026-09-16

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
   - If a non-Remove request is pending, reconcile it first. If that result fails `concludesReconciliation()`, return it marked as not a Remove. Otherwise continue.
   - Send Remove. `shouldReconcileUnknownRequest` also diverts a pending Remove, so a retried press finishes the original request.
   - Tests: a pending Remove, a pending Apply or Enable, and a reconcile that stays unknown.
2. **Port.** Add `MacHelperRemoval` and `suspend fun remove()` to `MacHelperPort` and `MacHelperCommands`; the fakes are updated. The desktop mapping is ordered, and the first match wins:

   | Tuple | Result | Next action in the row |
   | --- | --- | --- |
   | Remove concluded: `Success` + `NotRegistered` + `Idle` | `REMOVED` | helper removed; Posato can move to the Trash |
   | any other `Success` | `REMOVE_AGAIN` | Remove from this Mac again |
   | `UnknownOutcome` of a Remove | `UNCERTAIN` | Remove again finishes that request |
   | earlier request with `!concludesReconciliation()`, or a verification or transport failure | `CHECK_AGAIN` | Check again |
   | exact unlaunchable tuple: `ActionRequired`, service `RecoveryRequired`, phase `RecoveryRequired`, `ManualRecovery`, `Lifecycle` | `CANNOT_START` | the existing registered-but-cannot-start copy |
   | service `ApprovalRequired`, or action `BackgroundApproval` | `APPROVAL_REQUIRED` | allow Posato in System Settings, then remove again |
   | exact local not-enabled tuple: `ActionRequired`, service `NotRegistered` or `UnavailableOrIncompatible`, phase `RecoveryRequired`, `ManualRecovery`, `Lifecycle` | `NOT_ENABLED` | Enable on this Mac, then remove again |
   | action `ManualRecovery` with failure `Integrity` or `Storage` (any phase), or outcome `Conflict` | `PROXY_ATTENTION` | proxy settings need attention; remove again |
   | anything else, such as `RuleRepair`/`Integrity` or `Failure`/`Unavailable` | `REMOVE_AGAIN` | Remove from this Mac again, never Check again |

   Tests pin every tuple above to its result, including the rule failure (`RecoveryRequired` service + `RuleRepair`/`Integrity`) and the daemon failure (`Failure`/`Unavailable`). The reconciliation decision is an internal pure function, because the client talks to a real process.
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
6. **Physical acceptance**, attended on a Developer ID candidate built from this branch, against a captured baseline, and rebuilt whenever a review fix touches removal, mapping, or client wiring. Results are below.
7. **Closeout.** Update the wiki macOS enforcement topic, add one wiki-log entry, run `./gradlew quality`, and get an independent completed-change review.

The write surface is the port, setup state, `MacSetupSection`, session wiring, strings, `DesktopMacHelperState`, `MacHelperCommands`, the `MacOsHelperClient` remove and reconciliation, tests and fakes, and DESIGN.md. No helper, daemon, or wire protocol change is made.

## Result

`./gradlew quality` passed at `641f280`, with `DesktopMacHelperStateTest` (26), `MacOsHelperClientTest` (6), and `MacHelperSetupUiStateTest` (16) green. The physical run was attended on 2026-09-16 on notarized candidate 6, installed over build 4 by quit, replace, and open. `AC-04` ran before `AC-03`, because the Mac was already in the removed state, which saved one enable cycle. Evidence stays in the ignored run directory.

| Check | Result |
| --- | --- |
| `AC-01`, `D1` | Remove appears in the expanded row, is disabled during a session under "Removal is available after the session ends", and confirms destructively before running |
| Removal | No administrator prompt appeared, and the row reported the helper removed and Posato ready for the Trash |
| `AC-02` | The right is absent, the daemon is gone from the system domain, HTTP and HTTPS proxy match the baseline, and the daemon background item changed from `enabled` to `disabled` |
| After removal | A session started but said the helper is not enabled instead of claiming a pause, and a relaunch kept the removed state |
| `AC-04` | Moving the app to the Trash left both records `disabled`; emptying the Trash removed every Posato record; reinstall, enable, and a blocked website all worked |
| `AC-03` subset | With the background item off, Remove refused with the approval notice beside Check again, and the right stayed installed |

## High-risk plan review

- **First pass (`86eabf3`): `changes-required`.**
  - **R1:** the mapping did not match the real Remove tuples. Fixed by the tuple-ordered table.
  - **R2:** "Check again" re-installed a removed right. Now daemon-reached failures point to Remove again.
  - **R3:** Remove could reconcile another pending request. It now reconciles that request first, and only a concluded Remove maps to `REMOVED`.
  - **R4:** the physical checks had no baseline. The baseline, background-item pass criteria, no-prompt observation, and rerun rule are now in step 6.
  - **Second pass (`10fe15a`): `changes-required`.** R2–R4 were confirmed. **R5:** the table rows were ambiguous, so a rule failure matched `PROXY_ATTENTION` and a daemon failure matched `NOT_ENABLED`. Fixed with exact tuple conditions and pinning tests.
  - **Third pass (`e75617e`): `approved`.** R5 was resolved with no new Critical or Required findings.
  - **Recommendations adopted:** confirm-time refusal including a starting session or busy enforcement, no Remove in `RECOVERY_REQUIRED`, observing a post-removal session start, and reusing the existing button style.

## Completed-change review

- **First pass (`bfdaf66`): `changes-required`.** The mapping, wiring, session refusal, and UI were confirmed.
  - **Required 1:** the removal sequencing in the client had no test. It was moved into `removeAfterReconciling`, with tests for an earlier request that stays unknown, one that concludes, and a pending Remove.
  - **Recommended 2, adopted:** Check again stayed beside results that had reached the daemon and could reinstall the rule. It is now hidden while such a result is shown.
  - **Optional 3, adopted:** DESIGN.md now lists `CANNOT_START` and the availability wording.
  - **Optional 4, declined:** starting a session while a removal is in progress waits and then fails truthfully.
  - **Rerun rule:** because these fixes touch removal wiring, candidate 5 is superseded by candidate 6 before the physical run.
- **Re-check: `approved`.** R1 is resolved, with no remaining Critical or Required findings.

## Blockers and accepted risks

- **`open`: Remove retry with the right already absent.** A retried Remove relies on `AuthorizationRightRemove` returning `errAuthorizationDenied` when the right is already absent. That is unverified; the helper and daemon are out of scope.
- **Evidence limit:** physically, removal only restores from `Idle`, because the session ends first. Restoring a non-`Idle` proxy is covered by unit tests only.
- **Accepted:** a pending Remove finished by a later Status shows `NOT_ENABLED` without the removal message.
- **Criteria amended by evidence (2026-09-16):** background item records keep `allowed` after unregistering, so only `enabled` separates a removed helper; the records survive a move to the Trash and disappear when the bundle is deleted; and the Login Items pane shows a removed item until System Settings is reopened.
