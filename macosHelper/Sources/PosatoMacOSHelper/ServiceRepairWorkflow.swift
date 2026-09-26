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
  case .prepareGrant, .grant, .revokeGrant:
    return grantUnavailableResponse(serviceState: .recoveryRequired, failure: .unavailable)
  default:
    return nil
  }
}

/// Grant operations never own proxy state, so a failure to reach the daemon or a declined prompt is
/// answered locally instead of ending the helper; the application settles the switch from Status.
func grantUnavailableResponse(
  serviceState: ServiceState,
  failure: FailureCategory
) -> WireResponsePayload {
  return WireResponsePayload(
    outcome: .failure,
    serviceState: serviceState,
    ownershipPhase: .idle,
    failure: failure
  )
}

let standingGrantPrompt =
  "Allow Posato to start and resume sessions on this Mac without an administrator password."

func recoveredSetupPayload(
  requestOperation: WireOperation,
  reconcilePayload: WireReconcilePayload?,
  error: Error
) -> WireResponsePayload? {
  // Only a daemon that never received the request can be answered locally. A request whose outcome
  // is unknown stays unknown so the client reconciles the original identity instead of replacing it.
  guard case PipeFailure.unavailable = error else {
    return nil
  }
  return setupDaemonUnavailableResponse(
    requestOperation: requestOperation,
    reconcilePayload: reconcilePayload
  )
}

/// Enable answers with the re-read status. A registration that threw and did not move the status
/// off not-registered cannot be repeated into a different outcome, so it is reported as a failure
/// instead of an Enable button that silently does nothing.
func enableOutcomePayload(
  serviceState state: ServiceState,
  registrationFailed: Bool
) -> WireResponsePayload {
  guard registrationFailed, state == .notRegistered else {
    return WireLifecyclePolicy.unreconciledServiceResponse(serviceState: state)
  }
  return WireLifecyclePolicy.failedEnableResponse(serviceState: state)
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
      let payload = recoveredSetupPayload(
        requestOperation: request.operation,
        reconcilePayload: reconcilePayload,
        error: error
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
  switch try forwardedAuthorization(for: request.operation) {
  case .none:
    break
  case .declined:
    return try localResponse(
      request: request,
      payload: grantUnavailableResponse(serviceState: .ready, failure: .cancelled)
    )
  case .granted(let grant):
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

enum ForwardedAuthorization {
  case none
  case declined
  case granted(ApplyAuthorizationGrant)
}

/// The one-use Apply form is obtained immediately before its Apply. The opt-in form carries its own
/// prompt, and declining it is an answer rather than a failure that ends the helper.
func forwardedAuthorization(for operation: WireOperation) throws -> ForwardedAuthorization {
  switch operation {
  case .apply:
    return .granted(try AuthorizationPolicy.acquireGrant(.apply))
  case .grant:
    guard
      let grant = try? AuthorizationPolicy.acquireGrant(
        .standingApply,
        prompt: standingGrantPrompt
      )
    else {
      return .declined
    }
    return .granted(grant)
  default:
    return .none
  }
}

/// Answers locally, before any prompt or forwarding, an Apply that does not target this helper's
/// own listener, and a grant request from an application whose launch could have loaded foreign
/// code.
func localAuthorizationRefusal(
  request: WireMessage,
  sessionPort: UInt16?,
  launchEnvironmentIsClean: Bool,
  serviceState: ServiceState
) -> WireResponsePayload? {
  let targetsSession = BrowserDomainRequestHandler.applyTargetsSession(
    payload: request.payload,
    sessionPort: sessionPort
  )
  if request.operation.isApply, !targetsSession {
    return BrowserDomainRequestHandler.applyWithoutSessionResponse(serviceState: serviceState)
  }
  guard request.operation == .grant || request.operation == .applyWithGrant,
    !launchEnvironmentIsClean
  else {
    return nil
  }
  return grantUnavailableResponse(serviceState: serviceState, failure: .standingGrantUnavailable)
}
