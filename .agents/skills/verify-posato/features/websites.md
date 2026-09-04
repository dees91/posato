# Websites

Websites lets a user keep a list of exact domains that Posato will pause: add a
domain, see why an entry was rejected, edit or remove an existing row, and find
the list unchanged after the app restarts.

## Sub-features

- `website-add` saves a valid exact domain as a new row.
- `website-edit` changes an existing domain through `Edit` and `Save change`.
- `website-cancel-edit` leaves the row unchanged through `Cancel`.
- `website-reject` keeps invalid, duplicate, or empty input out of the list and
  explains why under the field.
- `website-persist` shows the same rows after the app is relaunched and in the
  local database.
- `website-remove` deletes a row and restores `No websites added` when the list
  becomes empty.

## How to get to it (user POV)

- Open Posato on any target and tap `Paused items` in the switch at the top;
  the app opens on `Session` after every launch.
- Scroll to the `Websites` section: an `Exact domain` field (placeholder
  `example.com`), the helper text `Enter an exact domain such as example.com,
  not a full URL.`, the `Add website` button, and one row per saved domain
  with `Edit` and `Remove`.
- Press Return in the field or tap `Add website` to submit.

## Driving it with posato-control

Preconditions:

- The app is launched through the CLI, `wait -t <target> --for exists --text "Paused items" --role button --timeout-seconds 30` returned `ok`, then `tap -t <target> --text "Paused items" --role button` and `wait -t <target> --for exists --text "Add website" --timeout-seconds 30` returned `ok`.
- No row named `example.com` or `example.org` exists (`find -t <target> --text example.com` returns an empty list).
- `PC` points at the installed CLI; replace `<target>` with `sim`, `desktop`, or `device`. The domain field is always `--role textField --near-text "Websites"`.

- **Add.** Type a domain and submit with Return. Run `$PC type -t <target> --role textField --near-text "Websites" --input example.com --clear --submit`. Then `$PC wait -t <target> --for exists --text example.com --role text` returns `ok` and `$PC find -t <target> --text "No websites added"` returns an empty list.
- **Add through the fixture.** Run `$PC run -t <target> --scenario tools/posato-control/fixtures/scenarios/add-website.json` instead of the step above when a relaunch is acceptable. Every step reports `ok: true`; the artifacts include `screenshot-8-after-add.png` and `snapshot-9-after-add.json` showing the `example.com` row.
- **Edit.** Choose `Edit` on the row. Run `$PC tap -t <target> --text Edit --role button --near-text example.com`. `$PC find -t <target> --text "Save change" --role button` returns one element. Run `$PC type -t <target> --role textField --near-text "Websites" --input example.org --clear --submit`; `wait --for exists --text example.org --role text` returns `ok` and `find --text example.com --role text` is empty.
- **Cancel edit.** Choose `Edit` on `example.org`, then `Cancel`. Run `$PC tap -t <target> --text Edit --role button --near-text example.org` and `$PC tap -t <target> --text Cancel --role button`. `find --text example.org --role text` still returns one element and `find --text "Add website" --role button` returns one element.
- **Reject.** Submit an invalid value, then a duplicate. Run `$PC type -t <target> --role textField --near-text "Websites" --input "not a domain" --clear --submit`; `find --text "Enter a valid domain with at least two labels."` returns one text element and no row named `not a domain` appears. Run `$PC type -t <target> --role textField --near-text "Websites" --input example.org --clear --submit`; `find --text "That exact domain is already in the list."` returns one element. A rejected submit keeps the keyboard open on iOS, so continue with the relaunch below.
- **Persist.** Relaunch and read back. Run `$PC launch -t <target>` (no `--fresh`) and `$PC wait -t <target> --for exists --text example.org --role text`. On `desktop` and `sim` also run `$PC db query -t <target> --sql "select canonical_domain from exact_domain_policy"`; the rows contain `example.org`. On `device`, the `wait` after the relaunch is the read-back.
- **Proof.** Capture the populated state. Run `$PC screenshot -t <target> --name websites-populated` and `$PC snapshot -t <target> --format text --human`; the artifacts show `Paused items` and the `example.org` row.
- **Remove.** Choose `Remove` on the row. Run `$PC tap -t <target> --text Remove --role button --near-text example.org`. `$PC wait -t <target> --for absent --text example.org --role text` returns `ok`; when it was the last row, `find --text "No websites added"` returns one element. The fixture `remove-website.json` does the same for `example.com`.

## Gotchas

- On the iPhone and Simulator the `Add website` button is hidden behind the
  keyboard while the field has focus and loses its label; always submit with
  `--submit` (Return triggers the same action) instead of tapping the button.
- A rejected submit keeps the field focused and the keyboard open, which
  hides every row and button below the field; relaunch before touching them.
- Anchor the field on the `Websites` header. `--near-text "Add website"` only
  works while the keyboard is down, `--index` picks the group field on iOS,
  and the desktop field has no label at all.
- `Edit` and `Remove` also exist in the application group row; without
  `--near-text` the first match may belong to the group. `near` prefers the
  control on the anchor's own row, so it stays correct with several rows.
- Rows are sorted alphabetically by domain; with two or more rows the
  alphabetically last row sits at the bottom edge on an iPhone-sized screen,
  where Compose drops the labels of clipped buttons. `tap`, `type`, and
  `snapshot` with a query scroll such a row into view when the `--near-text`
  anchor is visible; `wait` and `find` do not scroll, so read a clipped row
  after a `tap` on it or after `scrollTo` in a scenario.
- While a mutation is saving, a `Saving` label appears next to the button and
  the controls are disabled; `wait --for enabled --text "Add website"` before
  the next action if a step fails with a disabled element.
- Desktop `launch --fresh` deletes the developer's real local databases; add
  and remove rows instead, or restore from the run's `backup/desktop/`.
