import Darwin
import Foundation
import PosatoMacOSServiceCore
import ServiceManagement

guard CommandLine.arguments.count == 1,
  Bundle.main.bundleIdentifier == ServiceContract.helperIdentifier,
  let identity = try? CodeSigningRequirements.currentIdentity(
    expectedIdentifier: ServiceContract.helperIdentifier
  ),
  (try? CodeSigningRequirements.validateRunningPeer(
    processIdentifier: getppid(),
    selfIdentity: identity,
    peerIdentifier: ServiceContract.applicationIdentifier
  )) != nil,
  (try? CodeSigningRequirements.validateHelperPackageRelationship(selfIdentity: identity)) != nil,
  let daemonRequirement = try? CodeSigningRequirements.peerRequirement(
    selfIdentity: identity,
    peerIdentifier: ServiceContract.daemonIdentifier
  )
else {
  exit(EXIT_FAILURE)
}

private let service = SMAppService.daemon(plistName: ServiceContract.daemonPlistName)
private var daemon: DaemonConnection?
private var sequenceValidator = WireSequenceValidator()
private var connectionIdentifier: Data?
private var sessionIdentifier: Data?
private var activeRequest: WireMessage?
private var leaseRenewer: LeaseRenewer?

do {
  while let encoded = try readFrame() {
    let receivedAt = DispatchTime.now()
    let request = try WireCodec.decode(encoded, maximumBytes: WireLimits.maximumFrameBytes)
    guard sequenceValidator.accept(request.sequence) else {
      throw PipeFailure.invalidFrame
    }
    if connectionIdentifier == nil {
      guard request.kind == .hello, request.operation == .none,
        request.connectionIdentifier == Data(repeating: 0, count: WireLimits.identifierBytes),
        request.sessionIdentifier.contains(where: { $0 != 0 }),
        request.requestIdentifier.contains(where: { $0 != 0 }),
        try WireCapabilities.supportsRequired(WireCapabilities.decode(request.payload))
      else {
        throw PipeFailure.invalidFrame
      }
      let identifier = try randomIdentifier()
      connectionIdentifier = identifier
      sessionIdentifier = request.sessionIdentifier
      let welcome = try WireMessage(
        kind: .welcome,
        operation: .none,
        sequence: request.sequence,
        deadlineMilliseconds: 0,
        connectionIdentifier: identifier,
        sessionIdentifier: request.sessionIdentifier,
        requestIdentifier: request.requestIdentifier,
        payload: WireCapabilities.encode(WireLimits.requiredCapabilities)
      )
      try writeFrame(WireCodec.encode(welcome))
      continue
    }
    if request.kind == .cancel {
      guard request.operation == .none, request.payload.isEmpty,
        request.connectionIdentifier == connectionIdentifier,
        request.sessionIdentifier == sessionIdentifier,
        request.deadlineMilliseconds > 0,
        activeRequest == nil || activeRequest?.requestIdentifier == request.requestIdentifier
      else {
        throw PipeFailure.invalidFrame
      }
      leaseRenewer?.cancel()
      if let activeRequest, let daemon {
        let restore = try WireMessage(
          kind: .request,
          operation: .restore,
          sequence: request.sequence,
          deadlineMilliseconds: try remainingDeadline(
            receivedAt: receivedAt,
            budgetMilliseconds: request.deadlineMilliseconds
          ),
          connectionIdentifier: request.connectionIdentifier,
          sessionIdentifier: request.sessionIdentifier,
          requestIdentifier: activeRequest.requestIdentifier,
          payload: Data()
        )
        _ = try daemon.perform(restore)
      }
      let cancelled = try localResponse(
        request: request,
        outcome: .success,
        state: serviceState(service.status)
      )
      try writeFrame(WireCodec.encode(cancelled))
      break
    }
    guard request.kind == .request,
      request.connectionIdentifier == connectionIdentifier,
      request.connectionIdentifier.contains(where: { $0 != 0 }),
      request.sessionIdentifier == sessionIdentifier,
      request.requestIdentifier.contains(where: { $0 != 0 }),
      request.deadlineMilliseconds > 0
    else {
      throw PipeFailure.invalidFrame
    }
    switch request.operation {
    case .status, .enable, .repair, .restore, .disable, .remove:
      guard request.payload.isEmpty else {
        throw PipeFailure.invalidFrame
      }
    case .apply:
      guard request.payload.count == 2 else {
        throw PipeFailure.invalidFrame
      }
    case .reconcile:
      break
    case .none, .renew:
      throw PipeFailure.invalidFrame
    }
    let reconcilePayload =
      request.operation == .reconcile
      ? try WireReconcilePayload.decode(request.payload) : nil
    let response: WireMessage
    switch request.operation {
    case .status where service.status != .enabled:
      response = try localResponse(
        request: request,
        outcome: .success,
        state: serviceState(service.status)
      )
    case .disable where service.status != .enabled:
      response = try localResponse(
        request: request,
        outcome: .actionRequired,
        state: serviceState(service.status),
        action: service.status == .requiresApproval ? .backgroundApproval : .manualRecovery,
        failure: .lifecycle
      )
    case .remove where service.status != .enabled:
      response = try localResponse(
        request: request,
        outcome: .actionRequired,
        state: serviceState(service.status),
        action: service.status == .requiresApproval ? .backgroundApproval : .manualRecovery,
        failure: .lifecycle
      )
    case .reconcile where service.status != .enabled:
      response = try localResponse(
        request: request,
        outcome: .actionRequired,
        state: serviceState(service.status),
        action: service.status == .requiresApproval ? .backgroundApproval : .manualRecovery,
        failure: .lifecycle
      )
    case .enable:
      if service.status != .enabled {
        try service.register()
      }
      if service.status != .enabled {
        let state = serviceState(service.status)
        response = try localResponse(
          request: request,
          outcome: .actionRequired,
          state: state,
          action: state == .approvalRequired ? .backgroundApproval : .none
        )
        break
      }
      fallthrough
    default:
      if daemon == nil {
        daemon = try DaemonConnection(requirement: daemonRequirement)
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
      response = try withExtendedLifetime(authorizationGrant) {
        try daemon!.perform(forwarded)
      }
      let responsePayload = try WireResponsePayload.decode(response.payload)
      if WireLifecyclePolicy.ownsAppliedMutation(
        requestOperation: request.operation,
        reconcilePayload: reconcilePayload,
        ownershipVerified: true,
        response: responsePayload
      ) {
        activeRequest = request
        leaseRenewer?.cancel()
        leaseRenewer = LeaseRenewer(connection: daemon!, request: request)
      } else if WireLifecyclePolicy.completesCleanup(
        requestOperation: request.operation,
        reconcilePayload: reconcilePayload,
        response: responsePayload
      ) {
        leaseRenewer?.cancel()
        leaseRenewer = nil
        activeRequest = nil
        if WireLifecyclePolicy.shouldUnregister(
          requestOperation: request.operation,
          reconcilePayload: reconcilePayload,
          response: responsePayload
        ) {
          try service.unregister()
        }
      }
      if activeRequest == nil {
        daemon = nil
      }
    }
    try writeFrame(WireCodec.encode(response))
  }
  if let request = activeRequest, let daemon {
    leaseRenewer?.cancel()
    let restore = try WireMessage(
      kind: .request,
      operation: .restore,
      sequence: request.sequence,
      deadlineMilliseconds: 5_000,
      connectionIdentifier: request.connectionIdentifier,
      sessionIdentifier: request.sessionIdentifier,
      requestIdentifier: try randomIdentifier(),
      payload: Data()
    )
    _ = try? daemon.perform(restore)
  }
} catch {
  if let request = activeRequest, let daemon {
    leaseRenewer?.cancel()
    let restore = try? WireMessage(
      kind: .request,
      operation: .restore,
      sequence: request.sequence,
      deadlineMilliseconds: 5_000,
      connectionIdentifier: request.connectionIdentifier,
      sessionIdentifier: request.sessionIdentifier,
      requestIdentifier: Data(repeating: 0, count: WireLimits.identifierBytes),
      payload: Data()
    )
    if let restore {
      _ = try? daemon.perform(restore)
    }
  }
  exit(EXIT_FAILURE)
}
