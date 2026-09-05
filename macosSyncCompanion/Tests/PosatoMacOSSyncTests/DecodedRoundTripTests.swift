import Foundation
import Testing

@testable import PosatoMacOSSync

@Test func givenDecodedCreateAnchorWhenAnchorMissingThenCreatedIsReturned() throws {
  let backend = FakeCloudBackend()
  let fields = testAnchorFields()
  let payload = anchorPayload(binding: syntheticBinding, fields: fields)
  let request = try SyncCodec.decode(
    try SyncCodec.encode(cloudRequest(operation: .createAnchor, payload: payload))
  )
  let response = RequestHandler.handle(request, dependencies: cloudDependencies(backend: backend))

  #expect(response.outcome == .created)
}

@Test func givenDecodedReadItemWhenItemStoredThenFoundIsReturned() throws {
  let backend = InMemoryKeychainBackend()
  let store = WorkspaceKeyStore(backend: backend)
  let created = store.create(
    account: testAccount(), accessGroup: syntheticAccessGroup, value: testItem())
  #expect(created == .created)
  let payload = keyPayload(binding: syntheticBinding, account: testAccount())
  let encoded = try SyncCodec.encode(testRequest(operation: .readItem, payload: payload))
  let request = try SyncCodec.decode(encoded)
  let response = RequestHandler.handle(
    request,
    dependencies: SyncDependencies(
      entitlements: FakeEntitlements(value: provisionedEntitlements()),
      accounts: FakeAccounts(.available(syntheticBinding)),
      keys: WorkspaceKeyStore(backend: backend),
      clouds: CloudStore(backend: FakeCloudBackend()),
    )
  )

  #expect(response.outcome == .found)
}
