import Foundation

public enum StandingGrantFailure: Error, Equatable {
  case unavailable
}

public struct StandingGrantEntry: Codable, Equatable, Sendable {
  public let userID: UInt32
  public let accountIdentifier: UUID
  public let platformIdentifier: UUID
  public let grantedAt: Date

  public init(userID: UInt32, accountIdentifier: UUID, platformIdentifier: UUID, grantedAt: Date) {
    self.userID = userID
    self.accountIdentifier = accountIdentifier
    self.platformIdentifier = platformIdentifier
    self.grantedAt = grantedAt
  }
}

public struct StandingGrantRecord: Codable, Equatable, Sendable {
  public static let currentSchemaVersion = 1
  public static let maximumEntries = 8

  public let schemaVersion: Int
  public let entries: [StandingGrantEntry]

  public init(entries: [StandingGrantEntry]) {
    schemaVersion = Self.currentSchemaVersion
    self.entries = entries
  }

  public var hasValidBounds: Bool {
    return schemaVersion == Self.currentSchemaVersion
      && !entries.isEmpty
      && entries.count <= Self.maximumEntries
      && Set(entries.map(\.userID)).count == entries.count
  }
}

public protocol SystemIdentity {
  func accountIdentifier(for userID: UInt32) -> UUID?
  func platformIdentifier() -> UUID?
  func consoleUserID() -> UInt32?
}

public enum StandingGrantPolicy {
  public static func isGranted(
    record: StandingGrantRecord?,
    peerUserID: UInt32,
    identity: some SystemIdentity
  ) -> Bool {
    guard let record, let current = currentEntry(for: peerUserID, identity: identity) else {
      return false
    }
    return record.entries.contains { $0.matches(current) }
  }

  /// The console belongs to a person only when a real account owns it; `loginwindow` and root mean
  /// nobody is signed in at the Mac.
  public static func consoleUserID(name: String?, userID: UInt32) -> UInt32? {
    guard let name, name != "loginwindow", userID != 0 else {
      return nil
    }
    return userID
  }

  public static func granting(
    record: StandingGrantRecord?,
    peerUserID: UInt32,
    identity: some SystemIdentity,
    now: Date
  ) throws -> StandingGrantRecord {
    guard let current = currentEntry(for: peerUserID, identity: identity) else {
      throw StandingGrantFailure.unavailable
    }
    let kept = (record?.entries ?? []).filter { existing in
      existing.userID != peerUserID && isCurrent(existing, identity: identity)
    }
    guard kept.count < StandingGrantRecord.maximumEntries else {
      throw StandingGrantFailure.unavailable
    }
    let granted = StandingGrantEntry(
      userID: current.userID,
      accountIdentifier: current.accountIdentifier,
      platformIdentifier: current.platformIdentifier,
      grantedAt: now
    )
    return StandingGrantRecord(entries: kept + [granted])
  }

  public static func revoking(record: StandingGrantRecord, peerUserID: UInt32) -> StandingGrantRecord? {
    let kept = record.entries.filter { $0.userID != peerUserID }
    return kept.isEmpty ? nil : StandingGrantRecord(entries: kept)
  }

  private static func currentEntry(
    for peerUserID: UInt32,
    identity: some SystemIdentity
  ) -> StandingGrantEntry? {
    guard identity.consoleUserID() == peerUserID,
      let account = identity.accountIdentifier(for: peerUserID),
      let platform = identity.platformIdentifier()
    else {
      return nil
    }
    return StandingGrantEntry(
      userID: peerUserID,
      accountIdentifier: account,
      platformIdentifier: platform,
      grantedAt: .distantPast
    )
  }

  private static func isCurrent(_ entry: StandingGrantEntry, identity: some SystemIdentity) -> Bool {
    return identity.accountIdentifier(for: entry.userID) == entry.accountIdentifier
      && identity.platformIdentifier() == entry.platformIdentifier
  }
}

extension StandingGrantEntry {
  fileprivate func matches(_ current: StandingGrantEntry) -> Bool {
    return userID == current.userID
      && accountIdentifier == current.accountIdentifier
      && platformIdentifier == current.platformIdentifier
  }
}

public protocol StandingGrantPersistence: Sendable {
  func load() throws -> StandingGrantRecord?
  func save(_ record: StandingGrantRecord) throws
  func remove() throws
}

public final class StandingGrantStore: StandingGrantPersistence, @unchecked Sendable {
  public static let productionURL = URL(
    fileURLWithPath: "/Library/Application Support/Posato/ProxySettings/apply-grant-v1.plist"
  )

  private let file: ProtectedPlistFile

  public init(url: URL = productionURL, expectedOwner: uid_t = 0) {
    file = ProtectedPlistFile(url: url, expectedOwner: expectedOwner)
  }

  public func load() throws -> StandingGrantRecord? {
    guard let data = try file.load() else {
      return nil
    }
    guard let record = try? PropertyListDecoder().decode(StandingGrantRecord.self, from: data),
      record.hasValidBounds
    else {
      throw DurableOwnershipFailure.invalidState
    }
    return record
  }

  public func save(_ record: StandingGrantRecord) throws {
    guard record.hasValidBounds else {
      throw DurableOwnershipFailure.invalidState
    }
    let encoder = PropertyListEncoder()
    encoder.outputFormat = .binary
    try file.save(encoder.encode(record))
  }

  public func remove() throws {
    try file.remove()
  }
}
