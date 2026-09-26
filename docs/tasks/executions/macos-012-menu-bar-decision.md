# Execution: `MACOS-012`

- **Brief:** [Menu bar presence decision](../specifications/macos-012-menu-bar-decision.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent agent (plan and decision record); maintainer on PR #87
- **Branch:** `docs/macos-012-menu-bar-decision`
- **Updated:** 2026-09-26 (comparison complete; awaiting review and decision)

## Plan

Git history of this file (`128f79b`) keeps the full reviewed plan:

1. Write the decision as ADR 0009 in the ADR 0008 shape.
2. Compare A, B, and C from code and ADR analysis. Two criteria were added:
   - the preserved update, removal, and ADR 0004 guarantees;
   - reopen and duplicate instances.
3. Measure resources in Tart clones of `primary`, driven through
   `posato-control` and direct `tart exec`. Probes P0 (current build) and P1
   (resident patch with `Tray`) run idle and in a session. P2 (JNI status
   item) is built only if `Tray` fails. P3 (native agent) sets the floor.
   Add a menu capability check and a launch-at-login probe.
4. Write out every amendment, including early-end friction in the menu, the
   not-enforcing states, and the driver extensions for `MACOS-013`.
5. List the open decisions with recommendations. Get an independent review,
   run `quality`, then record the maintainer's decision.

## High-risk plan review

- **Verdict:** `approved` after correction. The first pass returned
  changes-required with four Required findings and no Critical:
  - the preserve boundary for `MACOS-011`, `MACOS-009`, and ADR 0004;
  - a wrong single-instance fact;
  - driver limits without a window;
  - early-end friction and `RESUME_REQUIRED`.

  All four are folded into the plan above. Accepted Recommended items: P2 is
  built only on a capability failure, JVM and helper are sampled separately,
  P0 includes a session, and the login probe has a stated question.

## Result

- **Decision record.** [ADR 0009](../../decisions/0009-macos-menu-bar-presence.md)
  (`Proposed`) holds:
  - the comparison of A, B, and C;
  - the measured figures;
  - the recommendation: A, a resident process with a native status item;
  - the verbatim ADR 0003, ADR 0004, ADR 0008, `DESIGN.md`, README, and
    limits amendments;
  - the `MACOS-013` plan with its evidence table and `posato-control`
    extensions;
  - the open decisions D1-D5.

  Routing (decisions README, wiki index, idea 7) follows acceptance, as it
  did for ADR 0008.
- **Prototypes.**
  - P1 was a patch in a detached throwaway worktree, since removed. The
    window closed without exiting, the session owner and updater ran at
    application scope, and a Compose `Tray`, an activation-policy switch, and
    `SMAppService.mainApp` probes were added.
  - P3 was a Swift status agent, and small `axmenu` and `clickat` guest
    tools opened and inspected menus. All three were built in the
    scratchpad.

  None is tracked.
- **Deviations.**
  - P2 was not built: P1's `Tray` failed accessibility, and P3 answered the
    native `NSStatusItem` question with the same AppKit API.
  - P0 without a session ran in a first clone. `first-install-skip` left the
    daemon unregistered there, and the Retry path ended in a request that did
    not finish. Every other row ran in a fresh clone, enabled through
    onboarding and `vm prompt background`. A P1 window-open row on that clone
    replaces a same-clone P0 idle row.
  - The login item's removal from System Settings was not driven; only the
    separate record is `observed`.
  - A transient 60% helper CPU in the first enforced session is recorded in
    ADR 0009 as out of scope.

## Completed-change review

- **Verdict:** `pending`

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| P0 idle and session, P1 idle and session, P1 window open, P3 in Tart clones of `primary` | pass | 10-minute samples in ADR 0009; run directories under ignored `build/verification/runs/` |
| Blocking with the window closed (P1) | pass | `observe --website http://example.com/ --expect blocked` returned `paused` |
| Expiry with no window (P1) | pass | expiry marker in `local_session_expiry`; menu line "No session active" |
| Reopen through LaunchServices (P1) | pass | same process, window shown, `Foreground` |
| Status-item accessibility | P1 fail, P3 pass | AX tree and `AXPress` through `axmenu` |
| Login item probe (P1) | pass | status `enabled`; separate Open at Login row |

## Blockers and accepted risks

- No blocker. `AC-04` waits for the maintainer's answers to D1-D5.
