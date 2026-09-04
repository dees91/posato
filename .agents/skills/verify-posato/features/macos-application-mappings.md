# macOS application mappings

On a Mac, a user chooses which locally installed applications belong to the
application group through a native picker, sees the chosen applications listed
under `Applications`, and removes them one by one. The choices never leave the
device.

## Sub-features

- `mapping-choose` opens the native application picker from `Choose applications`.
- `mapping-list` shows each chosen application with a `Remove` action.
- `mapping-remove` deletes one chosen application.
- `mapping-empty` shows `No applications chosen on this device.` when none is chosen.
- `mapping-errors` explains a failed pick (`The application picker could not be opened.`, `Posato cannot be added to its own application group.`, capacity and verification messages).

## How to get to it (user POV)

- Open the desktop app and tap `Paused items` in the switch at the top; the
  `Applications` section shows `No applications chosen on this device.` and
  the `Choose applications` button, or the list of chosen applications with
  `Remove`.
- On iOS the same section has its own picker; see
  [iOS application mappings](./ios-application-mappings.md).

## Driving it with posato-control

Preconditions:

- Target `desktop` only.
- The staged package is development-signed: `posato.macos.signingIdentity` is
  set in the ignored `local.properties` and `doctor -t desktop` reports
  `desktop.staged` as ok, not a warning. An ad-hoc package refuses to start
  the helper and the button reports `The application picker could not be
  opened.`.
- Accessibility and Screen Recording are granted to the agent's host.

- **Empty state.** Read the section. Run `$PC find -t desktop --text "No applications chosen on this device."`; one text element is returned.
- **Open the picker.** Choose the button. Run `$PC tap -t desktop --text "Choose applications" --role button`. A native open panel appears, owned by the helper process, and the Posato window stays where it was.
- **Pick applications.** This step is manual: the panel belongs to
  `PosatoMacOSHelper`, a separate process the CLI does not drive. Ask the
  maintainer to select one application (not Posato) and confirm, or report the
  path as unreachable with the command above.
- **List and remove.** After a manual pick, run `$PC snapshot -t desktop --format text --human`; a row with the application name and a `Remove` button appears. Run `$PC tap -t desktop --text Remove --role button --near-text "<application name>"`; `wait --for exists --text "No applications chosen on this device."` returns `ok`.
- **Persist.** Run `$PC db query -t desktop --database macos-application-mappings.db --sql "select count(*) as mappings from localApplicationMapping"` before and after removal; the count goes from 1 to 0 (the stored columns are opaque blobs, so read only the count).
- **Proof.** Run `$PC screenshot -t desktop --name mappings` after the pick and after the removal.

## Gotchas

- The picker cannot be scripted: the open panel runs in the helper process,
  outside the Posato accessibility tree. Verify up to the button press and the
  state after a manual selection; never claim the pick itself was automated.
- With an ad-hoc signed package (`doctor` warns on `desktop.staged`) the
  helper is refused and the section shows `The application picker could not
  be opened.`; that message is the expected outcome, not a defect.
- Choosing Posato itself is rejected with `Posato cannot be added to its own
  application group.`.
- `Remove` also exists in the group row and in every website row; keep
  `--near-text <application name>`.
- Chosen applications are device-only: a Mac shows only its own choices, and
  an iPhone shows only the ones chosen on that iPhone.
