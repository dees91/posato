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

- See the pull request.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `AppleSyncLastSyncTest` on `main` `462d8fbe` with the state field only | Failed as expected (`expected:<1000> but was:<null>`, `expected:<5000> but was:<null>`) | gradle run of the session |
| `:shared:jvmTest`, Detekt, ktlint, iOS and Android compiles | Passed | gradle run of the session |
| Galaxy XCover7: link `/sdcard/PosatoSync`, expand Folder sync | "Last synced 10:24 PM" | `build/verification/e2e/sync-023-android.log`, runs `20261020-222231-2e6c`, `20261020-222521-524c` |
| `./gradlew quality` on the head | See the pull request's Verified comment | |

## Blockers and accepted risks

- None.
