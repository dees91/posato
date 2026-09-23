# Execution: `ONBOARDING-003`

- **Brief:** [Continuous onboarding entry](../specifications/onboarding-003-continuous-entry.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** delegated implementer, pending assignment; Codex prepared the handoff
- **Reviewer:** independent agent for handoff review; implementation review pending
- **Branch:** `feature/onboarding-003-continuous-entry`
- **Updated:** 2026-09-23

## Plan

1. Inspect `WebsiteStep`, `OnboardingPage`, submission receipts, and saved-policy reads. Keep field focus after accepted entry and keep Continue reachable with keyboard insets.
2. Present the saved total separately from per-submission feedback and arrange expanded Mac permission actions using the existing design tokens.
3. Test any changed state behavior and drive the accepted onboarding paths on Mac, iPhone, and iPad. Preserve local data and clean up synthetic entries.
4. Obtain independent completed-change review, resolve Required findings, run final quality checks, and close the record and wiki entry before the final substantive push.

## Parallel ownership

- Start from `a8bc2c5`. Own `OnboardingSteps.kt`, `OnboardingPage.kt`, `OnboardingPermission.kt`, and related onboarding state/tests and resource entries as needed.
- Keep `MacHelperPort`, helper readiness/lifecycle contracts, session enforcement, Gradle/Xcode configuration, and navigation ownership unchanged. Escalate a necessary shared-contract change before editing it.
- Integrate onboarding first. Its implementer owns the first changes to `DESIGN.md`, `strings.xml`, verification recipes, and the wiki log. `MACOS-011` rebases after this merge before editing those shared files.
- Run native verification one task at a time on each target. A worktree does not isolate the installed app, helper, or desktop database from another run.

## Result

- Prepared the brief and delegated execution plan in an isolated worktree. Product implementation has not started.
- Worktree provisioned from the main checkout's complete ignored `local.properties`; the verification driver builds and its help command runs. Independent handoff review passed.

## Completed-change review

- Handoff documents: approved by independent Codex agent `review_handoff`; no Critical, Required, or advisory findings.
- Review evidence: the agent examined the complete brief and execution plan against the task workflow, quality contract, roadmap, and relevant design/ADR authorities; validated local links and explicit anchors; checked whitespace, conflict markers, portable paths, named source files, and base commit `a8bc2c5`. It ran document checks only, with no application tests.
- Product implementation: pending; review the completed diff and actual verification before marking this task done.

## Verification

- `./gradlew :posato-control:installDist` passed; installed driver `--help` passed. Configuration copy matches the main checkout and remains ignored.
- Brief and execution-record relative links resolve; both documents are within the task-workflow size guidance. No product verification has run for this task.

## Blockers and accepted risks

- No known prerequisite blocker. Assign an implementer and schedule native-target access before implementation verification.
