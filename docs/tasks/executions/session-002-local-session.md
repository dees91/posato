# Execution: `SESSION-002`

- **Brief:** [Integrate safe local start, enforcement, early end, expiry, failure, and recovery](../specifications/session-002-local-session.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending
- **Reviewer:** pending until assigned; independent plan review required before implementation
- **Branch:** `feature/session-002-local-session`
- **Worktree:** `~/Projects/Polyglot/posato-session-002`
- **Updated:** 2026-09-07

## Plan

1. Resolve the three open decisions in the brief with the maintainer, then
   independent plan review.
2. Common enforcement port and outcomes; JVM adapter over the two macOS
   enforcers; iOS adapter over `IosEnforcement` and `IosSuspendedExpiry`.
3. Session sequencing in the ViewModel and store boundary: commit, apply,
   report; end, clear, report; Retry; relaunch; iOS reconciliation.
4. DI graphs, entry points, host wiring; active-surface enforcement state and
   copy in the accepted vocabulary.
5. Tests, updated `verify-posato` recipes and fixtures, `desktop` and `sim`
   runs, physical Mac and iPhone rows, `./gradlew quality`, completed-change
   review, record and wiki closeout, pull request.

## High-risk plan review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Result

- Not started.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| pending | pending | pending |

## Blockers and accepted risks

- Open decisions in the brief (partial apply, relaunch resume, early end
  after a failed apply) must be accepted before implementation.
- The iPhone is shared with `SYNC-007`; device runs are sequential.

## Final

- **Status:** `active`
- **Outcome:** pending
