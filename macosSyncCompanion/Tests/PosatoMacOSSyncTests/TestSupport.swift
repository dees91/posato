import Foundation
import Security

@testable import PosatoMacOSSync

func testIdentifier(_ value: UInt8) -> Data {
  var bytes = Data(count: SyncLimits.identifierBytes)
  bytes[6] = 0x40
  bytes[8] = 0x80
  bytes[15] = value
  return bytes
}

func testAccount(_ value: UInt8 = 1) -> String {
  return ItemCodec.accountText(from: testIdentifier(value))!
}

func testItem(workspace: UInt8 = 1, keyByte: UInt8 = 6) -> Data {
  var item = Data()
  item.append(testIdentifier(workspace))
  item.append(testIdentifier(2))
  item.append(testIdentifier(3))
  item.append(Data(repeating: keyByte, count: SyncLimits.keyBytes))
  let checksum = ItemCodec.crc32(item, length: SyncLimits.itemBytes - SyncLimits.checksumBytes)
  for shift in [24, 16, 8, 0] {
    item.append(UInt8((checksum >> shift) & 0xFF))
  }
  return item
}

func testRequest(
  operation: SyncOperation,
  payload: Data = Data(),
  deadline: UInt32 = 5_000
) -> SyncMessage {
  return SyncMessage(
    operation: operation,
    requestIdentifier: Data(repeating: 9, count: SyncLimits.identifierBytes),
    deadlineMilliseconds: deadline,
    capabilities: SyncLimits.keychainCapability,
    outcome: nil,
    payload: payload,
  )
}

func keyPayload(binding: Data, account: String, item: Data? = nil) -> Data {
  var payload = Data()
  payload.append(binding)
  payload.append(Data(account.utf8))
  if let item {
    payload.append(item)
  }
  return payload
}

let syntheticBinding = Data(repeating: 7, count: SyncLimits.bindingBytes)
let syntheticAccessGroup = "A1B2C3D4E5.app.posato.sync"

struct FakeEntitlements: EntitlementReader {
  var value: CompanionEntitlements?

  func load() -> CompanionEntitlements? {
    return value
  }
}

final class FakeAccounts: AccountBindingSource, @unchecked Sendable {
  private var results: [BindingNative]
  private(set) var deadlines: [UInt32] = []
  var pauseFirstResolve: TimeInterval = 0

  init(_ results: BindingNative...) {
    self.results = results
  }

  func resolve(deadlineMilliseconds: UInt32) -> BindingNative {
    deadlines.append(deadlineMilliseconds)
    if deadlines.count == 1, pauseFirstResolve > 0 {
      Thread.sleep(forTimeInterval: pauseFirstResolve)
    }
    if results.isEmpty {
      return .undetermined
    }
    if results.count == 1 {
      return results[0]
    }
    return results.removeFirst()
  }
}

final class InMemoryKeychainBackend: KeychainBackend, @unchecked Sendable {
  private var stored: Data?
  private(set) var lastAdded: [String: Any]?
  private(set) var lastQuery: [String: Any]?
  private(set) var addCount = 0
  private(set) var deleteCount = 0
  var addStatus: OSStatus = errSecSuccess
  var copyStatus: OSStatus?
  var deleteStatus: OSStatus = errSecSuccess
  var onCopy: (() -> Void)?

  func add(_ attributes: [String: Any]) -> OSStatus {
    addCount += 1
    lastAdded = attributes
    if addStatus != errSecSuccess {
      return addStatus
    }
    if stored != nil {
      return errSecDuplicateItem
    }
    stored = attributes[kSecValueData as String] as? Data
    return errSecSuccess
  }

  func copyMatching(_ query: [String: Any], result: inout CFTypeRef?) -> OSStatus {
    onCopy?()
    lastQuery = query
    if let copyStatus {
      return copyStatus
    }
    guard let stored else {
      return errSecItemNotFound
    }
    result = stored as CFData
    return errSecSuccess
  }

  func delete(_ query: [String: Any]) -> OSStatus {
    deleteCount += 1
    lastQuery = query
    if deleteStatus != errSecSuccess {
      return deleteStatus
    }
    stored = nil
    return errSecSuccess
  }
}

func provisionedEntitlements() -> CompanionEntitlements {
  return CompanionEntitlements(
    icloudContainers: [SyncLimits.containerIdentifier],
    icloudServices: ["CloudKit"],
    keychainAccessGroups: [syntheticAccessGroup],
  )
}
