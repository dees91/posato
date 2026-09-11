# Execution: `MACOS-007`

- **Brief:** [Helper setup recovery](../specifications/macos-007-helper-setup-recovery.md)
- **Status:** `done`
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
  follows a lost Enable with Status. Only a lost reply and the
  registered-but-unlaunchable tuple keep the original request; every
  conclusive answer releases it.
- Native Status/Enable loss of a daemon endpoint that never accepted the
  request returns RecoveryRequired instead of exiting the helper. A deadline
  that expires after the request reached the daemon stays an unknown outcome.
  Repair still does not unregister when cleanup is unconfirmed.
- UI adds `UNCERTAIN` and `RECOVERY_REQUIRED`. Onboarding shows progress and
  keeps Mac Not now usable. Restart is no longer described as registration
  repair. Registered-but-unlaunchable uses Check again; it does not tell the
  person to remove Login Items, because that would unregister without Idle.
- Physical Check on the current development package reached
  registered-but-unlaunchable, then uncertainty on a later lost reply. Check
  again started immediately instead of the previous dead-end. Relaunch and
  Session navigation started no helper. iCloud and local websites were
  unchanged.
- AC-01: leftover copies were removed and Background Items reset. Empty BTM
  reported `SMAppService.notFound`; Check now maps that to not-enabled so
  Enable is offered. After a fresh register and Login Items allow, launchd
  submitted `system/app.posato.macos.proxy-settings` for the current
  development-package helper. This Mac showed Background helper enabled
  (`20260911-161739-e44b`) and again after relaunch (`20260911-161812-45c5`).
  HTTP(S) proxy stayed disabled.

## Completed-change review (notFound → Enable)

- **Verdict:** approved, independent reviewer, 2026-09-11
- **Critical or Required findings:** none
- **Resolution:** `SMAppService.notFound` maps to not-enabled so Check offers Enable; Enable swallows `register()` throws and reads status.

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

## Hosted review (PR #49, `e8943ad`)

| Finding | Class | Decision | Rule | Cost |
| --- | --- | --- | --- | --- |
| `PipeFailure.unavailable` still covers a deadline that expires after XPC dispatch, so an unknown outcome became a local RecoveryRequired answer and the request identity was dropped | Required | accept | ADR 0004 unknown-outcome reconciliation; T-07 structured IPC outcomes | small: separate `unknownOutcome` from `unavailable` in the daemon transport |

- **Recommendation after this correction:** merge. Both hosted passes for this
  pull request are used; a third needs a recorded maintainer decision.

## Completed-change review (PR #49, `47caf0d`)

| Finding | Class | Decision | Rule | Cost |
| --- | --- | --- | --- | --- |
| A reconcile reporting `NotRegistered` never released the request and a reconcile never registers, so Enable became permanently inert | Required | accept | AC-02 no dead-end retry | small: only the unlaunchable tuple keeps the request |
| The retained request made `apply`, `restore` and `configure*` throw `IllegalStateException` out of the enforcement path | Required | accept | AC-02; session start must return a result | covered by the same correction |
| Empty `catch` discards the `register()` error, so a persistent registration failure reads as not enabled | Advisory | decline for now | advisory findings do not expand scope | medium: new failure payload and adapter mapping |
| The reconcile-diversion policy lives in the client and the adapter, and the fake models the adapter | Advisory | decline for now | advisory findings do not expand scope | medium: move the policy to the client, rewrite the fake |
| `UNCERTAIN` and `RECOVERY_REQUIRED` collapse to `mac_setup_attention` in the collapsed summary | Advisory | decline for now | advisory findings do not expand scope | small: two summary strings |
| `RECOVERY_REQUIRED` offers only Check again, which returns the same answer | Advisory | decline for now | advisory findings do not expand scope | medium: repeat detection and escalation copy |
| `NOT_ENABLED` offers Enable and Check again side by side | Advisory | decline | outside this diff | small |
| A Mac with no enforcement can still start a session with only a collapsed row as the signal | Advisory | decline | outside this diff; product decision | medium to large |

- **Resolution:** both Required findings share one cause in
  `concludesReconciliation()`; the correction narrows retention to the
  unreconciled setup tuple, shares that tuple with the adapter mapping, and
  adds classification and re-enable regressions. The remaining
  retain-on-unknown path for a pending Apply predates this change.
- **Recommendation:** the advisory findings are a maintainer scope decision;
  none of them blocks merge.
- **Independent review of the correction:** approved, 2026-09-11, no Critical
  or Required findings. One advisory: `NSXPCConnectionInvalid` can also reach
  an established connection invalidated in flight, so the comment now states
  that accepted bounded risk instead of claiming the connection was never
  established. Reviewer reran the focused desktop and `ServiceRepairWorkflow`
  tests.

## Verification

| Check | Result / evidence |
| --- | --- |
| Diagnosis review | Independent read-only review approved code/evidence distinction at `54343f7`; no tests run |
| Native diagnosis | `build/verification/runs/20260911-mac-helper-diagnosis/` |
| Focused JVM/Swift tests | Pass: desktop helper retry tests, onboarding holder tests, `ServiceRepairWorkflowTests`, reconciliation-classification and delivery-failure regressions |
| `./gradlew quality` | Pass |
| Signed desktop package | Pass: `posato-control build -t desktop`, signingMode development |
| Native Session This Mac | Pass: runs `20260911-135440-2c16` (progress), `20260911-135736-6bdb` (recovery copy), `20260911-140000-05b6` (uncertain retry), `20260911-140223-fd20` (relaunch, no helper), `20260911-161739-e44b` and `20260911-161812-45c5` (Ready after reset, relaunch) |
| Simulator first-install | Pass: `20260911-140336-0038` |
| Approval-required UI | Fake-covered; did not occur naturally |
| Attended Apply session | Not run; daemon forward path extracted, Apply timeout still unknown |

## Blockers and accepted risks

- System registration is shared across development bundles. Leftover copies
  and a Background Items reset were maintainer-approved for AC-01; other
  background items had to be re-allowed. Do not repeat `resetbtm` without a
  new decision.
- The paired Mac/iPhone setup, iCloud workspace, and local websites were
  preserved. Desktop onboarding deferral during Enable was unit-tested only.
- Threat-model `T-07`/`T-08` reviewed; no control amendment. MACOS-007 uses
  the existing bounded IPC and no-unregister-on-uncertain-cleanup rules.
