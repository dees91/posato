import Foundation
import PosatoMacOSServiceCore

struct ServiceRecoveryOperations {
  let remainingMilliseconds: () throws -> UInt32
  let currentServiceState: () -> ServiceState
  let invalidateDaemon: () -> Void
  let register: () throws -> Void
  let connectDaemon: () throws -> Void
  let performOriginalRequest: (UInt32) throws -> WireResponsePayload
}

func setupDaemonUnavailableResponse(
  requestOperation: WireOperation,
  reconcilePayload: WireReconcilePayload?
) -> WireResponsePayload? {
  switch WireLifecyclePolicy.effectiveOperation(
    requestOperation: requestOperation,
    reconcilePayload: reconcilePayload
  ) {
  case .status, .enable:
    return WireLifecyclePolicy.unreconciledServiceResponse(
      serviceState: .recoveryRequired
    )
  default:
    return nil
  }
}

func performDaemonLifecycleRequest(
  request: WireMessage,
  receivedAt: DispatchTime,
  daemonRequirement: String,
  reconcilePayload: WireReconcilePayload?,
  daemon: inout DaemonConnection?
) throws -> WireMessage {
  do {
    return try forwardDaemonLifecycleRequest(
      request: request,
      receivedAt: receivedAt,
      daemonRequirement: daemonRequirement,
      daemon: &daemon
    )
  } catch {
    guard
      let payload = setupDaemonUnavailableResponse(
        requestOperation: request.operation,
        reconcilePayload: reconcilePayload
      )
    else {
      throw error
    }
    daemon?.invalidate()
    daemon = nil
    return try localResponse(request: request, payload: payload)
  }
}

func forwardDaemonLifecycleRequest(
  request: WireMessage,
  receivedAt: DispatchTime,
  daemonRequirement: String,
  daemon: inout DaemonConnection?
) throws -> WireMessage {
  if daemon == nil {
    daemon = try DaemonConnection(requirement: daemonRequirement)
  }
  guard let connection = daemon else {
    throw PipeFailure.unavailable
  }
  var forwardedPayload = request.payload
  var authorizationGrant: ApplyAuthorizationGrant?
  defer {
    forwardedPayload.resetBytes(
      in: forwardedPayload.startIndex..<forwardedPayload.endIndex
    )
  }
  if request.operation == .apply {
    let grant = try AuthorizationPolicy.acquireApplyGrant()
    authorizationGrant = grant
    forwardedPayload.append(grant.externalForm)
  }
  var forwarded = try WireMessage(
    kind: request.kind,
    operation: request.operation,
    sequence: request.sequence,
    deadlineMilliseconds: try remainingDeadline(
      receivedAt: receivedAt,
      budgetMilliseconds: request.deadlineMilliseconds
    ),
    connectionIdentifier: request.connectionIdentifier,
    sessionIdentifier: request.sessionIdentifier,
    requestIdentifier: request.requestIdentifier,
    payload: forwardedPayload
  )
  defer {
    forwarded.payload.resetBytes(
      in: forwarded.payload.startIndex..<forwarded.payload.endIndex
    )
  }
  return try withExtendedLifetime(authorizationGrant) {
    try connection.perform(forwarded)
  }
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
