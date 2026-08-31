import Foundation
import Testing

@testable import PosatoMacOSServiceCore

final class MemoryOwnershipPersistence: OwnershipPersistence, @unchecked Sendable {
  var record: OwnershipRecord?
  var failAfterSavingPhase: OwnershipPhase?

  func load() throws -> OwnershipRecord? {
    return record
  }

  func save(_ record: OwnershipRecord) throws {
    self.record = record
    if failAfterSavingPhase == record.phase {
      failAfterSavingPhase = nil
      throw ProxyOwnershipFailure.unavailable
    }
  }

  func remove() throws {
    record = nil
  }
}

@Test func givenAppliedRecordPersistedBeforeSaveFailureThenStatusRemainsApplied() throws {
  let persistence = MemoryOwnershipPersistence()
  persistence.failAfterSavingPhase = .applied
  let engine = ProxyOwnershipEngine(
    persistence: persistence,
    configuration: MemoryProxyConfiguration()
  )

  #expect(throws: ProxyOwnershipFailure.unavailable) {
    try engine.apply(
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: requestIdentifier,
      canonicalInputDigest: canonicalDigest,
      port: 17_769
    )
  }
  #expect(try engine.status() == .applied)
}

@Test func givenExactAppliedRequestWhenRepeatedOrReconciledThenAppliedStateRemains() throws {
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration()
  let engine = ProxyOwnershipEngine(
    persistence: persistence,
    configuration: configuration
  )
  #expect(
    try engine.apply(
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: requestIdentifier,
      canonicalInputDigest: canonicalDigest,
      port: 17_769
    ) == .applied
  )
  let appliedSnapshot = configuration.snapshotValue

  #expect(
    try engine.apply(
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: requestIdentifier,
      canonicalInputDigest: canonicalDigest,
      port: 17_769
    ) == .applied
  )
  #expect(
    try engine.reconcile(
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: requestIdentifier,
      canonicalInputDigest: canonicalDigest
    ) == .applied
  )
  #expect(configuration.snapshotValue == appliedSnapshot)
  #expect(persistence.record?.phase == .applied)
}

@Test func givenAbsentOrExactRecordWhenApplyOwnershipVerifiedThenOnlyExactReportsMatch() throws {
  let persistence = MemoryOwnershipPersistence()
  let engine = ProxyOwnershipEngine(
    persistence: persistence,
    configuration: MemoryProxyConfiguration()
  )

  #expect(
    try !engine.verifyApplyOwnership(
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: requestIdentifier,
      canonicalInputDigest: canonicalDigest
    )
  )
  _ = try engine.apply(
    sessionIdentifier: sessionIdentifier,
    requestIdentifier: requestIdentifier,
    canonicalInputDigest: canonicalDigest,
    port: 17_769
  )
  #expect(
    try engine.verifyApplyOwnership(
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: requestIdentifier,
      canonicalInputDigest: canonicalDigest
    )
  )
}

final class MemoryProxyConfiguration: ProxyConfigurationAccess, @unchecked Sendable {
  let serviceIdentifier = "synthetic-service"
  var primaryServiceIdentifier = "synthetic-service"
  var snapshotValue: ProxySnapshot

  init(
    http: ProxyTuple = emptyTuple,
    https: ProxyTuple = emptyTuple,
    additionalProxyEnabled: Bool = false
  ) {
    snapshotValue = ProxySnapshot(
      serviceIdentifier: serviceIdentifier,
      http: http,
      https: https,
      additionalProxyEnabled: additionalProxyEnabled
    )
  }

  func currentPrimaryServiceIdentifier() throws -> String {
    return primaryServiceIdentifier
  }

  func snapshot(serviceIdentifier: String) throws -> ProxySnapshot {
    guard serviceIdentifier == self.serviceIdentifier else {
      throw ProxyOwnershipFailure.unavailable
    }
    return snapshotValue
  }

  func replaceTuples(
    expected: ProxySnapshot,
    http: ProxyTuple,
    https: ProxyTuple,
    requirePrimaryService: Bool
  ) throws -> ProxySnapshot {
    guard expected == snapshotValue else {
      throw ProxyOwnershipFailure.conflict
    }
    snapshotValue = ProxySnapshot(
      serviceIdentifier: serviceIdentifier,
      http: http,
      https: https,
      additionalProxyEnabled: snapshotValue.additionalProxyEnabled
    )
    return snapshotValue
  }
}

@Test func givenPrimaryServiceChangeWhenMaintainedThenRecordedServiceIsRestored() throws {
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration()
  let engine = ProxyOwnershipEngine(
    persistence: persistence,
    configuration: configuration
  )
  _ = try engine.apply(
    sessionIdentifier: sessionIdentifier,
    requestIdentifier: requestIdentifier,
    canonicalInputDigest: canonicalDigest,
    port: 17_769
  )
  configuration.primaryServiceIdentifier = "replacement-service"

  #expect(try engine.maintain() == .idle)
  #expect(configuration.snapshotValue.http == emptyTuple)
  #expect(configuration.snapshotValue.https == emptyTuple)
  #expect(persistence.record == nil)
}

@Test func givenDifferentOwnerWhenRenewedThenItConflictsWithoutChangingOwnership() throws {
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration()
  let engine = ProxyOwnershipEngine(
    persistence: persistence,
    configuration: configuration
  )
  _ = try engine.apply(
    sessionIdentifier: sessionIdentifier,
    requestIdentifier: requestIdentifier,
    canonicalInputDigest: canonicalDigest,
    port: 17_769
  )

  #expect(throws: ProxyOwnershipFailure.conflict) {
    try engine.maintain(
      sessionIdentifier: Data(repeating: 9, count: WireLimits.identifierBytes),
      requestIdentifier: requestIdentifier
    )
  }
  #expect(throws: ProxyOwnershipFailure.conflict) {
    try engine.maintain(
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: Data(repeating: 9, count: WireLimits.identifierBytes)
    )
  }
  #expect(persistence.record?.phase == .applied)
}

let emptyTuple = ProxyTuple(enabled: nil, host: nil, port: nil)
private let sessionIdentifier = Data(repeating: 1, count: 16)
private let requestIdentifier = Data(repeating: 2, count: 16)
private let canonicalDigest = Data(repeating: 3, count: 32)

@Test func givenIdleStateWhenAppliedAndRestoredThenBaselineAndUnrelatedValuesReturn() throws {
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration()
  let engine = ProxyOwnershipEngine(
    persistence: persistence,
    configuration: configuration
  )

  #expect(
    try engine.apply(
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: requestIdentifier,
      canonicalInputDigest: canonicalDigest,
      port: 17_769
    ) == .applied
  )
  #expect(try engine.restore() == .idle)
  #expect(configuration.snapshotValue.http == emptyTuple)
  #expect(configuration.snapshotValue.https == emptyTuple)
  #expect(persistence.record == nil)
}

@Test func givenPreparedStateWithoutMutationWhenReconciledThenStateClears() throws {
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration()
  let applied = ProxyTuple(
    enabled: .integer(1),
    host: .string("127.0.0.1"),
    port: .integer(17_769)
  )
  persistence.record = OwnershipRecord(
    sessionIdentifier: sessionIdentifier,
    requestIdentifier: requestIdentifier,
    canonicalInputDigest: canonicalDigest,
    serviceIdentifier: configuration.serviceIdentifier,
    phase: .prepared,
    baselineHTTP: emptyTuple,
    baselineHTTPS: emptyTuple,
    appliedHTTP: applied,
    appliedHTTPS: applied
  )
  let engine = ProxyOwnershipEngine(
    persistence: persistence,
    configuration: configuration
  )

  #expect(try engine.reconcile() == .idle)
  #expect(persistence.record == nil)
}

@Test func givenAnotherPreparedOwnerWhenApplyPreflightRunsThenRecordRemainsUnchanged() throws {
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration()
  let applied = ProxyTuple(
    enabled: .integer(1),
    host: .string("127.0.0.1"),
    port: .integer(17_769)
  )
  let original = OwnershipRecord(
    sessionIdentifier: sessionIdentifier,
    requestIdentifier: requestIdentifier,
    canonicalInputDigest: canonicalDigest,
    serviceIdentifier: configuration.serviceIdentifier,
    phase: .prepared,
    baselineHTTP: emptyTuple,
    baselineHTTPS: emptyTuple,
    appliedHTTP: applied,
    appliedHTTPS: applied
  )
  persistence.record = original
  let engine = ProxyOwnershipEngine(
    persistence: persistence,
    configuration: configuration
  )

  #expect(throws: ProxyOwnershipFailure.conflict) {
    _ = try engine.verifyApplyOwnership(
      sessionIdentifier: Data(repeating: 9, count: WireLimits.identifierBytes),
      requestIdentifier: Data(repeating: 8, count: WireLimits.identifierBytes),
      canonicalInputDigest: Data(repeating: 7, count: 32)
    )
  }
  #expect(persistence.record == original)
  #expect(configuration.snapshotValue.http == emptyTuple)
  #expect(configuration.snapshotValue.https == emptyTuple)
}

@Test func givenAppliedHTTPConflictWhenRestoredThenHTTPIsPreservedAndHTTPSReturns() throws {
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration()
  let engine = ProxyOwnershipEngine(
    persistence: persistence,
    configuration: configuration
  )
  _ = try engine.apply(
    sessionIdentifier: sessionIdentifier,
    requestIdentifier: requestIdentifier,
    canonicalInputDigest: canonicalDigest,
    port: 17_769
  )
  let externalHTTP = ProxyTuple(
    enabled: .integer(1),
    host: .string("external.invalid"),
    port: .integer(8080)
  )
  configuration.snapshotValue = ProxySnapshot(
    serviceIdentifier: configuration.serviceIdentifier,
    http: externalHTTP,
    https: configuration.snapshotValue.https,
    additionalProxyEnabled: false
  )

  #expect(try engine.restore() == .recoveryRequired)
  #expect(configuration.snapshotValue.http == externalHTTP)
  #expect(configuration.snapshotValue.https == emptyTuple)
  #expect(persistence.record?.phase == .recoveryRequired)
}

@Test func givenSameRequestWithDifferentInputWhenAppliedThenItConflicts() throws {
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration()
  let engine = ProxyOwnershipEngine(
    persistence: persistence,
    configuration: configuration
  )
  _ = try engine.apply(
    sessionIdentifier: sessionIdentifier,
    requestIdentifier: requestIdentifier,
    canonicalInputDigest: canonicalDigest,
    port: 17_769
  )

  #expect(throws: ProxyOwnershipFailure.conflict) {
    try engine.apply(
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: requestIdentifier,
      canonicalInputDigest: Data(repeating: 4, count: 32),
      port: 17_770
    )
  }
}

@Test func givenDifferentRequestWhenReconciledThenItConflicts() throws {
  let configuration = MemoryProxyConfiguration()
  let persistence = MemoryOwnershipPersistence()
  let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)
  let session = Data(repeating: 1, count: WireLimits.identifierBytes)
  let request = Data(repeating: 2, count: WireLimits.identifierBytes)
  _ = try engine.apply(
    sessionIdentifier: session,
    requestIdentifier: request,
    canonicalInputDigest: Data(repeating: 3, count: 32),
    port: 8080
  )

  #expect(throws: ProxyOwnershipFailure.conflict) {
    try engine.reconcile(
      sessionIdentifier: session,
      requestIdentifier: Data(repeating: 4, count: WireLimits.identifierBytes),
      canonicalInputDigest: Data(repeating: 3, count: 32)
    )
  }
}

@Test func givenDifferentDigestWhenReconciledThenItConflicts() throws {
  let configuration = MemoryProxyConfiguration()
  let persistence = MemoryOwnershipPersistence()
  let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)
  let session = Data(repeating: 1, count: WireLimits.identifierBytes)
  let request = Data(repeating: 2, count: WireLimits.identifierBytes)
  _ = try engine.apply(
    sessionIdentifier: session,
    requestIdentifier: request,
    canonicalInputDigest: Data(repeating: 3, count: 32),
    port: 8080
  )

  #expect(throws: ProxyOwnershipFailure.conflict) {
    try engine.reconcile(
      sessionIdentifier: session,
      requestIdentifier: request,
      canonicalInputDigest: Data(repeating: 4, count: 32)
    )
  }
}
