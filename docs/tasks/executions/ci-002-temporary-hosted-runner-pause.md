# Execution: `CI-002`

- **Brief:**
  [`../specifications/ci-002-temporary-hosted-runner-pause.md`](../specifications/ci-002-temporary-hosted-runner-pause.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** `Codex`
- **Reviewer:** `independent Codex reviewer`
- **Branch:** `targets-001-exact-domains`
- **Updated:** `2026-08-28`

## Plan

1. Replace automatic GitHub Actions triggers with manual dispatch while keeping
   the full workflow intact.
2. Record the local aggregate quality gate and restoration condition in the
   accepted development authority and maintained wiki.
3. Validate the workflow, run local quality, and complete an independent
   review before pushing the reversible change.

## Result

- Automatic pull-request and `main` push triggers are removed through the
  accepted pause; the unchanged full workflow remains available by manual
  dispatch.
- Development authorities now require a fresh local aggregate quality pass and
  record the 2026-09-05 restoration condition.

## Completed-change review

- **Verdict:** `approved after correction`
- **Critical or Required findings:** The reviewer warned that removing the only
  automatic status producer would block merges if `Quality` were required by
  branch protection.
- **Resolution:** GitHub's branch-protection and ruleset APIs both returned the
  current-plan `403`, while PR #5 reports `MERGEABLE`. The brief no longer
  asserts that unavailable protection exists, and no visibility or plan change
  is made. The reviewer premise does not apply on the verified repository.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Workflow validation | `pass` | The workflow has only `workflow_dispatch`; the PR-only classifier skips and the `always()` quality condition explicitly accepts manual dispatch. `git diff --check` passes. |
| `./gradlew quality --rerun-tasks` | `pass` | All 92 aggregate tasks executed successfully. |
| GitHub merge-policy inspection | `pass` | Branch-protection and ruleset APIs report that the features require GitHub Pro or a public repository; PR #5 reports `MERGEABLE`. |

## Blockers and accepted risks

- Hosted CI cannot run until Actions minutes or spending become available.

## Final

- **Status:** `done`
- **Outcome:** Automatic hosted CI is paused with local quality as the accepted
  gate and a documented restoration condition.
