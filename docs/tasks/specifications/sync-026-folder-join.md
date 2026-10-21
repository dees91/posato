# `SYNC-026`: Join a folder workspace in two steps, in either order

- **Review tier:** `standard`
- **Tier reason:** The expanded Folder sync row and the way it calls the
  existing join: a read-only check of whether the chosen folder holds a
  workspace, and an automatic join with quiet retries while the section is
  open. The bootstrap, pairing format, key handling, status values, and
  persisted schema stay as they are. Escalate to High-risk if the fix needs
  any of them.
- **Dependencies:** `SYNC-025` (#183, the folder wording this row builds
  on). `DOCS-006` and `RELEASE-007` wait for this row.
- **Integration group:** PR-FOLDER-JOIN
- **Authority:** [Release roadmap](../release-roadmap.md) revision 25, row
  `SYNC-026`; idea 48 in the
  [wiki idea queue](../../wiki/topics/mvp-open-questions.md);
  [ADR 0010](../../decisions/0010-linux-android-and-folder-workspace.md);
  the clickable mockup the maintainer accepted on 2026-10-21
  (`user-confirmed`).
- **Record:** [execution record](../executions/sync-026-folder-join.md)

## Outcome

A person links a device to a folder workspace in two steps they can do in
either order: choose the shared folder, and scan the QR code shown on the
other device with the phone's own camera or type the code. The device joins
as soon as it has both, without another button. While the folder has not
caught up, Posato says so and keeps checking while the section is open. An
empty folder offers to create the workspace instead. Once linked, the row is
calm: status, last sync, the folder, one primary **Add a device**, a quiet
**Sync now**, and a quiet red **Remove workspace**.

## Boundaries

- Scanning stays a hint: Posato never uses the camera. The system camera
  opens the `posato://join` link, which fills in the code. A computer shows
  only "type the code".
- A code from a link before a folder is chosen marks step 2 done and keeps
  step 1 open. A code is used once, as before.
- Retries: only "not in the folder yet" and "folder unavailable" wait, with a
  growing delay, while the section is shown, and stop after 10 minutes with
  the not-found message. Invalid, expired, refused, and wrong-workspace codes
  stop at once and show under the code field.
- An empty folder never joins or creates on its own: with no code it offers
  **Create the workspace**; with a code it waits for the folder.
- The removed-copy explanation from `SYNC-025` stays.
- Non-goals: an in-app scanner, the pairing dialog's content, iCloud mode,
  and how the folder is chosen.

## Acceptance

- `AC-01`: Tests written first fix the step state for each order (folder
  first, code first, empty folder, terminal error) and the join policy (wait
  and retry on not found, stop on terminal results, give up after 10
  minutes).
- `AC-02`: On the Android phone: a scanned link before choosing the folder
  joins as soon as the folder is chosen; a typed code after choosing the
  folder joins; a code whose offer has not arrived waits, then joins once it
  arrives; an expired code shows its error under the field. Light and dark
  screenshots of each step.
- `AC-03`: A computer (Linux clone) joins by a typed code.
- `AC-04`: `DESIGN.md` records the layout as `user-confirmed` (2026-10-21).

## Verification

- `shared` tests for `FolderJoinFlow`.
- `posato-control` on the Galaxy XCover7 (`openURL` of a `posato://join`
  link, `flow folder` with `relay`), and a Tart Ubuntu clone joining by code.

## Decisions or blockers

- None open. Copy follows `DESIGN.md`; the mockup's text was a draft.
