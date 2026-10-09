# Execution: `SESSION-007`

- **Brief:** [session-007-longer-quick-choices.md](../specifications/session-007-longer-quick-choices.md)
- **Status:** `done`: implemented, reviewed, and verified; ready for review
- **Review tier:** `standard`
- **Implementer:** Claude Code agent
- **Reviewer:** independent agent; `changes-required` on `66815d11`, approved on `51b0d2f7`
- **Branch:** `task/session-007-longer-quick-choices`
- **Updated:** 2026-10-09 (closeout)

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
- **Stale end-of-day choice** (review item 5): a choice that is no longer
  offered, or that setup keeps past midnight, is cleared and the length
  returns to 25 minutes; Review and a refused Start report too short instead
  of starting a short or next-day pause. Review shows the dated end.
- **iOS reflow** (review item 3): two rows of three lengths when the widest
  label does not fit a sixth of the row, as at the largest text size.

## Tests written failing first

- `SessionEndOfDayTest` (daylight-saving days, 5-minute edge): 5 of 5 failed
  against a naive stub, then passed.
- `SessionViewModelTest` stale end-of-day choice (Review after 23:55 and
  past midnight, a late Start): the three tests fail with the stale check and
  the reset on refusal removed, and pass with them. They replace an earlier
  pure-function test of the same rules.
- `JvmSessionTimeFormatTest` zone change: failed while the zone was cached.

## Verification

Run directories are under the worktree's ignored `build/verification/runs/`.

- **Uncommitted tree before `15ee9a09`** (19:43–20:07): test iPhone `flow
  session` 480, 30, 25 minutes and Until end of day (`194801-8e0c`,
  `195854-5daf`, `195935-56d5`, `200015-adfe`, `194926-1455`),
  `session-relaunch-ios` (`200059-cc26`); Simulator `session-start`,
  `session-start-saved-items`, `session-expiry` (`200426-1085`,
  `200536-fd6e`, `200714-8b44`); iPad screenshot (`203235-cd47`).
- **`15ee9a09`**: Simulator `flow session` end of day, 480, 30 minutes and
  `session-start` (`204710-a87c`, `205003-ac8a`, `205101-4731`,
  `205157-77b8`).
- **Uncommitted tree before `66815d11`** (Mac VM, 21:26–21:52): guest clock
  at 23:52: row shown, Review "Until 12:00 AM", Start at 23:55:38 refused
  with "Enter 5 minutes or more." and the row hidden (`213745-86a4`,
  `213758-e358`, `214115-31a0`); `session-start-desktop`, `flow session`
  480, end of day, 30, 45 minutes, `session-expiry-desktop`
  (`214334-c9c8`, `214403-c750`, `214424-25d8`, `214443-04b0`,
  `214506-8f4e`, `214534-fb2a`).
- **Final code head `010ce6ab`**, host loaded by another job (load average
  14–37): test iPhone `flow session --until-end-of-day` and `--minutes 480`
  (`221520-7462`, `221601-6480`); Simulator at the largest text size, two
  rows of three without clipping (`222802-1460`); Mac VM fresh clones,
  `pause-sets-desktop` with the reveal step (`222933-cba1`) and `flow
  session --until-end-of-day` (`223710-452d`).
- **Merge of `main` into the reviewed head, `4aa64ad3`** (2026-10-09,
  23:21–23:24): fresh Mac VM clone, `flow set` then `flow session`: 8 hours
  from 23:21 ended at 10/10/26, 7:21 AM (`232155-a310`) and Until end of day
  at 10/10/26, 12:00 AM (`232218-fc7c`); test iPhone `flow session`: Until
  end of day at 10/10/2026, 00:00 (`232303-6027`) and 480 minutes from 23:24
  at 07:24 (`232414-123e`). Each run went through Review and Start.
- **Skipped** under the maintainer's order of 2026-10-09 (translated): "keep
  VM and iPhone verification to a minimum, once before the PR where it makes
  sense". Not rerun on the final head: the other session scenarios and the
  Mac midnight edge. `010ce6ab` changed the midnight path (the stale reset),
  and the `SessionViewModelTest` cases above cover it instead of a VM run.
- `driver-settle-ios` failed at step 4 on the Simulator at both text sizes,
  because it types through the hardware keyboard, so **Done** never shows
  (`222158-7aa4`, `222233-96c2`); on the test iPhone it passed on
  `51b0d2f7`'s code (`225244-c4a5`). The shorter press-and-drag of the
  driver's screen swipes may still break scroll scenarios not run here.

## Review

- Independent review of `66815d11`: `changes-required`; items 3–6 and 8
  fixed in `010ce6ab`.
- Follow-ups, not changed here: `IosSessionTimeFormat` keeps its formatters'
  zone from creation (it cannot be verified on iOS in this row), and
  `FirstCloseChoice.kt:45` formats with `ZoneId.systemDefault()`, which the
  JVM caches.
- Open: what Mac VoiceOver speaks for the lengths. The spoken label is set
  as the text's content description, but the Mac accessibility tree exposed
  the visible "1 h"; not checked with VoiceOver.
