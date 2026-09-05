import Foundation
import Testing

@testable import PosatoMacOSSync

@Test func givenMissingEntitlementsWhenResolvingBindingThenCloudKitIsNotUsed() {
  let accounts = FakeAccounts(.available(syntheticBinding))
  let backend = InMemoryKeychainBackend()
  let response = RequestHandler.handle(
    testRequest(operation: .resolveBinding),
    dependencies: SyncDependencies(
      entitlements: FakeEntitlements(value: nil),
      accounts: accounts,
      keys: WorkspaceKeyStore(backend: backend),
      clouds: CloudStore(backend: FakeCloudBackend()),
    ),
  )

  #expect(response.outcome == .unavailable)
  #expect(backend.addCount == 0)
}

@Test func givenMissingEntitlementsWhenReadingItemThenKeychainIsNotUsed() {
  let backend = InMemoryKeychainBackend()
  let payload = keyPayload(binding: syntheticBinding, account: testAccount())
  let response = RequestHandler.handle(
    testRequest(operation: .readItem, payload: payload),
    dependencies: SyncDependencies(
      entitlements: FakeEntitlements(value: nil),
      accounts: FakeAccounts(.available(syntheticBinding)),
      keys: WorkspaceKeyStore(backend: backend),
      clouds: CloudStore(backend: FakeCloudBackend()),
    ),
  )

  #expect(response.outcome == .retryable)
  #expect(backend.lastQuery == nil)
}

@Test func givenDifferentBindingWhenReadingThenNoKeychainQueryRuns() {
  let backend = InMemoryKeychainBackend()
  let payload = keyPayload(binding: syntheticBinding, account: testAccount())
  let other = Data(repeating: 3, count: SyncLimits.bindingBytes)
  let response = RequestHandler.handle(
    testRequest(operation: .readItem, payload: payload),
    dependencies: SyncDependencies(
      entitlements: FakeEntitlements(value: provisionedEntitlements()),
      accounts: FakeAccounts(.available(other)),
      keys: WorkspaceKeyStore(backend: backend),
      clouds: CloudStore(backend: FakeCloudBackend()),
    ),
  )

  #expect(response.outcome == .accountChanged)
  #expect(response.requestIdentifier == Data(repeating: 9, count: SyncLimits.identifierBytes))
  #expect(backend.lastQuery == nil)
}

@Test func givenUnavailableBindingWhenCreatingThenOutcomeIsRetryable() {
  let backend = InMemoryKeychainBackend()
  let payload = keyPayload(binding: syntheticBinding, account: testAccount(), item: testItem())
  let response = RequestHandler.handle(
    testRequest(operation: .createItem, payload: payload),
    dependencies: SyncDependencies(
      entitlements: FakeEntitlements(value: provisionedEntitlements()),
      accounts: FakeAccounts(.unavailable),
      keys: WorkspaceKeyStore(backend: backend),
      clouds: CloudStore(backend: FakeCloudBackend()),
    ),
  )

  #expect(response.outcome == .retryable)
  #expect(backend.addCount == 0)
}

@Test func givenPostflightMismatchWhenReadSucceedsThenBytesAreNotReturned() {
  let backend = InMemoryKeychainBackend()
  let store = WorkspaceKeyStore(backend: backend)
  let account = testAccount()
  #expect(
    store.create(account: account, accessGroup: syntheticAccessGroup, value: testItem()) == .created
  )
  let other = Data(repeating: 4, count: SyncLimits.bindingBytes)
  let payload = keyPayload(binding: syntheticBinding, account: account)
  let response = RequestHandler.handle(
    testRequest(operation: .readItem, payload: payload),
    dependencies: SyncDependencies(
      entitlements: FakeEntitlements(value: provisionedEntitlements()),
      accounts: FakeAccounts(.available(syntheticBinding), .available(other)),
      keys: store,
      clouds: CloudStore(backend: FakeCloudBackend()),
    ),
  )

  #expect(response.outcome == .unknownOutcome)
  #expect(response.payload.isEmpty)
}

@Test func givenElapsedPreflightWhenPostflightRunsThenRemainingDeadlineIsPassed() {
  let accounts = FakeAccounts(.available(syntheticBinding), .available(syntheticBinding))
  accounts.pauseFirstResolve = 0.05
  let backend = InMemoryKeychainBackend()
  let store = WorkspaceKeyStore(backend: backend)
  let account = testAccount()
  #expect(
    store.create(account: account, accessGroup: syntheticAccessGroup, value: testItem()) == .created
  )
  let payload = keyPayload(binding: syntheticBinding, account: account)
  let response = RequestHandler.handle(
    testRequest(operation: .readItem, payload: payload, deadline: 1_000),
    dependencies: SyncDependencies(
      entitlements: FakeEntitlements(value: provisionedEntitlements()),
      accounts: accounts,
      keys: store,
      clouds: CloudStore(backend: FakeCloudBackend()),
    ),
  )

  #expect(response.outcome == .found)
  #expect(accounts.deadlines.count == 2)
  #expect(accounts.deadlines[0] <= 1_000)
  #expect(accounts.deadlines[1] < accounts.deadlines[0])
}

@Test func givenMatchingBindingWhenCreatedThenCreatedIsReturned() {
  let payload = keyPayload(binding: syntheticBinding, account: testAccount(), item: testItem())
  let response = RequestHandler.handle(
    testRequest(operation: .createItem, payload: payload),
    dependencies: SyncDependencies(
      entitlements: FakeEntitlements(value: provisionedEntitlements()),
      accounts: FakeAccounts(.available(syntheticBinding)),
      keys: WorkspaceKeyStore(backend: InMemoryKeychainBackend()),
      clouds: CloudStore(backend: FakeCloudBackend()),
    ),
  )

  #expect(response.outcome == .created)
  #expect(response.operation == .createItem)
}

@Test func givenAccessGroupWithoutTeamPrefixWhenGuardedThenItIsRejected() {
  let entitlements = CompanionEntitlements(
    icloudContainers: [SyncLimits.containerIdentifier],
    icloudServices: ["CloudKit"],
    keychainAccessGroups: ["app.posato.sync"],
  )
  #expect(EntitlementGuard.accessGroup(from: entitlements) == nil)
}

@Test func givenSignedAccessGroupWhenGuardedThenTheEntitlementValueIsUsed() {
  #expect(EntitlementGuard.accessGroup(from: provisionedEntitlements()) == syntheticAccessGroup)
}

@Test func givenAccountChangeDuringReadThenBytesAreNotReturned() {
  let backend = InMemoryKeychainBackend()
  let store = WorkspaceKeyStore(backend: backend)
  let account = testAccount()
  let changeName = Notification.Name("posato.sync.test.account-changed")
  #expect(
    store.create(account: account, accessGroup: syntheticAccessGroup, value: testItem()) == .created
  )
  backend.onCopy = {
    NotificationCenter.default.post(name: changeName, object: nil)
  }
  let payload = keyPayload(binding: syntheticBinding, account: account)
  let response = RequestHandler.handle(
    testRequest(operation: .readItem, payload: payload),
    dependencies: SyncDependencies(
      entitlements: FakeEntitlements(value: provisionedEntitlements()),
      accounts: FakeAccounts(.available(syntheticBinding)),
      keys: store,
      clouds: CloudStore(backend: FakeCloudBackend()),
      accountChangeName: changeName,
    ),
  )

  #expect(response.outcome == .unknownOutcome)
  #expect(response.payload.isEmpty)
}

@Test func givenMissingCloudKitServiceWhenGuardedThenAccessGroupIsRejected() {
  let entitlements = CompanionEntitlements(
    icloudContainers: [SyncLimits.containerIdentifier],
    icloudServices: [],
    keychainAccessGroups: [syntheticAccessGroup],
  )
  #expect(EntitlementGuard.accessGroup(from: entitlements) == nil)
}
