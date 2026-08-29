import Foundation
import Testing

@testable import PosatoMacOSHelper

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
