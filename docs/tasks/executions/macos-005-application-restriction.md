# Execution: `MACOS-005`

- **Brief:** [Restrict locally mapped macOS applications without affecting unselected applications](../specifications/macos-005-application-restriction.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Muse Code session (2026-09-05)
- **Reviewer:** maintainer (plan review, 2026-09-05); independent review agent (completed-change review, 2026-09-05)
- **Branch:** `feature/macos-005-application-restriction`
- **Worktree:** `~/Projects/Polyglot/posato-macos-005`
- **Updated:** 2026-09-05

## Plan

1. Resolve the two open decisions in the brief with the maintainer, then
   independent plan review.
2. Pipe operation and capability bit, daemon rejection, Kotlin protocol and
   orchestrator in `desktopApp`.
3. Helper observer, requirement matcher, grace state machine, fixed notice.
4. Swift and JVM tests, redaction test, gated physical harness with two
   disposable signed test applications.
5. Physical rows, `./gradlew quality`, completed-change review, record and
   wiki closeout, pull request.

## High-risk plan review

- **Verdict:** `changes-required` (maintainer, 2026-09-05; no second round
  once R1–R3 are recorded here and folded into the plan)
- **Critical or Required findings:** R1 notice mechanism unfeasible as
  planned — helper main thread blocks in `readFrame()`, no run loop, so no
  `NSAlert`: use `UNUserNotificationCenter` from the helper bundle (a) with
  `activateParent()` + `SESSION-002` UI as recorded fallback (b); R2
  orchestrator has no requirement bytes — add an internal `desktopApp`
  accessor over the `TARGETS-003` database by id, harness feeds blobs via
  `codesign`/`csreq` through a test-only resolver seam, control app stays
  out of the set; R3 helper-only op `12` has no daemon state — unknown on
  configure or clear kills the helper and reports `Failed`,
  verified-active is configure success with accepted count equal to set
  size, asserted exactly by the JVM test.
- **Resolution:** revised plan incorporates R1–R3; implementation may start
  after this entry.

## Result

- Implementation complete 2026-09-05 except the maintainer-gated physical
  rows: pipe op `12` + capability bit `8` with daemon rejection, Kotlin
  protocol + `MacOsApplicationEnforcer` + internal requirement accessor,
  helper matcher/observer/grace/notice session, Swift and JVM tests, and
  the gated harness. Both review `Recommended` notes were folded in
  (DB-backed accessor test, brief R3 line).
- The first harness run exposed a real defect after the completed-change
  review: `NSWorkspace.runningApplications` never refreshes without a run
  loop, so applications launched after activation survived. Fixed by
  enumerating process identifiers via `libproc` with per-pid
  `NSRunningApplication` hydration (`ProcessApplicationListing`, covered
  by a live-system unit test); proven on a release helper driven over the
  real pipe (`APP_TERMINATED` for launch-after-configure). `./gradlew
  quality` rerun green after the fix.

## Completed-change review

- **Verdict:** `approve` (independent review agent, 2026-09-05)
- **Critical or Required findings:** none.
- **Recommended findings:** (1) no direct DB-backed test for
  `designatedRequirements` — fixed with a store round-trip test;
  (2) brief `Verification` still named daemon reconciliation — fixed to
  the R3 kill-and-`Failed` rule. Both ride this closeout, no extra pass.
- **Resolution:** all accepted findings corrected across the diff;
  `./gradlew quality` rerun green after the last correction.

## Completed-change re-review (late fixes)

- **Scope:** stale-snapshot re-resolution in `poll`, `destroySpawnedHelper`,
  harness `destroyHelper` simplification, record rows for runs 2–4
  (committed diff `3a157b5..c244436`).
- **Verdict:** `changes-required` (independent review agent, 2026-09-05):
  no pipe/capability violation, no privacy leak; 2 Required, 2 advisory.
- **Triage:**

  | Finding | Class | Decision | Rule | Cost |
  | --- | --- | --- | --- | --- |
  | Failed/truncated pid enumeration reads as mass exit and disarms tracking | Required | accept, fixed | correctness | sizing-query + bounded retry, nil skips the tick; 1 new test |
  | Recycled pid inherits grace state without re-match | Required | accept, fixed | correctness (5s grace) | per-tick re-match of tracked pids, silent drop on doubt; 1 new test |
  | `destroySpawnedHelper` can no-op on grandchildren | Advisory | decline | test seam scope | helper spawns nothing; direct-child kill is exact for this harness |
  | `Failed` wrapping `Success` on count mismatch | Advisory | decline | brief-locked | R3 pins this mapping; changing it needs a brief amendment |
- **Resolution:** both Required findings fixed in source; `poll` split into
  `poll`/`refresh` to satisfy the lint complexity/length gates (no
  suppression). Swift suite 148/148, `swiftFormatCheck`/`swiftLintCheck`
  clean, `./gradlew quality` green, team-signed package rebuilt, reinstalled,
  and deep-strict verified. Physical rerun (run 7) passed all 8 rows on the
  corrected binary after two gate timeouts without maintainer input.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Swift unit tests (strict) | pass | `:macosHelper:swiftTest` via `quality`: 143+ tests incl. 20 new enforcement rows |
| JVM unit tests | pass | `:desktopApp:test`: `ApplicationEnforcementProtocolTest` 7/7, `MacOsApplicationEnforcerTest` 7/7, redaction 1/1, mappings 7/7 |
| `./gradlew quality` | pass | `BUILD SUCCESSFUL`, rerun after the last correction |
| `git diff --check`, suppression, private-data scans | pass | no whitespace errors, no new `Suppress`, no logging or identity in added lines |
| Spike (matching + terminate) | pass | `/tmp/macos005-spike`: `MATCH` on live disposable app, `NO_MATCH` on Finder, graceful quit verified |
| Gated physical harness rows | pass | run 7 `BUILD SUCCESSFUL` on the corrected binary: all 8 rows in `build/verification/macos-005/run.md` (launch-during, notice `NOTIFY_GO` with maintainer-allowed banner, control, running-at-activation, clear, forced-termination, parent-exit, done); canary scan of evidence empty; no stray processes (runs 2–3 abandoned with defects above; runs 5–6 timed out at `NOTIFY_GO` with no maintainer input) |
| Stale-snapshot notice defect (found by run 2) | fixed, unit-proven | run 2 `launch-during` passed but no notification prompt ever appeared: held `NSRunningApplication` snapshots never refresh without a run loop, so `isTerminated` stayed false and `requestAuthorization` never ran (confirmed via `usernoted` log silence). Fix: each poll re-resolves tracked entries against a fresh listing pass; a pid vanished from enumeration counts as terminated. Swift suite 146/146 incl. 2 new regression rows; `swiftFormatCheck`/`swiftLintCheck` clean; `verifyMacOsDevelopmentPackaging` green on the rebuilt bundle |
| Harness self-kill defect (found by run 3) | fixed | run 3 passed control/activation/clear rows, then `destroyHelper` ran `pkill -9 -f PosatoMacOSHelper`, whose pattern also matched the harness launcher command line (it embeds the helper path), SIGKILLing the run itself. Fix: `MacOsHelperClient.destroySpawnedHelper()` kills only the launcher's child via `ProcessHandle`; the `pkill` is gone. Lesson: the harness must be relaunched with the Apple Development identity (`-PposatoMacOsSigningIdentity` + `-PposatoMacOsSyncProvisioningProfile` from `local.properties`); the ad-hoc fallback fails the client team check. `./gradlew quality` green after the fix |

## Blockers and accepted risks

- Brief decisions (grace rule, generic notice) accepted `user-confirmed`
  2026-09-05 and written into the brief.
- Physical rows need the maintainer at the Mac (System Settings approval,
  one notification allow, six harness rows); the pull request follows
  that evidence. The single wiki-log entry lands in the pre-PR commit.

## Final

- **Status:** `ready-for-pr`
- **Outcome:** met (all verification green, re-review findings resolved)
