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
