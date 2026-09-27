# Execution: `NOTIFY-001`

- **Brief:** [Local notifications for pauses](../specifications/notify-001-session-notifications.md)
- **Status:** `ready-for-review`
- **Review tier:** `standard`
- **Implementer:** Claude, under the maintainer's delegated night mandate (2026-09-26)
- **Reviewer:** independent agent (completed change)
- **Branch:** `feature/notify-001-session-notifications`
- **Updated:** 2026-09-27

## Plan

1. `SessionNotificationPlanner` (shared): pure rules that map status transitions to actions: schedule the end, cancel it, announce a pause started elsewhere, and ask permission.
2. `SessionNotices` and `SessionNotifier` (shared): apply the rules through a `SessionNotificationPlatform` port, keep the preference and the asked-once marker, and run where the session owner is hosted (the iOS `PosatoApplication`, the Mac `DesktopPresence`).
3. Platforms: `SessionNotificationCenter` (Swift, `UNUserNotificationCenter`) and `MacSessionNotifications` (the AppKit JNI leaf, `UNUserNotificationCenter`, with a foreground-presentation delegate). The preferences use `UserDefaults`.
4. UI: a **Notifications** section in About Posato.
5. Tests written first for the planner; notices tests confirmed by mutation (see Result).

## Result

- **Delivered.**
  - Planner rules in `SessionNotificationPlanner`.
  - `SessionNotices` and `SessionNotifier`, hosted by `PosatoApplication` on iOS and by `DesktopPresence` on the Mac.
  - `SessionNotificationPlatform`, implemented by `SessionNotificationCenter` in Swift and by `MacSessionNotifications` through the AppKit JNI leaf with `-framework UserNotifications`.
  - The **Notifications** section in About Posato.
  - Updates to `DESIGN.md` and `PRIVACY.md`.
  - `posato-control` gains `vm allow-notifications`. The macOS banner shows **Options**, and so **Allow**, only under the pointer.
- **Tests.**
  - The planner tests (5) were written first and failed against a stub.
  - The `SessionNotices` tests are listed under the `AC-04` deviation below; `SessionNoticesStaleEndTest` covers the stale-end check.
  - Notice texts sit behind an interface, so the tests need no Compose resources.
- **Deviation from `AC-04`.** The planner tests were written failing first. The preference and asked-once tests (`SessionNoticesTest`, `SessionNoticesSwitchTest`) were written after the code; mutations that drop each guard (`cancelEnd` on switch-off, the reschedule on switch-on, the asked-once flag) make them fail.
- **Deviation.** A worktree without the ignored `local.properties` builds an ad hoc package. Its This Mac state is unavailable, which is the known `MACOS-007` behavior. Copying `local.properties` into the worktree fixed it.

## Completed-change review

- **Verdict:** `changes-required`, then resolved.
- **R1** (switch-off half of `AC-03` unevidenced, toggle untested): `SessionNoticesSwitchTest` turns the switch off and on during a pause; the VM run below turns it off before a pause that expires.
- **Rec1** (permission request blocking the collector): the request now runs beside the collector, and an ALLOWED answer reschedules the current pause's end.
- **Rec2** (Objective-C exception across JNI in an unbundled run): the leaf returns early when the process has no bundle identifier.
- **O1** (switch race): the switch is read again after the text loads.
- **O2** (receive-only devices are never asked): kept as briefed; a product question for the maintainer.
- **O3** (`@autoreleasepool` in the JNI leaf): not taken. The leaf's existing functions share the pattern; wrapping only the new ones would be inconsistent, and a leaf-wide change belongs in its own change.
- **O4** (weak iOS delegate if the view controller is ever built twice): accepted residual; the app has one scene.
- **O5** (housekeeping): `GuestNotificationPrompt` moved to its own file, and `vm allow-notifications` checks that the banner names Posato.
- **Re-review:** `changes-required` (R-A: `AC-01` not rerun on the fixed build), then resolved by the fresh-clone run in Verification below. The review of that correction approved it.
  - **Rec-A**: switching notices on during the first pause now asks and, once allowed, schedules the end again (test).
  - **Rec-B**: a test holds the permission answer and shows an early end is withdrawn at once and not restored by the later answer.
  - **O-A**: scheduling checks that the pause still ends at that time after the text loads (test; the mutation that drops the check fails it).
  - **O-B**: the record's status lines and test bullets are aligned.

### Maintainer review (2026-09-27, three P2)

- **Permission only after a local start.** Turning the switch on asks only during a pause started on this device and only if the system was never asked; before, it asked whenever the permission was undetermined.
- **Opt-out during the started-elsewhere text load.** The switch is read again after the text loads, as for the end notice.
- **Pending permission read.** The read runs beside the status collector, so a first local pause that starts while it is pending still asks.
- Each fix has a test that failed before it (`SessionNoticesPermissionTriggerTest`, and the switch test now expects no ask for a received pause).

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `:shared:jvmTest`, `:desktopApp:test`, detekt, ktlint | pass | notifications, composition, and desktop suites |
| Mac, fresh Tart clone (`AC-01`) | pass | The first start raised "Posato" Notifications once. `vm allow-notifications` allowed it. **Pause over** appeared at the natural end of a 5-minute pause. An early end removed the pending request (usernoted log). |
| Mac, fresh Tart clone on the fixed build (`AC-01`, re-review R-A) | pass | Run `20260927-021322-056d` (5-minute pause): the end notice was requested at 02:13:34 before permission; the permission banner was allowed at 02:13:48 (`vm allow-notifications`, retried until usernoted logged "allow: YES"), and the end was requested again in the same second; at the natural end (02:18:26) usernoted presented it "as banner". A first attempt's allow click missed while macOS's "App Background Activity" banner was stacked above it; that run is not counted. |
| Mac, switch off (`AC-03`) | pass | The **Pause notifications** switch read `0` in About. A 5-minute pause then expired (`session-expiry-desktop`, run `20260927-005446-6735`); the screen showed no **Pause over**, and `usernoted` logged no Posato request. |
| Mac, system-denied state (`AC-03`) | pass | With notifications off in System Settings, About showed the switch disabled with "Notifications are turned off for Posato in System Settings." |
| iOS Simulator, iPhone 17 (`AC-02`) | pass | The permission alert appeared after the first start, and Allow was tapped through the SpringBoard scope. The driver found **Pause over** in SpringBoard at the end of a 5-minute pause. |
| Test iPhone | blocked | `DEVICE_AUTOMATION_LOCKED`: the phone must be unlocked and the XCTest passcode entered after a restart. |

## Blockers and accepted risks

- **Not driven end to end: "Pause started" from another device.** It needs two linked devices in one iCloud workspace. The planner tests cover the rule, and the post path is the same one proven by the permission and end notices.
- **Test iPhone.** Rerun on the device once it is unlocked for XCTest.

## Final

- **Status:** `ready-for-review`
- **Outcome:** met on Mac and Simulator; the device run waits for the test iPhone to be unlocked for XCTest
