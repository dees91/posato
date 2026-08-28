# `CI-002`: Pause automatic hosted CI temporarily

- **Review tier:** `standard`
- **Tier reason:** This is a reversible workflow and governance configuration
  change with no production runtime behavior.
- **Dependencies:** `CI-001`, `QUALITY-001`
- **Integration group:** `PR-DOMAINS`
- **Authority:** explicit maintainer authorization on 2026-08-28

## Outcome

GitHub Actions consumes no automatic pull-request or `main` push minutes
through 2026-09-05 while local aggregate quality remains the merge gate.

## Boundaries

- Retain the complete CI workflow and manual dispatch capability.
- Do not weaken `./gradlew quality` or proportional review, and do not change
  repository visibility or paid-plan settings.
- Restore automatic pull-request and `main` push triggers when hosted minutes
  become available.

## Acceptance

- `AC-01` — Pull requests and pushes do not automatically trigger the workflow.
- `AC-02` — Manual dispatch still runs the full quality job.
- `AC-03` — Development authorities identify the local temporary merge gate
  and restoration condition.

## Verification

- Validate the workflow structure, run `./gradlew quality`, and review the
  completed configuration diff independently.

## Decisions or blockers

- Hosted execution remains unavailable until the account receives more Actions
  minutes or its spending configuration changes.
- GitHub reports that branch protection and repository rulesets are unavailable
  for this private repository on its current plan; the pull request remains
  mergeable without a hosted status.
