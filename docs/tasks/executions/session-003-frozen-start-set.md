# Execution: `SESSION-003`

- **Brief:** [Keep the active session's frozen start set truthful across a relaunch](../specifications/session-003-frozen-start-set.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** implementation agent (2026-09-08)
- **Reviewer:** independent completed-change review pending
- **Branch:** `feature/session-003-frozen-start-set`
- **Worktree:** `~/Projects/Polyglot/posato-session-003`
- **Updated:** 2026-09-08

## Plan

1. `D1` and `D2` answered by the maintainer on 2026-09-08: persist domains and
   the application count; Resume keeps applying the current set and the
   post-relaunch copy is corrected.
2. Extend the session schema and add the next migration; persist the set in the
   same transaction as the session row and clear it on end and expiry.
3. Read the persisted set in the coordinator's `settle` and reconcile paths;
   keep the freeze at start unchanged.
4. Tests: store round trip, relaunch behavior, no leak into the next session,
   pre-upgrade fallback, migration with existing rows intact.
5. Driver relaunch row on the desktop target, `./gradlew quality`, independent
   completed-change review, closeout and PR.

## Result

- Pending implementation.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | `pending` | |
| `commonTest` store and coordinator rows | `pending` | |
| Migration with existing rows | `pending` | automated migration verification is off in the build |
| Driver relaunch row (desktop) | `pending` | |

## Blockers and accepted risks

- `D1` and `D2` are decided; implementation is unblocked. The persisted set
  carries no opaque mapping identifier, so the `A-03` boundary is unchanged.
- The desktop driver's accessibility tree is unproven on this machine, which is
  the subject of `QUALITY-005` in the same wave. If the relaunch row cannot be
  driven here, record it as blocked with that clearing condition rather than
  claiming a pass.
- Wave 7 parallel boundary: this task must not touch `feature/sync/**`, the
  dependency injection graphs, or `tools/posato-control/**`.

## Final

- **Status:** `active`
- **Outcome:** pending
