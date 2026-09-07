# CI-003: Require manually dispatched quality before merge

- Record path: recorded; review tier: High-risk because repository merge
  authorization changes. The maintainer explicitly authorized it after enabling Pro.
- Dependencies: existing manual CI workflow and GitHub Actions `Quality` check.
- Outcome: `main` cannot accept a PR without the current head's required CI
  check, including ordinary administrator merges, while CI remains manual-only.

## Boundaries

- Configure only `dees91/posato` branch protection for `main`; inspect existing
  protection/rulesets first and preserve unrelated settings if present.
- Require `Quality` from the observed GitHub Actions app, enforce administrators,
  require PRs with zero mandatory approving reviews, disallow force pushes and
  deletion, and use no bypass list. Do not introduce review-count, signature,
  merge-queue, or automatic workflow requirements.
- Require the PR branch to be current with `main` before merge; after an update,
  explicitly dispatch CI for the new head. Do not merge or probe with real pushes.
- Diagnose the existing macOS proxy test failure with focused, non-mutating
  tests. Report evidence and prevention; application fixes require a separate
  implementation decision if they change native runtime behavior.
- The maintainer subsequently authorized correcting the observed test-pool
  starvation: move blocking test orchestration off Swift concurrency workers,
  retain parallel execution, deadlines, assertions, and native runtime behavior.
  This test-only correction uses Standard completed-change review.

## Acceptance and verification

- Independent plan review before changing GitHub settings; independent completed
  review afterwards. Read back settings and confirm the currently failed PR is
  blocked through read-only API state, without attempting merge.
- Keep manual `workflow_dispatch` and make onboarding/quality authorities
  consistent with server enforcement and its administrator-edit limitation.
- Identify the failing tests and reproduce the relevant failure where feasible;
  distinguish measured causes from remaining hypotheses. No blind timeout increase.
- Verify the correction with the native suite, local aggregate quality, and a
  manually dispatched current-head CI run on macOS 15/Xcode 26.3.
