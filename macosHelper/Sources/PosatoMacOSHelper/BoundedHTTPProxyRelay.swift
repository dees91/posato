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
    scheduleTimeout(after: idleTimeout, client: client, upstream: upstream)
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

  func forwardToLoopback(
    client: NWConnection,
    addresses: [String],
    port: UInt16,
    initialData: Data,
    isTunnel: Bool
  ) {
    guard connections.count + directConnections.count < Self.maximumTrackedConnections,
      loopbackUpstreams.count < Self.maximumLoopbackRelays
    else {
      finish(client)
      return
    }
    let upstream = DirectTCPConnection(queue: queue)
    directConnections[ObjectIdentifier(upstream)] = upstream
    loopbackUpstreams.insert(ObjectIdentifier(upstream))
    scheduleTimeout(after: idleTimeout, client: client, upstream: upstream)
    upstream.start(
      literalAddresses: addresses,
      port: port,
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

  /// A loopback relay is established once the tunnel is confirmed or the upstream's first response byte arrives;
  /// from then on it has no idle timeout (ADR 0005, MACOS-024 amendment).
  private func markEstablishedIfLoopback(client: NWConnection, upstream: DirectTCPConnection) {
    let identifier = ObjectIdentifier(upstream)
    guard loopbackUpstreams.contains(identifier), !establishedLoopbackUpstreams.contains(identifier)
    else {
      return
    }
    establishedLoopbackUpstreams.insert(identifier)
    cancelRequestTimeout(for: client)
    cancelRequestTimeout(for: upstream)
  }

  func beginTunnelRelay(client: NWConnection, upstream: DirectTCPConnection) {
    scheduleTimeout(after: idleTimeout, client: client, upstream: upstream)
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
    markEstablishedIfLoopback(client: client, upstream: upstream)
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
    markEstablishedIfLoopback(client: client, upstream: upstream)
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
          after: self.idleTimeout,
          client: client,
          upstream: upstream
        )
        self.relay(from: upstream, to: client)
        if self.loopbackUpstreams.contains(ObjectIdentifier(upstream)) {
          self.finishWhenClientCloses(client: client, upstream: upstream)
        }
      }
    }
  }

  /// An absolute-form loopback relay reads nothing more from its client, so without this an established relay
  /// would keep its capped slot after the client leaves (ADR 0005, MACOS-024). Any further byte is refused too.
  private func finishWhenClientCloses(client: NWConnection, upstream: DirectTCPConnection) {
    client.receive(
      minimumIncompleteLength: 1,
      maximumLength: 1,
      completion: { [weak self, weak client, weak upstream] _, _, _, _ in
        guard let self, let client, let upstream else {
          return
        }
        self.queue.async {
          self.finish(client, upstream)
        }
      }
    )
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
        scheduleTimeout(after: idleTimeout, client: client, upstream: upstream)
        relay(from: client, to: upstream)
      }
    case .upstreamToClient:
      if isComplete || error != nil {
        finish(client, upstream)
      } else {
        scheduleTimeout(after: idleTimeout, client: client, upstream: upstream)
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
      scheduleTimeout(after: idleTimeout, client: client, upstream: upstream)
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
      scheduleTimeout(after: idleTimeout, client: client, upstream: upstream)
      relay(from: upstream, to: client)
    }
  }
}
