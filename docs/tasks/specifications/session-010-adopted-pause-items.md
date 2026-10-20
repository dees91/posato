# `SESSION-010`: A device that joins during a pause enforces it

- **Review tier:** Standard
- **Tier reason:** A change to when one exchange pass captures an adopted
  session's paused items. It keeps the format-1 bundle, the reducer, the
  session record's fields, and the trust boundary. Escalate to High-risk if
  the fix needs a sync format, persisted schema, or reducer change.
- **Dependencies:** `SYNC-018` (merged in #168); release 1.5, wave 4.
  `DOCS-006` and `RELEASE-007` wait for this row.
- **Integration group:** PR-SESSION-ADOPTION
- **Authority:** [Release roadmap](../release-roadmap.md) revision 23, row
  `SESSION-010`; idea 39 in the
  [wiki idea queue](../../wiki/topics/mvp-open-questions.md).
- **Record:** [execution record](../executions/session-010-adopted-pause-items.md)

## Outcome

A device that links to a workspace while another device's pause is running
blocks that pause's websites and apps right away, until the pause ends. It
no longer waits for the next pause.

## Boundaries

- Today one pass runs the session phase before the policy phase
  (`AppleSync.runExchange`). Adoption copies the device's local set once
  (`SessionProjectionReconciler.adoptCandidate` through `captureFrozen`), and
  that happens before `PolicyReconciler` writes the set's websites, so a
  device that has just linked freezes an empty or stale set.
- The fix either captures the adopted session again once, after the same
  pass's policy phase, or captures from the converged projection. Either way
  a later delivery keeps the stored set, as the adoption rule says now.
- A session started on the device itself keeps its own frozen set. Repeated
  delivery of an adopted session never re-captures it after the first
  complete capture.
- A policy-capacity refusal later in the pass must not suppress or delay an
  accepted session end (`AppleSync.kt` comment at the policy phase).
- The fix covers every transport: iCloud links have the same pass order.
- Non-goals: live set edits reaching an already adopted pause, schedule
  occurrences, and any change to how the starting device freezes its set.

## Acceptance

- `AC-01`: An exchange test, written first and shown failing on `main`,
  links a device during a peer's pause with a website set. It shows that the
  adopted session enforces the set's websites in the same pass.
- `AC-02`: With a policy-capacity refusal in that pass, a peer's early end is
  still applied and clears enforcement.
- `AC-03`: On real devices a device that joins during a running pause blocks
  a set website within one exchange: a Linux clone joining an Android pause
  through a folder, and a Mac VM joining through iCloud.

## Verification

- `shared` jvmTest regressions in `AppleSyncSessionConvergenceTest` (link
  during a pause) and a capacity case beside `PolicyReconcilerCapacityTest`.
- `posato-control`: the Android phone or emulator starts a pause, then
  `flow folder join` on the Linux clone, then `observe` a set website as
  blocked. Then the same with two Tart Macs over iCloud (`flow icloud link`).

## Decisions or blockers

- None. Choosing between a second capture and the projection is an
  implementation choice for the plan.
