import Foundation
import Testing

@testable import PosatoMacOSSync

@Test func givenBundlesWhenDeletingWorkspaceRecordsThenBundlesGoFirstAndAnchorLast() {
  let backend = FakeCloudBackend()
  backend.changesScript = [
    .fetched(
      BackendChanges(
        changed: [testBundleRecord(identifier: 9), testBundleRecord(identifier: 10)],
        deletedNames: [],
        token: Data([7]),
        moreComing: false
      )
    ),
    .fetched(BackendChanges(changed: [], deletedNames: [], token: Data([8]), moreComing: false)),
  ]
  let store = CloudStore(backend: backend)

  #expect(store.deleteWorkspaceRecords(timeout: 5) == .deletedAndAbsent)
  #expect(
    backend.deleteRecordCalls == [
      [testBundleName(9), testBundleName(10)], [CloudNames.anchorName],
    ]
  )
}

@Test func givenMultiPageTraversalWhenDeletingWorkspaceRecordsThenTokensChain() {
  let backend = FakeCloudBackend()
  backend.changesScript = [
    .fetched(
      BackendChanges(
        changed: [testBundleRecord(identifier: 9)],
        deletedNames: [],
        token: Data([7]),
        moreComing: true
      )
    ),
    .fetched(
      BackendChanges(
        changed: [testBundleRecord(identifier: 10)],
        deletedNames: [],
        token: Data([8]),
        moreComing: false
      )
    ),
    .fetched(BackendChanges(changed: [], deletedNames: [], token: Data([9]), moreComing: false)),
  ]
  let store = CloudStore(backend: backend)

  #expect(store.deleteWorkspaceRecords(timeout: 5) == .deletedAndAbsent)
  #expect(backend.requestedTokens == [nil, Data([7]), nil])
  #expect(
    backend.deleteRecordCalls == [
      [testBundleName(9), testBundleName(10)], [CloudNames.anchorName],
    ]
  )
}

@Test func givenMalformedBundleWhenDeletingWorkspaceRecordsThenRemovalCompletes() {
  let backend = FakeCloudBackend()
  let mystery = RawRecord(name: "mystery", type: "MysteryType", fields: [:], allKeys: [])
  backend.changesScript = [
    .fetched(
      BackendChanges(
        changed: [mystery, testBundleRecord(identifier: 9)],
        deletedNames: [],
        token: Data([7]),
        moreComing: false
      )
    ),
    .fetched(
      BackendChanges(changed: [mystery], deletedNames: [], token: Data([8]), moreComing: false)),
  ]
  let store = CloudStore(backend: backend)

  #expect(store.deleteWorkspaceRecords(timeout: 5) == .deletedAndAbsent)
  #expect(backend.deleteRecordCalls == [[testBundleName(9)], [CloudNames.anchorName]])
}

@Test func givenManyBundlesWhenDeletingWorkspaceRecordsThenDeletesAreBatched() {
  let backend = FakeCloudBackend()
  let names = (0..<250).map { "bundle-\($0)" }
  let records = names.map {
    RawRecord(name: $0, type: CloudNames.bundleType, fields: [:], allKeys: [])
  }
  backend.changesScript = [
    .fetched(
      BackendChanges(changed: records, deletedNames: [], token: Data([7]), moreComing: false)),
    .fetched(BackendChanges(changed: [], deletedNames: [], token: Data([8]), moreComing: false)),
  ]
  let store = CloudStore(backend: backend)

  #expect(store.deleteWorkspaceRecords(timeout: 5) == .deletedAndAbsent)
  #expect(backend.deleteRecordCalls.count == 4)
  #expect(backend.deleteRecordCalls[0].count == 100)
  #expect(backend.deleteRecordCalls[1].count == 100)
  #expect(backend.deleteRecordCalls[2].count == 50)
  #expect(backend.deleteRecordCalls[3] == [CloudNames.anchorName])
}

@Test func givenRemainingBundleWhenDeletingWorkspaceRecordsThenUnknownOutcomeIsReturned() {
  let backend = FakeCloudBackend()
  backend.changes = .fetched(
    BackendChanges(
      changed: [testBundleRecord(identifier: 9)],
      deletedNames: [],
      token: Data([7]),
      moreComing: false
    )
  )
  let store = CloudStore(backend: backend)

  #expect(store.deleteWorkspaceRecords(timeout: 5) == .unknownOutcome)
}

@Test func givenPresentAnchorWhenDeletingWorkspaceRecordsThenUnknownOutcomeIsReturned() {
  let backend = FakeCloudBackend()
  backend.records[CloudNames.anchorName] = testAnchorRecord()
  backend.changes = .fetched(
    BackendChanges(changed: [], deletedNames: [], token: Data([7]), moreComing: false)
  )
  let store = CloudStore(backend: backend)

  #expect(store.deleteWorkspaceRecords(timeout: 5) == .unknownOutcome)
}

@Test func givenDeleteFaultWhenDeletingWorkspaceRecordsThenRetryableIsReturned() {
  let backend = FakeCloudBackend()
  backend.deleteFault = .retryable
  backend.changes = .fetched(
    BackendChanges(changed: [], deletedNames: [], token: Data([7]), moreComing: false)
  )
  let store = CloudStore(backend: backend)

  #expect(store.deleteWorkspaceRecords(timeout: 5) == .retryable)
}

@Test func givenMissingZoneWhenDeletingWorkspaceRecordsThenDeletedAndAbsentIsReturned() {
  let backend = FakeCloudBackend()
  backend.changes = .zoneMissing
  let store = CloudStore(backend: backend)

  #expect(store.deleteWorkspaceRecords(timeout: 5) == .deletedAndAbsent)
  #expect(backend.deleteRecordCalls.isEmpty)
}

@Test func givenPresentZoneWhenDeletingWorkspaceRecordsThenDeletedAndAbsentIsReturned() {
  let backend = FakeCloudBackend()
  backend.zone = .found
  backend.changes = .fetched(
    BackendChanges(changed: [], deletedNames: [], token: Data([7]), moreComing: false)
  )
  let store = CloudStore(backend: backend)

  #expect(store.deleteWorkspaceRecords(timeout: 5) == .deletedAndAbsent)
}

@Test func givenLeftoverBundlesWhenSweepingThenEnumeratedSetIsDeleted() {
  let backend = FakeCloudBackend()
  backend.changes = .fetched(
    BackendChanges(
      changed: [testBundleRecord(identifier: 9)],
      deletedNames: [],
      token: Data([7]),
      moreComing: false
    )
  )
  let store = CloudStore(backend: backend)

  #expect(store.sweepBundlesIfAnchorMissing(timeout: 5) == .swept)
  #expect(backend.deleteRecordCalls == [[testBundleName(9)]])
}

@Test func givenAppearingAnchorWhenSweepingThenNothingIsDeleted() {
  let backend = FakeCloudBackend()
  backend.records[CloudNames.anchorName] = testAnchorRecord()
  backend.changes = .fetched(
    BackendChanges(
      changed: [testBundleRecord(identifier: 9)],
      deletedNames: [],
      token: Data([7]),
      moreComing: false
    )
  )
  let store = CloudStore(backend: backend)

  #expect(store.sweepBundlesIfAnchorMissing(timeout: 5) == .anchorPresent)
  #expect(backend.deleteRecordCalls.isEmpty)
}

@Test func givenMissingZoneWhenSweepingThenSweptIsReturned() {
  let backend = FakeCloudBackend()
  backend.changes = .zoneMissing
  let store = CloudStore(backend: backend)

  #expect(store.sweepBundlesIfAnchorMissing(timeout: 5) == .swept)
  #expect(backend.deleteRecordCalls.isEmpty)
}
