import Foundation
import Testing

@testable import PosatoMacOSServiceCore

@Test func givenAdditionalProxyFlagWhenCheckedThenEnabledStateIsDetected() throws {
  for key in ["SOCKSEnable", "ProxyAutoConfigEnable", "ProxyAutoDiscoveryEnable"] {
    #expect(
      try SystemProxyConfiguration.additionalProxyEnabled(
        values: [key: NSNumber(value: 1)]
      )
    )
  }
  #expect(
    try !SystemProxyConfiguration.additionalProxyEnabled(
      values: [
        "SOCKSEnable": NSNumber(value: 0),
        "ProxyAutoConfigEnable": NSNumber(value: 0),
        "ProxyAutoDiscoveryEnable": NSNumber(value: 0),
      ]
    )
  )
}

@Test func givenUnrelatedProxyValuesWhenTuplesChangeThenTheyRemainExact() {
  let original: [String: Any] = [
    "HTTPEnable": NSNumber(value: 0),
    "HTTPProxy": "baseline.invalid",
    "HTTPPort": NSNumber(value: 8080),
    "HTTPSEnable": NSNumber(value: 0),
    "HTTPSProxy": "baseline.invalid",
    "HTTPSPort": NSNumber(value: 8443),
    "ExceptionsList": ["localhost", "*.invalid"],
    "NestedSyntheticValue": ["Mode": "preserve", "Enabled": true],
  ]
  let applied = ProxyTuple(
    enabled: .integer(1),
    host: .string("127.0.0.1"),
    port: .integer(17_769)
  )

  let resulting = SystemProxyConfiguration.replacingTuples(
    in: original,
    http: applied,
    https: applied
  )

  #expect(resulting["ExceptionsList"] as? [String] == ["localhost", "*.invalid"])
  #expect(
    NSDictionary(dictionary: resulting["NestedSyntheticValue"] as? [String: Any] ?? [:])
      .isEqual(to: ["Mode": "preserve", "Enabled": true])
  )
  #expect(SystemProxyConfiguration.dictionariesEqual(resulting, resulting))
  var altered = resulting
  altered["NestedSyntheticValue"] = ["Mode": "changed", "Enabled": true]
  #expect(!SystemProxyConfiguration.dictionariesEqual(resulting, altered))
}
