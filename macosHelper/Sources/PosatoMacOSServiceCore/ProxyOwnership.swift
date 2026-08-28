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
}

public struct ProxySnapshot: Equatable, Sendable {
  public let serviceIdentifier: String
  public let http: ProxyTuple
  public let https: ProxyTuple
  public let automaticProxyEnabled: Bool

  public init(
    serviceIdentifier: String,
    http: ProxyTuple,
    https: ProxyTuple,
    automaticProxyEnabled: Bool = false
  ) {
    self.serviceIdentifier = serviceIdentifier
    self.http = http
    self.https = https
    self.automaticProxyEnabled = automaticProxyEnabled
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

public final class ProxyOwnershipEngine: @unchecked Sendable {
  private let persistence: OwnershipPersistence
  private let configuration: ProxyConfigurationAccess
  private let loopbackHost = "127.0.0.1"

  public init(
    persistence: OwnershipPersistence,
    configuration: ProxyConfigurationAccess
  ) {
    self.persistence = persistence
    self.configuration = configuration
  }

  public func status() throws -> OwnershipPhase {
    guard let record = try persistence.load() else {
      return .idle
    }
    guard record.hasValidBounds else {
      throw ProxyOwnershipFailure.recoveryRequired
    }
    return record.phase
  }

  public func apply(
    sessionIdentifier: Data,
    requestIdentifier: Data,
    canonicalInputDigest: Data,
    port: UInt16
  ) throws -> OwnershipPhase {
    guard port > 0 else {
      throw ProxyOwnershipFailure.invalidInput
    }
    if try matchingApplyRecord(
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: requestIdentifier,
      canonicalInputDigest: canonicalInputDigest
    ) != nil {
      return try reconcile()
    }
    let serviceIdentifier = try configuration.currentPrimaryServiceIdentifier()
    let baseline = try configuration.snapshot(serviceIdentifier: serviceIdentifier)
    guard !baseline.automaticProxyEnabled else {
      throw ProxyOwnershipFailure.unavailable
    }
    let applied = appliedTuple(port: port)
    var record = OwnershipRecord(
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: requestIdentifier,
      canonicalInputDigest: canonicalInputDigest,
      serviceIdentifier: serviceIdentifier,
      phase: .prepared,
      baselineHTTP: baseline.http,
      baselineHTTPS: baseline.https,
      appliedHTTP: applied,
      appliedHTTPS: applied
    )
    try persistence.save(record)
    let resulting = try configuration.replaceTuples(
      expected: baseline,
      http: applied,
      https: applied,
      requirePrimaryService: true
    )
    guard resulting.http == applied, resulting.https == applied else {
      throw ProxyOwnershipFailure.recoveryRequired
    }
    record.phase = .applied
    try persistence.save(record)
    return .applied
  }

  public func verifyApplyOwnership(
    sessionIdentifier: Data,
    requestIdentifier: Data,
    canonicalInputDigest: Data
  ) throws {
    _ = try matchingApplyRecord(
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: requestIdentifier,
      canonicalInputDigest: canonicalInputDigest
    )
  }

  public func reconcile() throws -> OwnershipPhase {
    guard let record = try persistence.load() else {
      return .idle
    }
    guard record.hasValidBounds else {
      throw ProxyOwnershipFailure.recoveryRequired
    }
    let current = try configuration.snapshot(
      serviceIdentifier: record.serviceIdentifier
    )
    if record.phase == .prepared,
      current.http == record.baselineHTTP,
      current.https == record.baselineHTTPS
    {
      try persistence.remove()
      return .idle
    }
    return try restore(record: record, current: current)
  }

  public func reconcile(
    sessionIdentifier: Data,
    requestIdentifier: Data,
    canonicalInputDigest: Data
  ) throws -> OwnershipPhase {
    guard sessionIdentifier.count == WireLimits.identifierBytes,
      requestIdentifier.count == WireLimits.identifierBytes,
      canonicalInputDigest.count == 32
    else {
      throw ProxyOwnershipFailure.invalidInput
    }
    guard let record = try persistence.load() else {
      return .idle
    }
    guard record.sessionIdentifier == sessionIdentifier,
      record.requestIdentifier == requestIdentifier,
      record.canonicalInputDigest == canonicalInputDigest
    else {
      throw ProxyOwnershipFailure.conflict
    }
    return try reconcile()
  }

  public func restore() throws -> OwnershipPhase {
    guard let record = try persistence.load() else {
      return .idle
    }
    guard record.hasValidBounds else {
      throw ProxyOwnershipFailure.recoveryRequired
    }
    let current = try configuration.snapshot(
      serviceIdentifier: record.serviceIdentifier
    )
    return try restore(record: record, current: current)
  }

  public func maintain() throws -> OwnershipPhase {
    guard let record = try persistence.load() else {
      return .idle
    }
    return try maintain(record: record)
  }

  public func maintain(
    sessionIdentifier: Data,
    requestIdentifier: Data
  ) throws -> OwnershipPhase {
    guard sessionIdentifier.count == WireLimits.identifierBytes,
      requestIdentifier.count == WireLimits.identifierBytes
    else {
      throw ProxyOwnershipFailure.invalidInput
    }
    guard let record = try persistence.load() else {
      return .idle
    }
    guard record.sessionIdentifier == sessionIdentifier,
      record.requestIdentifier == requestIdentifier
    else {
      throw ProxyOwnershipFailure.conflict
    }
    return try maintain(record: record)
  }

  private func maintain(record: OwnershipRecord) throws -> OwnershipPhase {
    guard record.hasValidBounds, record.phase == .applied else {
      throw ProxyOwnershipFailure.recoveryRequired
    }
    let primaryService = try configuration.currentPrimaryServiceIdentifier()
    let current = try configuration.snapshot(serviceIdentifier: record.serviceIdentifier)
    guard primaryService == record.serviceIdentifier,
      current.http == record.appliedHTTP,
      current.https == record.appliedHTTPS
    else {
      return try restore(record: record, current: current)
    }
    return .applied
  }

  private func restore(
    record original: OwnershipRecord,
    current: ProxySnapshot
  ) throws -> OwnershipPhase {
    var record = original
    record.phase = .restorePending
    try persistence.save(record)
    let httpConflict =
      current.http != record.appliedHTTP
      && current.http != record.baselineHTTP
    let httpsConflict =
      current.https != record.appliedHTTPS
      && current.https != record.baselineHTTPS
    let targetHTTP =
      current.http == record.appliedHTTP
      ? record.baselineHTTP : current.http
    let targetHTTPS =
      current.https == record.appliedHTTPS
      ? record.baselineHTTPS : current.https
    let resulting = try configuration.replaceTuples(
      expected: current,
      http: targetHTTP,
      https: targetHTTPS,
      requirePrimaryService: false
    )
    guard resulting.http == targetHTTP, resulting.https == targetHTTPS else {
      record.phase = .recoveryRequired
      try persistence.save(record)
      throw ProxyOwnershipFailure.recoveryRequired
    }
    if httpConflict || httpsConflict {
      record.phase = .recoveryRequired
      try persistence.save(record)
      return .recoveryRequired
    }
    try persistence.remove()
    return .idle
  }

  private func appliedTuple(port: UInt16) -> ProxyTuple {
    return ProxyTuple(
      enabled: .integer(1),
      host: .string(loopbackHost),
      port: .integer(Int64(port))
    )
  }

  private func matchingApplyRecord(
    sessionIdentifier: Data,
    requestIdentifier: Data,
    canonicalInputDigest: Data
  ) throws -> OwnershipRecord? {
    guard sessionIdentifier.count == WireLimits.identifierBytes,
      sessionIdentifier.contains(where: { $0 != 0 }),
      requestIdentifier.count == WireLimits.identifierBytes,
      requestIdentifier.contains(where: { $0 != 0 }),
      canonicalInputDigest.count == 32
    else {
      throw ProxyOwnershipFailure.invalidInput
    }
    guard let existing = try persistence.load() else {
      return nil
    }
    guard existing.hasValidBounds else {
      throw ProxyOwnershipFailure.recoveryRequired
    }
    guard existing.sessionIdentifier == sessionIdentifier,
      existing.requestIdentifier == requestIdentifier,
      existing.canonicalInputDigest == canonicalInputDigest
    else {
      throw ProxyOwnershipFailure.conflict
    }
    return existing
  }
}
