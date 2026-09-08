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

- Rebased onto `origin/main` (PR #40 `SESSION-003`, PR #41 `SYNC-009`); the
  `Main.kt` composition root is untouched, only the window-property block stays
  in scope (unused — no product change was needed).
- `AC-01`: bounded no-repro. Ad-hoc (`20260908-160504-5cff`) and
  development-signed (`20260908-160528-9fb2`) runs both expose a full tree from
  the first readiness wait, so no recorded lead was bisected and no fix was
  manufactured. The `SESSION-002` empty tree does not reproduce on the current
  application.
- `AC-02`: new `DESKTOP_WINDOW_UNAVAILABLE` (exit 4) with message and hint,
  enforced by shared guard `requireAddressableWindow` on both the public
  snapshot path (before subtree selection) and the scenario path;
  `ScenarioRunner.waitFor` tolerates a transient empty tree while polling and
  reports the named failure at the deadline instead of `WAIT_TIMEOUT`.
- `AC-03`: fixture proven unattended (`20260908-162338-ebbd`, 30/30 steps).
  Two earlier runs were operator-confirmed (`authd` authentication seconds
  after the prompt) and landed in the attended active-claim path; the proof
  run shows no authentication and reaches Retry. Fixed one stale exact-text
  expectation (`textContains` against the accepted `SESSION-003` copy).
- Maintainer data unchanged throughout: pre/post-flight `[wp.pl]`, no active
  session, proxy restored, app terminated.

## Completed-change review

- **Verdict:** `approve`, no Critical or Required findings (independent review, read-only; live evidence taken as implementer-reported and checked for consistency).
- **Critical or Required findings:** none.
- **Resolution:** accepted one Recommended advisory (foreign-error propagation test for the new `waitFor` branch — added to `ScenarioWindowWaitTest`, affected checks rerun green); declined none. The Optional zero-timeout note needs no action (no caller uses a zero timeout).

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Reproduction in both staging modes | `done`, no repro | `20260908-160504-5cff` (adhoc), `20260908-160528-9fb2` (development) |
| Focused tests for the unaddressable-window failure | `pass` | `DesktopWindowGuardTest`, `ScenarioWindowWaitTest`, full `:posato-control:test` |
| Public error plumbing | `pass` | named-error envelope `ok:false` + exit 3 demo; new code exit 4 asserted in test |
| `session-start-action-required-desktop.json` unattended | `pass` | `20260908-162338-ebbd`, 30/30 steps, no `authd` authentication |
| Control desktop fixture | `pass` | `add-website-desktop.json` + `remove-website-desktop.json`, `[wp.pl]` intact |
| `./gradlew quality` | `pass` | full aggregate gate green after the last correction |

## Blockers and accepted risks

- The failure is not established as permanent. The `SESSION-002` row on
  2026-09-07 recorded an empty tree, while a `TARGETS-003` driver run on
  2026-09-08 drove the same desktop application successfully, and the failing
  run directory was deleted with its worktree. If reproduction fails in both
  staging modes, that finding is the result; do not manufacture a fix for an
  unobserved cause. The fixture still runs regardless: only its actual failure
  records `blocked`/unproven.
- Wave boundary: this task owns `tools/posato-control/**` and
  `.agents/skills/verify-posato/**` and at most the desktop window leaf. It
  must not touch `shared/**/feature/session/**`, `feature/sync/**`, or the
  dependency injection graphs. `SYNC-009` is merged; its `Main.kt` composition
  root is hands off.

## Final

- **Status:** `done`
- **Outcome:** all four acceptance criteria met; PR #42 opened from `feature/quality-005-desktop-accessibility`.
- **Post-review correction:** accepted the inline P2 (recovery instructions were confined to `ControlException.hint`, which `StepError` drops on the scenario path). The message itself now carries the relaunch sentence; the hint keeps the detail. Covered by message assertions in both new tests; affected checks rerun green. Correction tier: trivial self-check (narrow error-text change, fully asserted).
