import Darwin
import Foundation
import PosatoMacOSServiceCore
import Security
import ServiceManagement

private enum PipeFailure: Error {
  case invalidFrame
  case unavailable
}

private final class DaemonConnection: @unchecked Sendable {
  private let connection: NSXPCConnection
  private let callLock = NSLock()
  private var nextSequence: UInt32 = 1

  init(requirement: String) throws {
    connection = NSXPCConnection(
      machServiceName: ServiceContract.daemonIdentifier,
      options: .privileged
    )
    connection.setCodeSigningRequirement(requirement)
    connection.remoteObjectInterface = NSXPCInterface(with: ProxySettingsService.self)
    connection.resume()
  }

  deinit {
    connection.invalidate()
  }

  func invalidate() {
    connection.invalidate()
  }

  func perform(_ request: WireMessage) throws -> WireMessage {
    callLock.lock()
    defer { callLock.unlock() }
    guard nextSequence <= WireLimits.maximumOperationsPerConnection else {
      throw PipeFailure.invalidFrame
    }
    var xpcRequest = try WireMessage(
      kind: request.kind,
      operation: request.operation,
      sequence: nextSequence,
      deadlineMilliseconds: request.deadlineMilliseconds,
      connectionIdentifier: request.connectionIdentifier,
      sessionIdentifier: request.sessionIdentifier,
      requestIdentifier: request.requestIdentifier,
      payload: request.payload
    )
    defer {
      xpcRequest.payload.resetBytes(
        in: xpcRequest.payload.startIndex..<xpcRequest.payload.endIndex
      )
    }
    nextSequence += 1
    var encoded = try WireCodec.encode(xpcRequest)
    defer { encoded.resetBytes(in: encoded.startIndex..<encoded.endIndex) }
    guard encoded.count <= WireLimits.maximumXPCBytes else {
      throw PipeFailure.invalidFrame
    }
    let semaphore = DispatchSemaphore(value: 0)
    let lock = NSLock()
    var result: Data?
    let proxy = connection.remoteObjectProxyWithErrorHandler { _ in
      semaphore.signal()
    }
    guard let service = proxy as? ProxySettingsService else {
      throw PipeFailure.unavailable
    }
    service.perform(encoded) { response in
      lock.withLock {
        result = response
      }
      semaphore.signal()
    }
    let timeout = DispatchTime.now() + .milliseconds(Int(request.deadlineMilliseconds))
    guard semaphore.wait(timeout: timeout) == .success,
      let responseData = lock.withLock({ result })
    else {
      throw PipeFailure.unavailable
    }
    let response = try WireCodec.decode(
      responseData,
      maximumBytes: WireLimits.maximumXPCBytes
    )
    guard response.kind == .response,
      response.operation == xpcRequest.operation,
      response.sequence == xpcRequest.sequence,
      response.connectionIdentifier == xpcRequest.connectionIdentifier,
      response.sessionIdentifier == xpcRequest.sessionIdentifier,
      response.requestIdentifier == xpcRequest.requestIdentifier
    else {
      throw PipeFailure.invalidFrame
    }
    return try WireMessage(
      kind: response.kind,
      operation: response.operation,
      sequence: request.sequence,
      deadlineMilliseconds: response.deadlineMilliseconds,
      connectionIdentifier: response.connectionIdentifier,
      sessionIdentifier: response.sessionIdentifier,
      requestIdentifier: response.requestIdentifier,
      payload: response.payload
    )
  }
}

private final class LeaseRenewer: @unchecked Sendable {
  private let timer: DispatchSourceTimer

  init(connection: DaemonConnection, request: WireMessage) {
    timer = DispatchSource.makeTimerSource(
      queue: DispatchQueue(label: "app.posato.macos.helper.lease")
    )
    timer.schedule(deadline: .now() + .seconds(5), repeating: .seconds(5))
    timer.setEventHandler {
      do {
        let renew = try WireMessage(
          kind: .request,
          operation: .renew,
          sequence: request.sequence,
          deadlineMilliseconds: 5_000,
          connectionIdentifier: request.connectionIdentifier,
          sessionIdentifier: request.sessionIdentifier,
          requestIdentifier: request.requestIdentifier,
          payload: Data()
        )
        _ = try connection.perform(renew)
      } catch {
        connection.invalidate()
      }
    }
    timer.resume()
  }

  func cancel() {
    timer.cancel()
  }
}

private func randomIdentifier() throws -> Data {
  var data = Data(count: WireLimits.identifierBytes)
  let status = data.withUnsafeMutableBytes { bytes in
    SecRandomCopyBytes(kSecRandomDefault, bytes.count, bytes.baseAddress!)
  }
  guard status == errSecSuccess else {
    throw PipeFailure.unavailable
  }
  return data
}

private func readExactly(count: Int) throws -> Data? {
  var data = Data()
  while data.count < count {
    let chunk = FileHandle.standardInput.readData(ofLength: count - data.count)
    if chunk.isEmpty {
      if data.isEmpty {
        return nil
      }
      throw PipeFailure.invalidFrame
    }
    data.append(chunk)
  }
  return data
}

private func readFrame() throws -> Data? {
  guard let prefix = try readExactly(count: 4) else {
    return nil
  }
  let length = prefix.reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
  guard length > 0, length <= WireLimits.maximumFrameBytes else {
    throw PipeFailure.invalidFrame
  }
  return try readExactly(count: Int(length))
}

private func writeFrame(_ frame: Data) throws {
  guard frame.count <= WireLimits.maximumFrameBytes else {
    throw PipeFailure.invalidFrame
  }
  var length = UInt32(frame.count).bigEndian
  let prefix = withUnsafeBytes(of: &length) { Data($0) }
  try FileHandle.standardOutput.write(contentsOf: prefix)
  try FileHandle.standardOutput.write(contentsOf: frame)
}

private func remainingDeadline(
  receivedAt: DispatchTime,
  budgetMilliseconds: UInt32
) throws -> UInt32 {
  return try WireDeadline.remainingMilliseconds(
    receivedUptimeNanoseconds: receivedAt.uptimeNanoseconds,
    currentUptimeNanoseconds: DispatchTime.now().uptimeNanoseconds,
    budgetMilliseconds: budgetMilliseconds
  )
}

private func localResponse(
  request: WireMessage,
  outcome: WireOutcome,
  state: ServiceState,
  action: ActionRequiredReason = .none,
  failure: FailureCategory = .none
) throws -> WireMessage {
  return try WireMessage(
    kind: .response,
    operation: request.operation,
    sequence: request.sequence,
    deadlineMilliseconds: 0,
    connectionIdentifier: request.connectionIdentifier,
    sessionIdentifier: request.sessionIdentifier,
    requestIdentifier: request.requestIdentifier,
    payload: WireResponsePayload(
      outcome: outcome,
      serviceState: state,
      ownershipPhase: .idle,
      actionRequired: action,
      failure: failure
    ).encode()
  )
}

private func serviceState(_ status: SMAppService.Status) -> ServiceState {
  switch status {
  case .notRegistered:
    return .notRegistered
  case .enabled:
    return .ready
  case .requiresApproval:
    return .approvalRequired
  case .notFound:
    return .unavailableOrIncompatible
  @unknown default:
    return .unavailableOrIncompatible
  }
}

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
