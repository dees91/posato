import Foundation
import SystemConfiguration
import Testing

@testable import PosatoMacOSServiceCore

// A failed configd read also returns no value; only kSCStatusNoKey proves the key is gone. A real
// IPC or deserialization failure cannot be produced in a Tart run, so the rule is checked here.
@Test func givenReadFailureWithoutValueWhenCheckedThenAbsenceIsNotConfirmed() throws {
  #expect(throws: SystemProxyConfigurationFailure.preferences) {
    try NetworkServiceAbsence.isMissing(nil, status: kSCStatusFailed)
  }
  #expect(throws: SystemProxyConfigurationFailure.preferences) {
    try NetworkServiceAbsence.isMissing(nil, status: kSCStatusOK)
  }
}

@Test func givenMissingKeyOrPresentValueWhenCheckedThenOnlyTheMissingKeyIsAbsent() throws {
  #expect(try NetworkServiceAbsence.isMissing(nil, status: kSCStatusNoKey))
  #expect(try !NetworkServiceAbsence.isMissing("value" as CFString, status: kSCStatusOK))
}
