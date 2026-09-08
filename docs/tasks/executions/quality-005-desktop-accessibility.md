# Execution: `QUALITY-005`

- **Brief:** [Make the desktop accessibility tree readable and prove the unattended session fixture](../specifications/quality-005-desktop-accessibility.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** implementation agent (2026-09-08)
- **Reviewer:** independent completed-change review pending
- **Branch:** `feature/quality-005-desktop-accessibility`
- **Worktree:** `~/Projects/Polyglot/posato-quality-005`
- **Updated:** 2026-09-08

## Plan

1. Reproduce first: stage the desktop package in both signing modes, launch
   through the driver, and capture `snapshot` output for each. Record whether
   the empty tree appears at all on this machine.
2. If it reproduces, bisect the recorded leads in order: staging mode, the JNI
   window-chrome leaf, and the missing manual-accessibility attribute on the
   target.
3. Make an unaddressable window a named driver failure regardless of the
   diagnosis, with a focused test.
4. Run `session-start-action-required-desktop.json` unattended plus one control
   fixture; record evidence or the blocked clearing condition.
5. Update the feature map with the reproduction and diagnosis, run
   `./gradlew quality`, independent completed-change review, closeout and PR.

## Result

- Pending implementation.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Reproduction in both staging modes | `pending` | |
| Focused test for the unaddressable-window failure | `pending` | |
| `session-start-action-required-desktop.json` unattended | `pending` | |
| Control desktop fixture | `pending` | |
| `./gradlew quality` | `pending` | |

## Blockers and accepted risks

- The failure is not established as permanent. The `SESSION-002` row on
  2026-09-07 recorded an empty tree, while a `TARGETS-003` driver run on
  2026-09-08 drove the same desktop application successfully, and the failing
  run directory was deleted with its worktree. If reproduction fails in both
  staging modes, that finding is the result; do not manufacture a fix for an
  unobserved cause.
- Wave 7 parallel boundary: this task owns `tools/posato-control/**` and
  `.agents/skills/verify-posato/**` and at most the desktop window leaf. It
  must not touch `shared/**/feature/session/**`, `feature/sync/**`, or the
  dependency injection graphs.
- `SESSION-003` in this wave needs a desktop relaunch row and is blocked on the
  same tooling; report the outcome as soon as `AC-01` is answered.

## Final

- **Status:** `active`
- **Outcome:** pending
