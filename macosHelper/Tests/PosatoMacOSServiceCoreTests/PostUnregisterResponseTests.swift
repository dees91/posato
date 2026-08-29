import Testing

@testable import PosatoMacOSServiceCore

@Test func givenSuccessfulUnregisterWhenResponseUpdatedThenPostServiceStateIsReported() {
  let response = WireResponsePayload(
    outcome: .success,
    serviceState: .ready,
    ownershipPhase: .idle
  )

  let updated = WireLifecyclePolicy.postUnregisterResponse(
    response,
    serviceState: .notRegistered
  )

  #expect(
    updated
      == WireResponsePayload(
        outcome: .success,
        serviceState: .notRegistered,
        ownershipPhase: .idle
      )
  )
}
