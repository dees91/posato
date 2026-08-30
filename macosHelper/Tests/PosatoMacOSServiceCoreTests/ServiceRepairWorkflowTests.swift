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
