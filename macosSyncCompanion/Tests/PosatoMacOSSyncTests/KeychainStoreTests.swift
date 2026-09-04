import Foundation
import Security
import Testing

@testable import PosatoMacOSSync

@Test func givenAbsentItemWhenCreatedThenSelectorUsesDataProtectionAndSynchronizable() {
  let backend = InMemoryKeychainBackend()
  let store = WorkspaceKeyStore(backend: backend)
  let item = testItem()
  let account = testAccount()

  #expect(
    store.create(account: account, accessGroup: syntheticAccessGroup, value: item) == .created)
  #expect(backend.lastAdded?[kSecAttrSynchronizable as String] as? Bool == true)
  #expect(
    backend.lastAdded?[kSecUseDataProtectionKeychain as String] as? Bool == true
  )
  #expect(
    backend.lastAdded?[kSecAttrAccessible as String] as? String
      == kSecAttrAccessibleAfterFirstUnlock as String
  )
  #expect(backend.lastAdded?[kSecAttrService as String] as? String == SyncLimits.keyService)
  #expect(backend.addCount == 1)
}

@Test func givenIdenticalItemWhenCreatedThenExistingValueIsKept() {
  let backend = InMemoryKeychainBackend()
  let store = WorkspaceKeyStore(backend: backend)
  let item = testItem()
  let account = testAccount()

  #expect(
    store.create(account: account, accessGroup: syntheticAccessGroup, value: item) == .created)
  #expect(
    store.create(account: account, accessGroup: syntheticAccessGroup, value: item) == .identical)
  #expect(store.read(account: account, accessGroup: syntheticAccessGroup) == .found(item))
}

@Test func givenDifferentItemWhenCreatedThenExistingValueIsNotReplaced() {
  let backend = InMemoryKeychainBackend()
  let store = WorkspaceKeyStore(backend: backend)
  let original = testItem(keyByte: 6)
  let other = testItem(keyByte: 7)
  let account = testAccount()

  #expect(
    store.create(account: account, accessGroup: syntheticAccessGroup, value: original) == .created)
  #expect(
    store.create(account: account, accessGroup: syntheticAccessGroup, value: other)
      == .integrityFailure)
  #expect(store.read(account: account, accessGroup: syntheticAccessGroup) == .found(original))
}

@Test func givenStoredItemWhenDeletedThenAbsenceIsVerified() {
  let backend = InMemoryKeychainBackend()
  let store = WorkspaceKeyStore(backend: backend)
  let account = testAccount()

  #expect(
    store.create(account: account, accessGroup: syntheticAccessGroup, value: testItem()) == .created
  )
  #expect(
    store.deleteAndVerifyAbsent(account: account, accessGroup: syntheticAccessGroup)
      == .deletedAndAbsent)
  #expect(store.read(account: account, accessGroup: syntheticAccessGroup) == .missing)
  #expect(backend.deleteCount == 1)
}

@Test func givenLockedKeychainWhenReadThenOutcomeIsRetryable() {
  let backend = InMemoryKeychainBackend()
  backend.copyStatus = errSecInteractionNotAllowed
  let store = WorkspaceKeyStore(backend: backend)

  #expect(store.read(account: testAccount(), accessGroup: syntheticAccessGroup) == .retryable)
}
