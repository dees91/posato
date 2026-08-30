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

@Test func givenReconciledAuthorizationRuleWhenDispatchedThenOriginalIntentIsPreserved() throws {
  var calls: [String] = []

  try reconcileAuthorizationRule(
    for: .enable,
    verify: { calls.append("verify") },
    repair: { calls.append("repair") }
  )
  #expect(calls == ["verify"])

  calls.removeAll()
  try reconcileAuthorizationRule(
    for: .repair,
    verify: { calls.append("verify") },
    repair: { calls.append("repair") }
  )
  #expect(calls == ["repair"])
}
