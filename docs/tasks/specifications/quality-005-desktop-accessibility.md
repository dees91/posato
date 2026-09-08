# `QUALITY-005`: Make the desktop accessibility tree readable and prove the unattended session fixture

- **Review tier:** `standard`
- **Tier reason:** Verification tooling and, if the cause is in the
  application, one narrow desktop window change. Reversible, no privilege or
  protocol surface. The recorded path is used because the cause is unknown and
  the outcome must survive as durable evidence for every later desktop row.
- **Dependencies:** completed `QUALITY-002` (driver and accessibility bridge),
  `QUALITY-004` (`--process` selector, `doctor` provisioning state, the
  helper's documented non-inspectable panel), `DESIGN-001` (current desktop
  window chrome), `SESSION-002` (the fixture that could not be proven)
- **Integration group:** `PR-VERIFICATION-ACCESSIBILITY`
- **Authority:** MVP roadmap revision 11 (`QUALITY-005`, wave P3/W3.1b),
  [`AGENTS.md`](../../../AGENTS.md) application-verification rule (a change is
  proven by driving the real application through the driver),
  the [`verify-posato` skill](../../../.agents/skills/verify-posato/SKILL.md),
  and the open item recorded in
  [`docs/wiki/topics/macos-enforcement.md`](../../wiki/topics/macos-enforcement.md)
  ("the desktop driver accessibility-tree investigation")

## Outcome

The driver reads the desktop application's accessibility tree reliably on the
supported Mac, the previously unproven
`session-start-action-required-desktop.json` fixture runs to completion
unattended, and the condition that produced an empty tree is either removed or
recorded as a named, detectable state instead of a silent one.

## Boundaries

- Start by reproducing. The failure is not established as permanent: the
  `SESSION-002` row on 2026-09-07 saw an empty tree while a `TARGETS-003`
  driver run on 2026-09-08 drove the same desktop application successfully, and
  the failing run directory no longer exists. The first deliverable is a
  reproduction or a bounded statement that it cannot be reproduced.
- Investigate the recorded leads before inventing new ones: the staged package
  flips between development-signed and ad-hoc depending on whether
  `./gradlew quality` restaged it; the desktop window is reconfigured through a
  JNI Objective-C leaf that landed after the last known-good desktop run; the
  bridge sets no manual-accessibility attribute on the target; and the
  application element can answer its role while reporting no window children,
  which the bridge currently returns as success with an empty child list.
- An empty tree must never again read as a successful snapshot. Whatever the
  cause, the driver reports a named failure the way the helper's panel already
  reports `PROCESS_NOT_INSPECTABLE`.
- Non-goal: redoing `QUALITY-004`. The `--process` selector, the keyboard
  fallback for the non-inspectable panel, and `doctor` provisioning state stay
  as accepted. Extend `doctor` only if the diagnosis names a checkable
  precondition it does not already report.
- Non-goal: changing the product's appearance. If the window chrome is the
  cause, the fix keeps the accepted `DESIGN.md` appearance or the change is
  escalated rather than applied.
- This task owns `tools/posato-control/**` and
  `.agents/skills/verify-posato/**`, plus at most the desktop window leaf under
  `desktopApp/src/main/{objc,kotlin}/**` if the diagnosis lands there. It must
  not touch `shared/**/feature/session/**`, `feature/sync/**`, or the
  dependency injection graphs, which belong to `SESSION-003` and `SYNC-009` in
  this wave.
- `desktopApp/**/Main.kt` is shared with `SYNC-009`, which adds the graph
  composition root there while this task may change only the window-property
  block. The regions are disjoint and this task merges first, so `SYNC-009`
  rebases onto it.

## Acceptance

- `AC-01` — The cause of an empty desktop accessibility tree is stated with
  evidence, including whether it is environmental, staging-dependent, or
  caused by the application, and which recorded lead it was.
- `AC-02` — A snapshot that yields no addressable window is reported as a
  named, non-zero failure with a message that says what to do, never as an
  empty success.
- `AC-03` — `session-start-action-required-desktop.json` completes unattended
  on this Mac, or the task is recorded as `blocked` with the exact clearing
  condition and no fixture is claimed as proven.
- `AC-04` — The feature map states the reproduction and the diagnosis, so the
  next desktop row does not re-investigate it, and `./gradlew quality` passes
  with no new suppression.

## Verification

- Reproduction attempt on the supported Mac against both staging modes,
  development-signed and ad-hoc, with the snapshot output captured under the
  ignored `build/verification/`.
- A focused test for the new failure path in the bridge wrapper, so an empty
  window list maps to the named error rather than to an empty tree.
- The unattended fixture run end to end, plus one previously passing desktop
  fixture as the control.
- `./gradlew quality`, `git diff --check`, and the scoped secret and path scan.

## Decisions or blockers

- Open: if the diagnosis is that the driver must set a manual-accessibility
  attribute on the target process, that is a change in how the tool addresses a
  running application. Recommendation: acceptable inside this task, because it
  is the same class of change `QUALITY-004` already made for the helper, and
  it stays in the driver rather than in the product.
- Blocker risk: the failing run directory was deleted with its worktree, so the
  exact shape of the empty tree is unrecorded. If reproduction fails in both
  staging modes, close `AC-01` with that finding, keep `AC-02`, and record the
  fixture as unproven rather than manufacturing a fix for an unobserved cause.
