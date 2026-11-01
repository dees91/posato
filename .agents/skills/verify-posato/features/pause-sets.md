# Pause sets

Pause sets replaces Paused items. Its root is a list of every set on this
device; a set's row opens that set's Websites and Apps. The first set shows as
"My set" until renamed. Behavior: `DESIGN.md` "Release 1.3 pause sets" and
`docs/product/pause-sets-decisions.md`.

## Recipes

- `pause-sets-desktop.json` (Mac, after onboarding with the helper and Set
  up this Mac; iCloud is not needed): New set with a website, Make default,
  Rename, a schedule saved off with the default set preselected, the set
  choice in Session setup and `Set: <name>` in Review, Delete with **Move to
  My set and delete**, the limit of ten with **New set** disabled, and the
  list after a relaunch. A schedule saved on can start while the run
  continues; the recipe saves it off for that reason.
- `pause-sets-notice-desktop.json` (Mac, right after `flow icloud link`,
  before any scenario or `flow set` opens Pause sets): the one-time "Update
  Posato on your other devices" notice on the first visit and no notice after
  a relaunch. The first linked visit uses up the notice whatever opened the
  screen. Run it only when iCloud is in scope (`SKILL.md`, "When to link
  iCloud").
- `pause-sets-device.json` (test iPhone): "Apps on this iPhone" wording, a new
  set with a website, its choice in Session setup and Review, and its deletion.

- Export and import (`TARGETS-007`, every target but the Simulator): `flow
  export --code [--out F]` returns the code and keeps the file in the run's
  evidence; `flow import --file F --code C --mode combine|separate|replace
  [--turn-on]` returns the review's line and the result, or `refused` with
  its reason. On a Mac push the file first (`vm push --to name.posato`) and
  pass `/Users/posato/name.posato`; a panel left open by a failed run stays
  open (presses bypass its modality), so close it with `CancelButton` before
  the next run. Proof for a coded file: it opens with the code outside
  Posato too (PBKDF2, HKDF, AES-GCM as in the record). For the refusal,
  start a pause on a set the file lacks with `flow session`, then replace.
  A file sealed with another round count does not open by design.

- Website windows (`TARGETS-013`, desktop and Android; iOS later): `flow set
  --name Work --website window.example --website blocked.example --window
  window.example=30/5` creates the set and sets the rule through the row's
  **Open briefly…** dialog, waiting for the row caption "Opens every 30 min
  for 5 min"; `--window window.example=off` presses **Don't open**. Then
  `flow session --set Work` and `run --scenario
  fixtures/scenarios/website-window-session-desktop.json`: Session lists
  `window.example` with "Opens at HH:MM" or "Open until HH:MM" (the row's
  accessible label is "window.example, opens at 10:30") and no row for the
  unruled `blocked.example`. Clearing the rule during the session removes the
  row within a second. The times follow the guest's clock (`vm exec date`);
  whether a browser load is blocked at those times is phase 3's enforcement
  rows, not this recipe.

## Driving notes

- List rows are buttons labelled with their whole text; query them with
  `textContains` and role `button` (`"Work"`, `"My set"`). The sidebar button
  `Pause sets` also contains "Pause set", so query the setup row as
  `textContains "Pause set,"`.
- On the Mac each row and the set screen have `More actions for <name>` with
  Rename, Make default (not on the default), and Delete (not on the default).
  A website row's menu is `Actions for <website>` with Edit, Open briefly…
  (`targets_open_briefly`), and Remove; the dialog's choices are
  `targets_window_period_30|60|120` and `targets_window_length_5|10|15`, then
  `action_save_short` or `targets_window_clear`.
  On iOS a row slides out Rename and Delete under `swipeLeft`, and the set
  screen's bar keeps `More actions for <name>` with all three.
- The set choice is a `Pause set, <current>` button (on iOS a system pop-up
  labelled `Pause set` with the set as its value) whose menu rows read
  `<name>, <n> websites`, the default as `<name>, Default · <n> websites`.
- Delete is refused while a running session or schedule uses the set; the
  set screen then says removed items stay paused until that pause ends.
- A run that links iCloud ends with Remove workspace before `vm destroy`.
