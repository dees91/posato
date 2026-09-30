# Execution: `NAV-001`

- **Brief:** [nav-001-navigation3.md](../specifications/nav-001-navigation3.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Claude Code agent
- **Reviewer:** independent Claude Code agent (completed-change review)
- **Branch:** `feat/nav-001-navigation3`
- **Updated:** 2026-09-30

## Plan

1. Pin Navigation 3 after checking its JVM and iOS artifacts against Compose
   Multiplatform 1.10.3.
2. Put each destination's screens on a `NavDisplay` whose stack is derived
   from the screen's existing state and whose `onBack` is the top screen's
   explicit Back action, so draft handling does not change.
3. Add the Mac back commands and extend `posato-control` with an iOS edge
   swipe and a VM trackpad swipe.
4. Verify on Tart, the iPad Simulator, and the test iPhone; review; close.

## Result

- Navigation 3 `1.1.1` with navigationevent-compose `1.0.1`. `1.1.2` pulls
  Compose runtime 1.11, which crashed the Mac application at launch with
  Compose UI 1.10.3 (`CompositionLocal LocalHostDefaultProvider not
  present`, `observed`).
- `PosatoNavStack` hosts the shell (destination, About Posato, Licenses,
  license text), Session (overview, duration, review, This Mac setup, early
  end, scheduled early end), Paused items (browser, website editor; search's
  Back to adding is a back handler), and Schedules (list, editor, setup).
  Onboarding stays forward-only (`user-confirmed`).
- Back is off while a start, end, save, or system approval or password
  prompt is in progress, through a disabled child back dispatcher.
- Mac: Command-[ and Escape (Compose dispatches Escape as back), and the
  two-finger swipe between pages through AppKit swipe tracking in the main
  window only (`user-confirmed` 2026-09-29). iOS keeps Navigation 3's slide
  and settles without it under Reduce Motion; the Mac has no transition.
- After maintainer testing through the Tart window with a real trackpad
  (`user-confirmed` 2026-09-30): the swipe first navigated only after
  AppKit's settling animation, which felt late. A gesture-driven slide and a
  fixed 0.5 lift threshold were tried and rejected; the chosen version
  navigates on AppKit's first settling frame, following its decision,
  including speed. Each stacked screen is opaque, after a recording showed a
  transparent screen sliding over another on the iPhone.
- `posato-control`: iOS scenario step `swipeBack`; desktop `swipe-back
  [--cancel]` for a VM. A spike showed that synthetic phased scroll events
  drive AppKit swipe tracking to completion or cancellation, but report the
  whole movement at once.
- Deviation: the brief's draft handling "as `DESIGN.md` defines" matches
  `main`, where editors discard a draft on Back or Cancel without asking;
  back does the same. `DESIGN.md` records the back rule.

## Completed-change review

- **Verdict:** approved after corrections.
- **Required finding:** `posato-control` import order failed ktlint; fixed.
- **Accepted recommendations:** keep the full stack while back is off (the
  first version cut it, which animated forward after Start or Save); draw
  outgoing editors from their last state; block back during a schedule save;
  limit the trackpad swipe to the main window without a sheet or modal
  window; state the onboarding decision in the brief. All in `8c31e4a`.
- **Maintainer review of PR #113 (P2, accepted 2026-09-30):** Escape in a
  focused editor field left the editor and discarded the draft. The first
  Escape in a Posato text or search field now only leaves the field; the
  next goes back. Tart: `mac-esc.log` (30 steps), the schedule name check in
  `mac-setup.log`, and every earlier Mac matrix rerun.
- **Advisory, not taken:** completion from AppKit's end phase instead of the
  gesture amount; a momentum Begin phase in the synthetic swipe.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` at `8c31e4a` | pass | aggregate gate incl. iOS builds |
| Tart primary, fresh clone, `8c31e4a`: shell stack | pass, 46 steps | `build/verification/nav-001/mac-shell.log` |
| Tart: Session and Schedules setup routes, destination switching | pass, 49 steps | `mac-stacks.log` |
| Tart: website editor and search | pass, 60 steps | `mac-targets.log` |
| Tart: back while System Settings approval is pending, control, schedule editor | pass | `mac-setup.log` |
| Tart: duration, review, early end after setup | pass, 53 steps | `mac-session.log` |
| iPad Pro 11-inch Simulator, landscape sidebar and portrait, `8c31e4a` | pass, 44 steps | `ipad-swipe.json`, run `20260929-214759-dc13` |
| Test iPhone (`-t device`), `8c31e4a`: About/Licenses, schedule editor, website editor, root | pass, 36 steps | `iphone-swipe.json`, run `20260929-220627-4576` |

Each Tart matrix drives Command-[ and Escape over VNC, `swipe-back` complete
and `--cancel`, and the explicit Back actions, asserting the screen before
and after. Back on a stack's first screen changes nothing.

