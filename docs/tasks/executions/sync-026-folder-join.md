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
