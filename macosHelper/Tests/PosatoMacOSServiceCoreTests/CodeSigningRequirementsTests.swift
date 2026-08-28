import Testing

@testable import PosatoMacOSServiceCore

@Test func givenFixedIdentitiesWhenRequirementIsBuiltThenItPinsPeerAndTeam() throws {
  let identity = SigningIdentity(
    identifier: ServiceContract.helperIdentifier,
    teamIdentifier: "SYNTHETICTEAM"
  )

  let requirement = try CodeSigningRequirements.requirementText(
    identity: identity,
    expectedIdentifier: ServiceContract.daemonIdentifier
  )

  #expect(requirement.contains("identifier \"app.posato.macos.proxy-settings\""))
  #expect(requirement.contains("certificate leaf[subject.OU] = \"SYNTHETICTEAM\""))
  #expect(requirement.hasPrefix("anchor apple generic"))
}

@Test func givenInjectedRequirementSyntaxWhenRequirementIsBuiltThenItIsRejected() {
  let identity = SigningIdentity(
    identifier: ServiceContract.helperIdentifier,
    teamIdentifier: "TEAM\" or true"
  )

  #expect(throws: CodeSigningFailure.invalidIdentity) {
    try CodeSigningRequirements.requirementText(
      identity: identity,
      expectedIdentifier: ServiceContract.daemonIdentifier
    )
  }
}

@Test func givenEmptyTeamWhenRequirementIsBuiltThenItIsRejected() {
  let identity = SigningIdentity(
    identifier: ServiceContract.helperIdentifier,
    teamIdentifier: ""
  )

  #expect(throws: CodeSigningFailure.invalidIdentity) {
    try CodeSigningRequirements.requirementText(
      identity: identity,
      expectedIdentifier: ServiceContract.daemonIdentifier
    )
  }
}
