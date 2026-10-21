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

## Result

- `FolderWorkspaceTest` "given a copy of a workspace this device removed
  …": failed on `main` (`expected:<true> but was:<false>` once the state
  field existed), passes now. The six `BootstrapCoordinatorTest` refusal
  cases now name `RemovedWorkspace`; `:shared:jvmTest` sync tests pass.
- Folder mode: running, unlinked retry, linked local-only, and the row's
  connected summary name the folder; a removed copy reads "This folder holds
  a copy of a workspace you removed from this device…", summary "Holds a
  workspace you removed". iCloud mode is unchanged.

## Verification

- Galaxy XCover7, APK from this branch: fresh install and onboarding
  (`20261021-132331-d93f` to `-132337-255b`), folder workspace created in
  `/sdcard/PosatoSync025` (`-133057-719a`); the linked row shows "Latest
  sync attempt completed" and no iCloud text (`-133344-2bdd`). The
  workspace's `Posato` folder was copied to `/sdcard/PosatoCopy025` and the
  workspace removed (`-133728-c46c`; the source folder is empty, the copy
  keeps `workspace` and `bundles`).
- Open: linking to the copy and capturing the removed-copy text. The run
  was interrupted because another session drove the same phone at the same
  time (`flow folder link` from the `SYNC-024` worktree).

## Checks

| Check | Result |
| --- | --- |
| `./gradlew quality` | passed on the final tree |
