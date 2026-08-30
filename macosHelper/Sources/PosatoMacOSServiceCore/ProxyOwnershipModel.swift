import Foundation

public enum ProxyValue: Codable, Equatable, Sendable {
  case integer(Int64)
  case string(String)
}

public struct ProxyTuple: Codable, Equatable, Sendable {
  public var enabled: ProxyValue?
  public var host: ProxyValue?
  public var port: ProxyValue?

  public init(enabled: ProxyValue?, host: ProxyValue?, port: ProxyValue?) {
    self.enabled = enabled
    self.host = host
    self.port = port
  }

  var hasValidBaselineSemantics: Bool {
    let validEnabled: Bool
    switch enabled {
    case .integer(let value):
      validEnabled = value == 0 || value == 1
    case nil:
      validEnabled = true
    case .string:
      validEnabled = false
    }
    let validHost: Bool
    switch host {
    case .string(let value):
      validHost = value.utf8.count <= 2_048
    case nil:
      validHost = true
    case .integer:
      validHost = false
    }
    let validPort: Bool
    switch port {
    case .integer(let value):
      validPort = value >= 0 && value <= 65_535
    case nil:
      validPort = true
    case .string:
      validPort = false
    }
    return validEnabled && validHost && validPort
  }

  var hasValidAppliedSemantics: Bool {
    return enabled == .integer(1)
      && host == .string("127.0.0.1")
      && {
        if case .integer(let value) = port {
          return value > 0 && value <= 65_535
        }
        return false
      }()
  }

  var isEnabled: Bool {
    return enabled != nil && enabled != .integer(0)
  }
}

public struct ProxySnapshot: Equatable, Sendable {
  public let serviceIdentifier: String
  public let http: ProxyTuple
  public let https: ProxyTuple
  public let additionalProxyEnabled: Bool

  public init(
    serviceIdentifier: String,
    http: ProxyTuple,
    https: ProxyTuple,
    additionalProxyEnabled: Bool = false
  ) {
    self.serviceIdentifier = serviceIdentifier
    self.http = http
    self.https = https
    self.additionalProxyEnabled = additionalProxyEnabled
  }

  var hasEnabledProxy: Bool {
    return http.isEnabled || https.isEnabled || additionalProxyEnabled
  }
}

public struct OwnershipRecord: Codable, Equatable, Sendable {
  public static let schemaVersion = 1

  public let schema: Int
  public let sessionIdentifier: Data
  public let requestIdentifier: Data
  public let canonicalInputDigest: Data
  public let serviceIdentifier: String
  public var phase: OwnershipPhase
  public let baselineHTTP: ProxyTuple
  public let baselineHTTPS: ProxyTuple
  public let appliedHTTP: ProxyTuple
  public let appliedHTTPS: ProxyTuple

  public init(
    sessionIdentifier: Data,
    requestIdentifier: Data,
    canonicalInputDigest: Data,
    serviceIdentifier: String,
    phase: OwnershipPhase,
    baselineHTTP: ProxyTuple,
    baselineHTTPS: ProxyTuple,
    appliedHTTP: ProxyTuple,
    appliedHTTPS: ProxyTuple
  ) {
    schema = Self.schemaVersion
    self.sessionIdentifier = sessionIdentifier
    self.requestIdentifier = requestIdentifier
    self.canonicalInputDigest = canonicalInputDigest
    self.serviceIdentifier = serviceIdentifier
    self.phase = phase
    self.baselineHTTP = baselineHTTP
    self.baselineHTTPS = baselineHTTPS
    self.appliedHTTP = appliedHTTP
    self.appliedHTTPS = appliedHTTPS
  }

  public var hasValidBounds: Bool {
    return schema == Self.schemaVersion
      && sessionIdentifier.count == WireLimits.identifierBytes
      && sessionIdentifier.contains { $0 != 0 }
      && requestIdentifier.count == WireLimits.identifierBytes
      && requestIdentifier.contains { $0 != 0 }
      && canonicalInputDigest.count == 32
      && !serviceIdentifier.isEmpty
      && serviceIdentifier.utf8.count <= 256
      && !serviceIdentifier.utf8.contains(0)
      && phase != .idle
      && baselineHTTP.hasValidBaselineSemantics
      && baselineHTTPS.hasValidBaselineSemantics
      && appliedHTTP.hasValidAppliedSemantics
      && appliedHTTPS == appliedHTTP
  }
}

public protocol OwnershipPersistence: Sendable {
  func load() throws -> OwnershipRecord?
  func save(_ record: OwnershipRecord) throws
  func remove() throws
}

public protocol ProxyConfigurationAccess: Sendable {
  func currentPrimaryServiceIdentifier() throws -> String
  func snapshot(serviceIdentifier: String) throws -> ProxySnapshot
  func replaceTuples(
    expected: ProxySnapshot,
    http: ProxyTuple,
    https: ProxyTuple,
    requirePrimaryService: Bool
  ) throws -> ProxySnapshot
}

public enum ProxyOwnershipFailure: Error, Equatable {
  case conflict
  case invalidInput
  case recoveryRequired
  case unavailable
}
