# Application group

The singleton application group is now created automatically after a successful
nonempty native app selection. Existing custom names remain stored unchanged.
The UI focuses on choosing applications, not naming or managing group metadata.

## Sub-features

- `group-automatic` creates Applications when the first nonempty selection saves.
- `group-preserve` leaves an existing name unchanged.
- `group-cancel` creates nothing for cancellation or an empty first selection.
- `group-partial-failure` retains saved device choices if metadata saving fails.
- `group-recovery` offers Enable selected apps without reopening the picker.
- `group-persist` retains metadata across a normal relaunch.

## How to get to it (user POV)

- Open Paused items, select Apps, and choose apps using the native picker.
- Successful nonempty selection activates the group automatically.
- If the selection saved but its group did not, read the inline notice and
  use Enable selected apps after resolving any policy conflict.
- There is no Add group, group-name editor, or group Remove action in this UI.

## Driving it with posato-control

Preconditions:

- Use a development-signed Mac or iPhone with the native picker available;
  the Simulator cannot prove native selection or automatic group creation.
- Record existing names and choices. Do not delete a user's group to manufacture
  an empty state. Test fixtures cover deterministic failures and conflicts.

- **Open:** `$PC tap -t <target> --text "Paused items" --role button`, then
  `$PC tap -t <target> --text-contains Apps --role button`.
- **Choose:** `$PC tap -t <target> --text "Choose apps" --role button`.
  Follow the platform mapping recipe to make a real nonempty selection.
- **Verify on Mac:** `$PC db query -t desktop --sql "select canonical_name from application_policy"`.
  A previously absent row now contains Applications; an existing name is unchanged.
- **Cancel:** Open the picker again and cancel through its native controls.
  Read back the prior count and name; neither should change.
- **Recover when exposed:** `$PC tap -t <target> --text "Enable selected apps" --role button`.
  The notice disappears after metadata saves; the selected applications remain.
  Do not inject a database failure just to expose the notice.
- **Persist:** Relaunch normally, open Apps, and confirm the same selected count
  and available controls. On iPhone this proves the visible result only; tests
  and Mac read-back cover the metadata name.
- **Restore:** Remove only applications selected for this run through their
  menu or Clear selection. Clearing choices does not delete group metadata.

## Gotchas

- Metadata and native choices have separate persistence boundaries. A partial
  failure is not permission to discard the native selection or report success.
- Reading retained choices does not silently create the group; recovery is explicit.
- The former manual group CRUD capability remains in the model, not in the
  new user interface. Do not describe it as a current drivable path.
- Never seed tokens or write policy rows to claim this flow passed.
