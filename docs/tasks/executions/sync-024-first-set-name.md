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

- `SyncReducer` exposes `defaultChosen`: any applicable `set-default`
  counts, also one naming a removed set; the projection digest includes it.
- `seedsFor` queues the first set's name while the workspace's first set
  is unnamed; `seededDefault` queues the local resolved default under the
  R2 conditions, after every put; `seedLocalExtras` reads only the current
  workspace's queued changes.
- ADR 0006 "Migration identity" states the rule and its known limits.
- `posato-control flow set` gains `--rename-from` and `--make-default`.
- Android (Galaxy XCover7, fresh install from this branch): renamed the
  first set "Focus", made "Evening" the default, then created a folder
  workspace in `/sdcard/Sync/Posato-024`; Pause sets still shows "Focus"
  and "Evening" as Default.

## Review

- Plan review, 2026-10-21: no Critical; seven Required, folded as R1 to R7.

## Checks

| Check | Result |
| --- | --- |
| Failing tests on `43c61ae4` (before the fix) | create, join unnamed, concurrent, the updated old-rule case, drain, double seed, stale rows, and the reducer flag fail; guards pass |
| `:shared:jvmTest` sync tests after `7ad40c41` | pass |
| Android runs | `20261021-121555-8c67` reset to `20261021-121601-4745` onboard; `-121701-c7bb` rename, `-121729-843f` Evening, `-121808-b14e` default, `-122009-633a` folder link, `-122306-cc82` snapshot, `-122309-5164` screenshot |
| `./gradlew quality` | passed after the line-length and style fixes |
| Linux join and iCloud create on Tart clones | pending, waits for the coordinator |
