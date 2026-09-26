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

@Test func givenOwnedApplyWhenConfigureArrivesThenItIsRefusedAsInvalidInputWithoutAPort() throws {
  let response = try BrowserDomainRequestHandler.configureRefusedWhileAppliedResponse(
    serviceState: .ready)

  #expect(response.port == 0)
  #expect(response.outcome.outcome == .failure)
  #expect(response.outcome.ownershipPhase == .applied)
  #expect(response.outcome.failure == .invalidInput)
}

@Test func givenNoSessionWhenApplyArrivesThenItIsRefusedBeforeAuthorization() {
  let response = BrowserDomainRequestHandler.applyWithoutSessionResponse(serviceState: .ready)

  #expect(response.outcome == .failure)
  #expect(response.ownershipPhase == .idle)
  #expect(response.failure == .invalidInput)
}

@Test func givenApplyPortWhenCheckedThenOnlyTheSessionListenerPortIsAccepted() {
  let listenerPort = Data([0xC3, 0x51])

  #expect(
    BrowserDomainRequestHandler.applyTargetsSession(payload: listenerPort, sessionPort: 50_001))
  #expect(
    !BrowserDomainRequestHandler.applyTargetsSession(
      payload: Data([0xC3, 0x52]), sessionPort: 50_001))
  #expect(!BrowserDomainRequestHandler.applyTargetsSession(payload: listenerPort, sessionPort: nil))
  #expect(!BrowserDomainRequestHandler.applyTargetsSession(payload: Data([0, 0]), sessionPort: 0))
  #expect(
    !BrowserDomainRequestHandler.applyTargetsSession(payload: Data([0xC3]), sessionPort: 50_001))
}
