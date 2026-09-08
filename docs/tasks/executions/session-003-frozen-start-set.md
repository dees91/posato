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

- Persisted the frozen start set as `FrozenStartSet` (exact domains plus the
  application count, redacted string form), carried on
  `LocalSessionStatus.Active`, written atomically with the session row by
  migration `5.sqm` (two nullable columns on `local_session`), and cleared on
  early end and on expiry commit. Malformed bytes fail closed to `CORRUPTION`;
  a missing set on a pre-upgrade row falls back to live targets.
- The coordinator displays the persisted set on the `settle`, `reconcile`,
  and re-apply paths while `apply` requests keep using the current targets
  (`D2`); the active copy now states both halves. No new suppression; two
  Detekt findings were fixed at the source.
- Wave boundary kept: only `shared/**/feature/session/**` plus the session
  schema and migration. Known residual: the Selected-items application-names
  list stays live because `D1` persists the count only.

## Completed-change review

- **Verdict:** `approve` (independent review, no Critical, no Required,
  1 Recommended, 3 Optional observations needing no action)
- **Critical or Required findings:** none
- **Resolution:** the Recommended closeout is this update. The Optional notes
  stand as decided: `toFrozenStartSet` mirrors `toEnforcedSet`, tampered bytes
  on an ended row report `CORRUPTION`, and the caption describes the
  post-re-converge steady state.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` after the last correction | `pass` | worktree run, 197 tasks, `BUILD SUCCESSFUL` |
| `commonTest`/`jvmTest` session suites | `pass` | 15 store, 16 coordinator, 20 ViewModel, 5 frozen-set rows, 0 failures |
| Migration with existing rows | `pass` | v5 simulation keeps policy rev 7, replica and session rows, null fallback |
| Driver relaunch row (desktop) | `pass` | `build/verification/runs/20260908-123255-d78f`; frozen 2-set survives relaunch with Resume, end clears, synthetics removed, `wp.pl` intact |

## Blockers and accepted risks

- `D1` and `D2` are decided; implementation is unblocked. The persisted set
  carries no opaque mapping identifier, so the `A-03` boundary is unchanged.
- The desktop driver worked in this worktree (accessibility and screen
  recording granted); the `QUALITY-005` empty-tree finding did not reproduce
  here.
- Wave 7 parallel boundary kept: no touch of `feature/sync/**`, the
  dependency injection graphs, or `tools/posato-control/**`.

## Final

- **Status:** `done`
- **Outcome:** implemented, verified, and reviewed on `feature/session-003-frozen-start-set`
