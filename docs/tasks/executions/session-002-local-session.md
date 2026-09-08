# Execution: `SESSION-002`

- **Brief:** [Integrate safe local start, enforcement, early end, expiry, failure, and recovery](../specifications/session-002-local-session.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending
- **Reviewer:** pending until assigned; independent plan review required before implementation
- **Branch:** `feature/session-002-local-session`
- **Worktree:** `~/Projects/Polyglot/posato-session-002`
- **Updated:** 2026-09-07

## Plan

1. Resolve the three open decisions in the brief with the maintainer, then
   independent plan review.
2. Common enforcement port and outcomes; JVM adapter over the two macOS
   enforcers; iOS adapter over `IosEnforcement` and `IosSuspendedExpiry`.
3. Session sequencing in the ViewModel and store boundary: commit, apply,
   report; end, clear, report; Retry; relaunch; iOS reconciliation.
4. DI graphs, entry points, host wiring; active-surface enforcement state and
   copy in the accepted vocabulary.
5. Tests, updated `verify-posato` recipes and fixtures, `desktop` and `sim`
   runs, physical Mac and iPhone rows, `./gradlew quality`, completed-change
   review, record and wiki closeout, pull request.

## High-risk plan review

- **Verdict:** `changes-required`, then approved without a second round once
  R1–R4 below are in the plan (reviewer decision 2026-09-07).
- **Critical or Required findings:** R1 desktop driver scenarios collide with
  the unscriptable admin prompt; R2 `status()` has no iOS seam; R3 mid-session
  policy edits vs frozen enforcement undecided; R4 step 7 understates the
  `iosApp.swift` / `mainViewController` wiring.
- **Resolution:** plan revision accepted by the maintainer: R1 desktop split
  into unattended action-required variants plus a marker-file attended row
  (`APPLY_GO` / `ROWS_DONE` / `ABORT` under `build/verification/session-002/`);
  R2 live `status` read in the existing `IosEnforcementProvider.swift`, brief
  surface extended for that file and the `SKILL.md` session lines; R3 effective
  set frozen at Start with next-pause copy, macOS Resume / iOS silent
  re-converge; R4 exact host signature
  `mainViewController(cryptoProvider:applicationMappingsProvider:enforcementProvider:suspendedExpiryProvider:)`.
  Recommended adoptions (browser-first order, prompt-naming copy, JVM
  `status()` semantics, provisioning done) are in the accepted plan.

## Result

- Implemented the common enforcement port, the JVM adapter over both macOS
  enforcers, the iOS adapter over `IosEnforcement` and `IosSuspendedExpiry`
  with a live `status` read, ViewModel sequencing with a frozen start set and
  Retry/Resume, DI and host wiring on both platforms, the accepted-vocabulary
  copy, new sequencing/adapter/redaction tests, the split desktop driver
  recipes with one new action-required fixture, and the wiki sections below.
  No new Swift files, no project-file change, no migration, no `DESIGN.md`
  change. The Detekt `TooManyFunctions` finding moved sequencing into
  `SessionEnforcementCoordinator`; no suppression was added.

## Completed-change review

- **Verdict:** `changes-required` (independent review, no Critical, 4
  Required, 1 Recommended); all Required resolved below and `quality`
  rerun green afterwards.
- **Critical or Required findings:** R1 reconcile raced in-flight apply/clear
  (spurious Resume on JVM, concurrent clear plus apply on iOS); R2 iOS
  schedule failure left restrictions on while reporting failure; R3
  expired-reconciliation clear outcome discarded toward a clean end; R4 no
  test for the iOS silent re-converge half.
- **Resolution:** R1 settle skips reconcile while the view is busy; R2 failed
  schedules clear the restrictions first (fail-closed, copy stays true); R3
  reconcile branches on the clear outcome into `CLEAR_FAILED` with Retry; R4
  `commonTest` covers silent re-apply on relaunch and on the status poll plus
  an `iosTest` for the schedule-failure clear. Recommended R5 (frozen set
  refreezes from live policy after relaunch) recorded as follow-up: the true
  fix needs persisted start-set state owned by the `SESSION-001` contracts.
- **PR review corrections (owner review, 1 P1 + 5 P2, all accepted):** end
  after a failed apply is clean when clear reports unavailable or refused;
  start after `CLEAR_FAILED` clears first; app-only `status()` requires a
  ready helper service; one transient `UNKNOWN` keeps the state, demotion
  after three consecutive; the poll runs fire-and-forget from the ticker so a
  slow helper never stalls the countdown. The dedicated coordinator poll loop
  was reverted: an immortal `viewModelScope` loop hangs `runTest` teardown.
  Polling stays subscriber-driven (re-settle on entry covers foreground).

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` after the last correction | pass | worktree run, 197 tasks, `BUILD SUCCESSFUL` |
| `commonTest`/`jvmTest` session and enforcement suites | pass | `SessionEnforcementTest`, `EnforcementRedactionTest`, existing `SessionViewModelTest` |
| `desktopApp` adapter suite | pass | `JvmSessionEnforcementTest` (order, fail-closed restore, status) |
| `iosTest` adapter suite + Simulator XCTest | pass | `IosSessionEnforcementTest`, new Swift `status` tests, via `quality` |
| `verify-posato` `sim` start, early end, cleanup | pass | `build/verification/runs/20260907-205310-5e5c/` (start), early-end run, empty policy after cleanup; active snapshot shows the attention notice with Retry |
| `verify-posato` desktop action-required fixture | blocked | empty desktop accessibility tree in this environment (rendered UI confirmed by screenshot, run `20260907-205834-a335`); needs maintainer eyes |
| Physical Mac rows (Safari, disposable app, early end, expiry, proxy baseline) | pending | maintainer-attended, needs admin prompt confirmation |
| Physical iPhone rows (Screen Time, presentation, force-quit expiry, reopen) | pending | sequential with `SYNC-007`, needs the shared iPhone |

## Blockers and accepted risks

- The three brief decisions are accepted (`user-confirmed`): fail-closed
  partial apply, Resume on macOS relaunch, confirmation kept for early end
  after a failed apply.
- The iPhone is shared with `SYNC-007`; device runs are sequential.
- The desktop driver sees an empty accessibility tree on this machine while
  the app renders correctly; the unattended desktop fixture is committed but
  unproven here, and the attended rows need the maintainer at the Mac.
- Accepted residuals: a JVM apply failure followed by a failed browser
  restore, or an iOS schedule failure followed by a failed restriction clear,
  can leave restrictions on behind an attention state; the status poll and the
  relaunch paths converge later states, and the frozen set after relaunch
  reflects live policy (R5 follow-up above).

## Final

- **Status:** `active`
- **Outcome:** pending maintainer-attended rows, completed-change review, and PR
