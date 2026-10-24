import Foundation
import PosatoMacOSServiceCore

/// Answers the parent's Hello, the first frame on the pipe or a later one that opens a new
/// connection, and returns the new connection identifier.
func welcomeParentConnection(hello request: WireMessage) throws -> Data {
  guard request.operation == .none,
    request.connectionIdentifier == Data(repeating: 0, count: WireLimits.identifierBytes),
    request.sessionIdentifier.contains(where: { $0 != 0 }),
    request.requestIdentifier.contains(where: { $0 != 0 }),
    try WireCapabilities.supports(
      WireCapabilities.decode(request.payload),
      required: WireLimits.requiredParentHelperCapabilities
    )
  else {
    throw PipeFailure.invalidFrame
  }
  let identifier = try randomIdentifier()
  let welcome = try WireMessage(
    kind: .welcome,
    operation: .none,
    sequence: request.sequence,
    deadlineMilliseconds: 0,
    connectionIdentifier: identifier,
    sessionIdentifier: request.sessionIdentifier,
    requestIdentifier: request.requestIdentifier,
    payload: WireCapabilities.encode(WireLimits.requiredParentHelperCapabilities)
  )
  try writeFrame(WireCodec.encode(welcome))
  return identifier
}

/// Runs one request on a daemon connection that is opened for it and closed after it.
func withConnectionOfItsOwn(
  _ body: (inout DaemonConnection?) throws -> WireMessage
) rethrows -> WireMessage {
  var connection: DaemonConnection?
  defer { connection?.invalidate() }
  return try body(&connection)
}
