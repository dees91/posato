import CryptoKit
import Foundation

/// The value of a service's `ExceptionsList` (ADR 0004, MACOS-024 amendment). Entries compare by their UTF-8 bytes,
/// never by Unicode canonical equivalence, so a list that differs only in normalization is a different list.
public enum ProxyExceptions: Equatable, Sendable {
  case absent
  case list([String])
  /// A value Posato cannot represent within the D4 bounds: not an array, a non-string entry, more than
  /// `maximumEntries` entries, or an entry longer than `maximumEntryBytes`. It carries a digest of the raw value so
  /// that compare-and-swap still detects a change, and it never equals a recorded list.
  case unreadable(Data)

  /// The D4 bound on a baseline list that Apply accepts.
  public static let maximumEntries = 256
  public static let maximumEntryBytes = 2_048
  /// The entries Posato adds while its proxy is applied, in this order.
  public static let loopbackEntries = ["localhost", "127.0.0.1", "::1"]
  /// A list Posato itself applied to a baseline at the D4 bound must still read back as a list.
  public static let maximumReadableEntries = maximumEntries + loopbackEntries.count

  public static func == (lhs: ProxyExceptions, rhs: ProxyExceptions) -> Bool {
    switch (lhs, rhs) {
    case (.absent, .absent):
      return true
    case (.list(let first), .list(let second)):
      return first.map { Array($0.utf8) } == second.map { Array($0.utf8) }
    case (.unreadable(let first), .unreadable(let second)):
      return first == second
    default:
      return false
    }
  }

  var entries: [String]? {
    switch self {
    case .absent:
      return []
    case .list(let entries):
      return entries
    case .unreadable:
      return nil
    }
  }

  /// SHA-256 over the entry count and, per entry in order, its length and raw UTF-8 bytes (all big-endian UInt32).
  static func digest(_ entries: [String]) -> Data {
    var encoded = Data()
    append(UInt32(entries.count), to: &encoded)
    for entry in entries {
      let bytes = Array(entry.utf8)
      append(UInt32(bytes.count), to: &encoded)
      encoded.append(contentsOf: bytes)
    }
    return Data(SHA256.hash(data: encoded))
  }

  /// The loopback entries a baseline lacks, compared by exact bytes, in the fixed order.
  public static func missingLoopbackEntries(in entries: [String]) -> [String] {
    let present = Set(entries.map { Array($0.utf8) })
    return loopbackEntries.filter { !present.contains(Array($0.utf8)) }
  }

  private static func append(_ value: UInt32, to data: inout Data) {
    withUnsafeBytes(of: value.bigEndian) { data.append(contentsOf: $0) }
  }
}

/// What a mutation does with `ExceptionsList`; the resulting dictionary is always built from the locked re-read.
public enum ProxyExceptionsTarget: Equatable, Sendable {
  case untouched
  case set([String])
  case remove
}
