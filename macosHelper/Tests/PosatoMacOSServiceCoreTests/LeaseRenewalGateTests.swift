import Foundation
import Testing

@testable import PosatoMacOSHelper
@testable import PosatoMacOSServiceCore
@testable import PosatoProxySettingsDaemon

@Test func givenMaximumRequestsWhenSequenceConsumedThenRotationIsRequired() throws {
  var sequence = DaemonRequestSequence()

  for expected in 1...WireLimits.maximumOperationsPerConnection {
    #expect(sequence.hasCapacity)
    #expect(try sequence.take() == expected)
  }

  #expect(!sequence.hasCapacity)
  #expect(throws: PipeFailure.self) {
    try sequence.take()
  }
}

@Test func givenRenewalConnectionWhenRenewSucceedsThenItDoesNotOwnCleanup() throws {
  let state = ConnectionState()
  let request = try WireMessage(
    kind: .request,
    operation: .renew,
    sequence: 1,
    deadlineMilliseconds: 5_000,
    connectionIdentifier: Data(repeating: 1, count: WireLimits.identifierBytes),
    sessionIdentifier: Data(repeating: 2, count: WireLimits.identifierBytes),
    requestIdentifier: Data(repeating: 3, count: WireLimits.identifierBytes),
    payload: Data()
  )
  let response = WireResponsePayload(
    outcome: .success,
    serviceState: .ready,
    ownershipPhase: .applied
  )

  state.update(request: request, response: response, ownershipVerified: true)

  #expect(!state.shouldRestoreOnInvalidation())
}

@Test func givenInFlightRenewalWhenRetiredThenRetirementWaitsAndLaterWorkIsSkipped() {
  let gate = LeaseRenewalGate()
  let renewalStarted = DispatchSemaphore(value: 0)
  let finishRenewal = DispatchSemaphore(value: 0)
  let retirementStarted = DispatchSemaphore(value: 0)
  let retirementFinished = DispatchSemaphore(value: 0)

  DispatchQueue.global().async {
    gate.run {
      renewalStarted.signal()
      finishRenewal.wait()
    }
  }
  #expect(renewalStarted.wait(timeout: .now() + .seconds(1)) == .success)

  DispatchQueue.global().async {
    retirementStarted.signal()
    gate.retire()
    retirementFinished.signal()
  }
  #expect(retirementStarted.wait(timeout: .now() + .seconds(1)) == .success)
  #expect(retirementFinished.wait(timeout: .now() + .milliseconds(100)) == .timedOut)

  finishRenewal.signal()
  #expect(retirementFinished.wait(timeout: .now() + .seconds(1)) == .success)

  var laterWorkRan = false
  gate.run {
    laterWorkRan = true
  }
  #expect(!laterWorkRan)
}
