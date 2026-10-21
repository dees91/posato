# `SYNC-025`: Say folder instead of iCloud in folder mode

- **Review tier:** `standard`
- **Tier reason:** Folder-mode messages, plus one new bootstrap outcome that
  tells a removed workspace's copy apart from a retryable failure. Status,
  retries, the format, and the persisted schema stay as they are.
- **Dependencies:** `SYNC-018` (merged). `SYNC-026` rebuilds the same folder
  section after this row; `DOCS-006` and `RELEASE-007` wait for it.
- **Integration group:** PR-FOLDER-WORDING
- **Authority:** [Release roadmap](../release-roadmap.md) revision 25, row
  `SYNC-025`; [ADR 0010](../../decisions/0010-linux-android-and-folder-workspace.md).
- **Record:** [execution record](../executions/sync-025-folder-wording.md)

## Outcome

In folder mode Posato never names iCloud. A folder that holds a copy of a
workspace this device removed says so for as long as it is chosen, and says
what to do, instead of "Sync with iCloud didn't finish."

## Boundaries

- Folder mode only: iCloud mode keeps every sentence it has.
- The removed copy stays `RETRYABLE` with the same retries; the bootstrap
  reports it as its own outcome so the state can carry it.
- Non-goals: the layout of the folder section (`SYNC-026`), pairing errors,
  and any change to how removal or the removed list works.

## Acceptance

- `AC-01`: A test written first shows that a device choosing a copy of a
  workspace it removed is not told so; after the change its state reports
  the removed copy, and the refusal tests name the new outcome.
- `AC-02`: In folder mode the running, unlinked retry, linked
  not-yet-synced, and connected texts name the folder, not iCloud.
- `AC-03`: On the Android phone, a copy of a removed folder workspace shows
  the lasting explanation, and the folder row shows no iCloud wording.

## Verification

- `shared` jvmTest: `FolderWorkspaceTest`, `BootstrapCoordinatorTest`.
- `posato-control` on the Galaxy XCover7: create a folder workspace, copy
  its `Posato` folder, remove the workspace, link to the copy, screenshot.

## Decisions or blockers

- None.
