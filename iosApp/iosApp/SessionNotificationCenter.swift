import Foundation
import PosatoShared
import UserNotifications

/// Posts and schedules Posato's local pause notices. Nothing leaves the iPhone: the notices are
/// local, and the preference lives in the app's own defaults.
final class SessionNotificationCenter: NSObject, SessionNotificationPlatform, UNUserNotificationCenterDelegate {
    private static let endIdentifier = "app.posato.ios.session.end"
    private static let enabledKey = "PosatoPauseNotificationsEnabled"
    private static let askedKey = "PosatoPauseNotificationsAsked"

    private let center: UNUserNotificationCenter
    private let defaults: UserDefaults

    init(center: UNUserNotificationCenter = .current(), defaults: UserDefaults = .standard) {
        self.center = center
        self.defaults = defaults
        super.init()
        center.delegate = self
    }

    var receivedPauseNeedsResume: Bool { false }

    func permission(handler: @escaping (NotificationPermission) -> Void) {
        center.getNotificationSettings { settings in
            handler(Self.permission(settings.authorizationStatus))
        }
    }

    func requestPermission(handler: @escaping (NotificationPermission) -> Void) {
        center.requestAuthorization(options: [.alert, .sound]) { _, _ in
            self.permission(handler: handler)
        }
    }

    func scheduleEnd(atEpochMillis: Int64, title: String, body: String) {
        let seconds = Double(atEpochMillis) / 1000 - Date().timeIntervalSince1970
        guard seconds > 0 else { return }
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false)
        center.add(UNNotificationRequest(identifier: Self.endIdentifier, content: content, trigger: trigger))
    }

    func cancelEnd() {
        center.removePendingNotificationRequests(withIdentifiers: [Self.endIdentifier])
    }

    func post(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        center.add(UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil))
    }

    func isEnabled() -> Bool {
        defaults.object(forKey: Self.enabledKey) as? Bool ?? true
    }

    func setEnabled(enabled: Bool) {
        defaults.set(enabled, forKey: Self.enabledKey)
    }

    func wasPermissionAsked() -> Bool {
        defaults.bool(forKey: Self.askedKey)
    }

    func markPermissionAsked() {
        defaults.set(true, forKey: Self.askedKey)
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }

    private static func permission(_ status: UNAuthorizationStatus) -> NotificationPermission {
        switch status {
        case .notDetermined:
            return .notDetermined
        case .denied:
            return .denied
        default:
            return .allowed
        }
    }
}
