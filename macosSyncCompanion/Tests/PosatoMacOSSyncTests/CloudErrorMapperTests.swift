import CloudKit
import Foundation
import Testing

@testable import PosatoMacOSSync

private func ckError(_ code: CKError.Code, partials: [CKRecord.ID: NSError] = [:]) -> NSError {
  var userInfo: [String: Any] = [:]
  if !partials.isEmpty {
    userInfo[CKPartialErrorsByItemIDKey] = partials
  }
  return NSError(domain: CKError.errorDomain, code: code.rawValue, userInfo: userInfo)
}

private func zoneID() -> CKRecordZone.ID {
  return RecordCodec.zoneID()
}

@Test func givenZoneNotFoundWhenMappingZoneLookupThenItIsMissing() {
  #expect(
    CloudErrorMapper.zoneLookup(from: ckError(.zoneNotFound)) == .missing
  )
  #expect(
    CloudErrorMapper.zoneLookup(from: ckError(.userDeletedZone)) == .missing
  )
}

@Test func givenNetworkFailuresWhenMappingThenTheyAreRetryable() {
  for code: CKError.Code in [
    .networkFailure, .networkUnavailable, .serviceUnavailable, .requestRateLimited,
    .quotaExceeded, .zoneBusy,
  ] {
    #expect(CloudErrorMapper.isRetryable(ckError(code)), "expected retryable for \(code)")
    #expect(
      CloudErrorMapper.zoneLookup(from: ckError(code)) == .failed(.retryable),
      "expected retryable lookup for \(code)"
    )
  }
}

@Test func givenPermissionFailureWhenMappingThenItIsUnknown() {
  #expect(!CloudErrorMapper.isRetryable(ckError(.permissionFailure)))
  #expect(
    CloudErrorMapper.zoneLookup(from: ckError(.permissionFailure)) == .failed(.unknown)
  )
}

@Test func givenNonCloudKitErrorWhenMappingThenItIsUnknown() {
  let error = NSError(domain: NSPOSIXErrorDomain, code: 1, userInfo: [:])

  #expect(!CloudErrorMapper.isRetryable(error))
  #expect(!CloudErrorMapper.isServerRecordChanged(error))
  #expect(!CloudErrorMapper.isZoneAbsent(error))
}

@Test func givenUnknownItemWhenMappingRecordLookupThenItIsMissing() {
  #expect(
    CloudErrorMapper.recordLookup(from: ckError(.unknownItem)) == .missing
  )
}

@Test func givenZoneNotFoundWhenMappingRecordLookupThenZoneIsMissing() {
  #expect(
    CloudErrorMapper.recordLookup(from: ckError(.zoneNotFound)) == .zoneMissing
  )
}

@Test func givenSinglePartialUnknownItemWhenMappingRecordLookupThenItIsMissing() {
  let recordID = CKRecord.ID(recordName: "workspace", zoneID: zoneID())
  let error = ckError(.partialFailure, partials: [recordID: ckError(.unknownItem)])

  #expect(CloudErrorMapper.recordLookup(from: error) == .missing)
}

@Test func givenServerRecordChangedWhenCheckingConflictThenItIsDetected() {
  #expect(CloudErrorMapper.isServerRecordChanged(ckError(.serverRecordChanged)))
}

@Test func givenSinglePartialConflictWhenCheckingConflictThenItIsDetected() {
  let recordID = CKRecord.ID(recordName: "workspace", zoneID: zoneID())
  let error = ckError(
    .partialFailure,
    partials: [recordID: ckError(.serverRecordChanged)]
  )

  #expect(CloudErrorMapper.isServerRecordChanged(error))
}

@Test func givenMultiPartialConflictWhenCheckingConflictThenItIsNotDetected() {
  let first = CKRecord.ID(recordName: "one", zoneID: zoneID())
  let second = CKRecord.ID(recordName: "two", zoneID: zoneID())
  let error = ckError(
    .partialFailure,
    partials: [first: ckError(.serverRecordChanged), second: ckError(.networkFailure)]
  )

  #expect(!CloudErrorMapper.isServerRecordChanged(error))
}
