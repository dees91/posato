import Foundation
import PosatoMacOSServiceCore
import Testing

@testable import PosatoMacOSHelper

@Test func givenRestoredIdleWhenEffectiveChainFailsThenApplyReportsIncompatible() {
  let restored = WireResponsePayload(outcome: .success, serviceState: .ready, ownershipPhase: .idle)

  let response = BrowserDomainRequestHandler.effectiveChainFailureResponse(restored: restored)

  #expect(response.outcome == .actionRequired)
  #expect(response.serviceState == .unavailableOrIncompatible)
  #expect(response.ownershipPhase == .idle)
  #expect(response.actionRequired == .incompatible)
  #expect(response.failure == .unavailable)
}

@Test func givenMissingRestoreWhenEffectiveChainFailsThenApplyReportsRecoveryRequired() {
  let response = BrowserDomainRequestHandler.effectiveChainFailureResponse(restored: nil)

  #expect(response.outcome == .actionRequired)
  #expect(response.serviceState == .recoveryRequired)
  #expect(response.ownershipPhase == .recoveryRequired)
  #expect(response.actionRequired == .manualRecovery)
}

@Test func givenRestoreConflictWhenEffectiveChainFailsThenThePhaseIsPreserved() {
  let restored = WireResponsePayload(
    outcome: .actionRequired, serviceState: .recoveryRequired, ownershipPhase: .recoveryRequired)

  let response = BrowserDomainRequestHandler.effectiveChainFailureResponse(restored: restored)

  #expect(response.outcome == .actionRequired)
  #expect(response.ownershipPhase == .recoveryRequired)
  #expect(response.actionRequired == .manualRecovery)
}
