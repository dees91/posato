import CloudKit
import Foundation

/// Anchorless sweep: the delete pass drains one workspace with its
/// anchor present, while the sweep removes leftover bundles only
/// while no anchor exists, re-checking the anchor per set.
extension DeletePass {
  /// Sweeps leftover bundles while no anchor exists. Every page is
  /// re-checked against the anchor after enumeration and before deletion,
  /// so a bundle published under a freshly minted anchor is never removed.
  mutating func runSweep() -> BundleSweepNative {
    if timeUp() {
      return sweepCheckpoint()
    }
    switch backend.fetchRecord(name: CloudNames.anchorName, timeout: callTimeout()) {
    case .missing:
      break
    case .found:
      return .anchorPresent
    case .zoneMissing:
      return .swept
    case .failed(let fault):
      return sweepDeleteFault(fault)
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

  private mutating func sweepStep() -> SweepContinuation {
    if pagesLeft <= 0 || timeUp() {
      return .done(sweepCheckpoint())
    }
    switch nextPage() {
    case .zoneMissing:
      return .done(.swept)
    case .budgetExhausted:
      return .done(sweepCheckpoint())
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
      // Bank only past deleted sets, mirroring the drain: a checkpoint
      // implies every earlier page is gone.
      token = pageToken
      if timeUp() {
        return .done(sweepCheckpoint())
      }
      return .proceed(token: pageToken)
    }
  }

  private mutating func deleteSweepPage(names: [String]) -> BundleSweepNative {
    if names.isEmpty {
      return .swept
    }
    if timeUp() {
      return sweepCheckpoint()
    }
    switch backend.fetchRecord(name: CloudNames.anchorName, timeout: callTimeout()) {
    case .missing:
      break
    case .found:
      return .anchorPresent
    case .zoneMissing:
      return .swept
    case .failed(let fault):
      return sweepDeleteFault(fault)
    }
    if let fault = delete(names: names) {
      return sweepDeleteFault(fault)
    }
    return .swept
  }

  /// Sweep mirror of DeletePass.deleteFault: a failure that arrives after
  /// the work budget is gone becomes the last confirmed checkpoint.
  private func sweepDeleteFault(_ fault: BackendFault) -> BundleSweepNative {
    if timeUp() {
      return sweepCheckpoint()
    }
    return mapSweepFault(fault)
  }

  private func sweepCheckpoint() -> BundleSweepNative {
    guard let cursor = token else {
      return .retryable
    }
    return .incomplete(cursor: cursor)
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

private enum SweepContinuation {
  case proceed(token: Data)
  case done(BundleSweepNative)
}
