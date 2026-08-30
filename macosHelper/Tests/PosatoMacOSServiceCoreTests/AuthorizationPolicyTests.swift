import Foundation
import Testing

@testable import PosatoMacOSServiceCore
@testable import PosatoProxySettingsDaemon

@Test func givenExpectedAuthorizationRuleWhenValidatedThenItMatchesExactly() {
  let definition = AuthorizationPolicy.applyRightDefinition()

  #expect(AuthorizationPolicy.hasExpectedApplyRightDefinition(definition))
  #expect(definition["timeout"] as? Int == AuthorizationPolicy.applyRightTimeoutSeconds)
}

@Test func givenSharedOrRootAuthorizationRuleWhenValidatedThenItIsRejected() {
  var shared = AuthorizationPolicy.applyRightDefinition()
  shared["shared"] = true
  var rootAllowed = AuthorizationPolicy.applyRightDefinition()
  rootAllowed["allow-root"] = true

  #expect(!AuthorizationPolicy.hasExpectedApplyRightDefinition(shared))
  #expect(!AuthorizationPolicy.hasExpectedApplyRightDefinition(rootAllowed))
}

@Test func givenInvalidExternalFormWhenValidatedThenOwnedBytesAreZeroed() {
  var material = Data(repeating: 0xA5, count: 7)

  #expect(throws: AuthorizationPolicyFailure.invalidExternalForm) {
    try AuthorizationPolicy.validateAndDestroyApplyExternalForm(&material)
  }
  #expect(material.allSatisfy { $0 == 0 })
}

@Test func givenEnableAuthorizationRuleWhenConvergedThenItIsOnlyVerified() throws {
  var calls: [String] = []

  try convergeAuthorizationRule(
    for: .enable,
    verify: { calls.append("verify") },
    repair: { calls.append("repair") }
  )
  #expect(calls == ["verify"])
}

@Test func givenExactRepairAuthorizationRuleWhenConvergedThenItIsNotRewritten() throws {
  var calls: [String] = []

  try convergeAuthorizationRule(
    for: .repair,
    verify: { calls.append("verify") },
    repair: { calls.append("repair") }
  )

  #expect(calls == ["verify"])
}

@Test func givenUnavailableRepairAuthorizationRuleWhenConvergedThenItIsRepairedOnce() throws {
  var calls: [String] = []

  try convergeAuthorizationRule(
    for: .repair,
    verify: {
      calls.append("verify")
      throw AuthorizationPolicyFailure.ruleUnavailable
    },
    repair: { calls.append("repair") }
  )

  #expect(calls == ["verify", "repair"])
}

@Test func givenUnexpectedRepairAuthorizationFailureWhenConvergedThenItFailsClosed() {
  var calls: [String] = []

  #expect(throws: AuthorizationPolicyFailure.denied) {
    try convergeAuthorizationRule(
      for: .repair,
      verify: {
        calls.append("verify")
        throw AuthorizationPolicyFailure.denied
      },
      repair: { calls.append("repair") }
    )
  }

  #expect(calls == ["verify"])
}
