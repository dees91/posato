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

- Pending.

## Completed-change review

- **Verdict:** `pending`

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Pending | | |

## Blockers and accepted risks

- Pending.

## Final

- **Status:** `active`
- **Outcome:** pending
