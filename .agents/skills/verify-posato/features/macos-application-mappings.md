# macOS application mappings

On a Mac, a user chooses which locally installed applications belong to the
application group through a native picker, sees the chosen applications listed
under `Applications`, and removes them one by one. The choices never leave the
device. The picker is fully drivable; nothing here needs a human.

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
- The staged package is development-signed: `posato.macos.signingIdentity` and
  `posato.macos.syncProvisioningProfile` are set in the ignored
  `local.properties`, and `doctor -t desktop` reports `desktop.staged`,
  `desktop.signingIdentity`, and `desktop.syncProfile` as ok. An ad-hoc package
  refuses to start the helper and the button reports `The application picker
  could not be opened.`. `./gradlew quality` restages ad-hoc, so run
  `$PC build -t desktop` after it.
- Accessibility and Screen Recording are granted to the agent's host.
- The picker steps address the helper process, so the Mac must not be locked
  and no other application may steal the front while the panel is open; the
  driver brings the helper forward itself and refuses rather than typing
  blindly if it cannot.

- **Empty state.** Read the section. Run `$PC find -t desktop --text "No applications chosen on this device."`; one text element is returned.
- **Open the picker.** Choose the button. Run `$PC tap -t desktop --text "Choose applications" --role button`. A native open panel appears, owned by the helper process, and the Posato window stays where it was.
- **Wait for the panel.** Run `$PC wait -t desktop --process PosatoMacOSHelper --for exists --timeout-seconds 30`. With no query this waits for the helper to own a visible window, which is the readiness gate; the panel has no accessibility tree to wait on.
- **Pick an application.** Drive the panel by keyboard only, with no coordinates and no localized labels:

  ```shell
  $PC press -t desktop --process PosatoMacOSHelper --key g --modifiers cmd,shift
  $PC type  -t desktop --process PosatoMacOSHelper --input "/Applications/Safari.app" --clear --submit
  $PC press -t desktop --process PosatoMacOSHelper --key return
  ```

  `⌘⇧G` opens the panel's go-to-folder field, `--clear --submit` replaces any remembered path and resolves this one, and the second `Return` confirms `Choose`. Exactly two returns are needed: `--submit` resolves the path, the second confirms. Choose any installed application except Posato itself.
- **Confirm the panel closed.** Run `$PC wait -t desktop --process PosatoMacOSHelper --for absent --timeout-seconds 20`. A queryless `--process` wait accepts `exists` and `absent` only; the helper exits with its panel, and a selector that no longer resolves counts as absent. The earlier `--for exists` is what makes this meaningful: on its own, `absent` is also satisfied by a name that never resolved at all.
- **List and remove.** Run `$PC wait -t desktop --for exists --text Safari`; the row with the application name and its `Remove` button appears in the application's own tree. Run `$PC tap -t desktop --text Remove --role button --near-text Safari`; `wait --for exists --text "No applications chosen on this device."` returns `ok`.
- **Persist.** Run `$PC db query -t desktop --database macos-application-mappings.db --sql "select count(*) as mappings from localApplicationMapping"` before and after removal; the count goes from 1 to 0 (the stored columns are opaque blobs, so read only the count).
- **Proof.** Run `$PC screenshot -t desktop --process PosatoMacOSHelper --name panel-open` while the panel is up, and `$PC screenshot -t desktop --name mappings` after the pick and after the removal. The panel screenshot needs the selector because the panel sits above the application's window layer.

## Gotchas

- The panel has no accessibility tree (`observed`, 2026-09-04). The helper
  presents it with `NSOpenPanel.runModal()` and never runs an `NSApplication`
  event loop, so it owns a real window while exposing no accessibility server:
  `snapshot` and `find` with `--process PosatoMacOSHelper` fail with
  `PROCESS_NOT_INSPECTABLE`. That is the expected answer, not a broken
  selector. Drive the panel by key events and read the result in the
  application's own tree.
- `PosatoMacOSHelper` is not a picker-only process: since `MACOS-004` the same
  helper also runs the loopback proxy and the browser-domain session. A helper
  that is already running, or one whose window never appears, may be doing
  enforcement work rather than presenting a panel, so treat the queryless
  `--for exists` wait as the only readiness signal and never infer a picker
  state from the process being alive.
- The helper must be frontmost for those key events, because a process with no
  accessibility server cannot receive a per-process post. The driver brings it
  forward itself and refuses with `PROCESS_NOT_ALLOWED` if it cannot, so a
  keystroke never lands in a window you did not address. Do not click into
  another application while the panel is open.
- With an ad-hoc signed package (`doctor` warns on `desktop.staged`) the
  helper is refused and the section shows `The application picker could not
  be opened.`; that message is the expected outcome, not a defect.
- Choosing Posato itself is rejected with `Posato cannot be added to its own
  application group.`.
- `Remove` also exists in the group row and in every website row; keep
  `--near-text <application name>`.
- Chosen applications are device-only: a Mac shows only its own choices, and
  an iPhone shows only the ones chosen on that iPhone.
