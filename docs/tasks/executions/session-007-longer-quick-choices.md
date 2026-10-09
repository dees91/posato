# Execution: `SESSION-007`

- **Brief:** [session-007-longer-quick-choices.md](../specifications/session-007-longer-quick-choices.md)
- **Status:** `active`: brief only; implementation waits for `D1` and `D2`
- **Review tier:** `standard`
- **Implementer:** Claude Code agent
- **Reviewer:** independent agent, after implementation
- **Branch:** `task/session-007-longer-quick-choices`
- **Updated:** 2026-10-09

## Plan

1. Record the maintainer's `D1` and `D2` in `DESIGN.md` and the sessions
   feature map.
2. Write the end-of-day resolution test first, for the daylight-saving and
   5-minute edges. Then resolve **Until end of day** as an end time through
   `SessionSetup.validateEndTime`, and widen the quick choices on both hosts.
3. Update `posato-control flow session` and the scenarios only where the
   labels or the Review text change.
4. Verify in a Mac VM, including a guest clock set near midnight, and on the
   test iPhone. Then run the Standard review.
