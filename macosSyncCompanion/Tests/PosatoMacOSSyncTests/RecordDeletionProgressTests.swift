import CloudKit
import Foundation
import Testing

@testable import PosatoMacOSSync

/// Append-only fake CloudKit history. The token is a big-endian history
/// index; deletes mark records gone and append tombstones without pruning,
/// so replays observe deletions exactly like the live change feed. Each test
/// drives several fresh `RecordDeletion` instances against one shared
/// harness: a new instance models a killed companion process, and only
/// server state survives across instances. Deadline tests with the virtual
/// wall clock live in `RecordDeletionDeadlineTests.swift`.
final class HistoryBackend: CloudBackend, @unchecked Sendable {
  enum Event {
    case upsert(RawRecord)
    case tombstone(String)
  }

  var history: [Event] = []
  var live: [String: RawRecord] = [:]
  var zoneGone = false
  var latency: TimeInterval = 0
  var deleteLatency: TimeInterval?
  var clock: ManualClock?
  var onAnchorDelete: (() -> Void)?
  private(set) var opLog: [String] = []
  private(set) var deleted: [[String]] = []
  private(set) var timeouts: [TimeInterval] = []

  private func tick(timeout: TimeInterval, latency override: TimeInterval? = nil) {
    timeouts.append(timeout)
    if let clock {
      clock.advance(override ?? latency)
    }
  }

  func seedBundles(_ names: [String]) {
    for name in names {
      let record = RawRecord(name: name, type: CloudNames.bundleType, fields: [:], allKeys: [])
      history.append(.upsert(record))
      live[name] = record
    }
  }

  func seedTombstones(_ names: [String]) {
    for name in names {
      history.append(.tombstone(name))
    }
  }

  func publishAnchor() {
    let record = RawRecord(
      name: CloudNames.anchorName, type: CloudNames.anchorType, fields: [:], allKeys: [])
    history.append(.upsert(record))
    live[CloudNames.anchorName] = record
  }

  func fetchZone(timeout: TimeInterval) -> ZoneLookup {
    opLog.append("fetchZone")
    return zoneGone ? .missing : .found
  }

  func saveZone(timeout: TimeInterval) -> BackendFault? {
    opLog.append("saveZone")
    return nil
  }

  func fetchRecord(name: String, timeout: TimeInterval) -> BackendLookup {
    tick(timeout: timeout)
    opLog.append("anchorRead:\(name)")
    if zoneGone {
      return .zoneMissing
    }
    guard let record = live[name] else {
      return .missing
    }
    return .found(record)
  }

  func saveRecord(_ record: RawRecord, timeout: TimeInterval) -> BackendSave {
    opLog.append("saveRecord")
    return .saved
  }

  func fetchChanges(tokenData: Data?, timeout: TimeInterval) -> BackendChangesResult {
    tick(timeout: timeout)
    let index: Int
    if let tokenData {
      guard tokenData.count == 8 else {
        return .failed(.unknown)
      }
      var value: UInt64 = 0
      for byte in tokenData {
        value = (value << 8) | UInt64(byte)
      }
      guard value <= UInt64(history.count) else {
        return .tokenExpired
      }
      index = Int(value)
    } else {
      index = 0
    }
    guard index < history.count else {
      opLog.append("fetchChanges:caught-up")
      return .fetched(
        BackendChanges(changed: [], deletedNames: [], token: encode(index), moreComing: false))
    }
    opLog.append("fetchChanges:\(index)")
    switch history[index] {
    case .upsert(let record):
      return .fetched(
        BackendChanges(
          changed: [record], deletedNames: [], token: encode(index + 1),
          moreComing: index + 1 < history.count))
    case .tombstone(let name):
      return .fetched(
        BackendChanges(
          changed: [], deletedNames: [name], token: encode(index + 1),
          moreComing: index + 1 < history.count))
    }
  }

  func deleteRecords(names: [String], timeout: TimeInterval) -> BackendDelete {
    tick(timeout: timeout, latency: deleteLatency)
    opLog.append("delete:\(names.joined(separator: ","))")
    deleted.append(names)
    if names.contains(CloudNames.anchorName) {
      onAnchorDelete?()
    }
    for name in names where live.removeValue(forKey: name) != nil {
      history.append(.tombstone(name))
    }
    return .deleted
  }

  private func encode(_ index: Int) -> Data {
    var value = UInt64(index).bigEndian
    return Data(bytes: &value, count: MemoryLayout<UInt64>.size)
  }
}

@Test func givenTombstonePrefixBeyondBudgetWhenDeletingThenAttemptsChainToCompletion() {
  let backend = HistoryBackend()
  backend.seedTombstones((0..<10).map { "gone-\($0)" })
  backend.seedBundles(["b1", "b2"])
  backend.publishAnchor()

  // Each instance models a killed companion process: only the returned
  // cursor and the server history survive. The second cursor is deliberately
  // dropped to model a response that never arrived.
  let first = RecordDeletion(backend: backend)
  let incomplete: Data
  switch first.deleteWorkspaceRecords(timeout: 5, pageBudget: 4) {
  case .incomplete(let cursor):
    incomplete = cursor
  case .deletedAndAbsent, .retryable, .unknownOutcome, .integrityFailure:
    Issue.record("expected incomplete inside the tombstone prefix")
    return
  }
  #expect(backend.deleted.isEmpty)

  let second = RecordDeletion(backend: backend)
  switch second.deleteWorkspaceRecords(timeout: 5, resumeToken: incomplete, pageBudget: 4) {
  case .incomplete:
    break
  case .deletedAndAbsent, .retryable, .unknownOutcome, .integrityFailure:
    Issue.record("expected second incomplete past the remaining prefix")
    return
  }

  let third = RecordDeletion(backend: backend)
  switch third.deleteWorkspaceRecords(timeout: 5, resumeToken: incomplete, pageBudget: 30) {
  case .deletedAndAbsent:
    break
  case .incomplete, .retryable, .unknownOutcome, .integrityFailure:
    Issue.record("expected completion once the budget covers the tail")
    return
  }
  #expect(backend.live.isEmpty)
  #expect(Set(backend.deleted.flatMap { $0 }) == ["b1", "b2", CloudNames.anchorName])
}

@Test func givenVerificationBeyondBudgetWhenDeletingThenResumeCompletesWithOneAnchorDelete() {
  let backend = HistoryBackend()
  backend.seedBundles(["b1"])
  backend.publishAnchor()
  // Twelve publishes race the anchor delete, so verification alone needs
  // several more passes after the drain and the anchor delete.
  backend.onAnchorDelete = {
    backend.seedTombstones((0..<12).map { "late-\($0)" })
  }

  let first = RecordDeletion(backend: backend)
  let incomplete: Data
  switch first.deleteWorkspaceRecords(timeout: 5, pageBudget: 4) {
  case .incomplete(let cursor):
    incomplete = cursor
  case .deletedAndAbsent, .retryable, .unknownOutcome, .integrityFailure:
    Issue.record("expected incomplete inside verification")
    return
  }
  guard let split = splitDeleteResumeToken(incomplete), split.phase == .verify else {
    Issue.record("expected a verify-phase cursor")
    return
  }

  let second = RecordDeletion(backend: backend)
  switch second.deleteWorkspaceRecords(timeout: 5, resumeToken: incomplete, pageBudget: 30) {
  case .deletedAndAbsent:
    break
  case .incomplete, .retryable, .unknownOutcome, .integrityFailure:
    Issue.record("expected verification to complete on resume")
    return
  }
  #expect(backend.live.isEmpty)
  #expect(backend.deleted.filter { $0 == [CloudNames.anchorName] }.count == 1)
}

@Test func givenAnchorPublishedDuringVerificationWhenResumingThenUnknownWithoutDeletes() {
  let backend = HistoryBackend()
  backend.seedBundles(["b1"])
  backend.onAnchorDelete = {
    backend.seedTombstones((0..<12).map { "late-\($0)" })
  }

  let first = RecordDeletion(backend: backend)
  let incomplete: Data
  switch first.deleteWorkspaceRecords(timeout: 5, pageBudget: 4) {
  case .incomplete(let cursor):
    incomplete = cursor
  case .deletedAndAbsent, .retryable, .unknownOutcome, .integrityFailure:
    Issue.record("expected incomplete inside verification")
    return
  }

  // A concurrent fresh attempt establishes a new workspace before the
  // resumed verification runs: the resumed pass must abort on the present
  // anchor instead of deleting or trusting anything.
  backend.publishAnchor()
  let deletesBefore = backend.deleted.count
  let second = RecordDeletion(backend: backend)
  #expect(
    second.deleteWorkspaceRecords(timeout: 5, resumeToken: incomplete, pageBudget: 30)
      == .unknownOutcome)
  #expect(backend.deleted.count == deletesBefore)
}

@Test func givenAppearingAnchorMidSweepWhenDeletingNextPageThenNothingAfterIsDeleted() {
  let backend = HistoryBackend()
  backend.seedBundles(["b1", "b2"])

  let first = RecordDeletion(backend: backend)
  let cursor: Data
  switch first.sweepBundlesIfAnchorMissing(timeout: 5, pageBudget: 1) {
  case .incomplete(let next):
    cursor = next
  case .swept, .anchorPresent, .retryable, .unknownOutcome:
    Issue.record("expected incomplete after the first swept page")
    return
  }
  #expect(backend.deleted == [["b1"]])
  let anchor = CloudNames.anchorName
  #expect(
    Array(backend.opLog.prefix(4)) == [
      "anchorRead:\(anchor)", "fetchChanges:0", "anchorRead:\(anchor)", "delete:b1",
    ])

  backend.seedBundles(["bx"])
  backend.publishAnchor()

  let second = RecordDeletion(backend: backend)
  #expect(second.sweepBundlesIfAnchorMissing(timeout: 5, resumeToken: cursor) == .anchorPresent)
  #expect(backend.live["bx"] != nil)
  #expect(backend.live[CloudNames.anchorName] != nil)
  #expect(backend.deleted == [["b1"]])
}

@Test func givenMixedDeleteErrorsWhenMappingThenOnlyRealErrorsFail() {
  func partial(_ items: [NSError]) -> NSError {
    var info: [AnyHashable: Any] = [:]
    for (index, item) in items.enumerated() {
      info[CKRecord.ID(recordName: "r\(index)", zoneID: testZoneID())] = item
    }
    return NSError(
      domain: CKError.errorDomain, code: CKError.partialFailure.rawValue,
      userInfo: [CKPartialErrorsByItemIDKey: info])
  }
  func item(_ code: CKError.Code) -> NSError {
    return NSError(domain: CKError.errorDomain, code: code.rawValue)
  }

  #expect(CloudErrorMapper.recordDelete(from: partial([item(.unknownItem)])) == .deleted)
  #expect(CloudErrorMapper.recordDelete(from: item(.unknownItem)) == .deleted)
  #expect(
    CloudErrorMapper.recordDelete(
      from: partial([item(.unknownItem), item(.serverRecordChanged)])) == .failed(.unknown)
  )
  #expect(
    CloudErrorMapper.recordDelete(
      from: partial([item(.unknownItem), item(.networkFailure)])) == .failed(.retryable)
  )
  #expect(
    CloudErrorMapper.recordDelete(from: item(.zoneNotFound)) == .deleted
  )
}

@Test func givenDeleteOperationFactoryWhenBuiltThenDeletionIsNonAtomic() {
  let operation = CKCloudDatabase.makeDeleteOperation(ids: [
    CKRecord.ID(recordName: "b1", zoneID: testZoneID())
  ])
  #expect(operation.isAtomic == false)
  #expect(operation.recordIDsToDelete?.count == 1)
}

private func testZoneID() -> CKRecordZone.ID {
  return CKRecordZone.ID(zoneName: "test-zone", ownerName: "__defaultOwner__")
}
