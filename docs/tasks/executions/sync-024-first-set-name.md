# Execution: `SYNC-024`

- **Brief:** [Keep the first set's name and the default when a device creates a workspace](../specifications/sync-024-first-set-name.md)
- **Status:** `in-review` (review findings folded; ready for the pull request)
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
- `AC-03` on devices, all through `posato-control` with this branch:
  - Android (Galaxy XCover7): renamed the first set "Focus", made
    "Evening" the default, created a folder workspace; both kept.
  - A Linux clone joined through the relay and shows "Focus" with
    "Evening" as Default.
  - A fresh Linux clone renamed "Other" joined the same workspace and shows
    "Focus"; the phone still shows "Focus".
  - A Tart Mac, after removing the test account's old iCloud workspace,
    renamed its first set "Focus", made "Evening" the default, and linked
    with `flow icloud link`; both kept. `flow set --rename-from` and
    `--make-default` worked on the desktop, Android, and Linux.

## Review

- Plan review, 2026-10-21: no Critical; seven Required, folded as R1 to R7.
- Independent completed-change review, 2026-10-21: no Critical; Required:
  the maintainer's acceptance of the ADR wording (`AC-04`, now met).
  Recommended taken: a default whose new set is already queued (R-1, fixed
  test first), the concurrent winner pinned (R-2), a last-slot boundary
  test (R-3), revision-labelled runs (R-4), README iOS note (R-5); optional
  O1 (a repeated rename is a no-op), O5 (ADR wording), and O6 (brief).

## Acceptance

- `AC-01`, `AC-02`: tests below. `AC-03`: device runs below.
- `AC-04` met: the maintainer accepted the ADR 0006 wording, including the
  cap, removed, and refused conditions and both known limits
  (`user-confirmed` 2026-10-21).

## Checks

| Check | Result |
| --- | --- |
| Failing tests first | on `43c61ae4`: create, join unnamed, concurrent, the old-rule case, drain, double seed, stale rows, reducer flag; on `f1a191df`: the queued-set default; guards passed |
| Mutation for the last-slot test | with the cap condition loosened to `<=`, the last-slot, cap, and queued-set cases fail; reverted |
| `./gradlew quality` | passed on the closeout commit (`b4652bec` plus a shorter test name and this record) |

Verified `b4652bec` Android create (Galaxy XCover7, folder): reset run `20261021-133920-7c34`, rename `20261021-134033-0a76`, Evening `20261021-134109-cc83`, default `20261021-134155-5ea6`, link run `20261021-134219-11c3`; Focus and Evening Default kept.
Verified `b4652bec` Linux join (Tart Ubuntu clone, relay): link run `20261021-134615-d0d4`, offer `20261021-134641-96ec`, join run `20261021-134706-fe90`, snapshot run `20261021-134742-483c` shows Focus and Evening, Default.

Earlier runs on `dc968b3a`, before the R-1 fix (which only changes a
default whose set was already queued, not exercised there): Linux
negative join "Other" (`20261021-124621-1bf1`, snapshot `-124658-8829`
shows Focus) and the Tart Mac iCloud create (`20261021-125614-5c5a`,
snapshot `-130340-baae` keeps Focus and Evening Default). Runs on the
`6dc51df8` APK (`20261021-121555` to `-123808`) are superseded.
Cleanup: workspaces removed (`-130831-380c`, `-134751-d16d`), clones
destroyed, folders deleted, no pause or VPN left.
