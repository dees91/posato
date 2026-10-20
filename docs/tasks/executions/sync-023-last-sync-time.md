# Execution: `SYNC-023`

- **Brief:** [sync-023-last-sync-time.md](../specifications/sync-023-last-sync-time.md)
- **Status:** `done`
- **Review tier:** Standard
- **Implementer:** Claude Code agent, under the maintainer's 2026-10-20
  mandate to finish release 1.5 unattended
- **Reviewer:** independent agent
- **Branch:** `sync-023-last-sync-time`
- **Updated:** 2026-10-20

## Plan

1. Failing `AppleSync` tests for record, keep, and restart.
2. A local table, a store, and a tracker; a caption in the section.
3. A run on the phone.

## Result

- `sync_last_exchange` (migration 14) keeps one time per workspace.
  `SqlBootstrapStore.clearEstablished` clears it with the rest of the
  workspace's state. `SqlLastSyncStore` reads and writes it; a failure only
  leaves the line out.
- `AppleSync` shows the stored time at the start of a pass and records the
  time of each pass that ends `COMPLETED` (`LastSyncTracker`). An unlinked
  device shows none.
- The expanded section shows "Last synced <time>" through the device's
  `SessionTimeFormat` (clock time today, date and time before) or "Not
  synced yet on this device".
- The migration test's pinned schema version moves from 14 to 15.

## Completed-change review

- Independent agent, 2026-10-20: no Critical, one Required. A restart
  whose first pass stopped before the workspace was reached (offline, or
  iCloud undetermined) showed "Not synced yet". Resolved test first: the
  stored time is read from this device whenever the linked state is
  refreshed, once per workspace. Recommended taken: the formatter is
  required. Declined: a `DESIGN.md` line for the caption, which waits for
  the maintainer, and a remove-then-link test.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `AppleSyncLastSyncTest` on `main` `462d8fbe` with the state field only, and the offline-restart case before its fix | Failed as expected (`expected:<1000> but was:<null>`, `expected:<5000> but was:<null>`) | gradle run of the session |
| `:shared:jvmTest`, Detekt, ktlint, iOS and Android compiles | Passed | gradle run of the session |
| Galaxy XCover7: link `/sdcard/PosatoSync`, expand Folder sync | "Last synced 10:24 PM" | `build/verification/e2e/sync-023-android.log`, runs `20261020-222231-2e6c`, `20261020-222521-524c` |
| `./gradlew quality` on the head | See the pull request's Verified comment | |

## Blockers and accepted risks

- None.
