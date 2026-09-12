import CloudKit
import Foundation

enum CloudErrorMapper {
  static func isTokenExpired(_ error: NSError) -> Bool {
    let unwrapped = unwrapSinglePartial(error)
    return unwrapped.domain == CKError.errorDomain
      && unwrapped.code == CKError.changeTokenExpired.rawValue
  }

  static func isRetryable(_ error: NSError) -> Bool {
    let unwrapped = unwrapSinglePartial(error)
    guard unwrapped.domain == CKError.errorDomain,
      let code = CKError.Code(rawValue: unwrapped.code)
    else {
      return false
    }
    switch code {
    case .networkFailure,
      .networkUnavailable,
      .serviceUnavailable,
      .requestRateLimited,
      .quotaExceeded,
      .zoneBusy:
      return true
    default:
      return false
    }
  }

  static func isServerRecordChanged(_ error: NSError) -> Bool {
    return unwrapSinglePartial(error).domain == CKError.errorDomain
      && unwrapSinglePartial(error).code == CKError.serverRecordChanged.rawValue
  }

  static func isZoneAbsent(_ error: NSError) -> Bool {
    let unwrapped = unwrapSinglePartial(error)
    guard unwrapped.domain == CKError.errorDomain,
      let code = CKError.Code(rawValue: unwrapped.code)
    else {
      return false
    }
    switch code {
    case .zoneNotFound, .userDeletedZone:
      return true
    default:
      return false
    }
  }

  static func zoneLookup(from error: NSError) -> ZoneLookup {
    if isZoneAbsent(error) {
      return .missing
    }
    return isRetryable(error) ? .failed(.retryable) : .failed(.unknown)
  }

  static func recordLookup(from error: NSError) -> BackendLookup {
    let unwrapped = unwrapSinglePartial(error)
    if isZoneAbsent(unwrapped) {
      return .zoneMissing
    }
    if isUnknownItem(unwrapped) {
      return .missing
    }
    return isRetryable(unwrapped) ? .failed(.retryable) : .failed(.unknown)
  }

  static func isUnknownItem(_ error: NSError) -> Bool {
    return error.domain == CKError.errorDomain
      && CKError.Code(rawValue: error.code) == .unknownItem
  }

  static func recordDelete(from error: NSError) -> BackendDelete {
    if isZoneAbsent(error) {
      return .deleted
    }
    var sawRetryable = false
    for item in partialErrors(error) {
      if isUnknownItem(item) || isZoneAbsent(item) {
        continue
      }
      if isRetryable(item) {
        sawRetryable = true
      } else {
        return .failed(.unknown)
      }
    }
    return sawRetryable ? .failed(.retryable) : .deleted
  }

  private static func partialErrors(_ error: NSError) -> [NSError] {
    guard error.domain == CKError.errorDomain,
      error.code == CKError.partialFailure.rawValue,
      let partial = error.userInfo[CKPartialErrorsByItemIDKey] as? [AnyHashable: Any]
    else {
      return [error]
    }
    let items = partial.values.compactMap { $0 as? NSError }
    return items.isEmpty ? [error] : items
  }

  private static func unwrapSinglePartial(_ error: NSError) -> NSError {
    guard error.domain == CKError.errorDomain,
      error.code == CKError.partialFailure.rawValue,
      let partial = error.userInfo[CKPartialErrorsByItemIDKey] as? [AnyHashable: Any],
      partial.count == 1,
      let inner = partial.values.first as? NSError
    else {
      return error
    }
    return inner
  }
}
