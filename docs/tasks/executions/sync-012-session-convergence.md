# Execution: `SYNC-012`

- **Brief:** [Session convergence](../specifications/sync-012-session-convergence.md)
- **Status:** `active` (implementation; D1–D3 accepted 2026-09-12)
- **Review tier:** `high-risk`
- **Implementer:** Muse Code implementation agent (plan approved by maintainer 2026-09-12).
- **Reviewer:** independent Codex agent `sync012_plan_review` (plan); completed-change reviewer assigned at implementation.
- **Branch:** `feature/sync-012-session-convergence` (implementation; branched from `docs/sync-012-plan`).
- **Updated:** `2026-09-12`

## Observed starting point

- Baseline `111251f` includes PR #50 and the driver documentation correction. No SYNC-015 parallel-write freeze remains necessary after its merge; preserve its regression behavior.
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
- **Advisory:** P2 highlighted the native iOS expiry handoff. The AC-03 fault matrix now names that boundary explicitly; this adds no separate tooling task or unconditional native redesign.
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

## Result and checks so far

- Planning phase complete (brief, independent plan review with no Required findings, D1–D3 accepted 2026-09-12); implementation in progress on this branch.
- Source inspection covers the reducer/writer, Apple sync orchestration, session store/schema, ViewModel/coordinator, accepted authorities, and verification recipes listed above.
- Documentation self-check passed: local link targets exist and diff whitespace is clean. The planned application tests have since run; see the passes below.
- Implementation debug pass 2026-09-12 (`observed`): the `Inactive` reconciler branch never adopted a remote `Current` candidate, so no peer adoption converged; fixed by adopting only currently eligible starts (never ended/expired/future, never over a mid-flight local command). The owner tick dedup keyed on identity/deadline only, so an Active to Ended transition on one row never settled; fixed by tracking the active kind alongside the tag. A deliberate early end from an adopted row recorded no intent, so the receiving peer's end never converged; fixed in the store and the reconciler seed gate per AC-05 (`end from the receiving peer`), with no-ended-backfill scoping kept in the seed gate (the workspace must already hold the un-ended start). The owner also returned a stale fresh read instead of a failed commit (START_FAILED/END_FAILED unreachable) and cleared an end twice (synchronously plus via settle); both fixed so settle owns the transition exactly once. All temporary debug prints and the temporary probe test are removed.
- Test corrections 2026-09-12 (`inferred`, same pass): the adopted-end store test now expects the propagated end intent per AC-05; the expiry test clears setup enforcement history before the restart phase (same pattern as the replacement test) so the revival check starts clean; the removal test performs the explicit re-link its comments describe (SYNC-015 resets the replica continuation, so the replica is only readable after the fresh link refetches it), asserts exactly the original start is accepted back with no replay, then ends the preserved session before starting fresh (the store refuses start-over-active with ALREADY_ACTIVE) with a double exchange for the round trip. The superseded writer-level `evaluateSession` was removed after migrating its one cancellation-safety test to `sessionCandidate`.
- Deterministic evidence 2026-09-12 (`observed`): full `:shared:jvmTest` green, `:shared:iosSimulatorArm64Test` green. detekt initially reported 16 branch findings; 15 were fixed by refactoring (unused parameters, return counts, seed complexity, intent-log split, dead API removal, DI inlining, `sessionTriggers` fun-to-val, ViewModel helper extraction).
- Suppression decision (`user-confirmed`, 2026-09-12): one class-scoped `@Suppress("TooManyFunctions")` on `SessionTransitionOwner` (24 functions, limit 11) approved by the maintainer after the written justification (single serialized owner per the accepted plan; no sub-11 partition; split-brain hazard). The annotation is applied and its exact-match entry recorded in the `verifyApprovedQualityExceptions` allowlist in `build.gradle.kts`; no other suppression was added.
- `./gradlew quality` green in full on 2026-09-12 (`observed`): ktlint (after autocorrect of branch formatting plus a `-Werror` unused-expression fix in `runExchange`), detekt, the suppression-allowlist gate, iOS Swift tests, host builds, desktop packaging, and all compiled targets/tests.

## Blockers and decisions

- D1–D3 decided (`user-confirmed`, 2026-09-12): D1 link-once-active-only with consent copy, D2 removal preserves the local session while discarding old-workspace intent, D3 remote start via existing port / Mac Resume flow.
- Physical completion requires the provisioned linked Mac/iPhone and attended Mac authorization. Missing hardware or permission is a named blocker, not substitute Simulator evidence.
- Best-effort delivery, local authorization, current-policy reapply, and platform expiry limits remain unchanged. This task does not close MVP-001 or public-release readiness.
