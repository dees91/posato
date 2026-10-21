# Execution: `SYNC-024`

- **Brief:** [Keep the first set's name and the default when a device creates a workspace](../specifications/sync-024-first-set-name.md)
- **Status:** `in-progress` (brief, plan, and failing regression)
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Branch:** `sync-024-first-set-name`

## Plan

1. Failing regression first (`AppleSyncFirstSetNameTest`, done): on `main`
   the create case fails with `expected:<Focus> but was:<null>`, and with
   the assertions swapped the default fails too (the first set comes back
   as default). The join case passes.
2. D1, how a replica knows it may publish. Two options:
   - **A. "The workspace has not chosen yet" (recommended).** At first link
     (no base yet) `seedsFor` also queues `set-put` for the first set when
     the workspace's first set has no name, and `seedLocalExtras` queues
     `set-default` when the workspace holds no `set-default` and the local
     default differs from the resolved one. The projection gains one
     derived value, whether any `set-default` was applied, computed by the
     reducer from operations it already holds (no format change). No
     persisted flag, so a crash between establishing and seeding needs
     nothing new: the seed reruns while there is no base, and an
     existing pending `set-put` or `set-default` suppresses a second one.
     It also keeps the name of a device that joins a workspace where nobody
     named the first set. Known race: a join whose projection has not yet
     received another device's earlier name or default can publish its
     own, and the greater total order then wins; folder sync delivers the
     pairing offer after the earlier bundles in practice.
   - **B. "This device created the workspace."** Carry created or joined on
     `BootstrapResult.Ready` and persist it until the first seed (a
     bootstrap-store column and migration), then publish only when created.
     Closer to the roadmap wording, but a schema change, and a device that
     loses an iCloud anchor race and joins still loses its name.
3. Implement D1 test first for the unit cases beside
   `PolicyReconcilerPauseSetTest` (the existing first-link case asserts the
   old rule and changes with D1); update ADR 0006 "Migration identity" and
   the `PolicyReconciler.seedLocalExtras` comment.
4. Device runs (`AC-03`), independent review, `./gradlew quality`, then the
   pull request to babysit-pr.

## Result

Pending.

## Review

- Plan review: pending.

## Checks

| Check | Result |
| --- | --- |
| `AppleSyncFirstSetNameTest` on `main` `305d5bd7` | create case fails (`expected:<Focus> but was:<null>`), join case passes |
