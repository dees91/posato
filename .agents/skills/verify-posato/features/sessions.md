# Sessions

Session provides a local timer from five minutes to 24 hours, backed by the
existing session store. Setup, review, early end, expiry, and restart reflect
real persisted state. Session-driven blocking and synchronization remain unwired.

## Sub-features

- `session-inactive` shows NO SESSION ACTIVE, Start a session, and compact
  selected-item counts. With no effective items, Choose paused items is primary.
- `session-setup` offers 25 min, 45 min, 60 min plus Hours and Minutes wheels
  with arrow buttons and a real Ends at preview.
- `session-review` shows the real end time, selection summary, current warnings,
  Start this pause, and Change duration.
- `session-details` opens read-only website/app lists in an iOS sheet or Mac
  dialog, with category tabs, website search, and Close list.
- `session-active` shows SESSION ACTIVE, remaining time, and End session early.
- `session-early-end` asks Ready to return? with End session and Keep this pause.
- `session-expiry` persists natural expiry and does not revive after restart.
- `session-persist` preserves the active session and its end time across relaunch.

## How to get to it (user POV)

- Session is the launch destination and remains reachable through bottom
  navigation on iOS or the Mac sidebar.
- Choose Start a session, adjust duration, Review session, then Start this pause.
- Open a summary disclosure to inspect a long list without editing it.
- End session early opens its own confirmation surface. Paused items remains
  editable during an active session.

## Driving it with posato-control

Preconditions:

- Launch through the CLI, require doctor to pass, and begin without an active
  session. Preserve existing data and reserve example.com for the fixture.
- The expiry fixtures require example.com to be present and remove it afterwards.

- **Start:** `$PC run -t <target> --scenario tools/posato-control/fixtures/scenarios/session-start.json`
  (use session-start-desktop.json on Mac). It adds example.com, selects 25 min,
  reviews, and starts. Expect End session early and an active screenshot/snapshot.
- **Duration:** In setup use `$PC tap -t <target> --text "Increase Hours" --role button`
  and the corresponding Decrease Hours / Increase Minutes / Decrease Minutes
  buttons. Read the changed values and Ends at preview. At 24 hours minutes are
  zero; at zero hours minutes cannot drop below five.
- **Details:** Tap the website or application count, inspect Selected items,
  switch category using `--text-contains Websites` or Apps with role button,
  then `$PC tap -t <target> --text "Close list" --role button`.
  No edit or remove action should be offered in this view.
- **Persist and end early:** `$PC run -t <target> --scenario tools/posato-control/fixtures/scenarios/session-early-end.json`.
  It really relaunches first, verifies the active state, and confirms End session.
  On Mac/Simulator, `db query --sql "select ended_early from local_session"`
  changes from 0 to 1; local_session_expiry remains empty.
- **Expire:** Run session-expiry.json (or session-expiry-desktop.json) with
  `$PC run -t <target> --scenario <path>`. It selects 25 min, decreases minutes
  twenty times, starts, and waits up to 400 seconds for NO SESSION ACTIVE.
  Read `db query --sql "select count(*) from local_session_expiry"`; expect 1.
  Relaunch and confirm the expired state remains.
- **Empty selection:** Without effective items, Choose paused items routes to
  the editor. If items disappear while reviewing, Start this pause is disabled
  with the real action-required notice.
- **Restore:** End any session this run started and remove example.com if the
  expiry fixture did not already remove it. Preserve the user's other rows.

## Gotchas

- There is no minutes text field. Arrow and wheel changes are immediate; Review
  session reads the current value, not an unsubmitted string.
- The active/review summary stays compact with hundreds of rows. Open Selected
  items to inspect a particular domain; it is not inline on the main screen.
- Actual load/authorization failures remain visible. A saved native selection
  without group metadata is not an effective application target.
- Expiry needs a real five-minute wait; do not alter the database or clock.
- Restart returns to Session, so re-enter Paused items before website cleanup.
