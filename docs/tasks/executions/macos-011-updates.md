# Execution: `MACOS-011`

- **Brief:** [Accepted macOS update path](../specifications/macos-011-updates.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** delegated implementer, pending assignment; Codex prepared the handoff
- **Reviewer:** independent plan reviewer; implementation review pending
- **Branch:** `feature/macos-011-updates`
- **Updated:** 2026-09-23

## Plan

1. Obtain independent review of this plan against ADR 0008 before implementation. At delegate intake, confirm signing/notarization access and key custody without exposing private material.
2. Review and pin the upstream dependency, then prove admission and cancellation on two locally signed and notarized candidates with a separate test feed. Identify supported positive evidence for installer completion or termination with no pending replacement. Cover both Install replies and keep the maintenance gate closed across concurrency, another instance, and process restarts.
3. Stop and mark delivery blocked if that proof fails. After it passes, integrate the separate updater leaf, Kotlin admission/recovery orchestration, and local consent/settings using the accepted native UI. Confirm cleanup rather than relying on best-effort close.
4. Extend the existing repeated release/packaging flow for the signed feed, nested Sparkle binaries, final notarized DMG, and notices. Validate requests against ADR 0008 and test all required failure/recovery scenarios.
5. Obtain independent completed-change review, run final quality checks, apply reviewed authority amendments, and coordinate public wording and stable publication with `RELEASE-003`. Record the proof and remaining limits; do not publish a stable release from this task.

## Parallel ownership

- Start from `a8bc2c5`, which includes `MACOS-010` and `IOS-004`. The independent updater proof may proceed alongside `ONBOARDING-003`; the wave label introduces no additional dependency on onboarding for that proof.
- Own updater-specific desktop/native code, required admission and helper/session lifecycle changes, and macOS packaging/build integration. Preserve existing onboarding/helper public contracts during the parallel stage; coordinate any required change before editing it.
- Leave onboarding UI and resources to `ONBOARDING-003`. Rebase after its merge before editing `DESIGN.md`, shared `strings.xml`, verification recipes, or the wiki log. If proof work needs one of those files earlier, serialize that edit with its owner first.
- Reserve the physical Mac for notarized update/enforcement experiments. Never run these alongside onboarding's desktop verification; worktrees share installed services and local user data. Confirm the device is available before an attended permission step.

## High-risk plan review

- **Verdict:** `approved` for the bounded initial proof plan by independent Codex agent `review_handoff` on 2026-09-23; no Critical or Required findings.
- The reviewer examined the complete brief and plan against ADR 0008, including both Install replies, atomic admission, confirmed cleanup, concurrency/restart coverage, positive safe-release evidence, and the stop condition.
- Approval covers the proposed experiment, not a concrete mechanism or delivery feasibility. Obtain focused plan re-review before implementing a materially different safety mechanism; completed-delivery review remains required.

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
