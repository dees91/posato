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
// Read once here, before the pipe queue starts; ParentLaunchEnvironment.isCleanForParent says why.
private let parentLaunchEnvironmentIsClean = ParentLaunchEnvironment.isCleanForParent()
DispatchQueue(label: "app.posato.macos.helper.pipe").async {
  let service = SMAppService.daemon(plistName: ServiceContract.daemonPlistName)
  let endpoint = DaemonEndpoint(requirement: daemonRequirement, register: service.register)
  var daemon: DaemonConnection?
  var sequenceValidator = WireSequenceValidator()
  var connectionIdentifier: Data?
  var sessionIdentifier: Data?
  var activeRequest: WireMessage?
  var leaseRenewer: LeaseRenewer?
  var domainSession: BrowserDomainSession?
  var applications = ApplicationSessionHost()
  do {
    while let encoded = try readFrame() {
      let receivedAt = DispatchTime.now()
      let request = try WireCodec.decode(
        encoded,
        maximumBytes: WireLimits.maximumFrameBytes,
        allowsHelperOnlyOperations: true,
        maximumDeadlineMilliseconds: WireLimits.maximumSelectionDeadlineMilliseconds
      )
      // ADR 0004 bounds a connection at 256 operations, so the parent opens a new one with a fresh
      // Hello in the same session; the process keeps what it holds, including the owning connection.
      if request.kind == .hello, connectionIdentifier != nil {
        guard request.sessionIdentifier == sessionIdentifier else {
          throw PipeFailure.invalidFrame
        }
        sequenceValidator = WireSequenceValidator()
      }
      guard sequenceValidator.accept(request.sequence) else {
        throw PipeFailure.invalidFrame
      }
      if request.kind == .hello {
        connectionIdentifier = try welcomeParentConnection(hello: request)
        sessionIdentifier = request.sessionIdentifier
        continue
      }
      guard connectionIdentifier != nil else {
        throw PipeFailure.invalidFrame
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
      case .status:
        guard request.payload.isEmpty || request.payload == WireStatusRequest.includeGrantState
        else {
          throw PipeFailure.invalidFrame
        }
      case .enable, .repair, .restore, .disable, .remove, .selectApplications, .prepareGrant,
        .grant,
        .revokeGrant:
        guard request.payload.isEmpty else {
          throw PipeFailure.invalidFrame
        }
      case .apply, .applyWithGrant:
        guard request.payload.count == 2 else {
          throw PipeFailure.invalidFrame
        }
      case .configureBrowserDomains, .configureApplications:
        guard !request.payload.isEmpty else {
          throw PipeFailure.invalidFrame
        }
      case .reconcile:
        break
      case .none, .renew:
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
      if let refusal = localAuthorizationRefusal(
        request: request,
        sessionPort: domainSession?.port,
        launchEnvironmentIsClean: parentLaunchEnvironmentIsClean,
        serviceState: serviceState(service.status)
      ) {
        try writeFrame(WireCodec.encode(localResponse(request: request, payload: refusal)))
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
        case .prepareGrant where service.status != .enabled,
          .grant where service.status != .enabled,
          .revokeGrant where service.status != .enabled:
          response = try localResponse(
            request: request,
            payload: grantUnavailableResponse(
              serviceState: serviceState(service.status),
              failure: .unavailable
            )
          )
        case .enable where activeRequest != nil:
          // A failed Enable replaces its connection; the one that owns the pause must stay.
          response = try withConnectionOfItsOwn { enabler in
            try performEnableRequest(
              request: request,
              receivedAt: receivedAt,
              service: service,
              endpoint: endpoint,
              daemon: &enabler
            )
          }
        case .enable:
          response = try performEnableRequest(
            request: request,
            receivedAt: receivedAt,
            service: service,
            endpoint: endpoint,
            daemon: &daemon
          )
        case .status where activeRequest != nil,
          .prepareGrant where activeRequest != nil,
          .grant where activeRequest != nil,
          .revokeGrant where activeRequest != nil:
          // Reads and grant changes during a pause use a connection of their own, as with no pause,
          // so the owning connection keeps its operations for applying and restoring.
          response = try withConnectionOfItsOwn { reader in
            try performDaemonLifecycleRequest(
              request: request,
              receivedAt: receivedAt,
              endpoint: endpoint,
              reconcilePayload: reconcilePayload,
              daemon: &reader
            )
          }
        default:
          response = try performDaemonLifecycleRequest(
            request: request,
            receivedAt: receivedAt,
            endpoint: endpoint,
            reconcilePayload: reconcilePayload,
            daemon: &daemon
          )
        }
      }
      var responsePayload = try WireResponsePayload.decodeStatus(response.payload).response
      let applyNeedsRestore =
        WireLifecyclePolicy.needsEffectiveChainCheck(
          requestOperation: request.operation,
          response: responsePayload
        )
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
      if let restore = try? restoreMessage(
        after: request,
        deadlineMilliseconds: WireLimits.fallbackRestoreDeadlineMilliseconds,
        requestIdentifier: Data(repeating: 0, count: WireLimits.identifierBytes)
      ) {
        _ = try? daemon.perform(restore)
      }
    }
    exit(EXIT_FAILURE)
  }
  exit(EXIT_SUCCESS)
}
RunLoop.main.add(NSMachPort(), forMode: .common)
RunLoop.main.run()
