import Testing

@testable import PosatoMacOSHelper

@Test func givenVPNOrManagedProxyWhenReadThenActivationIsRefused() {
  let vpn = NetworkCompatibility(
    reader: StubReader(
      NetworkCompatibilityFacts(
        managedProxyOverlay: false,
        vpnOrRelayActive: true,
        pathUnsatisfied: false
      )))
  let managed = NetworkCompatibility(
    reader: StubReader(
      NetworkCompatibilityFacts(
        managedProxyOverlay: true,
        vpnOrRelayActive: false,
        pathUnsatisfied: false
      )))
  let unsatisfied = NetworkCompatibility(
    reader: StubReader(
      NetworkCompatibilityFacts(
        managedProxyOverlay: false,
        vpnOrRelayActive: false,
        pathUnsatisfied: true
      )))
  let clear = NetworkCompatibility(
    reader: StubReader(
      NetworkCompatibilityFacts(
        managedProxyOverlay: false,
        vpnOrRelayActive: false,
        pathUnsatisfied: false
      )))

  #expect(!vpn.activationIsAllowed())
  #expect(!managed.activationIsAllowed())
  #expect(!unsatisfied.activationIsAllowed())
  #expect(clear.activationIsAllowed())
}

private struct StubReader: NetworkCompatibilityReading {
  let stored: NetworkCompatibilityFacts

  init(_ facts: NetworkCompatibilityFacts) {
    stored = facts
  }

  func facts() -> NetworkCompatibilityFacts {
    return stored
  }
}
