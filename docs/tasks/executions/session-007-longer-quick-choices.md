# Execution: `SESSION-007`

- **Brief:** [session-007-longer-quick-choices.md](../specifications/session-007-longer-quick-choices.md)
- **Status:** `active`: implemented; Mac VM run and independent review pending
- **Review tier:** `standard`
- **Implementer:** Claude Code agent
- **Reviewer:** independent agent, after implementation
- **Branch:** `task/session-007-longer-quick-choices`
- **Updated:** 2026-10-09

## Plan

1. Record `D1` = A and `D2` = A (`user-confirmed`, 2026-10-09; see the
   brief) in `DESIGN.md` and the sessions feature map.
2. Write the end-of-day resolution test first, for the daylight-saving and
   5-minute edges. Then resolve **Until end of day** as an end time through
   `SessionSetup.validateEndTime`, and widen the quick choices on both hosts.
3. Update `posato-control flow session` and the scenarios only where the
   labels or the Review text change.
4. Verify in a Mac VM, including a guest clock set near midnight, and on the
   test iPhone. Then run the Standard review.

## Decisions during implementation

Decided by the agent under the maintainer's delegation of 2026-10-09:

- **Review needs a scroll on the iPhone.** With the separate **Until end of
  day** row, **Review session** sits below the fold on a phone (observed on
  the 375-point test iPhone and an iPhone 17 Simulator; it still fits on the
  iPad). The accepted `D2` layout is kept rather than moving Review into the
  navigation bar. A drag that starts on the system countdown wheel turns the
  wheel instead of the page, so the iOS driver now starts its screen swipes
  outside pickers, and `flow session` and the session scenarios reveal Review
  before choosing a length.
- **The time left on a running pause stays in minutes** ("479 min left"
  during an 8-hour pause). The brief asks only that Review name long lengths
  in hours; changing the active countdown is left out of this row.
- **On the Mac, Until end of day ends the same choice group.** As a separate
  row it pushed **Review session** below the default window in a Tart VM,
  where accessibility scrolling cannot move this page. In one group it sits
  after the six lengths and wraps to its own line only in a narrow window;
  iOS keeps the separate row of `D2`.
- **Mac lengths keep their visible labels** (`25 min` … `8 h`), which is
  what the Mac accessibility tree exposes; iOS segments show `25m` … `8h`
  and expose "25 minutes" … "8 hours". `flow session` and the desktop
  scenarios query the Mac labels.
