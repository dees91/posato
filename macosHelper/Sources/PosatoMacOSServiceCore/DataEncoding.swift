import Foundation

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
