import CloudKit
import Foundation

struct SyncDependencies: Sendable {
  var entitlements: any EntitlementReader
  var accounts: any AccountBindingSource
  var keys: WorkspaceKeyStore
  var accountChangeName: Notification.Name = .CKAccountChanged
}

enum RequestHandler {
  static func handle(_ request: SyncMessage, dependencies: SyncDependencies) -> SyncMessage {
    guard request.capabilities & SyncLimits.keychainCapability == SyncLimits.keychainCapability
    else {
      return request.respond(outcome: .unknownOutcome)
    }
    guard let entitlements = dependencies.entitlements.load(),
      let accessGroup = EntitlementGuard.accessGroup(from: entitlements)
    else {
      let outcome: SyncOutcome =
        request.operation == .resolveBinding ? .unavailable : .retryable
      return request.respond(outcome: outcome)
    }
    switch request.operation {
    case .resolveBinding:
      return resolveBinding(request, accounts: dependencies.accounts)
    case .readItem:
      return readItem(request, accessGroup: accessGroup, dependencies: dependencies)
    case .createItem:
      return createItem(request, accessGroup: accessGroup, dependencies: dependencies)
    case .deleteItemAndVerifyAbsent:
      return deleteItem(request, accessGroup: accessGroup, dependencies: dependencies)
    }
  }

  private static func resolveBinding(
    _ request: SyncMessage,
    accounts: any AccountBindingSource
  ) -> SyncMessage {
    switch accounts.resolve(deadlineMilliseconds: request.deadlineMilliseconds) {
    case .available(let binding):
      return request.respond(outcome: .found, payload: binding)
    case .unavailable:
      return request.respond(outcome: .unavailable)
    case .restricted:
      return request.respond(outcome: .restricted)
    case .undetermined:
      return request.respond(outcome: .undetermined)
    }
  }

  private static func readItem(
    _ request: SyncMessage,
    accessGroup: String,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    guard var fields = keyFields(from: request.payload, expectingItem: false) else {
      return request.respond(outcome: .integrityFailure)
    }
    defer { fields.clear() }
    switch preflight(request, expected: fields.binding, accounts: dependencies.accounts) {
    case .proceed:
      break
    case .respond(let message):
      return message
    }
    let (result, changed) = observingAccountChange(name: dependencies.accountChangeName) {
      dependencies.keys.read(account: fields.account, accessGroup: accessGroup)
    }
    if changed || !postflight(request, expected: fields.binding, accounts: dependencies.accounts) {
      if case .found(var item) = result {
        item.resetBytes(in: item.startIndex..<item.endIndex)
      }
      return request.respond(outcome: .unknownOutcome)
    }
    return mapRead(result, request: request)
  }

  private static func createItem(
    _ request: SyncMessage,
    accessGroup: String,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    guard var fields = keyFields(from: request.payload, expectingItem: true) else {
      return request.respond(outcome: .integrityFailure)
    }
    defer { fields.clear() }
    switch preflight(request, expected: fields.binding, accounts: dependencies.accounts) {
    case .proceed:
      break
    case .respond(let message):
      return message
    }
    let (result, changed) = observingAccountChange(name: dependencies.accountChangeName) {
      dependencies.keys.create(
        account: fields.account,
        accessGroup: accessGroup,
        value: fields.item,
      )
    }
    if changed || !postflight(request, expected: fields.binding, accounts: dependencies.accounts) {
      return request.respond(outcome: .unknownOutcome)
    }
    return mapCreate(result, request: request)
  }

  private static func deleteItem(
    _ request: SyncMessage,
    accessGroup: String,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    guard var fields = keyFields(from: request.payload, expectingItem: false) else {
      return request.respond(outcome: .integrityFailure)
    }
    defer { fields.clear() }
    switch preflight(request, expected: fields.binding, accounts: dependencies.accounts) {
    case .proceed:
      break
    case .respond(let message):
      return message
    }
    let (result, changed) = observingAccountChange(name: dependencies.accountChangeName) {
      dependencies.keys.deleteAndVerifyAbsent(
        account: fields.account,
        accessGroup: accessGroup,
      )
    }
    if changed || !postflight(request, expected: fields.binding, accounts: dependencies.accounts) {
      return request.respond(outcome: .unknownOutcome)
    }
    return mapDelete(result, request: request)
  }

  private static func observingAccountChange<Result>(
    name: Notification.Name,
    _ body: () -> Result
  ) -> (Result, Bool) {
    let flag = AccountChangeFlag()
    let observer = NotificationCenter.default.addObserver(
      forName: name,
      object: nil,
      queue: nil
    ) { _ in
      flag.mark()
    }
    defer { NotificationCenter.default.removeObserver(observer) }
    let result = body()
    return (result, flag.isMarked)
  }

  private enum Preflight {
    case proceed
    case respond(SyncMessage)
  }

  private static func preflight(
    _ request: SyncMessage,
    expected: Data,
    accounts: any AccountBindingSource
  ) -> Preflight {
    switch accounts.resolve(deadlineMilliseconds: request.deadlineMilliseconds) {
    case .available(let current):
      if ItemCodec.constantTimeEquals(current, expected) {
        return .proceed
      }
      return .respond(request.respond(outcome: .accountChanged))
    case .unavailable, .restricted, .undetermined:
      return .respond(request.respond(outcome: .retryable))
    }
  }

  private static func postflight(
    _ request: SyncMessage,
    expected: Data,
    accounts: any AccountBindingSource
  ) -> Bool {
    switch accounts.resolve(deadlineMilliseconds: request.deadlineMilliseconds) {
    case .available(let current):
      return ItemCodec.constantTimeEquals(current, expected)
    case .unavailable, .restricted, .undetermined:
      return false
    }
  }

  private struct KeyFields {
    var binding: Data
    var account: String
    var item: Data

    mutating func clear() {
      binding.resetBytes(in: binding.startIndex..<binding.endIndex)
      item.resetBytes(in: item.startIndex..<item.endIndex)
    }
  }

  private static func keyFields(from payload: Data, expectingItem: Bool) -> KeyFields? {
    let expectedCount =
      SyncLimits.bindingBytes + SyncLimits.accountBytes + (expectingItem ? SyncLimits.itemBytes : 0)
    guard payload.count == expectedCount else {
      return nil
    }
    let binding = payload.subdata(in: 0..<SyncLimits.bindingBytes)
    let accountData = payload.subdata(
      in: SyncLimits.bindingBytes..<(SyncLimits.bindingBytes + SyncLimits.accountBytes)
    )
    guard let account = String(data: accountData, encoding: .utf8),
      ItemCodec.isCanonicalAccount(account)
    else {
      return nil
    }
    let item: Data
    if expectingItem {
      let raw = payload.suffix(SyncLimits.itemBytes)
      guard let validated = ItemCodec.validateItem(Data(raw), account: account) else {
        return nil
      }
      item = validated
    } else {
      item = Data()
    }
    return KeyFields(binding: binding, account: account, item: item)
  }

  private static func mapRead(_ result: KeyItemNative, request: SyncMessage) -> SyncMessage {
    switch result {
    case .found(let item):
      return request.respond(outcome: .found, payload: item)
    case .missing:
      return request.respond(outcome: .missing)
    case .retryable:
      return request.respond(outcome: .retryable)
    case .integrityFailure:
      return request.respond(outcome: .integrityFailure)
    case .unknownOutcome:
      return request.respond(outcome: .unknownOutcome)
    case .created, .identical, .deletedAndAbsent:
      return request.respond(outcome: .unknownOutcome)
    }
  }

  private static func mapCreate(_ result: KeyItemNative, request: SyncMessage) -> SyncMessage {
    switch result {
    case .created:
      return request.respond(outcome: .created)
    case .identical:
      return request.respond(outcome: .identical)
    case .retryable:
      return request.respond(outcome: .retryable)
    case .integrityFailure:
      return request.respond(outcome: .integrityFailure)
    case .unknownOutcome:
      return request.respond(outcome: .unknownOutcome)
    case .found, .missing, .deletedAndAbsent:
      return request.respond(outcome: .unknownOutcome)
    }
  }

  private static func mapDelete(_ result: KeyItemNative, request: SyncMessage) -> SyncMessage {
    switch result {
    case .deletedAndAbsent:
      return request.respond(outcome: .deletedAndAbsent)
    case .retryable:
      return request.respond(outcome: .retryable)
    case .integrityFailure:
      return request.respond(outcome: .integrityFailure)
    case .unknownOutcome:
      return request.respond(outcome: .unknownOutcome)
    case .found, .missing, .created, .identical:
      return request.respond(outcome: .unknownOutcome)
    }
  }
}

private final class AccountChangeFlag: @unchecked Sendable {
  private let lock = NSLock()
  private var marked = false

  func mark() {
    lock.lock()
    marked = true
    lock.unlock()
  }

  var isMarked: Bool {
    lock.lock()
    defer { lock.unlock() }
    return marked
  }
}
