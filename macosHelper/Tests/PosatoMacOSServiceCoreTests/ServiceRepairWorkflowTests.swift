import Foundation
import PosatoMacOSServiceCore
import Testing

@testable import PosatoMacOSHelper

private enum RepairTestFailure: Error {
  case expected
}

private final class RepairHarness {
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

  func operations() -> ServiceRepairOperations {
    return ServiceRepairOperations(
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
      throw RepairTestFailure.expected
    }
    state = registeredState
  }

  private func connectDaemon() throws {
    events.append("connect")
    if connectThrows {
      throw RepairTestFailure.expected
    }
  }

  private func performOriginalRequest(_: UInt32) throws -> WireResponsePayload {
    events.append("finalRequest")
    if finalThrows {
      throw RepairTestFailure.expected
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
  let harness = RepairHarness()

  let response = try performServiceRepair(operations: harness.operations())

  #expect(response.outcome == .success)
  #expect(response.ownershipPhase == .idle)
  #expect(harness.lifecycleEvents == ["connect", "finalRequest"])
  #expect(harness.deadlineCalls == 1)
}

@Test func givenMissingServiceWhenRepairedThenCurrentServiceIsRegisteredOnce() throws {
  for state in [ServiceState.notRegistered, .unavailableOrIncompatible] {
    let harness = RepairHarness(state: state)

    let response = try performServiceRepair(operations: harness.operations())

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

@Test func givenApprovalRequiredWhenRepairedThenNoTransitionStarts() throws {
  let harness = RepairHarness(state: .approvalRequired)

  let response = try performServiceRepair(operations: harness.operations())

  #expect(response.outcome == .actionRequired)
  #expect(response.actionRequired == .backgroundApproval)
  #expect(harness.events.isEmpty)
}

@Test func givenRegistrationErrorWhenRepairedThenNoConnectionStarts() {
  let harness = RepairHarness(state: .notRegistered)
  harness.registerThrows = true

  #expect(throws: RepairTestFailure.expected) {
    try performServiceRepair(operations: harness.operations())
  }
  #expect(harness.lifecycleEvents == ["invalidate", "register"])
}

@Test func givenRegistrationWithoutReadinessWhenRepairedThenSuccessIsRejected() throws {
  let harness = RepairHarness(state: .notRegistered)
  harness.registeredState = .approvalRequired

  let response = try performServiceRepair(operations: harness.operations())

  #expect(response.outcome == .actionRequired)
  #expect(response.serviceState == .approvalRequired)
  #expect(harness.lifecycleEvents == ["invalidate", "register"])
}

@Test func givenConnectionErrorWhenRepairedThenRecoveryIsRequired() throws {
  let harness = RepairHarness()
  harness.connectThrows = true

  let response = try performServiceRepair(operations: harness.operations())

  #expect(response.outcome == .actionRequired)
  #expect(response.serviceState == .recoveryRequired)
  #expect(harness.lifecycleEvents == ["connect"])
}

@Test func givenDeadlineExhaustionWhenRegisteringThenNoLaterTransitionStarts() {
  for failureAt in 1...3 {
    let harness = RepairHarness(state: .notRegistered)
    harness.deadlineFailureAt = failureAt

    #expect(throws: WireProtocolFailure.invalidDeadline) {
      try performServiceRepair(operations: harness.operations())
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
  let harness = RepairHarness()
  harness.finalState = .approvalRequired

  let response = try performServiceRepair(operations: harness.operations())

  #expect(response.outcome == .actionRequired)
  #expect(response.serviceState == .approvalRequired)
  #expect(response.ownershipPhase == .recoveryRequired)
}

@Test func givenFinalTransportLossWhenRepairedThenConnectionIsInvalidated() {
  let harness = RepairHarness()
  harness.finalThrows = true

  #expect(throws: RepairTestFailure.expected) {
    try performServiceRepair(operations: harness.operations())
  }
  #expect(harness.lifecycleEvents == ["connect", "finalRequest", "invalidate"])
}

@Test func givenIncompleteFinalCleanupWhenRepairedThenSuccessIsRejected() throws {
  let harness = RepairHarness()
  harness.finalResponse = WireResponsePayload(
    outcome: .success,
    serviceState: .ready,
    ownershipPhase: .recoveryRequired
  )

  let response = try performServiceRepair(operations: harness.operations())

  #expect(response.outcome == .actionRequired)
  #expect(response.serviceState == .recoveryRequired)
  #expect(response.ownershipPhase == .recoveryRequired)
}

@Test func givenDirectOrReconciledRepairWhenClassifiedThenBothUseServiceWorkflow() throws {
  let reconcile = try WireReconcilePayload(
    originalOperation: .repair,
    canonicalInputDigest: WireCodec.canonicalInputDigest(
      operation: .repair,
      payload: Data()
    )
  )

  #expect(isServiceRepair(requestOperation: .repair, reconcilePayload: nil))
  #expect(isServiceRepair(requestOperation: .reconcile, reconcilePayload: reconcile))
  #expect(!isServiceRepair(requestOperation: .enable, reconcilePayload: nil))
}
