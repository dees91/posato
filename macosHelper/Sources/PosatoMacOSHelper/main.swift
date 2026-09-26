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
private var domainSession: BrowserDomainSession?
private var applications = ApplicationSessionHost()

do {
  while let encoded = try readFrame() {
    let receivedAt = DispatchTime.now()
    let request = try WireCodec.decode(
      encoded,
      maximumBytes: WireLimits.maximumFrameBytes,
      allowsHelperOnlyOperations: true,
      maximumDeadlineMilliseconds: WireLimits.maximumSelectionDeadlineMilliseconds
    )
    guard sequenceValidator.accept(request.sequence) else {
      throw PipeFailure.invalidFrame
    }
    if connectionIdentifier == nil {
      guard request.kind == .hello, request.operation == .none,
        request.connectionIdentifier == Data(repeating: 0, count: WireLimits.identifierBytes),
        request.sessionIdentifier.contains(where: { $0 != 0 }),
        request.requestIdentifier.contains(where: { $0 != 0 }),
        try WireCapabilities.supports(
          WireCapabilities.decode(request.payload),
          required: WireLimits.requiredParentHelperCapabilities
        )
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
        payload: WireCapabilities.encode(WireLimits.requiredParentHelperCapabilities)
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
      leaseRenewer?.cancelAndWait()
      domainSession?.stop()
      domainSession = nil
      applications.stop()
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
        payload: WireResponsePayload(
          outcome: .success,
          serviceState: serviceState(service.status),
          ownershipPhase: .idle
        )
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
    case .status, .enable, .repair, .restore, .disable, .remove, .selectApplications:
      guard request.payload.isEmpty else {
        throw PipeFailure.invalidFrame
      }
    case .apply:
      guard request.payload.count == 2 else {
        throw PipeFailure.invalidFrame
      }
    case .configureBrowserDomains, .configureApplications:
      guard !request.payload.isEmpty else {
        throw PipeFailure.invalidFrame
      }
    case .reconcile:
      break
    case .none, .renew, .prepareGrant, .grant, .revokeGrant, .applyWithGrant:
      throw PipeFailure.invalidFrame
    }
    if request.operation == .configureBrowserDomains {
      domainSession = try BrowserDomainRequestHandler.dispatch(
        request: request, receivedAt: receivedAt, service: service,
        existing: domainSession, applyOwned: activeRequest != nil
      )
      continue
    }
    if try applications.dispatch(request: request, receivedAt: receivedAt, service: service) {
      continue
    }
    if request.operation == .apply, domainSession == nil {
      let refused = try localResponse(
        request: request,
        payload: BrowserDomainRequestHandler.applyWithoutSessionResponse(
          serviceState: serviceState(service.status)
        )
      )
      try writeFrame(WireCodec.encode(refused))
      continue
    }
    if request.operation == .selectApplications {
      let response = try BrowserDomainRequestHandler.handleSelectApplications(
        request: request,
        receivedAt: receivedAt
      )
      try writeFrame(WireCodec.encode(response))
      continue
    }
    let reconcilePayload =
      request.operation == .reconcile
      ? try WireReconcilePayload.decode(request.payload) : nil
    if WireLifecyclePolicy.reconcilesExistingOwnership(
      requestOperation: request.operation,
      reconcilePayload: reconcilePayload
    ) {
      leaseRenewer?.cancelAndWait()
      leaseRenewer = nil
    }
    var response: WireMessage
    if let recoveryOperation = serviceRecoveryOperation(
      requestOperation: request.operation,
      reconcilePayload: reconcilePayload
    ) {
      let recoveryPayload = try performServiceRecovery(
        operation: recoveryOperation,
        operations: ServiceRecoveryOperations(
          remainingMilliseconds: {
            try remainingDeadline(
              receivedAt: receivedAt,
              budgetMilliseconds: request.deadlineMilliseconds
            )
          },
          currentServiceState: {
            serviceState(service.status)
          },
          invalidateDaemon: {
            daemon?.invalidate()
            daemon = nil
          },
          register: {
            try service.register()
          },
          connectDaemon: {
            if daemon == nil {
              daemon = try DaemonConnection(requirement: daemonRequirement)
            }
          },
          performOriginalRequest: { deadlineMilliseconds in
            var forwardedPayload = request.payload
            defer {
              forwardedPayload.resetBytes(
                in: forwardedPayload.startIndex..<forwardedPayload.endIndex
              )
            }
            var forwarded = try WireMessage(
              kind: request.kind,
              operation: request.operation,
              sequence: request.sequence,
              deadlineMilliseconds: deadlineMilliseconds,
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
            guard let daemon else {
              throw PipeFailure.unavailable
            }
            let forwardedResponse = try daemon.perform(forwarded)
            return try WireResponsePayload.decode(forwardedResponse.payload)
          }
        )
      )
      response = try localResponse(request: request, payload: recoveryPayload)
    } else {
      switch request.operation {
      case .status where service.status != .enabled,
        .disable where service.status != .enabled,
        .remove where service.status != .enabled,
        .reconcile where service.status != .enabled:
        response = try localResponse(
          request: request,
          payload: WireLifecyclePolicy.unreconciledServiceResponse(
            serviceState: serviceState(service.status)
          )
        )
      case .enable:
        var registrationFailed = false
        if service.status != .enabled {
          do {
            try service.register()
          } catch {
            // SMAppService throws when approval is now required or the item
            // already exists; the status read below is the setup outcome. A
            // status that did not move keeps this as a registration failure.
            registrationFailed = true
          }
        }
        if service.status != .enabled {
          response = try localResponse(
            request: request,
            payload: enableOutcomePayload(
              serviceState: serviceState(service.status),
              registrationFailed: registrationFailed
            )
          )
          break
        }
        fallthrough
      default:
        response = try performDaemonLifecycleRequest(
          request: request,
          receivedAt: receivedAt,
          daemonRequirement: daemonRequirement,
          reconcilePayload: reconcilePayload,
          daemon: &daemon
        )
      }
    }
    var responsePayload = try WireResponsePayload.decode(response.payload)
    let applyNeedsRestore =
      request.operation == .apply
      && responsePayload.outcome == .success
      && domainSession?.validateEffectiveChain() != true
    if applyNeedsRestore {
      var restoredPayload: WireResponsePayload?
      if let daemon {
        let restore = try restoreMessage(
          after: request,
          deadlineMilliseconds: (try? remainingDeadline(
            receivedAt: receivedAt,
            budgetMilliseconds: request.deadlineMilliseconds
          )) ?? WireLimits.fallbackRestoreDeadlineMilliseconds
        )
        if let restored = try? daemon.perform(restore) {
          restoredPayload = try? WireResponsePayload.decode(restored.payload)
        }
      }
      domainSession?.stop()
      domainSession = nil
      responsePayload = BrowserDomainRequestHandler.effectiveChainFailureResponse(
        restored: restoredPayload
      )
      response = try localResponse(request: request, payload: responsePayload)
    }
    if WireLifecyclePolicy.ownsAppliedMutation(
      requestOperation: request.operation,
      reconcilePayload: reconcilePayload,
      ownershipVerified: true,
      response: responsePayload
    ) {
      activeRequest = request
      leaseRenewer?.cancelAndWait()
      leaseRenewer = LeaseRenewer(
        ownershipConnection: daemon!,
        daemonRequirement: daemonRequirement,
        request: request
      )
    } else if WireLifecyclePolicy.completesCleanup(
      requestOperation: request.operation,
      reconcilePayload: reconcilePayload,
      response: responsePayload
    ) {
      domainSession?.stop()
      domainSession = nil
      leaseRenewer = nil
      activeRequest = nil
      if WireLifecyclePolicy.shouldUnregister(
        requestOperation: request.operation,
        reconcilePayload: reconcilePayload,
        response: responsePayload
      ) {
        try service.unregister()
        response.payload = WireLifecyclePolicy.postUnregisterResponse(
          responsePayload,
          serviceState: serviceState(service.status)
        ).encode()
      }
    }
    if activeRequest == nil {
      daemon = nil
    }
    try writeFrame(WireCodec.encode(response))
  }
  domainSession?.stop()
  domainSession = nil
  applications.stop()
  if let request = activeRequest, let daemon {
    leaseRenewer?.cancelAndWait()
    let restore = try restoreMessage(
      after: request,
      deadlineMilliseconds: WireLimits.fallbackRestoreDeadlineMilliseconds,
      requestIdentifier: try randomIdentifier()
    )
    _ = try? daemon.perform(restore)
  }
} catch {
  domainSession?.stop()
  domainSession = nil
  applications.stop()
  if let request = activeRequest, let daemon {
    leaseRenewer?.cancelAndWait()
    let restore = try? restoreMessage(
      after: request,
      deadlineMilliseconds: WireLimits.fallbackRestoreDeadlineMilliseconds,
      requestIdentifier: Data(repeating: 0, count: WireLimits.identifierBytes)
    )
    if let restore {
      _ = try? daemon.perform(restore)
    }
  }
  exit(EXIT_FAILURE)
}
