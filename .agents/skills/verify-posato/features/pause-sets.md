# Pause sets

Pause sets replaces Paused items. Its root is a list of every set on this
device; a set's row opens that set's Websites and Apps. The first set shows as
"My set" until renamed. Behavior: `DESIGN.md` "Release 1.3 pause sets" and
`docs/product/pause-sets-decisions.md`.

## Recipes

- `pause-sets-desktop.json` (Mac, after a linked onboarding with the helper
  and Set up this Mac): the update notice on the first visit, New set with a
  website, Make default, Rename, a schedule saved off with the default set
  preselected, the set choice in Session setup and `Set: <name>` in Review,
  Delete with **Move to My set and delete**, the limit of ten with **New set** disabled,
  and no notice after a relaunch. A schedule saved on can start while the run
  continues; the recipe saves it off for that reason.
- `pause-sets-device.json` (test iPhone): "Apps on this iPhone" wording, a new
  set with a website, its choice in Session setup and Review, and its deletion.

## Driving notes

- List rows are buttons labelled with their whole text; query them with
  `textContains` and role `button` (`"Work"`, `"My set"`). The sidebar button
  `Pause sets` also contains "Pause set", so query the setup row as
  `textContains "Pause set,"`.
- On the Mac each row and the set screen have `More actions for <name>` with
  Rename, Make default (not on the default), and Delete (not on the default).
  On iOS a row slides out Rename and Delete under `swipeLeft`, and the set
  screen's bar keeps `More actions for <name>` with all three.
- The set choice is a `Pause set, <current>` button (on iOS a system pop-up
  labelled `Pause set` with the set as its value) whose menu rows read
  `<name>, <n> websites`, the default as `<name>, Default · <n> websites`.
- Delete is refused while a running session or schedule uses the set; the
  set screen then says removed items stay paused until that pause ends.
- A run that links iCloud ends with Remove workspace before `vm destroy`.
