import Foundation
import PosatoMacOSServiceCore

/// The daemon's code requirement and its registration. macOS 13 can record the approval of the
/// daemon without submitting it to launchd until the next restart; registering the enabled service
/// again submits it, so setup reads there do that once per helper process before they report
/// recovery. Later macOS versions were not seen to need it and keep the previous behavior.
final class DaemonEndpoint {
  let requirement: String
  private let register: () throws -> Void
  private var resubmitted = false

  init(requirement: String, register: @escaping () throws -> Void) {
    self.requirement = requirement
    self.register = register
  }

  func resubmitOnce() -> Bool {
    if #available(macOS 14, *) {
      return false
    }
    guard !resubmitted else {
      return false
    }
    resubmitted = true
    try? register()
    return true
  }
}

/// Retries once after the resubmission. A daemon that is still unreachable answers as it would have
/// without it; any other failure is thrown, so an enable keeps an unknown outcome for the client to
/// reconcile.
func retryAfterResubmission(
  request: WireMessage,
  receivedAt: DispatchTime,
  endpoint: DaemonEndpoint,
  firstPayload: WireResponsePayload,
  daemon: inout DaemonConnection?
) throws -> WireMessage {
  do {
    return try forwardDaemonLifecycleRequest(
      request: request,
      receivedAt: receivedAt,
      daemonRequirement: endpoint.requirement,
      daemon: &daemon
    )
  } catch PipeFailure.unavailable {
    daemon?.invalidate()
    daemon = nil
    return try localResponse(request: request, payload: firstPayload)
  }
}
