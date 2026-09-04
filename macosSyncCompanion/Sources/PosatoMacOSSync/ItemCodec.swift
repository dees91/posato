import Foundation

enum ItemCodec {
  private static let hexDigits = Array("0123456789abcdef".utf8)
  private static let crcPolynomial: UInt32 = 0xEDB8_8320
  private static let uuidVersionByte = 6
  private static let uuidVariantByte = 8

  static func crc32(_ bytes: Data, length: Int) -> UInt32 {
    var crc: UInt32 = 0xFFFF_FFFF
    for index in 0..<length {
      crc ^= UInt32(bytes[index])
      for _ in 0..<8 {
        if crc & 1 != 0 {
          crc = (crc >> 1) ^ crcPolynomial
        } else {
          crc >>= 1
        }
      }
    }
    return ~crc
  }

  static func isCanonicalAccount(_ text: String) -> Bool {
    guard text.utf8.count == SyncLimits.accountBytes else {
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

  static func accountText(from identifier: Data) -> String? {
    guard identifier.count == SyncLimits.identifierBytes else {
      return nil
    }
    var text = [UInt8]()
    text.reserveCapacity(SyncLimits.accountBytes)
    func appendByte(_ byte: UInt8) {
      text.append(hexDigits[Int(byte >> 4)])
      text.append(hexDigits[Int(byte & 0x0F)])
    }
    for index in 0..<4 {
      appendByte(identifier[index])
    }
    text.append(UInt8(ascii: "-"))
    for index in 4..<6 {
      appendByte(identifier[index])
    }
    text.append(UInt8(ascii: "-"))
    for index in 6..<8 {
      appendByte(identifier[index])
    }
    text.append(UInt8(ascii: "-"))
    for index in 8..<10 {
      appendByte(identifier[index])
    }
    text.append(UInt8(ascii: "-"))
    for index in 10..<16 {
      appendByte(identifier[index])
    }
    return String(bytes: text, encoding: .utf8)
  }

  static func identifierBytes(from account: String) -> Data? {
    guard isCanonicalAccount(account) else {
      return nil
    }
    var bytes = Data(count: SyncLimits.identifierBytes)
    var offset = 0
    var index = account.startIndex
    while index < account.endIndex {
      if account[index] == "-" {
        index = account.index(after: index)
        continue
      }
      let next = account.index(after: index)
      guard next < account.endIndex,
        let high = hexValue(account[index]),
        let low = hexValue(account[next])
      else {
        return nil
      }
      bytes[offset] = UInt8((high << 4) | low)
      offset += 1
      index = account.index(after: next)
    }
    return offset == SyncLimits.identifierBytes ? bytes : nil
  }

  static func validateItem(_ item: Data, account: String) -> Data? {
    guard item.count == SyncLimits.itemBytes else {
      return nil
    }
    let checksumOffset = SyncLimits.itemBytes - SyncLimits.checksumBytes
    let expected = crc32(item, length: checksumOffset)
    var stored: UInt32 = 0
    for index in 0..<SyncLimits.checksumBytes {
      stored = (stored << 8) | UInt32(item[checksumOffset + index])
    }
    guard stored == expected else {
      return nil
    }
    let workspace = item.prefix(SyncLimits.identifierBytes)
    guard isUuidV4(workspace),
      isUuidV4(item.subdata(in: 16..<32)),
      isUuidV4(item.subdata(in: 32..<48)),
      let expectedAccount = accountText(from: Data(workspace)),
      expectedAccount == account
    else {
      return nil
    }
    return item
  }

  static func constantTimeEquals(_ left: Data, _ right: Data) -> Bool {
    guard left.count == right.count else {
      return false
    }
    var difference: UInt8 = 0
    for index in 0..<left.count {
      difference |= left[index] ^ right[index]
    }
    return difference == 0
  }

  private static func isUuidV4(_ bytes: Data) -> Bool {
    guard bytes.count == SyncLimits.identifierBytes else {
      return false
    }
    return bytes[uuidVersionByte] & 0xF0 == 0x40 && bytes[uuidVariantByte] & 0xC0 == 0x80
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
