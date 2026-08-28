import Foundation
import PosatoMacOSServiceCore

private final class ReplyBox: @unchecked Sendable {
  private let reply: (Data?) -> Void

  init(_ reply: @escaping (Data?) -> Void) {
    self.reply = reply
  }

  func callAsFunction(_ data: Data?) {
    reply(data)
  }
}

private final class ConnectionState: @unchecked Sendable {
  private let lock = NSLock()
  private var ownsAppliedMutation = false

  func update(
    request: WireMessage,
    response: WireResponsePayload,
    ownershipVerified: Bool
  ) {
    let reconcilePayload =
      request.operation == .reconcile
      ? try? WireReconcilePayload.decode(request.payload) : nil
    lock.withLock {
      if WireLifecyclePolicy.ownsAppliedMutation(
        requestOperation: request.operation,
        reconcilePayload: reconcilePayload,
        ownershipVerified: ownershipVerified,
        response: response
      ) {
        ownsAppliedMutation = true
      } else if WireLifecyclePolicy.completesCleanup(
        requestOperation: request.operation,
        reconcilePayload: reconcilePayload,
        response: response
      ) {
        ownsAppliedMutation = false
      }
    }
  }

  func shouldRestoreOnInvalidation() -> Bool {
    return lock.withLock { ownsAppliedMutation }
  }
}

private final class RequestCoordinator: @unchecked Sendable {
  private let queue = DispatchQueue(label: "app.posato.macos.proxy-settings.requests")
  private let engine = ProxyOwnershipEngine(
    persistence: DurableOwnershipStore(),
    configuration: SystemProxyConfiguration()
  )
  private var leaseDeadline: DispatchTime?
  private var activeConnections = 0

  func start() {
    queue.async {
      _ = try? self.engine.reconcile()
      self.scheduleLeaseCheck()
      self.scheduleIdleExit()
    }
  }

  func connectionOpened() {
    queue.async {
      self.activeConnections += 1
    }
  }

  func perform(
    _ encoded: Data,
    deadline: DispatchTime,
    connectionState: ConnectionState,
    reply: ReplyBox
  ) {
    queue.async {
      reply(
        self.process(
          encoded,
          deadline: deadline,
          connectionState: connectionState
        )
      )
    }
  }

  func connectionInvalidated(state: ConnectionState) {
    queue.async {
      self.activeConnections = max(0, self.activeConnections - 1)
      if state.shouldRestoreOnInvalidation() {
        self.leaseDeadline = nil
        _ = try? self.engine.restore()
      }
      self.scheduleIdleExit()
    }
  }

  private func process(
    _ original: Data,
    deadline: DispatchTime,
    connectionState: ConnectionState
  ) -> Data? {
    var encoded = original
    defer { encoded.resetBytes(in: encoded.startIndex..<encoded.endIndex) }
    guard var request = try? WireCodec.decode(encoded, maximumBytes: WireLimits.maximumXPCBytes),
      request.kind == .request,
      request.sequence > 0,
      request.sequence <= WireLimits.maximumOperationsPerConnection,
      request.deadlineMilliseconds > 0,
      request.connectionIdentifier.contains(where: { $0 != 0 }),
      request.sessionIdentifier.contains(where: { $0 != 0 }),
      request.requestIdentifier.contains(where: { $0 != 0 })
    else {
      return nil
    }
    guard DispatchTime.now() < deadline else {
      return nil
    }
    defer { request.payload.resetBytes(in: request.payload.startIndex..<request.payload.endIndex) }
    let response: WireResponsePayload
    var applyOwnershipVerified = false
    do {
      let phase: OwnershipPhase
      switch request.operation {
      case .status:
        guard request.payload.isEmpty else {
          throw ProxyOwnershipFailure.invalidInput
        }
        try AuthorizationPolicy.verifyApplyRight()
        phase = try engine.status()
      case .enable:
        guard request.payload.isEmpty else {
          throw ProxyOwnershipFailure.invalidInput
        }
        phase = try engine.reconcile()
        guard phase == .idle else {
          throw ProxyOwnershipFailure.recoveryRequired
        }
        try AuthorizationPolicy.installApplyRight()
      case .repair:
        guard request.payload.isEmpty else {
          throw ProxyOwnershipFailure.invalidInput
        }
        phase = try engine.reconcile()
        guard phase == .idle else {
          throw ProxyOwnershipFailure.recoveryRequired
        }
        try AuthorizationPolicy.repairApplyRight()
      case .apply:
        guard request.payload.count == 2 + AuthorizationPolicy.externalFormBytes else {
          throw ProxyOwnershipFailure.invalidInput
        }
        let canonicalInputDigest = WireCodec.canonicalInputDigest(
          operation: request.operation,
          payload: Data(request.payload.prefix(2))
        )
        try engine.verifyApplyOwnership(
          sessionIdentifier: request.sessionIdentifier,
          requestIdentifier: request.requestIdentifier,
          canonicalInputDigest: canonicalInputDigest
        )
        applyOwnershipVerified = true
        try AuthorizationPolicy.verifyApplyRight()
        let port = UInt16(request.payload[0]) << 8 | UInt16(request.payload[1])
        var authorizationData = Data(request.payload.dropFirst(2))
        try AuthorizationPolicy.validateAndDestroyApplyExternalForm(&authorizationData)
        phase = try engine.apply(
          sessionIdentifier: request.sessionIdentifier,
          requestIdentifier: request.requestIdentifier,
          canonicalInputDigest: canonicalInputDigest,
          port: port
        )
        leaseDeadline = .now() + .seconds(15)
      case .restore, .disable:
        guard request.payload.isEmpty else {
          throw ProxyOwnershipFailure.invalidInput
        }
        phase = try engine.restore()
        leaseDeadline = nil
      case .remove:
        guard request.payload.isEmpty else {
          throw ProxyOwnershipFailure.invalidInput
        }
        phase = try engine.restore()
        guard phase == .idle else {
          throw ProxyOwnershipFailure.recoveryRequired
        }
        try AuthorizationPolicy.removeApplyRight()
        leaseDeadline = nil
      case .reconcile:
        let reconcile = try WireReconcilePayload.decode(request.payload)
        phase = try reconcileUnknown(request: request, payload: reconcile)
      case .renew:
        guard request.payload.isEmpty else {
          throw ProxyOwnershipFailure.invalidInput
        }
        phase = try engine.maintain(
          sessionIdentifier: request.sessionIdentifier,
          requestIdentifier: request.requestIdentifier
        )
        if phase == .applied {
          leaseDeadline = .now() + .seconds(15)
        } else {
          leaseDeadline = nil
        }
      case .none:
        throw ProxyOwnershipFailure.invalidInput
      }
      response = WireResponsePayload(
        outcome: phase == .recoveryRequired ? .conflict : .success,
        serviceState: phase == .recoveryRequired ? .recoveryRequired : .ready,
        ownershipPhase: phase,
        actionRequired: phase == .recoveryRequired ? .proxyRecovery : .none
      )
    } catch AuthorizationPolicyFailure.denied {
      response = WireResponsePayload(
        outcome: .actionRequired,
        serviceState: .ready,
        ownershipPhase: (try? engine.status()) ?? .idle,
        actionRequired: .administratorAuthentication,
        failure: .permission
      )
    } catch AuthorizationPolicyFailure.ruleUnavailable {
      response = WireResponsePayload(
        outcome: .actionRequired,
        serviceState: .recoveryRequired,
        ownershipPhase: (try? engine.status()) ?? .idle,
        actionRequired: .ruleRepair,
        failure: .integrity
      )
    } catch AuthorizationPolicyFailure.invalidExternalForm {
      response = failureResponse(.permission)
    } catch ProxyOwnershipFailure.conflict {
      response = WireResponsePayload(
        outcome: .conflict,
        serviceState: .recoveryRequired,
        ownershipPhase: (try? engine.status()) ?? .recoveryRequired,
        actionRequired: .proxyRecovery
      )
    } catch ProxyOwnershipFailure.invalidInput {
      response = failureResponse(.invalidInput)
    } catch ProxyOwnershipFailure.recoveryRequired {
      response = recoveryResponse(.integrity)
    } catch ProxyOwnershipFailure.unavailable {
      response = WireResponsePayload(
        outcome: .actionRequired,
        serviceState: .unavailableOrIncompatible,
        ownershipPhase: (try? engine.status()) ?? .idle,
        actionRequired: .incompatible,
        failure: .unavailable
      )
    } catch SystemProxyConfigurationFailure.primaryService {
      response = failureResponse(.lifecycle)
    } catch SystemProxyConfigurationFailure.preferences {
      response = failureResponse(.unavailable)
    } catch SystemProxyConfigurationFailure.protocolConfiguration {
      response = recoveryResponse(.integrity)
    } catch SystemProxyConfigurationFailure.mutation {
      response = failureResponse(.unavailable)
    } catch DurableOwnershipFailure.invalidFile {
      response = recoveryResponse(.integrity)
    } catch DurableOwnershipFailure.invalidState {
      response = recoveryResponse(.integrity)
    } catch DurableOwnershipFailure.unavailable {
      response = recoveryResponse(.storage)
    } catch {
      response = failureResponse(.unavailable)
    }
    connectionState.update(
      request: request,
      response: response,
      ownershipVerified: applyOwnershipVerified || request.operation == .reconcile
    )
    return try? WireCodec.encode(
      WireMessage(
        kind: .response,
        operation: request.operation,
        sequence: request.sequence,
        deadlineMilliseconds: 0,
        connectionIdentifier: request.connectionIdentifier,
        sessionIdentifier: request.sessionIdentifier,
        requestIdentifier: request.requestIdentifier,
        payload: response.encode()
      )
    )
  }

  private func failureResponse(_ failure: FailureCategory) -> WireResponsePayload {
    return WireResponsePayload(
      outcome: .failure,
      serviceState: .ready,
      ownershipPhase: (try? engine.status()) ?? .idle,
      failure: failure
    )
  }

  private func reconcileUnknown(
    request: WireMessage,
    payload: WireReconcilePayload
  ) throws -> OwnershipPhase {
    switch payload.originalOperation {
    case .apply:
      return try engine.reconcile(
        sessionIdentifier: request.sessionIdentifier,
        requestIdentifier: request.requestIdentifier,
        canonicalInputDigest: payload.canonicalInputDigest
      )
    case .restore, .disable:
      try verifyCanonicalEmptyInput(payload)
      return try engine.restore()
    case .remove:
      try verifyCanonicalEmptyInput(payload)
      let phase = try engine.restore()
      guard phase == .idle else {
        throw ProxyOwnershipFailure.recoveryRequired
      }
      try AuthorizationPolicy.removeApplyRight()
      return .idle
    case .enable, .repair:
      try verifyCanonicalEmptyInput(payload)
      let phase = try engine.reconcile()
      guard phase == .idle else {
        throw ProxyOwnershipFailure.recoveryRequired
      }
      try AuthorizationPolicy.verifyApplyRight()
      return .idle
    case .status:
      try verifyCanonicalEmptyInput(payload)
      try AuthorizationPolicy.verifyApplyRight()
      return try engine.status()
    case .none, .reconcile, .renew:
      throw ProxyOwnershipFailure.invalidInput
    }
  }

  private func verifyCanonicalEmptyInput(_ payload: WireReconcilePayload) throws {
    guard
      payload.canonicalInputDigest
        == WireCodec.canonicalInputDigest(
          operation: payload.originalOperation,
          payload: Data()
        )
    else {
      throw ProxyOwnershipFailure.conflict
    }
  }

  private func recoveryResponse(_ failure: FailureCategory) -> WireResponsePayload {
    return WireResponsePayload(
      outcome: .actionRequired,
      serviceState: .recoveryRequired,
      ownershipPhase: (try? engine.status()) ?? .recoveryRequired,
      actionRequired: .manualRecovery,
      failure: failure
    )
  }

  private func scheduleLeaseCheck() {
    queue.asyncAfter(deadline: .now() + .seconds(1)) {
      if self.leaseDeadline != nil, let phase = try? self.engine.maintain(),
        phase != .applied
      {
        self.leaseDeadline = nil
        self.scheduleIdleExit()
      }
      if let deadline = self.leaseDeadline, DispatchTime.now() >= deadline {
        self.leaseDeadline = nil
        _ = try? self.engine.restore()
        self.scheduleIdleExit()
      }
      self.scheduleLeaseCheck()
    }
  }

  private func scheduleIdleExit() {
    queue.asyncAfter(deadline: .now() + .seconds(1)) {
      if self.activeConnections == 0, self.leaseDeadline == nil,
        (try? self.engine.status()) == .idle
      {
        exit(EXIT_SUCCESS)
      }
    }
  }
}

private final class ProxySettingsServiceObject: NSObject, ProxySettingsService {
  private let coordinator: RequestCoordinator
  let connectionState = ConnectionState()
  private let lock = NSLock()
  private var connectionIdentifier: Data?
  private var sessionIdentifier: Data?
  private var sequenceValidator = WireSequenceValidator()

  init(coordinator: RequestCoordinator) {
    self.coordinator = coordinator
  }

  func perform(_ request: Data, withReply reply: @escaping (Data?) -> Void) {
    guard
      let message = try? WireCodec.decode(
        request,
        maximumBytes: WireLimits.maximumXPCBytes
      ),
      message.kind == .request
    else {
      reply(nil)
      return
    }
    let accepted = lock.withLock {
      guard sequenceValidator.accept(message.sequence) else {
        return false
      }
      if connectionIdentifier == nil {
        connectionIdentifier = message.connectionIdentifier
        sessionIdentifier = message.sessionIdentifier
      }
      guard message.connectionIdentifier == connectionIdentifier,
        message.sessionIdentifier == sessionIdentifier
      else {
        return false
      }
      return true
    }
    guard accepted else {
      reply(nil)
      return
    }
    coordinator.perform(
      request,
      deadline: .now() + .milliseconds(Int(message.deadlineMilliseconds)),
      connectionState: connectionState,
      reply: ReplyBox(reply)
    )
  }
}

private final class ListenerDelegate: NSObject, NSXPCListenerDelegate {
  private let coordinator: RequestCoordinator

  init(coordinator: RequestCoordinator) {
    self.coordinator = coordinator
  }

  func listener(
    _ listener: NSXPCListener,
    shouldAcceptNewConnection connection: NSXPCConnection
  ) -> Bool {
    connection.exportedInterface = NSXPCInterface(with: ProxySettingsService.self)
    let service = ProxySettingsServiceObject(coordinator: coordinator)
    connection.exportedObject = service
    coordinator.connectionOpened()
    connection.invalidationHandler = { [coordinator, service] in
      coordinator.connectionInvalidated(state: service.connectionState)
    }
    connection.resume()
    return true
  }
}

guard geteuid() == 0,
  let identity = try? CodeSigningRequirements.currentIdentity(
    expectedIdentifier: ServiceContract.daemonIdentifier
  ),
  let helperRequirement = try? CodeSigningRequirements.peerRequirement(
    selfIdentity: identity,
    peerIdentifier: ServiceContract.helperIdentifier
  )
else {
  exit(EXIT_FAILURE)
}

private let coordinator = RequestCoordinator()
private let delegate = ListenerDelegate(coordinator: coordinator)
private let listener = NSXPCListener(machServiceName: ServiceContract.daemonIdentifier)
coordinator.start()
listener.setConnectionCodeSigningRequirement(helperRequirement)
listener.delegate = delegate
listener.resume()
RunLoop.current.run()
