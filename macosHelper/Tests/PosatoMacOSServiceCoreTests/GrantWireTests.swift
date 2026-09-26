import Foundation
import Testing

@testable import PosatoMacOSServiceCore

@Test func givenApplyWithGrantWhenLifecycleEvaluatedThenItIsTreatedAsApply() {
  let applied = WireResponsePayload(
    outcome: .success, serviceState: .ready, ownershipPhase: .applied)
  let postEffectFailure = WireResponsePayload(
    outcome: .actionRequired,
    serviceState: .recoveryRequired,
    ownershipPhase: .applied,
    actionRequired: .manualRecovery,
    failure: .integrity
  )

  #expect(
    WireLifecyclePolicy.ownsAppliedMutation(
      requestOperation: .applyWithGrant,
      reconcilePayload: nil,
      ownershipVerified: true,
      response: applied
    )
  )
  #expect(
    WireLifecyclePolicy.failClosedApplyResponse(
      requestOperation: .applyWithGrant,
      reconcilePayload: nil,
      ownershipVerified: false,
      response: postEffectFailure
    ).outcome == .conflict
  )
  #expect(
    WireLifecyclePolicy.needsEffectiveChainCheck(
      requestOperation: .applyWithGrant, response: applied))
  #expect(
    !WireLifecyclePolicy.needsEffectiveChainCheck(requestOperation: .status, response: applied))
}

@Test func givenStatusReplyWhenDecodedThenOnlyAnOptionalGrantByteFollowsTheFiveBytes() throws {
  let base = WireResponsePayload(outcome: .success, serviceState: .ready, ownershipPhase: .idle)
  var withGrant = base.encode()
  withGrant.append(0b11)

  let plain = try WireResponsePayload.decodeStatus(base.encode())
  let granted = try WireResponsePayload.decodeStatus(withGrant)

  #expect(plain.response == base && plain.grantState == nil)
  #expect(
    granted.response == base
      && granted.grantState == WireGrantState([.standingRightExact, .granted]))
  #expect(throws: WireProtocolFailure.invalidFrame) {
    try WireResponsePayload.decodeStatus(withGrant + Data([0]))
  }
}
