import Foundation
import PosatoMacOSServiceCore

private final class ProxySettingsServiceObject: NSObject, ProxySettingsService {
  private let coordinator: RequestCoordinator
  let connectionState = ConnectionState()
  private let lock = NSLock()
  private var connectionIdentifier: Data?
  private var sessionIdentifier: Data?
  private var sequenceValidator = WireSequenceValidator()

  init(coordinator: RequestCoordinator) {
    self.coordinator = coordinator
  }

  func perform(_ request: Data, withReply reply: @escaping (Data?) -> Void) {
    guard
      let message = try? WireCodec.decode(
        request,
        maximumBytes: WireLimits.maximumXPCBytes
      ),
      message.kind == .request
    else {
      reply(nil)
      return
    }
    let accepted = lock.withLock {
      guard sequenceValidator.accept(message.sequence) else {
        return false
      }
      if connectionIdentifier == nil {
        connectionIdentifier = message.connectionIdentifier
        sessionIdentifier = message.sessionIdentifier
      }
      guard message.connectionIdentifier == connectionIdentifier,
        message.sessionIdentifier == sessionIdentifier
      else {
        return false
      }
      return true
    }
    guard accepted else {
      reply(nil)
      return
    }
    coordinator.perform(
      request,
      deadline: .now() + .milliseconds(Int(message.deadlineMilliseconds)),
      connectionState: connectionState,
      reply: ReplyBox(reply)
    )
  }
}

private final class ListenerDelegate: NSObject, NSXPCListenerDelegate {
  private let coordinator: RequestCoordinator

  init(coordinator: RequestCoordinator) {
    self.coordinator = coordinator
  }

  func listener(
    _ listener: NSXPCListener,
    shouldAcceptNewConnection connection: NSXPCConnection
  ) -> Bool {
    connection.exportedInterface = NSXPCInterface(with: ProxySettingsService.self)
    let service = ProxySettingsServiceObject(coordinator: coordinator)
    connection.exportedObject = service
    coordinator.connectionOpened()
    connection.invalidationHandler = { [coordinator, service] in
      coordinator.connectionInvalidated(state: service.connectionState)
    }
    connection.resume()
    return true
  }
}

guard geteuid() == 0,
  let identity = try? CodeSigningRequirements.currentIdentity(
    expectedIdentifier: ServiceContract.daemonIdentifier
  ),
  let helperRequirement = try? CodeSigningRequirements.peerRequirement(
    selfIdentity: identity,
    peerIdentifier: ServiceContract.helperIdentifier
  )
else {
  exit(EXIT_FAILURE)
}

private let coordinator = RequestCoordinator()
private let delegate = ListenerDelegate(coordinator: coordinator)
private let listener = NSXPCListener(machServiceName: ServiceContract.daemonIdentifier)
do {
  try coordinator.start()
} catch {
  exit(EXIT_FAILURE)
}
listener.setConnectionCodeSigningRequirement(helperRequirement)
listener.delegate = delegate
listener.resume()
RunLoop.current.run()
