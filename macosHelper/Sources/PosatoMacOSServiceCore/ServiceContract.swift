import Foundation

public enum ServiceContract {
  public static let applicationIdentifier = "app.posato.macos"
  public static let helperIdentifier = "app.posato.macos.helper"
  public static let daemonIdentifier = "app.posato.macos.proxy-settings"
  public static let daemonPlistName = "app.posato.macos.proxy-settings.plist"
}

@objc public protocol ProxySettingsService {
  func perform(
    _ request: Data,
    withReply reply: @escaping (Data?) -> Void
  )
}
