# `SCHEDULE-004`: Deliver pause sets on Mac and iPhone

- **Review tier:** High-risk
- **Tier reason:** New mandatory format-1 kinds 12-19, database and App Group
  migrations on linked devices, and changed enforcement composition on both
  platforms.
- **Dependencies:** `SCHEDULE-003` (merged in #115, ADR 0006 amendment
  accepted) and `NAV-001` (merged in #113); release 1.3, wave 3.
  `DOCS-004` and `RELEASE-005` wait for this row.
- **Integration group:** PR-PAUSE-SET-DELIVERY
- **Authority:** [Release roadmap](../release-roadmap.md) row
  `SCHEDULE-004`; [pause sets scope](../../product/pause-sets.md) and
  [rules](../../product/pause-sets-decisions.md); maintainer named the row on
  2026-09-30.

## Outcome

People can create named pause sets, choose one for each manual session and
schedule, and have overlapping pauses block the union of their sets on Mac
and iPhone, with existing websites, app choices, and schedules migrated into
the first set.

## Boundaries

- Follow the [implementation plan](../../product/pause-sets-decisions.md#implementation-plan-for-schedule-004)
  in its four stacked slices (model and sync, migration and storage, Pause
  sets UI, hosts), each proven before the next.
- Settle the items the rules leave to this row by measurement: the iOS union
  of the manual and schedule stores, the shield and web-filter limits for a
  large union, the Mac clear-then-apply gap, the extension's file-timestamp
  need, and the local-only downgrade.
- Non-goals: optional kinds 128-255, sharing sets across Apple Accounts, and
  portable application choices.

## Acceptance

- `AC-01` — Golden vectors and failing-first reducer tests cover every kind
  12-19 payload and the delivery permutations named in the plan.
- `AC-02` — Two Tart VMs upgraded from a 1.2 workspace keep blocking
  throughout, get one first set, and pass the plan's overlap, live-edit,
  deletion, limit, linking, and offline runs.
- `AC-03` — On the test iPhone: upgrade with app choices, per-set apps,
  websites-only start, overlap of a manual session and a schedule, extension
  start and end with Posato closed, and the upgrade without opening the app.
- `AC-04` — A set created on the Mac starts, through a schedule, with its
  websites on the iPhone.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- As listed in the plan. Migration tests use hand-built earlier schemas
  with synthetic rows (`user-confirmed` 2026-09-30); real 1.2 data is
  proven by the AC-02 and AC-03 upgrade runs.

## Decisions or blockers

- Maintainer gates from the plan: an independent High-risk plan review of
  this brief before implementation, the Pause sets screens on screenshots
  after slice 3, the migration run on synthetic 1.2 data before
  `RELEASE-005`, and the `PRIVACY.md` edits with the release.
