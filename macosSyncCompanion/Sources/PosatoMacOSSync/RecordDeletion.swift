import CloudKit
import Foundation

struct RecordDeletion: Sendable {
  var backend: any CloudBackend

  func deleteWorkspaceRecords(
    timeout: TimeInterval,
    resumeToken: Data? = nil,
    pageBudget: Int = SyncLimits.recordDeletePageBudget,
    deadlineNanoseconds: UInt64 = .max,
    nowNanoseconds: @Sendable @escaping () -> UInt64 = { DispatchTime.now().uptimeNanoseconds }
  ) -> RecordDeleteNative {
    guard let (phase, server) = splitDeleteResumeToken(resumeToken) else {
      return .integrityFailure
    }
    var pass = DeletePass(
      backend: backend, timeout: timeout, deadlineNanoseconds: deadlineNanoseconds,
      nowNanoseconds: nowNanoseconds, phase: phase, token: server, previous: server,
      pagesLeft: pageBudget
    )
    return pass.runDelete()
  }

  func sweepBundlesIfAnchorMissing(
    timeout: TimeInterval,
    resumeToken: Data? = nil,
    pageBudget: Int = SyncLimits.recordDeletePageBudget,
    deadlineNanoseconds: UInt64 = .max,
    nowNanoseconds: @Sendable @escaping () -> UInt64 = { DispatchTime.now().uptimeNanoseconds }
  ) -> BundleSweepNative {
    var pass = DeletePass(
      backend: backend, timeout: timeout, deadlineNanoseconds: deadlineNanoseconds,
      nowNanoseconds: nowNanoseconds, phase: .traverse, token: resumeToken, previous: resumeToken,
      pagesLeft: pageBudget
    )
    return pass.runSweep()
  }

}

/// One bounded traversal pass. The token advances only past pages whose
/// bundles were deleted, so a token handed to the next request implies every
/// earlier page is gone; a killed request resumes from the previous token.
/// The pass carries its phase in the token: traversal deletes enumerated
/// sets with the own anchor present, while verification runs only after this
/// pass deleted the anchor and aborts on any live bundle or present anchor.
struct DeletePass {
  let backend: any CloudBackend
  let timeout: TimeInterval
  let deadlineNanoseconds: UInt64
  let nowNanoseconds: @Sendable () -> UInt64
  var phase: DeleteResumePhase
  var token: Data?
  var previous: Data?
  var pagesLeft: Int
  var restarted = false

  mutating func runDelete() -> RecordDeleteNative {
    if phase == .verify {
      switch verifyEntryGate() {
      case .proceed:
        break
      case .abort(let outcome):
        return outcome
      }
      return verifyFromToken()
    }
    if let outcome = drainBundles() {
      return outcome
    }
    if let outcome = removeAnchor() {
      return outcome
    }
    phase = .verify
    return verifyFromToken()
  }

  private enum EntryGate {
    case proceed
    case abort(RecordDeleteNative)
  }

  /// Verification runs only after this removal deleted the anchor, so any
  /// present anchor belongs to a concurrently established workspace: abort
  /// without touching or trusting anything it published.
  private func verifyEntryGate() -> EntryGate {
    switch backend.fetchRecord(name: CloudNames.anchorName, timeout: callTimeout()) {
    case .missing:
      return .proceed
    case .found:
      return .abort(.unknownOutcome)
    case .zoneMissing:
      return .abort(.deletedAndAbsent)
    case .failed(let fault):
      return .abort(mapFault(fault))
    }
  }

  /// Deletes bundles page by page. Returns nil once the traversal is
  /// exhausted and the anchor phase may run. The drain intentionally reads
  /// no anchor: full removal runs with the own anchor present, and presence
  /// alone cannot tell it apart from a concurrently established one. A
  /// foreign anchor is caught instead by the verify entry gate and the
  /// anchor-delete tail below.
  mutating func drainBundles() -> RecordDeleteNative? {
    while true {
      if pagesLeft <= 0 || timeUp() {
        return checkpoint()
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
        // Bank only past deleted pages: a checkpoint implies every earlier
        // page is gone, so a resume never skips an enumerated set whose
        // deletes never ran. Replaying a page after a kill is safe because
        // deletes are idempotent; skipping one would strand live records
        // behind the resumed token and the verify scan alike.
        token = pageToken
        if timeUp() {
          return checkpoint()
        }
      }
    }
  }

  /// Anchor deletion stays unconditional: a missing anchor reports success
  /// through the delete mapper, which keeps resumed passes idempotent.
  func removeAnchor() -> RecordDeleteNative? {
    if let fault = delete(names: [CloudNames.anchorName]) {
      return mapFault(fault)
    }
    switch backend.fetchRecord(name: CloudNames.anchorName, timeout: callTimeout()) {
    case .missing, .zoneMissing:
      return nil
    case .found:
      return .unknownOutcome
    case .failed(let fault):
      return mapFault(fault)
    }
  }

  /// Absence scan continuing from the drain's end token: the drain already
  /// covered every earlier page, so only newer changes can appear here.
  /// Live bundles mean a concurrent publish, which removal must not delete
  /// blindly, so they abort as unknown instead.
  mutating func verifyFromToken() -> RecordDeleteNative {
    while true {
      if pagesLeft <= 0 || timeUp() {
        return checkpoint()
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
        // Verification deletes nothing, so banking past a confirmed-empty
        // page keeps the checkpoint honest by construction.
        token = pageToken
        if timeUp() {
          return checkpoint()
        }
      }
    }
  }

  /// Emits the resumable checkpoint, always carrying the phase: a nil
  /// server token with the verify phase still resumes verifying instead of
  /// re-entering traversal. A fresh traversal with nothing banked reports
  /// plain retryable.
  private func checkpoint() -> RecordDeleteNative {
    if token == nil, phase == .traverse {
      return .retryable
    }
    return .incomplete(cursor: encodeDeleteResumeToken(phase: phase, server: token))
  }

  private func remainingNanoseconds() -> UInt64 {
    let now = nowNanoseconds()
    guard now < deadlineNanoseconds else {
      return 0
    }
    return deadlineNanoseconds - now
  }

  /// Per-call budget for each backend operation, shrinking as the pass
  /// consumes the request deadline. No fetch or delete batch may start a
  /// wait the checkpoint could not survive.
  func callTimeout() -> TimeInterval {
    if deadlineNanoseconds == .max {
      return timeout
    }
    return TimeInterval(remainingNanoseconds()) / 1_000_000_000
  }

  func timeUp() -> Bool {
    guard deadlineNanoseconds != .max else {
      return false
    }
    return remainingNanoseconds() <= SyncLimits.deleteCheckpointReserveNanoseconds
  }

  func delete(names: [String]) -> BackendFault? {
    for chunk in chunked(names, size: SyncLimits.recordDeleteBatchSize) {
      switch backend.deleteRecords(names: chunk, timeout: callTimeout()) {
      case .deleted:
        break
      case .failed(let fault):
        return fault
      }
    }
    return nil
  }

  mutating func nextPage() -> PageStep {
    while true {
      switch backend.fetchChanges(tokenData: token, timeout: callTimeout()) {
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
    // A repeated token with more pages coming means the server made no
    // progress: abort instead of looping. A terminal page may legitimately
    // echo the previous token, and every exhausted branch below leaves its
    // loop, so the guard applies only while more pages remain.
    if fetched.moreComing {
      guard archived != previous else {
        return .failed(.unknown)
      }
      previous = archived
      return .page(names: names, token: archived)
    }
    previous = archived
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

}

enum PageStep {
  case page(names: [String], token: Data)
  case exhausted(names: [String], token: Data)
  case zoneMissing
  case failed(BackendFault)
}

extension CloudStore {
  func deleteWorkspaceRecords(
    timeout: TimeInterval,
    resumeToken: Data? = nil,
    pageBudget: Int = SyncLimits.recordDeletePageBudget,
    deadlineNanoseconds: UInt64 = .max,
    nowNanoseconds: @Sendable @escaping () -> UInt64 = { DispatchTime.now().uptimeNanoseconds }
  ) -> RecordDeleteNative {
    return RecordDeletion(backend: backend).deleteWorkspaceRecords(
      timeout: timeout,
      resumeToken: resumeToken,
      pageBudget: pageBudget,
      deadlineNanoseconds: deadlineNanoseconds,
      nowNanoseconds: nowNanoseconds
    )
  }

  func sweepBundlesIfAnchorMissing(
    timeout: TimeInterval,
    resumeToken: Data? = nil,
    pageBudget: Int = SyncLimits.recordDeletePageBudget,
    deadlineNanoseconds: UInt64 = .max,
    nowNanoseconds: @Sendable @escaping () -> UInt64 = { DispatchTime.now().uptimeNanoseconds }
  ) -> BundleSweepNative {
    return RecordDeletion(backend: backend).sweepBundlesIfAnchorMissing(
      timeout: timeout,
      resumeToken: resumeToken,
      pageBudget: pageBudget,
      deadlineNanoseconds: deadlineNanoseconds,
      nowNanoseconds: nowNanoseconds
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
