import Darwin
import Foundation
import Security

public struct SigningIdentity: Equatable, Sendable {
  public let identifier: String
  public let teamIdentifier: String

  public init(identifier: String, teamIdentifier: String) {
    self.identifier = identifier
    self.teamIdentifier = teamIdentifier
  }
}

public enum CodeSigningFailure: Error, Equatable {
  case invalidIdentity
  case requirementCompilationFailed
  case signatureRejected
}

public enum CodeSigningRequirements {
  public static func currentIdentity(expectedIdentifier: String) throws -> SigningIdentity {
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
    var signingInformation: CFDictionary?
    guard
      SecCodeCopySigningInformation(
        staticCode,
        SecCSFlags(rawValue: kSecCSSigningInformation),
        &signingInformation
      ) == errSecSuccess,
      let values = signingInformation as? [String: Any],
      let identifier = values[kSecCodeInfoIdentifier as String] as? String,
      let teamIdentifier = values[kSecCodeInfoTeamIdentifier as String] as? String,
      identifier == expectedIdentifier,
      isRequirementValue(teamIdentifier)
    else {
      throw CodeSigningFailure.signatureRejected
    }
    let identity = SigningIdentity(
      identifier: identifier,
      teamIdentifier: teamIdentifier
    )
    let requirement = try compiledRequirement(
      identity: identity,
      expectedIdentifier: identifier
    )
    guard SecStaticCodeCheckValidity(staticCode, [], requirement) == errSecSuccess else {
      throw CodeSigningFailure.signatureRejected
    }
    return identity
  }

  public static func peerRequirement(
    selfIdentity: SigningIdentity,
    peerIdentifier: String
  ) throws -> String {
    let requirement = try requirementText(
      identity: selfIdentity,
      expectedIdentifier: peerIdentifier
    )
    _ = try compiledRequirement(text: requirement)
    return requirement
  }

  public static func validateRunningPeer(
    processIdentifier: pid_t,
    selfIdentity: SigningIdentity,
    peerIdentifier: String
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
    let requirement = try compiledRequirement(
      text: requirementText(identity: selfIdentity, expectedIdentifier: peerIdentifier)
    )
    guard SecCodeCheckValidity(code, [], requirement) == errSecSuccess else {
      throw CodeSigningFailure.signatureRejected
    }
  }

  public static func validateHelperPackageRelationship(selfIdentity: SigningIdentity) throws {
    let helperBundle = Bundle.main.bundleURL.resolvingSymlinksInPath()
    let helpersDirectory = helperBundle.deletingLastPathComponent()
    let contentsDirectory = helpersDirectory.deletingLastPathComponent()
    let parentApplication = contentsDirectory.deletingLastPathComponent()
    guard helperBundle.lastPathComponent == "PosatoMacOSHelper.app",
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
    let requirement = try compiledRequirement(
      text: requirementText(
        identity: selfIdentity,
        expectedIdentifier: ServiceContract.applicationIdentifier
      )
    )
    guard SecStaticCodeCheckValidity(staticCode, [], requirement) == errSecSuccess else {
      throw CodeSigningFailure.signatureRejected
    }
  }

  public static func requirementText(
    identity: SigningIdentity,
    expectedIdentifier: String
  ) throws -> String {
    guard isRequirementValue(identity.identifier),
      isRequirementValue(identity.teamIdentifier),
      isRequirementValue(expectedIdentifier)
    else {
      throw CodeSigningFailure.invalidIdentity
    }
    return "anchor apple generic and identifier \"\(expectedIdentifier)\""
      + " and certificate leaf[subject.OU] = \"\(identity.teamIdentifier)\""
  }

  private static func compiledRequirement(
    identity: SigningIdentity,
    expectedIdentifier: String
  ) throws -> SecRequirement {
    let text = try requirementText(
      identity: identity,
      expectedIdentifier: expectedIdentifier
    )
    return try compiledRequirement(text: text)
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
