# iOS application mappings

On an iPhone running a development-signed or TestFlight build a person grants
Screen Time access and chooses individual applications through Family Controls.
The shared UI knows only a count, not application names. The Simulator reports
this capability as unavailable.

## Sub-features

- `ios-mapping-access` reports not-determined, denied, restricted, and unavailable
  states without implying authorization succeeded.
- `ios-mapping-choose` opens Choose apps without requiring an existing group.
- `ios-mapping-count` shows the private selection count and Clear selection.
- `ios-mapping-retain` keeps prior choices after Cancel, rejected category/domain
  selection, or authorization loss before Save.
- `ios-mapping-automatic-group` creates Applications after a successful nonempty
  selection only when metadata is absent.
- `ios-mapping-empty` shows Make room beyond the browser.

## How to get to it (user POV)

- Open Paused items from the bottom navigation and select Apps.
- Read On this iPhone only and the real access state, then tap Choose apps.
- Allow Screen Time access if requested, choose individual applications, and
  Save. Cancel keeps the previous selection.
- Clear selection removes device choices; it does not delete group metadata.

## Driving it with posato-control

Preconditions:

- Target device, connected and unlocked, with development-team configuration
  in ignored local.properties. Require doctor to pass and a Debug iphoneos build.
- Record existing choices. No manual group creation or token injection is needed.

- **Open Apps:** `$PC tap -t device --text "Paused items" --role button`, then
  `$PC tap -t device --text-contains Apps --role button`.
- **Open picker:** Put `{"action":"tap","query":{"text":"Choose apps","role":"button"}}`
  and a screenshot in the same scenario. With undetermined authorization,
  capture the system consent alert before another XCUITest invocation dismisses it.
- **Grant and select:** Ask the maintainer to approve the system alert and
  choose individual applications. The consent alert belongs to SpringBoard
  and the selection list is rendered out of process; neither is reliably
  represented by the app's accessibility tree. Stop when this human action
  is required; do not substitute a fixture or captured token file.
- **Save or cancel:** The app-owned toolbar is drivable with
  `$PC tap -t device --text Save --role button` or the same command for Cancel.
- **Read back:** Capture the app's count with `$PC snapshot -t device --format text --human`
  and `$PC screenshot -t device --name ios-mappings`. Confirm the Apps-tab
  count matches the private-selection row. A cancellation leaves it unchanged.
- **Persist:** Relaunch without fresh, re-enter Apps, and capture the same count.
  After a fresh launch or reset, run `first-install-skip.json` first (see
  [First install](./onboarding.md)).
  The device database is not readable through the driver.
- **Clear:** `$PC tap -t device --text "Clear selection" --role button`.
  Expect Make room beyond the browser and an Apps count of zero.
- **Unavailable:** On Simulator, open Apps and capture
  Choosing apps is not available in this version of Posato. The picker must
  not open; this is not proof of the device-only path.

## Gotchas

- A new XCUITest invocation can dismiss a system alert. Capture it in the same
  scenario as the action that presents it, then request the human step.
- An unchanged accessibility tree does not prove no consent alert appeared.
  Inspect the screenshot; system copy follows the device language.
- Reinstalling can reset authorization and application-container data.
  Do not reinstall or reset a user's device as routine cleanup.
- The maintained store is in the App Group container; the old private-container
  location is only a migration source. Historical captured-token restoration
  is not proof of current selection, automatic group creation, or enforcement.
- Category/web-domain selection is rejected with Choose individual applications
  only and must preserve prior application choices.
- A native selection saved before metadata failure stays on the phone; use
  Enable selected apps after resolving the real failure, not a second picker.
- Simulator runs cannot verify the Family Controls picker.
- A TestFlight build is driven after its installation from TestFlight: skip
  `build` and `install`, because `install -t device` would replace it with a
  development build, and `reset -t device` uninstalls whichever build is present.
