# Sessions

Session provides a bounded timer from five minutes to 24 hours, backed by
persisted session state. Linked Apple devices exchange session intent through
the [sync flow](sync.md); each applies its own authorized local selections.
The frozen summary describes the start; reapplication uses the current local
policy. A received timer alone is not enforcement proof: Mac adoption
can require Resume restrictions and an attended administrator confirmation.

## Sub-features

- `session-inactive` shows NO SESSION ACTIVE, Start a session, and compact
  selected-item counts. With no effective items, Choose paused items is primary.
- `session-setup` offers 25 min, 45 min, 60 min plus Hours and Minutes wheels
  with arrow buttons and a real Ends at preview.
- `session-review` shows the real end time, selection summary, current warnings,
  Start this pause, and Change duration.
- `session-details` opens read-only website/app lists in an iOS sheet or Mac
  dialog, with category tabs, a website filter, an editing route, and Close list.
- `session-edit-route` opens the corresponding Paused items category from
  the Session summary or selected-items list.
- `session-active` shows SESSION ACTIVE, remaining time, and End session early.
- `session-early-end` asks Ready to return? with End session and Keep this pause.
- `session-expiry` persists natural expiry and does not revive after restart.
- `session-persist` preserves the active session and its end time across relaunch.
- `session-blocking` pauses a website and an application during a session, as a
  person meets them, and releases both after it ends.
- `session-mac-setup` (Mac only) offers Check Mac setup inside the This Mac row below iCloud,
  reads nothing before the press, then names the real helper state with one
  action and a quiet Check again, except not enabled, which shows Enable on
  this Mac alone. A state other than ready is also named once as a notice above
  the session action while the row is collapsed.

## How to get to it (user POV)

- Session is the launch destination and remains reachable through bottom
  navigation on iOS or the Mac sidebar.
- Choose Start a session, adjust duration, Review session, then Start this pause.
- Open a summary disclosure to inspect a long list without editing it.
- Use Add or edit websites or Manage apps in the summary to reach the matching
  Paused items category in one action. The selected-items list offers the same
  route. Filter list reveals the website-only filter when needed.
- End session early opens its own confirmation surface. Paused items remains
  editable during an active session.
- Expand iCloud to reach sync and workspace-removal controls. Expanding the row
  never starts an exchange.
- On the Mac, expand This Mac below iCloud. Check Mac setup reports the
  helper state; Enable on this Mac registers it; Open System Settings and Check
  again cover background approval. Nothing runs until a button is pressed. Once
  a read has returned a state other than ready, Session names it above the
  session action until the row is expanded.

## Driving it with posato-control

Preconditions:

- Launch through the CLI, require doctor to pass, and begin without an active
  session. Preserve existing data and reserve example.com for the fixture.
- The expiry fixtures require example.com to be present and remove it afterwards.

- **Start:** `$PC run -t <target> --scenario tools/posato-control/fixtures/scenarios/session-start.json`.
  It adds example.com, selects 25 min, reviews, and starts. Expect End session early
  and an active screenshot/snapshot. On `sim` enforcement reports unavailable truthfully:
  the session is active and the attention notice is visible.
- **Start without confirmation (desktop, unattended):**
  `$PC run -t desktop --scenario tools/posato-control/fixtures/scenarios/session-start-action-required-desktop.json`.
  Requires a Mac with the helper enabled. Use it when nobody confirms the administrator
  prompt: Start leaves the session active with the Retry notice, the run asserts it, ends
  early through the nothing-restricted confirmation, asserts Retry is gone after the clean
  end, and removes example.com. The Retry wait allows up to 240 seconds for the helper
  deadline path. The nothing-restricted step matches by `textContains` because the
  accepted copy continues with a second sentence. A confirmed prompt instead lands in
  the enforcement path below (active claim, no Retry): `QUALITY-005` proved both on
  2026-09-08 — confirmed runs show `authd` authentication seconds after the prompt,
  while the unconfirmed run reaches Retry with no authentication at all.
- **Start with enforcement (desktop in a Tart VM, unattended):** run session-start-desktop.json
  with `--vm primary` in the background and `$PC vm prompt admin --line primary` beside it;
  the prompt appears about ten seconds after Start. The same pairing runs
  session-expiry-desktop.json.
- **Observe blocking (both targets):** with `example.com` and one application paused, on the
  desktop run `$PC observe -t desktop --vm primary --website http://example.com/ --application
  Safari --expect blocked` during the session and `--expect allowed` after it: the request
  through the system proxy returns the pause page (`outcome: paused`) and Safari is ended at
  launch, then the real page loads and Safari keeps running. On the test iPhone run
  `observe-blocking-ios.json` during the session and `observe-unblocked-ios.json` after it:
  SpringBoard shows "You cannot use Calculator because it is restricted." and Safari shows
  "Website Not Allowed", then Calculator's keypad and the Example Domain page appear. Each
  fixture fails in the opposite state, so a pass is not a timing accident.
- **Duration:** In setup use `$PC tap -t <target> --text "Increase Hours" --role button`
  and the corresponding Decrease Hours / Increase Minutes / Decrease Minutes
  buttons. Read the changed values and Ends at preview. At 24 hours minutes are
  zero; at zero hours minutes cannot drop below five.
- **Details:** Tap the website or application count, inspect Selected items,
  switch category using `--text-contains Websites` or Apps with role button,
  then `$PC tap -t <target> --text "Close list" --role button`.
  List rows have no inline edit or remove action.
- **Edit route:** From Session, `$PC tap -t <target> --text "Add or edit websites" --role button`.
  A snapshot shows the Paused items heading and Add websites field. Return to
  Session, then `$PC tap -t <target> --text "Manage apps" --role button`;
  the Paused items Apps tab and its Choose apps action are visible. Repeat each
  route from the matching category inside Selected items.
- **Filter:** Open the website count, tap Filter list, then `$PC type -t <target> --role textField --input absent.example --clear`.
  A snapshot shows Filters this list only, the filtered count, and No websites
  match this filter. Clear the field and confirm the original list returns.
- **Unfinished edit:** Start editing a saved website without submitting, return
  to Session, then use Add or edit websites. Paused items shows Add websites and
  Resume website edit. Add a different website, resume, and verify the original
  unsaved Website domain text remains. Other website row actions stay disabled
  until the edit is resumed and finished or canceled.
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
- **Mac setup (desktop, the deferred-onboarding route):** back up and
  `reset -t desktop`, launch, and drive the first-install flow to the permission
  step with the `first-install-skip.json` steps (Not now on the permission
  step), then finish to Session. `pgrep -f PosatoMacOSHelper` is empty; switch
  to Paused items and back to Session and it stays empty. `$PC snapshot -t
  desktop --format text` shows the This Mac row. Expand it with `$PC tap -t desktop --text-contains
  "This Mac," --role button`; `pgrep` stays empty and Check Mac setup is now
  visible. `$PC tap -t desktop --text
  "Check Mac setup" --role button`; `$PC wait -t desktop --for exists --text
  "This Mac, Background helper enabled" --role button --timeout-seconds 130`; screenshot and snapshot.
  `pgrep` is now non-empty, which is the on-demand evidence. No readiness notice
  appears above the session action while the helper is ready, and the row starts
  collapsed after every relaunch. Press the quiet
  `Check again` once and expect the same state. Capture the secondary setup
  action and scroll to include Check again in the result screenshot. With
  VoiceOver enabled, check that progress and the returned result are announced
  on both presses, including the unchanged enabled result. An accessibility
  snapshot alone does not prove spoken delivery. Restore the database afterwards.
- **Restore:** End any session this run started and remove example.com if the
  expiry fixture did not already remove it. Preserve the user's other rows.

## Gotchas

- Starting on the Mac raises the administrator prompt for the helper Apply; `vm prompt
  admin` confirms it, otherwise expect the action-required state with Retry.
- Removing the network service that holds the proxy settings during a session leaves a
  false "Restrictions active" and a stale record (`MACOS-020`); do not use that path as a
  cleanup step.
- The active summary shows the frozen start set; reapplication uses the current
  local policy. Relaunching the Mac app during a session needs Resume restrictions.
- There is no minutes text field. Read the displayed duration and review deadline
  before starting; repeated driver decrease taps are not proof of the final value.
  The attended SYNC-012 run requested twenty decreases after 25 min but persisted
  ten minutes. Use the observed deadline for expiry checks, without changing time
  or database state.
- Set scenario `launch.terminateExisting` explicitly to `false` when continuing a
  session on the same host instance; omitting it selects the restart default.
  After setup or confirmation recreates Session, expand iCloud before its actions.
- The active/review summary stays compact with hundreds of rows. Open Selected
  items to inspect a particular domain; it is not inline on the main screen.
- Actual load/authorization failures remain visible. A saved native selection
  without group metadata is not an effective application target.
- Expiry needs a real five-minute wait; do not alter the database or clock.
- Restart returns to Session, so re-enter Paused items before website cleanup.
  A fresh database shows the first-install flow instead; run
  `first-install-skip.json` first (see [First install](./onboarding.md)).
- iCloud and This Mac start collapsed whenever Session is recreated. Expand
  the relevant row after returning from Paused items or relaunching. If an
  expanded action is offscreen, use scenario `scrollTo` before tapping it;
  `find` and `wait` do not scroll.
- An ad-hoc restaged package (what `./gradlew quality` leaves behind) cannot
  reach the signed daemon, so Check reports the unavailable state on it. That is
  the one non-ready branch reproducible without touching system registration,
  and it also shows the repeated-result sentence and the collapsed-row notice.
  Rebuild with `$PC build -t desktop` before claiming anything about the ready
  path.
- Only the enabled branch of This Mac is reachable on a Mac whose helper is
  already approved; not enabled, approval required, unavailable, uncertain,
  recovery required, and the lost-connection retry stay unit-only unless they
  occur naturally. Each helper request has a 120-second deadline; the caption
  reads Checking Mac setup… meanwhile. After a lost helper reply, Check again
  reconciles that original request instead of issuing Status. Registered but
  unlaunchable is recovery required, not Ready. In-app Check again does not
  unregister it. After a Background Items database reset, Check reports not
  enabled and offers Enable on this Mac alone; it is not the unavailable
  Check-again-only path. An Enable whose registration attempt fails and leaves
  the service unregistered reports that setup could not be completed, not not
  enabled. The second time the same result comes back in a state whose action
  cannot change it — could not be checked or enabled, or registered but cannot
  start — the options add one sentence offering a restart of the Mac; an
  unfinished request does not get that sentence, and restarting Posato is still
  not the recovery path. The notice above the session action appears only after a
  real read, and disappears while the row is expanded, a call is running, or a
  session is actively enforcing.
  Login Items or leftover-bundle removal needs maintainer approval before any
  out-of-band change.
