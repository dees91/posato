import CloudKit
import Foundation

struct RecordDeletion: Sendable {
  var backend: any CloudBackend

  func deleteWorkspaceRecords(timeout: TimeInterval) -> RecordDeleteNative {
    let names: [String]
    switch traverseBundleNames(timeout: timeout) {
    case .zoneMissing:
      return .deletedAndAbsent
    case .failed(let fault):
      return mapFault(fault)
    case .bundles(let enumerated):
      names = enumerated
    }
    if let fault = delete(names: names, timeout: timeout) {
      return mapFault(fault)
    }
    if let fault = delete(names: [CloudNames.anchorName], timeout: timeout) {
      return mapFault(fault)
    }
    return verifyAbsent(timeout: timeout)
  }

  func sweepBundlesIfAnchorMissing(timeout: TimeInterval) -> BundleSweepNative {
    let names: [String]
    switch traverseBundleNames(timeout: timeout) {
    case .zoneMissing:
      return .swept
    case .failed(let fault):
      return mapSweepFault(fault)
    case .bundles(let enumerated):
      names = enumerated
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
    if let fault = delete(names: names, timeout: timeout) {
      return mapSweepFault(fault)
    }
    return .swept
  }

  private func delete(names: [String], timeout: TimeInterval) -> BackendFault? {
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

  private func verifyAbsent(timeout: TimeInterval) -> RecordDeleteNative {
    switch backend.fetchRecord(name: CloudNames.anchorName, timeout: timeout) {
    case .missing, .zoneMissing:
      break
    case .found:
      return .unknownOutcome
    case .failed(let fault):
      return mapFault(fault)
    }
    switch traverseBundleNames(timeout: timeout) {
    case .zoneMissing:
      return .deletedAndAbsent
    case .failed(let fault):
      return mapFault(fault)
    case .bundles(let remaining):
      return remaining.isEmpty ? .deletedAndAbsent : .unknownOutcome
    }
  }

  private func traverseBundleNames(timeout: TimeInterval) -> BundleTraversal {
    var state = BundleTraversalState()
    while true {
      switch backend.fetchChanges(tokenData: state.token, timeout: timeout) {
      case .tokenExpired where !state.restarted:
        state.restart()
      case .tokenExpired:
        return .failed(.retryable)
      case .zoneMissing:
        return .zoneMissing
      case .failed(let fault):
        return .failed(fault)
      case .fetched(let fetched):
        if let finished = state.absorb(fetched) {
          return finished
        }
      }
    }
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

private enum BundleTraversal: Equatable {
  case bundles([String])
  case zoneMissing
  case failed(BackendFault)
}

private struct BundleTraversalState {
  var token: Data?
  var names: [String] = []
  var restarted = false
  var previous: Data?

  mutating func restart() {
    token = nil
    names = []
    restarted = true
    previous = nil
  }

  mutating func absorb(_ fetched: BackendChanges) -> BundleTraversal? {
    for record in fetched.changed where record.type == CloudNames.bundleType {
      names.append(record.name)
    }
    guard let archived = fetched.token, !archived.isEmpty,
      archived.count <= SyncLimits.cursorBytes
    else {
      return .failed(.unknown)
    }
    if !fetched.moreComing {
      return .bundles(names)
    }
    guard archived != previous else {
      return .failed(.unknown)
    }
    previous = archived
    token = archived
    return nil
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
