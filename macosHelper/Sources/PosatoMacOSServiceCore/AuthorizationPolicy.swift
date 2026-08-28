import Foundation
import Security

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
  public static let applyRight = "app.posato.macos.proxy.apply"
  public static let externalFormBytes = MemoryLayout<AuthorizationExternalForm>.size
  static let applyRightTimeoutSeconds = 30

  public static func installApplyRight() throws {
    var existing: CFDictionary?
    let existingStatus = AuthorizationRightGet(applyRight, &existing)
    if existingStatus == errAuthorizationSuccess {
      try verifyApplyRight()
      return
    }
    guard existingStatus == errAuthorizationDenied else {
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
    try setApplyRight()
  }

  public static func repairApplyRight() throws {
    try setApplyRight()
  }

  private static func setApplyRight() throws {
    var authorization: AuthorizationRef?
    guard AuthorizationCreate(nil, nil, [], &authorization) == errAuthorizationSuccess,
      let authorization
    else {
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
    defer { AuthorizationFree(authorization, []) }
    let definition = applyRightDefinition()
    let status = AuthorizationRightSet(
      authorization,
      applyRight,
      definition as CFTypeRef,
      nil,
      nil,
      nil
    )
    guard status == errAuthorizationSuccess else {
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
    try verifyApplyRight()
  }

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

  public static func verifyApplyRight() throws {
    var definition: CFDictionary?
    guard AuthorizationRightGet(applyRight, &definition) == errAuthorizationSuccess,
      let values = definition as? [String: Any],
      hasExpectedApplyRightDefinition(values)
    else {
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
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

  public static func removeApplyRight() throws {
    var authorization: AuthorizationRef?
    guard AuthorizationCreate(nil, nil, [], &authorization) == errAuthorizationSuccess,
      let authorization
    else {
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
    defer { AuthorizationFree(authorization, []) }
    let status = AuthorizationRightRemove(authorization, applyRight)
    guard status == errAuthorizationSuccess || status == errAuthorizationDenied else {
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
    try verifyApplyRightAbsent()
  }

  public static func verifyApplyRightAbsent() throws {
    var definition: CFDictionary?
    guard AuthorizationRightGet(applyRight, &definition) == errAuthorizationDenied else {
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
  }

  public static func acquireApplyGrant() throws -> ApplyAuthorizationGrant {
    var authorization: AuthorizationRef?
    guard AuthorizationCreate(nil, nil, [], &authorization) == errAuthorizationSuccess,
      let authorization
    else {
      throw AuthorizationPolicyFailure.denied
    }
    let status = withApplyRight { rights in
      AuthorizationCopyRights(
        authorization,
        &rights,
        nil,
        [.interactionAllowed, .extendRights, .preAuthorize],
        nil
      )
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

  public static func validateAndDestroyApplyExternalForm(_ data: inout Data) throws {
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
    let status = withApplyRight { rights in
      AuthorizationCopyRights(authorization, &rights, nil, [], nil)
    }
    guard status == errAuthorizationSuccess else {
      throw AuthorizationPolicyFailure.denied
    }
  }

  private static func withApplyRight(_ body: (inout AuthorizationRights) -> OSStatus) -> OSStatus {
    return applyRight.withCString { name in
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

  private static func boolean(_ value: Any?) -> Bool? {
    return (value as? NSNumber)?.boolValue
  }

  private static func integer(_ value: Any?) -> Int? {
    return (value as? NSNumber)?.intValue
  }
}
