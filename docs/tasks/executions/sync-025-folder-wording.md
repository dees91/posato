# Execution: `SYNC-025`

- **Brief:** [Say folder instead of iCloud in folder mode](../specifications/sync-025-folder-wording.md)
- **Status:** `in progress`
- **Review tier:** `standard`
- **Implementer:** Claude
- **Branch:** `sync-025-folder-wording`

## Plan

1. Failing `FolderWorkspaceTest` case: a removed workspace's copy chosen as
   the folder must be reported on the sync state.
2. `BootstrapResult.RemovedWorkspace` from `refuseIfRemoved`, mapped to the
   same `RETRYABLE` status and key read; `AppleSyncState.removedWorkspaceCopy`
   set by the bootstrap and cleared by every other status change.
3. Folder messages: running, unlinked retry, linked local-only, the
   connected summary, and the removed copy (text and row summary).
4. Android run, review, quality.
