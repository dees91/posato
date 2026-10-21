# Execution: `SYNC-026`

- **Brief:** [Join a folder workspace in two steps, in either order](../specifications/sync-026-folder-join.md)
- **Status:** `in progress`
- **Review tier:** `standard`
- **Implementer:** Claude
- **Branch:** `sync-026-folder-join`, from `sync-025-folder-wording` (#183)

## Plan

1. `FolderSyncControls` gains a read-only `contents()` (no folder, empty,
   holds a workspace, unavailable) from the folder's anchor, the same read
   the pairing accept already does, and `scansCodes` (a phone, where the
   system camera opens join links).
2. A pure `FolderJoinFlow`: the step view from folder, contents, pending
   code, and the last result; and a `FolderJoiner` that accepts a code,
   waits and retries on not found or unavailable with a growing delay up to
   10 minutes, and stops on terminal results. Tests first.
3. `SyncBootstrapUiState` holds the pending code (scanned or typed); the
   folder section runs the joiner while it is shown and the folder and code
   are present.
4. Rebuild `FolderOptions`: two numbered steps, the folder chip with
   **Change**, the scan hint (phones), the code field with **Join**, the
   waiting note, errors under the field, **Create the workspace** for an
   empty folder, and the calm linked state.
5. `posato-control` flows adapted; Android runs; Linux run after the VM is
   free; `DESIGN.md`; review.

## Result

- **Tests:** `FolderJoinFlowTest` (commonTest) failed on the stub
  (`1c5fee8e` then the failing-test commit: 7 of 9 failed; the two that
  passed are guards) and passes after the implementation.
- **Product:** `FolderSyncControls.contents()` reads the folder's anchor
  without writing; `FolderJoinState` keeps the pending code (a scanned link
  stays until used, so a rebuilt screen keeps it), the folder contents, and
  the waiting join; `FolderJoinStepsView` draws the steps; the linked row is
  the folder chip, **Add a device**, and quiet **Sync now** and **Remove
  workspace**.
- **Tooling:** `flow folder link` presses **Change** and **Create
  workspace**; `join` only waits; `remove` reads the unlinked steps; the
  outcome read checks the screen before scrolling and always reads once
  before its deadline (the Android timeouts came from scrolling away from a
  control already on screen). verify-posato `folder-sync.md` and the
  posato-control README follow.
- **Limits:** a typed code is not kept when Android rebuilds the screen (a
  theme change); a scanned code is.

## Checks

| Check | Result |
| --- | --- |
| `./gradlew quality` | passed on the implementation |
| Android, Galaxy XCover7 (all runs `20261021-`) | create `144556-cc6b`; scan first, then folder: joined `145011-cdf6`; folder first, code typed while its offer was missing: waited (`151221-60bd`), joined after the offer arrived (`151150-2059`); expired code `150329-7313`; remove `151309-6fa7`, `151631-f398` |
| Light and dark screenshots | scan first `144928-6b4e`/`144935-42a4`; code step `145758-6b1f`/`145805-c277`; expired `150415-12fb`/`150421-4b1c`; create `144531-c71b`/`151947-534f`; linked `145106-5743`/`145120-6cee`; waiting (light only) `151221-60bd` |
| Linux clone, join by typed code (`AC-03`) | open: waits for a Linux VM |
