import Foundation
import Security

struct CompanionEntitlements: Equatable, Sendable {
  var icloudContainers: [String]
  var icloudServices: [String]
  var keychainAccessGroups: [String]
}

protocol EntitlementReader: Sendable {
  func load() -> CompanionEntitlements?
}

struct SecTaskEntitlementReader: EntitlementReader {
  func load() -> CompanionEntitlements? {
    guard let task = SecTaskCreateFromSelf(nil) else {
      return nil
    }
    let containers = stringList(
      task: task,
      key: "com.apple.developer.icloud-container-identifiers"
    )
    let services = stringList(task: task, key: "com.apple.developer.icloud-services")
    let groups = stringList(task: task, key: "keychain-access-groups")
    guard let containers, let services, let groups else {
      return nil
    }
    return CompanionEntitlements(
      icloudContainers: containers,
      icloudServices: services,
      keychainAccessGroups: groups,
    )
  }

  private func stringList(task: SecTask, key: String) -> [String]? {
    var error: Unmanaged<CFError>?
    guard let value = SecTaskCopyValueForEntitlement(task, key as CFString, &error) else {
      return nil
    }
    return value as? [String]
  }
}

enum EntitlementGuard {
  static func accessGroup(from entitlements: CompanionEntitlements) -> String? {
    guard entitlements.icloudContainers.contains(SyncLimits.containerIdentifier),
      entitlements.icloudServices.contains("CloudKit")
    else {
      return nil
    }
    let matches = entitlements.keychainAccessGroups.filter(isWorkspaceAccessGroup)
    guard matches.count == 1 else {
      return nil
    }
    return matches[0]
  }

  private static func isWorkspaceAccessGroup(_ value: String) -> Bool {
    let suffix = "." + SyncLimits.accessGroupSuffix
    guard value.hasSuffix(suffix) else {
      return false
    }
    let prefix = String(value.dropLast(suffix.count))
    guard prefix.utf8.count == 10 else {
      return false
    }
    return prefix.utf8.allSatisfy { byte in
      (byte >= 48 && byte <= 57) || (byte >= 65 && byte <= 90)
    }
  }
}
