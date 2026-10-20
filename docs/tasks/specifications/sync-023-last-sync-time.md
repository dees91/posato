# `SYNC-023`: When this device last synchronized

- **Review tier:** Standard
- **Tier reason:** One additive local table, written after a completed pass
  and read for display. It changes no synchronized format, reducer, key, or
  trust boundary. Escalate to High-risk if it has to be synchronized.
- **Dependencies:** `SYNC-018` (merged in #168); release 1.5, wave 4.
- **Integration group:** PR-LAST-SYNC
- **Authority:** [Release roadmap](../release-roadmap.md) revision 24, row
  `SYNC-023`; idea 42 (`user-confirmed` 2026-10-20).
- **Record:** [execution record](../executions/sync-023-last-sync-time.md)

## Outcome

The synchronization section of a linked device says when it last completed
a sync, for example "Last synced 10:24 PM", or "Not synced yet on this
device". Through a folder, this tells a person whether changes still arrive.

## Boundaries

- The time is that of the last pass that ended `COMPLETED`, on every
  platform and for iCloud and folder workspaces. A failing pass, a restart,
  or a pass still running keeps the earlier time.
- It is kept on the device per workspace (migration 14), and never
  synchronized. Removing the workspace, or a device that is not linked,
  shows none.
- The status texts and the collapsed row stay as they are.
- Non-goals: per-device times of other members, and alerts about long
  silences.

## Acceptance

- `AC-01`: Tests written first and failing on `main`: a completed pass
  records its time; a later failing pass keeps it; after a restart whose
  first pass fails, the earlier time shows.
- `AC-02`: On a linked phone the expanded section shows "Last synced"
  with the device's clock time.

## Verification

- `shared` jvmTest (`AppleSyncLastSyncTest`, migration test).
- `posato-control -t android`: link a folder, expand the section, read the
  line.

## Decisions or blockers

- None.
