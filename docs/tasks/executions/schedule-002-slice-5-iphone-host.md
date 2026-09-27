# Execution: `SCHEDULE-002` slice 5

- **Brief:** [Schedules start on their own on an iPhone](../specifications/schedule-002-slice-5-iphone-host.md)
- **Status:** `ready-for-review` (device acceptance blocked, see Checks)
- **Review tier:** `high-risk`
- **Implementer:** Claude, under the maintainer's delegated goal (2026-09-27)
- **Reviewer:** independent agents (design and completed change)
- **Branch:** `feature/schedule-002-iphone-host`, stacked on #101
- **Updated:** 2026-09-27

## Plan

1. **Shared host.** `ScheduleClaims` becomes the host's enforcement port (`PauseClaims` on Mac); the host gains `announcesStarts`, `announcedElsewhere` and `publish`, and `run()`/`refresh()` join `ScheduledPauses`, so the process that hosts sessions also runs the host and a return to the foreground catches up.
2. **Table.** `ScheduleMonitorTables.build` (common, pure) lists enabled accepted plans with their stopped dates (skip, end, terminal; yesterday to 400 days ahead), the running occurrences with their pinned start and end, and the paused items.
3. **iPhone bridge.** `IosScheduleClaims` over the schedule's own store (release clears only what it holds, including what the extension applied), `IosScheduleStartGate` (Screen Time approved is ready; a platform failure retries), and `IosScheduleMonitorPublisher` (renders notice texts, publishes only changed tables, reads start records). DI takes one `IosScheduleBridge` from Swift.
4. **Swift.** `ScheduleMonitorShared.swift` (app and extension): names, the table and start-record files, the occurrence rule, the web-domain helper now shared with the manual enforcer, the store and poster seams. `ScheduleMonitorEvents.swift`: start applies and announces once per occurrence; end clears unless an occurrence still runs a minute later and says Pause over with the app's end-notice identifier. `IosScheduleMonitorPublisher.swift`: writes the table, prunes start records, registers one repeating activity per plan after Screen Time approval, leaves unchanged plans alone, stops removed ones, and adds a one-shot tail for a running occurrence whose edited plan no longer ends it. The extension routes by activity prefix; the expiry path is unchanged.
5. **Tests first** as listed in `AC-01`.

## Result

- **Delivered** as planned. On iPhone the extension announces starts, so the app never posts a second one; a setup notice is not posted there.
- **Design review.** An independent design pass (Plan agent) settled the separate store, the one-writer table, the start records, the tail, and the verification limits; its risks are carried below.
- **Completed-change review:** `changes-required` with one Critical and two Required findings, all fixed with tests: the table was republished only when a redacted fingerprint changed, so new plans, skips and deletions never reached the monitor (every evaluation now publishes; Swift rewrites the file only when it changed and always reconciles registrations); a later Screen Time approval registered nothing until the next launch (now reconciled on every publish); a non-Gregorian device calendar broke dates (Swift now uses a Gregorian calendar in the device's time zone). Recommended, taken: turning notices off refreshes the table at once; the monitor waits for a manual session's end before Pause over; interval comparison reads only the fields Posato sets.
- **Known limits** (`source-claim` or `hypothesis`, to confirm on the device): repeating schedules that cross midnight; `intervalDidStart` when monitoring starts inside an interval (the app's catch-up covers it); a fall-back night can run up to an hour past the 24-hour cap until the app opens; notices posted from the extension (the app's planned end notice uses the same identifier, so a duplicate replaces rather than stacks).

## Checks

- `./gradlew quality` passed: Kotlin JVM and iOS tests, the Swift tests on the iPhone 17 Simulator (17 new schedule monitor tests), and the device build of the app and the monitor extension.
- **Blocked:** the device acceptance in `AC-02`. `posato-control find -t device --text Schedules` answers `DEVICE_AUTOMATION_LOCKED` ("iOS did not enable UI automation for the driver … ask the device owner to unlock the iPhone and, if it asks, enter the passcode for XCTest"), and the Simulator cannot enforce Screen Time. Next action: the owner unlocks the test iPhone for XCTest once; then `observe-blocking-ios.json` at a schedule 16 minutes ahead with the app force-quit, and `observe-unblocked-ios.json` after its end.
