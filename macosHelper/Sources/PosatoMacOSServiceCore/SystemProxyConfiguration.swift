import Foundation
import SystemConfiguration

public enum SystemProxyConfigurationFailure: Error, Equatable {
  case primaryService
  case preferences
  case protocolConfiguration
  case mutation
}

public final class SystemProxyConfiguration: ProxyConfigurationAccess, @unchecked Sendable {
  private let preferenceName = "Posato proxy settings" as CFString

  public init() {}

  public func currentPrimaryServiceIdentifier() throws -> String {
    guard
      let store = SCDynamicStoreCreate(nil, preferenceName, nil, nil),
      let global = SCDynamicStoreCopyValue(
        store,
        "State:/Network/Global/IPv4" as CFString
      ) as? [String: Any],
      let identifier = global["PrimaryService"] as? String,
      !identifier.isEmpty,
      identifier.utf8.count <= 256
    else {
      throw SystemProxyConfigurationFailure.primaryService
    }
    return identifier
  }

  public func snapshot(serviceIdentifier: String) throws -> ProxySnapshot {
    let preferences = try createPreferences()
    SCPreferencesSynchronize(preferences)
    return try snapshot(preferences: preferences, serviceIdentifier: serviceIdentifier)
  }

  public func replaceTuples(
    expected: ProxySnapshot,
    http: ProxyTuple,
    https: ProxyTuple,
    requirePrimaryService: Bool
  ) throws -> ProxySnapshot {
    guard geteuid() == 0 else {
      throw SystemProxyConfigurationFailure.mutation
    }
    let preferences = try createPreferences()
    guard SCPreferencesLock(preferences, false) else {
      throw SystemProxyConfigurationFailure.mutation
    }
    defer { SCPreferencesUnlock(preferences) }
    SCPreferencesSynchronize(preferences)
    if requirePrimaryService {
      guard try currentPrimaryServiceIdentifier() == expected.serviceIdentifier else {
        throw ProxyOwnershipFailure.conflict
      }
    }
    let protocolValue = try proxyProtocol(
      preferences: preferences,
      serviceIdentifier: expected.serviceIdentifier
    )
    let current = try snapshot(
      protocolValue: protocolValue,
      serviceIdentifier: expected.serviceIdentifier
    )
    guard current == expected else {
      throw ProxyOwnershipFailure.conflict
    }
    let values = try configuration(protocolValue: protocolValue)
    let expectedValues = Self.replacingTuples(in: values, http: http, https: https)
    guard SCNetworkProtocolSetConfiguration(protocolValue, expectedValues as CFDictionary),
      SCPreferencesCommitChanges(preferences),
      SCPreferencesApplyChanges(preferences)
    else {
      throw SystemProxyConfigurationFailure.mutation
    }
    SCPreferencesSynchronize(preferences)
    let resultingProtocol = try proxyProtocol(
      preferences: preferences,
      serviceIdentifier: expected.serviceIdentifier
    )
    let resultingValues = try configuration(protocolValue: resultingProtocol)
    guard Self.dictionariesEqual(resultingValues, expectedValues) else {
      throw ProxyOwnershipFailure.recoveryRequired
    }
    return try snapshot(
      protocolValue: resultingProtocol,
      serviceIdentifier: expected.serviceIdentifier
    )
  }

  private func createPreferences() throws -> SCPreferences {
    guard let preferences = SCPreferencesCreate(nil, preferenceName, nil) else {
      throw SystemProxyConfigurationFailure.preferences
    }
    return preferences
  }

  private func snapshot(
    preferences: SCPreferences,
    serviceIdentifier: String
  ) throws -> ProxySnapshot {
    let protocolValue = try proxyProtocol(
      preferences: preferences,
      serviceIdentifier: serviceIdentifier
    )
    return try snapshot(
      protocolValue: protocolValue,
      serviceIdentifier: serviceIdentifier
    )
  }

  private func snapshot(
    protocolValue: SCNetworkProtocol,
    serviceIdentifier: String
  ) throws -> ProxySnapshot {
    let values = try configuration(protocolValue: protocolValue)
    return ProxySnapshot(
      serviceIdentifier: serviceIdentifier,
      http: try tuple(prefix: "HTTP", values: values),
      https: try tuple(prefix: "HTTPS", values: values),
      automaticProxyEnabled: try automaticProxyEnabled(values: values)
    )
  }

  private func proxyProtocol(
    preferences: SCPreferences,
    serviceIdentifier: String
  ) throws -> SCNetworkProtocol {
    guard let service = SCNetworkServiceCopy(preferences, serviceIdentifier as CFString),
      let protocolValue = SCNetworkServiceCopyProtocol(
        service,
        kSCNetworkProtocolTypeProxies
      )
    else {
      throw SystemProxyConfigurationFailure.protocolConfiguration
    }
    return protocolValue
  }

  private func configuration(protocolValue: SCNetworkProtocol) throws -> [String: Any] {
    guard let configuration = SCNetworkProtocolGetConfiguration(protocolValue) else {
      guard SCError() == kSCStatusOK else {
        throw SystemProxyConfigurationFailure.protocolConfiguration
      }
      return [:]
    }
    guard let values = configuration as NSDictionary as? [String: Any] else {
      throw SystemProxyConfigurationFailure.protocolConfiguration
    }
    return values
  }

  private func tuple(prefix: String, values: [String: Any]) throws -> ProxyTuple {
    return ProxyTuple(
      enabled: try proxyValue(values["\(prefix)Enable"]),
      host: try proxyValue(values["\(prefix)Proxy"]),
      port: try proxyValue(values["\(prefix)Port"])
    )
  }

  private func proxyValue(_ value: Any?) throws -> ProxyValue? {
    guard let value else {
      return nil
    }
    if let string = value as? String, string.utf8.count <= 2_048 {
      return .string(string)
    }
    if let number = value as? NSNumber {
      return .integer(number.int64Value)
    }
    throw SystemProxyConfigurationFailure.protocolConfiguration
  }

  private func automaticProxyEnabled(values: [String: Any]) throws -> Bool {
    return try enabledFlag(values["ProxyAutoConfigEnable"])
      || enabledFlag(values["ProxyAutoDiscoveryEnable"])
  }

  private func enabledFlag(_ value: Any?) throws -> Bool {
    guard let value else {
      return false
    }
    guard let number = value as? NSNumber else {
      throw SystemProxyConfigurationFailure.protocolConfiguration
    }
    return number.intValue != 0
  }

  private func set(
    tuple: ProxyTuple,
    prefix: String,
    values: inout [String: Any]
  ) {
    set(value: tuple.enabled, key: "\(prefix)Enable", values: &values)
    set(value: tuple.host, key: "\(prefix)Proxy", values: &values)
    set(value: tuple.port, key: "\(prefix)Port", values: &values)
  }

  static func replacingTuples(
    in original: [String: Any],
    http: ProxyTuple,
    https: ProxyTuple
  ) -> [String: Any] {
    var values = original
    let configuration = SystemProxyConfiguration()
    configuration.set(tuple: http, prefix: "HTTP", values: &values)
    configuration.set(tuple: https, prefix: "HTTPS", values: &values)
    return values
  }

  static func dictionariesEqual(
    _ first: [String: Any],
    _ second: [String: Any]
  ) -> Bool {
    return NSDictionary(dictionary: first).isEqual(to: second)
  }

  private func set(
    value: ProxyValue?,
    key: String,
    values: inout [String: Any]
  ) {
    switch value {
    case .integer(let integer):
      values[key] = NSNumber(value: integer)
    case .string(let string):
      values[key] = string
    case nil:
      values.removeValue(forKey: key)
    }
  }
}
