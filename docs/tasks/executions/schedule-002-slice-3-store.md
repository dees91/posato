# Execution: `SCHEDULE-002` slice 3

- **Brief:** [Schedules you can save, share and manage](../specifications/schedule-002-slice-3-store.md)
- **Status:** `ready-for-review`
- **Review tier:** `high-risk`
- **Implementer:** Claude, under the maintainer's delegated goal (2026-09-27)
- **Reviewer:** independent agents (plan and completed change)
- **Branch:** `feature/schedule-002-store`, stacked on #98
- **Updated:** 2026-09-27

## Plan

1. **Schema** (`migrations/11.sqm`, `Schedule.sq`), every column with `typeof` and range checks:
   - `local_schedule(id BLOB16 PK, name TEXT 1-80 bytes, weekdays 1-127, start 0-1439, end 0-1439, start <> end, enabled 0/1)`: the effective plans.
   - `local_schedule_fact(id, kind 'skip'|'end', year, month, day, PK all)`: grow-only facts, local and received.
   - `local_schedule_terminal(id, year, month, day, PK)` and `local_schedule_pin(id, year, month, day PK, start_epoch_millis, notices)`: created now for slice 4's host, unused in this slice.
   - `sync_schedule_intent(sequence PK, workspace_id BLOB16, kind 'put'|'remove'|'skip'|'end', id, nullable plan and date columns checked per kind)`.
   - `sync_schedule_seed(workspace_id BLOB16 PK)`: seeding ran for that workspace.
2. **Store** (`feature/schedules/data/SqlScheduleStore.kt`, interface `ScheduleStore`): `snapshot()` and a `changes` flow; `save(plan)`, `remove(id)`, `skip(ref, today)`. Each writes the local rows and, when a workspace is linked, one intent, in one transaction (workspace captured from the bootstrap store like `SyncTargetPolicyStore`). The cap and the wire rules are checked before writing. Sync side: `readIntents`, `deleteIntent`, `clearIntents`, `isSeeded`/`markSeeded`, `materialize(projection)`.
3. **Sync** (`sync/bootstrap/ScheduleSync.kt`): one pass per exchange in `AppleSync.runExchange`, after the session phase: seed once (puts for local plans the projection neither lists nor removed, skip/end intents for facts from yesterday on, and local plans the workspace removed are dropped), drain intents through `writer.mutate` (skipping what the projection already reflects, discarding other workspaces' rows), then materialize (projection schedules overlaid by remaining intents; projection facts merged into local facts). A drained put whose identifier is neither live nor removed publishes the capacity attention. `AppleWorkspaceRemoval` clears schedule intents and seeds and keeps local plans.
4. **Zone on iOS**: a common `OffsetScheduleZone(offsetAt)` resolves gaps (the transition instant, found by bisection) and overlaps (the first instance); iOS feeds it `NSTimeZone.localTimeZone.secondsFromGMTForDate`, reread each call. Tested with the synthetic zone.
5. **UI**: `SchedulesViewModel` replaces `SchedulesNavigationState` and reads the store, the engine and the zone.
   - List rows: name, days, hours (with "ends next day" when crossing midnight), an enabled switch, "Next: <weekday>, <date> at <time>" or "Turned off", and after Skip next both the skipped and the next run; one readiness line per device.
   - Editor: name, weekdays, start and end wheels, enabled; Save with inline errors (name, no day, under 15 minutes, equal times); Delete with confirmation.
   - Empty state with **Add schedule**; on a Mac that is not set up, the setup card stays first and Add stays available.
   - Preview copy and inactive buttons go.
   - The first Add on a linked workspace notes "Update Posato on your other devices to keep them in sync."
6. **Notices**: `SessionNotifier.onScheduleSaved()` asks once (the asked-once marker shared with the first local start); Schedules shows "Turn on pause notices" while undetermined.
7. **Tests, written first**: store transaction and intent tests, seeding once, overlay versus stale projection, remote removal versus local edit, capacity attention, the migration test, the zone algorithm, and the view model's save validation and skip-next key.
8. **E2E**: updated recipes on the Mac VM (and a linked peer), and on the iPhone or Simulator; screenshots and a GIF.

## High-risk plan review

- **Verdict:** `changes-required`, folded here before the store, sync and UI code.
- **R1 No silent loss at the cap.** The reducer adds `refusedSchedules` (the latest refused put per identifier that is neither live nor removed) to the projection and its digest. Materialize keeps them as local rows flagged `refused`; the UI shows "Couldn't sync: 10 schedules is the most" with Delete, hosts never run them, and a freed slot makes them live without any action. A new attention reason, `SCHEDULE_CAPACITY`, has its own text.
- **R2 Phase and publishing.** The schedule pass runs after the session phase and before the policy phase, and ends with `publishPending`. A failure halts the exchange like the policy phase. The final status keeps the more urgent of the schedule and policy results (action required, then capacity, then synced). Every store write that records an intent calls `syncNow()` through a decorator holding `AppleSync`, like `SyncTargetPolicyStore`.
- **R3 One validation.** The store saves `normalizeApplicationPolicyNameNfc(name.trim())` and checks `ScheduleWireRules` before writing; the editor uses the same rules. A drained row the writer refuses as `INVALID_MUTATION` is discarded, not retried forever.
- **R4 Migration tests.** Every downgrade helper also drops the six new tables.
- **R5 Mac creation follows DESIGN.md.** On a Mac, Add schedule appears once this Mac's setup is verified (the setup card comes first); slice 6 moves the gate to "Ready for schedules". On iPhone a plan can be saved before Screen Time is allowed.
- **R6 Removed stays removed.** Intents for identifiers in `removedScheduleIds` are dropped from the overlay and deleted.
- **Recommended, taken.** A save is refused ("Couldn't save. Try again.") when the workspace cannot be read, instead of writing without an intent. Seeding records its intents and the seed marker in one transaction under the write gate, and materialize reads intents and writes rows in one transaction from the projection after the drain. `SqlBootstrapStore.clearEstablished` clears schedule intents and seeds with the other intents. Deleting or removing a schedule prunes its facts, pins and terminal markers. The iOS zone multiplies `secondsFromGMTForDate` by 1000 and calls `NSTimeZone.resetSystemTimeZone()` before each read. Skip state comes from stored facts, and the skip key is `ScheduleOccurrences.next(...).key`. "Turn on pause notices" shows only while the switch is on, the permission is undetermined and a schedule exists; it asks the system directly. The update note shows when the workspace is linked and no schedule exists yet. Rows make no claim that a schedule will start until the hosts exist.
- **Optional.** `local_schedule_pin.notices` is a bitmask: 1 = start notice posted, 2 = setup-required notice posted.

## Result

- **Delivered.** Schedules are stored locally (migration 11), mirrored through intents and one schedule pass per exchange, and managed in the real Schedules screen and editor on Mac and iPhone. Plans refused at the shared cap stay visible as "Couldn't sync" and rejoin by themselves when a slot frees, or when the workspace is removed or they are removed locally. The first schedule saved on a device asks for notification permission once. Nothing starts automatically yet.
- **Deviations.**
  - The view model is a plain `SchedulesHolder` (like the other screens' holders), not a `SchedulesViewModel`.
  - Some store and holder tests were written after the code; each was checked by a mutation that the suite kills (workspace filter, reflected intents, invalid-intent discard, publish halt, intent deletion, notice trigger, empty-name refusal, cancel-delete, skip key, refused-plan promotion, failed-change notice).
  - The 10-schedule cap and the linked-peer check are covered by isolated tests (`SqlScheduleStoreTest`, `ScheduleSyncTest`) rather than E2E; slice 4's E2E already names "a peer VM receives the plan".
  - The test iPhone refused UI automation (`DEVICE_AUTOMATION_LOCKED`, "ask the device owner to unlock the iPhone"), so AC-04 ran on the iPhone 17 Simulator, as the brief allows. The Simulator reports Screen Time as allowed, so the "Allow Screen Time" card is proven by `ScheduleScreenTimeReadinessTest`, not on screen.
- **Completed-change review:** `changes-required` with one Required finding (the iPhone never showed "Allow Screen Time"), fixed by reading Screen Time access passively and asking only from the card. Recommended findings taken: refused plans rejoin after removal or unlinking; `SyncScheduleStore` tests; a notice when a list change does not save; `SchedulePlan` redacts its name. Optional, declined: an `AppleSync` test for the capacity status precedence (the precedence is two lines, covered by the pass result tests).

## Checks

- `./gradlew quality` passed after the last correction (local properties moved aside for the packaging check).
- Mac, Tart VM (primary line): onboarding with setup deferred; `schedules-mac-needs-setup-desktop.json` (setup card, no Add schedule); setup from Schedules with the system dialogs answered by the driver (27 s), then Add schedule offered; `schedules-desktop.json` passed end to end (empty-name refusal, save with next run, skip next, rename and turn off, relaunch, Keep and Delete), and again on the final build after the review corrections.
- iPhone 17 Simulator (iOS 26.5): `first-install-skip.json`, then `schedules-device.json` passed end to end.
