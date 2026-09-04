import Foundation
import Network

extension BoundedHTTPProxy {
  func finish(_ connections: NWConnection...) {
    finish(Array(connections))
  }

  func finish(_ connections: [NWConnection]) {
    for connection in connections {
      cancelRequestTimeout(for: connection)
      connection.cancel()
      self.connections.removeValue(forKey: ObjectIdentifier(connection))
    }
  }

  func finish(_ client: NWConnection, _ upstream: DirectTCPConnection) {
    cancelRequestTimeout(for: client)
    cancelRequestTimeout(for: upstream)
    client.cancel()
    upstream.cancel()
    connections.removeValue(forKey: ObjectIdentifier(client))
    directConnections.removeValue(forKey: ObjectIdentifier(upstream))
  }

  func cancelRequestTimeout(for connection: NWConnection) {
    requestTimeouts.removeValue(forKey: ObjectIdentifier(connection))?.cancel()
  }

  func cancelRequestTimeout(for connection: DirectTCPConnection) {
    requestTimeouts.removeValue(forKey: ObjectIdentifier(connection))?.cancel()
  }

  func scheduleTimeout(
    after delay: TimeInterval,
    client: NWConnection,
    upstream: DirectTCPConnection
  ) {
    cancelRequestTimeout(for: client)
    cancelRequestTimeout(for: upstream)
    let timeout = DispatchWorkItem { [weak self, weak client, weak upstream] in
      guard let self, let client, let upstream else {
        return
      }
      self.finish(client, upstream)
    }
    requestTimeouts[ObjectIdentifier(client)] = timeout
    requestTimeouts[ObjectIdentifier(upstream)] = timeout
    queue.asyncAfter(deadline: .now() + delay, execute: timeout)
  }

  func scheduleTimeout(after delay: TimeInterval, for connections: [NWConnection]) {
    for connection in connections {
      cancelRequestTimeout(for: connection)
    }
    let timeout = DispatchWorkItem { [weak self, connections] in
      self?.finish(connections)
    }
    for connection in connections {
      requestTimeouts[ObjectIdentifier(connection)] = timeout
    }
    queue.asyncAfter(deadline: .now() + delay, execute: timeout)
  }

  func handleListenerFailure(_ failedListener: NWListener) {
    guard listener === failedListener else {
      return
    }
    failedListener.cancel()
    listener = nil
    finish(Array(connections.values))
    for connection in directConnections.values {
      connection.cancel()
    }
    directConnections.removeAll()
    startupCondition.withLock {
      boundPortStorage = nil
    }
  }

  func notifyBlockedRequest() {
    blockedRequestHandlerLock.lock()
    let handler = blockedRequestHandler
    blockedRequestHandlerLock.unlock()
    handler?()
  }
}
