import Foundation
import Testing

@testable import PosatoMacOSServiceCore

@Test func givenReplayOrGapWhenSequenceValidatedThenItIsRejected() {
  var validator = WireSequenceValidator()
  let first = validator.accept(1)
  let replay = validator.accept(1)
  let gap = validator.accept(3)
  let second = validator.accept(2)

  #expect(first)
  #expect(!replay)
  #expect(!gap)
  #expect(second)
}

@Test func givenElapsedBudgetWhenDeadlineCalculatedThenOnlyRemainingTimeIsReturned() throws {
  #expect(
    try WireDeadline.remainingMilliseconds(
      receivedUptimeNanoseconds: 1_000_000_000,
      currentUptimeNanoseconds: 1_025_000_000,
      budgetMilliseconds: 100
    ) == 75
  )
  #expect(throws: WireProtocolFailure.invalidDeadline) {
    try WireDeadline.remainingMilliseconds(
      receivedUptimeNanoseconds: 1_000_000_000,
      currentUptimeNanoseconds: 1_100_000_000,
      budgetMilliseconds: 100
    )
  }
}

@Test func givenFailedCleanupWhenLifecycleEvaluatedThenOwnershipAndRegistrationRemain() {
  let failed = WireResponsePayload(
    outcome: .actionRequired,
    serviceState: .recoveryRequired,
    ownershipPhase: .recoveryRequired,
    actionRequired: .proxyRecovery,
    failure: .integrity
  )

  #expect(
    !WireLifecyclePolicy.completesCleanup(
      requestOperation: .disable,
      reconcilePayload: nil,
      response: failed
    )
  )
  #expect(
    !WireLifecyclePolicy.shouldUnregister(
      requestOperation: .remove,
      reconcilePayload: nil,
      response: failed
    )
  )
}

@Test func givenSuccessfulReconciledRemoveWhenEvaluatedThenUnregisterIsAllowed() throws {
  let reconcile = try WireReconcilePayload(
    originalOperation: .remove,
    canonicalInputDigest: WireCodec.canonicalInputDigest(
      operation: .remove,
      payload: Data()
    )
  )
  let success = WireResponsePayload(
    outcome: .success,
    serviceState: .ready,
    ownershipPhase: .idle
  )

  #expect(
    WireLifecyclePolicy.shouldUnregister(
      requestOperation: .reconcile,
      reconcilePayload: reconcile,
      response: success
    )
  )
}

@Test func givenReconciledApplyWhenAppliedThenConnectionOwnsTheMutation() throws {
  let reconcile = try WireReconcilePayload(
    originalOperation: .apply,
    canonicalInputDigest: Data(repeating: 1, count: 32)
  )
  let applied = WireResponsePayload(
    outcome: .success,
    serviceState: .ready,
    ownershipPhase: .applied
  )

  #expect(
    WireLifecyclePolicy.ownsAppliedMutation(
      requestOperation: .reconcile,
      reconcilePayload: reconcile,
      ownershipVerified: true,
      response: applied
    )
  )
}

@Test func givenApplyWithUnknownPreparedEffectThenConnectionKeepsCleanupOwnership() {
  let response = WireResponsePayload(
    outcome: .failure,
    serviceState: .recoveryRequired,
    ownershipPhase: .prepared,
    actionRequired: .manualRecovery,
    failure: .unavailable
  )

  #expect(
    WireLifecyclePolicy.ownsAppliedMutation(
      requestOperation: .apply,
      reconcilePayload: nil,
      ownershipVerified: true,
      response: response
    )
  )
}

@Test func givenApplyFailureWithVerifiedAppliedStateThenConnectionKeepsCleanupOwnership() {
  let response = WireResponsePayload(
    outcome: .actionRequired,
    serviceState: .recoveryRequired,
    ownershipPhase: .applied,
    actionRequired: .manualRecovery,
    failure: .storage
  )

  #expect(
    WireLifecyclePolicy.ownsAppliedMutation(
      requestOperation: .apply,
      reconcilePayload: nil,
      ownershipVerified: true,
      response: response
    )
  )
}

@Test func givenUnverifiedApplyFailureWithForeignPreparedStateThenConnectionOwnsNothing() {
  let response = WireResponsePayload(
    outcome: .failure,
    serviceState: .recoveryRequired,
    ownershipPhase: .prepared,
    actionRequired: .manualRecovery,
    failure: .invalidInput
  )

  #expect(
    !WireLifecyclePolicy.ownsAppliedMutation(
      requestOperation: .apply,
      reconcilePayload: nil,
      ownershipVerified: false,
      response: response
    )
  )
}

@Test func givenOwnershipPhaseWhenLifecycleEvaluatedThenOnlyIdleCompletesCleanup() {
  #expect(WireLifecyclePolicy.cleanupCompleted(.idle))
  #expect(!WireLifecyclePolicy.cleanupCompleted(.applied))
  #expect(!WireLifecyclePolicy.cleanupCompleted(.recoveryRequired))
  #expect(!WireLifecyclePolicy.cleanupCompleted(nil))
}

@Test func givenMaintenancePhaseWhenLifecycleEvaluatedThenOnlyAppliedKeepsLeaseHealthy() {
  #expect(WireLifecyclePolicy.leaseRemainsHealthy(afterMaintenance: .applied))
  #expect(!WireLifecyclePolicy.leaseRemainsHealthy(afterMaintenance: .idle))
  #expect(!WireLifecyclePolicy.leaseRemainsHealthy(afterMaintenance: .restorePending))
  #expect(!WireLifecyclePolicy.leaseRemainsHealthy(afterMaintenance: nil))
}

@Test func givenRenewalResponseWhenLifecycleEvaluatedThenOnlySuccessfulAppliedKeepsLeaseHealthy() {
  let successfulApplied = WireResponsePayload(
    outcome: .success,
    serviceState: .ready,
    ownershipPhase: .applied
  )
  let conflictingApplied = WireResponsePayload(
    outcome: .conflict,
    serviceState: .recoveryRequired,
    ownershipPhase: .applied
  )
  let actionRequiredApplied = WireResponsePayload(
    outcome: .actionRequired,
    serviceState: .recoveryRequired,
    ownershipPhase: .applied
  )
  let successfulIdle = WireResponsePayload(
    outcome: .success,
    serviceState: .ready,
    ownershipPhase: .idle
  )

  #expect(WireLifecyclePolicy.keepsLeaseHealthy(afterRenewal: successfulApplied))
  #expect(!WireLifecyclePolicy.keepsLeaseHealthy(afterRenewal: conflictingApplied))
  #expect(!WireLifecyclePolicy.keepsLeaseHealthy(afterRenewal: actionRequiredApplied))
  #expect(!WireLifecyclePolicy.keepsLeaseHealthy(afterRenewal: successfulIdle))
}

@Test func givenUnverifiedApplyResponseWhenFailedClosedThenConflictPreservesDetails() throws {
  let reconcile = try WireReconcilePayload(
    originalOperation: .apply,
    canonicalInputDigest: Data(repeating: 1, count: 32)
  )
  let postEffectFailure = WireResponsePayload(
    outcome: .actionRequired,
    serviceState: .recoveryRequired,
    ownershipPhase: .applied,
    actionRequired: .manualRecovery,
    failure: .storage
  )
  let unverifiedDirect = WireLifecyclePolicy.failClosedApplyResponse(
    requestOperation: .apply,
    reconcilePayload: nil,
    ownershipVerified: false,
    response: postEffectFailure
  )
  let unverifiedReconcile = WireLifecyclePolicy.failClosedApplyResponse(
    requestOperation: .reconcile,
    reconcilePayload: reconcile,
    ownershipVerified: false,
    response: postEffectFailure
  )
  let idle = WireResponsePayload(
    outcome: .success,
    serviceState: .ready,
    ownershipPhase: .idle
  )
  let unverifiedIdle = WireLifecyclePolicy.failClosedApplyResponse(
    requestOperation: .reconcile,
    reconcilePayload: reconcile,
    ownershipVerified: false,
    response: idle
  )

  #expect(unverifiedDirect.outcome == .conflict)
  #expect(unverifiedDirect.serviceState == postEffectFailure.serviceState)
  #expect(unverifiedDirect.ownershipPhase == postEffectFailure.ownershipPhase)
  #expect(unverifiedDirect.actionRequired == postEffectFailure.actionRequired)
  #expect(unverifiedDirect.failure == postEffectFailure.failure)
  #expect(unverifiedReconcile.outcome == .conflict)
  #expect(unverifiedIdle == idle)
}

@Test func givenFailClosedResponseThenOnlyVerifiedApplyIsOwned() throws {
  let reconcile = try WireReconcilePayload(
    originalOperation: .apply,
    canonicalInputDigest: Data(repeating: 1, count: 32)
  )
  let postEffectFailure = WireResponsePayload(
    outcome: .actionRequired,
    serviceState: .recoveryRequired,
    ownershipPhase: .applied,
    actionRequired: .manualRecovery,
    failure: .storage
  )
  let unverifiedDirect = WireLifecyclePolicy.failClosedApplyResponse(
    requestOperation: .apply,
    reconcilePayload: nil,
    ownershipVerified: false,
    response: postEffectFailure
  )
  let unverifiedReconcile = WireLifecyclePolicy.failClosedApplyResponse(
    requestOperation: .reconcile,
    reconcilePayload: reconcile,
    ownershipVerified: false,
    response: postEffectFailure
  )

  #expect(
    !WireLifecyclePolicy.ownsAppliedMutation(
      requestOperation: .apply,
      reconcilePayload: nil,
      ownershipVerified: true,
      response: unverifiedDirect
    )
  )
  #expect(
    !WireLifecyclePolicy.ownsAppliedMutation(
      requestOperation: .reconcile,
      reconcilePayload: reconcile,
      ownershipVerified: true,
      response: unverifiedReconcile
    )
  )
  #expect(
    WireLifecyclePolicy.ownsAppliedMutation(
      requestOperation: .apply,
      reconcilePayload: nil,
      ownershipVerified: true,
      response: postEffectFailure
    )
  )
}

@Test func givenUnavailableDaemonWhenRespondingThenCleanupIsNotClaimed() {
  let approval = WireLifecyclePolicy.unreconciledServiceResponse(
    serviceState: .approvalRequired
  )
  let unavailable = WireLifecyclePolicy.unreconciledServiceResponse(
    serviceState: .notRegistered
  )

  #expect(approval.outcome == .actionRequired)
  #expect(approval.ownershipPhase == .recoveryRequired)
  #expect(approval.actionRequired == .backgroundApproval)
  #expect(approval.failure == .lifecycle)
  #expect(unavailable.outcome == .actionRequired)
  #expect(unavailable.serviceState == .notRegistered)
  #expect(unavailable.ownershipPhase == .recoveryRequired)
  #expect(unavailable.actionRequired == .manualRecovery)
  #expect(unavailable.failure == .lifecycle)
}
