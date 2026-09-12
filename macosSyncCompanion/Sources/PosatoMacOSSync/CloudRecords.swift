import CloudKit
import Foundation

enum CloudNames {
  static let zoneName = "PosatoSyncV1"
  static let anchorType = "PosatoWorkspaceV1"
  static let anchorName = "workspace"
  static let anchorFields = ["workspaceId", "transportEpochId", "keyEpochId"]
  static let bundleType = "PosatoEncryptedBundleV1"
  static let bundlePayloadField = "payload"
}

struct RawRecord: Equatable, Sendable {
  var name: String
  var type: String
  var fields: [String: Data]
  var allKeys: [String]

  static func from(_ record: CKRecord) -> RawRecord {
    var fields: [String: Data] = [:]
    for key in record.allKeys() {
      if let value = record[key] as? Data {
        fields[key] = value
      }
    }
    return RawRecord(
      name: record.recordID.recordName,
      type: record.recordType,
      fields: fields,
      allKeys: record.allKeys()
    )
  }

  func makeRecord(zoneID: CKRecordZone.ID) -> CKRecord? {
    let record = CKRecord(
      recordType: type,
      recordID: CKRecord.ID(recordName: name, zoneID: zoneID)
    )
    for (key, value) in fields {
      record[key] = value as NSData
    }
    return record
  }
}

struct ChangePageFields: Equatable, Sendable {
  var moreComing: Bool
  var cursor: Data
  var bundleIdentifier: Data?
  var bundle: Data?
}

struct BundleParts: Equatable, Sendable {
  var binding: Data
  var identifier: Data
  var bundle: Data
}

enum RecordCodec {
  private static let hexDigits = Array("0123456789abcdef".utf8)

  static func zoneID() -> CKRecordZone.ID {
    return CKRecordZone.ID(zoneName: CloudNames.zoneName, ownerName: CKCurrentUserDefaultName)
  }

  static func anchorRecordID(zoneID: CKRecordZone.ID) -> CKRecord.ID {
    return CKRecord.ID(recordName: CloudNames.anchorName, zoneID: zoneID)
  }

  static func bundleRecordID(identifier: Data, zoneID: CKRecordZone.ID) -> CKRecord.ID? {
    guard let name = uuidText(from: identifier) else {
      return nil
    }
    return CKRecord.ID(recordName: name, zoneID: zoneID)
  }

  static func uuidText(from identifier: Data) -> String? {
    guard identifier.count == SyncLimits.bundleIdentifierBytes else {
      return nil
    }
    var text = [UInt8]()
    text.reserveCapacity(SyncLimits.uuidTextBytes)
    for index in 0..<4 {
      appendByte(identifier[index], to: &text)
    }
    text.append(UInt8(ascii: "-"))
    for index in 4..<6 {
      appendByte(identifier[index], to: &text)
    }
    text.append(UInt8(ascii: "-"))
    for index in 6..<8 {
      appendByte(identifier[index], to: &text)
    }
    text.append(UInt8(ascii: "-"))
    for index in 8..<10 {
      appendByte(identifier[index], to: &text)
    }
    text.append(UInt8(ascii: "-"))
    for index in 10..<16 {
      appendByte(identifier[index], to: &text)
    }
    return String(bytes: text, encoding: .utf8)
  }

  static func isCanonicalUUID(_ text: String) -> Bool {
    guard text.utf8.count == SyncLimits.uuidTextBytes else {
      return false
    }
    let scalars = Array(text.unicodeScalars)
    let dashes = [8, 13, 18, 23]
    for (index, scalar) in scalars.enumerated() {
      if dashes.contains(index) {
        if scalar != "-" {
          return false
        }
      } else if !isLowerHex(scalar) {
        return false
      }
    }
    return true
  }

  static func validateAnchor(_ record: RawRecord) -> Data? {
    guard record.type == CloudNames.anchorType,
      record.name == CloudNames.anchorName,
      Set(record.allKeys) == Set(CloudNames.anchorFields)
    else {
      return nil
    }
    var combined = Data()
    for field in CloudNames.anchorFields {
      guard let value = record.fields[field], value.count == SyncLimits.identifierBytes else {
        return nil
      }
      combined.append(value)
    }
    guard combined.count == SyncLimits.anchorBytes else {
      return nil
    }
    return combined
  }

  static func validateBundle(_ record: RawRecord) -> (Data, Data)? {
    guard record.type == CloudNames.bundleType,
      isCanonicalUUID(record.name),
      Set(record.allKeys) == Set([CloudNames.bundlePayloadField]),
      let payload = record.fields[CloudNames.bundlePayloadField],
      !payload.isEmpty,
      payload.count <= SyncLimits.bundleBytes,
      let identifier = identifierBytes(from: record.name),
      uuidText(from: identifier) == record.name
    else {
      return nil
    }
    return (identifier, payload)
  }

  static func archiveToken(_ token: CKServerChangeToken) -> Data? {
    guard
      let data = try? NSKeyedArchiver.archivedData(
        withRootObject: token,
        requiringSecureCoding: true
      ),
      data.count <= SyncLimits.cursorBytes
    else {
      return nil
    }
    return data
  }

  static func unarchiveToken(_ data: Data) -> CKServerChangeToken? {
    guard !data.isEmpty, data.count <= SyncLimits.cursorBytes else {
      return nil
    }
    return try? NSKeyedUnarchiver.unarchivedObject(ofClass: CKServerChangeToken.self, from: data)
  }

  static func identifierBytes(from text: String) -> Data? {
    guard isCanonicalUUID(text) else {
      return nil
    }
    var bytes = Data(count: SyncLimits.bundleIdentifierBytes)
    var offset = 0
    var index = text.startIndex
    while index < text.endIndex {
      if text[index] == "-" {
        index = text.index(after: index)
        continue
      }
      let next = text.index(after: index)
      guard next < text.endIndex,
        let high = hexValue(text[index]),
        let low = hexValue(text[next])
      else {
        return nil
      }
      bytes[offset] = UInt8((high << 4) | low)
      offset += 1
      index = text.index(after: next)
    }
    return offset == SyncLimits.bundleIdentifierBytes ? bytes : nil
  }

  private static func appendByte(_ byte: UInt8, to text: inout [UInt8]) {
    text.append(hexDigits[Int(byte >> 4)])
    text.append(hexDigits[Int(byte & 0x0F)])
  }

  private static func isLowerHex(_ scalar: Unicode.Scalar) -> Bool {
    return (scalar >= "0" && scalar <= "9") || (scalar >= "a" && scalar <= "f")
  }

  private static func hexValue(_ character: Character) -> Int? {
    guard let ascii = character.asciiValue else {
      return nil
    }
    switch character {
    case "0"..."9":
      return Int(ascii - UInt8(ascii: "0"))
    case "a"..."f":
      return Int(ascii - UInt8(ascii: "a") + 10)
    default:
      return nil
    }
  }
}

enum PageCodec {
  static func encode(_ page: ChangePageFields) -> Data? {
    guard page.cursor.count <= SyncLimits.cursorBytes else {
      return nil
    }
    var encoded = Data()
    encoded.append(page.moreComing ? 1 : 0)
    encoded.appendBigEndian(UInt32(page.cursor.count))
    encoded.append(page.cursor)
    switch (page.bundleIdentifier, page.bundle) {
    case (.some(let identifier), .some(let bundle)):
      guard identifier.count == SyncLimits.bundleIdentifierBytes,
        !bundle.isEmpty,
        bundle.count <= SyncLimits.bundleBytes
      else {
        return nil
      }
      encoded.append(1)
      encoded.append(identifier)
      encoded.appendBigEndian(UInt32(bundle.count))
      encoded.append(bundle)
    case (nil, nil):
      encoded.append(0)
    default:
      return nil
    }
    guard encoded.count <= SyncLimits.maximumResponsePayloadBytes else {
      return nil
    }
    return encoded
  }

  static func decode(_ encoded: Data) -> ChangePageFields? {
    var cursor = PageCursor(data: encoded)
    guard let moreByte = cursor.readByte(), moreByte <= 1,
      let cursorLength = cursor.readLength(limit: SyncLimits.cursorBytes),
      let token = cursor.readData(count: cursorLength),
      let presentByte = cursor.readByte(), presentByte <= 1
    else {
      return nil
    }
    var identifier: Data?
    var bundle: Data?
    if presentByte == 1 {
      guard let rawIdentifier = cursor.readData(count: SyncLimits.bundleIdentifierBytes),
        let bundleLength = cursor.readLength(limit: SyncLimits.bundleBytes),
        bundleLength > 0,
        let rawBundle = cursor.readData(count: bundleLength)
      else {
        return nil
      }
      identifier = rawIdentifier
      bundle = rawBundle
    }
    guard cursor.isDrained else {
      return nil
    }
    return ChangePageFields(
      moreComing: moreByte == 1,
      cursor: token,
      bundleIdentifier: identifier,
      bundle: bundle
    )
  }
}

enum CloudRequestCodec {
  static func bindingOnly(_ payload: Data) -> Data? {
    guard payload.count == SyncLimits.bindingBytes else {
      return nil
    }
    return payload.prefix(SyncLimits.bindingBytes)
  }

  static func anchorRequest(_ payload: Data) -> (binding: Data, fields: Data)? {
    guard payload.count == SyncLimits.bindingBytes + SyncLimits.anchorBytes else {
      return nil
    }
    return (
      Data(payload.prefix(SyncLimits.bindingBytes)),
      Data(payload.suffix(SyncLimits.anchorBytes))
    )
  }

  static func bundleRequest(_ payload: Data) -> BundleParts? {
    let minimum = SyncLimits.bindingBytes + SyncLimits.bundleIdentifierBytes + 1
    guard payload.count >= minimum,
      payload.count <= SyncLimits.maximumPayloadBytes
    else {
      return nil
    }
    let bundle = payload.suffix(from: SyncLimits.bindingBytes + SyncLimits.bundleIdentifierBytes)
    guard bundle.count <= SyncLimits.bundleBytes else {
      return nil
    }
    return BundleParts(
      binding: payload.prefix(SyncLimits.bindingBytes),
      identifier: payload.subdata(
        in: SyncLimits.bindingBytes..<(SyncLimits.bindingBytes + SyncLimits.bundleIdentifierBytes)
      ),
      bundle: Data(bundle)
    )
  }

  static func cursorRequest(_ payload: Data) -> (binding: Data, cursor: Data)? {
    // The extra byte admits the delete-path phase prefix; fetch cursors
    // are still strictly validated downstream by unarchiveToken, and sweep
    // cursors by their handler, so all three keep their exact outcomes.
    guard payload.count >= SyncLimits.bindingBytes,
      payload.count <= SyncLimits.bindingBytes + SyncLimits.cursorBytes
        + SyncLimits.deleteResumePhaseBytes
    else {
      return nil
    }
    return (
      payload.prefix(SyncLimits.bindingBytes),
      Data(payload.suffix(from: SyncLimits.bindingBytes))
    )
  }
}

private struct PageCursor {
  let data: Data
  var offset = 0

  init(data: Data) {
    self.data = Data(data)
  }

  var isDrained: Bool {
    return offset == data.count
  }

  mutating func readByte() -> UInt8? {
    guard offset < data.count else {
      return nil
    }
    defer { offset += 1 }
    return data[offset]
  }

  mutating func readLength(limit: Int) -> Int? {
    guard data.count - offset >= 4 else {
      return nil
    }
    let value = data.subdata(in: offset..<(offset + 4)).reduce(UInt32(0)) { partial, byte in
      (partial << 8) | UInt32(byte)
    }
    offset += 4
    guard value <= limit else {
      return nil
    }
    return Int(value)
  }

  mutating func readData(count: Int) -> Data? {
    guard count >= 0, data.count - offset >= count else {
      return nil
    }
    defer { offset += count }
    return data.subdata(in: offset..<(offset + count))
  }
}
