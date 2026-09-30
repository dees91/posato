# Execution: `SCHEDULE-004`

- **Brief:** [Deliver pause sets on Mac and iPhone](../specifications/schedule-004-pause-sets.md)
- **Status:** `active` (plan approved; slice 1 next); **Updated:** 2026-09-30
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

1. **Model and sync** (`shared/.../feature/sync`).
   - `PauseSetId` (UUIDv4 or the all-zero first set). Kinds 17 and 18 are
     the existing `SchedulePut` and `SessionStart` with an optional set id
     (absent means kind 8 or 6), so snapshot validation, terminal expiry
     and every `as? SessionStart` path keep working; new payloads only for
     12-16 and 19. A `PauseSetOperationCodec` beside
     `ScheduleOperationCodec`; `readPayload` rejects 20-127.
   - Reducer: set liveness from the whole operation set first, the 10-set
     cap with a derived `SET_CAPACITY` outcome, domains keyed by
     `(set, domain)` with kinds 2 and 3 as the first set and the 2,048 cap
     over unique domains of live sets, the default fallback, kinds 8/17 in
     one schedule register and 6/18 in one session group, set reference
     status (live, removed, refused, unknown), and kind 19 presence.
     `SyncProjection.domains` stays the first set's domains for the code
     slice 1 leaves unchanged. The test-only `SyncProjectionDigest` gains
     the new fields and a `SET_CAPACITY` tag.
   - `LocalSyncMutation` gains the new mutations; authoring stays on kinds
     2-4, 6 and 8 until slice 3.
   - Failing-first `commonTest` (JVM and iOS): golden vectors and
     non-canonical inputs for every payload; reducer cases for delivery
     permutations, remove racing put, a domain racing its set's removal or
     freed by it, cap edges, a removed first set (slot freed, kinds 2, 3, 6
     and 8 dropped), kind 8 after kind 17, kind 6 against kind 18,
     references to removed, refused and unknown sets, per-set kind 15,
     duplicate kind 19, and default fallback; a kind 18 session's expiry
     fact and snapshot.
2. **Measurements** (throwaway builds, nothing committed): the iPhone
   manual and schedule store union, the shield and web-filter limits for a
   large union, and the Mac clear-then-apply gap in a Tart VM. A result
   that contradicts a rule stops the task for a maintainer decision (the
   Mac gap becomes an ADR 0004 question).
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
  first-set name and default are not published at first link; an
  internal known limit, no ADR change.

## Result

- Pending; review and checks follow each slice.

## Final

- **Status:** `active`; **Outcome:** pending
