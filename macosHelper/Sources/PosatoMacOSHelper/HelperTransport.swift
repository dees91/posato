import Foundation
import PosatoMacOSServiceCore
import Security
import ServiceManagement

enum PipeFailure: Error {
  case invalidFrame
  case unavailable
}

final class DaemonConnection: @unchecked Sendable {
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
    guard nextSequence <= WireLimits.maximumOperationsPerConnection else {
      throw PipeFailure.invalidFrame
    }
    let sequenced = try WireMessage(
      kind: request.kind,
      operation: request.operation,
      sequence: nextSequence,
      deadlineMilliseconds: request.deadlineMilliseconds,
      connectionIdentifier: request.connectionIdentifier,
      sessionIdentifier: request.sessionIdentifier,
      requestIdentifier: request.requestIdentifier,
      payload: request.payload
    )
    nextSequence += 1
    return sequenced
  }

  private func send(_ encoded: Data, deadlineMilliseconds: UInt32) throws -> Data {
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
    let timeout = DispatchTime.now() + .milliseconds(Int(deadlineMilliseconds))
    guard semaphore.wait(timeout: timeout) == .success,
      let response = lock.withLock({ result })
    else {
      throw PipeFailure.unavailable
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

final class LeaseRenewer: @unchecked Sendable {
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
    return .unavailableOrIncompatible
  @unknown default:
    return .unavailableOrIncompatible
  }
}
