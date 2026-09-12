import CloudKit
import Foundation
import Testing

@testable import PosatoMacOSSync

/// Virtual wall clock for deadline tests: backend calls advance it by the
/// scripted latency while the pass under test reads it, so a whole
/// multi-second budget executes deterministically in microseconds. The fake
/// `HistoryBackend` lives in `RecordDeletionProgressTests.swift`; both files
/// compile into one test module.
final class ManualClock: @unchecked Sendable {
  var nowNanoseconds: UInt64 = 0

  func advance(_ seconds: TimeInterval) {
    nowNanoseconds += UInt64(seconds * 1_000_000_000)
  }
}

@Test func givenSlowPagesWhenDeletingThenCheckpointReturnsWithinBudget() {
  let clock = ManualClock()
  let backend = HistoryBackend()
  backend.clock = clock
  backend.latency = 1.0
  backend.seedBundles((1...6).map { "b\($0)" })

  let first = RecordDeletion(backend: backend)
  let incomplete: Data
  switch first.deleteWorkspaceRecords(
    timeout: 30, pageBudget: 30,
    deadlineNanoseconds: 6_000_000_000,
    nowNanoseconds: { clock.nowNanoseconds }
  ) {
  case .incomplete(let cursor):
    incomplete = cursor
  case .deletedAndAbsent, .retryable, .unknownOutcome, .integrityFailure:
    Issue.record("expected a wall-clock checkpoint")
    return
  }
  // Two pages complete with deletes inside the six-second budget; the third
  // page is banked but left for the resumed pass, and every backend call
  // receives the shrinking remainder instead of the original timeout.
  guard let split = splitDeleteResumeToken(incomplete), split.phase == .traverse else {
    Issue.record("expected a traverse-phase cursor")
    return
  }
  #expect(backend.deleted == [["b1"], ["b2"]])
  #expect(backend.timeouts.count == 4)
  #expect(zip(backend.timeouts, backend.timeouts.dropFirst()).allSatisfy(>))
  #expect(backend.timeouts.allSatisfy { $0 < 30 })

  let second = RecordDeletion(backend: backend)
  let resumed = second.deleteWorkspaceRecords(timeout: 30, resumeToken: incomplete, pageBudget: 30)
  switch resumed {
  case .deletedAndAbsent:
    break
  case .incomplete, .retryable, .unknownOutcome, .integrityFailure:
    Issue.record("expected completion on resume inside a fresh budget")
    return
  }
  #expect(backend.live.isEmpty)
}

@Test func givenSlowDeleteAfterFetchWhenDeletingThenCheckpointCoversOnlyDeletedPages() {
  // Fetches are fast but deletes are slow: the budget dies between the
  // second fetch and its deletes, so the checkpoint covers only the first
  // page and the resume replays the second. Banking the fetched page first
  // would hand back a cursor past live bundles the resume and the verify
  // scan never see.
  let clock = ManualClock()
  let backend = HistoryBackend()
  backend.clock = clock
  backend.latency = 0.5
  backend.deleteLatency = 3.0
  backend.seedBundles(["b1", "b2"])

  let first = RecordDeletion(backend: backend)
  let incomplete: Data
  switch first.deleteWorkspaceRecords(
    timeout: 30, pageBudget: 30,
    deadlineNanoseconds: 6_000_000_000,
    nowNanoseconds: { clock.nowNanoseconds }
  ) {
  case .incomplete(let cursor):
    incomplete = cursor
  case .deletedAndAbsent, .retryable, .unknownOutcome, .integrityFailure:
    Issue.record("expected a wall-clock checkpoint")
    return
  }
  guard let split = splitDeleteResumeToken(incomplete), split.phase == .traverse else {
    Issue.record("expected a traverse-phase cursor")
    return
  }
  #expect(backend.deleted == [["b1"]])
  #expect(clock.nowNanoseconds <= 6_000_000_000 - SyncLimits.deleteCheckpointReserveNanoseconds)

  let second = RecordDeletion(backend: backend)
  switch second.deleteWorkspaceRecords(timeout: 30, resumeToken: incomplete, pageBudget: 30) {
  case .deletedAndAbsent:
    break
  case .incomplete, .retryable, .unknownOutcome, .integrityFailure:
    Issue.record("expected completion on resume")
    return
  }
  #expect(Array(backend.deleted.prefix(2)) == [["b1"], ["b2"]])
  #expect(backend.live.isEmpty)
}

@Test func givenBudgetLostAfterAnchorDeleteWhenDeletingThenCheckpointIsAlreadyVerify() {
  // The budget dies between the anchor-batch success and its confirm-read:
  // the checkpoint must already carry the verify phase, or the resume would
  // re-traverse and delete the anchor again.
  let clock = ManualClock()
  let backend = HistoryBackend()
  backend.clock = clock
  backend.seedBundles(["b1"])
  backend.publishAnchor()
  backend.onAnchorDelete = {
    clock.advance(10)
  }

  let first = RecordDeletion(backend: backend)
  let incomplete: Data
  switch first.deleteWorkspaceRecords(
    timeout: 30, pageBudget: 30,
    deadlineNanoseconds: 6_000_000_000,
    nowNanoseconds: { clock.nowNanoseconds }
  ) {
  case .incomplete(let cursor):
    incomplete = cursor
  case .deletedAndAbsent, .retryable, .unknownOutcome, .integrityFailure:
    Issue.record("expected a wall-clock checkpoint past the anchor delete")
    return
  }
  guard let split = splitDeleteResumeToken(incomplete), split.phase == .verify else {
    Issue.record("expected a verify-phase cursor")
    return
  }

  let second = RecordDeletion(backend: backend)
  switch second.deleteWorkspaceRecords(timeout: 30, resumeToken: incomplete, pageBudget: 30) {
  case .deletedAndAbsent:
    break
  case .incomplete, .retryable, .unknownOutcome, .integrityFailure:
    Issue.record("expected verification to complete on resume")
    return
  }
  #expect(backend.live.isEmpty)
  #expect(backend.deleted.filter { $0 == [CloudNames.anchorName] }.count == 1)
}
