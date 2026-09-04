# Execution: `SESSION-001`

- **Brief:** [Provide one bounded manual session and consolidate repeated UI](../specifications/session-001-session-core.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending
- **Reviewer:** pending
- **Branch:** `feature/session-001-session-core`
- **Worktree:** `~/Projects/Polyglot/posato-session-001`
- **Updated:** 2026-09-04

## Plan

1. Obtain the independent plan review and the maintainer's decisions on the
   five recommendations in the brief (expiry observation, clock semantics,
   product bounds and zero-item start, session table, navigation).
2. Define the session domain: identifier reuse from `SYNC-002`, setup
   validation, the inactive, active, and ended state machine, the terminal
   expiry rule, and a clock port with a fake for tests.
3. Add the SQLDelight migration and store for the single local session and
   its expiry marker, committed atomically and read back fail-closed.
4. Implement the session ViewModel and screens: status summary, setup,
   review with effective local items and action-required reasons, active
   surface, early-end confirmation, and expiry transition; wire both graphs
   and the route between session and Paused items.
5. Consolidate the components repeated by the Paused items and session
   screens into `app.posato.core.designsystem`, keeping the Paused items
   behavior unchanged, and update `DESIGN.md` only from rendered evidence.
6. Run `./gradlew quality` on JVM and iOS Simulator, drive `desktop` and `sim`
   through `verify-posato` with a new sessions feature recipe, complete the
   independent completed-change review, rerun affected checks, and close this
   record with the single wiki-log entry in the closeout commit.

## High-risk plan review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Result

- pending

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| pending | pending | pending |

## Blockers and accepted risks

- None yet; no physical device is required because enforcement and the
  suspended-expiry callback belong to later tasks.

## Final

- **Status:** pending
- **Outcome:** pending
