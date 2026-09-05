import Foundation
import PosatoMacOSServiceCore
import ServiceManagement

enum BrowserDomainRequestHandler {
  /// Configure replaces the domain session. While an Apply is owned the system proxy points at
  /// the live listener, so the request is refused without touching that session; otherwise the
  /// replacement listener starts first and the previous one stops only afterwards, so no
  /// applied port is ever left without a listener.
  static func handleConfigure(
    request: WireMessage,
    receivedAt: DispatchTime,
    service: SMAppService,
    existing: BrowserDomainSession?,
    applyOwned: Bool
  ) throws -> (response: WireMessage, session: BrowserDomainSession?) {
    if applyOwned {
      let refused = try configureResponseMessage(
        request: request,
        receivedAt: receivedAt,
        response: configureRefusedWhileAppliedResponse(
          serviceState: serviceState(service.status)
        )
      )
      return (refused, existing)
    }
    defer { existing?.stop() }
    let configureResponse: BrowserDomainConfigureResponse
    var session: BrowserDomainSession?
    do {
      let parsed = try BrowserDomainConfigurePayload.decode(request.payload)
      let started = BrowserDomainSession(payload: parsed)
      let port = try started.start()
      session = started
      configureResponse = try BrowserDomainConfigureResponse(
        outcome: WireResponsePayload(
          outcome: .success,
          serviceState: serviceState(service.status),
          ownershipPhase: .idle
        ),
        port: port
      )
    } catch let failure as BrowserDomainSessionFailure {
      session = nil
      configureResponse = try configureFailureResponse(failure: failure, service: service)
    } catch {
      session = nil
      configureResponse = try BrowserDomainConfigureResponse(
        outcome: WireResponsePayload(
          outcome: .failure,
          serviceState: serviceState(service.status),
          ownershipPhase: .idle,
          failure: .invalidInput
        ),
        port: 0
      )
    }
    let response = try configureResponseMessage(
      request: request,
      receivedAt: receivedAt,
      response: configureResponse
    )
    return (response, session)
  }

  /// A configure that arrives while this helper owns an applied mutation is a caller error, not
  /// an environment change; the applied session stays untouched.
  static func configureRefusedWhileAppliedResponse(
    serviceState: ServiceState
  ) throws -> BrowserDomainConfigureResponse {
    return try BrowserDomainConfigureResponse(
      outcome: WireResponsePayload(
        outcome: .failure,
        serviceState: serviceState,
        ownershipPhase: .applied,
        failure: .invalidInput
      ),
      port: 0
    )
  }

  /// Apply without a configured session would authenticate and mutate the proxy toward a port
  /// with no listener; it is refused before the authorization prompt.
  static func applyWithoutSessionResponse(serviceState: ServiceState) -> WireResponsePayload {
    return WireResponsePayload(
      outcome: .failure,
      serviceState: serviceState,
      ownershipPhase: .idle,
      failure: .invalidInput
    )
  }

  private static func configureResponseMessage(
    request: WireMessage,
    receivedAt: DispatchTime,
    response: BrowserDomainConfigureResponse
  ) throws -> WireMessage {
    return try WireMessage(
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
      payload: response.encode()
    )
  }

  @MainActor
  static func handleSelectApplications(
    request: WireMessage,
    receivedAt: DispatchTime
  ) throws -> WireMessage {
    let selection = try ApplicationSelectionService().select()
    return try WireMessage(
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
      payload: selection.encode()
    )
  }

  /// Apply succeeded in the daemon but the effective proxy chain still contained another route, so
  /// the helper restored the baseline. The Apply request fails as incompatible instead of echoing
  /// the Restore outcome; a restoration that did not reach Idle surfaces as recovery required.
  static func effectiveChainFailureResponse(restored: WireResponsePayload?) -> WireResponsePayload {
    guard let restored, restored.outcome == .success, restored.ownershipPhase == .idle else {
      return WireResponsePayload(
        outcome: .actionRequired,
        serviceState: .recoveryRequired,
        ownershipPhase: restored?.ownershipPhase ?? .recoveryRequired,
        actionRequired: .manualRecovery,
        failure: .unavailable
      )
    }
    return WireResponsePayload(
      outcome: .actionRequired,
      serviceState: .unavailableOrIncompatible,
      ownershipPhase: .idle,
      actionRequired: .incompatible,
      failure: .unavailable
    )
  }

  private static func configureFailureResponse(
    failure: BrowserDomainSessionFailure,
    service: SMAppService
  ) throws -> BrowserDomainConfigureResponse {
    switch failure {
    case .incompatible:
      return try BrowserDomainConfigureResponse(
        outcome: WireResponsePayload(
          outcome: .actionRequired,
          serviceState: .unavailableOrIncompatible,
          ownershipPhase: .idle,
          actionRequired: .incompatible,
          failure: .unavailable
        ),
        port: 0
      )
    case .unavailable:
      return try BrowserDomainConfigureResponse(
        outcome: WireResponsePayload(
          outcome: .failure,
          serviceState: serviceState(service.status),
          ownershipPhase: .idle,
          failure: .unavailable
        ),
        port: 0
      )
    }
  }
}
