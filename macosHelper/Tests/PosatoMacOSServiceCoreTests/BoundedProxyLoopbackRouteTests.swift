import Foundation
import Testing

@testable import PosatoMacOSHelper

// MACOS-024 (ADR 0005 amendment): only `localhost`, `127.0.0.1`, and `[::1]` relay on any port other than the
// listener's own. A VM run proves the three happy paths; this matrix guards against a widened loopback match, a DNS
// lookup of `localhost`, or a rewritten `Host`.

private let listenerPort: UInt16 = 17_769

private func route(_ request: String) -> BoundedProxyRoute? {
  return BoundedProxyRequestParser.route(
    requestData: Data(request.utf8),
    selectedHosts: ["example.com"],
    listenerPort: listenerPort
  )
}

@Test func givenTheThreeLoopbackHostsOnAnyPortWhenCONNECTedThenTheyTunnelToLiteralAddresses() {
  let localhost = ["127.0.0.1", "::1"]
  #expect(
    route("CONNECT localhost:18765 HTTP/1.1\r\nHost: localhost:18765\r\n\r\n")
      == .loopbackTunnel(addresses: localhost, port: 18_765, initialData: Data())
  )
  #expect(
    route("CONNECT LOCALHOST.:18765 HTTP/1.1\r\nHost: LOCALHOST.:18765\r\n\r\n")
      == .loopbackTunnel(addresses: localhost, port: 18_765, initialData: Data())
  )
  #expect(
    route("CONNECT 127.0.0.1:443 HTTP/1.1\r\nHost: 127.0.0.1:443\r\n\r\n")
      == .loopbackTunnel(addresses: ["127.0.0.1"], port: 443, initialData: Data())
  )
  #expect(
    route("CONNECT [::1]:18765 HTTP/1.1\r\nHost: [::1]:18765\r\n\r\n")
      == .loopbackTunnel(addresses: ["::1"], port: 18_765, initialData: Data())
  )
}

@Test func givenAbsoluteFormToLocalhostWhenRoutedThenItForwardsWithTheOriginalHost() throws {
  let routed = route(
    "POST http://localhost:18765/mcp HTTP/1.1\r\nHost: localhost:18765\r\n"
      + "Content-Length: 2\r\n\r\n{}"
  )

  guard case .loopbackForward(let addresses, let port, let initialData) = routed else {
    Issue.record("expected a loopback forward, got \(String(describing: routed))")
    return
  }
  let forwarded = try #require(String(bytes: initialData, encoding: .utf8))
  #expect(addresses == ["127.0.0.1", "::1"])
  #expect(port == 18_765)
  #expect(forwarded.contains("\r\nHost: localhost:18765\r\n"))
  #expect(forwarded.hasSuffix("\r\n\r\n{}"))
}

@Test func givenOtherLoopbackFormsOrTheListenerPortWhenRoutedOffStandardPortsThenTheyAreRefused() {
  let refusedAuthorities = [
    "localhost:\(listenerPort)", "127.0.0.1:\(listenerPort)", "[::1]:\(listenerPort)",
    "0.0.0.0:18765", "127.0.0.2:18765", "[::ffff:127.0.0.1]:18765", "[0:0:0:0:0:0:0:1]:18765",
    "127.1:18765", "app.localhost:18765",
  ]
  for authority in refusedAuthorities {
    #expect(
      route("CONNECT \(authority) HTTP/1.1\r\nHost: \(authority)\r\n\r\n") == nil,
      "CONNECT \(authority)")
    #expect(
      route("GET http://\(authority)/ HTTP/1.1\r\nHost: \(authority)\r\n\r\n") == nil,
      "GET http://\(authority)/"
    )
  }
  #expect(route("GET /mcp HTTP/1.1\r\nHost: localhost:18765\r\n\r\n") == nil)
}
