# Execution: `SCHEDULE-004`

- **Brief:** [Deliver pause sets on Mac and iPhone](../specifications/schedule-004-pause-sets.md)
- **Status:** `active` (slice 1 and measurements done; slice 3 next); **Updated:** 2026-09-30
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

1. **Model and sync** (`shared/.../feature/sync`, done): kinds 12-19 in the
   codec and reducer; kinds 14, 15, 17 and 18 are the existing payloads
   with an optional set, so every `as? SessionStart` path keeps working.
   `SyncProjection.domains` stays the first set's domains until slice 3.
2. **Measurements** (done, nothing committed): the iPhone filter bound and
   store union, and the Mac clear-then-apply gap; results under Blockers.
3. **Migration and storage.**
   - `13.sqm`: set table; a set column on local websites, policy intents,
     `sync_policy_base_domain`, `sync_schedule_intent`, `sync_session_intent`,
     schedules and the local session; queued set intents for kinds 12, 13
     and 16; a kind 19 marker; a per-part retention table for domains and,
     on the Mac, each kept app's requirement, so a kept item stays
     enforceable after its set loses it. iPhone kept-app tokens stay in the
     backup-excluded App Group store that the extension also writes.
   - Retention for parts running at upgrade is written once by app code
     after the migration, with an injected clock, before the first
     enforcement update or edit; it replaces `frozen_domains` as the
     running session's source.
   - Unique-domain counting for the local 1,024 limit
     (`SqlLocalPolicyStatements`, `WebsiteBatchSubmission`,
     `PolicyReconciler`) and `isWorkspaceFull`; `seedLocalExtras` per set.
   - The Mac app-choice database gains its first migration (set column);
     iPhone app choices move to one file per set, the 64-token ceiling
     over unique tokens.
   - Authoring switches to kinds 14, 15, 17 and 18 and stops kind 4; kind
     19 once per database on first open in a linked workspace and at first
     link, which publishes `set-put` for non-first sets only.
   - Failing-first migration tests from earlier schemas built from tracked
     history (shared version 12, Mac app-choice version 1) with synthetic
     rows, migrated to the current version, including a running session and
     schedule pins; no 1.2-produced database is checked in (`user-confirmed`
     2026-09-30). Real 1.2 data is proven by the AC-02 and AC-03 upgrade runs;
     the capture confirms the 1.2 app-choice file reports schema version 1.
     The local-only downgrade is decided by running 1.2 against a migrated
     database before `13.sqm` is final; failing closed without data loss is
     acceptable.
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

## Result

- **Slice 1** (`1f96769` and its review correction): as planned.
  The `LocalSyncMutation` variants move to slice 3, their first consumer.
  Slice 1 must not ship alone: `ScheduleSync.toSynced`
  and `toPlan` drop the set, kind 18 starts still enforce the first set,
  a removed first set empties `projection.domains`, and `isWorkspaceFull`
  counts the first set only. Slices 3 and 5 close these.

## Completed-change review

- **Slice 1:** `changes-required`, then corrected: a missing case for a
  domain leaving its only set now fails under that mutation.

## Verification

- Slice 1: the sync suites on JVM and iOS pass (new tests failed first
  against stubs), 11 mutations each fail a test, and `quality` passes
  after the last correction.

## Blockers and accepted risks

- **iPhone web filter capacity** (wiki `ios-enforcement`): over 25 websites
  turn off all website blocking on iPhone, already in 1.2. Decided
  (`user-confirmed` 2026-09-30): fixed in 1.3 here, no 1.2 patch; a new
  part pauses what fits and names the rest; iPhone apps capped at 50.
- **Mac clear-then-apply gap** (wiki `macos-enforcement`): about 0.2-0.25 s
  unblocked per change; accepted for 1.3, backlog row `MACOS-025`.

## Final

- **Status:** `active`; **Outcome:** pending
