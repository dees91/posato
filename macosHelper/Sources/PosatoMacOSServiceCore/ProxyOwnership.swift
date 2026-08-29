import Foundation

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
    let unchangedPreparedBaseline =
      record.phase == .prepared
      && current.http == record.baselineHTTP
      && current.https == record.baselineHTTPS
    if unchangedPreparedBaseline {
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
