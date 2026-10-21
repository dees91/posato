# `SYNC-024`: Keep the first set's name and the default when a device creates a workspace

- **Review tier:** High-risk
- **Tier reason:** It changes what a replica authors at first link, which
  ADR 0006 fixes ("At first link it authors no name for the first set and
  no `set-default`"). The format-1 bundle and the reducer stay as they are,
  but the seed rule decides whose choice a workspace keeps, for iCloud and
  folder links on every platform. A brief independent plan review comes
  before the implementation.
- **Dependencies:** `SYNC-018` (merged in #168); release 1.5, wave 4b.
  `DOCS-006` and `RELEASE-007` wait for this row.
- **Integration group:** PR-FIRST-SET-NAME
- **Authority:** [Release roadmap](../release-roadmap.md) revision 25, row
  `SYNC-024`;
  [ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md)
  "Migration identity"; [ADR 0010](../../decisions/0010-linux-android-and-folder-workspace.md).
- **Record:** [execution record](../executions/sync-024-first-set-name.md)

## Outcome

A device that renamed its first set, or made another set the default,
before it linked keeps that name and default when it creates a new
workspace, and every device that joins later receives them. A device that
joins a workspace where a name or default was already chosen still takes
the workspace's choice.

## Boundaries

- `observed` cause (2026-10-21): a change made with no workspace is not
  queued (`SyncTargetPolicyStore.recordedWrite`). At first link
  `PolicyReconciler.seedLocalExtras` queues a `set-put` only for a set the
  workspace does not know (`seedsFor`), and the first set is live from the
  start, so its name is never queued; `chosenDefault` is never queued
  either. `mergeLive` then takes the workspace's null name and
  `chosenDefault` its first-set default. The DOCS-006 captures saw "Focus"
  become "My set" on Android twice.
- The fix stays in the first-link seed and the merge: no new operation
  kind, no reducer rule, and no bundle change. The reducer already accepts a
  `set-put` for the first set as a rename.
- A choice another device made must not be overwritten by a late link, as
  ADR 0006 intends. The plan decides how a replica tells "no choice yet"
  from "a choice made elsewhere" (see Decisions).
- The same rule covers iCloud and folder links on Mac, iPhone, Linux, and
  Android, since the seed is shared code.
- Non-goals: the one-time "Update Posato on your other devices" notice
  (shown once per linked device by design), renames after linking (already
  queued), and app choices.

## Acceptance

- `AC-01`: An exchange test, written first and shown failing on `main`: a
  device with the first set renamed "Focus" and another set as default
  creates a workspace; after one pass both are kept locally and published
  as `set-put` and `set-default`.
- `AC-02`: A device that joins a workspace whose first set is already named
  keeps the workspace's name and publishes none of its own (passes today
  and keeps passing).
- `AC-03`: On real devices: the Android phone renames the first set
  "Focus", creates a folder workspace, and still shows "Focus"; a Linux
  clone that joins shows "Focus" too. A Mac VM that renames and creates an
  iCloud or folder workspace keeps its name.
- `AC-04`: ADR 0006's first-link sentence states the new rule, accepted by
  the maintainer.

## Verification

- `shared` tests `AppleSyncFirstSetNameTest` (create and join) and the
  first-link seed cases in `PolicyReconcilerPauseSetTest`, then
  `./gradlew quality`.
- `posato-control`: on the phone `flow set` renames the first set, then
  `flow folder link`; `snapshot` shows "Focus". The Linux clone joins with
  `flow folder join` and shows "Focus". A Tart Mac renames and links with
  `flow icloud link` or `flow folder link`.

## Decisions or blockers

- **D1 (maintainer):** how a replica knows it may publish its first-set
  name and default. The plan recommends one; ADR 0006's sentence changes
  either way and needs acceptance (`AC-04`).
