# Back navigation

Every destination keeps its own stack: Pause sets (list, a set, its editors),
Schedules (list, editor, setup), and About with its licenses. Back leaves the
top screen the way its explicit Back action does, so an editor discards its
draft without asking. Back on a stack's first screen changes nothing.

## Sub-features

- `back-explicit` presses a screen's own Back action, such as **Back to pause
  sets**.
- `back-mac-keys` goes back with Command-[ and with Escape.
- `back-mac-swipe` goes back with the trackpad's two-finger swipe between
  pages, and stays put when the swipe is released early.
- `back-ios-swipe` goes back with the edge swipe on iPhone and iPad.
- `back-blocked` ignores back while a start, end, save, or system approval or
  password prompt is in progress.

## How to get to it (user POV)

- Mac: Command-[, Escape, or a two-finger swipe right over the main window.
  Escape in a text or search field first leaves the field; the next Escape
  goes back.
- iPhone and iPad: a drag from the leading screen edge most of the way across.
- Both: the Back action at the top of every nested screen.

## Driving it with posato-control

Preconditions:

- Open a nested screen first (a set, the schedule editor, or a license) and
  assert it with `wait` before each back action, so the run proves a change.

- **Mac keys:** `$PC vm press cmd-[ --line <line>` and
  `$PC vm press escape --line <line>`, then `wait` for the screen below. The
  element command `press --key` has no `[` key; use `vm press` for the chord.
- **Mac swipe:** `$PC swipe-back -t desktop --vm <line>` completes the swipe;
  `--cancel` releases it below the threshold and the screen stays.
- **iOS swipe:** a scenario step `{"name":"back","action":"swipeBack"}`
  followed by a `waitFor` on the screen below; run it on the Simulator and on
  the test iPhone (`-t device`).
- **Blocked back:** start a save or a session and send back while its
  progress shows; the screen must not change.

## Gotchas

- Quit System Settings before `vm press`; left in front after a prompt, it
  takes Command-[ for itself.
- The Mac swipe works only in the main window without a sheet or modal.
- Synthetic swipes report the whole movement at their start, so they prove
  completion and cancellation, not the gesture's progress animation.
