import Darwin
import Foundation
import Network
import Testing

@testable import PosatoMacOSHelper

func expectation(description: String) -> BlockingExpectation {
  BlockingExpectation(description: description)
}

func wait(for expectations: [BlockingExpectation], timeout: TimeInterval) {
  for item in expectations {
    #expect(item.wait(timeout: timeout), "\(item.description) timed out")
  }
}

final class BlockingExpectation: @unchecked Sendable {
  let description: String
  private let lock = NSLock()
  private var fulfilled = false
  private let condition = NSCondition()

  init(description: String) {
    self.description = description
  }

  func fulfill() {
    condition.lock()
    fulfilled = true
    condition.broadcast()
    condition.unlock()
  }

  func wait(timeout: TimeInterval) -> Bool {
    let deadline = Date().addingTimeInterval(timeout)
    condition.lock()
    defer { condition.unlock() }
    while !fulfilled, condition.wait(until: deadline) {}
    return fulfilled
  }
}

func sendLoopbackRequest(port: UInt16, request: String) throws -> String {
  let descriptor = try connectedLoopbackSocket(port: port)
  defer { Darwin.close(descriptor) }
  try send(request, to: descriptor)
  return try receiveToEnd(from: descriptor)
}

func connectedLoopbackSocket(port: UInt16) throws -> Int32 {
  let descriptor = socket(AF_INET, SOCK_STREAM, 0)
  guard descriptor >= 0 else {
    throw POSIXError(.ENOTSOCK)
  }
  var timeout = timeval(tv_sec: 3, tv_usec: 0)
  setsockopt(
    descriptor,
    SOL_SOCKET,
    SO_RCVTIMEO,
    &timeout,
    socklen_t(MemoryLayout.size(ofValue: timeout))
  )
  var address = sockaddr_in()
  address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
  address.sin_family = sa_family_t(AF_INET)
  address.sin_port = port.bigEndian
  address.sin_addr = in_addr(s_addr: inet_addr("127.0.0.1"))
  let connected = withUnsafePointer(to: &address) { pointer in
    pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { sockaddrPointer in
      Darwin.connect(descriptor, sockaddrPointer, socklen_t(MemoryLayout<sockaddr_in>.size))
    }
  }
  guard connected == 0 else {
    Darwin.close(descriptor)
    throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .ECONNREFUSED)
  }
  return descriptor
}

func send(_ request: String, to descriptor: Int32) throws {
  try send(Data(request.utf8), to: descriptor)
}

func send(_ data: Data, to descriptor: Int32) throws {
  try data.withUnsafeBytes { buffer in
    guard let baseAddress = buffer.baseAddress else {
      throw POSIXError(.EIO)
    }
    var offset = 0
    while offset < buffer.count {
      let written = Darwin.send(
        descriptor,
        baseAddress.advanced(by: offset),
        buffer.count - offset,
        0
      )
      guard written > 0 else {
        throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
      }
      offset += written
    }
  }
}

func receiveLine(from descriptor: Int32) throws -> String {
  var line = Data()
  var byte: UInt8 = 0
  while true {
    let count = Darwin.recv(descriptor, &byte, 1, 0)
    guard count == 1 else {
      throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
    }
    line.append(byte)
    if line.count >= 2, line[line.count - 2] == 13, byte == 10 {
      break
    }
  }
  guard let text = String(bytes: line, encoding: .utf8) else {
    throw POSIXError(.EILSEQ)
  }
  return text
}

func receiveToEnd(from descriptor: Int32) throws -> String {
  var response = Data()
  var buffer = [UInt8](repeating: 0, count: 4_096)
  while true {
    let count = Darwin.recv(descriptor, &buffer, buffer.count, 0)
    if count == 0 {
      break
    }
    guard count > 0 else {
      throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
    }
    response.append(buffer, count: count)
  }
  guard let text = String(bytes: response, encoding: .utf8) else {
    throw POSIXError(.EILSEQ)
  }
  return text
}

final class LocalHTTPOrigin: @unchecked Sendable {
  private let listener: NWListener
  let port: UInt16

  init() throws {
    let parameters = NWParameters.tcp
    parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: .any)
    let listener = try NWListener(using: parameters)
    let ready = DispatchSemaphore(value: 0)
    let selectedPort = LockedPort()
    let recordedHosts = HostRecorder()
    let originQueue = DispatchQueue(label: "app.posato.macos.helper.proxy-test-origin")
    listener.stateUpdateHandler = { [weak listener] state in
      if case .ready = state, let port = listener?.port?.rawValue {
        selectedPort.value = port
        ready.signal()
      }
    }
    listener.newConnectionHandler = { connection in
      Self.accept(connection, queue: originQueue, hosts: recordedHosts)
    }
    listener.start(queue: originQueue)
    guard ready.wait(timeout: .now() + 2) == .success, let port = selectedPort.value else {
      listener.cancel()
      throw BoundedHTTPProxyError.listenerTimeout
    }
    self.listener = listener
    self.port = port
    self.hostsRecorder = recordedHosts
  }

  private let hostsRecorder: HostRecorder

  func stop() {
    listener.cancel()
  }

  func receivedHosts() -> [String] {
    return hostsRecorder.snapshot()
  }

  private static func accept(
    _ connection: NWConnection,
    queue: DispatchQueue,
    hosts: HostRecorder
  ) {
    connection.start(queue: queue)
    receive(on: connection, accumulated: Data(), hosts: hosts)
  }

  private static func receive(
    on connection: NWConnection,
    accumulated: Data,
    hosts: HostRecorder
  ) {
    connection.receive(
      minimumIncompleteLength: 1,
      maximumLength: 16_384,
      completion: { data, _, isComplete, _ in
        handleTestOriginChunk(
          connection: connection,
          accumulated: accumulated,
          data: data,
          isComplete: isComplete,
          hosts: hosts
        )
      }
    )
  }

  private static func handleTestOriginChunk(
    connection: NWConnection,
    accumulated: Data,
    data: Data?,
    isComplete: Bool,
    hosts: HostRecorder
  ) {
    var request = accumulated
    if let data {
      request.append(data)
    }
    if let headerEnd = request.range(of: Data([13, 10, 13, 10])) {
      guard let header = String(bytes: request[..<headerEnd.upperBound], encoding: .utf8) else {
        connection.cancel()
        return
      }
      if let hostLine = header.split(separator: "\r\n").first(where: {
        $0.lowercased().hasPrefix("host:")
      }) {
        hosts.append(hostLine.dropFirst(5).trimmingCharacters(in: .whitespaces))
      }
      let body = Data("allowed-control".utf8)
      var response = Data(
        "HTTP/1.1 200 OK\r\nContent-Length: \(body.count)\r\nConnection: close\r\n\r\n".utf8
      )
      response.append(body)
      connection.send(
        content: response,
        completion: .contentProcessed { _ in
          connection.cancel()
        }
      )
      return
    }
    if isComplete {
      connection.cancel()
    } else {
      receive(on: connection, accumulated: request, hosts: hosts)
    }
  }
}

final class HostRecorder: @unchecked Sendable {
  private let lock = NSLock()
  private var hosts: [String] = []

  func append(_ host: String) {
    lock.lock()
    hosts.append(host)
    lock.unlock()
  }

  func snapshot() -> [String] {
    lock.lock()
    defer { lock.unlock() }
    return hosts
  }
}

final class LockedPort: @unchecked Sendable {
  private let lock = NSLock()
  private var storage: UInt16?

  var value: UInt16? {
    get {
      lock.lock()
      defer { lock.unlock() }
      return storage
    }
    set {
      lock.lock()
      storage = newValue
      lock.unlock()
    }
  }
}
