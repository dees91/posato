# Execution: `SYNC-025`

- **Brief:** [Say folder instead of iCloud in folder mode](../specifications/sync-025-folder-wording.md)
- **Status:** `done`
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
  a copy of a workspace you removed from this device…", summary "Folder
  holds a removed workspace". Announcements use the same folder-aware
  sentence. The removed-copy state is not stored, so it shows after each
  attempt and not after a restart until the next one (left to `SYNC-026`).
  iCloud mode is unchanged.

## Verification

- Verified b9c405a2 on the Galaxy XCover7 (Android 14), APK from this tree, run 20261021-135649-4ec0: fresh install and onboarding (`-135545-efb8` to `-135551-7448`), folder workspace created in `/sdcard/PosatoSync025`.
- Verified b9c405a2: its `Posato` folder copied to `/sdcard/PosatoCopy025`, then **Remove workspace**, run 20261021-135929-cebc; the source folder is empty and the copy keeps `workspace` and `bundles`.
- Verified b9c405a2: linking to the copy stays unlinked; the row reads "Holds a workspace you removed" and the section shows the removed-copy text, run 20261021-140318-bb62 (screenshot `-140321-7ab7`); no element contains "iCloud", run 20261021-140330-323b.
- An earlier attempt on the same tree was interrupted by another session driving the phone; its runs are not evidence. Both folders were deleted afterwards and the lease released.
- `posato-control flow folder link` waits for **Add a device** or **Join**, so on the removed copy it times out by design (`-140032-e945`, its message quotes the new row text).

## Review

- Independent agent, 2026-10-21: no Critical. Required fixed: join and
  check announcements spoke the iCloud sentence in folder mode (now one
  folder-aware `statusMessage`, with `SyncMessagesTest` written first and
  failing on the old mapping against the shipped English text); this wiki
  log entry. Recommended taken: status and the removed copy in one state
  update; the restart gap stated. Optional taken: the row summary reads
  "Folder holds a removed workspace". The duplicate linking sentence under
  the removed-copy text is left to `SYNC-026`.

## Checks

| Check | Result |
| --- | --- |
| `./gradlew quality` | passed on the final tree |
