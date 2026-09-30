# Execution: `SCHEDULE-004`

- **Brief:** [Deliver pause sets on Mac and iPhone](../specifications/schedule-004-pause-sets.md)
- **Status:** `active` (slices 1 and 3 and measurements done; slice 4 next); **Updated:** 2026-09-30
- **Review tier:** `high-risk`; **Implementer:** Claude; **Reviewer:** independent agent
- **Branch:** `feat/schedule-004-pause-sets` (slice 1); later slices stack on it

## Plan

Stacked pull requests for the four slices of the [implementation
plan](../../product/pause-sets-decisions.md#implementation-plan-for-schedule-004),
plus a measurement step (2) that commits nothing; PR #119 carries slice 1 and
this record, and each slice is proven before the next. The stack merges only
after slice 5 (`user-confirmed` 2026-09-30): slice 3 alone would publish kinds
14 and 19 from `main`, and slice 4 alone would pause several sets without
union and retention.

1. **Model and sync** (done): kinds 12-19 in the codec and reducer; kinds
   14, 15, 17 and 18 are the existing payloads with an optional set.
2. **Measurements** (done, nothing committed): the iPhone filter bound and
   store union, and the Mac clear-then-apply gap; results under Blockers.
3. **Migration and storage** (done, PR #123): `13.sqm` puts websites,
   intents, sync bases, schedules and the session in sets, with kind 19 and
   upgrade markers and per-part retention of domains and kept Mac apps;
   the Mac app-choice database gains its first migration and iPhone app
   choices one file per set; authoring moves to kinds 12-19 and stops kind
   4. Migration tests build earlier schemas from tracked history with
   synthetic rows (`user-confirmed`); a 1.2 build decides the downgrade.
4. **Pause sets UI** per `DESIGN.md` "Release 1.3 pause sets": destination,
   set list and editor, New set and Rename, Make default, Delete with
   **Change their set** (schedule moves written before `set-remove`), set
   choice in Session and the schedule editor, readiness copy without the
   app group check, per-part running summary, the one-time update notice.
   Proven by E2E on a Tart VM and the test iPhone; screenshots go to the
   maintainer gate.
5. **Hosts.**
   - Mac: `PauseClaims` composes one request from all running parts (union
     of set resolutions plus retention, latest end), checks host limits
     before any clear, and recomposes on a part end or set edit; items
     over the limit are refused, never the applied configuration.
   - iPhone: App Group table version 2 with each domain stored once, the
     extension union at `intervalDidStart` and per-part release at
     `intervalDidEnd`, the version-1 table read as the first set until the
     first version-2 commit.
   - Failure inventory written before code: union, retention, limits,
     latest end, every stop reason including workspace removal; Swift
     cases for an unreadable version 2 never falling back and a crash
     between the version-2 write and the version-1 delete.
   - The brief's AC-02, AC-03 and AC-04 runs, plus a Mac run where two
     overlapping sets exceed 1,024 unique websites.

## High-risk plan review

- **Verdict:** `approved`; after three correction rounds: R1 kind 18 as a
  new payload broke expiry and snapshot validation; R2 missing reducer
  cases; R3 incomplete storage list; R4 kind 4 kept; R5 upgrade retention in
  SQL; R6 kept Mac apps unresolvable; R7 no Mac app-choice migration test;
  N1 stale merge point; N2 iPhone tokens in the backed-up SQL store; a
  migration test starting at version 13.
- **Maintainer decision** (`user-confirmed` 2026-09-30): a new workspace's
  first-set name and default stay unpublished at first link (known limit).

- **Slice 3 design review:** `changes-required`, folded in: the Mac
  migration is already transactional; retention exists only for the active
  session or a live pin, including after expiry markers and workspace
  removal; a deleted set's app choices are cleared. The maintainer kept
  kept-app requirements in the policy database, overriding the reviewer's
  A-03 finding (threat model exception), and chose the current first set as
  upgrade retention (ADR amended); a 1.2 iPhone with 51-64 apps keeps them
  and refuses additions; an iPhone downgrade crash counts as failing closed.

## Result

- **Slice 1** (`1f96769` and its review correction): as planned.
  The `LocalSyncMutation` variants move to slice 3, their first consumer.
  Slice 1 must not ship alone: `ScheduleSync.toSynced`
  and `toPlan` drop the set, kind 18 starts still enforce the first set,
  a removed first set empties `projection.domains`, and `isWorkspaceFull`
  counts the first set only. Slices 3 and 5 close these.
- **Slice 3** (`fee6e5e`..`aff62eb`, sub-steps 3.0-3.6): as planned, except
  set-scoped target loading for sessions and schedules moves to slice 5
  (its first consumer is the union), the set-capacity refusal copy moves
  to slice 4, and the test harness pre-marks kind 19 so existing bundle
  counts stay exact. The per-set reconciler tests were written after a
  compiler-forced rewrite (a deviation from failing-first); seven
  mutations each fail one of them.

## Completed-change review

- **Slice 1:** `changes-required`, then corrected: a missing case for a
  domain leaving its only set now fails under that mutation.
- **Slice 3:** `changes-required`, then corrected: an unreadable Mac
  app-choice store no longer completes the upgrade with no kept apps; it
  stays pending (regression failed first). Declined: comparing CHECK
  clauses in the schema-parity test (Recommended).

## Verification

- Slice 1: the sync suites on JVM and iOS pass (new tests failed first
  against stubs), 11 mutations each fail a test, and `quality` passes
  after the last correction.
- Slice 3: new tests failed first (except the reconciler tests above);
  `quality` passes at every sub-step and after the review correction. Mac upgrade and downgrade on a Tart VM
  with data from the notarized 1.2.0 DMG (policy user_version 12,
  app-choice 1): 1.3 migrates both (14 and 2) and shows the same two
  websites in the first set; 1.2 reinstalled on that data starts, shows no
  websites and leaves the database unchanged, which fails closed.

## Blockers and accepted risks

- **iPhone web filter capacity** (wiki `ios-enforcement`): over 25 websites
  turn off all website blocking on iPhone, already in 1.2. Decided
  (`user-confirmed` 2026-09-30): fixed in 1.3 here, no 1.2 patch; a new
  part pauses what fits and names the rest; iPhone apps capped at 50.
- **Mac clear-then-apply gap** (wiki `macos-enforcement`): about 0.2-0.25 s
  unblocked per change; accepted for 1.3, backlog row `MACOS-025`.

## Final

- **Status:** `active`; **Outcome:** pending
