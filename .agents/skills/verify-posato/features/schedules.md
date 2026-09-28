# Schedules

A person adds, edits, turns off, skips the next occurrence of, and deletes
named weekday and time schedules on Mac and iPhone. Plans are stored on the
device and reach the other devices of a linked iCloud workspace. Nothing
starts automatically yet: rows show the next run, not an active pause.

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

## Gotchas

- Scroll to controls before tapping them, especially with the iPhone keyboard
  visible. The name field's Return action clears focus.
- The first saved schedule asks for notification permission once; answer
  it with `vm allow-notifications` on Mac or `optional` springboard taps on
  iPhone so it does not cover later steps.
- Rows never claim a schedule will start: the hosts that start them arrive
  in later slices. Do not use this recipe as evidence of enforcement.
