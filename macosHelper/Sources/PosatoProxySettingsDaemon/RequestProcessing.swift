import Foundation
import PosatoMacOSServiceCore

extension RequestCoordinator {
  func process(
    _ original: Data,
    peerUserID: UInt32?,
    deadline: DispatchTime,
    connectionState: ConnectionState
  ) -> Data? {
    var encoded = original
    defer { encoded.resetBytes(in: encoded.startIndex..<encoded.endIndex) }
    guard var request = validRequest(encoded), DispatchTime.now() < deadline else {
      return nil
    }
    defer { request.payload.resetBytes(in: request.payload.startIndex..<request.payload.endIndex) }
    var context = RequestContext(peerUserID: peerUserID)
    let rawResponse = response(for: request, context: &context)
    let reconcilePayload =
      request.operation == .reconcile
      ? try? WireReconcilePayload.decode(request.payload) : nil
    let response = WireLifecyclePolicy.failClosedApplyResponse(
      requestOperation: request.operation,
      reconcilePayload: reconcilePayload,
      ownershipVerified: context.ownershipVerified,
      response: rawResponse
    )
    connectionState.update(
      request: request,
      response: response,
      ownershipVerified: context.ownershipVerified
    )
    var payload = response.encode()
    if let grantState = context.grantState, response.outcome == .success {
      payload.append(grantState.rawValue)
    }
    return encode(payload: payload, for: request)
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
    context: inout RequestContext
  ) -> WireResponsePayload {
    do {
      let phase = try perform(request, context: &context)
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
    context: inout RequestContext
  ) throws -> OwnershipPhase {
    switch request.operation {
    case .status:
      return try performStatus(request, context: &context)
    case .apply, .applyWithGrant:
      return try performApply(
        request,
        context: &context,
        grantAuthorized: request.operation == .applyWithGrant
      )
    case .reconcile:
      return try reconcileUnknown(
        request: request,
        payload: WireReconcilePayload.decode(request.payload),
        context: &context
      )
    case .prepareGrant, .grant, .revokeGrant:
      return try performGrantOperation(request, peerUserID: context.peerUserID)
    default:
      return try performLifecycle(request)
    }
  }

  private func performLifecycle(_ request: WireMessage) throws -> OwnershipPhase {
    switch request.operation {
    case .enable, .repair:
      return try performEnable(request, repair: request.operation == .repair)
    case .restore:
      try requireEmptyPayload(request)
      return try performRestore()
    case .disable:
      try requireEmptyPayload(request)
      return try performDisable()
    case .remove:
      try requireEmptyPayload(request)
      return try performRemove()
    case .renew:
      return try performRenew(request)
    default:
      throw ProxyOwnershipFailure.invalidInput
    }
  }

  private func performStatus(
    _ request: WireMessage,
    context: inout RequestContext
  ) throws -> OwnershipPhase {
    let includesGrantState = request.payload == WireStatusRequest.includeGrantState
    guard request.payload.isEmpty || includesGrantState else {
      throw ProxyOwnershipFailure.invalidInput
    }
    try requireExactRule(.apply)
    let phase = try engine.status()
    if includesGrantState {
      context.grantState = try grantState(peerUserID: context.peerUserID)
    }
    return phase
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
    try convergeRules(repair: repair)
    return phase
  }

  private func performApply(
    _ request: WireMessage,
    context: inout RequestContext,
    grantAuthorized: Bool
  ) throws -> OwnershipPhase {
    let authorizationBytes = grantAuthorized ? 0 : AuthorizationPolicy.externalFormBytes
    guard request.payload.count == 2 + authorizationBytes else {
      throw ProxyOwnershipFailure.invalidInput
    }
    let port = Data(request.payload.prefix(2))
    let canonicalInputDigest = WireCodec.canonicalInputDigest(operation: .apply, payload: port)
    _ = try engine.verifyApplyOwnership(
      sessionIdentifier: request.sessionIdentifier,
      requestIdentifier: request.requestIdentifier,
      canonicalInputDigest: canonicalInputDigest
    )
    context.ownershipVerified = true
    try requireExactRule(.apply)
    if grantAuthorized {
      try requireStandingGrant(peerUserID: context.peerUserID)
    } else {
      var authorizationData = Data(request.payload.dropFirst(2))
      try rules.validateAndDestroy(.apply, externalForm: &authorizationData)
    }
    let phase = try engine.apply(
      sessionIdentifier: request.sessionIdentifier,
      requestIdentifier: request.requestIdentifier,
      canonicalInputDigest: canonicalInputDigest,
      port: UInt16(port[port.startIndex]) << 8 | UInt16(port[port.startIndex + 1])
    )
    leaseDeadline = .now() + .seconds(15)
    return phase
  }

  private func performRestore() throws -> OwnershipPhase {
    let phase = try engine.restore()
    leaseDeadline = nil
    return phase
  }

  /// Disable ends with the helper unregistered, so it also takes back every standing grant; the
  /// Restore that ends a session never does.
  private func performDisable() throws -> OwnershipPhase {
    let phase = try performRestore()
    if phase == .idle {
      try removeGrantRecord()
    }
    return phase
  }

  private func performRemove() throws -> OwnershipPhase {
    let phase = try engine.restore()
    guard phase == .idle else {
      throw ProxyOwnershipFailure.recoveryRequired
    }
    try removeGrantRecord()
    try rules.remove(.apply)
    try rules.remove(.standingApply)
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

  func requireEmptyPayload(_ request: WireMessage) throws {
    guard request.payload.isEmpty else {
      throw ProxyOwnershipFailure.invalidInput
    }
  }

  private func reconcileUnknown(
    request: WireMessage,
    payload: WireReconcilePayload,
    context: inout RequestContext
  ) throws -> OwnershipPhase {
    switch payload.originalOperation {
    case .apply:
      context.ownershipVerified = try engine.verifyApplyOwnership(
        sessionIdentifier: request.sessionIdentifier,
        requestIdentifier: request.requestIdentifier,
        canonicalInputDigest: payload.canonicalInputDigest
      )
      return try engine.reconcile(
        sessionIdentifier: request.sessionIdentifier,
        requestIdentifier: request.requestIdentifier,
        canonicalInputDigest: payload.canonicalInputDigest
      )
    case .restore:
      try verifyCanonicalEmptyInput(payload)
      return try performRestore()
    case .disable:
      try verifyCanonicalEmptyInput(payload)
      return try performDisable()
    case .remove:
      try verifyCanonicalEmptyInput(payload)
      return try performRemove()
    case .enable, .repair:
      try verifyCanonicalEmptyInput(payload)
      let phase = try engine.reconcile()
      guard phase == .idle else {
        throw ProxyOwnershipFailure.recoveryRequired
      }
      try convergeRules(repair: payload.originalOperation == .repair)
      return .idle
    case .status:
      try verifyCanonicalEmptyInput(payload)
      try requireExactRule(.apply)
      return try engine.status()
    case .none, .reconcile, .renew, .selectApplications, .configureBrowserDomains,
      .configureApplications, .prepareGrant, .grant, .revokeGrant, .applyWithGrant:
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
    return grantResponse(for: error)
      ?? authorizationResponse(for: error)
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

  private func grantResponse(for error: Error) -> WireResponsePayload? {
    switch error {
    case StandingGrantFailure.unavailable:
      return failureResponse(.standingGrantUnavailable)
    case StandingGrantFailure.storage:
      return failureResponse(.storage)
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
    payload: Data,
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
        payload: payload
      )
    )
  }
}
