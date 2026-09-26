# Execution: `NOTIFY-001`

- **Brief:** [Local notifications for pauses](../specifications/notify-001-session-notifications.md)
- **Status:** `active`
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
  - The `SessionNotices` tests (preference off, permission asked once) were written after the code. A mutation that dropped both guards made both fail.
  - Notice texts sit behind an interface, so the tests need no Compose resources.
- **Deviation.** A worktree without the ignored `local.properties` builds an ad hoc package. Its This Mac state is unavailable, which is the known `MACOS-007` behavior. Copying `local.properties` into the worktree fixed it.

## Completed-change review

- **Verdict:** `pending`

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `:shared:jvmTest`, `:desktopApp:test`, detekt, ktlint | pass | notifications, composition, and desktop suites |
| Mac, fresh Tart clone (`AC-01`) | pass | The first start raised "Posato" Notifications once. `vm allow-notifications` allowed it. **Pause over** appeared at the natural end of a 5-minute pause. An early end removed the pending request (usernoted log). |
| Mac, system-denied state (`AC-03`) | pass | With notifications off in System Settings, About showed the switch disabled with "Notifications are turned off for Posato in System Settings." |
| iOS Simulator, iPhone 17 (`AC-02`) | pass | The permission alert appeared after the first start, and Allow was tapped through the SpringBoard scope. The driver found **Pause over** in SpringBoard at the end of a 5-minute pause. |
| Test iPhone | blocked | `DEVICE_AUTOMATION_LOCKED`: the phone must be unlocked and the XCTest passcode entered after a restart. |

## Blockers and accepted risks

- **Not driven end to end: "Pause started" from another device.** It needs two linked devices in one iCloud workspace. The planner tests cover the rule, and the post path is the same one proven by the permission and end notices.
- **Test iPhone.** Rerun on the device once it is unlocked for XCTest.

## Final

- **Status:** `active` until the completed-change review
- **Outcome:** met on Mac and Simulator; device run pending
