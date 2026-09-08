import Foundation

/// System-critical processes that the picker must never offer and enforcement
/// must never terminate, even when selected (threat T-08). The helper cannot
/// trust the requirement set it is handed, so both sides consult this one
/// source instead of keeping their own copies.
enum SystemCriticalApplication {
  private static let bundleIdentifiers: Set<String> = [
    "com.apple.finder",
    "com.apple.dock",
    "com.apple.loginwindow",
    "com.apple.systemuiserver",
    "com.apple.controlcenter",
    "com.apple.notificationcenterui",
    "com.apple.systempreferences",
    "com.apple.SystemSettings",
  ]

  private static let coreServicesPrefix = "/System/Library/CoreServices/"

  static func isSystemCritical(bundleIdentifier: String?, bundleURL: URL?) -> Bool {
    if bundleURL?.path.hasPrefix(coreServicesPrefix) == true {
      return true
    }
    guard let bundleIdentifier else {
      return false
    }
    return bundleIdentifiers.contains(bundleIdentifier)
  }
}
