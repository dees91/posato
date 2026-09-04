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

@Test func givenListenerFailureWhenResolvingThenChainNeverFallsBackDirect() throws {
  let proxy = BoundedHTTPProxy(selectedHosts: ["example.com"])
  let port = try proxy.start()
  proxy.stop()

  let resolver = StubResolver(hops: [.loopback(port: port)])
  let validator = ProxyChainValidator(resolver: resolver)
  #expect(
    validator.containsOnlyLoopback(
      selectedHosts: ["example.com"],
      port: port,
      settings: nil
    )
  )
  #expect(resolver.hopsCalled)
  #expect(!resolver.returnedDirect)
}

private final class StubResolver: ProxyChainResolving {
  private let hopsToReturn: [ProxyChainHop]
  private(set) var hopsCalled = false
  private(set) var returnedDirect = false

  init(hops: [ProxyChainHop]) {
    hopsToReturn = hops
  }

  func hops(url: URL, settings: CFDictionary?) -> [ProxyChainHop] {
    hopsCalled = true
    returnedDirect = hopsToReturn.contains(.direct)
    return hopsToReturn
  }
}
