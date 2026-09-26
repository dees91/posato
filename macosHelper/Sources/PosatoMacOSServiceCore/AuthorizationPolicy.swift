import Foundation
import Security

public enum AuthorizationRight: Sendable, CaseIterable {
  case apply
  case standingApply

  public var name: String {
    switch self {
    case .apply:
      return "app.posato.macos.proxy.apply"
    case .standingApply:
      return "app.posato.macos.proxy.standing-apply"
    }
  }
}

public enum AuthorizationRuleState: Sendable, Equatable {
  case absent
  case exact
  case mismatched
}

/// The daemon's view of the two custom rules. The system implementation is `AuthorizationPolicy`.
public protocol AuthorizationRules: Sendable {
  func state(of right: AuthorizationRight) throws -> AuthorizationRuleState
  func write(_ right: AuthorizationRight) throws
  func remove(_ right: AuthorizationRight) throws
  func validateAndDestroy(_ right: AuthorizationRight, externalForm: inout Data) throws
}

public struct SystemAuthorizationRules: AuthorizationRules {
  public init() {}

  public func state(of right: AuthorizationRight) throws -> AuthorizationRuleState {
    return try AuthorizationPolicy.state(of: right)
  }

  public func write(_ right: AuthorizationRight) throws {
    try AuthorizationPolicy.write(right)
  }

  public func remove(_ right: AuthorizationRight) throws {
    try AuthorizationPolicy.remove(right)
  }

  public func validateAndDestroy(_ right: AuthorizationRight, externalForm: inout Data) throws {
    try AuthorizationPolicy.validateAndDestroyExternalForm(&externalForm, right: right)
  }
}

public enum AuthorizationPolicyFailure: Error, Equatable {
  case denied
  case invalidExternalForm
  case ruleUnavailable
}

public final class ApplyAuthorizationGrant: @unchecked Sendable {
  private var material: Data
  private let authorization: AuthorizationRef

  init(externalForm: Data, authorization: AuthorizationRef) {
    material = externalForm
    self.authorization = authorization
  }

  public var externalForm: Data {
    return material
  }

  deinit {
    material.resetBytes(in: material.startIndex..<material.endIndex)
    AuthorizationFree(authorization, [.destroyRights])
  }
}

public enum AuthorizationPolicy {
  public static let applyRight = AuthorizationRight.apply.name
  public static let externalFormBytes = MemoryLayout<AuthorizationExternalForm>.size
  static let applyRightTimeoutSeconds = 30

  public static func state(of right: AuthorizationRight) throws -> AuthorizationRuleState {
    var definition: CFDictionary?
    let status = AuthorizationRightGet(right.name, &definition)
    if status == errAuthorizationDenied {
      return .absent
    }
    guard status == errAuthorizationSuccess, let values = definition as? [String: Any] else {
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
    return hasExpectedApplyRightDefinition(values) ? .exact : .mismatched
  }

  public static func write(_ right: AuthorizationRight) throws {
    var authorization: AuthorizationRef?
    guard AuthorizationCreate(nil, nil, [], &authorization) == errAuthorizationSuccess,
      let authorization
    else {
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
    defer { AuthorizationFree(authorization, []) }
    let status = AuthorizationRightSet(
      authorization,
      right.name,
      applyRightDefinition() as CFTypeRef,
      nil,
      nil,
      nil
    )
    guard status == errAuthorizationSuccess, try state(of: right) == .exact else {
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
  }

  /// Both rules share the Apply definition: a freshly authenticated administrator, never shared,
  /// with only the 30-second window needed to carry one external form to the daemon.
  static func applyRightDefinition() -> [String: Any] {
    return [
      "class": "user",
      "group": "admin",
      "authenticate-user": true,
      "session-owner": false,
      "shared": false,
      "timeout": applyRightTimeoutSeconds,
      "version": 1,
    ]
  }

  static func hasExpectedApplyRightDefinition(_ values: [String: Any]) -> Bool {
    return values["class"] as? String == "user"
      && values["group"] as? String == "admin"
      && boolean(values["authenticate-user"]) == true
      && boolean(values["session-owner"]) == false
      && boolean(values["shared"]) == false
      && boolean(values["allow-root"]) != true
      && integer(values["timeout"]) == applyRightTimeoutSeconds
      && integer(values["version"]) == 1
  }

  public static func remove(_ right: AuthorizationRight) throws {
    var authorization: AuthorizationRef?
    guard AuthorizationCreate(nil, nil, [], &authorization) == errAuthorizationSuccess,
      let authorization
    else {
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
    defer { AuthorizationFree(authorization, []) }
    let status = AuthorizationRightRemove(authorization, right.name)
    guard status == errAuthorizationSuccess || status == errAuthorizationDenied,
      try state(of: right) == .absent
    else {
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
  }

  public static func acquireGrant(
    _ right: AuthorizationRight,
    prompt: String? = nil
  ) throws -> ApplyAuthorizationGrant {
    var authorization: AuthorizationRef?
    guard AuthorizationCreate(nil, nil, [], &authorization) == errAuthorizationSuccess,
      let authorization
    else {
      throw AuthorizationPolicyFailure.denied
    }
    let status = withRight(right) { rights in
      withPromptEnvironment(prompt) { environment in
        AuthorizationCopyRights(
          authorization,
          &rights,
          environment,
          [.interactionAllowed, .extendRights, .preAuthorize],
          nil
        )
      }
    }
    guard status == errAuthorizationSuccess else {
      AuthorizationFree(authorization, [.destroyRights])
      throw AuthorizationPolicyFailure.denied
    }
    var externalForm = AuthorizationExternalForm()
    defer {
      _ = withUnsafeMutableBytes(of: &externalForm) { bytes in
        bytes.initializeMemory(as: UInt8.self, repeating: 0)
      }
    }
    guard AuthorizationMakeExternalForm(authorization, &externalForm) == errAuthorizationSuccess
    else {
      AuthorizationFree(authorization, [.destroyRights])
      throw AuthorizationPolicyFailure.denied
    }
    let data = withUnsafeBytes(of: externalForm) { Data($0) }
    return ApplyAuthorizationGrant(externalForm: data, authorization: authorization)
  }

  public static func validateAndDestroyExternalForm(
    _ data: inout Data,
    right: AuthorizationRight
  ) throws {
    defer { data.resetBytes(in: data.startIndex..<data.endIndex) }
    guard data.count == externalFormBytes else {
      throw AuthorizationPolicyFailure.invalidExternalForm
    }
    var externalForm = AuthorizationExternalForm()
    defer {
      _ = withUnsafeMutableBytes(of: &externalForm) { bytes in
        bytes.initializeMemory(as: UInt8.self, repeating: 0)
      }
    }
    _ = withUnsafeMutableBytes(of: &externalForm) { destination in
      data.copyBytes(to: destination)
    }
    var authorization: AuthorizationRef?
    guard
      AuthorizationCreateFromExternalForm(&externalForm, &authorization) == errAuthorizationSuccess,
      let authorization
    else {
      throw AuthorizationPolicyFailure.invalidExternalForm
    }
    defer { AuthorizationFree(authorization, [.destroyRights]) }
    let status = withRight(right) { rights in
      AuthorizationCopyRights(authorization, &rights, nil, [], nil)
    }
    guard status == errAuthorizationSuccess else {
      throw AuthorizationPolicyFailure.denied
    }
  }

  private static func withRight(
    _ right: AuthorizationRight,
    _ body: (inout AuthorizationRights) -> OSStatus
  ) -> OSStatus {
    return right.name.withCString { name in
      var item = AuthorizationItem(
        name: name,
        valueLength: 0,
        value: nil,
        flags: 0
      )
      return withUnsafeMutablePointer(to: &item) { itemPointer in
        var rights = AuthorizationRights(count: 1, items: itemPointer)
        return body(&rights)
      }
    }
  }

  private static func withPromptEnvironment(
    _ prompt: String?,
    _ body: (UnsafePointer<AuthorizationEnvironment>?) -> OSStatus
  ) -> OSStatus {
    guard let prompt else {
      return body(nil)
    }
    return kAuthorizationEnvironmentPrompt.withCString { key in
      var bytes = Array(prompt.utf8)
      return bytes.withUnsafeMutableBytes { value in
        var item = AuthorizationItem(
          name: key,
          valueLength: value.count,
          value: value.baseAddress,
          flags: 0
        )
        return withUnsafeMutablePointer(to: &item) { itemPointer in
          var environment = AuthorizationEnvironment(count: 1, items: itemPointer)
          return body(&environment)
        }
      }
    }
  }

  private static func boolean(_ value: Any?) -> Bool? {
    return (value as? NSNumber)?.boolValue
  }

  private static func integer(_ value: Any?) -> Int? {
    return (value as? NSNumber)?.intValue
  }
}
