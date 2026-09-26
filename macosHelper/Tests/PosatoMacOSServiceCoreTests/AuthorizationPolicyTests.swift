import Foundation
import Testing

@testable import PosatoMacOSServiceCore

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
    try AuthorizationPolicy.validateAndDestroyExternalForm(&material, right: .apply)
  }
  #expect(material.allSatisfy { $0 == 0 })
}
