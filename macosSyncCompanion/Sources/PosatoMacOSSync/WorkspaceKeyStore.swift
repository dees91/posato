import Foundation
import Security

protocol KeychainBackend: Sendable {
  func add(_ attributes: [String: Any]) -> OSStatus
  func copyMatching(_ query: [String: Any], result: inout CFTypeRef?) -> OSStatus
  func delete(_ query: [String: Any]) -> OSStatus
}

struct SystemKeychainBackend: KeychainBackend {
  func add(_ attributes: [String: Any]) -> OSStatus {
    return SecItemAdd(attributes as CFDictionary, nil)
  }

  func copyMatching(_ query: [String: Any], result: inout CFTypeRef?) -> OSStatus {
    return SecItemCopyMatching(query as CFDictionary, &result)
  }

  func delete(_ query: [String: Any]) -> OSStatus {
    return SecItemDelete(query as CFDictionary)
  }
}

enum KeyItemNative: Equatable, Sendable {
  case found(Data)
  case missing
  case created
  case identical
  case deletedAndAbsent
  case retryable
  case integrityFailure
  case unknownOutcome
}

struct WorkspaceKeyStore: Sendable {
  private let backend: any KeychainBackend

  init(backend: any KeychainBackend = SystemKeychainBackend()) {
    self.backend = backend
  }

  func read(account: String, accessGroup: String) -> KeyItemNative {
    switch copy(account: account, accessGroup: accessGroup) {
    case .success(let value):
      guard let item = ItemCodec.validateItem(value, account: account) else {
        return .integrityFailure
      }
      return .found(item)
    case .missing:
      return .missing
    case .retryable:
      return .retryable
    }
  }

  func create(account: String, accessGroup: String, value: Data) -> KeyItemNative {
    guard let item = ItemCodec.validateItem(value, account: account) else {
      return .integrityFailure
    }
    switch copy(account: account, accessGroup: accessGroup) {
    case .success(let existing):
      if ItemCodec.constantTimeEquals(existing, item) {
        return .identical
      }
      return .integrityFailure
    case .retryable:
      return .retryable
    case .missing:
      break
    }
    let status = backend.add(addQuery(account: account, accessGroup: accessGroup, value: item))
    if status == errSecDuplicateItem {
      switch copy(account: account, accessGroup: accessGroup) {
      case .success(let existing):
        return ItemCodec.constantTimeEquals(existing, item) ? .identical : .integrityFailure
      case .missing, .retryable:
        return .unknownOutcome
      }
    }
    guard status == errSecSuccess else {
      return mapFailure(status)
    }
    return .created
  }

  func deleteAndVerifyAbsent(account: String, accessGroup: String) -> KeyItemNative {
    let status = backend.delete(exactQuery(account: account, accessGroup: accessGroup))
    guard status == errSecSuccess || status == errSecItemNotFound else {
      return mapFailure(status)
    }
    switch copy(account: account, accessGroup: accessGroup) {
    case .missing:
      return .deletedAndAbsent
    case .success:
      return .integrityFailure
    case .retryable:
      return .unknownOutcome
    }
  }

  private enum CopyResult {
    case success(Data)
    case missing
    case retryable
  }

  private func copy(account: String, accessGroup: String) -> CopyResult {
    var query = exactQuery(account: account, accessGroup: accessGroup)
    query[kSecReturnData as String] = kCFBooleanTrue
    query[kSecMatchLimit as String] = kSecMatchLimitOne
    var result: CFTypeRef?
    let status = backend.copyMatching(query, result: &result)
    if status == errSecItemNotFound {
      return .missing
    }
    guard status == errSecSuccess, let data = result as? Data else {
      return .retryable
    }
    return .success(data)
  }

  private func exactQuery(account: String, accessGroup: String) -> [String: Any] {
    return [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: SyncLimits.keyService,
      kSecAttrAccount as String: account,
      kSecAttrAccessGroup as String: accessGroup,
      kSecAttrSynchronizable as String: kCFBooleanTrue as Any,
      kSecUseDataProtectionKeychain as String: kCFBooleanTrue as Any,
    ]
  }

  private func addQuery(account: String, accessGroup: String, value: Data) -> [String: Any] {
    var query = exactQuery(account: account, accessGroup: accessGroup)
    query[kSecValueData as String] = value
    query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
    return query
  }

  private func mapFailure(_ status: OSStatus) -> KeyItemNative {
    switch status {
    case errSecMissingEntitlement, errSecAuthFailed, errSecInteractionNotAllowed,
      errSecNotAvailable:
      return .retryable
    default:
      return .retryable
    }
  }
}
