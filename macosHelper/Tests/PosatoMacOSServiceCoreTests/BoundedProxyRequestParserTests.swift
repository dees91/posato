import Foundation
import Testing

@testable import PosatoMacOSHelper

private let selected: Set<String> = ["example.com"]
private let listenerPort: UInt16 = 17_769

@Test func givenSelectedHostWhenRoutedThenHTTPAndCONNECTAreBlockedBeforePortCheck() {
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n".utf8),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == .blockedConnect
  )
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data("CONNECT example.com:444 HTTP/1.1\r\nHost: example.com:444\r\n\r\n".utf8),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == .blockedConnect
  )
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data("GET http://EXAMPLE.COM/path HTTP/1.1\r\nHost: EXAMPLE.COM\r\n\r\n".utf8),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == .blockedHTTP
  )
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "GET http://example.com:8080/ HTTP/1.1\r\nHost: example.com:8080\r\n\r\n".utf8),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == .blockedHTTP
  )
}

@Test func givenUnselectedSiblingOrSubdomainWhenRoutedThenCONNECTTunnelsOn443() {
  guard
    case .tunnel(let subdomain, let subdomainPort, _) = BoundedProxyRequestParser.route(
      requestData: Data(
        "CONNECT www.example.com:443 HTTP/1.1\r\nHost: www.example.com:443\r\n\r\n".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    )
  else {
    Issue.record("A subdomain must not match the exact-host rule")
    return
  }
  #expect(subdomain == "www.example.com")
  #expect(subdomainPort == 443)

  guard
    case .tunnel(let suffixHost, _, _) = BoundedProxyRequestParser.route(
      requestData: Data(
        "CONNECT example.com.invalid:443 HTTP/1.1\r\nHost: example.com.invalid:443\r\n\r\n".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    )
  else {
    Issue.record("A suffix host must not match the exact-host rule")
    return
  }
  #expect(suffixHost == "example.com.invalid")
}

@Test func givenUnselectedNonstandardPortWhenRoutedThenItIsRejected() {
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "CONNECT www.example.com:444 HTTP/1.1\r\nHost: www.example.com:444\r\n\r\n".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "GET http://example.org:8080/ HTTP/1.1\r\nHost: example.org:8080\r\n\r\n".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
}

@Test func givenMalformedAuthorityWhenRoutedThenTheConnectionFailsClosed() {
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "CONNECT ｅxample.com:443 HTTP/1.1\r\nHost: ｅxample.com:443\r\n\r\n".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "GET http://user@example.org/private HTTP/1.1\r\nHost: example.org\r\n\r\n".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data("CONNECT example.org:0 HTTP/1.1\r\nHost: example.org:0\r\n\r\n".utf8),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "GET https://example.org/invalid-proxy-form HTTP/1.1\r\nHost: example.org\r\n\r\n".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
}

@Test func givenDuplicateOrMissingAuthorityWhenRoutedThenTheConnectionFailsClosed() {
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "GET http://example.org/ HTTP/1.1\r\nHost: example.org\r\nHost: example.com\r\n\r\n".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data("GET http://example.org/ HTTP/1.1\r\n\r\n".utf8),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "CONNECT example.com..:443 HTTP/1.1\r\nHost: example.com..:443\r\n\r\n".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "GET http://example.org:70000/ HTTP/1.1\r\nHost: example.org\r\n\r\n".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
}

@Test func givenFixedLocalBlockedPageWhenRoutedThenOnlyTheExactRouteIsServed() {
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "GET /blocked HTTP/1.1\r\nHost: 127.0.0.1:17769\r\nConnection: close\r\n\r\n".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == .localBlockedPage
  )
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "GET /other HTTP/1.1\r\nHost: 127.0.0.1:17769\r\nConnection: close\r\n\r\n".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "GET /blocked?x=1 HTTP/1.1\r\nHost: 127.0.0.1:17769\r\n\r\n".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
}

@Test func givenNonExactBlockedPageVariantsWhenRoutedThenTheyAreRejected() {
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "POST /blocked HTTP/1.1\r\nHost: 127.0.0.1:17769\r\nContent-Length: 1\r\n\r\nx".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data("CONNECT 127.0.0.1:17769 HTTP/1.1\r\nHost: 127.0.0.1:17769\r\n\r\n".utf8),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "GET http://127.0.0.1:17769/ HTTP/1.1\r\nHost: 127.0.0.1:17769\r\n\r\n".utf8),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
}

@Test func givenOversizedOrConflictingFramesWhenRoutedThenTheyFailClosed() {
  let pipelinedFirst = "GET http://example.org/first HTTP/1.1\r\nHost: example.org\r\n\r\n"
  let pipelinedSecond = "GET http://example.com/second HTTP/1.1\r\nHost: example.com\r\n\r\n"
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data((pipelinedFirst + pipelinedSecond).utf8),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "GET http://127.0.0.1:8080/ HTTP/1.1\r\nHost: example.com\r\n\r\n".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == nil
  )
}

@Test func givenAllowedHTTPControlWhenRoutedThenTheForwardedRequestClosesTheConnection() {
  guard
    case .forward(let host, let port, let initialData) = BoundedProxyRequestParser.route(
      requestData: Data(
        "GET http://example.org/control HTTP/1.1\r\nHost: example.org\r\nConnection: keep-alive\r\n\r\n"
          .utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    )
  else {
    Issue.record("An unselected HTTP host on port 80 must be forwarded")
    return
  }
  guard let forwarded = String(bytes: initialData, encoding: .utf8) else {
    Issue.record("Forwarded bytes must be valid UTF-8")
    return
  }
  #expect(host == "example.org")
  #expect(port == 80)
  #expect(forwarded.contains("Connection: close"))
  #expect(!forwarded.lowercased().contains("keep-alive"))
}

@Test func givenOneTerminalDotWhenRoutedThenTheSelectedHostIsStillBlocked() {
  #expect(
    BoundedProxyRequestParser.route(
      requestData: Data(
        "CONNECT example.com.:443 HTTP/1.1\r\nHost: example.com.:443\r\n\r\n".utf8
      ),
      selectedHosts: selected,
      listenerPort: listenerPort
    ) == .blockedConnect
  )
}
