# `GOVERNANCE-003`: Bound hosted review and CI repetition

- **Review tier:** `standard`
- **Tier reason:** Durable workflow and CI configuration change with no product-runtime behavior.
- **Dependencies:** `GOVERNANCE-002`, `CI-001`
- **Integration group:** Review and CI efficiency
- **Authority:** Explicit maintainer authorization, 2026-08-27

## Outcome

Pull requests retain meaningful review and the full Apple quality gate without
creating an unbounded hosted-review loop or spending macOS runner time on
Markdown-only changes.

## Boundaries

- Limit hosted `@codex review` to one manually requested pass per pull request
  by default; another pass requires an explicit maintainer request.
- Keep human feedback, proportional independent review, and resolution of all
  Critical and Required findings.
- Keep the existing full macOS quality job for every substantive review-ready
  pull-request diff and every push to `main`.
- Use draft pull requests during iteration without allocating a runner; skip
  only the macOS job when a review-ready diff is entirely Markdown.
- Do not add a review bot, custom action, service, dependency, or latest-push
  heuristic.

## Acceptance

- `AC-01` — Active review instructions permit at most one hosted review by
  default and do not require hosted re-review after its corrections.
- `AC-02` — Advisory hosted findings cannot create work or another review pass
  without maintainer acceptance.
- `AC-03` — CI allocates no runner for draft pull requests, skips macOS only for
  an all-Markdown review-ready diff, and fails closed to macOS for other
  review-ready changes or scope-detection failure.
- `AC-04` — Task closeout and review bookkeeping do not require a follow-up
  repository commit after the final substantive push.

## Verification

- Validate the workflow and representative draft, Markdown-only, mixed, and
  `main` classifications; run documentation links, wiki lint, and
  `git diff --check`.
- Obtain one independent completed-change review focused on correctness,
  simplicity, and consistency.

## Decisions or blockers

- The maintainer explicitly rejected recurring hosted review and macOS CI
  cycles; no blocker remains.
