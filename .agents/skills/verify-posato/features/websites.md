# Websites

Websites stores canonical exact domains. A person can add one domain or paste
a batch of domains and HTTP(S) URLs, search the saved list, edit or remove rows,
and leave an unfinished draft while visiting Session. Session-driven blocking
is covered by the session recipes; saved websites synchronize across linked
devices joined to one workspace (see Sync with iCloud).

## Sub-features

- `website-add` saves all valid unique entries in one revision, including the
  `www` counterpart of each accepted host as a second exact-domain row.
- `website-batch` discards URL paths, queries, and fragments before storage;
  reports added/duplicate counts and retains rejected entries in the draft.
- `website-draft` keeps newer text when an earlier save completes, keeps the
  whole draft on save failure, and retains it across in-app navigation.
- `website-search` filters without altering the add draft and resets list position.
- `website-edit` uses the row menu, Edit, Save changes, and Cancel.
- `website-remove` removes only the selected row.
- `website-persist` reads saved domains after relaunch; drafts are not persisted.
- `website-long-list` reaches offscreen rows without expanding Session's summary.

## How to get to it (user POV)

- Choose Paused items in the iOS bottom navigation or macOS sidebar.
- Websites is initially selected. Add websites is the primary field, with Add
  inside it; Return submits, Shift-Return inserts a line break on desktop.
- Search temporarily replaces the add field; Back to adding restores its draft.
- Tap Done to dismiss the keyboard. Open a row's ellipsis for Edit or Remove.

## Driving it with posato-control

Preconditions:

- Launch through the CLI, require doctor to pass, open Paused items, and wait
  for Search. Select `--text-contains Websites --role button` if Apps is open.
- Reserve `example.com`, `example.org`, and `design-proof-01.example` through
  `design-proof-50.example` for the matching fixture; do not remove pre-existing
  user rows with these names. Record existing data before a mutation.
- The edit fixture additionally reserves `edit-proof.example` and
  `changed-proof.example`; start with neither present and a short visible list.

- **Add:** `$PC type -t <target> --role textField --input example.com --clear --submit`,
  then `$PC tap -t <target> --text Done --role button`. Expect both
  `example.com` and `www.example.com`. Run the add-website fixture for a
  complete sequence with screenshot, snapshot, and real scrollTo.
- **Batch and long list:** `$PC run -t <target> --scenario tools/posato-control/fixtures/scenarios/website-batch-list.json`.
  It leaves the rejected `invalid` text in the field, reaches both list ends,
  filters one row, and verifies the draft after switching destinations.
- **Edit:** `$PC tap -t <target> --text "Actions for example.com" --role button`,
  then `$PC tap -t <target> --text Edit --role button`.
  Type `example.org` with `--role textField --clear --submit`; expect the
  renamed row. Cancel instead leaves the saved row unchanged.
- **Editor and keyboard regression:** Run `website-edit.json` on a small iPhone
  with its software keyboard, and on the other hosts. It captures visible Save
  changes while typing, checks draft retention and Cancel, reads the saved edit
  after relaunch, removes its fixture, and confirms cleanup after another relaunch.
- **Search:** `$PC tap -t <target> --text Search --role button`, then
  `$PC type -t <target> --role textField --input example --clear`.
  Capture the filtered list; `$PC tap -t <target> --text "Back to adding" --role button`
  returns to the original draft.
- **Persist:** Relaunch without fresh, open Paused items again, and scrollTo
  the saved row. On Mac/Simulator also use
  `$PC db query -t <target> --sql "select canonical_domain from exact_domain_policy"`.
  The database contains domains, never the submitted URL path/query/fragment.
  After a fresh launch or reset, run `first-install-skip.json` first (see
  [First install](./onboarding.md)).
- **Remove:** Open `Actions for example.org`, then `Remove`, and wait for
  that domain to be absent. Removing one host of a `www` pair leaves the
  other. The remove-website fixtures remove `example.com` and then
  `www.example.com`.
- **Restore batch:** Run `website-batch-list-cleanup.json` from Websites.
  It removes the 50 fixture domains and their `www` counterparts through
  their menus. Confirm their absence using a database query or relaunch
  read-back on iPhone.

## Gotchas

- Return keeps focus deliberately; Done, switching category, and Search /
  Back to adding clear it. Do not relaunch just to hide the keyboard.
- Batch limits are 65,536 UTF-16 code units overall, 1,024 per trimmed entry,
  and 1,024 unique stored domains. Credentials, unsupported schemes, IPs,
  wildcards, invalid hosts, and capacity overflow are rejected.
- Duplicate entries are acknowledged, not added again. Empty separators are
  ignored; empty input does not submit.
- Failed or conflicting writes must not clear the draft. Reload after a
  revision conflict before retrying. A late acknowledgement must not erase
  text entered after submission.
- Lazy rows outside the viewport are absent from accessibility snapshots.
  Use scrollTo before opening their menu with
  `query.within: {"text":"Saved websites","role":"group"}`;
  find/wait do not move the list. Allow up to 90 seconds for a long list when
  the retained starting position requires reaching the end before reversing.
- Do not run another foreground automation while desktop text is being typed.
  Do not use a reset to clean the developer's real data.
