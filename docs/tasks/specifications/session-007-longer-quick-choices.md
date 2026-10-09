# `SESSION-007`: Longer quick choices for a manual pause

- **Review tier:** Standard
- **Tier reason:** A change to the shared session setup and to how a manual
  pause resolves its end. It stays inside the accepted 5-minute minimum and
  24-hour maximum. It adds no new persisted field, sync format, privilege, or
  platform boundary, because a session already carries an absolute end time.
  Escalate to High-risk if the fix needs any of them.
- **Dependencies:** None; release 1.4, wave 1. `DOCS-005` and `RELEASE-006`
  wait for this row.
- **Integration group:** PR-SESSION-QUICK-DURATIONS
- **Authority:** [Release roadmap](../release-roadmap.md) revision 20, row
  `SESSION-007`; idea 28 in the
  [wiki idea queue](../../wiki/topics/mvp-open-questions.md); the maintainer
  named the row on 2026-10-09.
- **Record:** [execution record](../executions/session-007-longer-quick-choices.md)

## Outcome

When a person starts a pause by hand on the Mac or the iPhone, one tap gives
lengths up to a working day, and one choice ends the pause at midnight on the
device's clock. The hour and minute controls still allow any other length.

## Boundaries

- Today setup offers 25, 45, and 60 minutes (`DurationPresets` in
  `SessionDurationContent.kt`): a segmented control on iOS and a choice group
  on the Mac. After the quick choices come the system countdown wheel on iOS
  (at most 23 h 59 min) and the Hours and Minutes wheels on the Mac. This
  row widens the quick choices to the final set decided below and keeps both
  wheels.
- **Until end of day** is an end time, not a length. Review shows midnight as
  the end, and Start uses that same instant through
  `SessionSetup.validateEndTime`. Neither Review nor Start rounds it to whole
  minutes or moves it to the next day. If fewer than 5 minutes remain at
  Start, Start refuses with the existing too-short failure and returns to
  setup.
- Midnight is the next local midnight in the device's time zone. The choice
  is hidden when fewer than 5 minutes remain until midnight, or when more than
  24 hours remain (only possible early on a 25-hour daylight-saving day). A
  time zone change during the pause leaves the absolute end unchanged.
- Choosing a wheel value clears **Until end of day**, and choosing a quick
  length moves the wheels to that length.
- Review names a long length in hours. It does not show it as hundreds of
  minutes. `posato-control flow session` and the `25 min` scenario steps keep
  working, or change in this row.
- `DESIGN.md` records the final set, labels, and layout on both hosts within
  the DESIGN-004 platform adaptation: system controls on iOS, drawn controls
  on the Mac, 44-point targets, and readable large text.
- Non-goals: schedules, the menu bar status item (its **Start a session…**
  opens this same setup), remembering a person's last choice, custom presets,
  and any change to the 24-hour maximum or the sync format.

## Acceptance

- `AC-01`: On the Mac and the iPhone, every quick length in the final set
  starts a pause whose Review and active end equal the start time plus that
  length.
- `AC-02`: **Until end of day** starts a pause that ends at the next local
  midnight. It is hidden with fewer than 5 minutes left, and a Start after the
  shown midnight has nearly passed refuses instead of starting a pause that
  ends the next day.
- `AC-03`: The wheels still reach any length from 5 minutes to the platform
  maximum, and a quick choice and the wheels never disagree about the
  selected length.
- `AC-04`: `DESIGN.md` and the sessions feature map describe the final set
  and layout, and the existing session scenarios pass.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Mac VM (`--vm primary`) and test iPhone (`-t device`): start a pause with
  the longest quick length and with **Until end of day**, then read the end on
  Review and on the active session. Run `session-start-desktop.json`,
  `session-start.json`, and `flow session` unchanged or as updated.
- Mac VM with the guest clock set near midnight (`tart exec`, with network
  time off and then restored): the choice is hidden at 23:56, shown at 23:50,
  and a Review left open across 23:55 refuses at Start.
- An isolated test of the end-of-day resolution only for failures that E2E
  cannot reach reliably: a 23- or 25-hour daylight-saving day and the
  5-minute edge. Write it failing first, under the testing policy.

## Decisions or blockers

Both decided by the maintainer (`user-confirmed`, 2026-10-09):

- `D1` = A: the quick choices are 25 min, 45 min, 1 h, 2 h, 4 h, 8 h, and
  **Until end of day**; 60 min becomes 1 h.
- `D2` = A: one row of the six lengths with short labels (VoiceOver reads
  them in full), then a separate **Until end of day** row that shows its end
  (`ends 00:00`), then the wheel. On iOS the row of lengths is the system
  segmented control; on the Mac it is the drawn choice group.
  On the Mac, **Until end of day** ends that same choice group instead of a
  separate row, so Review stays in the default window: decided by the agent
  under the maintainer's delegation of 2026-10-09, not yet `user-confirmed`.

No blocker.
