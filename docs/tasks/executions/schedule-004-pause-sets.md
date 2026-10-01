# Execution: `SCHEDULE-004`

- **Brief:** [Deliver pause sets on Mac and iPhone](../specifications/schedule-004-pause-sets.md)
- **Status:** `active` (slices 1, 3 and 4 and measurements done; slice 5 next); **Updated:** 2026-10-01
- **Review tier:** `high-risk`; **Implementer:** Claude; **Reviewer:** independent agent
- **Branch:** `feat/schedule-004-pause-sets` (slice 1); later slices stack on it

## Plan

Stacked pull requests for the slices of the [implementation
plan](../../product/pause-sets-decisions.md#implementation-plan-for-schedule-004)
plus a measurement step that commits nothing; each slice is proven before
the next, and the stack merges only after slice 5 (`user-confirmed`
2026-09-30).

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
4. **Pause sets UI** (done, PR #124) per `DESIGN.md` "Release 1.3 pause
   sets", proven by E2E on a Tart VM and the test iPhone; screenshots go
   to the maintainer gate.
5. **Hosts.** Mac `PauseClaims` composes one request from all running
   parts (union plus retention, latest end) and checks limits before any
   clear; iPhone App Group table version 2 with the extension union and
   per-part release; failure inventory written before code; the brief's
   AC-02, AC-03 and AC-04 runs and a Mac run over 1,024 unique websites.

## High-risk plan review

- **Verdict:** `approved`; after three correction rounds: R1 kind 18 as a
  new payload broke expiry and snapshot validation; R2 missing reducer
  cases; R3 incomplete storage list; R4 kind 4 kept; R5 upgrade retention in
  SQL; R6 kept Mac apps unresolvable; R7 no Mac app-choice migration test;
  N1 stale merge point; N2 iPhone tokens in the backed-up SQL store; a
  migration test starting at version 13.
- **Maintainer decision** (`user-confirmed` 2026-09-30): a new workspace's
  first-set name and default stay unpublished at first link (known limit).

- **Slice 3 design review:** `changes-required`, folded in; the maintainer
  kept kept-app requirements in the policy database (threat model
  exception), chose the current first set as upgrade retention (ADR
  amended), lets a 1.2 iPhone with 51-64 apps keep them, and accepts an
  iPhone downgrade crash as failing closed.
- **Slice 4 design review:** `changes-required`, folded in: set-scoped
  session targets, the app-group gate, onboarding, per-part state, notice
  storage, a queued set removal during merge, delete re-reads, drafts.

## Result

- **Slice 1** (`1f96769` and its review correction): as planned; the
  `LocalSyncMutation` variants moved to slice 3, their first consumer.
- **Slice 3** (`fee6e5e`..`aff62eb`, sub-steps 3.0-3.6): as planned, except
  set-scoped target loading for sessions and schedules moves to slice 5
  (its first consumer is the union), the set-capacity refusal copy moves
  to slice 4, and the test harness pre-marks kind 19 so existing bundle
  counts stay exact. The per-set reconciler tests were written after a
  compiler-forced rewrite (a deviation from failing-first); seven
  mutations each fail one of them.
- **Slice 4** (`a356777`..`11df007`): as planned, except set-scoped
  session targets move here from slice 5 (Review and start need them),
  and per-part set names in the running scheduled-pause summary move to
  slice 5 (they need the union's parts). The application group stays
  (DESIGN keeps it) and no longer needs the first set. The unused
  `adjustDuration` and its test are removed. Observed, not fixed: a tap in
  Session setup at the moment a starting schedule closes it shows an
  Error window (navigation dispatcher disposed).

## Completed-change review

- **Slice 1:** `changes-required`, then corrected: a missing case for a
  domain leaving its only set now fails under that mutation.
- **Slice 3:** `changes-required`, then corrected: an unreadable Mac
  app-choice store no longer completes the upgrade with no kept apps; it
  stays pending (regression failed first). Declined: comparing CHECK
  clauses in the schema-parity test (Recommended).
- **Slice 4:** `changes-required`, then corrected: a running session on a
  non-default set showed the default set's apps; naming the group failed
  once the first set was deleted (both regressions failed first). Also
  taken: the default re-checked before schedules move, the no-apps row
  message, a reload on setup cancel. Declined: the duplicated end time
  (Optional).

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
- Slice 4: new protocol, store, session and deletion tests failed first;
  `quality` passes after the last correction. `pause-sets-desktop.json` on
  a linked Tart VM passes end to end; it found the Start gate on an empty
  default set (fixed), and passes again on a fresh VM after the review
  corrections. iCloud Keychain pauses in each clone (`main` too), so runs
  need `vm icloud --resume`; Remove workspace there needed a second press.

## Blockers and accepted risks

- **iPhone web filter capacity** (wiki `ios-enforcement`): over 25 websites
  turn off all website blocking on iPhone, already in 1.2. Decided
  (`user-confirmed` 2026-09-30): fixed in 1.3 here, no 1.2 patch; a new
  part pauses what fits and names the rest; iPhone apps capped at 50.
- **Mac clear-then-apply gap** (wiki `macos-enforcement`): about 0.2-0.25 s
  unblocked per change; accepted for 1.3, backlog row `MACOS-025`.

## Final

- **Status:** `active`; **Outcome:** pending
