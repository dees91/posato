import Foundation

enum SyncLimits {
  static let protocolMajor: UInt16 = 1
  static let magic: UInt32 = 0x5053_594E
  static let headerBytes = 40
  static let identifierBytes = 16
  static let bindingBytes = 32
  static let accountBytes = 36
  static let keyBytes = 32
  static let checksumBytes = 4
  static let itemBytes = 84
  static let maximumPayloadBytes = 65_536
  static let maximumFrameBytes = headerBytes + maximumPayloadBytes
  static let maximumDeadlineMilliseconds: UInt32 = 120_000
  static let keychainCapability: UInt64 = 1
  static let containerIdentifier = "iCloud.app.posato.sync"
  static let keyService = "app.posato.sync.workspace-key.v1"
  static let accessGroupSuffix = "app.posato.sync"
  static let companionIdentifier = "app.posato.macos.sync"
  static let applicationIdentifier = "app.posato.macos"
  static let companionBundleName = "PosatoMacOSSync.app"
  static let bindingPrefix = "iCloud.app.posato.sync|account-binding|v1"
}

enum SyncOperation: UInt8, Sendable {
  case resolveBinding = 1
  case readItem = 2
  case createItem = 3
  case deleteItemAndVerifyAbsent = 4
}

enum SyncOutcome: UInt8, Sendable {
  case found = 1
  case missing = 2
  case created = 3
  case identical = 4
  case retryable = 5
  case accountChanged = 6
  case unknownOutcome = 7
  case integrityFailure = 8
  case unavailable = 9
  case restricted = 10
  case undetermined = 11
  case deletedAndAbsent = 12
}

enum SyncProtocolFailure: Error, Equatable {
  case invalidFrame
  case invalidVersion
  case invalidOperation
  case invalidDeadline
  case oversizedFrame
}

struct SyncMessage: Equatable, Sendable, CustomStringConvertible {
  var operation: SyncOperation
  var requestIdentifier: Data
  var deadlineMilliseconds: UInt32
  var capabilities: UInt64
  var outcome: SyncOutcome?
  var payload: Data

  var description: String {
    return "SyncMessage(redacted)"
  }

  func respond(outcome: SyncOutcome, payload: Data = Data()) -> SyncMessage {
    var response = self
    response.outcome = outcome
    response.payload = payload
    return response
  }

  mutating func clear() {
    payload.resetBytes(in: payload.startIndex..<payload.endIndex)
  }
}

enum SyncCodec {
  static func encode(_ message: SyncMessage) throws -> Data {
    guard message.requestIdentifier.count == SyncLimits.identifierBytes,
      message.payload.count <= SyncLimits.maximumPayloadBytes
    else {
      throw SyncProtocolFailure.invalidFrame
    }
    var encoded = Data()
    encoded.appendBigEndian(SyncLimits.magic)
    encoded.appendBigEndian(SyncLimits.protocolMajor)
    encoded.append(message.operation.rawValue)
    encoded.append(message.requestIdentifier)
    encoded.appendBigEndian(message.deadlineMilliseconds)
    encoded.appendBigEndian(message.capabilities)
    encoded.append(message.outcome?.rawValue ?? 0)
    encoded.appendBigEndian(UInt32(message.payload.count))
    encoded.append(message.payload)
    guard encoded.count <= SyncLimits.maximumFrameBytes else {
      throw SyncProtocolFailure.oversizedFrame
    }
    return encoded
  }

  static func decode(_ encoded: Data) throws -> SyncMessage {
    guard encoded.count <= SyncLimits.maximumFrameBytes else {
      throw SyncProtocolFailure.oversizedFrame
    }
    guard encoded.count >= SyncLimits.headerBytes else {
      throw SyncProtocolFailure.invalidFrame
    }
    var cursor = DataCursor(data: encoded)
    guard try cursor.readUInt32() == SyncLimits.magic else {
      throw SyncProtocolFailure.invalidFrame
    }
    guard try cursor.readUInt16() == SyncLimits.protocolMajor else {
      throw SyncProtocolFailure.invalidVersion
    }
    guard let operation = SyncOperation(rawValue: try cursor.readUInt8()) else {
      throw SyncProtocolFailure.invalidOperation
    }
    let requestIdentifier = try cursor.readData(count: SyncLimits.identifierBytes)
    guard requestIdentifier.contains(where: { byte in byte != 0 }) else {
      throw SyncProtocolFailure.invalidFrame
    }
    let deadline = try cursor.readUInt32()
    guard deadline > 0, deadline <= SyncLimits.maximumDeadlineMilliseconds else {
      throw SyncProtocolFailure.invalidDeadline
    }
    let capabilities = try cursor.readUInt64()
    let outcomeByte = try cursor.readUInt8()
    let outcome: SyncOutcome?
    if outcomeByte == 0 {
      outcome = nil
    } else if let decodedOutcome = SyncOutcome(rawValue: outcomeByte) {
      outcome = decodedOutcome
    } else {
      throw SyncProtocolFailure.invalidFrame
    }
    let payloadBytes = Int(try cursor.readUInt32())
    guard payloadBytes == cursor.remainingBytes,
      payloadBytes <= SyncLimits.maximumPayloadBytes
    else {
      throw SyncProtocolFailure.invalidFrame
    }
    let payload = try cursor.readData(count: payloadBytes)
    return SyncMessage(
      operation: operation,
      requestIdentifier: requestIdentifier,
      deadlineMilliseconds: deadline,
      capabilities: capabilities,
      outcome: outcome,
      payload: payload,
    )
  }
}

struct DataCursor {
  let data: Data
  var offset = 0

  var remainingBytes: Int {
    return data.count - offset
  }

  mutating func readUInt8() throws -> UInt8 {
    guard remainingBytes >= 1 else {
      throw SyncProtocolFailure.invalidFrame
    }
    defer { offset += 1 }
    return data[offset]
  }

  mutating func readUInt16() throws -> UInt16 {
    let bytes = try readData(count: 2)
    return bytes.reduce(UInt16(0)) { value, byte in
      (value << 8) | UInt16(byte)
    }
  }

  mutating func readUInt32() throws -> UInt32 {
    let bytes = try readData(count: 4)
    return bytes.reduce(UInt32(0)) { value, byte in
      (value << 8) | UInt32(byte)
    }
  }

  mutating func readUInt64() throws -> UInt64 {
    let bytes = try readData(count: 8)
    return bytes.reduce(UInt64(0)) { value, byte in
      (value << 8) | UInt64(byte)
    }
  }

  mutating func readData(count: Int) throws -> Data {
    guard count >= 0, remainingBytes >= count else {
      throw SyncProtocolFailure.invalidFrame
    }
    defer { offset += count }
    return data.subdata(in: offset..<(offset + count))
  }
}

extension Data {
  mutating func appendBigEndian(_ value: UInt16) {
    append(UInt8((value >> 8) & 0xFF))
    append(UInt8(value & 0xFF))
  }

  mutating func appendBigEndian(_ value: UInt32) {
    append(UInt8((value >> 24) & 0xFF))
    append(UInt8((value >> 16) & 0xFF))
    append(UInt8((value >> 8) & 0xFF))
    append(UInt8(value & 0xFF))
  }

  mutating func appendBigEndian(_ value: UInt64) {
    append(UInt8((value >> 56) & 0xFF))
    append(UInt8((value >> 48) & 0xFF))
    append(UInt8((value >> 40) & 0xFF))
    append(UInt8((value >> 32) & 0xFF))
    append(UInt8((value >> 24) & 0xFF))
    append(UInt8((value >> 16) & 0xFF))
    append(UInt8((value >> 8) & 0xFF))
    append(UInt8(value & 0xFF))
  }
}
