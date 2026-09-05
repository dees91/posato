import CloudKit
import Foundation

struct SyncDependencies: Sendable {
  var entitlements: any EntitlementReader
  var accounts: any AccountBindingSource
  var keys: WorkspaceKeyStore
  var clouds: CloudStore
  var accountChangeName: Notification.Name = .CKAccountChanged
}

enum RequestHandler {
  static func handle(_ request: SyncMessage, dependencies: SyncDependencies) -> SyncMessage {
    guard hasRequiredCapability(request) else {
      return request.respond(outcome: .unknownOutcome)
    }
    guard let entitlements = dependencies.entitlements.load(),
      let accessGroup = EntitlementGuard.accessGroup(from: entitlements)
    else {
      let outcome: SyncOutcome =
        request.operation == .resolveBinding ? .unavailable : .retryable
      return request.respond(outcome: outcome)
    }
    let started = DispatchTime.now()
    switch request.operation {
    case .resolveBinding, .readItem, .createItem, .deleteItemAndVerifyAbsent:
      return handleKeychain(
        request,
        accessGroup: accessGroup,
        started: started,
        dependencies: dependencies
      )
    case .fetchZone,
      .saveZone,
      .readAnchor,
      .createAnchor,
      .saveBundle,
      .fetchChanges,
      .deleteZoneAndVerifyAbsent:
      return handleCloud(request, started: started, dependencies: dependencies)
    }
  }

  private static func handleKeychain(
    _ request: SyncMessage,
    accessGroup: String,
    started: DispatchTime,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    switch request.operation {
    case .resolveBinding:
      return resolveBinding(request, started: started, accounts: dependencies.accounts)
    case .readItem:
      return readItem(
        request,
        accessGroup: accessGroup,
        started: started,
        dependencies: dependencies
      )
    case .createItem:
      return createItem(
        request,
        accessGroup: accessGroup,
        started: started,
        dependencies: dependencies
      )
    case .deleteItemAndVerifyAbsent:
      return deleteItem(
        request,
        accessGroup: accessGroup,
        started: started,
        dependencies: dependencies
      )
    case .fetchZone,
      .saveZone,
      .readAnchor,
      .createAnchor,
      .saveBundle,
      .fetchChanges,
      .deleteZoneAndVerifyAbsent:
      return request.respond(outcome: .unknownOutcome)
    }
  }

  private static func resolveBinding(
    _ request: SyncMessage,
    started: DispatchTime,
    accounts: any AccountBindingSource
  ) -> SyncMessage {
    switch RequestSupport.accountBinding(request, started: started, accounts: accounts) {
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
    started: DispatchTime,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    guard var fields = KeyRequestCodec.fields(from: request.payload, expectingItem: false) else {
      return request.respond(outcome: .integrityFailure)
    }
    defer { fields.clear() }
    switch RequestSupport.preflight(
      request,
      expected: fields.binding,
      started: started,
      accounts: dependencies.accounts
    ) {
    case .proceed:
      break
    case .respond(let message):
      return message
    }
    let (result, changed) = RequestSupport.observingAccountChange(
      name: dependencies.accountChangeName
    ) {
      dependencies.keys.read(account: fields.account, accessGroup: accessGroup)
    }
    let bindingHolds = RequestSupport.postflight(
      request,
      expected: fields.binding,
      started: started,
      accounts: dependencies.accounts
    )
    if changed || !bindingHolds {
      if case .found(var item) = result {
        item.resetBytes(in: item.startIndex..<item.endIndex)
      }
      return request.respond(outcome: .unknownOutcome)
    }
    return KeyRequestCodec.mapRead(result, request: request)
  }

  private static func createItem(
    _ request: SyncMessage,
    accessGroup: String,
    started: DispatchTime,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    guard var fields = KeyRequestCodec.fields(from: request.payload, expectingItem: true) else {
      return request.respond(outcome: .integrityFailure)
    }
    defer { fields.clear() }
    switch RequestSupport.preflight(
      request,
      expected: fields.binding,
      started: started,
      accounts: dependencies.accounts
    ) {
    case .proceed:
      break
    case .respond(let message):
      return message
    }
    let (result, changed) = RequestSupport.observingAccountChange(
      name: dependencies.accountChangeName
    ) {
      dependencies.keys.create(
        account: fields.account,
        accessGroup: accessGroup,
        value: fields.item,
      )
    }
    let bindingHolds = RequestSupport.postflight(
      request,
      expected: fields.binding,
      started: started,
      accounts: dependencies.accounts
    )
    if changed || !bindingHolds {
      return request.respond(outcome: .unknownOutcome)
    }
    return KeyRequestCodec.mapCreate(result, request: request)
  }

  private static func deleteItem(
    _ request: SyncMessage,
    accessGroup: String,
    started: DispatchTime,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    guard var fields = KeyRequestCodec.fields(from: request.payload, expectingItem: false) else {
      return request.respond(outcome: .integrityFailure)
    }
    defer { fields.clear() }
    switch RequestSupport.preflight(
      request,
      expected: fields.binding,
      started: started,
      accounts: dependencies.accounts
    ) {
    case .proceed:
      break
    case .respond(let message):
      return message
    }
    let (result, changed) = RequestSupport.observingAccountChange(
      name: dependencies.accountChangeName
    ) {
      dependencies.keys.deleteAndVerifyAbsent(
        account: fields.account,
        accessGroup: accessGroup,
      )
    }
    let bindingHolds = RequestSupport.postflight(
      request,
      expected: fields.binding,
      started: started,
      accounts: dependencies.accounts
    )
    if changed || !bindingHolds {
      return request.respond(outcome: .unknownOutcome)
    }
    return KeyRequestCodec.mapDelete(result, request: request)
  }

}

private enum KeyRequestCodec {
  struct Fields {
    var binding: Data
    var account: String
    var item: Data

    mutating func clear() {
      binding.resetBytes(in: binding.startIndex..<binding.endIndex)
      item.resetBytes(in: item.startIndex..<item.endIndex)
    }
  }

  static func fields(from payload: Data, expectingItem: Bool) -> Fields? {
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
    return Fields(binding: binding, account: account, item: item)
  }

  static func mapRead(_ result: KeyItemNative, request: SyncMessage) -> SyncMessage {
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

  static func mapCreate(_ result: KeyItemNative, request: SyncMessage) -> SyncMessage {
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

  static func mapDelete(_ result: KeyItemNative, request: SyncMessage) -> SyncMessage {
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
