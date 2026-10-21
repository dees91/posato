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
2. D1 = option A (`user-confirmed` 2026-10-21): at first link, publish
   only what the workspace has not chosen. The maintainer accepted that a
   stale joiner can overwrite an undelivered choice (the greater total
   order wins) and that joining an unnamed workspace publishes the
   joiner's name. Option B (persisted "created") was not taken.
3. The independent plan review (no Critical, seven Required) is folded in:
   - R1: queue the first set's `set-put` only while the workspace's first
     set has no name; never because the local name differs.
   - R2: queue `set-default` only when no `set-default` was ever applied,
     the local resolved default differs from the projection's resolved
     default, survives the seed filter (not removed), and would not be
     refused at the 10-set cap; queue it after every `set-put`.
   - R3: `seedLocalExtras` reads only the pending changes of the current
     workspace, for names, the default, and websites.
   - R4: ADR 0006 "Migration identity" states the new rule, its known
     limits (concurrent first links, an iCloud anchor race, and folder
     file order: the greater HLC, author, operation wins), that a joiner of
     an unnamed or undefaulted workspace changes it for every member, and
     compatibility (kinds 12 and 16 are mandatory since 1.3, no gate).
   - R5: failing tests first (list in Checks): create received by a second
     replica, join guard, join unnamed, default edge cases, retries, two
     concurrent links, stale workspace rows, the old-rule case in
     `PolicyReconcilerPauseSetTest`, and "a default was chosen" (any
     applied `set-default` counts) in the reducer and the projection
     digest.
   - R6: `posato-control flow set` gains `--rename-from` and
     `--make-default` before the device runs.
   - R7: device runs as in `AC-03`.
4. Implement in `SyncReducer` (derived `defaultChosen`),
   `PauseSetMerge.seedsFor`, and `PolicyReconciler.seedLocalExtras`.
5. Device runs, independent review, `./gradlew quality`, pull request to
   babysit-pr.

## Result

Pending.

## Review

- Plan review: pending.

## Checks

| Check | Result |
| --- | --- |
| `AppleSyncFirstSetNameTest` on `main` `305d5bd7` | create case fails (`expected:<Focus> but was:<null>`), join case passes |
