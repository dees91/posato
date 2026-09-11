import Foundation
import PosatoMacOSServiceCore
import Security
import ServiceManagement

enum PipeFailure: Error {
  case invalidFrame
  case unavailable
  case unknownOutcome
}

/// An invalid connection is the signal for a daemon endpoint that never launched, so it is treated
/// as conclusive: nothing was delivered. An established connection invalidated while a request is in
/// flight is indistinguishable from that, and is an accepted bounded risk because the daemon side of
/// Status and Enable converges on a later attempt. Every other interruption, including a deadline
/// that expires after dispatch, keeps the outcome unknown.
func daemonDeliveryFailure(_ error: Error?) -> PipeFailure {
  guard let error else {
    return .unknownOutcome
  }
  let failure = error as NSError
  guard failure.domain == NSCocoaErrorDomain, failure.code == NSXPCConnectionInvalid else {
    return .unknownOutcome
  }
  return .unavailable
}

struct DaemonRequestSequence {
  private var nextValue: UInt32 = 1

  var hasCapacity: Bool {
    return nextValue <= WireLimits.maximumOperationsPerConnection
  }

  mutating func take() throws -> UInt32 {
    guard hasCapacity else {
      throw PipeFailure.invalidFrame
    }
    let value = nextValue
    nextValue += 1
    return value
  }
}

final class DaemonConnection: @unchecked Sendable {
  private let connection: NSXPCConnection
  private let callLock = NSLock()
  private var requestSequence = DaemonRequestSequence()

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

  var hasCapacity: Bool {
    return callLock.withLock { requestSequence.hasCapacity }
  }

  func perform(_ request: WireMessage) throws -> WireMessage {
    callLock.lock()
    defer { callLock.unlock() }
    var xpcRequest = try sequencedRequest(request)
    defer {
      xpcRequest.payload.resetBytes(
        in: xpcRequest.payload.startIndex..<xpcRequest.payload.endIndex
      )
    }
    var encoded = try WireCodec.encode(xpcRequest)
    defer { encoded.resetBytes(in: encoded.startIndex..<encoded.endIndex) }
    guard encoded.count <= WireLimits.maximumXPCBytes else {
      throw PipeFailure.invalidFrame
    }
    let response = try send(encoded, deadlineMilliseconds: request.deadlineMilliseconds)
    return try validatedResponse(response, request: request, xpcRequest: xpcRequest)
  }

  private func sequencedRequest(_ request: WireMessage) throws -> WireMessage {
    let sequence = try requestSequence.take()
    let sequenced = try WireMessage(
      kind: request.kind,
      operation: request.operation,
      sequence: sequence,
      deadlineMilliseconds: request.deadlineMilliseconds,
      connectionIdentifier: request.connectionIdentifier,
      sessionIdentifier: request.sessionIdentifier,
      requestIdentifier: request.requestIdentifier,
      payload: request.payload
    )
    return sequenced
  }

  private func send(_ encoded: Data, deadlineMilliseconds: UInt32) throws -> Data {
    let semaphore = DispatchSemaphore(value: 0)
    let lock = NSLock()
    var result: Data?
    var deliveryFailure: Error?
    let proxy = connection.remoteObjectProxyWithErrorHandler { error in
      lock.withLock {
        deliveryFailure = error
      }
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
    let timeout = DispatchTime.now() + .milliseconds(Int(deadlineMilliseconds))
    guard semaphore.wait(timeout: timeout) == .success else {
      throw PipeFailure.unknownOutcome
    }
    guard let response = lock.withLock({ result }) else {
      throw daemonDeliveryFailure(lock.withLock { deliveryFailure })
    }
    return response
  }

  private func validatedResponse(
    _ encoded: Data,
    request: WireMessage,
    xpcRequest: WireMessage
  ) throws -> WireMessage {
    let response = try WireCodec.decode(
      encoded,
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

final class LeaseRenewalChannel: @unchecked Sendable {
  private let requirement: String
  private var connection: DaemonConnection?

  init(requirement: String) {
    self.requirement = requirement
  }

  func perform(_ request: WireMessage) throws -> WireMessage {
    if connection?.hasCapacity != true {
      let replacement = try DaemonConnection(requirement: requirement)
      connection?.invalidate()
      connection = replacement
    }
    guard let connection else {
      throw PipeFailure.unavailable
    }
    return try connection.perform(request)
  }

  func invalidate() {
    connection?.invalidate()
    connection = nil
  }
}

final class LeaseRenewalGate: @unchecked Sendable {
  private let lock = NSLock()
  private var isActive = true

  func run(_ operation: () -> Void) {
    lock.withLock {
      guard isActive else {
        return
      }
      operation()
    }
  }

  func retire() {
    lock.withLock {
      isActive = false
    }
  }
}

final class LeaseRenewer: @unchecked Sendable {
  private let timer: DispatchSourceTimer
  private let gate = LeaseRenewalGate()
  private let channel: LeaseRenewalChannel

  init(
    ownershipConnection: DaemonConnection,
    daemonRequirement: String,
    request: WireMessage
  ) {
    channel = LeaseRenewalChannel(requirement: daemonRequirement)
    timer = DispatchSource.makeTimerSource(
      queue: DispatchQueue(label: "app.posato.macos.helper.lease")
    )
    timer.schedule(deadline: .now() + .seconds(5), repeating: .seconds(5))
    timer.setEventHandler { [channel, gate] in
      gate.run {
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
          let response = try channel.perform(renew)
          let payload = try WireResponsePayload.decode(response.payload)
          guard WireLifecyclePolicy.keepsLeaseHealthy(afterRenewal: payload) else {
            throw PipeFailure.unavailable
          }
        } catch {
          channel.invalidate()
          ownershipConnection.invalidate()
          exit(EXIT_FAILURE)
        }
      }
    }
    timer.resume()
  }

  func cancelAndWait() {
    timer.cancel()
    gate.retire()
    channel.invalidate()
  }
}

func randomIdentifier() throws -> Data {
  var data = Data(count: WireLimits.identifierBytes)
  let status = data.withUnsafeMutableBytes { bytes in
    SecRandomCopyBytes(kSecRandomDefault, bytes.count, bytes.baseAddress!)
  }
  guard status == errSecSuccess else {
    throw PipeFailure.unavailable
  }
  return data
}

func readExactly(count: Int) throws -> Data? {
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

func readFrame() throws -> Data? {
  guard let prefix = try readExactly(count: 4) else {
    return nil
  }
  let length = prefix.reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
  guard length > 0, length <= WireLimits.maximumFrameBytes else {
    throw PipeFailure.invalidFrame
  }
  return try readExactly(count: Int(length))
}

func writeFrame(_ frame: Data) throws {
  guard frame.count <= WireLimits.maximumFrameBytes else {
    throw PipeFailure.invalidFrame
  }
  var length = UInt32(frame.count).bigEndian
  let prefix = withUnsafeBytes(of: &length) { Data($0) }
  try FileHandle.standardOutput.write(contentsOf: prefix)
  try FileHandle.standardOutput.write(contentsOf: frame)
}

func remainingDeadline(
  receivedAt: DispatchTime,
  budgetMilliseconds: UInt32
) throws -> UInt32 {
  return try WireDeadline.remainingMilliseconds(
    receivedUptimeNanoseconds: receivedAt.uptimeNanoseconds,
    currentUptimeNanoseconds: DispatchTime.now().uptimeNanoseconds,
    budgetMilliseconds: budgetMilliseconds
  )
}

/// A Restore request that follows `request` on the same connection and session, reusing its
/// request identity unless the caller supplies a fresh or anonymous one.
func restoreMessage(
  after request: WireMessage,
  deadlineMilliseconds: UInt32,
  requestIdentifier: Data? = nil
) throws -> WireMessage {
  return try WireMessage(
    kind: .request,
    operation: .restore,
    sequence: request.sequence,
    deadlineMilliseconds: deadlineMilliseconds,
    connectionIdentifier: request.connectionIdentifier,
    sessionIdentifier: request.sessionIdentifier,
    requestIdentifier: requestIdentifier ?? request.requestIdentifier,
    payload: Data()
  )
}

func localResponse(
  request: WireMessage,
  payload: WireResponsePayload
) throws -> WireMessage {
  return try WireMessage(
    kind: .response,
    operation: request.operation,
    sequence: request.sequence,
    deadlineMilliseconds: 0,
    connectionIdentifier: request.connectionIdentifier,
    sessionIdentifier: request.sessionIdentifier,
    requestIdentifier: request.requestIdentifier,
    payload: payload.encode()
  )
}

func serviceState(_ status: SMAppService.Status) -> ServiceState {
  switch status {
  case .notRegistered:
    return .notRegistered
  case .enabled:
    return .ready
  case .requiresApproval:
    return .approvalRequired
  case .notFound:
    // Empty BTM after reset reports notFound even when the plist is in-bundle.
    // Check must offer Enable, not the unavailable Check-again-only dead end.
    return .notRegistered
  @unknown default:
    return .unavailableOrIncompatible
  }
}
