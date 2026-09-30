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

// MACOS-024: the pre-Apply check must see the exceptions Apply will add, so that a selected domain one of them would
// cover fails before any system change instead of only after it.
@Test func givenLiveExceptionsWhenOverlaidThenMissingLoopbackEntriesAreAppendedOnce() {
  let live = ["ExceptionsList": ["*.local", "127.0.0.1"]] as CFDictionary

  let overlay = ProxyChainValidator().overlayLoopbackSettings(live, port: 17_769) as NSDictionary

  #expect(overlay["ExceptionsList"] as? [String] == ["*.local", "127.0.0.1", "localhost", "::1"])
}
