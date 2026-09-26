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

@Test func givenApplyWithGrantWhenDigestedThenItKeepsTheApplyIdentity() throws {
  let port = Data([0xC3, 0x51])
  let message = try WireMessage(
    kind: .request,
    operation: .applyWithGrant,
    sequence: 1,
    deadlineMilliseconds: 1_000,
    connectionIdentifier: Data(repeating: 1, count: 16),
    sessionIdentifier: Data(repeating: 2, count: 16),
    requestIdentifier: Data(repeating: 3, count: 16),
    payload: port
  )

  #expect(
    message.canonicalInputDigest == WireCodec.canonicalInputDigest(operation: .apply, payload: port)
  )
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
