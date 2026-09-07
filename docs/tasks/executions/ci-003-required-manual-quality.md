# CI-003 execution

Status: done

## Accepted host-build coverage correction

| Finding | Class | Decision | Result |
| --- | --- | --- | --- |
| Removed CI host builds left device-only Swift and Release outside local quality | P2 | Explicitly accepted by maintainer | Added unsigned Debug device and Release Simulator builds with matching Kotlin frameworks |

- Standard independent review approved the task graph, script, configuration
  flags, failure propagation, and documentation with no Critical/Required findings.
- Focused `iosHostBuildCheck` passed both builds in 3m 33s; its Debug compiler
  command includes `POSATO_FAMILY_CONTROLS_DEVELOPMENT`. Final `./gradlew quality`
  passed in 1m 8s with warmed local outputs, including both host builds and iOS
  XCTest. Shell syntax and `git diff --check` passed. No hosted rerun is required.

## Latest maintainer decision

- After the hosted run, the maintainer explicitly chose local quality and review
  instead of GitHub CI for now. Independent High-risk plan review approved
  removing only required status-check protection and disabling the CI workflow.
- Removed `main` required status checks, including their strict up-to-date
  setting. API readback preserves PR requirements, administrator enforcement,
  zero mandatory approvals, and force-push/deletion restrictions.
- Disabled the single hosted CI workflow and removed its tracked source. Git
  history preserves restoration; historical runs and repository-wide Actions
  settings are untouched. No merge was performed.
- The earlier scope and results below are historical where superseded here.
- Independent completed-change review confirmed the exact protection delta,
  disabled sole workflow, and consistent authorities with no Critical/Required
  findings. Final local `./gradlew quality` passed in 2m 8s; `git diff --check`
  passed. No hosted run is required under the superseding maintainer decision.

## Plan

1. Inspect current protection and check identity; review the narrow protection plan.
2. Configure and read back `main` protection without merging or testing a push.
3. Reproduce and diagnose the macOS proxy test timeout; report prevention.
4. Update the accepted quality authority and onboarding; complete independent review.
5. Implement the authorized test-only asynchronous boundary, review it, and verify
   local quality plus the current-head macOS 15 CI gate.

## Initial evidence

- GitHub now permits protection queries: `main` is unprotected and no rulesets exist.
- The failed current-head check is named `Quality` and belongs to GitHub Actions.
- The CI failure is in three existing macOS proxy relay tests; a prior focused
  local run and the complete native suite passed, so the timeout cause is not
  established merely by the original run's socket error.

## Result

- Independent plan review approved the narrow protection change.
- Protection was configured and read back: GitHub Actions `Quality`, strict
  up-to-date branch, administrator enforcement, PR with zero required approvals,
  force push and deletion disabled. PR #36 reports `BLOCKED`; no merge or push
  probe was attempted.
- An initial request containing both legacy `contexts` and `checks` was rejected
  without mutation; the documented `checks` replacement alone succeeded.
- Quality authority, development guide, root onboarding, and wiki synthesis now
  reflect the maintainer's superseding Pro/server-enforcement decision.
- Independent completed-change review of protection/docs approved with no open
  Critical/Required findings; reviewer verified live settings and blocked PR.
  `git diff --check` passed. Diagnosis remains outside that completed approval.

## Diagnosis

- Focused tests also pass with the strict cooperative-pool environment setting.
- An explicit native suite run passes all 151 tests. A bounded eight-process
  stress run passes 80 repetitions of the three failing tests (240 executions).
- These runs do not reproduce the CI macOS 15/Xcode 26.3 failure. Local tests
  use macOS 26/Xcode 26.6; no timeout root cause is claimed from green local runs.
- The maintainer approved temporary synthetic connection-stage diagnostics and
  targeted manual CI. A separate diagnostic branch retains the failing test
  assertions and adds stage-only timestamps; its job is not named `Quality`
  and cannot satisfy the merge gate. No runtime fix, test weakening, or blind
  timeout increase was made.
- Two macOS 15/Xcode 26.3 diagnostic runs reproduced the focused three-test
  failure without Gradle compilation load. Stage timestamps show utility work
  starting only after the blocking test socket deadlines release threads.
- In the same runner, a single test passes; serialized execution passes the
  three cases and all 151 native tests. The parallel full suite can also starve
  the semaphore-based lease-renewal test. This establishes test thread-pool
  starvation rather than network latency as the reproduced failure mechanism.
- Prevention: move blocking test I/O and semaphore orchestration off Swift
  concurrency workers and await completion asynchronously. Explicit native-suite
  serialization is a measured interim mitigation, not an implemented change.
  Neither longer socket deadlines nor a blind CI rerun fixes the dependency.
- Diagnostic instrumentation and workflow were reverted in their isolated
  worktree; no diagnostic changes enter the PR. Evidence remains ignored.
- Independent diagnosis review confirmed the trace/control interpretation and
  evidence limits with no Critical/Required findings. The temporary remote
  branch and clean worktree were removed; experiment commits remain locally
  recoverable.

## Permanent correction

- The maintainer authorized the test-harness fix after diagnosis. Blocking socket
  operations, listener startup, and semaphore/condition waits now use a dedicated
  thread bridged by a checked continuation. Test assertions remain in the awaiting
  Swift Testing task. The timer test suspends with `Task.sleep` instead of blocking.
- No proxy runtime code, test assertion, timeout value, or parallel-suite setting
  changed. The existing failing proxy and renewal cases are the regression seams;
  the diagnostic macOS 15 parallel failures provide the red baseline.
- An explicit local native run passes all 151 tests with warnings treated as
  errors. Independent correction review approved all seven touched test files
  with no Critical/Required findings, checked remaining blocking operations,
  and passed five focused proxy, renewal, and timer tests concurrently.
- Adding `async` exposed a formatter/linter brace conflict in the longest test
  signature. Shortening its name fixed the source without an exception; its
  assertions are unchanged. After that correction, full `./gradlew quality`
  passed, including all 151 parallel helper tests and native iOS XCTest.
- The macOS 15/Xcode 26.3 hosted run passed all 151 parallel helper tests in
  3.354 seconds, confirming the starvation correction. The aggregate then failed
  at iOS compilation: its older SDK lacks `approvedWithDataAccess`. Local quality
  passes on Xcode 26.6. That SDK mismatch remains a restoration consideration,
  not a claim of successful hosted quality or a change to authorization behavior.
