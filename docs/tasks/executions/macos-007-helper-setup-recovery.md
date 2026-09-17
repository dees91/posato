# Execution: `MACOS-007`

- **Brief:** [Helper setup recovery](../specifications/macos-007-helper-setup-recovery.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Grok 4.6; corrections by Claude Opus 5
- **Reviewer:** independent plan reviewer (2026-09-11); three independent completed-change reviews (2026-09-11)
- **Branch:** `docs/mac-helper-startup-diagnosis`
- **Updated:** 2026-09-11
- **Baseline:** rebased onto `origin/main` (`8403c14`, SYNC-014)

## Plan

1. Provision this worktree from the main checkout's ignored `local.properties`,
   build `:posato-control:installDist`, and read the wiki diagnosis and copied
   evidence before repeating native actions.
2. Reproduce the blocked retry in `DesktopMacHelperStateTest` and the client
   protocol tests, then correct the minimum client/adapter path. Keep the
   unknown-request identity and canonical input until reconciliation is
   conclusive; never let a setup retry supersede a pending Apply or Restore.
3. Localize the registered-service startup failure against ADR 0004 before
   changing registration; work only in `main.swift`, `HelperTransport.swift`,
   `ServiceRepairWorkflow.swift` and existing service-core seams, and keep
   no-unregister-on-unconfirmed-cleanup.
4. Reuse the shared helper holder, `MacHelperPort`, onboarding permission step
   and `MacSetupSection` for progress and truthful recovery actions; derive
   Not now availability from the platform, leaving iOS intact.
5. Update `DESIGN.md` and the affected verify-posato recipes, run focused
   checks then `./gradlew quality`, rebuild the signed package, and verify on
   the physical Mac plus a Simulator first-install regression.
6. Independent completed-change review, resolve Critical/Required findings,
   rerun affected checks, update this record and the existing wiki entry, and
   keep one PR wiki-log entry.

## Result

- Setup retry reconciles a pending unknown request instead of issuing Status or
  a new Enable. Only a lost reply and the registered-but-unlaunchable tuple keep
  the original request; every conclusive answer releases it, so a later Enable
  can register and Apply/Restore are not refused for the rest of the process.
- A helper XPC endpoint that never accepted a Status or Enable returns
  structured RecoveryRequired instead of exiting the helper; a deadline that
  expires after the request reached the daemon stays an unknown outcome.
- An Enable whose `register()` throws and leaves the service unregistered now
  reports a conclusive failure, which the adapter maps to unavailable rather
  than offering the same Enable again.
- The pending-request policy has one owner (`MacOsHelperClient`); the adapter
  keeps only "do not follow a lost Enable with Status", and the test fake models
  the client.
- UI adds `UNCERTAIN` and `RECOVERY_REQUIRED` with their own collapsed-row
  summaries, drops the redundant Check again from not enabled, adds one
  repeated-result sentence offering a Mac restart where the action cannot change
  the answer, and names a non-ready helper once above the session action.
  Onboarding shows progress and keeps Mac Not now usable.
- AC-01: leftover copies were removed and Background Items reset (maintainer
  approved). Empty BTM reported `SMAppService.notFound`; Check now maps that to
  not enabled. After a fresh register and Login Items allow, launchd submitted
  `system/app.posato.macos.proxy-settings` for the current development package;
  This Mac showed Background helper enabled (`20260911-161739-e44b`) and again
  after relaunch (`20260911-161812-45c5`). HTTP(S) proxy stayed disabled.

## Reviews

| Source | Classes | Range | Decision |
| --- | --- | --- | --- |
| Plan review (high-risk, pre-implementation) | approved, 0 Critical/Required | brief + plan | AC-02 clarified for unreconciled ActionRequired |
| Completed-change review | 1 Required | `3542750` | `RECOVERY_REQUIRED` mapping narrowed to the unlaunchable tuple; Login Items copy removed |
| Completed-change review (`notFound`) | approved, 0 Critical/Required | `47caf0d` | `SMAppService.notFound` maps to not enabled |
| Hosted `@codex review` pass 1 | 4 Required | `3542750`…`47caf0d` | all accepted: no unregister without Idle, record `blocked` while AC-01 was unmet, one wiki-log entry, catch only `PipeFailure.unavailable` |
| Hosted `@codex review` pass 2 | 1 Required | `e8943ad` | accepted: a post-dispatch timeout must stay an unknown outcome |
| Completed-change review (PR #49 comments) | 2 Required, 6 advisory | `47caf0d` | both Required accepted (one cause in `concludesReconciliation()`); the six advisory findings accepted as a maintainer scope decision on 2026-09-11 and implemented |
| Completed-change review of the correction | approved, 0 Critical/Required | `ff2e33c` | one advisory taken: the delivery-failure comment now states the accepted in-flight-invalidation risk |

Both hosted passes for this pull request are spent; a third needs a recorded
maintainer decision.

## Verification

| Check | Result / evidence |
| --- | --- |
| Native diagnosis | `build/verification/runs/20260911-mac-helper-diagnosis/` |
| Focused JVM/Swift tests | Pass: client retry and reconciliation classification, adapter mapping, holder repeat memory, `ServiceRepairWorkflowTests`, delivery-failure and enable-outcome regressions |
| `./gradlew quality` | Pass |
| Signed desktop package | Pass: `posato-control build -t desktop`, signingMode development |
| Native Session This Mac | Pass: runs `20260911-135440-2c16` (progress), `20260911-135736-6bdb` (recovery copy), `20260911-140000-05b6` (uncertain retry), `20260911-140223-fd20` (relaunch, no helper), `20260911-161739-e44b` and `20260911-161812-45c5` (Ready after reset, relaunch) |
| Native Session This Mac after the advisory fixes | Pass: `20260911-advisory-mac`, snapshots `01`-`07`. First install with Not now; no helper process before the press or after expanding the row; nothing named above the session action before a read (`01`, `02`). An ad-hoc restaged package then produced the real unavailable state: the collapsed row read attention, a second Check added the repeated-result sentence (`04`, `05`), and collapsing the row moved that state into a notice above the session action (`06`). After rebuilding the signed development package, Check reached Background helper enabled, the quiet Check again kept it, and the notice was absent in both row states (`07`). Relaunch started no helper. The maintainer's database was restored and checksummed |
| Simulator first-install | Pass: `20260911-140336-0038`, rerun after the advisory fixes as `20260911-advisory-sim` |
| Unavailable, its repeated-result sentence, and the non-ready notice | Physically observed in `20260911-advisory-mac` through an ad-hoc restaged package, which cannot reach the signed daemon; no system registration was touched |
| Not enabled, uncertain, recovery required, failed registration | Fake-, preview- and unit-covered only; the helper on this Mac is approved and Ready, and reproducing them needs an out-of-band registration change |
| VoiceOver spoken delivery | Not re-run after the advisory fixes; the ready announcement path is unchanged and the repeated-result sentence was verified only as rendered text |
| Attended Apply session | Not run; daemon forward path extracted, Apply timeout still unknown |

## Blockers and accepted risks

- System registration is shared across development bundles. Leftover copies and
  a Background Items reset were maintainer-approved for AC-01; other background
  items had to be re-allowed. Do not repeat `resetbtm` without a new decision.
- The overview notice names a helper state only after an explicit read, because
  `DESIGN.md` forbids reading helper state before a press. A Mac that has never
  been checked still shows only the collapsed "not checked" row before a session.
- `NSXPCConnectionInvalid` is treated as "the daemon never received the request".
  An established connection invalidated in flight is indistinguishable; it is
  accepted because the daemon side of Status and Enable converges on a retry.
- The paired Mac/iPhone setup, iCloud workspace, and local websites were
  preserved. Desktop onboarding deferral during Enable was unit-tested only.
- Threat-model `T-07`/`T-08` reviewed; no control amendment.
