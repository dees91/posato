import Foundation
import PosatoMacOSServiceCore
import Testing

@testable import PosatoMacOSHelper

private enum RecoveryTestFailure: Error {
  case expected
}

private final class RecoveryHarness {
  var state: ServiceState
  var events: [String] = []
  var deadlineCalls = 0
  var deadlineFailureAt: Int?
  var registerThrows = false
  var connectThrows = false
  var finalThrows = false
  var registeredState: ServiceState = .ready
  var finalState: ServiceState?
  var finalResponse = WireResponsePayload(
    outcome: .success,
    serviceState: .ready,
    ownershipPhase: .idle
  )

  init(state: ServiceState = .ready) {
    self.state = state
  }

  func perform(operation: WireOperation = .repair) throws -> WireResponsePayload {
    return try performServiceRecovery(operation: operation, operations: operations())
  }

  private func operations() -> ServiceRecoveryOperations {
    return ServiceRecoveryOperations(
      remainingMilliseconds: remainingMilliseconds,
      currentServiceState: { self.state },
      invalidateDaemon: { self.events.append("invalidate") },
      register: register,
      connectDaemon: connectDaemon,
      performOriginalRequest: performOriginalRequest
    )
  }

  private func remainingMilliseconds() throws -> UInt32 {
    deadlineCalls += 1
    events.append("deadline\(deadlineCalls)")
    if let deadlineFailureAt, deadlineCalls >= deadlineFailureAt {
      throw WireProtocolFailure.invalidDeadline
    }
    return 1_000
  }

  private func register() throws {
    events.append("register")
    if registerThrows {
      throw RecoveryTestFailure.expected
    }
    state = registeredState
  }

  private func connectDaemon() throws {
    events.append("connect")
    if connectThrows {
      throw RecoveryTestFailure.expected
    }
  }

  private func performOriginalRequest(_: UInt32) throws -> WireResponsePayload {
    events.append("finalRequest")
    if finalThrows {
      throw RecoveryTestFailure.expected
    }
    if let finalState {
      state = finalState
    }
    return finalResponse
  }

  var lifecycleEvents: [String] {
    return events.filter { !$0.hasPrefix("deadline") }
  }
}

@Test func givenReadyServiceWhenRepairedThenRegistrationIsUnchanged() throws {
  let harness = RecoveryHarness()

  let response = try harness.perform()

  #expect(response.outcome == .success)
  #expect(response.ownershipPhase == .idle)
  #expect(harness.lifecycleEvents == ["connect", "finalRequest"])
  #expect(harness.deadlineCalls == 1)
}

@Test func givenMissingServiceWhenRecoveredThenCurrentServiceIsRegisteredOnce() throws {
  for operation in [WireOperation.repair, .disable, .remove] {
    for state in [ServiceState.notRegistered, .unavailableOrIncompatible] {
      let harness = RecoveryHarness(state: state)

      let response = try harness.perform(operation: operation)

      #expect(response.outcome == .success)
      #expect(
        harness.lifecycleEvents == [
          "invalidate",
          "register",
          "connect",
          "finalRequest",
        ]
      )
      #expect(harness.deadlineCalls == 3)
    }
  }
}

@Test func givenApprovalRequiredWhenRepairedThenNoTransitionStarts() throws {
  let harness = RecoveryHarness(state: .approvalRequired)

  let response = try harness.perform()

  #expect(response.outcome == .actionRequired)
  #expect(response.actionRequired == .backgroundApproval)
  #expect(harness.events.isEmpty)
}

@Test func givenRegistrationErrorWhenRepairedThenNoConnectionStarts() {
  let harness = RecoveryHarness(state: .notRegistered)
  harness.registerThrows = true

  #expect(throws: RecoveryTestFailure.expected) {
    try harness.perform()
  }
  #expect(harness.lifecycleEvents == ["invalidate", "register"])
}

@Test func givenRegistrationWithoutReadinessWhenRepairedThenSuccessIsRejected() throws {
  let harness = RecoveryHarness(state: .notRegistered)
  harness.registeredState = .approvalRequired

  let response = try harness.perform()

  #expect(response.outcome == .actionRequired)
  #expect(response.serviceState == .approvalRequired)
  #expect(harness.lifecycleEvents == ["invalidate", "register"])
}

@Test func givenConnectionErrorWhenRepairedThenRecoveryIsRequired() throws {
  let harness = RecoveryHarness()
  harness.connectThrows = true

  let response = try harness.perform()

  #expect(response.outcome == .actionRequired)
  #expect(response.serviceState == .recoveryRequired)
  #expect(harness.lifecycleEvents == ["connect"])
}

@Test func givenDeadlineExhaustionWhenRegisteringThenNoLaterTransitionStarts() {
  for failureAt in 1...3 {
    let harness = RecoveryHarness(state: .notRegistered)
    harness.deadlineFailureAt = failureAt

    #expect(throws: WireProtocolFailure.invalidDeadline) {
      try harness.perform()
    }
    switch failureAt {
    case 1:
      #expect(harness.lifecycleEvents == ["invalidate"])
    case 2:
      #expect(harness.lifecycleEvents == ["invalidate", "register"])
    default:
      #expect(
        harness.lifecycleEvents == ["invalidate", "register", "connect", "invalidate"]
      )
    }
  }
}

@Test func givenServiceLossAfterFinalRepairWhenVerifiedThenSuccessIsRejected() throws {
  let harness = RecoveryHarness()
  harness.finalState = .approvalRequired

  let response = try harness.perform()

  #expect(response.outcome == .actionRequired)
  #expect(response.serviceState == .approvalRequired)
  #expect(response.ownershipPhase == .recoveryRequired)
}

@Test func givenFinalTransportLossWhenRepairedThenConnectionIsInvalidated() {
  let harness = RecoveryHarness()
  harness.finalThrows = true

  #expect(throws: RecoveryTestFailure.expected) {
    try harness.perform()
  }
  #expect(harness.lifecycleEvents == ["connect", "finalRequest", "invalidate"])
}

@Test func givenIncompleteFinalCleanupWhenRepairedThenSuccessIsRejected() throws {
  let harness = RecoveryHarness()
  harness.finalResponse = WireResponsePayload(
    outcome: .success,
    serviceState: .ready,
    ownershipPhase: .recoveryRequired
  )

  let response = try harness.perform()

  #expect(response.outcome == .actionRequired)
  #expect(response.serviceState == .recoveryRequired)
  #expect(response.ownershipPhase == .recoveryRequired)
}

@Test func givenDaemonLossWhenSetupIsReconciledThenRecoveryIsRequiredWithoutUnknown() throws {
  for operation in [WireOperation.status, .enable] {
    let reconcile = try WireReconcilePayload(
      originalOperation: operation,
      canonicalInputDigest: WireCodec.canonicalInputDigest(
        operation: operation,
        payload: Data()
      )
    )

    let direct = setupDaemonUnavailableResponse(
      requestOperation: operation,
      reconcilePayload: nil
    )
    let reconciled = setupDaemonUnavailableResponse(
      requestOperation: .reconcile,
      reconcilePayload: reconcile
    )

    #expect(direct?.outcome == .actionRequired)
    #expect(direct?.serviceState == .recoveryRequired)
    #expect(direct?.actionRequired == .manualRecovery)
    #expect(reconciled?.serviceState == .recoveryRequired)
  }
}

@Test func givenInvalidFrameWhenSetupFailsThenRecoveryIsNotClaimed() throws {
  for operation in [WireOperation.status, .enable] {
    #expect(
      recoveredSetupPayload(
        requestOperation: operation,
        reconcilePayload: nil,
        error: PipeFailure.invalidFrame
      ) == nil
    )
  }
}

@Test func givenUnknownOutcomeWhenSetupFailsThenRecoveryIsNotClaimed() throws {
  for operation in [WireOperation.status, .enable] {
    #expect(
      recoveredSetupPayload(
        requestOperation: operation,
        reconcilePayload: nil,
        error: PipeFailure.unknownOutcome
      ) == nil
    )
  }
}

@Test func givenDaemonDeliveryErrorsWhenClassifiedThenOnlyInvalidConnectionsAreConclusive() throws {
  #expect(
    daemonDeliveryFailure(
      NSError(domain: NSCocoaErrorDomain, code: NSXPCConnectionInvalid)
    ) == .unavailable
  )
  #expect(
    daemonDeliveryFailure(
      NSError(domain: NSCocoaErrorDomain, code: NSXPCConnectionInterrupted)
    ) == .unknownOutcome
  )
  #expect(daemonDeliveryFailure(nil) == .unknownOutcome)
}

@Test func givenUnavailableWhenSetupFailsThenRecoveryIsRequired() throws {
  let payload = recoveredSetupPayload(
    requestOperation: .status,
    reconcilePayload: nil,
    error: PipeFailure.unavailable
  )

  #expect(payload?.outcome == .actionRequired)
  #expect(payload?.serviceState == .recoveryRequired)
  #expect(payload?.actionRequired == .manualRecovery)
}

@Test func givenDaemonLossWhenApplyIsReconciledThenSetupDoesNotClaimRecovery() throws {
  let reconcile = try WireReconcilePayload(
    originalOperation: .apply,
    canonicalInputDigest: WireCodec.canonicalInputDigest(
      operation: .apply,
      payload: Data([0, 1])
    )
  )

  #expect(
    setupDaemonUnavailableResponse(
      requestOperation: .apply,
      reconcilePayload: nil
    ) == nil
  )
  #expect(
    setupDaemonUnavailableResponse(
      requestOperation: .reconcile,
      reconcilePayload: reconcile
    ) == nil
  )
}

@Test func givenServiceRecoveryWhenClassifiedThenOnlySupportedOperationsUseWorkflow() throws {
  for operation in [WireOperation.repair, .disable, .remove] {
    let reconcile = try WireReconcilePayload(
      originalOperation: operation,
      canonicalInputDigest: WireCodec.canonicalInputDigest(
        operation: operation,
        payload: Data()
      )
    )

    #expect(
      serviceRecoveryOperation(
        requestOperation: .reconcile,
        reconcilePayload: reconcile
      ) == operation
    )
  }

  #expect(serviceRecoveryOperation(requestOperation: .repair, reconcilePayload: nil) == .repair)
  #expect(serviceRecoveryOperation(requestOperation: .disable, reconcilePayload: nil) == nil)
  #expect(serviceRecoveryOperation(requestOperation: .remove, reconcilePayload: nil) == nil)
  #expect(serviceRecoveryOperation(requestOperation: .enable, reconcilePayload: nil) == nil)
}

@Test func givenGrantPathsWhenCheckedLocallyThenForeignCodeAndForeignPortsAreRefused() throws {
  func request(_ operation: WireOperation, _ payload: Data) throws -> WireMessage {
    return try WireMessage(
      kind: .request,
      operation: operation,
      sequence: 2,
      deadlineMilliseconds: 1_000,
      connectionIdentifier: Data(repeating: 1, count: 16),
      sessionIdentifier: Data(repeating: 2, count: 16),
      requestIdentifier: Data(repeating: 3, count: 16),
      payload: payload
    )
  }
  func refusal(_ message: WireMessage, clean: Bool) -> FailureCategory? {
    return localAuthorizationRefusal(
      request: message,
      sessionPort: 50_001,
      launchEnvironmentIsClean: clean,
      serviceState: .ready
    )?.failure
  }
  let ownPort = Data([0xC3, 0x51])
  let foreignPort = Data([0xC3, 0x52])

  #expect(refusal(try request(.grant, Data()), clean: false) == .standingGrantUnavailable)
  #expect(refusal(try request(.applyWithGrant, ownPort), clean: false) == .standingGrantUnavailable)
  #expect(refusal(try request(.apply, ownPort), clean: false) == nil)
  #expect(refusal(try request(.applyWithGrant, ownPort), clean: true) == nil)
  #expect(refusal(try request(.apply, foreignPort), clean: true) == .invalidInput)
  #expect(refusal(try request(.revokeGrant, Data()), clean: false) == nil)
}
