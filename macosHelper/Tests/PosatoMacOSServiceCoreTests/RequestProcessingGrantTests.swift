import Foundation
import Testing

@testable import PosatoMacOSServiceCore
@testable import PosatoProxySettingsDaemon

private let person: UInt32 = 501
private let otherPerson: UInt32 = 502
private let personAccount = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
private let otherAccount = UUID(uuidString: "22222222-2222-2222-2222-222222222222")!
private let thisMac = UUID(uuidString: "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA")!
private let listenerPort = Data([0xC3, 0x51])

private final class EventLog: @unchecked Sendable {
  var events: [String] = []
}

private final class FakeRules: AuthorizationRules, @unchecked Sendable {
  var states: [AuthorizationRight: AuthorizationRuleState] = [
    .apply: .exact, .standingApply: .exact,
  ]
  var acceptsForm = true
  var unreadableRight: AuthorizationRight?
  let log: EventLog

  init(log: EventLog) {
    self.log = log
  }

  func state(of right: AuthorizationRight) throws -> AuthorizationRuleState {
    guard right != unreadableRight else {
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
    return states[right] ?? .absent
  }

  func write(_ right: AuthorizationRight) throws {
    log.events.append("write \(right)")
    states[right] = .exact
  }

  func remove(_ right: AuthorizationRight) throws {
    log.events.append("remove \(right)")
    states[right] = .absent
  }

  func validateAndDestroy(_ right: AuthorizationRight, externalForm: inout Data) throws {
    log.events.append("validate \(right)")
    externalForm.resetBytes(in: externalForm.startIndex..<externalForm.endIndex)
    guard acceptsForm else {
      throw AuthorizationPolicyFailure.denied
    }
  }
}

private final class MemoryGrants: StandingGrantPersistence, @unchecked Sendable {
  var record: StandingGrantRecord?
  var unreadable = false
  var ioFailure = false
  let log: EventLog

  init(log: EventLog) {
    self.log = log
  }

  func load() throws -> StandingGrantRecord? {
    guard !unreadable else {
      throw DurableOwnershipFailure.invalidState
    }
    guard !ioFailure else {
      throw DurableOwnershipFailure.unavailable
    }
    return record
  }

  func save(_ record: StandingGrantRecord) throws {
    log.events.append("save grants")
    self.record = record
  }

  func remove() throws {
    log.events.append("remove grants")
    record = nil
    unreadable = false
  }
}

private struct Identity: SystemIdentity {
  func accountIdentifier(for userID: UInt32) -> UUID? {
    return [person: personAccount, otherPerson: otherAccount][userID]
  }

  func platformIdentifier() -> UUID? {
    return thisMac
  }

  func consoleUserID() -> UInt32? {
    return person
  }
}

private func grantedEntry(
  _ userID: UInt32 = person,
  account: UUID = personAccount
) -> StandingGrantEntry {
  return StandingGrantEntry(
    userID: userID,
    accountIdentifier: account,
    platformIdentifier: thisMac,
    grantedAt: Date(timeIntervalSince1970: 1_790_000_000)
  )
}

private final class Daemon {
  let log = EventLog()
  let ownership = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration()
  let rules: FakeRules
  let grants: MemoryGrants
  let coordinator: RequestCoordinator
  private var sequence: UInt32 = 0

  init(granted: [StandingGrantEntry] = [grantedEntry()]) {
    rules = FakeRules(log: log)
    grants = MemoryGrants(log: log)
    grants.record = granted.isEmpty ? nil : StandingGrantRecord(entries: granted)
    coordinator = RequestCoordinator(
      persistence: ownership,
      configuration: configuration,
      grants: grants,
      identity: Identity(),
      rules: rules
    )
  }

  func reply(
    _ operation: WireOperation,
    payload: Data = Data(),
    peer: UInt32? = person,
    requestIdentifier: Data? = nil
  ) throws -> Data {
    sequence += 1
    let request = try WireMessage(
      kind: .request,
      operation: operation,
      sequence: sequence,
      deadlineMilliseconds: 5_000,
      connectionIdentifier: Data(repeating: 7, count: 16),
      sessionIdentifier: Data(repeating: 8, count: 16),
      requestIdentifier: requestIdentifier
        ?? Data(repeating: UInt8(truncatingIfNeeded: sequence), count: 16),
      payload: payload
    )
    let encoded = try #require(
      coordinator.process(
        WireCodec.encode(request),
        peerUserID: peer,
        deadline: .now() + .seconds(5),
        connectionState: ConnectionState()
      )
    )
    return try WireCodec.decode(encoded, maximumBytes: WireLimits.maximumXPCBytes).payload
  }

  func send(
    _ operation: WireOperation,
    payload: Data = Data(),
    peer: UInt32? = person,
    requestIdentifier: Data? = nil
  ) throws -> WireResponsePayload {
    return try WireResponsePayload.decodeStatus(
      reply(operation, payload: payload, peer: peer, requestIdentifier: requestIdentifier)
    )
    .response
  }

  func reconcile(_ original: WireOperation) throws -> WireResponsePayload {
    let payload = try WireReconcilePayload(
      originalOperation: original,
      canonicalInputDigest: WireCodec.canonicalInputDigest(operation: original, payload: Data())
    )
    return try send(.reconcile, payload: payload.encode())
  }
}

private let externalForm = Data(repeating: 1, count: AuthorizationPolicy.externalFormBytes)

@Test func givenValidGrantWhenApplyWithGrantArrivesThenItAppliesWithoutAnExternalForm() throws {
  let daemon = Daemon()

  let response = try daemon.send(.applyWithGrant, payload: listenerPort)

  #expect(response.outcome == .success)
  #expect(response.ownershipPhase == .applied)
  #expect(daemon.ownership.record != nil)
  #expect(!daemon.log.events.contains { $0.hasPrefix("validate") })
}

@Test func givenNoUsableGrantWhenApplyWithGrantArrivesThenItFailsBeforeAnyDurableClaim() throws {
  let noRecord = Daemon(granted: [])
  let otherUserOnly = Daemon(granted: [grantedEntry(otherPerson, account: otherAccount)])
  let recreatedAccount = Daemon(granted: [grantedEntry(account: otherAccount)])
  let unreadable = Daemon()
  unreadable.grants.unreadable = true
  let missingStandingRule = Daemon()
  missingStandingRule.rules.states[.standingApply] = .mismatched
  let cases: [(Daemon, UInt32?)] = [
    (noRecord, person), (otherUserOnly, person), (recreatedAccount, person), (unreadable, person),
    (missingStandingRule, person), (Daemon(), nil), (Daemon(), otherPerson),
  ]

  for (daemon, peer) in cases {
    let response = try daemon.send(.applyWithGrant, payload: listenerPort, peer: peer)
    #expect(response.outcome == .failure)
    #expect(response.failure == .standingGrantUnavailable)
    #expect(daemon.ownership.record == nil)
  }
}

@Test func givenStatusWhenGrantStateIsRequestedThenOnlyASuccessfulReplyCarriesIt() throws {
  let daemon = Daemon()
  let withoutGrant = Daemon(granted: [])
  let brokenApplyRule = Daemon()
  brokenApplyRule.rules.states[.apply] = .absent

  #expect(try daemon.reply(.status).count == 5)
  #expect(
    try daemon.reply(.status, payload: WireStatusRequest.includeGrantState)
      == Data([1, 3, 1, 0, 0, 3]))
  #expect(try withoutGrant.reply(.status, payload: WireStatusRequest.includeGrantState).last == 1)
  #expect(
    try daemon.reply(.status, payload: WireStatusRequest.includeGrantState, peer: otherPerson).last
      == 1)
  #expect(
    try brokenApplyRule.reply(.status, payload: WireStatusRequest.includeGrantState).count == 5)
}

@Test func givenDisableThenGrantsAreDeletedButASessionRestoreKeepsThem() throws {
  let daemon = Daemon()
  let reconciled = Daemon()

  #expect(try daemon.send(.restore).outcome == .success)
  #expect(daemon.grants.record != nil)
  #expect(try daemon.send(.disable).outcome == .success)
  #expect(daemon.grants.record == nil)
  #expect(try reconciled.reconcile(.disable).outcome == .success)
  #expect(reconciled.grants.record == nil)
}

@Test func givenRemoveThenGrantsAreDeletedBeforeTheRules() throws {
  for reconciled in [false, true] {
    let daemon = Daemon()

    let response = try reconciled ? daemon.reconcile(.remove) : daemon.send(.remove)

    #expect(response.outcome == .success)
    #expect(daemon.log.events == ["remove grants", "remove apply", "remove standingApply"])
  }
}

@Test func givenEnableOrRepairWhenARuleIsWrittenThenGrantsAreDeletedFirst() throws {
  let absentApply = Daemon()
  absentApply.rules.states = [:]
  let exact = Daemon()
  let mismatchedOnEnable = Daemon()
  mismatchedOnEnable.rules.states[.standingApply] = .mismatched
  let mismatchedOnRepair = Daemon()
  mismatchedOnRepair.rules.states[.standingApply] = .mismatched
  let reconciledRepair = Daemon()
  reconciledRepair.rules.states[.apply] = .mismatched

  #expect(try absentApply.send(.enable).outcome == .success)
  #expect(
    absentApply.log.events == [
      "remove grants", "write apply", "remove grants", "write standingApply",
    ])
  #expect(try exact.send(.repair).outcome == .success)
  #expect(exact.log.events.isEmpty && exact.grants.record != nil)
  #expect(try mismatchedOnEnable.send(.enable).actionRequired == .ruleRepair)
  #expect(mismatchedOnEnable.grants.record != nil)
  #expect(try mismatchedOnRepair.send(.repair).outcome == .success)
  #expect(mismatchedOnRepair.log.events == ["remove grants", "write standingApply"])
  #expect(try reconciledRepair.reconcile(.repair).outcome == .success)
  #expect(reconciledRepair.log.events == ["remove grants", "write apply"])
}

@Test func givenPrepareGrantThenOnlyAnAbsentStandingRuleIsInstalledAfterDeletingGrants() throws {
  let absent = Daemon()
  absent.rules.states[.standingApply] = .absent
  let mismatched = Daemon()
  mismatched.rules.states[.standingApply] = .mismatched

  #expect(try absent.send(.prepareGrant).outcome == .success)
  #expect(absent.log.events == ["remove grants", "write standingApply"])
  #expect(try Daemon().send(.prepareGrant).outcome == .success)
  #expect(try mismatched.send(.prepareGrant).actionRequired == .ruleRepair)
  #expect(mismatched.log.events.isEmpty)
}

@Test func givenGrantThenTheCallersEntryIsRecordedOnlyAfterTheOptInFormIsValidated() throws {
  let declined = Daemon(granted: [])
  declined.rules.acceptsForm = false
  let noPeer = Daemon(granted: [])
  let granted = Daemon(granted: [])

  #expect(
    try declined.send(.grant, payload: externalForm).actionRequired == .administratorAuthentication)
  #expect(declined.grants.record == nil)
  #expect(
    try noPeer.send(.grant, payload: externalForm, peer: nil).failure == .standingGrantUnavailable)
  #expect(noPeer.grants.record == nil)
  #expect(try granted.send(.grant, payload: externalForm).outcome == .success)
  #expect(granted.log.events == ["validate standingApply", "save grants"])
  #expect(granted.grants.record?.entries.map(\.userID) == [person])
}

@Test func givenRevokeThenOnlyTheCallersEntryIsRemovedAndAnUnreadableRecordIsDeleted() throws {
  let shared = Daemon(granted: [grantedEntry(), grantedEntry(otherPerson, account: otherAccount)])
  let unreadable = Daemon()
  unreadable.grants.unreadable = true

  #expect(try shared.send(.revokeGrant).outcome == .success)
  #expect(shared.grants.record?.entries.map(\.userID) == [otherPerson])
  #expect(try shared.send(.revokeGrant).outcome == .success)
  #expect(try shared.send(.revokeGrant, peer: otherPerson).outcome == .success)
  #expect(shared.grants.record == nil)
  #expect(try unreadable.send(.revokeGrant).outcome == .success)
  #expect(unreadable.log.events == ["remove grants"])
}

@Test func givenLostApplyWithGrantReplyWhenReconciledAsApplyThenTheSameIntentIsFound() throws {
  let daemon = Daemon()
  let intent = Data(repeating: 0x42, count: 16)
  _ = try daemon.send(.applyWithGrant, payload: listenerPort, requestIdentifier: intent)
  let reconcile = try WireReconcilePayload(
    originalOperation: .apply,
    canonicalInputDigest: WireCodec.canonicalInputDigest(operation: .apply, payload: listenerPort)
  )

  let response = try daemon.send(.reconcile, payload: reconcile.encode(), requestIdentifier: intent)

  #expect(response.outcome == .success)
  #expect(response.ownershipPhase == .applied)
}

@Test func givenUnusableRecordWhenRepairedThenItIsDeletedEvenWithExactRules() throws {
  let daemon = Daemon()
  daemon.grants.unreadable = true

  #expect(try daemon.send(.repair).outcome == .success)
  #expect(daemon.log.events == ["remove grants"])
}

@Test func givenUnreadableStoreWhenGrantedThenOtherAccountsGrantsAreNotOverwritten() throws {
  let daemon = Daemon(granted: [grantedEntry(otherPerson, account: otherAccount)])
  daemon.grants.ioFailure = true

  #expect(try daemon.send(.grant, payload: externalForm).failure == .storage)
  #expect(daemon.grants.record?.entries.map(\.userID) == [otherPerson])
}

@Test func givenUnreadableGrantStoreWhenRevokedThenStatusNeverReportsTheGrantOff() throws {
  let daemon = Daemon()
  daemon.grants.ioFailure = true

  #expect(try daemon.send(.revokeGrant).failure == .storage)
  let status = try daemon.reply(.status, payload: WireStatusRequest.includeGrantState)
  #expect(status.count == 5)
  #expect(try WireResponsePayload.decodeStatus(status).response.failure == .storage)
  daemon.grants.ioFailure = false
  #expect(try daemon.reply(.status, payload: WireStatusRequest.includeGrantState).last == 3)
}

@Test func givenUnconfirmedGrantStateWhenStatusIsFlaggedThenOnlyAnUnusableRecordReadsOff() throws {
  let unreadableRule = Daemon()
  unreadableRule.rules.unreadableRight = .standingApply
  let invalidRecord = Daemon()
  invalidRecord.grants.unreadable = true

  #expect(
    try unreadableRule.reply(.status, payload: WireStatusRequest.includeGrantState).count == 5)
  #expect(
    try invalidRecord.reply(.status, payload: WireStatusRequest.includeGrantState)
      == Data([1, 3, 1, 0, 0, 1]))
}
