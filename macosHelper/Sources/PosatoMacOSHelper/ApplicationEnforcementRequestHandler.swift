import Foundation
import PosatoMacOSServiceCore
import ServiceManagement

struct ApplicationSessionHost {
  private var session: ApplicationEnforcementSession?

  mutating func dispatch(
    request: WireMessage,
    receivedAt: DispatchTime,
    service: SMAppService
  ) throws -> Bool {
    guard request.operation == .configureApplications else {
      return false
    }
    let handled = try ApplicationEnforcementRequestHandler.handleConfigure(
      request: request,
      receivedAt: receivedAt,
      serviceState: serviceState(service.status),
      existing: session
    )
    session = handled.session
    try writeFrame(WireCodec.encode(handled.response))
    return true
  }

  mutating func stop() {
    session?.stop()
    session = nil
  }
}

enum ApplicationEnforcementRequestHandler {
  /// Configure replaces the application session: the replacement observer
  /// starts before the previous one stops, so a launched application cannot
  /// slip through the handoff. An empty set clears the session. A corrupt
  /// payload keeps the existing session: enforcement is fail-open, so a
  /// caller bug must not disarm an active observer. Application enforcement
  /// mutates no system setting, so there is no Apply-owned state to refuse
  /// against.
  static func handleConfigure(
    request: WireMessage,
    receivedAt: DispatchTime,
    serviceState: ServiceState,
    existing: ApplicationEnforcementSession?,
    makeSession: (Set<Data>, UInt64?) -> ApplicationEnforcementSession = { requirements, endTime in
      ApplicationEnforcementSession(
        requirements: requirements,
        sessionEndEpochMilliseconds: endTime
      )
    }
  ) throws -> (response: WireMessage, session: ApplicationEnforcementSession?) {
    let configureResponse: ApplicationEnforcementConfigureResponse
    var session: ApplicationEnforcementSession?
    do {
      let parsed = try ApplicationEnforcementConfigurePayload.decode(request.payload)
      if parsed.requirements.isEmpty {
        session = nil
      } else {
        let started = makeSession(Set(parsed.requirements), parsed.sessionEndEpochMilliseconds)
        started.start()
        session = started
      }
      configureResponse = try ApplicationEnforcementConfigureResponse(
        outcome: WireResponsePayload(
          outcome: .success,
          serviceState: serviceState,
          ownershipPhase: .idle
        ),
        acceptedCount: UInt16(parsed.requirements.count)
      )
    } catch {
      session = existing
      configureResponse = try ApplicationEnforcementConfigureResponse(
        outcome: WireResponsePayload(
          outcome: .failure,
          serviceState: serviceState,
          ownershipPhase: .idle,
          failure: .invalidInput
        ),
        acceptedCount: 0
      )
    }
    if session !== existing {
      existing?.stop()
    }
    return (
      try WireMessage(
        kind: .response,
        operation: request.operation,
        sequence: request.sequence,
        deadlineMilliseconds: try remainingDeadline(
          receivedAt: receivedAt,
          budgetMilliseconds: request.deadlineMilliseconds
        ),
        connectionIdentifier: request.connectionIdentifier,
        sessionIdentifier: request.sessionIdentifier,
        requestIdentifier: request.requestIdentifier,
        payload: configureResponse.encode()
      ),
      session
    )
  }
}
