import Foundation
import Network

enum BoundedHTTPProxyError: Error, Equatable, Sendable {
  case invalidPort
  case listenerFailed
  case listenerTimeout
}

final class BoundedHTTPProxy: @unchecked Sendable {
  static let maximumTrackedConnections = 128
  static let idleConnectionTimeout: TimeInterval = 30
  static let headerTimeout: TimeInterval = 5
  static let receiveChunkLength = 16_384

  let selectedHosts: Set<String>
  let sessionEndEpochMilliseconds: UInt64?
  let resolveUpstream: (String, UInt16) -> (String, UInt16)
  let blockedRequestHandlerLock = NSLock()
  let queue = DispatchQueue(label: "app.posato.macos.helper.proxy")
  let startupCondition = NSCondition()
  var startupResult: Result<UInt16, BoundedHTTPProxyError>?
  var listener: NWListener?
  var connections: [ObjectIdentifier: NWConnection] = [:]
  var directConnections: [ObjectIdentifier: DirectTCPConnection] = [:]
  var requestTimeouts: [ObjectIdentifier: DispatchWorkItem] = [:]
  var blockedRequestHandler: (@Sendable () -> Void)?
  var boundPortStorage: UInt16?

  init(
    selectedHosts: Set<String>,
    sessionEndEpochMilliseconds: UInt64? = nil,
    resolveUpstream: @escaping (String, UInt16) -> (String, UInt16) = { host, port in
      (host, port)
    },
    blockedRequestHandler: (@Sendable () -> Void)? = nil
  ) {
    self.selectedHosts = selectedHosts
    self.sessionEndEpochMilliseconds = sessionEndEpochMilliseconds
    self.resolveUpstream = resolveUpstream
    self.blockedRequestHandler = blockedRequestHandler
  }

  var boundPort: UInt16? {
    startupCondition.withLock { boundPortStorage }
  }

  func setBlockedRequestHandler(_ handler: (@Sendable () -> Void)?) {
    blockedRequestHandlerLock.lock()
    blockedRequestHandler = handler
    blockedRequestHandlerLock.unlock()
  }

  func start(timeout: TimeInterval = 3) throws -> UInt16 {
    if let existing = takeExistingPort() {
      return existing
    }
    let listener = try buildListener()
    self.listener = listener
    attachHandlers(listener: listener)
    listener.start(queue: queue)
    return try awaitStartup(listener: listener, timeout: timeout)
  }

  func stop() {
    queue.sync {
      listener?.cancel()
      listener = nil
      for connection in connections.values {
        connection.cancel()
      }
      connections.removeAll()
      for connection in directConnections.values {
        connection.cancel()
      }
      directConnections.removeAll()
      for timeout in requestTimeouts.values {
        timeout.cancel()
      }
      requestTimeouts.removeAll()
    }
    startupCondition.withLock {
      boundPortStorage = nil
      startupResult = nil
    }
  }

  func accept(_ connection: NWConnection) {
    guard connections.count < Self.maximumTrackedConnections else {
      connection.cancel()
      return
    }
    let identifier = ObjectIdentifier(connection)
    connections[identifier] = connection
    scheduleTimeout(after: Self.headerTimeout, for: [connection])
    connection.stateUpdateHandler = { [weak self, weak connection] state in
      guard let self, let connection else {
        return
      }
      if case .failed = state {
        self.finish(connection)
      } else if case .cancelled = state {
        self.cancelRequestTimeout(for: connection)
        self.connections.removeValue(forKey: ObjectIdentifier(connection))
      }
    }
    connection.start(queue: queue)
    receiveRequest(on: connection, accumulated: Data())
  }

  func finishStartup(_ result: Result<UInt16, BoundedHTTPProxyError>) {
    startupCondition.withLock {
      guard startupResult == nil else {
        return
      }
      startupResult = result
      if case .success(let port) = result {
        boundPortStorage = port
      }
      startupCondition.broadcast()
    }
  }

  private func takeExistingPort() -> UInt16? {
    startupCondition.lock()
    defer { startupCondition.unlock() }
    if let existing = boundPortStorage {
      return existing
    }
    startupResult = nil
    return nil
  }

  private func buildListener() throws -> NWListener {
    let parameters = NWParameters.tcp
    parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: .any)
    do {
      return try NWListener(using: parameters)
    } catch {
      throw BoundedHTTPProxyError.listenerFailed
    }
  }

  private func attachHandlers(listener: NWListener) {
    listener.newConnectionHandler = { [weak self] connection in
      self?.accept(connection)
    }
    listener.stateUpdateHandler = { [weak self, weak listener] state in
      guard let self, let listener, self.listener === listener else {
        return
      }
      self.handleListenerState(state, listener: listener)
    }
  }

  private func handleListenerState(_ state: NWListener.State, listener: NWListener) {
    switch state {
    case .ready:
      guard let rawPort = listener.port?.rawValue else {
        finishStartup(.failure(.listenerFailed))
        return
      }
      finishStartup(.success(rawPort))
    case .failed:
      if boundPort == nil {
        finishStartup(.failure(.listenerFailed))
      } else {
        handleListenerFailure(listener)
      }
    default:
      break
    }
  }

  private func awaitStartup(listener: NWListener, timeout: TimeInterval) throws -> UInt16 {
    let deadline = Date().addingTimeInterval(timeout)
    startupCondition.lock()
    while startupResult == nil, startupCondition.wait(until: deadline) {}
    let result = startupResult
    startupCondition.unlock()
    guard let result else {
      listener.cancel()
      self.listener = nil
      startupCondition.withLock {
        boundPortStorage = nil
        startupResult = .failure(.listenerTimeout)
      }
      throw BoundedHTTPProxyError.listenerTimeout
    }
    return try result.get()
  }
}
