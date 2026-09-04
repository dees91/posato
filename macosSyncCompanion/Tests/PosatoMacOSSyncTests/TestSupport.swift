import CloudKit
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

func testAnchorFields(first: UInt8 = 1) -> Data {
  var fields = Data()
  fields.append(testIdentifier(first))
  fields.append(testIdentifier(2))
  fields.append(testIdentifier(3))
  return fields
}

func testBundleIdentifier(_ value: UInt8 = 9) -> Data {
  var bytes = Data(count: SyncLimits.bundleIdentifierBytes)
  bytes[6] = 0x40
  bytes[8] = 0x80
  bytes[15] = value
  return bytes
}

func testBundleName(_ value: UInt8 = 9) -> String {
  return RecordCodec.uuidText(from: testBundleIdentifier(value))!
}

func testBundleRecord(identifier: UInt8 = 9, byte: UInt8 = 11) -> RawRecord {
  return RawRecord(
    name: testBundleName(identifier),
    type: CloudNames.bundleType,
    fields: [CloudNames.bundlePayloadField: Data(repeating: byte, count: 8)],
    allKeys: [CloudNames.bundlePayloadField]
  )
}

func testAnchorRecord() -> RawRecord {
  return RawRecord(
    name: CloudNames.anchorName,
    type: CloudNames.anchorType,
    fields: [
      CloudNames.anchorFields[0]: testIdentifier(1),
      CloudNames.anchorFields[1]: testIdentifier(2),
      CloudNames.anchorFields[2]: testIdentifier(3),
    ],
    allKeys: CloudNames.anchorFields
  )
}

func anchorPayload(binding: Data, fields: Data) -> Data {
  var payload = Data()
  payload.append(binding)
  payload.append(fields)
  return payload
}

func bundlePayload(binding: Data, identifier: Data, bundle: Data) -> Data {
  var payload = Data()
  payload.append(binding)
  payload.append(identifier)
  payload.append(bundle)
  return payload
}

func cursorPayload(binding: Data, cursor: Data) -> Data {
  var payload = Data()
  payload.append(binding)
  payload.append(cursor)
  return payload
}

func cloudRequest(
  operation: SyncOperation,
  payload: Data = Data(),
  deadline: UInt32 = 5_000
) -> SyncMessage {
  return SyncMessage(
    operation: operation,
    requestIdentifier: Data(repeating: 9, count: SyncLimits.identifierBytes),
    deadlineMilliseconds: deadline,
    capabilities: SyncLimits.cloudkitCapability,
    outcome: nil,
    payload: payload,
  )
}

func cloudDependencies(
  backend: FakeCloudBackend = FakeCloudBackend(),
  accounts: FakeAccounts = FakeAccounts(.available(syntheticBinding))
) -> SyncDependencies {
  return SyncDependencies(
    entitlements: FakeEntitlements(value: provisionedEntitlements()),
    accounts: accounts,
    keys: WorkspaceKeyStore(backend: InMemoryKeychainBackend()),
    clouds: CloudStore(backend: backend),
  )
}

final class FakeCloudBackend: CloudBackend, @unchecked Sendable {
  var zone: ZoneLookup = .missing
  var zoneSaveFault: BackendFault?
  var zoneAfterSave: ZoneLookup?
  var records: [String: RawRecord] = [:]
  var zoneAbsentRecords = false
  var recordFault: BackendFault?
  var saveResult: BackendSave = .saved
  var changes: BackendChangesResult = .fetched(
    BackendChanges(changed: [], deletedNames: [], token: Data([1]), moreComing: false)
  )
  var deleteFault: BackendFault?
  private(set) var saveCalls = 0
  private(set) var fetchRecordCalls = 0
  private(set) var changesCalls = 0
  private(set) var zoneFetchCalls = 0
  private(set) var zoneSaveCalls = 0
  private(set) var zoneDeleteCalls = 0

  func fetchZone(timeout: TimeInterval) -> ZoneLookup {
    zoneFetchCalls += 1
    return zone
  }

  func saveZone(timeout: TimeInterval) -> BackendFault? {
    zoneSaveCalls += 1
    if let zoneAfterSave {
      zone = zoneAfterSave
    } else if zoneSaveFault == nil {
      zone = .found
    }
    return zoneSaveFault
  }

  func fetchRecord(name: String, timeout: TimeInterval) -> BackendLookup {
    fetchRecordCalls += 1
    if zoneAbsentRecords {
      return .zoneMissing
    }
    if let fault = recordFault {
      return .failed(fault)
    }
    guard let record = records[name] else {
      return .missing
    }
    return .found(record)
  }

  func saveRecord(_ record: RawRecord, timeout: TimeInterval) -> BackendSave {
    saveCalls += 1
    return saveResult
  }

  func fetchChanges(token: CKServerChangeToken?, timeout: TimeInterval) -> BackendChangesResult {
    changesCalls += 1
    return changes
  }

  func deleteZone(timeout: TimeInterval) -> BackendFault? {
    zoneDeleteCalls += 1
    return deleteFault
  }
}
