import PosatoMacOSServiceCore
import ServiceManagement
import Testing

@testable import PosatoMacOSHelper

@Test func givenServiceManagementNotFoundWhenMappedThenSetupTreatsItAsNotRegistered() {
  #expect(serviceState(.notFound) == .notRegistered)
  #expect(serviceState(.notRegistered) == .notRegistered)
  #expect(serviceState(.enabled) == .ready)
  #expect(serviceState(.requiresApproval) == .approvalRequired)
}

@Test func givenEnableOutcomesWhenAnsweredThenOnlyAFailedRegistrationIsAFailure() {
  let failed = enableOutcomePayload(serviceState: .notRegistered, registrationFailed: true)
  #expect(failed.outcome == .failure)
  #expect(failed.serviceState == .notRegistered)
  #expect(failed.actionRequired == .manualRecovery)

  let neverRegistered = enableOutcomePayload(
    serviceState: .notRegistered,
    registrationFailed: false
  )
  #expect(neverRegistered.outcome == .actionRequired)
  #expect(neverRegistered.serviceState == .notRegistered)

  for state in [ServiceState.approvalRequired, .recoveryRequired, .unavailableOrIncompatible] {
    let payload = enableOutcomePayload(serviceState: state, registrationFailed: true)
    #expect(payload.outcome == .actionRequired)
  }
}
