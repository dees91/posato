import Foundation
import Testing

@testable import PosatoMacOSSync

@Test func givenExistingZoneWhenSavingInStoreThenAlreadyExistsIsReturned() {
  let backend = FakeCloudBackend()
  backend.zone = .found
  let store = CloudStore(backend: backend)

  #expect(store.saveZone(timeout: 5) == .alreadyExists)
  #expect(backend.saveCalls == 0)
}

@Test func givenMissingZoneWhenSavingThenCreateIsConfirmedByFetch() {
  let backend = FakeCloudBackend()
  backend.zone = .missing
  let store = CloudStore(backend: backend)

  #expect(store.saveZone(timeout: 5) == .created)
}

@Test func givenFailedSaveWhenZoneStillMissingThenOutcomeIsUnknown() {
  let backend = FakeCloudBackend()
  backend.zone = .missing
  backend.zoneSaveFault = .unknown
  let store = CloudStore(backend: backend)

  #expect(store.saveZone(timeout: 5) == .unknownOutcome)
}

@Test func givenLostSaveResponseWhenZoneNowExistsThenAlreadyExistsIsReturned() {
  let backend = FakeCloudBackend()
  backend.zone = .missing
  backend.zoneSaveFault = .unknown
  backend.zoneAfterSave = .found
  let store = CloudStore(backend: backend)

  #expect(store.saveZone(timeout: 5) == .alreadyExists)
}

@Test func givenRetryableZoneFetchWhenFetchingThenRetryableIsReturned() {
  let backend = FakeCloudBackend()
  backend.zone = .failed(.retryable)
  let store = CloudStore(backend: backend)

  #expect(store.fetchZone(timeout: 5) == .retryable)
  #expect(store.saveZone(timeout: 5) == .retryable)
}

@Test func givenFoundAnchorWhenReadingThenFieldsAreReturned() {
  let backend = FakeCloudBackend()
  backend.records[CloudNames.anchorName] = testAnchorRecord()
  let store = CloudStore(backend: backend)

  #expect(store.readAnchor(timeout: 5) == .found(testAnchorFields()))
}

@Test func givenMalformedAnchorWhenReadingThenIntegrityFailureIsReturned() {
  let backend = FakeCloudBackend()
  var record = testAnchorRecord()
  record.fields = [:]
  record.allKeys = []
  backend.records[CloudNames.anchorName] = record
  let store = CloudStore(backend: backend)

  #expect(store.readAnchor(timeout: 5) == .integrityFailure)
}

@Test func givenAbsentZoneWhenReadingAnchorThenOutcomeIsUnknown() {
  let backend = FakeCloudBackend()
  backend.zoneAbsentRecords = true
  let store = CloudStore(backend: backend)

  #expect(store.readAnchor(timeout: 5) == .unknownOutcome)
}

@Test func givenExistingAnchorWhenCreatingThenConflictIsReturnedWithoutSave() {
  let backend = FakeCloudBackend()
  backend.records[CloudNames.anchorName] = testAnchorRecord()
  let store = CloudStore(backend: backend)

  #expect(store.createAnchor(fields: testAnchorFields(), timeout: 5) == .conflict)
  #expect(backend.saveCalls == 0)
}

@Test func givenIdenticalAnchorWhenCreatingThenConflictIsReturned() {
  let backend = FakeCloudBackend()
  backend.records[CloudNames.anchorName] = testAnchorRecord()
  let store = CloudStore(backend: backend)

  #expect(store.createAnchor(fields: testAnchorFields(), timeout: 5) == .conflict)
}

@Test func givenMissingAnchorWhenCreatingThenCreatedIsReturned() {
  let backend = FakeCloudBackend()
  let store = CloudStore(backend: backend)

  #expect(store.createAnchor(fields: testAnchorFields(), timeout: 5) == .created)
}

@Test func givenAbsentZoneWhenCreatingAnchorThenOutcomeIsUnknownWithoutSave() {
  let backend = FakeCloudBackend()
  backend.zoneAbsentRecords = true
  let store = CloudStore(backend: backend)

  #expect(store.createAnchor(fields: testAnchorFields(), timeout: 5) == .unknownOutcome)
  #expect(backend.saveCalls == 0)
}

@Test func givenRaceWhenCreatingAnchorThenConflictIsReturned() {
  let backend = FakeCloudBackend()
  backend.saveResult = .conflicted
  let store = CloudStore(backend: backend)

  #expect(store.createAnchor(fields: testAnchorFields(), timeout: 5) == .conflict)
}

@Test func givenShortFieldsWhenCreatingAnchorThenIntegrityFailureIsReturned() {
  let backend = FakeCloudBackend()
  let store = CloudStore(backend: backend)

  #expect(store.createAnchor(fields: Data([1, 2, 3]), timeout: 5) == .integrityFailure)
  #expect(backend.fetchRecordCalls == 0)
}

@Test func givenIdenticalBundleWhenSavingThenIdenticalIsReturnedWithoutSave() {
  let backend = FakeCloudBackend()
  backend.records[testBundleName()] = testBundleRecord()
  let store = CloudStore(backend: backend)

  #expect(
    store.saveBundle(
      identifier: testBundleIdentifier(),
      payload: Data(repeating: 11, count: 8),
      timeout: 5
    ) == .identical
  )
  #expect(backend.saveCalls == 0)
}

@Test func givenDifferentBundleWhenSavingThenConflictIsReturnedWithoutSave() {
  let backend = FakeCloudBackend()
  backend.records[testBundleName()] = testBundleRecord()
  let store = CloudStore(backend: backend)

  #expect(
    store.saveBundle(
      identifier: testBundleIdentifier(),
      payload: Data(repeating: 12, count: 8),
      timeout: 5
    ) == .conflict
  )
  #expect(backend.saveCalls == 0)
}

@Test func givenMissingBundleWhenSavingThenCreatedIsReturned() {
  let backend = FakeCloudBackend()
  let store = CloudStore(backend: backend)

  #expect(
    store.saveBundle(
      identifier: testBundleIdentifier(),
      payload: Data(repeating: 11, count: 8),
      timeout: 5
    ) == .created
  )
}

@Test func givenRaceWhenSavingBundleThenIdenticalIsReconciled() {
  let backend = FakeCloudBackend()
  backend.records[testBundleName()] = testBundleRecord()
  backend.saveResult = .conflicted
  let store = CloudStore(backend: backend)

  #expect(
    store.saveBundle(
      identifier: testBundleIdentifier(),
      payload: Data(repeating: 11, count: 8),
      timeout: 5
    ) == .identical
  )
}

@Test func givenAbsentZoneWhenSavingBundleThenOutcomeIsUnknown() {
  let backend = FakeCloudBackend()
  backend.zoneAbsentRecords = true
  let store = CloudStore(backend: backend)

  #expect(
    store.saveBundle(
      identifier: testBundleIdentifier(),
      payload: Data([1]),
      timeout: 5
    ) == .unknownOutcome
  )
  #expect(backend.saveCalls == 0)
}

@Test func givenEmptyPayloadWhenSavingBundleThenIntegrityFailureIsReturned() {
  let backend = FakeCloudBackend()
  let store = CloudStore(backend: backend)

  #expect(
    store.saveBundle(identifier: testBundleIdentifier(), payload: Data(), timeout: 5)
      == .integrityFailure
  )
  #expect(backend.fetchRecordCalls == 0)
}

@Test func givenBundleChangeWhenFetchingThenPageCarriesTheBundle() {
  let backend = FakeCloudBackend()
  backend.changes = .fetched(
    BackendChanges(
      changed: [testBundleRecord()],
      deletedNames: [],
      token: Data([4, 5]),
      moreComing: true
    )
  )
  let store = CloudStore(backend: backend)

  #expect(
    store.fetchChanges(cursor: Data(), timeout: 5)
      == .page(
        ChangePageNative(
          bundleIdentifier: testBundleIdentifier(),
          bundle: Data(repeating: 11, count: 8),
          moreComing: true,
          cursor: Data([4, 5])
        )
      )
  )
}

@Test func givenAnchorChangeWhenFetchingThenPageCarriesNoBundle() {
  let backend = FakeCloudBackend()
  backend.changes = .fetched(
    BackendChanges(
      changed: [testAnchorRecord()],
      deletedNames: [],
      token: Data([4]),
      moreComing: true
    )
  )
  let store = CloudStore(backend: backend)

  #expect(
    store.fetchChanges(cursor: Data(), timeout: 5)
      == .page(
        ChangePageNative(
          bundleIdentifier: nil,
          bundle: nil,
          moreComing: true,
          cursor: Data([4])
        )
      )
  )
}

@Test func givenDeletionWhenFetchingThenIntegrityFailureIsReturned() {
  let backend = FakeCloudBackend()
  backend.changes = .fetched(
    BackendChanges(
      changed: [],
      deletedNames: [testBundleName()],
      token: Data([4]),
      moreComing: false
    )
  )
  let store = CloudStore(backend: backend)

  #expect(store.fetchChanges(cursor: Data(), timeout: 5) == .integrityFailure)
}

@Test func givenUnknownTypeWhenFetchingThenIntegrityFailureIsReturned() {
  let backend = FakeCloudBackend()
  let mystery = RawRecord(
    name: "mystery",
    type: "MysteryType",
    fields: [:],
    allKeys: [],
  )
  backend.changes = .fetched(
    BackendChanges(
      changed: [mystery],
      deletedNames: [],
      token: Data([4]),
      moreComing: false,
    )
  )
  let store = CloudStore(backend: backend)

  #expect(store.fetchChanges(cursor: Data(), timeout: 5) == .integrityFailure)
}

@Test func givenMissingTokenWhenFetchingThenOutcomeIsUnknown() {
  let backend = FakeCloudBackend()
  backend.changes = .fetched(
    BackendChanges(changed: [], deletedNames: [], token: nil, moreComing: false)
  )
  let store = CloudStore(backend: backend)

  #expect(store.fetchChanges(cursor: Data(), timeout: 5) == .unknownOutcome)
}

@Test func givenAbsentZoneWhenFetchingThenZoneMissingIsReturned() {
  let backend = FakeCloudBackend()
  backend.changes = .zoneMissing
  let store = CloudStore(backend: backend)

  #expect(store.fetchChanges(cursor: Data(), timeout: 5) == .zoneMissing)
}

@Test func givenGarbageCursorWhenFetchingThenIntegrityFailureIsReturned() {
  let backend = FakeCloudBackend()
  let store = CloudStore(backend: backend)

  #expect(store.fetchChanges(cursor: Data([9, 9, 9]), timeout: 5) == .integrityFailure)
  #expect(backend.changesCalls == 0)
}

@Test func givenDeletedZoneWhenVerifyingAbsenceThenDeletedAndAbsentIsReturned() {
  let backend = FakeCloudBackend()
  backend.zone = .missing
  let store = CloudStore(backend: backend)

  #expect(store.deleteZoneAndVerifyAbsent(timeout: 5) == .deletedAndAbsent)
}

@Test func givenRemainingZoneWhenVerifyingAbsenceThenOutcomeIsUnknown() {
  let backend = FakeCloudBackend()
  backend.zone = .found
  let store = CloudStore(backend: backend)

  #expect(store.deleteZoneAndVerifyAbsent(timeout: 5) == .unknownOutcome)
}
