import Darwin
import Foundation
import Security

struct SigningIdentity: Equatable, Sendable {
  let identifier: String
  let teamIdentifier: String?

  var isAdHoc: Bool {
    return teamIdentifier == nil
  }
}

enum CodeSigningFailure: Error, Equatable {
  case invalidIdentity
  case requirementCompilationFailed
  case signatureRejected
}

enum ParentVerification {
  static func currentIdentity(expectedIdentifier: String) throws -> SigningIdentity {
    guard isRequirementValue(expectedIdentifier) else {
      throw CodeSigningFailure.invalidIdentity
    }
    guard let executableURL = Bundle.main.executableURL else {
      throw CodeSigningFailure.signatureRejected
    }
    var staticCode: SecStaticCode?
    guard SecStaticCodeCreateWithPath(executableURL as CFURL, [], &staticCode) == errSecSuccess,
      let staticCode
    else {
      throw CodeSigningFailure.signatureRejected
    }
    guard
      let identity = try? signingIdentity(
        of: staticCode,
        expectedIdentifier: expectedIdentifier
      )
    else {
      throw CodeSigningFailure.signatureRejected
    }
    if identity.isAdHoc {
      return identity
    }
    let requirement = try compiledRequirement(
      identity: identity,
      expectedIdentifier: identity.identifier,
    )
    guard SecStaticCodeCheckValidity(staticCode, [], requirement) == errSecSuccess else {
      throw CodeSigningFailure.signatureRejected
    }
    return identity
  }

  static func validateRunningParent(
    processIdentifier: pid_t,
    selfIdentity: SigningIdentity
  ) throws {
    guard processIdentifier > 1 else {
      throw CodeSigningFailure.signatureRejected
    }
    let attributes = [kSecGuestAttributePid as String: processIdentifier] as CFDictionary
    var code: SecCode?
    guard SecCodeCopyGuestWithAttributes(nil, attributes, [], &code) == errSecSuccess,
      let code
    else {
      throw CodeSigningFailure.signatureRejected
    }
    var staticCode: SecStaticCode?
    guard SecCodeCopyStaticCode(code, [], &staticCode) == errSecSuccess, let staticCode else {
      throw CodeSigningFailure.signatureRejected
    }
    try validatePeer(
      staticCode: staticCode,
      selfIdentity: selfIdentity,
      expectedIdentifier: SyncLimits.applicationIdentifier,
    )
    if !selfIdentity.isAdHoc {
      guard
        SecCodeCheckValidity(
          code, [],
          try compiledRequirement(
            identity: selfIdentity,
            expectedIdentifier: SyncLimits.applicationIdentifier,
          )) == errSecSuccess
      else {
        throw CodeSigningFailure.signatureRejected
      }
    }
  }

  static func validatePackageRelationship(selfIdentity: SigningIdentity) throws {
    let companionBundle = Bundle.main.bundleURL.resolvingSymlinksInPath()
    let helpersDirectory = companionBundle.deletingLastPathComponent()
    let contentsDirectory = helpersDirectory.deletingLastPathComponent()
    let parentApplication = contentsDirectory.deletingLastPathComponent()
    guard companionBundle.lastPathComponent == SyncLimits.companionBundleName,
      helpersDirectory.lastPathComponent == "Helpers",
      contentsDirectory.lastPathComponent == "Contents",
      parentApplication.pathExtension == "app"
    else {
      throw CodeSigningFailure.signatureRejected
    }
    var staticCode: SecStaticCode?
    guard SecStaticCodeCreateWithPath(parentApplication as CFURL, [], &staticCode) == errSecSuccess,
      let staticCode
    else {
      throw CodeSigningFailure.signatureRejected
    }
    try validatePeer(
      staticCode: staticCode,
      selfIdentity: selfIdentity,
      expectedIdentifier: SyncLimits.applicationIdentifier,
    )
  }

  static func requirementText(
    identity: SigningIdentity,
    expectedIdentifier: String
  ) throws -> String {
    guard let teamIdentifier = identity.teamIdentifier,
      isRequirementValue(identity.identifier),
      isRequirementValue(teamIdentifier),
      isRequirementValue(expectedIdentifier)
    else {
      throw CodeSigningFailure.invalidIdentity
    }
    return "anchor apple generic and identifier \"\(expectedIdentifier)\""
      + " and certificate leaf[subject.OU] = \"\(teamIdentifier)\""
  }

  static func validatePeerIdentifiers(
    selfIdentity: SigningIdentity,
    peerIdentifier: String,
    peerTeam: String?
  ) throws {
    guard peerIdentifier == SyncLimits.applicationIdentifier else {
      throw CodeSigningFailure.signatureRejected
    }
    switch (selfIdentity.teamIdentifier, peerTeam) {
    case (nil, nil):
      return
    case (let selfTeam?, let peerTeam?) where selfTeam == peerTeam:
      return
    default:
      throw CodeSigningFailure.signatureRejected
    }
  }

  static func verifyParent() -> Bool {
    guard
      let identity = try? currentIdentity(
        expectedIdentifier: SyncLimits.companionIdentifier
      ),
      (try? validateRunningParent(
        processIdentifier: getppid(),
        selfIdentity: identity
      )) != nil,
      (try? validatePackageRelationship(selfIdentity: identity)) != nil
    else {
      return false
    }
    return true
  }

  private static func validatePeer(
    staticCode: SecStaticCode,
    selfIdentity: SigningIdentity,
    expectedIdentifier: String
  ) throws {
    let peer = try signingIdentity(of: staticCode, expectedIdentifier: expectedIdentifier)
    try validatePeerIdentifiers(
      selfIdentity: selfIdentity,
      peerIdentifier: peer.identifier,
      peerTeam: peer.teamIdentifier,
    )
    if !selfIdentity.isAdHoc {
      let requirement = try compiledRequirement(
        identity: selfIdentity,
        expectedIdentifier: expectedIdentifier,
      )
      guard SecStaticCodeCheckValidity(staticCode, [], requirement) == errSecSuccess else {
        throw CodeSigningFailure.signatureRejected
      }
    }
  }

  private static func signingIdentity(
    of staticCode: SecStaticCode,
    expectedIdentifier: String
  ) throws -> SigningIdentity {
    var signingInformation: CFDictionary?
    guard
      SecCodeCopySigningInformation(
        staticCode,
        SecCSFlags(rawValue: kSecCSSigningInformation),
        &signingInformation
      ) == errSecSuccess,
      let values = signingInformation as? [String: Any],
      let identifier = values[kSecCodeInfoIdentifier as String] as? String,
      identifier == expectedIdentifier
    else {
      throw CodeSigningFailure.signatureRejected
    }
    let team = values[kSecCodeInfoTeamIdentifier as String] as? String
    if let team, isRequirementValue(team) {
      return SigningIdentity(identifier: identifier, teamIdentifier: team)
    }
    return SigningIdentity(identifier: identifier, teamIdentifier: nil)
  }

  private static func compiledRequirement(
    identity: SigningIdentity,
    expectedIdentifier: String
  ) throws -> SecRequirement {
    return try compiledRequirement(
      text: requirementText(identity: identity, expectedIdentifier: expectedIdentifier)
    )
  }

  private static func compiledRequirement(text: String) throws -> SecRequirement {
    var requirement: SecRequirement?
    guard
      SecRequirementCreateWithString(text as CFString, [], &requirement) == errSecSuccess,
      let requirement
    else {
      throw CodeSigningFailure.requirementCompilationFailed
    }
    return requirement
  }

  private static func isRequirementValue(_ value: String) -> Bool {
    guard !value.isEmpty, value.utf8.count <= 128 else {
      return false
    }
    return value.utf8.allSatisfy { byte in
      (byte >= 65 && byte <= 90) || (byte >= 97 && byte <= 122)
        || (byte >= 48 && byte <= 57) || byte == 45 || byte == 46
    }
  }
}
