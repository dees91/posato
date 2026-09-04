# Application group

The application group is the single named set of applications Posato can
pause. A user names it, edits or removes the name, and sees that the name may
sync while the chosen applications stay on the device.

## Sub-features

- `group-add` saves a group name and replaces the editor with the group row.
- `group-reject` keeps an empty, too long, or control-character name out and
  explains why under the field.
- `group-edit` changes the name through `Edit` and `Save change`.
- `group-remove` deletes the group and shows the editor again.
- `group-persist` shows the same name after a relaunch and in the local
  database.

## How to get to it (user POV)

- Open Posato on any target, tap `Paused items` in the switch at the top (the
  app opens on `Session`), and read the `Applications` section.
- Without a group the section shows `No application group yet.`, the
  `Application group name` field (placeholder `Social feeds`), the note `The
  group name may sync. App choices stay on this device.`, and `Add group`.
- With a group the section shows one row with the name, the note `Apps still
  need to be chosen on this device.` (until applications are chosen on that
  Mac or iPhone), and `Edit` and `Remove`.
- Press Return in the field or tap `Add group` to submit.

## Driving it with posato-control

Preconditions:

- The app is launched through the CLI, the `Paused items` readiness wait returned `ok`, and `tap -t <target> --text "Paused items" --role button` followed by the `Add website` wait returned `ok`.
- No group exists: `find -t <target> --text "No application group yet."` returns one element. On the desktop the developer may keep a group; remove it only with their agreement and re-add it at the end.

- **Add.** Type a name and submit with Return. Run `$PC type -t <target> --role textField --near-text "Applications" --input "Social feeds" --clear --submit`. `wait --for exists --text "Social feeds" --role text` returns `ok`, the editor disappears, and `find --text "Apps still need to be chosen on this device."` returns at least one element.
- **Edit.** Choose `Edit` on the group row. Run `$PC tap -t <target> --text Edit --role button --near-text "Social feeds"`. The field shows `Social feeds` with `Save change` and `Cancel`. Run `$PC type -t <target> --role textField --near-text "Applications" --input "Games" --clear --submit`; `wait --for exists --text Games --role text` returns `ok`.
- **Persist.** Relaunch and read back. Run `$PC launch -t <target>` and `$PC wait -t <target> --for exists --text Games --role text`. On `desktop` and `sim` also run `$PC db query -t <target> --sql "select canonical_name from application_policy"`; the row contains `Games`. On `device` the `wait` is the read-back.
- **Remove.** Choose `Remove` on the row. Run `$PC tap -t <target> --text Remove --role button --near-text Games`. `wait --for exists --text "No application group yet."` returns `ok` and the `Add group` button is back.
- **Reject.** Submit an empty name last. Run `$PC type -t <target> --role textField --near-text "Applications" --input "" --clear --submit`. `find --text "Enter an application group name."` returns one text element and no row appears; relaunch afterwards because the keyboard stays open on iOS.
- **Proof.** Run `$PC screenshot -t <target> --name group-populated` and `$PC snapshot -t <target> --format text --human`; the artifacts show the `Applications` section with the group row.

## Gotchas

- Only one group exists; while it exists the editor is gone, so an add recipe
  must start from the empty state or remove the row first.
- Anchor the field on the `Applications` header; `--near-text "Add group"`
  only works while the keyboard is down, and `--index 0` fails on a desktop
  that already shows a group row.
- On the iPhone the keyboard can cover `Add group`; submit with `--submit`. A
  rejected submit keeps the keyboard open, so relaunch before the next step.
- Removing the group keeps device-only application choices; the row text
  `These device-only choices are retained without an application group.` may
  appear on a Mac that had chosen applications.
- The desktop developer data usually contains a group; do not remove it
  without agreement, and re-add the original name when finished.
