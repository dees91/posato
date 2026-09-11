# Execution: `MACOS-007`

- **Brief:** [Helper setup recovery](../specifications/macos-007-helper-setup-recovery.md)
- **Status:** `active`; brief preparation, implementation not started
- **Review tier:** `high-risk`
- **Implementer:** pending handoff
- **Reviewer:** independent plan reviewer; completed-change reviewer assigned at implementation
- **Branch:** `docs/mac-helper-startup-diagnosis`
- **Updated:** 2026-09-11
- **Baseline:** `54343f7` (diagnosis) on `75ece32` (merged SYNC-011)

## Plan

1. Provision this worktree from the main checkout's complete ignored
   `local.properties` and build `:posato-control:installDist`. Read the wiki
   diagnosis and copied evidence before repeating native actions.
   Refresh the actual machine state; it is shared with other worktrees.
2. Reproduce the blocked retry with existing client/protocol tests and
   `DesktopMacHelperStateTest` (replace the case that expects repeated failure).
   Correct the minimum client/adapter path in
   `desktopApp/.../macos/{MacOsHelperClient,DesktopMacHelperState,MacHelperCommands}.kt`.
   Keep unknown-request identity and canonical input until reconciliation is
   conclusive; cover the client shared with `MacOsSessionEnforcement` so a
   setup retry cannot supersede pending Apply/Restore. Preserve failure semantics
   rather than following Enable blindly
   with Status. Validate with `./gradlew :desktopApp:test`; green means a retry
   can recover without restarting and uncertain repeats cannot author new intent.
   Include ActionRequired/RecoveryRequired returned without completed reconciliation;
   it must not clear the original request merely because it is non-Unknown.
3. Localize registered-service startup before changing it. Inspect the exact
   launchd/BTM relationship and signed bundle using ignored evidence; verify
   registration/authorization/ownership safety against ADR 0004. Work only in
   `macosHelper/.../{main,HelperTransport,ServiceRepairWorkflow}.swift` and existing
   service-core/packaging seams when the evidence requires it. Add regressions
   in `ServiceRepairWorkflowTests`, lifecycle/protocol tests, and signing checks
   as applicable; `./gradlew :macosHelper:swiftTest` must preserve no-unregister
   on uncertain cleanup. Complete a supported scoped physical recovery before
   claiming the startup bug fixed; obtain a decision if the ADR blocks it.
4. Reuse `MacHelperSetupUiState`, `MacHelperPort`, `OnboardingUiState`,
   `OnboardingPermission`, and `MacSetupSection` for progress and truthful
   recovery actions. Adapt `OnboardingScreen` only for needed callbacks/state;
   derive Not now availability from the platform/operation, keeping iOS intact.
   Add holder/adapter tests for deferral, late result, busy guard, and recovery
   outcomes; validate `./gradlew :shared:jvmTest :desktopApp:test`.
   Existing strings and previews are the UI seam; no extra settings panel.
5. Update DESIGN and affected `.agents/skills/verify-posato/features/` recipes;
   change `tools/posato-control` only when an existing fixture/consumer requires
   it. Validate focused checks, then final `./gradlew quality`, then rebuild
   the signed Mac package before native verification. Record Mac progress,
   timeout recovery, deferral, relaunch, and relevant enforcement restoration;
   run the Simulator first-install regression. Coordinate resets/prompts and
   exclusive use of the physical Mac; do not disturb parallel SYNC work.
6. Independent completed-change review, resolve Critical/Required findings,
   rerun affected checks, update this record and the existing wiki entry,
   commit and open one PR. Keep no additional plan file or per-fix wiki entries.

## High-risk plan review

- **Verdict:** approved, independent reviewer, 2026-09-11
- **Critical or Required findings:** none
- **Resolution:** optional clarification of unreconciled ActionRequired/RecoveryRequired
  added to AC-02 and the client regression slice; this implements the existing
  conclusive-reconciliation boundary, not a new scope decision.
- **Evidence:** reviewer inspected brief/plan, ADR 0004 timeout and lifecycle
  clauses, DESIGN, client request/reconciliation, native recovery/lifecycle,
  adapter mapping, and holder/permission ownership. No tests or system actions
  run by the reviewer; baseline tests below were run by the planning agent.

## Result

- Diagnosis is committed as `54343f7`; no product source or system registration
  changed during planning. Branch name may stay as-is during implementation.
- Observed: service registered/allowed but launchd cannot resolve its bundle;
  older helper reference exists, both daemon executables exist. Exact trigger
  remains inferred. Parent-side timeout followed by Status is a dead-end retry.
- Record path is recorded-task, tier high-risk; repo brief/record templates
  take precedence over the planning skill's scratch-plan format.

## Completed-change review

- **Verdict:** pending implementation

## Verification

| Check | Result / evidence |
| --- | --- |
| Diagnosis review | Independent read-only review approved code/evidence distinction at `54343f7`; no tests run |
| Native diagnosis | `build/verification/runs/20260911-mac-helper-diagnosis/`, copied from main at `75ece32`; logs, service state, UI, and one explicit recheck |
| Provisioning | Pass: complete local configuration copied; `:posato-control:installDist` built; `build/verification/macos-007-plan/provision.log` |
| Baseline tests | Pass: 10 `DesktopMacHelperStateTest`, 7 `MacHelperSetupUiStateTest`; `build/verification/macos-007-plan/baseline.log` |
| Repair and changed UI | Not implemented or verified |

## Blockers and accepted risks

- System registration is shared across development bundles; the older-copy
  hypothesis is not permission to delete it or reset all background items.
- The paired Mac/iPhone now contain the maintainer's fresh setup; preserve it.
  Implementation does not require iCloud workspace removal or iPhone reinstall.
- If no ADR-compliant recovery exists for an unlaunchable registered daemon,
  present the exact blocker and needed decision; neither a green UI nor a
  one-off machine reset closes AC-01 by itself.
