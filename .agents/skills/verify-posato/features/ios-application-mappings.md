# iOS application mappings

On an iPhone a person allows Screen Time access, chooses applications with the
system Family Controls picker, and sees how many applications are selected on
that device. The choices are opaque platform tokens: the app never learns the
application names, the count is the only shared detail, and nothing leaves the
device. The capability exists only in a development-signed Debug device build;
the Simulator and Release builds report it as unavailable.

## Sub-features

- `ios-mapping-gate` shows the selection controls only once an application
  group exists, mappings exist, or loading failed.
- `ios-mapping-access` maps live Screen Time authorization to `Allow Screen
  Time access to choose apps. Your choices stay on this device.`
  (not determined), `Screen Time access is off for Posato. Enable it in
  Settings to review chosen apps.` (denied), `Screen Time access is not
  available for this account or device.` (restricted), and `Choosing apps is
  not available in this version of Posato.` (Simulator and Release).
- `ios-mapping-choose` opens the system picker from `Choose applications`,
  `Review applications`, or `Allow and review applications`.
- `ios-mapping-count` shows `Applications selected: <n>` with `Clear selection`.
- `ios-mapping-empty` shows `No applications chosen on this device.`
- `ios-mapping-retain` keeps the prior selection on cancel, on a category or
  web-domain selection (`Choose individual applications only.`), and when
  authorization is lost before `Save`.

## How to get to it (user POV)

- Open the app on a development-signed iPhone build, tap `Paused items` in
  the switch at the top, and add an application group under `Applications`; the selection controls appear beneath the group
  row with the access sentence as the row's supporting text.
- Tap `Choose applications` to grant Screen Time access and open the picker,
  or `Review applications` once applications are already selected.
- Tap `Clear selection` to remove every choice on that device.
- On the Simulator and in Release builds the same section reports the
  capability as unavailable and opens no picker.

## Driving it with posato-control

Preconditions:

- Target `device` only, with `posato.apple.developmentTeam` in the ignored
  `local.properties`, the iPhone connected and unlocked, and `doctor -t device`
  reporting `ok: true`. The Family Controls entitlement and the picker code
  path exist only in the Debug `iphoneos` build.
- An application group must exist first, otherwise the whole section is hidden
  by design.

- **Reveal the section.** Add a group. Run `$PC type -t device --role textField --near-text "Applications" --input "Social feeds" --clear --submit`, then `$PC wait -t device --for exists --text "Social feeds" --role text`. `snapshot` now lists the access sentence, `No applications chosen on this device.`, and the `Choose applications` button.
- **Open the picker.** Run the tap inside a scenario, never as a single command: `{ "action": "tap", "query": { "text": "Choose applications", "role": "button" } }` followed by a `sleep` and a `screenshot`. With authorization not determined the screenshot shows the system Screen Time consent alert.
- **Grant access and pick applications.** This step is manual: the consent alert belongs to SpringBoard and the picker list is rendered out of process, so neither appears in the driver's accessibility tree. Ask the maintainer to accept the alert and select applications, or report the path as unreachable with the command above.
- **Or seed a captured selection instead of picking.** A selection captured once can be restored, so the picker is not needed on every run (`observed`, 2026-09-04; see the seeding rule in the gotchas). Consent still is: the Screen Time authorization resets to not determined on every reinstall.
- **Confirm or dismiss the picker.** `Save` and `Cancel` are the app's own toolbar buttons and are drivable: `$PC tap -t device --text Save --role button` and `$PC tap -t device --text Cancel --role button`.
- **Read the result.** Run `$PC wait -t device --for exists --text-contains "Applications selected:"`, then `$PC screenshot -t device --name ios-mappings`. Cancel instead leaves the previous count unchanged.
- **Persist.** Run `$PC launch -t device` (no `--fresh`) and `$PC find -t device --text-contains "Applications selected:"`; the device database is not readable, so this relaunch read-back is the side-effect proof.
- **Clear.** Run `$PC tap -t device --text "Clear selection" --role button`, then `$PC wait -t device --for exists --text "No applications chosen on this device."`.

## Gotchas

- Every single command costs an XCUITest launch, which dismisses any system
  alert that is on screen. A tap whose result is a system alert must be in the
  same scenario as the screenshot that captures it, or the evidence is lost.
- The consent alert and the picker list are not in the app's accessibility
  tree; a `snapshot` after the tap looks unchanged even though the alert is on
  the device. Never conclude from the tree alone that no alert appeared.
- System alert copy follows the device language, not the app's; match on the
  screenshot, not on English text.
- The section is hidden entirely without an application group; an empty
  `Applications` section is the expected state, not a defect.
- Reinstalling returns Screen Time authorization to not determined and destroys
  the whole data container, including the stored selection, the policy database
  and its websites and group; an ordinary relaunch preserves everything.
- A captured selection can be seeded back, which removes the picker from a
  rerun but not the consent alert (`observed`, 2026-09-04, iOS 26.5.2). The
  store is
  `<container>/Library/Application Support/Posato/ApplicationMappings/mappings-v1.json`,
  one JSON object with a `version` and one opaque `token` string per selected
  application. Capture and restore it with the app not running:

  ```shell
  xcrun devicectl device copy from --device <udid> \
    --domain-type appDataContainer --domain-identifier app.posato.ios \
    --source "Library/Application Support/Posato/ApplicationMappings/mappings-v1.json" \
    --destination <untracked directory beside local.properties>/mappings-v1.json
  xcrun devicectl device copy to --device <udid> \
    --domain-type appDataContainer --domain-identifier app.posato.ios \
    --source <the same file> \
    --destination "Library/Application Support/Posato/ApplicationMappings/mappings-v1.json"
  ```

  `copy from` needs a destination file path, not a directory. `copy to` writes
  the file as the app's own user with mode `0644`, and the app accepts it: after
  a full uninstall, reinstall, and restore, the section reported
  `Applications selected: 1` with `Clear selection`. The application group must
  exist first, otherwise the section stays hidden and you will read the seeding
  as failed. Keep the captured file untracked: it carries live selection tokens.
- What seeding does not prove (`open`): the count read-back shows the app loads
  and validates the restored tokens, not that the tokens still resolve to the
  same applications for enforcement in a new installation. Screen Time
  authorization is not determined at that point, so enforcement cannot be
  exercised anyway. `IOS-001` settles that question.
- The store moves into the App Group container with `IOS-001`. The commands
  above then need `--domain-type appGroupDataContainer` with the group
  identifier; `devicectl` supports both domains, so only the domain and the path
  change.
- Selecting a category or a web domain is rejected with `Choose individual
  applications only.` and keeps the previous applications.
- Simulator and Release builds never open a picker; do not report the iOS
  picker as verified from a Simulator run.
