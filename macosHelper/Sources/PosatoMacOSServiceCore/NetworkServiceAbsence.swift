import Foundation
import SystemConfiguration

/// Confirms that a recorded network service is gone, so no Posato-owned tuple can remain in it
/// (ADR 0004, `MACOS-020`).
enum NetworkServiceAbsence {
  static func isConfirmed(
    serviceIdentifier: String,
    preferences: SCPreferences,
    storeName: CFString
  ) throws -> Bool {
    guard !serviceIdentifier.contains("/") else {
      return false
    }
    guard SCPreferencesLock(preferences, false) else {
      throw SystemProxyConfigurationFailure.mutation
    }
    defer { SCPreferencesUnlock(preferences) }
    SCPreferencesSynchronize(preferences)
    let servicePath = "/\(kSCPrefNetworkServices)/\(serviceIdentifier)" as CFString
    guard SCPreferencesPathGetValue(preferences, servicePath) == nil,
      !anySetLinks(
        serviceIdentifier: serviceIdentifier,
        sets: SCPreferencesGetValue(preferences, kSCPrefSets)
      )
    else {
      return false
    }
    guard let store = SCDynamicStoreCreate(nil, storeName, nil, nil) else {
      throw SystemProxyConfigurationFailure.preferences
    }
    let proxiesKey = SCDynamicStoreKeyCreateNetworkServiceEntity(
      nil,
      kSCDynamicStoreDomainSetup,
      serviceIdentifier as CFString,
      kSCEntNetProxies
    )
    return SCDynamicStoreCopyValue(store, proxiesKey) == nil
  }

  /// Any set whose `Network/Service` dictionary still names the service, or sets that are missing
  /// or cannot be read as expected, count as a link.
  static func anySetLinks(serviceIdentifier: String, sets: CFPropertyList?) -> Bool {
    guard let setsByIdentifier = sets as? [String: Any] else {
      return true
    }
    return setsByIdentifier.values.contains { set in
      guard let set = set as? [String: Any] else {
        return true
      }
      guard let network = set[kSCCompNetwork as String] else {
        return false
      }
      guard let network = network as? [String: Any] else {
        return true
      }
      guard let services = network[kSCCompService as String] else {
        return false
      }
      guard let services = services as? [String: Any] else {
        return true
      }
      return services[serviceIdentifier] != nil
    }
  }
}
