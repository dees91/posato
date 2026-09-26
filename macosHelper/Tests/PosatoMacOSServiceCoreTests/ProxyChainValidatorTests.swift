import Foundation
import Testing

@testable import PosatoMacOSHelper

@Test func givenExactLoopbackHopsWhenValidatedThenCandidateChainPasses() {
  let resolver = StubResolver(hops: [.loopback(port: 4_443)])
  let validator = ProxyChainValidator(resolver: resolver)

  #expect(
    validator.containsOnlyLoopback(
      selectedHosts: ["example.com"],
      port: 4_443,
      settings: nil
    )
  )
}

@Test func givenDirectOrExtraHopWhenValidatedThenActivationIsRefused() {
  let direct = ProxyChainValidator(resolver: StubResolver(hops: [.direct]))
  let extra = ProxyChainValidator(
    resolver: StubResolver(hops: [.loopback(port: 4_443), .direct])
  )

  #expect(
    !direct.containsOnlyLoopback(selectedHosts: ["example.com"], port: 4_443, settings: nil)
  )
  #expect(
    !extra.containsOnlyLoopback(selectedHosts: ["example.com"], port: 4_443, settings: nil)
  )
}

private final class StubResolver: ProxyChainResolving {
  private let hopsToReturn: [ProxyChainHop]

  init(hops: [ProxyChainHop]) {
    hopsToReturn = hops
  }

  func hops(url: URL, settings: CFDictionary?) -> [ProxyChainHop] {
    return hopsToReturn
  }
}
