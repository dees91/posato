import Foundation

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

enum MemoryServiceState {
  case present
  case absent
  case unreadable
}

final class MemoryProxyConfiguration: ProxyConfigurationAccess, @unchecked Sendable {
  let serviceIdentifier = "synthetic-service"
  var primaryServiceIdentifier = "synthetic-service"
  var serviceState = MemoryServiceState.present
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
    guard serviceState == .present else {
      throw SystemProxyConfigurationFailure.protocolConfiguration
    }
    return snapshotValue
  }

  func serviceIsConfirmedAbsent(serviceIdentifier: String) throws -> Bool {
    return serviceIdentifier == self.serviceIdentifier && serviceState == .absent
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
