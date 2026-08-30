import PosatoMacOSServiceCore

struct ServiceRecoveryOperations {
  let remainingMilliseconds: () throws -> UInt32
  let currentServiceState: () -> ServiceState
  let invalidateDaemon: () -> Void
  let register: () throws -> Void
  let connectDaemon: () throws -> Void
  let performOriginalRequest: (UInt32) throws -> WireResponsePayload
}

func serviceRecoveryOperation(
  requestOperation: WireOperation,
  reconcilePayload: WireReconcilePayload?
) -> WireOperation? {
  let operation = WireLifecyclePolicy.effectiveOperation(
    requestOperation: requestOperation,
    reconcilePayload: reconcilePayload
  )
  switch operation {
  case .repair:
    return operation
  case .disable where requestOperation == .reconcile,
    .remove where requestOperation == .reconcile:
    return operation
  default:
    return nil
  }
}

func performServiceRecovery(
  operation: WireOperation,
  operations: ServiceRecoveryOperations
) throws -> WireResponsePayload {
  switch operations.currentServiceState() {
  case .ready:
    break
  case .notRegistered, .unavailableOrIncompatible:
    operations.invalidateDaemon()
    _ = try operations.remainingMilliseconds()
    try operations.register()
    _ = try operations.remainingMilliseconds()
    guard operations.currentServiceState() == .ready else {
      return unreconciledResponse(operations)
    }
  case .approvalRequired, .recoveryRequired:
    return unreconciledResponse(operations)
  }

  do {
    try operations.connectDaemon()
  } catch {
    _ = try operations.remainingMilliseconds()
    return WireLifecyclePolicy.unreconciledServiceResponse(
      serviceState: .recoveryRequired
    )
  }
  return try finishServiceRecovery(operation: operation, operations: operations)
}

private func finishServiceRecovery(
  operation: WireOperation,
  operations: ServiceRecoveryOperations
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
  guard cleanupCompleted(operation: operation, response: response) else {
    return incompleteRecoveryResponse(response)
  }
  return response
}

private func cleanupCompleted(
  operation: WireOperation,
  response: WireResponsePayload
) -> Bool {
  return WireLifecyclePolicy.completesCleanup(
    requestOperation: operation,
    reconcilePayload: nil,
    response: response
  )
}

private func unreconciledResponse(
  _ operations: ServiceRecoveryOperations
) -> WireResponsePayload {
  return WireLifecyclePolicy.unreconciledServiceResponse(
    serviceState: operations.currentServiceState()
  )
}

private func incompleteRecoveryResponse(
  _ response: WireResponsePayload
) -> WireResponsePayload {
  guard response.outcome == .success else {
    return response
  }
  return WireLifecyclePolicy.unreconciledServiceResponse(
    serviceState: .recoveryRequired
  )
}
