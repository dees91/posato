# `MACOS-007`: Recover Mac helper setup without a dead-end retry

- **Review tier:** `high-risk`
- **Tier reason:** Signed service registration, privileged proxy ownership, and recovery after an uncertain IPC outcome.
- **Dependencies:** `MACOS-003`, `MACOS-006`, `ONBOARDING-001`; existing Session This Mac route.
- **Integration group:** One helper-setup recovery PR, including the existing diagnosis commit.
- **Authority:** Maintainer request on 2026-09-11; [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md), [DESIGN.md](../../../DESIGN.md), threat-model `T-07`/`T-08`.

## Outcome

A person can enable or recover Mac helper setup through onboarding or This Mac, with visible progress, an effective next action after failure, and no false readiness or abandoned proxy ownership.

## Boundaries

- Start from the [observed diagnosis](../../wiki/topics/macos-enforcement.md#registered-service-failing-to-launch): launchd bundle-resolution failure plus a blocked post-timeout retry. An older installation is a hypothesis, not an established cause; verify before removing or relocating it.
- Preserve ADR 0004: fixed signed peers and bundled executable, bounded operations, original request identity/input for reconciliation, and confirmed cleanup before unregistering. Never clear pending uncertainty merely to permit a new command; do not recreate the client as a substitute for reconciliation.
- Check Mac setup remains status-only with no pending request. After uncertainty, an explicit retry reconciles that request first; its verified outcome determines the next action. Do not blindly issue Status after an unsuccessful Enable or silently turn checking into Repair/new Enable. Separate recovered status from a new user-authorized intent.
- Registered-but-unlaunchable is not Ready. Keep the supported service lifecycle; no global BTM/launchd reset, arbitrary shell/path IPC, state deletion, broader signing entitlement, automatic installer, or watchdog. A safe manual Apple recovery route is acceptable where ADR 0004 prohibits automatic recovery, but a generic endless retry is not.
- KISS/YAGNI: reuse the shared helper holder, protocol recovery, disclosure rows, notices, and native announcements. Add only the minimum semantic distinction needed for a truthful action; no settings redesign, telemetry, polling, timeout increase, or new dependencies. No helper access on launch/foreground or disclosure expansion before an explicit action.
- While Mac setup runs, show its actual activity and keep onboarding Not now usable. Deferring advances setup without cancelling or repeating the already authorized operation; a late result updates shared readiness without navigation or enforcement. Duplicate operation actions stay disabled. iOS authorization behavior is unchanged.
- Keep iCloud/workspaces, selections, local policy, and active sessions intact. System-service cleanup is distinct from a local-data reset. Do not fix this by deleting app data or changing the accepted Apply/Restore safety contract.

## Acceptance

- `AC-01` — Reproduce and localize the launch failure on the affected Mac before changing its registration. A supported, scoped recovery route then lets the current signed package reach authenticated Ready and Idle after relaunch; demonstrate registration is resolving to that package. If the required route violates ADR 0004 or cannot prove safe cleanup, stop with a concrete blocker rather than report completion.
- `AC-02` — A lost Enable/Status response followed by explicit retry in the same process reconciles the original request identity/input before another intent. Repeated unknown outcomes and unreconciled ActionRequired/RecoveryRequired responses remain bounded and retain the original request; a resolved outcome allows the correct subsequent action. Approval required, incompatible signing, and recovery required remain distinct where their next actions differ. No blind new Enable, exception-only retry loop, or unconditional success claim.
- `AC-03` — Onboarding and This Mac show progress immediately and present only an actionable outcome. Not now works during a delayed Mac call; Summary/Session receive its eventual state without auto-advancing or starting enforcement. UI copy distinguishes uncertainty from confirmed failure, preserves the collapsed Session layout, and does not promise a duration or that restarting fixes service registration. `user-confirmed` (2026-09-11): the collapsed rows stay as they are, and a helper state other than ready is additionally named once as a notice above the session action, so a Mac that can enforce nothing is visible before a session starts.
- `AC-04` — Recovery preserves active/uncertain proxy ownership, unrelated proxy edits, signed identity checks, and authorization boundaries. Healthy service checking does not re-register it; failed cleanup never permits unregister/reset or new Apply. Construction and untouched onboarding still start no helper.
- `AC-05` — Regression tests, native setup/retry evidence, updated design and verification recipes, independent completed-change review, and final `./gradlew quality` pass. Evidence distinguishes physical states from fake-only coverage and confirms no unintended local policy or iCloud changes.

## Verification

- JVM/Swift tests at existing client, adapter, and service-recovery seams: lost reply then retry, failed reconciliation retained across retries, resolved/denied/incompatible outcomes, unchanged healthy registration, and refusal to unregister with unconfirmed ownership. Shared holder tests cover busy guard, deferral, late completion, and unchanged iOS guard; no static-copy/UI unit tests.
- Signed physical Mac through `posato-control`: preserve diagnostic and proxy baselines; exercise the current failure and supported recovery, relaunch, then healthy recheck; capture progress/result plus screenshots and read-only service evidence. Use a short approved session to prove enforcement and exact restoration if lifecycle code changes. Existing approval on this Mac is not proof of approval-required UI; use fakes unless that state occurs naturally.
- Native Mac onboarding deferral and late result, Session recovery, and Simulator first-install regression; use controlled test delays for timeout logic, never add a production UI hook or reset the user's onboarding solely to obtain a screenshot without coordinating that reset.
- Update `DESIGN.md`, affected verify-posato onboarding/session recipes and existing driver consumers when controls change, and the existing wiki diagnosis. Maintain one PR wiki-log entry at implementation closeout; do not append another per correction. Review affected threat-model ownership at closeout.

## Decisions or blockers

- `user-confirmed`: investigate and prepare this fix in the existing diagnosis worktree. UI/retry choices above are the task's recommended implementation contract, not a retroactive claim that the maintainer approved an ADR amendment.
- Registration repair mechanics remain an evidence-dependent implementation step. Before destructive or out-of-band system cleanup, present the exact Posato-only targets, ownership/proxy evidence, and recovery sequence for maintainer approval. Ordinary in-app setup/retry needs no repeated approval. Any need to unregister with unconfirmed ownership, weaken trust, or require a new installation policy triggers an ADR/product decision before that work.
