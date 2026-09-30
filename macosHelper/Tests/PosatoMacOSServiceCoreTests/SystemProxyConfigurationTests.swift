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
    https: applied,
    exceptions: .set(["localhost", "*.invalid", "127.0.0.1", "::1"])
  )
  let restored = SystemProxyConfiguration.replacingTuples(
    in: resulting,
    http: emptyTuple,
    https: emptyTuple,
    exceptions: .remove
  )

  #expect(
    resulting["ExceptionsList"] as? [String] == ["localhost", "*.invalid", "127.0.0.1", "::1"])
  #expect(restored["ExceptionsList"] == nil)
  #expect(restored["NestedSyntheticValue"] != nil)
  #expect(
    NSDictionary(dictionary: resulting["NestedSyntheticValue"] as? [String: Any] ?? [:])
      .isEqual(to: ["Mode": "preserve", "Enabled": true])
  )
  #expect(SystemProxyConfiguration.dictionariesEqual(resulting, resulting))
  var altered = resulting
  altered["NestedSyntheticValue"] = ["Mode": "changed", "Enabled": true]
  #expect(!SystemProxyConfiguration.dictionariesEqual(resulting, altered))
}

// D4 bounds: a value outside them must read as unreadable, never throw (R4) and never pass as a list.
@Test func givenExceptionsValuesWhenReadThenOnlyBoundedStringArraysAreLists() {
  let longest = String(repeating: "a", count: ProxyExceptions.maximumEntryBytes)
  let bounded = Array(repeating: "a.example", count: ProxyExceptions.maximumReadableEntries)

  #expect(SystemProxyConfiguration.exceptions(nil) == .absent)
  #expect(SystemProxyConfiguration.exceptions([longest]) == .list([longest]))
  #expect(SystemProxyConfiguration.exceptions(bounded) == .list(bounded))
  for value: Any in [[longest + "a"], bounded + ["b.example"], ["a.example", 7], "a.example"] {
    guard case .unreadable = SystemProxyConfiguration.exceptions(value) else {
      Issue.record("\(value) must be unreadable")
      continue
    }
  }
  #expect(
    SystemProxyConfiguration.exceptions(["a", 1]) != SystemProxyConfiguration.exceptions(["a", 2]))
}
