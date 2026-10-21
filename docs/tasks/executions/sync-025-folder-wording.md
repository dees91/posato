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

- Verified 72dd5cd4 on the Galaxy XCover7 (Android 14), APK from this tree, run 20261021-142040-c33b: fresh install and onboarding (`-141937-d8f9` to `-141943-f4b2`), folder workspace created in `/sdcard/PosatoSync025`.
- Verified 72dd5cd4: the workspace's `Posato` folder copied to `/sdcard/PosatoCopy025`, then **Remove workspace**, run 20261021-142320-8c4b; the source is empty, the copy keeps `workspace` and `bundles`.
- Verified 72dd5cd4: linking to the copy stays unlinked; the removed-copy text shows, run 20261021-142702-7138; the row reads "Folder holds a removed workspace", run 20261021-142705-ce13; no element contains "iCloud", run 20261021-142708-9494 (screenshot `-142710-fdff`).
- `flow folder link` waits for **Add a device** or **Join**, so on the copy it times out by design (`-142415-f51c`, quoting the new row). Both folders deleted afterwards and the phone lease released. Earlier runs on pre-review trees are superseded.

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
