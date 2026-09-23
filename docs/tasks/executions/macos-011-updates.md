# Execution: `MACOS-011`

- **Brief:** [Accepted macOS update path](../specifications/macos-011-updates.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude; Codex prepared the handoff
- **Reviewer:** independent plan reviewer; implementation review pending
- **Branch:** `feature/macos-011-updates`
- **Updated:** 2026-09-23

## Plan

1. Obtain independent review of this plan against ADR 0008 before implementation. At delegate intake, confirm signing/notarization access and key custody without exposing private material.
2. Review and pin the upstream dependency, then prove admission and cancellation on two locally signed and notarized candidates with a separate test feed. Identify supported positive evidence for installer completion or termination with no pending replacement. Cover both Install replies and keep the maintenance gate closed across concurrency, another instance, and process restarts.
3. Stop and mark delivery blocked if that proof fails. After it passes, integrate the separate updater leaf, Kotlin admission/recovery orchestration, and local consent/settings using the accepted native UI. Confirm cleanup rather than relying on best-effort close.
4. Extend the existing repeated release/packaging flow for the signed feed, nested Sparkle binaries, final notarized DMG, and notices. Validate requests against ADR 0008 and test all required failure/recovery scenarios.
5. Obtain independent completed-change review, run final quality checks, apply reviewed authority amendments, and coordinate public wording and stable publication with `RELEASE-003`. Record the proof and remaining limits; do not publish a stable release from this task.

## Stage 1 safety mechanism

Concrete mechanism for the step 2 proof, revised after the focused plan
re-review below. Evidence:

- `SessionTransitionOwner` calls `EnforcementPort.apply`, which is the only
  enforcement entry on macOS.
- Sparkle 2.10.0 submits its installer as the launchd job
  `<bundle id>-sparkle-updater` (`SUInstallerLauncher.m`) with `LaunchOnlyOnce`.
  It normally runs in the user domain, and in the system domain when the
  bundle is not writable or its ownership does not match.
- The installer exits if the host connection drops before
  `startInstallation`. After that point, it replaces the bundle once the host
  terminates (`AppInstaller.m`).
- With automatic updates off, Sparkle starts an installer only after an
  Install reply through the user driver.

1. **Persistent gate.** Migration 10 adds the singleton table
   `local_update_maintenance`. It records the closed state, the from build, the
   target build, and when the gate closed. One immediate SQLite transaction
   closes the gate. That transaction uses the store's own active-session
   predicate to assert that no session is active.
2. **Apply admission.** A desktop `EnforcementPort` decorator holds one
   admission mutex around each `apply`. Inside the mutex it reads the persisted
   gate every time, and refuses with `FAILED` while the gate is closed. Closing
   and reopening take the same mutex. Every enforcement path goes through this
   port: local Start, Resume, retries, sync-adopted sessions, and helper
   recreation.
3. **Admitted cycle.** Before the first Install reply, the process sets an
   in-memory admitted-cycle flag. It clears the flag only after Sparkle
   reports an abort or completion and the installer is then observed absent.
   Reopening is evaluated only under the admission mutex, and only while no
   admitted cycle exists.
- **Verdict:** `approved` by the same independent agent on 2026-09-23, with no
  Critical or Required findings. This approves the Stage 1 mechanism for
  implementation. The physical experiment still decides feasibility. The cycle state is a necessary condition for
   reopening, never sufficient evidence.
4. **Instances.** Every process holds a shared lock on `instance.lock` through
   one channel. It exits if it cannot obtain that lock. Admission first takes a
   separate admission lock, then upgrades the instance lock to exclusive. It
   fails closed if any peer instance exists.
5. **Confirmed cleanup before the first Install reply.** Steps run in order,
   and any other answer keeps the gate closed with action-required recovery:
   1. Close the gate and set the helper maintenance backstop. The backstop
      refuses `apply` and any non-empty `configure*`.
   2. Reconcile every pending unknown helper outcome. A thrown request counts
      as unknown.
   3. Branch on the service state:
      - **Enabled service:** if `status()` reports Applied without an active
        local session, refuse admission, because a live lease belongs to
        someone else. Otherwise `restore()` must return Success with
        `Phase.Idle`, and `configureApplications(empty)` must return Success.
      - **Not registered only:** read the stored network preferences without
        the daemon, and require that no HTTP or HTTPS proxy is enabled on
        `127.0.0.1`. Posato writes its tuples to the service that was
        primary at Apply time, so the read covers every service from
        `SCNetworkServiceCopyAll`, including disabled services, in every
        network location. Any read failure counts as unknown.
      - **Approval-required or recovery-required:** refuse.
   4. Enter maintenance mode:
      - The helper client refuses every new helper spawn, including status,
        enable, and the application picker.
      - Companion transactions drain.
      - New transactions are refused.
      - The helper and companion processes are confirmed exited.

      Maintenance mode is also derived from the stored gate at startup. It is
      never left while an admitted cycle exists. It ends only once no admitted
      cycle exists and the installer-terminated and bundle-identity evidence
      hold.
      Helper spawns are then allowed again for revalidation, but `apply`
      stays refused until the gate reopens.
6. **Updater leaf.** `libPosatoUpdater.dylib` is separate from the window leaf
   and links the pinned Sparkle.framework. An `SPUUserDriver` adapter forwards
   to `SPUStandardUserDriver`.
   - The adapter gates the first Install reply, the one that can start a
     download and an installer, on asynchronous Kotlin admission.
   - It sends exactly one reply on the main thread: `.install` after
     admission, or `.dismiss` after refusal.
   - At the ready-to-install prompt or the resumable installing stage, the gate
     is already closed. Choosing not to install sends `.skip`, which cancels
     the installer through `SPUCancelInstallation`. Admission is never re-run
     there. At the resumable stage, `.skip` also marks the version as skipped,
     which is a documented limit.
   - If Sparkle aborts or dismisses while admission is still pending, the
     adapter drops the late result.
7. **Positive safe-release evidence.** While the gate is closed, reopening
   requires all of the following:
   - **No admitted cycle.**
   - **Installer terminated.** For the exact pinned label in both launchd
     domains, `launchctl` output has an exact recognized form: "service not
     found" or an explicit not-running state. Any other output counts as
     unknown. A test pins the label format to the vendored framework version.
     A process listing confirms that no `Autoupdate` process runs from this
     bundle.
   - **Bundle identity.** The on-disk bundle at the running path must equal the
     running build. It carries a valid Developer ID signature from the team.
     Its build is one of three: the recorded target build (replacement), the
     from build with the installer terminated (no pending replacement), or a
     newer build (manual recovery).
   - **Service-state revalidation.**
     - **Enabled service:** readiness, Idle, and compatibility revalidate.
     - **Not registered:** the stored-preferences proxy evidence from step 5
       holds. The gate reopens and the service stays disabled.
     - **Any other state:** the gate stays closed.

   Right after relaunch, evidence is re-checked for a bounded period while the
   installer exits. Otherwise the gate stays closed with action-required
   recovery. Updating never enables a disabled service.

Stage 1 does not touch the shared `strings.xml` or the session UI. A refused
apply surfaces as the existing apply failure. Distinct update messaging and
refusing a local Start wait for the rebase after `ONBOARDING-003`.

## Parallel ownership

- Start from `a8bc2c5`, which includes `MACOS-010` and `IOS-004`. The independent updater proof may proceed alongside `ONBOARDING-003`; the wave label introduces no additional dependency on onboarding for that proof.
- Own updater-specific desktop/native code, required admission and helper/session lifecycle changes, and macOS packaging/build integration. Preserve existing onboarding/helper public contracts during the parallel stage; coordinate any required change before editing it.
- Leave onboarding UI and resources to `ONBOARDING-003`. Rebase after its merge before editing `DESIGN.md`, shared `strings.xml`, verification recipes, or the wiki log. If proof work needs one of those files earlier, serialize that edit with its owner first.
- Reserve the physical Mac for notarized update/enforcement experiments. Never run these alongside onboarding's desktop verification; worktrees share installed services and local user data. Confirm the device is available before an attended permission step.

## High-risk plan review

- **Verdict:** `approved` for the bounded initial proof plan by independent Codex agent `review_handoff` on 2026-09-23; no Critical or Required findings.
- The reviewer examined the complete brief and plan against ADR 0008, including both Install replies, atomic admission, confirmed cleanup, concurrency/restart coverage, positive safe-release evidence, and the stop condition.
- Approval covers the proposed experiment, not a concrete mechanism or delivery feasibility. Obtain focused plan re-review before implementing a materially different safety mechanism; completed-delivery review remains required.

- **Focused re-review of the Stage 1 mechanism:** `changes-required` from an
  independent Claude agent on 2026-09-23. It found five Required findings and
  no Critical ones:
  - an admitted download could let the gate reopen before the installer exists;
  - a not-enabled service had no consistent cleanup path;
  - unknown helper outcomes were not reconciled;
  - helper and companion respawn during maintenance was not covered;
  - `launchctl` parsing could fail open.

  All five, plus the recommended fixes for instance lock upgrade, refusal at
  the second reply, abort races, foreign leases, and recovery exits, are folded
  into the mechanism above.
- **Focused re-check:** `changes-required`. R1 and R3-R5 are resolved, and
  `.skip` is confirmed to cancel the installer in 2.10.0. Two Required
  findings remain:
  - Q1: the not-registered proxy evidence must read the stored preferences of
    every service.
  - Q2: reopening needs a branch for a disabled service and a defined end to
    maintenance mode.

  Both are folded into the mechanism above, along with deriving maintenance
  mode at startup and the documented `.skip` limit.
- **Final check:** `changes-required`. Q1 and Q2 are resolved. One new
  Required finding, Q3: maintenance mode could end during an admitted
  download. It is folded in above, and maintenance now ends only once no
  admitted cycle exists.

## Result

- Prepared the brief and delegated execution plan in an isolated worktree. No updater code, dependency, signing key, feed, or release has been created.
- Worktree provisioned from the main checkout's complete ignored `local.properties`; the verification driver builds and its help command runs. Independent plan/handoff review passed.

## Completed-change review

- Handoff documents: approved by independent Codex agent `review_handoff`; no Critical, Required, or advisory findings.
- Review evidence: the agent examined the complete brief and execution plan against the task workflow, quality contract, roadmap, and relevant design/ADR authorities; validated local links and explicit anchors; checked whitespace, conflict markers, portable paths, named source files, and base commit `a8bc2c5`. It ran document checks only, with no application tests.
- Product implementation: pending; the delivery review must examine the actual admission/recovery mechanism and measured privacy behavior.

## Verification

- `./gradlew :posato-control:installDist` passed; installed driver `--help` passed. Configuration copy matches the main checkout and remains ignored.
- Brief and execution-record relative links resolve; both documents are within the task-workflow size guidance. No updater experiment or product verification has run.

## Blockers and accepted risks

- Safe release of maintenance admission remains unproven, as recorded in ADR 0008. The first implementation stage must resolve it or stop delivery.
- Signing/notarization access and update-key custody must be confirmed at delegate intake. The current product retains manual updates until delivery is verified.
