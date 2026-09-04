import Testing

@testable import PosatoMacOSSync

@Test func givenFixedIdentitiesWhenRequirementIsBuiltThenItPinsParentAndTeam() throws {
  let identity = SigningIdentity(
    identifier: SyncLimits.companionIdentifier,
    teamIdentifier: "SYNTHETICTEAM",
  )

  let requirement = try ParentVerification.requirementText(
    identity: identity,
    expectedIdentifier: SyncLimits.applicationIdentifier,
  )

  #expect(requirement.contains("identifier \"app.posato.macos\""))
  #expect(requirement.contains("certificate leaf[subject.OU] = \"SYNTHETICTEAM\""))
  #expect(requirement.hasPrefix("anchor apple generic"))
}

@Test func givenAdHocPeerWhenIdentifiersMatchThenThePairIsAccepted() throws {
  let identity = SigningIdentity(
    identifier: SyncLimits.companionIdentifier,
    teamIdentifier: nil,
  )

  try ParentVerification.validatePeerIdentifiers(
    selfIdentity: identity,
    peerIdentifier: SyncLimits.applicationIdentifier,
    peerTeam: nil,
  )
}

@Test func givenMixedAdHocAndTeamWhenPeerIsCheckedThenItIsRejected() {
  let identity = SigningIdentity(
    identifier: SyncLimits.companionIdentifier,
    teamIdentifier: nil,
  )

  #expect(throws: CodeSigningFailure.signatureRejected) {
    try ParentVerification.validatePeerIdentifiers(
      selfIdentity: identity,
      peerIdentifier: SyncLimits.applicationIdentifier,
      peerTeam: "SYNTHETICTEAM",
    )
  }
}

@Test func givenInjectedRequirementSyntaxWhenRequirementIsBuiltThenItIsRejected() {
  let identity = SigningIdentity(
    identifier: SyncLimits.companionIdentifier,
    teamIdentifier: "TEAM\" or true",
  )

  #expect(throws: CodeSigningFailure.invalidIdentity) {
    try ParentVerification.requirementText(
      identity: identity,
      expectedIdentifier: SyncLimits.applicationIdentifier,
    )
  }
}
