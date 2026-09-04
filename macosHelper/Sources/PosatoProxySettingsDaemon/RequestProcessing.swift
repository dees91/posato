import Foundation
import PosatoMacOSServiceCore

extension RequestCoordinator {
  func process(
    _ original: Data,
    deadline: DispatchTime,
    connectionState: ConnectionState
  ) -> Data? {
    var encoded = original
    defer { encoded.resetBytes(in: encoded.startIndex..<encoded.endIndex) }
    guard var request = validRequest(encoded), DispatchTime.now() < deadline else {
      return nil
    }
    defer { request.payload.resetBytes(in: request.payload.startIndex..<request.payload.endIndex) }
    var ownershipVerified = false
    let rawResponse = response(for: request, ownershipVerified: &ownershipVerified)
    let reconcilePayload =
      request.operation == .reconcile
      ? try? WireReconcilePayload.decode(request.payload) : nil
    let response = WireLifecyclePolicy.failClosedApplyResponse(
      requestOperation: request.operation,
      reconcilePayload: reconcilePayload,
      ownershipVerified: ownershipVerified,
      response: rawResponse
    )
    connectionState.update(
      request: request,
      response: response,
      ownershipVerified: ownershipVerified
    )
    return encode(response: response, for: request)
  }

  private func validRequest(_ encoded: Data) -> WireMessage? {
    guard let request = try? WireCodec.decode(encoded, maximumBytes: WireLimits.maximumXPCBytes),
      request.kind == .request,
      request.sequence > 0,
      request.sequence <= WireLimits.maximumOperationsPerConnection,
      request.deadlineMilliseconds > 0,
      request.connectionIdentifier.contains(where: { $0 != 0 }),
      request.sessionIdentifier.contains(where: { $0 != 0 }),
      request.requestIdentifier.contains(where: { $0 != 0 })
    else {
      return nil
    }
    return request
  }

  private func response(
    for request: WireMessage,
    ownershipVerified: inout Bool
  ) -> WireResponsePayload {
    do {
      let phase = try perform(request, ownershipVerified: &ownershipVerified)
      return WireResponsePayload(
        outcome: phase == .recoveryRequired ? .conflict : .success,
        serviceState: phase == .recoveryRequired ? .recoveryRequired : .ready,
        ownershipPhase: phase,
        actionRequired: phase == .recoveryRequired ? .proxyRecovery : .none
      )
    } catch {
      return response(for: error)
    }
  }

  private func perform(
    _ request: WireMessage,
    ownershipVerified: inout Bool
  ) throws -> OwnershipPhase {
    switch request.operation {
    case .status:
      return try performStatus(request)
    case .enable:
      return try performEnable(request, repair: false)
    case .repair:
      return try performEnable(request, repair: true)
    case .apply:
      return try performApply(request, ownershipVerified: &ownershipVerified)
    case .restore, .disable:
      return try performRestore(request)
    case .remove:
      return try performRemove(request)
    case .reconcile:
      return try reconcileUnknown(
        request: request,
        payload: WireReconcilePayload.decode(request.payload),
        ownershipVerified: &ownershipVerified
      )
    case .renew:
      return try performRenew(request)
    case .none, .selectApplications, .configureBrowserDomains:
      throw ProxyOwnershipFailure.invalidInput
    }
  }

  private func performStatus(_ request: WireMessage) throws -> OwnershipPhase {
    try requireEmptyPayload(request)
    try AuthorizationPolicy.verifyApplyRight()
    return try engine.status()
  }

  private func performEnable(
    _ request: WireMessage,
    repair: Bool
  ) throws -> OwnershipPhase {
    try requireEmptyPayload(request)
    let phase = try engine.reconcile()
    guard phase == .idle else {
      throw ProxyOwnershipFailure.recoveryRequired
    }
    if repair {
      try convergeAuthorizationRule(
        for: .repair,
        verify: AuthorizationPolicy.verifyApplyRight,
        repair: AuthorizationPolicy.repairApplyRight
      )
    } else {
      try AuthorizationPolicy.installApplyRight()
    }
    return phase
  }

  private func performApply(
    _ request: WireMessage,
    ownershipVerified: inout Bool
  ) throws -> OwnershipPhase {
    guard request.payload.count == 2 + AuthorizationPolicy.externalFormBytes else {
      throw ProxyOwnershipFailure.invalidInput
    }
    let canonicalInputDigest = WireCodec.canonicalInputDigest(
      operation: request.operation,
      payload: Data(request.payload.prefix(2))
    )
    _ = try engine.verifyApplyOwnership(
      sessionIdentifier: request.sessionIdentifier,
      requestIdentifier: request.requestIdentifier,
      canonicalInputDigest: canonicalInputDigest
    )
    ownershipVerified = true
    try AuthorizationPolicy.verifyApplyRight()
    let port = UInt16(request.payload[0]) << 8 | UInt16(request.payload[1])
    var authorizationData = Data(request.payload.dropFirst(2))
    try AuthorizationPolicy.validateAndDestroyApplyExternalForm(&authorizationData)
    let phase = try engine.apply(
      sessionIdentifier: request.sessionIdentifier,
      requestIdentifier: request.requestIdentifier,
      canonicalInputDigest: canonicalInputDigest,
      port: port
    )
    leaseDeadline = .now() + .seconds(15)
    return phase
  }

  private func performRestore(_ request: WireMessage) throws -> OwnershipPhase {
    try requireEmptyPayload(request)
    let phase = try engine.restore()
    leaseDeadline = nil
    return phase
  }

  private func performRemove(_ request: WireMessage) throws -> OwnershipPhase {
    try requireEmptyPayload(request)
    let phase = try engine.restore()
    guard phase == .idle else {
      throw ProxyOwnershipFailure.recoveryRequired
    }
    try AuthorizationPolicy.removeApplyRight()
    leaseDeadline = nil
    return phase
  }

  private func performRenew(_ request: WireMessage) throws -> OwnershipPhase {
    try requireEmptyPayload(request)
    let phase = try engine.maintain(
      sessionIdentifier: request.sessionIdentifier,
      requestIdentifier: request.requestIdentifier
    )
    leaseDeadline = phase == .applied ? .now() + .seconds(15) : nil
    return phase
  }

  private func requireEmptyPayload(_ request: WireMessage) throws {
    guard request.payload.isEmpty else {
      throw ProxyOwnershipFailure.invalidInput
    }
  }

  private func reconcileUnknown(
    request: WireMessage,
    payload: WireReconcilePayload,
    ownershipVerified: inout Bool
  ) throws -> OwnershipPhase {
    switch payload.originalOperation {
    case .apply:
      ownershipVerified = try engine.verifyApplyOwnership(
        sessionIdentifier: request.sessionIdentifier,
        requestIdentifier: request.requestIdentifier,
        canonicalInputDigest: payload.canonicalInputDigest
      )
      return try engine.reconcile(
        sessionIdentifier: request.sessionIdentifier,
        requestIdentifier: request.requestIdentifier,
        canonicalInputDigest: payload.canonicalInputDigest
      )
    case .restore, .disable:
      try verifyCanonicalEmptyInput(payload)
      return try engine.restore()
    case .remove:
      try verifyCanonicalEmptyInput(payload)
      let phase = try engine.restore()
      guard phase == .idle else {
        throw ProxyOwnershipFailure.recoveryRequired
      }
      try AuthorizationPolicy.removeApplyRight()
      return .idle
    case .enable, .repair:
      try verifyCanonicalEmptyInput(payload)
      let phase = try engine.reconcile()
      guard phase == .idle else {
        throw ProxyOwnershipFailure.recoveryRequired
      }
      try convergeAuthorizationRule(
        for: payload.originalOperation,
        verify: AuthorizationPolicy.verifyApplyRight,
        repair: AuthorizationPolicy.repairApplyRight
      )
      return .idle
    case .status:
      try verifyCanonicalEmptyInput(payload)
      try AuthorizationPolicy.verifyApplyRight()
      return try engine.status()
    case .none, .reconcile, .renew, .selectApplications, .configureBrowserDomains:
      throw ProxyOwnershipFailure.invalidInput
    }
  }

  private func verifyCanonicalEmptyInput(_ payload: WireReconcilePayload) throws {
    guard
      payload.canonicalInputDigest
        == WireCodec.canonicalInputDigest(
          operation: payload.originalOperation,
          payload: Data()
        )
    else {
      throw ProxyOwnershipFailure.conflict
    }
  }

  private func response(for error: Error) -> WireResponsePayload {
    return authorizationResponse(for: error)
      ?? ownershipResponse(for: error)
      ?? configurationResponse(for: error)
      ?? persistenceResponse(for: error)
      ?? failureResponse(.unavailable)
  }

  private func authorizationResponse(for error: Error) -> WireResponsePayload? {
    switch error {
    case AuthorizationPolicyFailure.denied:
      return WireResponsePayload(
        outcome: .actionRequired,
        serviceState: .ready,
        ownershipPhase: (try? engine.status()) ?? .idle,
        actionRequired: .administratorAuthentication,
        failure: .permission
      )
    case AuthorizationPolicyFailure.ruleUnavailable:
      return WireResponsePayload(
        outcome: .actionRequired,
        serviceState: .recoveryRequired,
        ownershipPhase: (try? engine.status()) ?? .idle,
        actionRequired: .ruleRepair,
        failure: .integrity
      )
    case AuthorizationPolicyFailure.invalidExternalForm:
      return failureResponse(.permission)
    default:
      return nil
    }
  }

  private func ownershipResponse(for error: Error) -> WireResponsePayload? {
    switch error {
    case ProxyOwnershipFailure.conflict:
      return WireResponsePayload(
        outcome: .conflict,
        serviceState: .recoveryRequired,
        ownershipPhase: (try? engine.status()) ?? .recoveryRequired,
        actionRequired: .proxyRecovery
      )
    case ProxyOwnershipFailure.invalidInput:
      return failureResponse(.invalidInput)
    case ProxyOwnershipFailure.recoveryRequired:
      return recoveryResponse(.integrity)
    case ProxyOwnershipFailure.unavailable:
      return WireResponsePayload(
        outcome: .actionRequired,
        serviceState: .unavailableOrIncompatible,
        ownershipPhase: (try? engine.status()) ?? .idle,
        actionRequired: .incompatible,
        failure: .unavailable
      )
    default:
      return nil
    }
  }

  private func configurationResponse(for error: Error) -> WireResponsePayload? {
    switch error {
    case SystemProxyConfigurationFailure.primaryService:
      return failureResponse(.lifecycle)
    case SystemProxyConfigurationFailure.preferences,
      SystemProxyConfigurationFailure.mutation:
      return failureResponse(.unavailable)
    case SystemProxyConfigurationFailure.protocolConfiguration:
      return recoveryResponse(.integrity)
    default:
      return nil
    }
  }

  private func persistenceResponse(for error: Error) -> WireResponsePayload? {
    switch error {
    case DurableOwnershipFailure.invalidFile,
      DurableOwnershipFailure.invalidState:
      return recoveryResponse(.integrity)
    case DurableOwnershipFailure.unavailable:
      return recoveryResponse(.storage)
    default:
      return nil
    }
  }

  private func encode(
    response: WireResponsePayload,
    for request: WireMessage
  ) -> Data? {
    return try? WireCodec.encode(
      WireMessage(
        kind: .response,
        operation: request.operation,
        sequence: request.sequence,
        deadlineMilliseconds: 0,
        connectionIdentifier: request.connectionIdentifier,
        sessionIdentifier: request.sessionIdentifier,
        requestIdentifier: request.requestIdentifier,
        payload: response.encode()
      )
    )
  }
}

func convergeAuthorizationRule(
  for operation: WireOperation,
  verify: () throws -> Void,
  repair: () throws -> Void
) throws {
  switch operation {
  case .enable:
    try verify()
  case .repair:
    do {
      try verify()
    } catch AuthorizationPolicyFailure.ruleUnavailable {
      try repair()
    }
  default:
    throw ProxyOwnershipFailure.invalidInput
  }
}
