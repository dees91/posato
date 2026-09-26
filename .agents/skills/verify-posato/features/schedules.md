# Schedules

Schedules currently exposes UI shells. A person can explore the editor and
device setup, but cannot save or execute a plan. Sample lists and active
sessions exist only in Compose previews.

## Sub-features

- `schedules-editor` opens the empty state and editor, changes days and times,
  and leaves Save schedule disabled.
- `schedules-setup` opens the device-specific setup explanation without
  changing any system permission.
- `schedules-navigation` returns to the existing Session and Paused items
  destinations; Cancel leaves no schedule behind.

## How to get to it (user POV)

After onboarding, choose Schedules in the primary navigation. On Mac,
Set up schedules opens the three prerequisites. Continue to schedule remains
disabled; Preview schedule editor opens the form without allowing creation.
On iPhone, Add schedule opens the editor and Set up this iPhone opens setup.

## Driving it with posato-control

Preconditions: launch the app through the driver and finish onboarding. Use
`-t desktop --vm primary` on Mac and `-t device` on the test iPhone.

- Open with `$PC tap -t <target> --text Schedules`, then
  `$PC tap -t <target> --text "Set up schedules"` on Mac. Verify Continue to
  schedule is disabled, then choose Preview schedule editor. On iPhone, use
  `$PC tap -t <target> --text "Add schedule"`.
- Enter a synthetic name with `$PC type -t <target> --role textField --input
  "Morning focus" --submit`. Return clears focus.
- Open the time control with `$PC tap -t <target> --text "Starts 09:00"`.
  Scroll to Increase Start hours and tap it, then tap Done. The button reads
  Starts 10:00.
- Scroll to Save schedule, then run `$PC wait -t <target> --for disabled
  --text "Save schedule"`. Capture a screenshot and snapshot. Cancel returns
  to the empty state, with no saved row.
- Open Set up schedules on Mac or Set up this iPhone, capture its disabled actions,
  then choose Not now. Session and Paused items remain accessible.

## Gotchas

- Scroll to controls before tapping them, especially with the iPhone keyboard
  visible. The name field's Return action clears focus.
- No schedule is stored or synchronized. Do not use these shells as evidence
  of recurrence, authorization, notifications or enforcement.
- The existing This Mac controls still work. The new onboarding and schedule
  setup shortcuts are disabled, and the upgrade offer is preview-only.
