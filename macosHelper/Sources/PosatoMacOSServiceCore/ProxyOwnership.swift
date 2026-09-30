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
    if let record = try matchingApplyRecord(
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: requestIdentifier,
      canonicalInputDigest: canonicalInputDigest
    ) {
      return try reconcileApply(record: record)
    }
    let serviceIdentifier = try configuration.currentPrimaryServiceIdentifier()
    let baseline = try configuration.snapshot(serviceIdentifier: serviceIdentifier)
    guard !baseline.hasEnabledProxy, let owning = OwnedExceptions.applying(to: baseline.exceptions)
    else {
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
      appliedHTTPS: applied,
      exceptions: owning.owned
    )
    try persistence.save(record)
    let resulting = try configuration.replaceTuples(
      expected: baseline,
      http: applied,
      https: applied,
      exceptions: owning.target,
      requirePrimaryService: true
    )
    guard resulting.http == applied, resulting.https == applied,
      owning.owned.isApplied(resulting.exceptions)
    else {
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
  ) throws -> Bool {
    return try matchingApplyRecord(
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: requestIdentifier,
      canonicalInputDigest: canonicalInputDigest
    ) != nil
  }

  public func reconcile() throws -> OwnershipPhase {
    guard let record = try persistence.load() else {
      return .idle
    }
    guard record.hasValidBounds else {
      throw ProxyOwnershipFailure.recoveryRequired
    }
    guard let current = try recordedServiceSnapshot(record: record) else {
      return try releaseRemovedService()
    }
    let unchangedPreparedBaseline =
      record.phase == .prepared
      && current.http == record.baselineHTTP
      && current.https == record.baselineHTTPS
      && record.exceptions?.isBaseline(current.exceptions) ?? true
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
    guard
      let record = try matchingApplyRecord(
        sessionIdentifier: sessionIdentifier,
        requestIdentifier: requestIdentifier,
        canonicalInputDigest: canonicalInputDigest
      )
    else {
      return .idle
    }
    return try reconcileApply(record: record)
  }

  public func restore() throws -> OwnershipPhase {
    guard let record = try persistence.load() else {
      return .idle
    }
    guard record.hasValidBounds else {
      throw ProxyOwnershipFailure.recoveryRequired
    }
    guard let current = try recordedServiceSnapshot(record: record) else {
      return try releaseRemovedService()
    }
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
    guard let current = try recordedServiceSnapshot(record: record) else {
      return try releaseRemovedService()
    }
    guard primaryService == record.serviceIdentifier,
      current.http == record.appliedHTTP,
      current.https == record.appliedHTTPS,
      record.exceptions?.isApplied(current.exceptions) ?? true
    else {
      return try restore(record: record, current: current)
    }
    return .applied
  }

  private func reconcileApply(record: OwnershipRecord) throws -> OwnershipPhase {
    if record.phase == .applied {
      return try maintain(record: record)
    }
    return try reconcile()
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

extension ProxyOwnershipEngine {
  /// The recorded service's current tuples, or nil once that service is confirmed gone. A removed
  /// service holds no Posato-owned tuple, so there is nothing to restore and no other service is
  /// touched in its place (ADR 0004).
  private func recordedServiceSnapshot(record: OwnershipRecord) throws -> ProxySnapshot? {
    do {
      return try configuration.snapshot(serviceIdentifier: record.serviceIdentifier)
    } catch {
      guard
        (try? configuration.serviceIsConfirmedAbsent(serviceIdentifier: record.serviceIdentifier))
          == true
      else {
        throw error
      }
      return nil
    }
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
    let exceptions = Self.exceptionsRestore(record: record, current: current.exceptions)
    let resulting = try configuration.replaceTuples(
      expected: current,
      http: targetHTTP,
      https: targetHTTPS,
      exceptions: exceptions.target,
      requirePrimaryService: false
    )
    guard resulting.http == targetHTTP, resulting.https == targetHTTPS,
      exceptions.reached(resulting.exceptions)
    else {
      record.phase = .recoveryRequired
      try persistence.save(record)
      throw ProxyOwnershipFailure.recoveryRequired
    }
    if httpConflict || httpsConflict || exceptions.conflict {
      record.phase = .recoveryRequired
      try persistence.save(record)
      return .recoveryRequired
    }
    try persistence.remove()
    return .idle
  }

  /// Restores an applied list to its baseline, keeps a baseline list, and leaves any other list untouched as a
  /// conflict (ADR 0004, MACOS-024 amendment D1). A version 1 record owns no exceptions change.
  private static func exceptionsRestore(
    record: OwnershipRecord,
    current: ProxyExceptions
  ) -> ExceptionsRestore {
    guard let owned = record.exceptions, !owned.isBaseline(current) else {
      return ExceptionsRestore(target: .untouched, conflict: false) { $0 == current }
    }
    guard let target = owned.restoreTarget(from: current) else {
      return ExceptionsRestore(target: .untouched, conflict: true) { $0 == current }
    }
    return ExceptionsRestore(target: target, conflict: false, reached: owned.isBaseline)
  }

  private func releaseRemovedService() throws -> OwnershipPhase {
    try persistence.remove()
    return .idle
  }
}

private struct ExceptionsRestore {
  let target: ProxyExceptionsTarget
  let conflict: Bool
  let reached: (ProxyExceptions) -> Bool
}
