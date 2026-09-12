import CloudKit
import Foundation

extension RequestHandler {
  static func handleCloud(
    _ request: SyncMessage,
    started: DispatchTime,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    switch request.operation {
    case .fetchZone:
      return fetchZone(request, started: started, dependencies: dependencies)
    case .saveZone:
      return saveZone(request, started: started, dependencies: dependencies)
    case .readAnchor:
      return readAnchor(request, started: started, dependencies: dependencies)
    case .createAnchor:
      return createAnchor(request, started: started, dependencies: dependencies)
    case .saveBundle:
      return saveBundle(request, started: started, dependencies: dependencies)
    case .fetchChanges:
      return fetchChanges(request, started: started, dependencies: dependencies)
    case .deleteWorkspaceRecords:
      return deleteRecords(request, started: started, dependencies: dependencies)
    case .sweepBundlesIfAnchorMissing:
      return sweepBundles(request, started: started, dependencies: dependencies)
    case .resolveBinding, .readItem, .createItem, .deleteItemAndVerifyAbsent:
      return request.respond(outcome: .unknownOutcome)
    }
  }

  static func hasRequiredCapability(_ request: SyncMessage) -> Bool {
    switch request.operation {
    case .resolveBinding:
      let allowed = SyncLimits.keychainCapability | SyncLimits.cloudkitCapability
      return request.capabilities & allowed != 0
    case .readItem, .createItem, .deleteItemAndVerifyAbsent:
      return request.capabilities & SyncLimits.keychainCapability
        == SyncLimits.keychainCapability
    case .fetchZone,
      .saveZone,
      .readAnchor,
      .createAnchor,
      .saveBundle,
      .fetchChanges,
      .deleteWorkspaceRecords,
      .sweepBundlesIfAnchorMissing:
      return request.capabilities & SyncLimits.cloudkitCapability
        == SyncLimits.cloudkitCapability
    }
  }

  private static func fetchZone(
    _ request: SyncMessage,
    started: DispatchTime,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    guard let binding = CloudRequestCodec.bindingOnly(request.payload) else {
      return request.respond(outcome: .integrityFailure)
    }
    switch RequestSupport.preflight(
      request,
      expected: binding,
      started: started,
      accounts: dependencies.accounts
    ) {
    case .proceed:
      break
    case .respond(let message):
      return message
    }
    let timeout = DeadlineBudget.remainingSeconds(
      started: started,
      budgetMilliseconds: request.deadlineMilliseconds
    )
    let (result, changed) = RequestSupport.observingAccountChange(
      name: dependencies.accountChangeName
    ) {
      dependencies.clouds.fetchZone(timeout: timeout)
    }
    guard !changed,
      RequestSupport.postflight(
        request,
        expected: binding,
        started: started,
        accounts: dependencies.accounts
      )
    else {
      return request.respond(outcome: .unknownOutcome)
    }
    switch result {
    case .found:
      return request.respond(outcome: .found)
    case .missing:
      return request.respond(outcome: .missing)
    case .retryable:
      return request.respond(outcome: .retryable)
    case .unknownOutcome:
      return request.respond(outcome: .unknownOutcome)
    }
  }

  private static func saveZone(
    _ request: SyncMessage,
    started: DispatchTime,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    guard let binding = CloudRequestCodec.bindingOnly(request.payload) else {
      return request.respond(outcome: .integrityFailure)
    }
    switch RequestSupport.preflight(
      request,
      expected: binding,
      started: started,
      accounts: dependencies.accounts
    ) {
    case .proceed:
      break
    case .respond(let message):
      return message
    }
    let timeout = DeadlineBudget.remainingSeconds(
      started: started,
      budgetMilliseconds: request.deadlineMilliseconds
    )
    let (result, changed) = RequestSupport.observingAccountChange(
      name: dependencies.accountChangeName
    ) {
      dependencies.clouds.saveZone(timeout: timeout)
    }
    guard !changed,
      RequestSupport.postflight(
        request,
        expected: binding,
        started: started,
        accounts: dependencies.accounts
      )
    else {
      return request.respond(outcome: .unknownOutcome)
    }
    switch result {
    case .created:
      return request.respond(outcome: .created)
    case .alreadyExists:
      return request.respond(outcome: .alreadyExists)
    case .retryable:
      return request.respond(outcome: .retryable)
    case .unknownOutcome:
      return request.respond(outcome: .unknownOutcome)
    }
  }

  private static func readAnchor(
    _ request: SyncMessage,
    started: DispatchTime,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    guard let binding = CloudRequestCodec.bindingOnly(request.payload) else {
      return request.respond(outcome: .integrityFailure)
    }
    switch RequestSupport.preflight(
      request,
      expected: binding,
      started: started,
      accounts: dependencies.accounts
    ) {
    case .proceed:
      break
    case .respond(let message):
      return message
    }
    let timeout = DeadlineBudget.remainingSeconds(
      started: started,
      budgetMilliseconds: request.deadlineMilliseconds
    )
    let (result, changed) = RequestSupport.observingAccountChange(
      name: dependencies.accountChangeName
    ) {
      dependencies.clouds.readAnchor(timeout: timeout)
    }
    let bindingHolds = RequestSupport.postflight(
      request,
      expected: binding,
      started: started,
      accounts: dependencies.accounts
    )
    if changed || !bindingHolds {
      if case .found(var fields) = result {
        fields.resetBytes(in: fields.startIndex..<fields.endIndex)
      }
      return request.respond(outcome: .unknownOutcome)
    }
    switch result {
    case .found(let fields):
      return request.respond(outcome: .found, payload: fields)
    case .missing:
      return request.respond(outcome: .missing)
    case .retryable:
      return request.respond(outcome: .retryable)
    case .unknownOutcome:
      return request.respond(outcome: .unknownOutcome)
    case .integrityFailure:
      return request.respond(outcome: .integrityFailure)
    }
  }

  private static func createAnchor(
    _ request: SyncMessage,
    started: DispatchTime,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    guard let parsed = CloudRequestCodec.anchorRequest(request.payload) else {
      return request.respond(outcome: .integrityFailure)
    }
    switch RequestSupport.preflight(
      request,
      expected: parsed.binding,
      started: started,
      accounts: dependencies.accounts
    ) {
    case .proceed:
      break
    case .respond(let message):
      return message
    }
    let timeout = DeadlineBudget.remainingSeconds(
      started: started,
      budgetMilliseconds: request.deadlineMilliseconds
    )
    let (result, changed) = RequestSupport.observingAccountChange(
      name: dependencies.accountChangeName
    ) {
      dependencies.clouds.createAnchor(fields: parsed.fields, timeout: timeout)
    }
    guard !changed,
      RequestSupport.postflight(
        request,
        expected: parsed.binding,
        started: started,
        accounts: dependencies.accounts
      )
    else {
      return request.respond(outcome: .unknownOutcome)
    }
    switch result {
    case .created:
      return request.respond(outcome: .created)
    case .conflict:
      return request.respond(outcome: .conflict)
    case .retryable:
      return request.respond(outcome: .retryable)
    case .unknownOutcome:
      return request.respond(outcome: .unknownOutcome)
    case .integrityFailure:
      return request.respond(outcome: .integrityFailure)
    }
  }
}
