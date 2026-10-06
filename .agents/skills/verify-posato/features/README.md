# Posato verification map

This directory is the maintained source for verifying the user-facing behavior
of Posato. Read the index before driving the app, then use the matching
feature file as the recipe.

## Baseline preconditions

- Build the CLI with `./gradlew :posato-control:installDist` and use
  `PC=tools/posato-control/build/install/posato-control/bin/posato-control`.
- Pick one target: `sim` (booted `iPhone 17`, app installed, no permissions
  needed), `desktop` (only in a Tart VM clone with `--vm primary|peer`, never
  on the host Mac; see the skill's unattended section), or `device` (connected, unlocked iPhone and
  `posato.apple.developmentTeam` in the ignored `local.properties`).
- Launch through the CLI (`launch -t <target>`), wait for the `Pause sets`
  button (`wait --for exists --text "Pause sets" --role button`), and
  require `doctor -t <target>` to report `ok: true`. The app opens on the
  `Session` destination after every launch and relaunch, except on a fresh
  database, which shows the [first-install flow](./onboarding.md) first; run
  `first-install-skip.json` after every `--fresh` launch or `reset`.
- Before a Websites or Applications recipe, switch destination with `tap
  --text "Pause sets" --role button`, open the set with `tap --text-contains
  "My set" --role button` (or the set the recipe names), and wait for
  `Search`; the
  Sessions recipe starts on `Session` and needs at least one website first.
- Start recipes from a state with no website named `example.com` and no
  active session; the Simulator can start from `launch --fresh` and a desktop
  VM clone from `vm create`; on the test iPhone remove what you add and end
  what you start instead of resetting.
- Never drive an instance that this run did not launch.

## Scenario state

Scenarios share one app's data. The table names the state each one starts
from; the session and pause-set scenarios check it in `precondition` steps,
which fail with `PRECONDITION_NOT_MET` and the remedy instead of a misleading
timeout. Clean up what a scenario leaves before the next one, or start from a
fresh clone or `launch --fresh`.

| Scenario | Starts from | Leaves | Clean up with |
| --- | --- | --- | --- |
| `session-start*`, `session-start-saved-items*`, `session-relaunch-ios` | no active session | an active session (`session-start*` also `example.com`) | `session-early-end.json`; `remove-website*.json` |
| `session-expiry*` | no active session, `example.com` saved | nothing; it removes `example.com` | none |
| `add-website*` | no `example.com` | `example.com` | `remove-website*.json` |
| `website-batch-list*` | no `design-proof-*.example` rows | 50 `design-proof-*.example` rows | `website-batch-list-cleanup*.json` |
| `pause-sets-desktop` | a linked workspace (`flow icloud link`) that never opened Pause sets, only My set, no schedules | the one-time notice used up; it deletes its sets and schedule | a freshly linked clone before a rerun |
| `pause-sets-device` | no `Work` set | nothing; it deletes `Work` | none |

An interrupted run leaves whatever it created so far; remove those rows by
hand with the same controls before rerunning.

## Driving conventions

- Every recipe names the exact `posato-control` command; keep labels, flags,
  and quoted copy unchanged.
- Main navigation is at the bottom on iPhone and iPad portrait, and in the
  sidebar on Mac and iPad landscape.
  Nested category-tab labels include counts; use `textContains` with role
  `button`. On the Mac open a row's `Actions for <name>` menu before Edit or
  Remove; on iOS see the next section.
- Each website mode has one text field, selected with `--role textField`.
  Return submits the add batch and keeps focus; tap `Done` to dismiss the
  keyboard and restore the iOS bottom navigation.
- Use scenario `scrollTo` for offscreen rows on both targets. Do not substitute
  Tab counts or database writes for the real list interaction.
- Prefer `run --scenario <file>` over many single commands on iOS.

## iOS and the Mac drive differently

Since `DESIGN-004` one shared interface adapts per platform. The Mac keeps the
drawn controls and in-page links; iOS uses the system's navigation and controls.
On iOS:

- Every pushed screen has a bar whose back button reads `Back to <previous
  title>`, for example `Back to Session`, `Back to Schedules`, `Back to Pause
  sets`, or `Back to <set name>`. The Mac's in-page links stay lower case
  (`Back to schedules`, `Back to pause sets`). Session's setup has no Cancel or
  Change duration on iOS; go back with the bar.
- Status labels are sentence case (`No session active`), and screen eyebrows
  such as `YOUR NEXT PAUSE` and `ONE LAST LOOK` are absent; wait for a control
  of the screen instead, such as `Review session` or `Start this pause`.
- Rows have no `Actions for <name>` button. A website row edits on a tap and
  removes with `swipeLeft` on its text, then `Remove`; an app row and a pause
  set row work the same way (`Remove`; `Rename` or `Delete`). A schedule row
  edits on a tap, and `swipeLeft` reveals `Skip next` and `Delete`. The set
  screen keeps its bar menu, `More actions for <name>`.
- Questions are system alerts: ending a pause early (`Ready to return?`, `End
  session`), deleting a schedule (`Delete <name>?`, `Keep` or `Delete`),
  naming a set (`Save`, `Cancel`), and deleting one (`Delete <name>?`,
  `Delete` or `Move to <set> and delete`).
- Times use the system pickers. A schedule's Starts and Ends are compact pickers
  labelled `Time Picker` (Starts first, value `09:00`): tap one, run
  `adjustWheels` with `["10","30"]`, then tap the element with id
  `PopoverDismissRegion`. Session setup's length is a countdown wheel: run
  `adjustWheels` with `["0","30"]`. The wheels follow a 24-hour clock.
- The pause set is a system pop-up button labelled `Pause set` with the set as
  its value; its menu rows read `<name>, <detail>`.
- Session's selected items open as a pushed screen with `Back to Session` and
  `Edit`; there is no `Close list` or `Filter list`, and the filter field is
  always shown for websites. `About Posato` is an info symbol in Session's bar,
  and in the iPad sidebar in landscape.
- Prefer `flow set`, `flow schedule`, and `flow session`: they follow both
  interfaces.
- Restore data after a mutation. Do not remove proof artifacts during cleanup.

## Proof and skip reporting

- Capture the user action and the resulting state, not only the final screen.
- UI proof is a `snapshot` (or a `wait` naming the element) plus a
  `screenshot` from the run directory.
- Mutation proof includes a read-only second view of the stored value:
  `db query` on the desktop and Simulator, `find` after a relaunch on the
  iPhone.
- Record the feature ID, the target, and the run id with every artifact; cite
  the run directory, never paste screenshots or paths into tracked files.
- Report an unreachable path with the attempted command and the failing
  check. Do not report a skipped entry point as verified through another
  target.

## Feature entry contract

Each feature file starts with an H1 title and one paragraph describing the
user-visible behavior. It then uses exactly four H2 sections in this order.

1. `Sub-features` lists short IDs with one line for each behavior.
2. `How to get to it (user POV)` lists every user entry point.
3. `Driving it with posato-control` starts with `Preconditions:` and uses
   labeled bullets that pair each user action with an exact command and an
   observable result.
4. `Gotchas` lists traps that can waste or invalidate a verification run.

Keep implementation details out of the map. Name only user paths, stable
handles, required state, commands, and observable proof.

## Features

- [Pause sets](./pause-sets.md) covers the set list, creating, renaming,
  choosing the default, deleting with Move to <set> and delete, and the set choice in
  Session and schedules.
- [Schedules](./schedules.md) covers adding, validating, skipping, editing,
  turning off, deleting and persisting schedules, device readiness and sync.

- [Websites](./websites.md) covers adding, rejecting, editing, removing, and
  persisting exact domains on every target.
- [Application group](./application-group.md) covers automatic creation,
  name preservation, and recovery after a partial metadata-save failure.
- [macOS application mappings](./macos-application-mappings.md) covers the
  desktop-only application picker, driven end to end through the helper
  process, and how to read its result.
- [iOS application mappings](./ios-application-mappings.md) covers the
  device-only Family Controls picker, its access states, and the consent and selection steps the driver runs on the test iPhone.
- [Sessions](./sessions.md) covers setting up, reviewing, starting, ending
  early, and expiring one manual session on every target, what survives a
  relaunch, and the observed blocking and release of a website and an
  application.
- [Sync with iCloud](./sync.md) covers the one consent action, the truthful
  outcomes it reports, joining in either device order, and how a from-empty
  rerun starts in VMs.
- [First install](./onboarding.md) covers the six-step first-run flow, the
  skip prelude every fresh launch needs, the upgrade row, and the degraded
  iCloud outcome.

- [Updates on Mac](./updates.md) covers the update-consent alert, preparing
  the previous release in a clone, the in-app update to the current one, and
  upgrading the previous release's development build on Mac and iPhone.
- [Back navigation](./navigation.md) covers explicit Back, Command-[ and
  Escape, the Mac trackpad swipe, and the iOS edge swipe.
- [About Posato and licenses](./licenses.md) covers the installed version,
  three offline legal documents, full-text
  scrolling, and return to the preceding primary destination on both hosts.
