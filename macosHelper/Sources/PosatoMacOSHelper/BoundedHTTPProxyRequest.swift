import Foundation
import Network

extension BoundedHTTPProxy {
  func receiveRequest(on connection: NWConnection, accumulated: Data) {
    connection.receive(
      minimumIncompleteLength: 1,
      maximumLength: Self.receiveChunkLength,
      completion: { [weak self, weak connection] data, _, isComplete, error in
        guard let self, let connection else {
          return
        }
        self.handleReceivedChunk(
          connection: connection,
          accumulated: accumulated,
          data: data,
          isComplete: isComplete,
          error: error
        )
      }
    )
  }

  private func handleReceivedChunk(
    connection: NWConnection,
    accumulated: Data,
    data: Data?,
    isComplete: Bool,
    error: NWError?
  ) {
    var request = accumulated
    if let data {
      request.append(data)
    }
    guard request.count <= BoundedProxyRequestParser.maximumRequestLength else {
      finish(connection)
      return
    }
    guard request.range(of: Data([13, 10, 13, 10])) != nil else {
      continueOrFinish(
        connection: connection, request: request, isComplete: isComplete, error: error)
      return
    }
    handleFramedRequest(
      connection: connection, request: request, isComplete: isComplete, error: error)
  }

  private func continueOrFinish(
    connection: NWConnection,
    request: Data,
    isComplete: Bool,
    error: NWError?
  ) {
    if isComplete || error != nil {
      finish(connection)
    } else {
      receiveRequest(on: connection, accumulated: request)
    }
  }

  private func handleFramedRequest(
    connection: NWConnection,
    request: Data,
    isComplete: Bool,
    error: NWError?
  ) {
    guard let framing = BoundedProxyRequestParser.framing(requestData: request) else {
      finish(connection)
      return
    }
    if case .fixedLength(let requiredLength) = framing {
      guard
        handleFixedLength(
          connection: connection,
          request: request,
          requiredLength: requiredLength,
          isComplete: isComplete,
          error: error
        )
      else {
        return
      }
    }
    guard let port = boundPort,
      let route = BoundedProxyRequestParser.route(
        requestData: request,
        selectedHosts: selectedHosts,
        listenerPort: port
      )
    else {
      finish(connection)
      return
    }
    cancelRequestTimeout(for: connection)
    dispatchRoute(route, on: connection)
  }

  private func handleFixedLength(
    connection: NWConnection,
    request: Data,
    requiredLength: Int,
    isComplete: Bool,
    error: NWError?
  ) -> Bool {
    if request.count < requiredLength {
      continueOrFinish(
        connection: connection, request: request, isComplete: isComplete, error: error)
      return false
    }
    guard request.count == requiredLength else {
      finish(connection)
      return false
    }
    return true
  }

  private func dispatchRoute(_ route: BoundedProxyRoute, on connection: NWConnection) {
    switch route {
    case .blockedHTTP:
      sendAndFinish(
        BlockedPage.httpResponse(sessionEndEpochMilliseconds: sessionEndEpochMilliseconds),
        on: connection
      )
    case .blockedConnect:
      notifyBlockedRequest()
      sendAndFinish(BlockedPage.connectRejection(), on: connection)
    case .localBlockedPage:
      sendAndFinish(
        BlockedPage.httpResponse(sessionEndEpochMilliseconds: sessionEndEpochMilliseconds),
        on: connection
      )
    case .tunnel(let host, let port, let initialData):
      forward(client: connection, host: host, port: port, initialData: initialData, isTunnel: true)
    case .forward(let host, let port, let initialData):
      forward(client: connection, host: host, port: port, initialData: initialData, isTunnel: false)
    }
  }
}
