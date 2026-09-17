# Execution: `SYNC-012`

- **Brief:** [Session convergence](../specifications/sync-012-session-convergence.md)
- **Status:** `done`; correction review and attended AC-05 complete, merge pending maintainer decision
- **Review tier:** `high-risk`
- **Implementers:** initial implementation agent; Codex PR correction (maintainer-authorized).
- **Reviewers:** independent `sync012_plan_review` (plan); `pr51_correction_review` (Standard correction).
- **Branch:** `feature/sync-012-session-convergence` (implementation; branched from `docs/sync-012-plan`).
- **PR:** #51 (open, review via PR; physical Mac/iPhone matrix complete).
- **Updated:** `2026-09-13`

## Observed starting point

- Baseline `03ba1a2` includes PR #50 and the driver documentation correction. No SYNC-015 parallel-write freeze remains necessary after its merge; preserve its regression behavior.
- `SyncWriter` already supports `StartSession`, `EndSession`, `evaluateSession`, and checkpointed `markTerminalExpiry`; `SyncReducer` owns total-order arbitration, conflict quarantine, future-start filtering, and no fallback from an ended/expired winner.
- `AppleSync.runExchange` currently projects policies only. `SessionViewModel` writes `LocalSessionStore` directly and drives an enforcement coordinator tied to `viewModelScope`; session reads/ticks depend on UI subscription.
- `SqlLocalSessionStore.start` accepts only a start at the current instant and removes other local expiry markers. Remote historical starts need a separate reconciliation path, and synchronized expiry retention cannot use that pruning rule.
- `SessionEnforcementCoordinator.settle` does not replace an already-active identity or clear a remotely ended session. Its generic APPLIED status cannot prove that the desired session identity and deadline are applied.
- `EffectiveSession.Inactive` does not identify why a candidate is inactive; reconciliation must use reducer-owned candidate information to bank expiry without inventing a second ordering implementation.
- `SuspendedExpiryScheduler.readReconciliation` consumes the native cleared marker before returning EXPIRED; scheduling also removes the prior marker. Include this handoff in AC-03 recovery verification, rather than assuming a native observation is already durably banked in common state.
- Historical SESSION-002 execution still lists physical checks as pending. Merged code is an implementation dependency, not evidence that those rows passed; this task measures its own enforcement paths.

## Plan

1. Resolve brief D1–D3 with the maintainer and complete independent High-risk plan review before implementation. Update only affected accepted-authority clauses after acceptance, including linking/session copy and lifecycle rules.
2. Add a common session-sync integration boundary and durable workspace-scoped intent/association storage. Commit local start/end plus their retry intent atomically; local-only commands remain independent of cloud availability. Bound retry metadata and retire acknowledged entries without creating a session history. Keep network waits outside the local command gate, with one documented lock order against the existing sync flight and writer mutex.
3. Drain session intents through the owned writer. Couple a durable authoring receipt to the operation commit, or an equivalent checkpoint-aware idempotency mechanism: a crash after commit but before intent acknowledgement must reuse the existing immutable start, never create a second start with the same session ID. Preserve original timing and command order; do not write around the writer's checkpoint/revision contract.
4. Add reducer-owned candidate information needed by a session reconciler, without changing arbitration. Reconcile after accepted exchange progress and on reopen even if later publication fails; a policy-capacity failure must not suppress an accepted session end. Drain or overlay pending local commands before remote projection can replace them.
5. Bridge local and replica terminal-expiry facts with recoverable ordering. Persist local expiry before inactivity/clear even when a key or writer is unavailable, then bank it through the writer before projecting that session again. Retain referenced facts across row replacement and discard only with their owning state; do not turn these into usage history.
6. Give one host-owned common coordinator responsibility for session transitions and enforcement while the app can run. UI commands, foreground, exchange, and ticks use that owner; subscriptions do not own the work. Reevaluate future starts even while inactive. Close cancels owned work; no new background execution guarantee.
7. Serialize desired-state transitions and tag asynchronous work by session identity/revision. End the ID shown in the confirmation, not whichever session later occupies the row. On replacement, reconcile previous cleanup and new application in order; after waits recheck identity and mandatory end, and ignore stale callbacks. Do not clear a newer session or apply an already-expired one. Retry re-derives the desired state.
8. Integrate D1–D3 and session-change observation into both host graphs and existing UI/recovery flows. Capture the receiving device's local frozen summary when adopting a new session; never replace it on repeated delivery. Keep current-policy reapply and opaque selections local. Cover missing selections, authorization, partial apply, clear failure, and suspended-expiry reconciliation for a replaced ID.
9. Add the next required SQLDelight migration after checking the schema at implementation time; preserve existing active/local-only sessions, frozen sets, expiry facts, policy intents, and replica rows. Update the feature map and reusable session/sync recipes for the new real user path.
10. Run the tests and physical matrix below, complete an independent completed-change review, address Critical/Required findings and rerun affected checks. Close out this record once, update affected wiki synthesis and one wiki-log entry, then prepare the scoped PR.

## High-risk plan review

- **Verdict:** technical plan approved on 2026-09-12, conditional on maintainer acceptance of D1–D3.
- **Critical or Required findings:** none.
- **Evidence:** reviewer inspected both planning documents in full, reducer/writer behavior and tests, session store/coordinator, AppleSync, enforcement ports, native iOS expiry handoff, and the referenced task/design/session authorities; `git diff --check` passed. No application tests were run for this planning-only change.
- **Advisory:** P2 highlighted the native iOS expiry handoff. The AC-03 fault matrix now names that boundary explicitly. Closing P1 #6 required a scoped native addition after all (`acknowledgeReconciliation` beside the now non-consuming read); the earlier "no native redesign" framing is superseded for this boundary. The change stays inside the brief's "focused platform adaptation if necessary" scope: no new wire kind, scheduler, or transport.
- **Resolution:** independent plan review is complete; maintainer approved the plan and accepted D1–D3 on 2026-09-12. The plan-review condition is satisfied; implementation authorized.

## Planned validation

| Boundary | Regression-capable evidence |
| --- | --- |
| Durable commands | Fail local transaction; restart before authoring, after operation commit, and before receipt/intent acknowledgement; assert one immutable start, original deadline, ordered end, and no wait on suspended CloudKit. |
| Projection | Two real cores/writers over fake transport; reversed/duplicate pages, offline concurrent starts, end-before-start, future candidate becoming eligible without new network input, duplicate-ID conflict, and no fallback. A valid remote start with less than five minutes remaining retains its original end rather than using local setup bounds. |
| Local intent races | Accepted remote start versus pending local start/end, confirmation of A after B wins, partial exchange/publication failure, and policy-capacity failure alongside an accepted end. |
| Terminal expiry | Exact end boundary, late expired start, restart/clock rollback, replacement with retained old starts, writer/key unavailable, and failure before each durable expiry step; assert no apply before recovery. Include native iOS EXPIRED observation followed by failure/restart before common persistence, and scheduling a replacement. Any changed handoff must preserve read → durable bank → acknowledgement or equivalent recovery. |
| Enforcement owner | Fake ports suspend apply/clear while a newer session/end arrives; leave Session screen, recreate UI, and close/reopen host. Assert ordered cleanup/application, correct deadline, no stale clear, and truthful failures. |
| Workspace lifecycle | Link with active/ended local state; remove success and failure; re-link to a different workspace; queued commands and stale callbacks cannot cross ownership. Preserve SYNC-015 continuation-reset regressions. |
| Storage upgrade | Open the preceding schema with an active local session and existing policy/replica state, upgrade, and verify preservation plus expiry/intent recovery. |
| Platform and quality | `./gradlew :shared:jvmTest`, `./gradlew :shared:iosSimulatorArm64Test`, affected native/host suites, then final `./gradlew quality`; no new suppression. |

## Physical verification sequence

1. Provision the implementation worktree with the complete ignored `local.properties`, build the driver, and run `doctor` for both targets. Build signed apps after quality restages artifacts. Establish the approved Mac/iPhone fixture through the real app, preserving existing user data and workspace state.
2. Mac starts a bounded session; iPhone consumes it through an ordinary exchange opportunity. Capture both timers plus actual iPhone restriction evidence. End on iPhone, exchange on Mac, and verify both cleanup outcomes. Repeat iPhone start, Mac receive/Resume as required, and Mac early end.
3. Exercise normal expiry on both devices without depending on delivery of a terminal operation. A short foreground run and an iPhone interval meeting the existing scheduler minimum distinguish foreground cleanup from suspended-expiry evidence. Reopen and verify terminal state and cleanup.
4. Hold one peer offline during start/end, reconnect and exchange, and prove an already-ended/expired session is not applied. Exercise relaunch during an active session and the existing Mac Resume/iOS reconciliation behavior.
5. Capture action-required behavior for naturally missing permission/setup; unit injection covers unavailable physical failures. Restore only fixtures changed by the run, finish its sessions, and verify restrictions are cleared. Record each run directory and its limits; a timer screenshot alone is insufficient.

Mac administrator confirmation is a maintainer-attended step. The driver cannot script SecurityAgent; an unattended action-required run does not replace enforcement proof. Use the session and sync feature recipes, no database writes or product hooks, and do not promise a bounded delivery time.

## Result and verification

- D1–D3 were accepted on 2026-09-12 after independent High-risk plan review. The implementation connects format-1 session intent, checkpointed authoring receipts, local/replica terminal facts, identity-bound confirmation, and local enforcement without changing the wire format or policy arbitration.
- Initial corrections include the missing coordinator deletion, command-time start validation, adoption from retained Ended rows, row-less expiry banking, terminal-fact retention across replacement, and recoverable native read/bank/ack. Failed reads bank nothing; ambiguous writer failures never justify marker deletion. Undetermined markers retain their transfer obligation without blocking unrelated sessions.
- Maintainer scope decision (2026-09-13): address all remaining PR threads, including P2 future-start reevaluation. The prior P2 deferral is superseded. A host-owned ticker uses a restored authenticated projection and the existing reducer, without another exchange or a Session-screen subscriber. Pending local intent and workspace gates remain authoritative; local terminal markers precede adoption/apply.
- Final correction (`observed` in source and deterministic probes): one conflated transition drain chooses fresh desired state at execution, rechecks identity/time after waits and before native effects, and records native outcomes while holding port serialization. Generic APPLIED is not identity proof. Unknown enforcement after reopening an Ended row requires cleanup; confirmed empty application and confirmed failed cleanup quiesce. Confirmed cleanup never counts as convergence for a later active identity; the attended receive-after-clear failure was reproduced, fixed in `00ae7a8`, and rechecked through Mac Resume and real blocking. Every applying path requires a successful displacement read and durable terminal bank.
- Native expiry records now use atomic files per identity, retaining compatibility with the legacy single record. A late already-attributed A expiry cannot be overwritten by B's later expiry. Reads prioritize the requested identity; acknowledgement removes only that identity after common persistence. This does not claim a callback-generation guarantee for the pre-existing fixed Device Activity name.
- Regression coverage: stale status during replacement; failed/unreadable displacement; SQL restart before native acknowledgement; same-ID native expiry after clock rollback; Ended restart cleanup; successful empty application quiescence; stale cleanup queued behind a newer apply; retry target loading suspended across replacement; host ticks without Session subscribers or network progress; accepted-state restore after SQL restart; multiple future candidates; pending local commands; removal; terminal-bank failure barring retry apply; native late-write and legacy coexistence.
- Red/green evidence: both original reviewer reproductions fail at correction baseline `56345a6`; the future-start probe fails when its second exchange is removed before local-time reconciliation is implemented. The corrected focused suites pass. Mutation checks also restore unknown-as-cleared, empty-result looping and pre-clear stale identity: exactly the three dedicated regressions fail, then the reviewed sources are restored byte-for-byte and the full gates pass. The later physical receive-after-clear regression also fails before its predicate correction and passes for prompt/non-prompt ports, identity and quiescence afterwards.
- Independent Standard completed-change review (`pr51_correction_review`, 2026-09-13): initial Required findings covered reopened cleanup, empty-enforcement looping and stale clears; the native late-write probe required retaining facts independently. All were corrected with regressions. Final verdict: Approve, no remaining actionable P1/P2 in the correction's scope. The later confirmed-clear correction also received an independent Approve with no P1/P2; all gates then passed again. These are local independent reviews, not a third hosted review request.
- Existing maintainer-approved class-scoped TooManyFunctions exception on SessionTransitionOwner remains unchanged. No new suppression, baseline or quality exception is introduced.

## Completion gates

| Gate | Evidence/status |
| --- | --- |
| Common/JVM and iOS Kotlin | `:shared:jvmTest`: 645 passed; `:shared:iosSimulatorArm64Test`: 633 passed. |
| Swift, host builds and quality | `iosSwiftTest`: 133 tests, 6 expected skips, zero failures; full `./gradlew quality` passed after the last correction. |
| Application driver | Corrected signed builds passed: desktop `build/verification/runs/20260913-154741-e78d/`, iPhone `build/verification/runs/20260913-154800-d836/`. Real user paths, screenshots, snapshots and cleanup are recorded in the matrix below. |
| AC-05 physical matrix | Passed with the maintainer on 2026-09-13: both start/end directions, iPhone background expiry, Mac expiry away from Session, offline/reconnect without observed revival, and fixture restoration. Evidence and revision limits are listed below. |
| PR threads | Correction `0bf5405` was pushed; all five threads received evidence replies and are resolved (two P1 classes plus accepted P2). No open review thread remains. Follow-up `00ae7a8` fixes the attended receive-after-clear finding; AC-05 is now complete. |

## Attended physical evidence (2026-09-13)

All paths below are under ignored `build/verification/runs/`; browser outcomes are `user-confirmed`, while driver trees, screenshots and read-only SQL are `observed`. The controlled domain is example.com, with example.org as the allowed control; original selections and workspace are preserved.

| Row | Evidence and limit |
| --- | --- |
| Mac start → iPhone receive → iPhone early end | `20260913-152912-b2da`, `20260913-152923-6e9e`, `20260913-153340-d765`, `20260913-153636-190d`: both browsers blocked the target and allowed the control, then opened the target after end. Baseline `732024e`; Mac was reopened during receiver preparation. |
| Corrected iPhone start → Mac Resume → Mac early end | `20260913-162251-d46d`, `20260913-162338-098b`, `20260913-162354-5e2c`, `20260913-162533-0c6f`, `20260913-162613-cd81`: `00ae7a8` receives after confirmed cleanup on the same Mac owner, offers Resume, applies after attended authorization, and both browsers confirm blocking followed by cleanup. |
| iPhone suspended expiry and reopen | A real 25-minute session from `20260913-153806-beec`; maintainer put Posato in background and confirmed target access after the deadline before returning to Posato. `20260913-162214-c949` then shows terminal state. This measures the earlier signed build's unchanged native expiry path, not a bounded callback time. |
| Mac expiry away from Session | `20260913-162750-4b27` starts with real blocking; SQL `20260913-162815-cbc1` proves the actual ten-minute deadline. After leaving Session (`20260913-162926-2f6a`), SQL `20260913-163818-6bd2` confirms terminal expiry, and the maintainer confirms browser access. Reopen `20260913-171335-4cd8` stays inactive. |
| Offline/reconnect | Maintainer kept iPhone offline before the final Mac start and through its deadline. After reconnect, `20260913-171302-dfd9` completes exchange and remains inactive; the maintainer confirms browser access before fixture deletion. No revived session was observed. |
| Fixture restoration | `20260913-171440-7e13` removes only example.com; SQL `20260913-171521-20f9` confirms three remaining domains and zero fixture rows. iPhone `20260913-171529-b488` confirms removal, three websites and inactive state. Existing application selections are unchanged; both controlled apps and log captures are stopped. |

## Limits and decisions

- D1 active-only linking, D2 local session preservation on removal, and D3 existing local authorization/Resume remain unchanged.
- Retained terminal facts have no hard offline growth bound; unknown transferability is not evidence for deletion.
- The native expiry reader reads legacy records, but the previous single-slot reader cannot consume new identity files. A rollback must preserve/drain unread terminal facts or use a forward correction; a blind revert is not a proved safe downgrade.
- Ticks run only while the host can execute. Neither timer UI nor unit/Simulator tests prove physical enforcement, suspended delivery or wakeup timing.
- Recipe limits: the initial fixture attempt did not save its input; the attended run used an explicit Add tap. Omitted scenario launch options restarted Mac; continuing steps now explicitly reuse the instance. Repeated duration taps produced ten minutes instead of five, so expiry used the actual read-only SQL deadline. These are evidence limits and recipe corrections, not new timing guarantees.
- Physical browser rows are maintainer-confirmed; device-local application selections were empty and unchanged. This is session-convergence evidence, not a new application-blocking platform matrix or an MVP/release-readiness claim. Mac administrator confirmation remains attended.
