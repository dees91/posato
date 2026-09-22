# Execution: `MACOS-010`

- **Brief:** [macos-010-macos-update-path.md](../specifications/macos-010-macos-update-path.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Codex
- **Reviewer:** independent Codex agent
- **Branch:** `docs/macos-010-update-path`
- **Updated:** 2026-09-22

## Plan

1. Compare the three update paths against current code, accepted authorities,
   and primary Sparkle, Apple, and GitHub sources.
2. Record the maintainer's selected path, exact proposed authority and public
   wording changes, and the bounded `MACOS-011` delivery plan.
3. Update wiki routing, obtain completed-change review, run the required local
   quality gate, and close the record before the final substantive push.

## High-risk plan review

- **Verdict:** approved for discovery before the comparison at `7da2ea2`;
  the focused review of the selected Sparkle plan also approved discovery.
- **Required finding:** cancellation callbacks do not prove that Sparkle's
  external installer has stopped. Reopening enforcement on that evidence can
  race a later bundle replacement.
- **Resolution:** require positive safe-release evidence, including after
  crash/relaunch, as the first delivery go/no-go experiment. Resolved in the
  delivery specification; runtime feasibility remains unverified.

## Result

- [ADR 0008](../../decisions/0008-macos-update-delivery.md) compares the three
  paths and records the accepted Sparkle direction, consent, release hosting,
  and signing-key custody.
- Exact architecture, lifecycle, privacy, and availability amendments are
  proposed for verified delivery. The separate `MACOS-011` plan starts with
  installation admission, cancellation, and restart proof.
- Wiki routing and synthesis reflect this decision. Current application
  behavior and published policy remain unchanged. No scope deviation.

## Completed-change review

- **Verdict:** approved; no Critical, Required, or advisory findings.
- The independent agent reviewed the complete documentation diff against
  `origin/main`, based on `371f9f5`, before this result-only closeout. It checked
  accepted authorities, current helper cleanup and JNI code, public policy,
  pinned Sparkle lifecycle sources, and GitHub release-asset behavior.
- Hosted review omitted under the documentation-only review rule.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `:posato-control:installDist` | pass | Existing worktree provisioned from the canonical ignored local configuration |
| Relative Markdown links | pass | All 139 local targets in the final staged Markdown files resolve |
| `git diff --check origin/main` | pass | Complete documentation diff, including the staged additions |
| `./gradlew quality` | pass | Reviewed working tree based on `371f9f5`, before result-only closeout; completed in 5m 2s; ignored `build/verification/macos-010-quality.log` |

## Blockers and accepted risks

- `MACOS-011` is not started. Its cancellation and recovery proof is a delivery
  gate, not a claim established by this documentation task.
