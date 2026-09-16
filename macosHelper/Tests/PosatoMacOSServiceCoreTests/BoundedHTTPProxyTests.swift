import Darwin
import Foundation
import Network
import Testing

@testable import PosatoMacOSHelper

@Test func givenSelectedHTTPWhenProxiedThenTheFixedPageIsUncacheable() async throws {
  let proxy = BoundedHTTPProxy(selectedHosts: ["example.com"])
  let port = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }

  let denied = try await sendLoopbackRequest(
    port: port,
    request:
      "GET http://example.com/private-canary?query-canary HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n"
  )
  #expect(denied.hasPrefix("HTTP/1.1 200 OK\r\n"))
  #expect(denied.contains("Cache-Control: no-store"))
  #expect(denied.contains("Connection: close"))
  #expect(denied.contains("This site is paused"))
  #expect(!denied.contains("example.com"))
  #expect(!denied.contains("private-canary"))
  #expect(!denied.contains("query-canary"))
  #expect(!denied.lowercased().contains("<script"))
}

@Test func givenSelectedCONNECTWhenProxiedThenAHostFreeSignalIsEmitted() async throws {
  let blockedEvent = expectation(description: "host-free blocked signal")
  let proxy = BoundedHTTPProxy(
    selectedHosts: ["example.com"],
    blockedRequestHandler: {
      blockedEvent.fulfill()
    }
  )
  let port = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }

  let denied = try await sendLoopbackRequest(
    port: port,
    request: "CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n"
  )
  #expect(denied.hasPrefix("HTTP/1.1 403 Forbidden\r\n"))
  #expect(denied.contains("Cache-Control: no-store"))
  #expect(denied.contains("Connection: close"))
  #expect(!denied.contains("example.com"))
  try await wait(for: [blockedEvent], timeout: 1)
}

@Test func givenLocalBlockedRouteWhenRequestedThenThePageIsUncacheable() async throws {
  let proxy = BoundedHTTPProxy(selectedHosts: ["example.com"])
  let port = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }

  let page = try await sendLoopbackRequest(
    port: port,
    request: "GET /blocked HTTP/1.1\r\nHost: 127.0.0.1:\(port)\r\nConnection: close\r\n\r\n"
  )
  #expect(page.hasPrefix("HTTP/1.1 200 OK\r\n"))
  #expect(page.contains("Cache-Control: no-store"))
  #expect(page.contains("Connection: close"))
  #expect(page.contains("This site is paused"))
}

@Test func givenAllowedHTTPWhenProxiedThenTheControlOriginReceivesOneRequest() async throws {
  let origin = try await LocalHTTPOrigin()
  defer { origin.stop() }
  let proxy = BoundedHTTPProxy(selectedHosts: ["example.com"]) { host, port in
    if host == "example.org", port == 80 {
      return ("127.0.0.1", origin.port)
    }
    return (host, port)
  }
  let proxyPort = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }

  let response = try await sendLoopbackRequest(
    port: proxyPort,
    request:
      "GET http://example.org/allowed HTTP/1.1\r\nHost: example.org\r\nConnection: close\r\n\r\n"
  )
  #expect(response.hasPrefix("HTTP/1.1 200 OK\r\n"))
  #expect(response.hasSuffix("allowed-control"))
  #expect(origin.receivedHosts() == ["example.org"])
}

@Test func givenPipelinedSelectedHTTPRequestWhenProxiedThenItIsNotForwarded() async throws {
  let origin = try await LocalHTTPOrigin()
  defer { origin.stop() }
  let proxy = BoundedHTTPProxy(selectedHosts: ["example.com"]) { host, port in
    if host == "example.org", port == 80 {
      return ("127.0.0.1", origin.port)
    }
    return (host, port)
  }
  let proxyPort = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }

  let descriptor = try await connectedLoopbackSocket(port: proxyPort)
  defer { Darwin.close(descriptor) }
  try await send(
    "GET http://example.org/allowed HTTP/1.1\r\nHost: example.org\r\nConnection: close\r\n\r\n",
    to: descriptor
  )
  let first = try await receiveToEnd(from: descriptor)
  #expect(first.hasPrefix("HTTP/1.1 200 OK\r\n"))

  let secondDescriptor = try await connectedLoopbackSocket(port: proxyPort)
  defer { Darwin.close(secondDescriptor) }
  let pipelinedFirst =
    "GET http://example.org/first HTTP/1.1\r\nHost: example.org\r\nConnection: close\r\n\r\n"
  let pipelinedSecond =
    "GET http://example.com/second HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n"
  try await send(pipelinedFirst + pipelinedSecond, to: secondDescriptor)
  _ = try? await receiveToEnd(from: secondDescriptor)
  #expect(!origin.receivedHosts().contains("example.com"))
}

@Test func givenCONNECTToTheListenerWhenRequestedThenItIsRejectedWithoutUpstream() async throws {
  let proxy = BoundedHTTPProxy(selectedHosts: ["example.com"])
  let port = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }

  let denied = try await sendLoopbackRequest(
    port: port,
    request: "CONNECT 127.0.0.1:\(port) HTTP/1.1\r\nHost: 127.0.0.1:\(port)\r\n\r\n"
  )
  #expect(denied.isEmpty || !denied.contains("200 Connection Established"))
}

@Test func givenBlockedPageCopyWhenEndTimeIsAbsentThenHeadlineHasNoUntilClause() throws {
  #expect(BlockedPage.headline(sessionEndEpochMilliseconds: nil) == "This site is paused")
  let html = try #require(
    String(bytes: BlockedPage.html(sessionEndEpochMilliseconds: nil), encoding: .utf8)
  )
  #expect(html.contains("This site is paused"))
  #expect(!html.contains("until"))
}
