# Execution: `DESIGN-001`

- **Brief:** [MVP design adoption](../specifications/design-001-mvp-design-adoption.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Codex
- **Reviewer:** Independent completed-change agent (`design_adoption_review`)
- **Branch:** `feature/mvp-design-adoption`
- **Updated:** `2026-09-07`

## Plan

1. Adapt the complete product design system and native shell from the frozen reference.
2. Test and implement bounded website/URL batches, durable submission feedback,
   automatic group activation, and the small session ViewModel adaptations.
3. Replace Session and Paused items presentation, preserving real service contracts.
4. Adapt native scrolling, scenarios, and verification skill recipes to the new UI.
5. Run focused and aggregate checks, native visual/interaction verification,
   independent completed-change review, and reconcile the accepted authorities.

## High-risk plan review

- **Verdict:** Approved by the maintainer through the implementation request.
- **Decision:** The maintainer explicitly replaces the separate agent plan review
  for this task; independent completed-change review remains required.

## Result

- Product design-system adoption, platform shells, real Session/Paused items
  screens, batch acknowledgements, automatic group activation, and the native
  driver/skill adaptations are implemented. The prototype remains unchanged.
- Aggregate `quality` passed after the final source correction. A full
  `--rerun-tasks` pass covered build changes; subsequent corrections used the
  normal aggregate gate. No quality exception was added.
  Shared JVM tests: 351 passed. Driver tests: 87 passed. iOS test/build targets,
  native packaging checks, formatting, Detekt, and skill validation passed.
- Development-signed desktop and physical iPhone builds passed; the physical
  app installed and launched. Native Mac checks passed for batch entry, long-list
  scrolling in both directions, search, editor draft retention, save/cancel,
  menus, read-only selection details, and cleanup. Native application selection,
  cancellation, restart persistence, and removal preserved the existing group name.
- Simulator checks passed for batch entry, both scrolling directions, search,
  draft retention, menus, and cleanup. Mac and Simulator passed real session
  start, restart, early end, and five-minute expiry with database read-back.
  Simulator light/dark, increased contrast, and the largest Dynamic Type size
  were inspected; font-size changes required relaunch for this verification.
  This is not a full accessibility or device-matrix certification.
- Independent review found no Critical/Required defect and one advisory about
  a rejected batch leaving its UI acknowledgement pending. The scoped correction
  and retry regression passed, and follow-up review confirmed no open findings.
  Final application-row extraction was also independently checked.
- Physical iPhone checks passed for the native picker, saved selection after
  restart, cancellation, and maintainer-assisted restoration of the original
  selection. Real session start, restart, early end, and five-minute expiry
  passed. Website edits and deletion were read back after real relaunches.
- Physical keyboard checks found duplicate IME subtraction in the root shell.
  Removing redundant padding fixed the collapsed editor. Domain editing now
  uses a URI keyboard without autocorrection. The maintained editor scenario
  covers visible controls, draft retention, Cancel, persistence, and cleanup;
  it waits for mutations to finish before restarting. Final native runs passed
  on Mac, Simulator, and iPhone. Independent follow-up review approved the
  correction; a suspected sheet issue was withdrawn after the enclosing dialog
  boundary and successful physical search were verified.
- All verification-created websites and application choices were removed.
  Existing user data and group names were preserved; no database reset,
  captured-token injection, or physical-device data reset was used.

## Resolved blocker and evidence limits

Physical iPhone XCUITest failed twice before the first scenario step with
`Timed out while enabling automation mode`. The maintainer returned and
unlocked the phone; subsequent native scenarios passed. Family Controls choices
required the maintainer, as documented by the verification skill.

There is no remaining implementation blocker. Full assistive-technology,
extreme-size, and measured contrast coverage is not claimed. Synchronization
and session-driven enforcement remain outside this presentation adoption.
The delivery remains one coherent review boundary; merge requires maintainer
authorization independently of these local checks.
