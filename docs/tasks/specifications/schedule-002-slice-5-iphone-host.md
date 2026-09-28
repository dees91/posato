# `SCHEDULE-002` slice 5: Schedules start on their own on an iPhone

- **Review tier:** `high-risk`
- **Tier reason:** The Device Activity monitor applies and clears shields while the app is closed, from a table of paused items the app writes into the App Group.
- **Dependencies:** slice 4 (#101, the host, its policy and pins), slice 3 (#99), `IOS-006` (merged, the monitor extension and its early-callback rule).
- **Integration group:** `PR-SCHEDULE-DELIVERY`, milestone `1.2.0`.
- **Authority:** [schedule rules](../../product/schedules-decisions.md) ("iPhone execution", "One pause at a time", "Notifications", integration contracts 2-4), [`DESIGN.md`](../../../DESIGN.md#release-12-setup-and-schedules).

## Outcome

On an iPhone that allowed Screen Time, each enabled schedule is registered as a repeating Device Activity; the monitor extension applies the paused items at the start and clears them at the end, also when Posato is closed, and announces the start and the end when notices are on. When the app opens or returns during an occurrence it catches up. Skip, End early, off and delete stop the occurrence here and on the other devices. A schedule never lifts a manual session's shields, and a manual session never lifts a schedule's.

## Boundaries

- **Separate store.** Scheduled pauses use their own named Managed Settings store; stores combine, so no claims are needed on iPhone.
- **One table, one writer.** Only the app writes the versioned App Group table (plans, stopped dates, running occurrences, paused items, the notices switch); the extension writes only one start record per occurrence. Both stay on the device and out of backups.
- **Consent.** Screen Time authorization is the consent; nothing is registered before it.
- **Extension.** It links nothing from the shared Kotlin framework and logs nothing.
- **Non-goals:** a schedule's paused-item changes reaching a running occurrence before its next start; a "Setup required" notification on iPhone (Schedules shows the Allow Screen Time card).

## Acceptance

- `AC-01` — Isolated tests: the table (enabled plans, stopped dates, running occurrences, paused items); the host with a monitor that announces; the Screen Time gate and the schedule store's claims; in Swift, the occurrence rule (weekday of the start, midnight, a stopped date, a late callback, a skipped wall time), the table and start-record files, the start and end events (announce once, a skip, notices off, an early end, an overlap, an unreadable table), and registration (none before Screen Time, one activity per plan, removed plans stopped, a tail for an edited running occurrence).
- `AC-02` — Test iPhone: a schedule 15 or more minutes ahead starts with the app closed (`observe-blocking-ios.json`), ends on time (`observe-unblocked-ios.json`), and catches up on launch; Skip and End early; a manual session inside a schedule.

## Verification

<!-- Unattended by default (AGENTS.md). -->

- `./gradlew quality` (JVM and iOS tests, the Swift tests on the Simulator, the device build of the app and the extension); the device E2E in `AC-02`, or a recorded blocker naming the failing command.
