import Foundation
import PosatoMacOSServiceCore
import ServiceManagement

final class ServiceTransitionResult: @unchecked Sendable {
  private let lock = NSLock()
  private var storedError: (any Error)?

  func store(_ error: (any Error)?) {
    lock.withLock {
      storedError = error
    }
  }

  func error() -> (any Error)? {
    return lock.withLock { storedError }
  }
}

func awaitUnregistration(
  service: SMAppService,
  timeoutMilliseconds: UInt32
) throws {
  let semaphore = DispatchSemaphore(value: 0)
  let result = ServiceTransitionResult()
  service.unregister { error in
    result.store(error)
    semaphore.signal()
  }
  let timeout = DispatchTime.now() + .milliseconds(Int(timeoutMilliseconds))
  guard semaphore.wait(timeout: timeout) == .success else {
    throw PipeFailure.unavailable
  }
  if let error = result.error() {
    throw error
  }
}

struct ServiceRepairOperations {
  let remainingMilliseconds: () throws -> UInt32
  let currentServiceState: () -> ServiceState
  let restoreExistingDaemon: (UInt32) throws -> WireResponsePayload
  let ownershipRestored: () -> Void
  let invalidateDaemon: () -> Void
  let unregister: (UInt32) throws -> Void
  let register: () throws -> Void
  let connectFreshDaemon: () throws -> Void
  let performOriginalRequest: (UInt32) throws -> WireResponsePayload
}

func isServiceRepair(
  requestOperation: WireOperation,
  reconcilePayload: WireReconcilePayload?
) -> Bool {
  return WireLifecyclePolicy.effectiveOperation(
    requestOperation: requestOperation,
    reconcilePayload: reconcilePayload
  ) == .repair
}

func performServiceRepair(
  operations: ServiceRepairOperations
) throws -> WireResponsePayload {
  switch operations.currentServiceState() {
  case .ready:
    if let response = try prepareRegisteredServiceForRepair(operations) {
      return response
    }
  case .notRegistered, .unavailableOrIncompatible:
    operations.invalidateDaemon()
  case .approvalRequired, .recoveryRequired:
    return unreconciledResponse(operations)
  }
  return try registerAndFinishRepair(operations)
}

private func prepareRegisteredServiceForRepair(
  _ operations: ServiceRepairOperations
) throws -> WireResponsePayload? {
  do {
    let response = try operations.restoreExistingDaemon(
      operations.remainingMilliseconds()
    )
    guard cleanupCompleted(response) else {
      return incompleteRepairResponse(response)
    }
    operations.ownershipRestored()
  } catch {
    operations.invalidateDaemon()
    throw error
  }
  operations.invalidateDaemon()

  do {
    try operations.unregister(operations.remainingMilliseconds())
  } catch {
    _ = try operations.remainingMilliseconds()
    return unreconciledResponse(operations)
  }
  _ = try operations.remainingMilliseconds()
  guard operations.currentServiceState() == .notRegistered else {
    return unreconciledResponse(operations)
  }
  return nil
}

private func registerAndFinishRepair(
  _ operations: ServiceRepairOperations
) throws -> WireResponsePayload {
  _ = try operations.remainingMilliseconds()
  try operations.register()
  _ = try operations.remainingMilliseconds()
  guard operations.currentServiceState() == .ready else {
    return unreconciledResponse(operations)
  }

  do {
    try operations.connectFreshDaemon()
  } catch {
    _ = try operations.remainingMilliseconds()
    return WireLifecyclePolicy.unreconciledServiceResponse(
      serviceState: .recoveryRequired
    )
  }
  _ = try operations.remainingMilliseconds()
  return try finishRepair(operations)
}

private func finishRepair(
  _ operations: ServiceRepairOperations
) throws -> WireResponsePayload {
  let response: WireResponsePayload
  do {
    response = try operations.performOriginalRequest(
      operations.remainingMilliseconds()
    )
  } catch {
    operations.invalidateDaemon()
    throw error
  }
  guard operations.currentServiceState() == .ready else {
    return unreconciledResponse(operations)
  }
  guard cleanupCompleted(response) else {
    return incompleteRepairResponse(response)
  }
  return response
}

private func cleanupCompleted(_ response: WireResponsePayload) -> Bool {
  return WireLifecyclePolicy.completesCleanup(
    requestOperation: .repair,
    reconcilePayload: nil,
    response: response
  )
}

private func unreconciledResponse(
  _ operations: ServiceRepairOperations
) -> WireResponsePayload {
  return WireLifecyclePolicy.unreconciledServiceResponse(
    serviceState: operations.currentServiceState()
  )
}

private func incompleteRepairResponse(
  _ response: WireResponsePayload
) -> WireResponsePayload {
  guard response.outcome == .success else {
    return response
  }
  return WireLifecyclePolicy.unreconciledServiceResponse(
    serviceState: .recoveryRequired
  )
}
