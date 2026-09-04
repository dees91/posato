import Foundation

struct WireDataCursor {
  let data: Data
  var offset = 0

  var remainingBytes: Int {
    data.count - offset
  }

  mutating func readUInt8() throws -> UInt8 {
    guard remainingBytes >= 1 else {
      throw WireProtocolFailure.invalidFrame
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

  mutating func readData(count: Int) throws -> Data {
    guard count >= 0, remainingBytes >= count else {
      throw WireProtocolFailure.invalidFrame
    }
    defer { offset += count }
    return data.subdata(in: offset..<(offset + count))
  }
}
