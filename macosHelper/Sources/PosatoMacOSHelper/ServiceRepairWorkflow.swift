import PosatoMacOSServiceCore

struct ServiceRepairOperations {
  let remainingMilliseconds: () throws -> UInt32
  let currentServiceState: () -> ServiceState
  let invalidateDaemon: () -> Void
  let register: () throws -> Void
  let connectDaemon: () throws -> Void
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
