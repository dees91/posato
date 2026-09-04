# Sessions

Sessions lets a user start one bounded quiet pause across the saved websites
and chosen applications: pick a length between 5 minutes and 24 hours, review
what the session will pause and whether anything still needs attention, start
it, watch it count down, end it early through a confirmation, or let it expire.
The session survives a relaunch and an expired session never comes back.

## Sub-features

- `session-inactive` shows `No session active` with `Start session` and a
  summary card of the websites and applications that would be paused.
- `session-setup` collects the length under `Session length`: a `Minutes`
  field (default 30), `Shorter session` and `Longer session`, an `Ends at
  <time>` preview, `Review session`, and `Cancel`; out-of-range input keeps
  the prior value and shows `Enter 5 minutes or more.` or `Enter 24 hours
  (1440 minutes) or fewer.`
- `session-review` shows `Review session`, `Ends at <time>`, the items card
  (`Websites (<n>)`, the group name, `Applications (<n>)` or `Choosing apps
  is not available in this version of Posato.`), and one notice when
  something needs attention; `Start session` is enabled only when the session
  would pause at least one item.
- `session-action-required` refuses to start with `Add websites or choose
  applications in Paused items first.` (nothing effective) or `Chosen
  applications could not be loaded. Nothing was changed.` (load failure,
  with `Retry`); `Apps still need to be chosen on this device.` and `Allow
  access to choose apps, or remove the application group to pause websites
  only.` only warn and keep `Start session` enabled.
- `session-active` shows `Session active until <time>`, `<n> min left` (or
  `Less than a minute left`), the items card, and `End session early`.
- `session-early-end` opens `End session early?` with `End session` and
  `Cancel`; confirming shows `No session active` and `The session ended
  early.`
- `session-expiry` turns an active session into `No session active` with
  `The session ended at <time>.` once the end passes while the app runs; a
  relaunch after that never revives it.
- `session-persist` keeps an active session active across a relaunch, with
  the same end time.

## How to get to it (user POV)

- Open Posato on any target; `Session` is the destination shown after launch
  and is reachable at any time through the `Session` button at the top.
- With no session, tap `Start session` to open setup, then `Review session`,
  then `Start session` again on the review screen.
- With an active session, tap `End session early` and confirm with `End
  session`, or wait for the end time.
- `Paused items` at the top (and the `Paused items` button under the status)
  opens the websites and applications that a session pauses.

## Driving it with posato-control

Preconditions:

- The app is launched through the CLI and `wait -t <target> --for exists --text "Paused items" --role button --timeout-seconds 30` returned `ok`.
- No active session (`find -t <target> --text "No session active"` returns one element) and no website named `example.com`.
- `PC` points at the installed CLI; replace `<target>` with `sim`, `desktop`, or `device`. The minutes field is always `--role textField --near-text "Session length"`.

- **Start through the fixture.** Run `$PC run -t sim --scenario tools/posato-control/fixtures/scenarios/session-start.json` (also `device`) or `$PC run -t desktop --scenario tools/posato-control/fixtures/scenarios/session-start-desktop.json`. The scenario relaunches, switches to `Paused items`, adds `example.com`, returns to `Session`, opens setup, submits 30 minutes, asserts `example.com` on the review screen, taps `Start session`, and waits for `End session early`; every step reports `ok: true` and the last two artifacts are the active screenshot and snapshot.
- **Refuse an empty start.** With no websites and no chosen applications, run `$PC tap -t <target> --text "Start session" --role button`, `$PC tap -t <target> --text "Review session" --role button`, then `$PC find -t <target> --text "Add websites or choose applications in Paused items first."`; one element is returned and `find --text "Start session" --role button` reports the button disabled. Tap `Cancel` to leave review.
- **Reject a bad length.** In setup run `$PC type -t <target> --role textField --near-text "Session length" --input 3 --clear --submit`; `find --text "Enter 5 minutes or more."` returns one element and the field keeps its previous value. `--input 1500` yields `Enter 24 hours (1440 minutes) or fewer.`
- **Persist.** With the session active run `$PC launch -t <target>` (no `--fresh`) and `$PC wait -t <target> --for exists --text "End session early" --role button`; on `desktop` and `sim` also `$PC db query -t <target> --sql "select ended_early from local_session"` returns one row with `0`.
- **End early through the fixture.** Run `$PC run -t <target> --scenario tools/posato-control/fixtures/scenarios/session-early-end.json` against the running app. It waits for `End session early`, taps it, waits for `End session early?`, taps `End session`, and waits for `No session active`; `db query --sql "select ended_early from local_session"` now returns `1` and `"select count(*) from local_session_expiry"` returns `0`.
- **Expire.** Run `$PC run -t sim --scenario tools/posato-control/fixtures/scenarios/session-expiry.json` or `-t desktop --scenario .../session-expiry-desktop.json` from an inactive state with `example.com` present. The scenario starts a 5-minute session and waits up to 400 seconds for `No session active`; afterwards `find --text-contains "The session ended at"` returns one element and `db query --sql "select count(*) from local_session_expiry"` returns `1`. A relaunch still shows `No session active`.
- **Proof.** `$PC screenshot -t <target> --name session-active` and `$PC snapshot -t <target> --format text --human` while active; the artifacts show `Session active until` and `End session early`.
- **Restore.** End the session if it is still active, switch to `Paused items`, and remove `example.com` (`remove-website.json` on `sim` and `device`, `remove-website-desktop.json` on `desktop`); on the desktop this is mandatory because the database is the developer's. Confirm with `db query --sql "select count(*) from exact_domain_policy where canonical_domain='example.com'"` returning `0`.

## Gotchas

- The minutes field submits only through Return or the IME action; `Review
  session` reads the last submitted value, not the text in the field. Use
  `--submit` (or `"submit": true`) on iOS; on the desktop `type` into the
  field, wait for `settled`, then `press --key return` (a `tap` on the field
  right before `type` makes the second lookup fail).
- `Start session` on the review screen is disabled while nothing would be
  paused; a disabled button is a correct refusal, not a missing element.
- Expiry is observed only while the app is in the foreground; a session that
  ends while the app is closed shows the expired state on the next launch.
- The expiry scenarios wait for a real 5-minute session; do not shorten the
  end time through the database.
- The review and active screens list every website near the top, so the
  desktop scenario proves the saved website there (`review-lists-item`)
  rather than on `Paused items`, where the row sits below the window when an
  application group exists.
- Every `launch` returns to `Session`, so a Websites recipe run after a
  session scenario must switch to `Paused items` again.
