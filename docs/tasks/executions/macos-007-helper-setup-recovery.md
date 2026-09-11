# Execution: `MACOS-007`

- **Brief:** [Helper setup recovery](../specifications/macos-007-helper-setup-recovery.md)
- **Status:** `blocked`; retry and truthful setup UI landed, AC-01 registration repair waits on maintainer-approved out-of-band cleanup
- **Review tier:** `high-risk`
- **Implementer:** Grok 4.6
- **Reviewer:** independent plan reviewer (2026-09-11); completed-change review 2026-09-11
- **Branch:** `docs/mac-helper-startup-diagnosis`
- **Updated:** 2026-09-11
- **Baseline:** rebased onto `origin/main` (`e9467a0`, SYNC-014)

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

- Client/adapter retry reconciles a pending unknown request. Enable no longer
  follows a lost Enable with Status. Unreconciled ManualRecovery keeps the
  original request.
- Native Status/Enable daemon loss returns RecoveryRequired instead of exiting
  the helper. Repair still does not unregister when cleanup is unconfirmed.
- UI adds `UNCERTAIN` and `RECOVERY_REQUIRED`. Onboarding shows progress and
  keeps Mac Not now usable. Restart is no longer described as registration
  repair. Registered-but-unlaunchable uses Check again; it does not tell the
  person to remove Login Items, because that would unregister without Idle.
- Physical Check on the current development package reached
  registered-but-unlaunchable, then uncertainty on a later lost reply. Check
  again started immediately instead of the previous dead-end. Relaunch and
  Session navigation started no helper. iCloud and local websites were
  unchanged.
- AC-01 remaining: BTM parent is still
  `/Applications/Posato-MACOS-004.app/Contents/Helpers/PosatoMacOSHelper.app`.
  launchd stays `EX_CONFIG`. In-app Enable cannot re-point that registration
  without unregister, which ADR 0004 forbids until Idle cleanup is confirmed.
  Maintainer approval is required before removing only that leftover app or
  its Login Item.

## Completed-change review

- **Verdict:** approved after Required fix
- **Critical or Required findings:** one Required: `RECOVERY_REQUIRED` mapping was too broad (proxy leftover, rule repair, and not-found ManualRecovery received Login Items copy)
- **Resolution:** keep the unlaunchable tuple distinct in copy, but Check again only; do not recommend Login Items from the app. Proxy leftover and rule repair stay `UNAVAILABLE`. Client auto-reconcile limited to Enable/Status; Apply/Restore still refuse while a request is unknown.
- **Advisory findings:** declined the broader “never auto-reconcile on the client” preference; setup Enable still reconciles a pending unknown via the adapter.

## Hosted review (PR #49, `3542750`)

| Finding | Class | Decision | Rule | Cost |
| --- | --- | --- | --- | --- |
| Login Items copy after daemon-unavailable Status/Enable without Idle | Required | accept | ADR 0004 confirmed cleanup before unregister; AC-04 | small: Check again only, copy does not unregister |
| Task record marked `done` while AC-01 unmet | Required | accept | AC-01 stop with a blocker rather than report completion | small: status `blocked` |
| Two wiki-log entries in one PR | Required | accept | at most one wiki-log entry per PR | small: collapse to one closeout entry |
| Broad catch maps protocol/integrity failures to daemon-loss recovery | Required | accept | T-07 structured IPC outcomes | small: catch only `PipeFailure.unavailable` |

- **Recommendation after this correction:** one more hosted pass.

## Verification

| Check | Result / evidence |
| --- | --- |
| Diagnosis review | Independent read-only review approved code/evidence distinction at `54343f7`; no tests run |
| Native diagnosis | `build/verification/runs/20260911-mac-helper-diagnosis/` |
| Focused JVM/Swift tests | Pass: desktop helper retry tests, onboarding holder tests, `ServiceRepairWorkflowTests` |
| `./gradlew quality` | Pass |
| Signed desktop package | Pass: `posato-control build -t desktop`, signingMode development |
| Native Session This Mac | Pass: runs `20260911-135440-2c16` (progress), `20260911-135736-6bdb` (recovery copy), `20260911-140000-05b6` (uncertain retry), `20260911-140223-fd20` (relaunch, no helper) |
| Simulator first-install | Pass: `20260911-140336-0038` |
| Approval-required UI | Fake-covered; did not occur naturally |
| Attended Apply session | Not run; daemon forward path extracted, Apply timeout still unknown |

## Blockers and accepted risks

- System registration is shared across development bundles. Do not delete
  `/Applications/Posato-MACOS-004.app` or reset all background items without
  an explicit maintainer decision. Exact Posato-only target: that leftover
  helper bundle and the `app.posato.macos.proxy-settings` Login Item.
- The paired Mac/iPhone setup, iCloud workspace, and local websites were
  preserved. Desktop onboarding deferral during Enable was unit-tested only.
- Threat-model `T-07`/`T-08` reviewed; no control amendment. MACOS-007 uses
  the existing bounded IPC and no-unregister-on-uncertain-cleanup rules.
