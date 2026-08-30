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
  var restoreResponse = WireResponsePayload(
    outcome: .success,
    serviceState: .ready,
    ownershipPhase: .idle
  )
  var restoreThrows = false
  var unregisterThrows = false
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
      restoreExistingDaemon: restoreExistingDaemon,
      ownershipRestored: { self.events.append("clearOwnership") },
      invalidateDaemon: { self.events.append("invalidate") },
      unregister: unregister,
      register: register,
      connectFreshDaemon: connectFreshDaemon,
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

  private func restoreExistingDaemon(_: UInt32) throws -> WireResponsePayload {
    events.append("restore")
    if restoreThrows {
      throw RepairTestFailure.expected
    }
    return restoreResponse
  }

  private func unregister(_: UInt32) throws {
    events.append("unregister")
    if unregisterThrows {
      throw RepairTestFailure.expected
    }
    state = .notRegistered
  }

  private func register() throws {
    events.append("register")
    if registerThrows {
      throw RepairTestFailure.expected
    }
    state = registeredState
  }

  private func connectFreshDaemon() throws {
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

@Test func givenEnabledServiceWhenRepairedThenCleanupPrecedesServiceCycle() throws {
  let harness = RepairHarness()

  let response = try performServiceRepair(operations: harness.operations())

  #expect(response.outcome == .success)
  #expect(response.ownershipPhase == .idle)
  #expect(
    harness.lifecycleEvents == [
      "restore",
      "clearOwnership",
      "invalidate",
      "unregister",
      "register",
      "connect",
      "finalRequest",
    ]
  )
  #expect(harness.deadlineCalls == 7)
}

@Test func givenUnknownOldRestoreWhenRepairedThenOwnershipRemainsUntilFreshReconciliation() throws {
  let harness = RepairHarness()
  harness.restoreThrows = true

  let response = try performServiceRepair(operations: harness.operations())

  #expect(response.outcome == .success)
  #expect(!harness.lifecycleEvents.contains("clearOwnership"))
  #expect(
    harness.lifecycleEvents == [
      "restore",
      "invalidate",
      "unregister",
      "register",
      "connect",
      "finalRequest",
    ]
  )
}

@Test func givenIncompleteOldCleanupWhenRepairedThenRegistrationIsUntouched() throws {
  let harness = RepairHarness()
  harness.restoreResponse = WireResponsePayload(
    outcome: .actionRequired,
    serviceState: .recoveryRequired,
    ownershipPhase: .recoveryRequired,
    actionRequired: .proxyRecovery,
    failure: .integrity
  )

  let response = try performServiceRepair(operations: harness.operations())

  #expect(response.outcome == .actionRequired)
  #expect(harness.lifecycleEvents == ["restore"])
}

@Test func givenMissingServiceWhenRepairedThenOnlyCurrentServiceIsRegistered() throws {
  let harness = RepairHarness(state: .notRegistered)

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
}

@Test func givenApprovalRequiredWhenRepairedThenNoTransitionStarts() throws {
  let harness = RepairHarness(state: .approvalRequired)

  let response = try performServiceRepair(operations: harness.operations())

  #expect(response.outcome == .actionRequired)
  #expect(response.actionRequired == .backgroundApproval)
  #expect(harness.events.isEmpty)
}

@Test func givenUnregisterErrorWhenRepairedThenRegistrationDoesNotStart() throws {
  let harness = RepairHarness()
  harness.unregisterThrows = true

  let response = try performServiceRepair(operations: harness.operations())

  #expect(response.outcome == .actionRequired)
  #expect(!harness.lifecycleEvents.contains("register"))
  #expect(!harness.lifecycleEvents.contains("connect"))
}

@Test func givenRegistrationErrorWhenRepairedThenUnknownOutcomeCanBeReconciled() {
  let harness = RepairHarness()
  harness.registerThrows = true

  #expect(throws: RepairTestFailure.expected) {
    try performServiceRepair(operations: harness.operations())
  }
  #expect(!harness.lifecycleEvents.contains("connect"))
}

@Test func givenDeadlineExhaustionWhenRepairedThenNoLaterTransitionStarts() {
  let forbiddenEvents: [String] = [
    "restore",
    "unregister",
    "register",
    "register",
    "connect",
    "finalRequest",
    "finalRequest",
  ]

  for failureAt in 1...7 {
    let harness = RepairHarness()
    harness.deadlineFailureAt = failureAt

    #expect(throws: WireProtocolFailure.invalidDeadline) {
      try performServiceRepair(operations: harness.operations())
    }
    #expect(!harness.events.contains(forbiddenEvents[failureAt - 1]))
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

@Test func givenFinalTransportLossWhenRepairedThenFreshConnectionIsInvalidated() {
  let harness = RepairHarness()
  harness.finalThrows = true

  #expect(throws: RepairTestFailure.expected) {
    try performServiceRepair(operations: harness.operations())
  }
  #expect(harness.lifecycleEvents.suffix(2) == ["finalRequest", "invalidate"])
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
