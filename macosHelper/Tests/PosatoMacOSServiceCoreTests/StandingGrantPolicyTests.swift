import Foundation
import Testing

@testable import PosatoMacOSServiceCore

private let person: UInt32 = 501
private let otherPerson: UInt32 = 502
private let personAccount = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
private let otherAccount = UUID(uuidString: "22222222-2222-2222-2222-222222222222")!
private let recreatedAccount = UUID(uuidString: "33333333-3333-3333-3333-333333333333")!
private let thisMac = UUID(uuidString: "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA")!
private let otherMac = UUID(uuidString: "BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB")!
private let grantedAt = Date(timeIntervalSince1970: 1_790_000_000)

private struct FixedIdentity: SystemIdentity {
  var accounts: [UInt32: UUID] = [person: personAccount, otherPerson: otherAccount]
  var platform: UUID? = thisMac
  var console: UInt32? = person

  func accountIdentifier(for userID: UInt32) -> UUID? {
    return accounts[userID]
  }

  func platformIdentifier() -> UUID? {
    return platform
  }

  func consoleUserID() -> UInt32? {
    return console
  }
}

private func entry(
  _ userID: UInt32 = person,
  account: UUID = personAccount,
  platform: UUID = thisMac
) -> StandingGrantEntry {
  return StandingGrantEntry(
    userID: userID,
    accountIdentifier: account,
    platformIdentifier: platform,
    grantedAt: grantedAt
  )
}

private func record(_ entries: [StandingGrantEntry]) -> StandingGrantRecord {
  return StandingGrantRecord(entries: entries)
}

@Test func givenMatchingEntryAndConsoleUserWhenEvaluatedThenApplyIsGranted() {
  #expect(
    StandingGrantPolicy.isGranted(
      record: record([entry()]), peerUserID: person, identity: FixedIdentity()))
}

@Test func givenBindingMismatchWhenEvaluatedThenApplyIsNotGranted() {
  var recreated = FixedIdentity()
  recreated.accounts[person] = recreatedAccount
  var missingAccount = FixedIdentity()
  missingAccount.accounts[person] = nil
  var movedMac = FixedIdentity()
  movedMac.platform = otherMac
  var noConsole = FixedIdentity()
  noConsole.console = nil
  var otherConsole = FixedIdentity()
  otherConsole.console = otherPerson
  let granted = record([entry()])

  #expect(
    !StandingGrantPolicy.isGranted(record: nil, peerUserID: person, identity: FixedIdentity()))
  #expect(
    !StandingGrantPolicy.isGranted(record: granted, peerUserID: otherPerson, identity: otherConsole)
  )
  #expect(!StandingGrantPolicy.isGranted(record: granted, peerUserID: person, identity: recreated))
  #expect(
    !StandingGrantPolicy.isGranted(record: granted, peerUserID: person, identity: missingAccount))
  #expect(!StandingGrantPolicy.isGranted(record: granted, peerUserID: person, identity: movedMac))
  #expect(!StandingGrantPolicy.isGranted(record: granted, peerUserID: person, identity: noConsole))
  #expect(
    !StandingGrantPolicy.isGranted(record: granted, peerUserID: person, identity: otherConsole))
}

@Test func givenConsoleSessionWhenNamedThenOnlyARealUserCounts() {
  #expect(StandingGrantPolicy.consoleUserID(name: "person", userID: person) == person)
  #expect(StandingGrantPolicy.consoleUserID(name: "loginwindow", userID: person) == nil)
  #expect(StandingGrantPolicy.consoleUserID(name: "root", userID: 0) == nil)
  #expect(StandingGrantPolicy.consoleUserID(name: nil, userID: person) == nil)
}

@Test func givenGrantWhenRecordedThenItReplacesTheSameUserAndDropsStaleEntries() throws {
  let stale = [
    entry(person, platform: otherMac),
    entry(otherPerson, account: recreatedAccount),
    entry(503, account: otherAccount),
  ]

  let granted = try StandingGrantPolicy.granting(
    record: record(stale),
    peerUserID: person,
    identity: FixedIdentity(),
    now: grantedAt
  )

  #expect(granted == record([entry()]))
}

@Test func givenGrantWhenPeerIsNotTheConsoleUserOrIdentityIsMissingThenItFailsClosed() {
  var otherConsole = FixedIdentity()
  otherConsole.console = otherPerson
  var noPlatform = FixedIdentity()
  noPlatform.platform = nil

  #expect(throws: StandingGrantFailure.unavailable) {
    try StandingGrantPolicy.granting(
      record: nil, peerUserID: person, identity: otherConsole, now: grantedAt)
  }
  #expect(throws: StandingGrantFailure.unavailable) {
    try StandingGrantPolicy.granting(
      record: nil, peerUserID: person, identity: noPlatform, now: grantedAt)
  }
}

@Test func givenFullRecordOfValidEntriesWhenAnotherUserIsGrantedThenItFailsClosed() {
  var identity = FixedIdentity()
  for userID in UInt32(600)..<UInt32(608) {
    identity.accounts[userID] = UUID()
  }
  let full = record(
    (UInt32(600)..<UInt32(608)).map { entry($0, account: identity.accounts[$0]!) }
  )

  #expect(throws: StandingGrantFailure.unavailable) {
    try StandingGrantPolicy.granting(
      record: full, peerUserID: person, identity: identity, now: grantedAt)
  }
}

@Test func givenRevokeWhenAppliedThenOnlyThePeersOwnEntryIsRemoved() {
  let both = record([entry(), entry(otherPerson, account: otherAccount)])

  #expect(
    StandingGrantPolicy.revoking(record: both, peerUserID: person, identity: FixedIdentity())
      == record([entry(otherPerson, account: otherAccount)])
  )
  #expect(
    StandingGrantPolicy.revoking(
      record: record([entry()]), peerUserID: person, identity: FixedIdentity()) == nil)
  #expect(
    StandingGrantPolicy.revoking(
      record: record([entry()]), peerUserID: otherPerson, identity: FixedIdentity())
      == record([entry()]))
}

@Test func givenStaleEntryWhenAnotherGrantIsRevokedThenItIsDroppedToo() {
  let stale = entry(otherPerson, account: recreatedAccount)
  let kept = entry(503, account: otherAccount)
  var identity = FixedIdentity()
  identity.accounts[503] = otherAccount

  #expect(
    StandingGrantPolicy.revoking(
      record: record([entry(), stale, kept]), peerUserID: person, identity: identity)
      == record([kept])
  )
}
