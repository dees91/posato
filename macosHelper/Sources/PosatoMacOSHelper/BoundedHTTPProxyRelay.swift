import Foundation
import Network

extension BoundedHTTPProxy {
  func forward(
    client: NWConnection,
    host: String,
    port: UInt16,
    initialData: Data,
    isTunnel: Bool
  ) {
    guard connections.count + directConnections.count < Self.maximumTrackedConnections else {
      finish(client)
      return
    }
    let upstream = DirectTCPConnection(queue: queue)
    directConnections[ObjectIdentifier(upstream)] = upstream
    let resolved = resolveUpstream(host, port)
    scheduleTimeout(after: Self.idleConnectionTimeout, client: client, upstream: upstream)
    upstream.start(
      host: resolved.0,
      port: resolved.1,
      completion: { [weak self, weak client, weak upstream] error in
        guard let self, let client, let upstream else {
          return
        }
        self.handleUpstreamReady(
          client: client,
          upstream: upstream,
          error: error,
          initialData: initialData,
          isTunnel: isTunnel
        )
      }
    )
  }

  func beginTunnelRelay(client: NWConnection, upstream: DirectTCPConnection) {
    scheduleTimeout(after: Self.idleConnectionTimeout, client: client, upstream: upstream)
    relay(from: client, to: upstream)
    relay(from: upstream, to: client)
  }

  func relay(from source: NWConnection, to destination: DirectTCPConnection) {
    source.receive(
      minimumIncompleteLength: 1,
      maximumLength: 65_536,
      completion: { [weak self, weak source, weak destination] data, _, isComplete, error in
        guard let self, let source, let destination else {
          return
        }
        self.handleClientChunk(
          client: source,
          upstream: destination,
          data: data,
          isComplete: isComplete,
          error: error
        )
      }
    )
  }

  private func handleClientChunk(
    client: NWConnection,
    upstream: DirectTCPConnection,
    data: Data?,
    isComplete: Bool,
    error: NWError?
  ) {
    guard let data, !data.isEmpty else {
      finishOrContinueEmpty(
        client: client,
        upstream: upstream,
        isComplete: isComplete,
        error: error,
        direction: .clientToUpstream
      )
      return
    }
    upstream.send(data) { sendError in
      self.handleClientRelayResult(
        client: client,
        upstream: upstream,
        sendError: sendError,
        isComplete: isComplete,
        error: error
      )
    }
  }

  func relay(from source: DirectTCPConnection, to destination: NWConnection) {
    source.receive(
      maximumLength: 65_536,
      completion: { [weak self, weak source, weak destination] data, isComplete, error in
        guard let self, let source, let destination else {
          return
        }
        self.handleUpstreamChunk(
          client: destination,
          upstream: source,
          data: data,
          isComplete: isComplete,
          error: error
        )
      }
    )
  }

  private func handleUpstreamChunk(
    client: NWConnection,
    upstream: DirectTCPConnection,
    data: Data?,
    isComplete: Bool,
    error: Error?
  ) {
    guard let data, !data.isEmpty else {
      finishOrContinueEmpty(
        client: client,
        upstream: upstream,
        isComplete: isComplete,
        error: error,
        direction: .upstreamToClient
      )
      return
    }
    client.send(
      content: data,
      completion: .contentProcessed { sendError in
        self.queue.async {
          self.handleUpstreamRelayResult(
            client: client,
            upstream: upstream,
            sendError: sendError,
            isComplete: isComplete,
            error: error
          )
        }
      }
    )
  }

  func sendAndFinish(_ data: Data, on connection: NWConnection) {
    connection.send(
      content: data,
      completion: .contentProcessed { [weak self, weak connection] _ in
        guard let self, let connection else {
          return
        }
        self.queue.async {
          self.finish(connection)
        }
      }
    )
  }

  private enum RelayDirection {
    case clientToUpstream
    case upstreamToClient
  }

  private func handleUpstreamReady(
    client: NWConnection,
    upstream: DirectTCPConnection,
    error: Error?,
    initialData: Data,
    isTunnel: Bool
  ) {
    guard error == nil else {
      finish(client, upstream)
      return
    }
    if isTunnel {
      sendTunnelEstablished(client: client, upstream: upstream, initialData: initialData)
    } else {
      sendForwardInitial(client: client, upstream: upstream, initialData: initialData)
    }
  }

  private func sendTunnelEstablished(
    client: NWConnection,
    upstream: DirectTCPConnection,
    initialData: Data
  ) {
    client.send(
      content: Data("HTTP/1.1 200 Connection Established\r\n\r\n".utf8),
      completion: .contentProcessed { sendError in
        self.queue.async {
          self.handleTunnelHeaderResult(
            client: client,
            upstream: upstream,
            sendError: sendError,
            initialData: initialData
          )
        }
      }
    )
  }

  private func handleTunnelHeaderResult(
    client: NWConnection,
    upstream: DirectTCPConnection,
    sendError: NWError?,
    initialData: Data
  ) {
    guard sendError == nil else {
      finish(client, upstream)
      return
    }
    guard !initialData.isEmpty else {
      beginTunnelRelay(client: client, upstream: upstream)
      return
    }
    upstream.send(initialData) { initialDataError in
      if initialDataError != nil {
        self.finish(client, upstream)
      } else {
        self.beginTunnelRelay(client: client, upstream: upstream)
      }
    }
  }

  private func sendForwardInitial(
    client: NWConnection,
    upstream: DirectTCPConnection,
    initialData: Data
  ) {
    upstream.send(initialData) { sendError in
      if sendError != nil {
        self.finish(client, upstream)
      } else {
        self.scheduleTimeout(
          after: Self.idleConnectionTimeout,
          client: client,
          upstream: upstream
        )
        self.relay(from: upstream, to: client)
      }
    }
  }

  private func finishOrContinueEmpty(
    client: NWConnection,
    upstream: DirectTCPConnection,
    isComplete: Bool,
    error: Error?,
    direction: RelayDirection
  ) {
    switch direction {
    case .clientToUpstream:
      if isComplete || error != nil {
        finish(client, upstream)
      } else {
        scheduleTimeout(after: Self.idleConnectionTimeout, client: client, upstream: upstream)
        relay(from: client, to: upstream)
      }
    case .upstreamToClient:
      if isComplete || error != nil {
        finish(client, upstream)
      } else {
        scheduleTimeout(after: Self.idleConnectionTimeout, client: client, upstream: upstream)
        relay(from: upstream, to: client)
      }
    }
  }

  private func handleClientRelayResult(
    client: NWConnection,
    upstream: DirectTCPConnection,
    sendError: Error?,
    isComplete: Bool,
    error: Error?
  ) {
    if sendError != nil || isComplete || error != nil {
      finish(client, upstream)
    } else {
      scheduleTimeout(after: Self.idleConnectionTimeout, client: client, upstream: upstream)
      relay(from: client, to: upstream)
    }
  }

  private func handleUpstreamRelayResult(
    client: NWConnection,
    upstream: DirectTCPConnection,
    sendError: Error?,
    isComplete: Bool,
    error: Error?
  ) {
    if sendError != nil || isComplete || error != nil {
      finish(client, upstream)
    } else {
      scheduleTimeout(after: Self.idleConnectionTimeout, client: client, upstream: upstream)
      relay(from: upstream, to: client)
    }
  }
}
