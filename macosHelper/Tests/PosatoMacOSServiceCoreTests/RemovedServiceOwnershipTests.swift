import Foundation
import Testing

@testable import PosatoMacOSServiceCore

private let sessionIdentifier = Data(repeating: 1, count: 16)
private let requestIdentifier = Data(repeating: 2, count: 16)
private let canonicalDigest = Data(repeating: 3, count: 32)

private func recordedOwnership(
  phase: OwnershipPhase,
  configuration: MemoryProxyConfiguration
) -> OwnershipRecord {
  let applied = ProxyTuple(
    enabled: .integer(1),
    host: .string("127.0.0.1"),
    port: .integer(17_769)
  )
  return OwnershipRecord(
    sessionIdentifier: sessionIdentifier,
    requestIdentifier: requestIdentifier,
    canonicalInputDigest: canonicalDigest,
    serviceIdentifier: configuration.serviceIdentifier,
    phase: phase,
    baselineHTTP: emptyTuple,
    baselineHTTPS: emptyTuple,
    appliedHTTP: applied,
    appliedHTTPS: applied,
    exceptions: nil
  )
}

@Test(arguments: [
  OwnershipPhase.prepared, .applied, .restorePending, .recoveryRequired,
])
func givenRecordedServiceRemovedWhenRestoredOrReconciledThenOwnershipClearsAndApplyWorks(
  phase: OwnershipPhase
) throws {
  for cleanup in [
    { (engine: ProxyOwnershipEngine) in try engine.restore() },
    { (engine: ProxyOwnershipEngine) in try engine.reconcile() },
  ] {
    let persistence = MemoryOwnershipPersistence()
    let configuration = MemoryProxyConfiguration()
    persistence.record = recordedOwnership(phase: phase, configuration: configuration)
    configuration.serviceState = .absent
    let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)

    #expect(try cleanup(engine) == .idle)
    #expect(persistence.record == nil)
    #expect(try engine.status() == .idle)

    configuration.serviceState = .present
    #expect(
      try engine.apply(
        sessionIdentifier: Data(repeating: 5, count: WireLimits.identifierBytes),
        requestIdentifier: Data(repeating: 6, count: WireLimits.identifierBytes),
        canonicalInputDigest: canonicalDigest,
        port: 17_770
      ) == .applied
    )
  }
}

@Test func givenAppliedServiceRemovedWhenMaintainedThenOwnershipClears() throws {
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration()
  let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)
  _ = try engine.apply(
    sessionIdentifier: sessionIdentifier,
    requestIdentifier: requestIdentifier,
    canonicalInputDigest: canonicalDigest,
    port: 17_769
  )
  configuration.serviceState = .absent

  #expect(
    try engine.maintain(
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: requestIdentifier
    ) == .idle
  )
  #expect(persistence.record == nil)
}

@Test func givenRecordedServiceUnreadableWhenRestoredOrReconciledThenOwnershipIsKept() throws {
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration()
  let original = recordedOwnership(phase: .applied, configuration: configuration)
  persistence.record = original
  configuration.serviceState = .unreadable
  let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)

  #expect(throws: SystemProxyConfigurationFailure.protocolConfiguration) {
    try engine.restore()
  }
  #expect(throws: SystemProxyConfigurationFailure.protocolConfiguration) {
    try engine.reconcile()
  }
  #expect(throws: SystemProxyConfigurationFailure.protocolConfiguration) {
    try engine.maintain()
  }
  #expect(persistence.record == original)
  #expect(try engine.status() == .applied)
}
