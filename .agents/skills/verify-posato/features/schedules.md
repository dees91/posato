# Schedules

A person adds, edits, turns off, skips the next occurrence of, and deletes
named weekday and time schedules on Mac and iPhone. Plans are stored on the
device and reach the other devices of a linked iCloud workspace. A Mac that
is ready for schedules starts and ends them on its own, with the window
closed, through the standing grant and never with a password dialog.

## Sub-features

- `schedules-manage` adds a schedule, shows its next run, skips the next
  occurrence, edits it, turns it off, and deletes it after a confirmation.
- `schedules-validation` refuses an empty name, no day, equal times, a
  schedule under 15 minutes and an eleventh schedule, with one caption each.
- `schedules-persistence` keeps plans across a relaunch.
- `schedules-readiness` shows Set up this Mac before a Mac's setup is
  verified (no Add schedule there) and Allow Screen Time on an iPhone that
  has not allowed it (Add schedule stays available).
- `schedules-sync` shows a plan saved on one linked device on the other
  after an exchange; a plan beyond the shared cap of 10 says it could not
  sync.

## How to get to it (user POV)

After onboarding, choose Schedules in the primary navigation. On a Mac
that is not set up, Set up this Mac opens the shared setup with the working
Set up Posato action; Back to schedules then offers Add schedule. On
iPhone, Add schedule is always offered.

## Driving it with posato-control

Tracked recipes, all starting on the Session screen after onboarding:

- `schedules-mac-needs-setup-desktop.json`: a Mac whose setup was deferred
  shows the setup card and no Add schedule.
- `schedules-desktop.json` (Mac, after setup) and `schedules-device.json`
  (iPhone or Simulator): add with an empty-name refusal, change the start
  to 10:00, save, skip next, rename and turn off, relaunch, then delete
  with Keep and Delete.

Use `-t desktop --vm primary` on Mac and `-t device` on the test iPhone. To
set up a Mac from Schedules, tap Set up this Mac, then Set up Posato, and
answer the system dialogs as they appear: `vm prompt background` once Login
Items & Extensions shows the helper, `vm prompt admin` for each password
dialog, and `vm prompt picker-bypass` if it asks. Poll `vm text` every few
seconds rather than waiting; This Mac is ready. can take five minutes.

- Rows expose Edit <name>, Skip next and Delete <name> buttons; the next run
  reads "Next: <day>, <date>" and a skip adds "Skipped: <day>, <date>".
- The editor's Schedule on row toggles the enabled switch.
- For sync, link two VMs (see [sync](./sync.md)), save on one, and wait for
  the row on the other after its next exchange.

## Automatic starts on a Mac

Start from a set-up Mac (the unified setup records the consent) with
`example.com` in Pause sets.

1. Read the guest clock with `$PC vm exec --line primary --script "date +'%s %z'"`
   and add a schedule that starts two to three minutes later: today's weekday
   selected, the start and end set with the Increase/Decrease wheel buttons,
   at least 15 minutes long. Generate that scenario from the clock instead of
   tracking one; it changes every run.
2. `$PC close-window -t desktop --vm primary`, wait until 20 s after the
   start, then `$PC observe -t desktop --vm primary --website http://example.com/ --expect blocked`
   (outcome `paused`; `https://` answers `unreachable` through the proxy).
   `$PC vm text --line primary --contains assword` must show no dialog
   (Posato's own caption also contains the word).
3. Relaunch catch-up: `terminate`, `observe --expect allowed` after the
   helper lease (about 25 s), `launch`, `observe --expect blocked`.
4. End early from Session asks first and then allows at once.
5. Setup required: turn the grant off in This Mac after saving a schedule;
   when it comes due there is no dialog, `observe --expect allowed`, and
   Session says "Setup required on this Mac".
6. A login launch is a `vm shutdown` and `vm boot`; Posato opens without a
   window, so `launch --adopt` and open the window from the menu
   (`menu --choose "Open Posato"`) before scenario steps.

## Gotchas

- Scroll to controls before tapping them, especially with the iPhone keyboard
  visible. The name field's Return action clears focus.
- The first saved schedule asks for notification permission once; answer
  it with `vm allow-notifications` on Mac or `optional` springboard taps on
  iPhone so it does not cover later steps.
- On a Mac that is not ready for schedules, Add schedule is hidden; save the
  plans before turning the grant off.
- The iPhone monitor extension needs the test iPhone; the Simulator cannot
  enforce Screen Time.
- To prove what the monitor extension does while Posato stays closed, such
  as a start right after an update installed without opening the app, run
  the Calculator and Safari checks in a scenario with `launch.skip: true`;
  any other scenario starts Posato first. A scenario `terminate` step closes
  a Posato this tool launched.
- `flow schedule` saves every day unless `--days` says otherwise; a
  hand-written scenario must press the run's weekday, because the editor
  proposes weekdays only and a weekend run would never start.
- Add schedules with `flow schedule`; the time wheels drop a tap now and
  then, and the command reads the label and corrects in rounds. A hand-written
  scenario must assert the `Starts HH:MM` label before saving. The 1.2 editor
  refuses plans shorter than 15 minutes.
- A manual session cannot start while a scheduled pause runs; for an
  overlap, start the session first and let a schedule begin inside it. A
  session shorter than 15 minutes ends only when Posato is open at its end.
