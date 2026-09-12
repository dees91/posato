import CloudKit
import Foundation

extension RequestHandler {
  static func saveBundle(
    _ request: SyncMessage,
    started: DispatchTime,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    guard let parsed = CloudRequestCodec.bundleRequest(request.payload) else {
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
      dependencies.clouds.saveBundle(
        identifier: parsed.identifier,
        payload: parsed.bundle,
        timeout: timeout
      )
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
    return mapBundleSave(result, request: request)
  }

  static func mapBundleSave(_ result: BundleSaveNative, request: SyncMessage) -> SyncMessage {
    switch result {
    case .created:
      return request.respond(outcome: .created)
    case .identical:
      return request.respond(outcome: .identical)
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

  static func fetchChanges(
    _ request: SyncMessage,
    started: DispatchTime,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    guard let parsed = CloudRequestCodec.cursorRequest(request.payload) else {
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
      dependencies.clouds.fetchChanges(cursor: parsed.cursor, timeout: timeout)
    }
    let bindingHolds = RequestSupport.postflight(
      request,
      expected: parsed.binding,
      started: started,
      accounts: dependencies.accounts
    )
    if changed || !bindingHolds {
      return request.respond(outcome: .unknownOutcome)
    }
    return mapChangeFetch(result, request: request)
  }

  static func mapChangeFetch(
    _ result: ChangeFetchNative,
    request: SyncMessage
  ) -> SyncMessage {
    switch result {
    case .tokenExpired:
      return request.respond(outcome: .tokenExpired)
    case .page(let page):
      guard
        let encoded = PageCodec.encode(
          ChangePageFields(
            moreComing: page.moreComing,
            cursor: page.cursor,
            bundleIdentifier: page.bundleIdentifier,
            bundle: page.bundle
          )
        )
      else {
        return request.respond(outcome: .unknownOutcome)
      }
      return request.respond(outcome: .found, payload: encoded)
    case .zoneMissing:
      return request.respond(outcome: .missing)
    case .retryable:
      return request.respond(outcome: .retryable)
    case .unknownOutcome:
      return request.respond(outcome: .unknownOutcome)
    case .integrityFailure:
      return request.respond(outcome: .integrityFailure)
    }
  }

  static func deleteRecords(
    _ request: SyncMessage,
    started: DispatchTime,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    guard let parsed = CloudRequestCodec.cursorRequest(request.payload) else {
      return request.respond(outcome: .integrityFailure)
    }
    let binding = parsed.binding
    let resumeToken = parsed.cursor.isEmpty ? nil : parsed.cursor
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
    let deadlineNanoseconds = passDeadlineNanoseconds(timeout: timeout)
    let (result, changed) = RequestSupport.observingAccountChange(
      name: dependencies.accountChangeName
    ) {
      dependencies.clouds.deleteWorkspaceRecords(
        timeout: timeout, resumeToken: resumeToken, deadlineNanoseconds: deadlineNanoseconds)
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
    return deleteResponse(request, result: result)
  }

  static func sweepBundles(
    _ request: SyncMessage,
    started: DispatchTime,
    dependencies: SyncDependencies
  ) -> SyncMessage {
    guard let parsed = CloudRequestCodec.cursorRequest(request.payload),
      // Sweep tokens are pure server tokens: anything longer is wire
      // garbage, rejected here exactly as before the delete-phase byte.
      parsed.cursor.count <= SyncLimits.cursorBytes
    else {
      return request.respond(outcome: .integrityFailure)
    }
    let binding = parsed.binding
    let resumeToken = parsed.cursor.isEmpty ? nil : parsed.cursor
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
    let deadlineNanoseconds = passDeadlineNanoseconds(timeout: timeout)
    let (result, changed) = RequestSupport.observingAccountChange(
      name: dependencies.accountChangeName
    ) {
      dependencies.clouds.sweepBundlesIfAnchorMissing(
        timeout: timeout, resumeToken: resumeToken, deadlineNanoseconds: deadlineNanoseconds)
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
    return sweepResponse(request, result: result)
  }
}

/// Wall-clock deadline for one delete/sweep pass: the request's remaining
/// budget as an absolute timestamp. The pass checkpoints against it with
/// time reserved for the response encoding and the caller's postflight.
private func passDeadlineNanoseconds(timeout: TimeInterval) -> UInt64 {
  DispatchTime.now().uptimeNanoseconds &+ UInt64(timeout * 1_000_000_000)
}

private func sweepResponse(_ request: SyncMessage, result: BundleSweepNative) -> SyncMessage {
  switch result {
  case .swept:
    return request.respond(outcome: .swept)
  case .anchorPresent:
    return request.respond(outcome: .anchorPresent)
  case .retryable:
    return request.respond(outcome: .retryable)
  case .unknownOutcome:
    return request.respond(outcome: .unknownOutcome)
  case .incomplete(let cursor):
    return request.respond(outcome: .incomplete, payload: cursor)
  }
}

private func deleteResponse(_ request: SyncMessage, result: RecordDeleteNative) -> SyncMessage {
  switch result {
  case .deletedAndAbsent:
    return request.respond(outcome: .deletedAndAbsent)
  case .retryable:
    return request.respond(outcome: .retryable)
  case .unknownOutcome:
    return request.respond(outcome: .unknownOutcome)
  case .integrityFailure:
    return request.respond(outcome: .integrityFailure)
  case .incomplete(let cursor):
    return request.respond(outcome: .incomplete, payload: cursor)
  }
}
