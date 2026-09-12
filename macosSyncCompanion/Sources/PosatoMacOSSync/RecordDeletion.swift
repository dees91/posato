import CloudKit
import Foundation

struct RecordDeletion: Sendable {
  var backend: any CloudBackend

  func deleteWorkspaceRecords(
    timeout: TimeInterval,
    resumeToken: Data? = nil,
    pageBudget: Int = SyncLimits.recordDeletePageBudget
  ) -> RecordDeleteNative {
    var pass = DeletePass(
      backend: backend, timeout: timeout, token: resumeToken, pagesLeft: pageBudget
    )
    if let outcome = pass.drainBundles() {
      return outcome
    }
    if let outcome = pass.removeAnchor() {
      return outcome
    }
    return pass.verifyAbsent()
  }

  func sweepBundlesIfAnchorMissing(
    timeout: TimeInterval,
    resumeToken: Data? = nil,
    pageBudget: Int = SyncLimits.recordDeletePageBudget
  ) -> BundleSweepNative {
    var pass = DeletePass(
      backend: backend, timeout: timeout, token: resumeToken, pagesLeft: pageBudget
    )
    return pass.runSweep()
  }

  private func mapFault(_ fault: BackendFault) -> RecordDeleteNative {
    switch fault {
    case .retryable:
      return .retryable
    case .unknown:
      return .unknownOutcome
    }
  }

  private func mapSweepFault(_ fault: BackendFault) -> BundleSweepNative {
    switch fault {
    case .retryable:
      return .retryable
    case .unknown:
      return .unknownOutcome
    }
  }
}

/// One bounded traversal pass. The token advances only past pages whose
/// bundles were deleted, so a token handed to the next request implies every
/// earlier page is gone; a killed request resumes from the previous token.
private struct DeletePass {
  let backend: any CloudBackend
  let timeout: TimeInterval
  var token: Data?
  var pagesLeft: Int
  var restarted = false
  var previous: Data?

  /// Deletes bundles page by page. Returns nil once the traversal is
  /// exhausted and the anchor phase may run.
  mutating func drainBundles() -> RecordDeleteNative? {
    while true {
      if pagesLeft <= 0 {
        return incomplete()
      }
      switch nextPage() {
      case .zoneMissing:
        return .deletedAndAbsent
      case .failed(let fault):
        return mapFault(fault)
      case .exhausted(let names, let exhaustedToken):
        pagesLeft -= 1
        if let fault = delete(names: names) {
          return mapFault(fault)
        }
        token = exhaustedToken
        return nil
      case .page(let names, let pageToken):
        pagesLeft -= 1
        if let fault = delete(names: names) {
          return mapFault(fault)
        }
        token = pageToken
      }
    }
  }

  /// Anchor deletion stays unconditional: a missing anchor reports success
  /// through the delete mapper, which keeps resumed passes idempotent.
  func removeAnchor() -> RecordDeleteNative? {
    if let fault = delete(names: [CloudNames.anchorName]) {
      return mapFault(fault)
    }
    switch backend.fetchRecord(name: CloudNames.anchorName, timeout: timeout) {
    case .missing, .zoneMissing:
      return nil
    case .found:
      return .unknownOutcome
    case .failed(let fault):
      return mapFault(fault)
    }
  }

  /// Confirms absence from the current token. Live bundles here mean a
  /// concurrent publish, which removal must not delete blindly, so this
  /// phase stays terminal: it reports unknown instead of resuming.
  mutating func verifyAbsent() -> RecordDeleteNative {
    while true {
      if pagesLeft <= 0 {
        return .unknownOutcome
      }
      switch nextPage() {
      case .zoneMissing:
        return .deletedAndAbsent
      case .failed(let fault):
        return mapFault(fault)
      case .exhausted(let names, _):
        pagesLeft -= 1
        return names.isEmpty ? .deletedAndAbsent : .unknownOutcome
      case .page(let names, let pageToken):
        pagesLeft -= 1
        if !names.isEmpty {
          return .unknownOutcome
        }
        token = pageToken
      }
    }
  }

  /// Sweeps leftover bundles while no anchor exists. Every page is
  /// re-checked against the anchor after enumeration and before deletion,
  /// so a bundle published under a freshly minted anchor is never removed.
  mutating func runSweep() -> BundleSweepNative {
    switch backend.fetchRecord(name: CloudNames.anchorName, timeout: timeout) {
    case .missing:
      break
    case .found:
      return .anchorPresent
    case .zoneMissing:
      return .swept
    case .failed(let fault):
      return mapSweepFault(fault)
    }
    while true {
      switch sweepStep() {
      case .proceed(let pageToken):
        token = pageToken
      case .done(let outcome):
        return outcome
      }
    }
  }

  private enum SweepContinuation {
    case proceed(token: Data)
    case done(BundleSweepNative)
  }

  private mutating func sweepStep() -> SweepContinuation {
    if pagesLeft <= 0 {
      return .done(sweepIncomplete())
    }
    switch nextPage() {
    case .zoneMissing:
      return .done(.swept)
    case .failed(let fault):
      return .done(mapSweepFault(fault))
    case .exhausted(let names, _):
      pagesLeft -= 1
      return .done(deleteSweepPage(names: names))
    case .page(let names, let pageToken):
      pagesLeft -= 1
      let outcome = deleteSweepPage(names: names)
      if outcome != .swept {
        return .done(outcome)
      }
      return .proceed(token: pageToken)
    }
  }

  private mutating func deleteSweepPage(names: [String]) -> BundleSweepNative {
    if names.isEmpty {
      return .swept
    }
    switch backend.fetchRecord(name: CloudNames.anchorName, timeout: timeout) {
    case .missing:
      break
    case .found:
      return .anchorPresent
    case .zoneMissing:
      return .swept
    case .failed(let fault):
      return mapSweepFault(fault)
    }
    if let fault = delete(names: names) {
      return mapSweepFault(fault)
    }
    return .swept
  }

  private func sweepIncomplete() -> BundleSweepNative {
    guard let cursor = token else {
      return .unknownOutcome
    }
    return .incomplete(cursor: cursor)
  }

  private func incomplete() -> RecordDeleteNative {
    guard let cursor = token else {
      return .unknownOutcome
    }
    return .incomplete(cursor: cursor)
  }

  private func delete(names: [String]) -> BackendFault? {
    for chunk in chunked(names, size: SyncLimits.recordDeleteBatchSize) {
      switch backend.deleteRecords(names: chunk, timeout: timeout) {
      case .deleted:
        break
      case .failed(let fault):
        return fault
      }
    }
    return nil
  }

  private mutating func nextPage() -> PageStep {
    while true {
      switch backend.fetchChanges(tokenData: token, timeout: timeout) {
      case .tokenExpired where !restarted:
        restart()
      case .tokenExpired:
        return .failed(.retryable)
      case .zoneMissing:
        return .zoneMissing
      case .failed(let fault):
        return .failed(fault)
      case .fetched(let fetched):
        return ingest(fetched)
      }
    }
  }

  private mutating func ingest(_ fetched: BackendChanges) -> PageStep {
    var names: [String] = []
    for record in fetched.changed where record.type == CloudNames.bundleType {
      names.append(record.name)
    }
    guard let archived = fetched.token, !archived.isEmpty,
      archived.count <= SyncLimits.cursorBytes
    else {
      return .failed(.unknown)
    }
    guard archived != previous else {
      return .failed(.unknown)
    }
    previous = archived
    if fetched.moreComing {
      return .page(names: names, token: archived)
    }
    return .exhausted(names: names, token: archived)
  }

  private mutating func restart() {
    token = nil
    restarted = true
    previous = nil
  }

  private func mapFault(_ fault: BackendFault) -> RecordDeleteNative {
    switch fault {
    case .retryable:
      return .retryable
    case .unknown:
      return .unknownOutcome
    }
  }

  private func mapSweepFault(_ fault: BackendFault) -> BundleSweepNative {
    switch fault {
    case .retryable:
      return .retryable
    case .unknown:
      return .unknownOutcome
    }
  }
}

private enum PageStep {
  case page(names: [String], token: Data)
  case exhausted(names: [String], token: Data)
  case zoneMissing
  case failed(BackendFault)
}

extension CloudStore {
  func deleteWorkspaceRecords(
    timeout: TimeInterval,
    resumeToken: Data? = nil,
    pageBudget: Int = SyncLimits.recordDeletePageBudget
  ) -> RecordDeleteNative {
    return RecordDeletion(backend: backend).deleteWorkspaceRecords(
      timeout: timeout,
      resumeToken: resumeToken,
      pageBudget: pageBudget
    )
  }

  func sweepBundlesIfAnchorMissing(
    timeout: TimeInterval,
    resumeToken: Data? = nil,
    pageBudget: Int = SyncLimits.recordDeletePageBudget
  ) -> BundleSweepNative {
    return RecordDeletion(backend: backend).sweepBundlesIfAnchorMissing(
      timeout: timeout,
      resumeToken: resumeToken,
      pageBudget: pageBudget
    )
  }
}

private func chunked(_ names: [String], size: Int) -> [[String]] {
  guard size > 0 else {
    return names.isEmpty ? [] : [names]
  }
  var chunks: [[String]] = []
  var index = names.startIndex
  while index < names.endIndex {
    let next = names.index(index, offsetBy: size, limitedBy: names.endIndex) ?? names.endIndex
    chunks.append(Array(names[index..<next]))
    index = next
  }
  return chunks
}
