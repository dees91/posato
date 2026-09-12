import Foundation
import Testing

@testable import PosatoMacOSSync

@Test func givenKeychainBitWhenFetchingZoneThenItIsRejectedWithoutCloudAccess() {
  let backend = FakeCloudBackend()
  backend.zone = .found
  let request = testRequest(operation: .fetchZone, payload: syntheticBinding)
  let response = RequestHandler.handle(request, dependencies: cloudDependencies(backend: backend))

  #expect(response.outcome == .unknownOutcome)
  #expect(backend.zoneFetchCalls == 0)
  #expect(backend.zoneSaveCalls == 0)
  #expect(backend.fetchRecordCalls == 0)
  #expect(backend.changesCalls == 0)
  #expect(backend.deleteRecordCalls.isEmpty)
}

@Test func givenCloudKitBitWhenReadingKeychainItemThenItIsRejected() {
  let backend = InMemoryKeychainBackend()
  let payload = keyPayload(binding: syntheticBinding, account: testAccount())
  var request = testRequest(operation: .readItem, payload: payload)
  request.capabilities = SyncLimits.cloudkitCapability
  let response = RequestHandler.handle(
    request,
    dependencies: SyncDependencies(
      entitlements: FakeEntitlements(value: provisionedEntitlements()),
      accounts: FakeAccounts(.available(syntheticBinding)),
      keys: WorkspaceKeyStore(backend: backend),
      clouds: CloudStore(backend: FakeCloudBackend()),
    )
  )

  #expect(response.outcome == .unknownOutcome)
  #expect(backend.lastQuery == nil)
}

@Test func givenCloudKitBitWhenResolvingBindingThenItIsAccepted() {
  let response = RequestHandler.handle(
    cloudRequest(operation: .resolveBinding),
    dependencies: cloudDependencies()
  )

  #expect(response.outcome == .found)
  #expect(response.payload == syntheticBinding)
}

@Test func givenUnavailableBindingWhenFetchingZoneThenRetryableWithoutCloudAccess() {
  let backend = FakeCloudBackend()
  backend.zone = .found
  let response = RequestHandler.handle(
    cloudRequest(operation: .fetchZone, payload: syntheticBinding),
    dependencies: cloudDependencies(
      backend: backend,
      accounts: FakeAccounts(.unavailable)
    )
  )

  #expect(response.outcome == .retryable)
  #expect(backend.zoneFetchCalls == 0)
}

@Test func givenOversizedCursorWhenFetchingChangesThenItIsRejectedWithoutCloudAccess() {
  let backend = FakeCloudBackend()
  let payload = cursorPayload(
    binding: syntheticBinding,
    cursor: Data(repeating: 1, count: SyncLimits.cursorBytes + 1)
  )
  let response = RequestHandler.handle(
    cloudRequest(operation: .fetchChanges, payload: payload),
    dependencies: cloudDependencies(backend: backend)
  )

  #expect(response.outcome == .integrityFailure)
  #expect(backend.changesCalls == 0)
}

@Test func givenMalformedAnchorWhenCreatingThenItIsRejectedWithoutCloudAccess() {
  let backend = FakeCloudBackend()
  let payload = anchorPayload(binding: syntheticBinding, fields: Data([1, 2, 3]))
  let response = RequestHandler.handle(
    cloudRequest(operation: .createAnchor, payload: payload),
    dependencies: cloudDependencies(backend: backend)
  )

  #expect(response.outcome == .integrityFailure)
  #expect(backend.fetchRecordCalls == 0)
}

@Test func givenMissingZoneWhenFetchingThenMissingIsReturned() {
  let response = RequestHandler.handle(
    cloudRequest(operation: .fetchZone, payload: syntheticBinding),
    dependencies: cloudDependencies()
  )

  #expect(response.outcome == .missing)
}

@Test func givenMissingZoneWhenSavingThenCreatedIsReturned() {
  let response = RequestHandler.handle(
    cloudRequest(operation: .saveZone, payload: syntheticBinding),
    dependencies: cloudDependencies()
  )

  #expect(response.outcome == .created)
}

@Test func givenExistingZoneWhenSavingThenAlreadyExistsIsReturned() {
  let backend = FakeCloudBackend()
  backend.zone = .found
  let response = RequestHandler.handle(
    cloudRequest(operation: .saveZone, payload: syntheticBinding),
    dependencies: cloudDependencies(backend: backend)
  )

  #expect(response.outcome == .alreadyExists)
}

@Test func givenStoredAnchorWhenReadingThenFieldsAreReturned() {
  let backend = FakeCloudBackend()
  backend.records[CloudNames.anchorName] = testAnchorRecord()
  let response = RequestHandler.handle(
    cloudRequest(operation: .readAnchor, payload: syntheticBinding),
    dependencies: cloudDependencies(backend: backend)
  )

  #expect(response.outcome == .found)
  #expect(response.payload == testAnchorFields())
}

@Test func givenStoredAnchorWhenCreatingThenConflictIsReturned() {
  let backend = FakeCloudBackend()
  backend.records[CloudNames.anchorName] = testAnchorRecord()
  let payload = anchorPayload(binding: syntheticBinding, fields: testAnchorFields())
  let response = RequestHandler.handle(
    cloudRequest(operation: .createAnchor, payload: payload),
    dependencies: cloudDependencies(backend: backend)
  )

  #expect(response.outcome == .conflict)
}

@Test func givenStoredBundleWhenSavingIdenticalThenIdenticalIsReturned() {
  let backend = FakeCloudBackend()
  backend.records[testBundleName()] = testBundleRecord()
  let payload = bundlePayload(
    binding: syntheticBinding,
    identifier: testBundleIdentifier(),
    bundle: Data(repeating: 11, count: 8)
  )
  let response = RequestHandler.handle(
    cloudRequest(operation: .saveBundle, payload: payload),
    dependencies: cloudDependencies(backend: backend)
  )

  #expect(response.outcome == .identical)
}

@Test func givenStoredBundleWhenSavingDifferentThenConflictIsReturned() {
  let backend = FakeCloudBackend()
  backend.records[testBundleName()] = testBundleRecord()
  let payload = bundlePayload(
    binding: syntheticBinding,
    identifier: testBundleIdentifier(),
    bundle: Data(repeating: 12, count: 8)
  )
  let response = RequestHandler.handle(
    cloudRequest(operation: .saveBundle, payload: payload),
    dependencies: cloudDependencies(backend: backend)
  )

  #expect(response.outcome == .conflict)
}

@Test func givenBundleChangeWhenFetchingThenPageRoundTrips() throws {
  let backend = FakeCloudBackend()
  backend.changes = .fetched(
    BackendChanges(
      changed: [testBundleRecord()],
      deletedNames: [],
      token: Data([4, 5]),
      moreComing: false
    )
  )
  let response = RequestHandler.handle(
    cloudRequest(operation: .fetchChanges, payload: syntheticBinding),
    dependencies: cloudDependencies(backend: backend)
  )

  #expect(response.outcome == .found)
  let decoded = try SyncCodec.decode(try SyncCodec.encode(response))
  let page = PageCodec.decode(decoded.payload)!

  #expect(page.moreComing == false)
  #expect(page.cursor == Data([4, 5]))
  #expect(page.bundleIdentifier == testBundleIdentifier())
  #expect(page.bundle == Data(repeating: 11, count: 8))
}

@Test func givenAbsentZoneWhenFetchingChangesThenMissingIsReturned() {
  let backend = FakeCloudBackend()
  backend.changes = .zoneMissing
  let response = RequestHandler.handle(
    cloudRequest(operation: .fetchChanges, payload: syntheticBinding),
    dependencies: cloudDependencies(backend: backend)
  )

  #expect(response.outcome == .missing)
}

@Test func givenPostflightMismatchWhenFetchingChangesThenUnknownOutcomeIsReturned() {
  let backend = FakeCloudBackend()
  backend.changes = .fetched(
    BackendChanges(
      changed: [testBundleRecord()],
      deletedNames: [],
      token: Data([4, 5]),
      moreComing: false
    )
  )
  let other = Data(repeating: 4, count: SyncLimits.bindingBytes)
  let response = RequestHandler.handle(
    cloudRequest(operation: .fetchChanges, payload: syntheticBinding),
    dependencies: cloudDependencies(
      backend: backend,
      accounts: FakeAccounts(.available(syntheticBinding), .available(other))
    )
  )

  #expect(response.outcome == .unknownOutcome)
  #expect(response.payload.isEmpty)
}

@Test func givenEmptyZoneWhenDeletingRecordsThenDeletedAndAbsentIsReturned() {
  let backend = FakeCloudBackend()
  backend.changesScript = [
    .fetched(BackendChanges(changed: [], deletedNames: [], token: Data([7]), moreComing: false)),
    .fetched(BackendChanges(changed: [], deletedNames: [], token: Data([8]), moreComing: false)),
  ]
  let response = RequestHandler.handle(
    cloudRequest(operation: .deleteWorkspaceRecords, payload: syntheticBinding),
    dependencies: cloudDependencies(backend: backend)
  )

  #expect(response.outcome == .deletedAndAbsent)
}

@Test func givenExhaustedBudgetWhenDeletingRecordsThenIncompleteCarriesCursor() {
  let backend = FakeCloudBackend()
  backend.changesScript = (0..<17).map { index in
    .fetched(
      BackendChanges(
        changed: [], deletedNames: ["gone-\(index)"], token: Data([UInt8(index)]),
        moreComing: true))
  }
  let response = RequestHandler.handle(
    cloudRequest(operation: .deleteWorkspaceRecords, payload: syntheticBinding),
    dependencies: cloudDependencies(backend: backend)
  )

  #expect(response.outcome == .incomplete)
  #expect(response.payload == Data([SyncLimits.deletePhaseTraverse, 15]))
}

@Test func givenOversizedTokenWhenDeletingRecordsThenIntegrityFailureIsReturned() {
  let response = RequestHandler.handle(
    cloudRequest(
      operation: .deleteWorkspaceRecords,
      payload: syntheticBinding + Data(repeating: 9, count: SyncLimits.cursorBytes + 1)),
    dependencies: cloudDependencies()
  )

  #expect(response.outcome == .integrityFailure)
}

@Test func givenElapsedPreflightWhenSavingZoneThenRemainingDeadlineIsPassed() {
  let accounts = FakeAccounts(.available(syntheticBinding), .available(syntheticBinding))
  accounts.pauseFirstResolve = 0.05
  let backend = FakeCloudBackend()
  backend.zone = .found
  let response = RequestHandler.handle(
    cloudRequest(
      operation: .saveZone,
      payload: syntheticBinding,
      deadline: 1_000
    ),
    dependencies: cloudDependencies(backend: backend, accounts: accounts)
  )

  #expect(response.outcome == .alreadyExists)
  #expect(accounts.deadlines.count == 2)
  #expect(accounts.deadlines[0] <= 1_000)
  #expect(accounts.deadlines[1] < accounts.deadlines[0])
}
