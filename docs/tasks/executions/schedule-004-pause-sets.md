# Execution: `SCHEDULE-004`

- **Brief:** [Deliver pause sets on Mac and iPhone](../specifications/schedule-004-pause-sets.md)
- **Status:** `done` (slices 1-5 and AC-01..AC-04); **Updated:** 2026-10-02
- **Review tier:** `high-risk`; **Implementer:** Claude; **Reviewer:** independent agent
- **Branch:** `feat/schedule-004-pause-sets` (slice 1); later slices stack on it

## Plan

Stacked pull requests for the slices of the [implementation
plan](../../product/pause-sets-decisions.md#implementation-plan-for-schedule-004)
plus a measurement step; each slice is proven before the next, and the
stack merges only after slice 5 (`user-confirmed` 2026-09-30).

1. **Model and sync** (done): kinds 12-19 in the codec and reducer.
2. **Measurements** (done, nothing committed): the iPhone filter bound and
   the Mac clear-then-apply gap; results under Blockers.
3. **Migration and storage** (done, PR #123): `13.sqm`, per-part retention,
   Mac app-choice migration, iPhone app choices one file per set.
4. **Pause sets UI** (done, PR #124), screenshots accepted at the gate.
5. **Hosts** (PR #125): one composed pause from all running parts with
   retention, latest end and device limits; iPhone table version 2 with one
   Swift composer for app and extension; AC-02, AC-03, AC-04 and a run over
   1,024 websites. Decisions (`user-confirmed` 2026-10-01): one PR, upgrade
   from a dev build of `v1.2.0`, deferred additions retried, a Mac manual
   part expiring while Posato is quit is a known limit.

## High-risk plan review

- **Verdict:** `approved` after three rounds (R1-R7, N1-N2); a new
  workspace's first-set name and default stay unpublished at first link
  (`user-confirmed` 2026-09-30, known limit).
- **Slice 3, 4 and 5 design reviews:** `changes-required`, folded in. The
  maintainer kept kept-app requirements in the policy database, chose the
  current first set as upgrade retention, and accepts an iPhone downgrade
  crash as failing closed.

## Result

- **Slice 1** (`1f96769`): as planned.
- **Slice 3** (`fee6e5e`..`aff62eb`): as planned, except set-scoped target
  loading moved to slice 5, the capacity copy to slice 4, and per-set
  reconciler tests written after a compiler-forced rewrite (deviation;
  seven mutations each fail one).
- **Slice 4** (`a356777`..`12f1df3`): as planned, except session targets
  moved here and per-part names to slice 5. Observed, not fixed: a tap in
  Session setup as a starting schedule closes it shows an Error window.
- **Slice 5** (`f6bf450`..`4c2aff0`): as planned, except:
  - iPhone: Swift is the only composer (app and extension, App Group
    `flock`); held records in the App Group replace SQL retention on iOS,
    and the table carries no default-set or held items;
  - `posato-control` gained `launch.skip` for closed-app checks;
  - the runs found and fixed: a pause part ending inside another cleared
    the Mac helper for up to a minute (`4883050`), a running session's set
    edit showed "Restrictions need attention" (`638c5f1`), reads that
    swept retention failed under a concurrent write (`24cc9f5`,
    `49fb68f`);
  - a disabled time-wheel arrow pressed through accessibility closed Posato
    (in the product since 1.0); fixed on the way;
  - deviation: the Swift v2 tests and the table test were written with the
    code; three mutations each fail one.

## Completed-change review

- **Slices 1, 3, 4:** `changes-required`, corrected (regressions failed
  first); declined: CHECK-clause parity (Recommended), a duplicated end
  time (Optional).
- **Slice 5:** `changes-required`, corrected in `4c2aff0` (tests failed
  first): Mac session and occurrences now count each other toward the
  limits (union could pass 64 apps and the helper cleared all); app-only
  set edits apply at once. Also taken (Recommended): replace keeps state
  when its clear fails, the extension clears when the rest pause nothing,
  no iOS SQL session retention, no iOS not-paused-yet count, a failed pin
  write skips the evaluation. Declined (Optional): adopt after a quit edit,
  partial admission of a part that never admitted, Swift descriptions.

## Verification

- Slices 1, 3, 4: new tests failed first (exceptions above); `quality`
  passes after each correction; Mac upgrade and downgrade on Tart from the
  1.2.0 DMG; `pause-sets-desktop.json` and `pause-sets-device.json` pass.
- Slice 5 `quality` passes at `4c2aff0` (193 Swift tests).
- **AC-03** (test iPhone, 2026-10-01): a `v1.2.0` dev build with consent,
  Calculator and a website, then 1.3 installed and never opened: the
  extension blocked both at start; overlapping 1.2 schedules updated
  during the first kept blocking when it ended; per-set apps, a
  websites-only start, two sets' union and a part's end with Posato
  closed, a manual session joined by a schedule.
- **AC-02** (two Tart VMs from a `v1.2.0` workspace): one first set with
  the old websites, session and schedule kept, 1.2 peer stopped receiving
  and its later edit landed in the first set, overlap and per-part
  release, add at once, removals kept (also on the peer and across a
  relaunch), 10-set cap on both, local-only link merge, offline start.
  Over 1,024: removed items stayed paused and additions waited.
- **AC-04:** checked by the maintainer (`user-confirmed` 2026-10-02): a set
  made on the Mac started with its websites on the iPhone through a
  schedule. The unattended run was blocked by the test account's CloudKit.

## Blockers and accepted risks

- **iPhone web filter capacity:** fixed in 1.3 (`user-confirmed`
  2026-09-30); iPhone apps capped at 50.
- **Mac clear-then-apply gap:** about 0.2-0.25 s; accepted, `MACOS-025`.
- **Mac update gap** (observed): about 25 s unblocked while the app is
  down during an in-place update; quitting clears restrictions since 1.0.
- **Received session on the Mac** (documented 1.2 rule): after a relaunch
  during an occurrence it is not enforced until the occurrence ends.
- **CloudKit delivery** (observed): 6-10 min between Tart VMs late on
  2026-10-01; after the 1,024 run Remove workspace failed on the iPhone for
  hours, and a fresh Tart VM could not establish a workspace
  (`Sync did not finish`) on 2026-10-02 01:00. The test iPhone is still
  linked to that workspace; its CloudKit zones need cleaning before the next
  linked run.
- **Offline checks:** `vm network --state off` leaves a Mac nothing to
  enforce through; offline runs keep the service, unroutable.

## Final

- **Status:** `done`; **Outcome:** ready for review; the stack (#119, #123,
  #124, #125) merges together with the maintainer.
