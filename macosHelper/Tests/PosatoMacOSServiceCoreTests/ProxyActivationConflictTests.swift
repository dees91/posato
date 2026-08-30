import Foundation
import Testing

@testable import PosatoMacOSServiceCore

@Test func givenExistingEnabledProxyWhenAppliedThenOwnershipAndConfigurationRemainUntouched() {
  let enabled = ProxyTuple(enabled: .integer(1), host: nil, port: nil)
  let configurations = [
    MemoryProxyConfiguration(http: enabled),
    MemoryProxyConfiguration(https: enabled),
    MemoryProxyConfiguration(additionalProxyEnabled: true),
  ]

  for configuration in configurations {
    let persistence = MemoryOwnershipPersistence()
    let baseline = configuration.snapshotValue
    let engine = ProxyOwnershipEngine(
      persistence: persistence,
      configuration: configuration
    )

    #expect(throws: ProxyOwnershipFailure.unavailable) {
      try engine.apply(
        sessionIdentifier: Data(repeating: 1, count: WireLimits.identifierBytes),
        requestIdentifier: Data(repeating: 2, count: WireLimits.identifierBytes),
        canonicalInputDigest: Data(repeating: 3, count: 32),
        port: 17_769
      )
    }
    #expect(persistence.record == nil)
    #expect(configuration.snapshotValue == baseline)
  }
}
