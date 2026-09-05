import Darwin
import Foundation
import Testing

@testable import PosatoMacOSHelper

@Test func givenAllowedCONNECTWhenProxiedThenTheTunnelRelaysBytesToTheUpstream() throws {
  let origin = try LocalHTTPOrigin()
  defer { origin.stop() }
  let proxy = BoundedHTTPProxy(selectedHosts: ["example.com"]) { host, port in
    if host == "example.org", port == 443 {
      return ("127.0.0.1", origin.port)
    }
    return (host, port)
  }
  let proxyPort = try proxy.start()
  defer { proxy.stop() }

  let descriptor = try connectedLoopbackSocket(port: proxyPort)
  defer { Darwin.close(descriptor) }
  try send(
    "CONNECT example.org:443 HTTP/1.1\r\nHost: example.org:443\r\n\r\n",
    to: descriptor
  )
  let established = try receiveLine(from: descriptor)
  #expect(established == "HTTP/1.1 200 Connection Established\r\n")
  _ = try receiveLine(from: descriptor)
  try send(
    "GET /tunnelled HTTP/1.1\r\nHost: example.org\r\nConnection: close\r\n\r\n",
    to: descriptor
  )
  let relayed = try receiveToEnd(from: descriptor)
  #expect(relayed.hasPrefix("HTTP/1.1 200 OK\r\n"))
  #expect(relayed.hasSuffix("allowed-control"))
  #expect(origin.receivedHosts() == ["example.org"])
}

@Test func givenIncompleteHeaderWhenTheDeadlinePassesThenItClosesWithoutUpstream() throws {
  let origin = try LocalHTTPOrigin()
  defer { origin.stop() }
  let proxy = BoundedHTTPProxy(
    selectedHosts: ["example.com"],
    resolveUpstream: { _, _ in ("127.0.0.1", origin.port) },
    headerTimeout: 0.3
  )
  let proxyPort = try proxy.start()
  defer { proxy.stop() }

  let descriptor = try connectedLoopbackSocket(port: proxyPort)
  defer { Darwin.close(descriptor) }
  let started = Date()
  try send("GET http://example.org/slow HTTP/1.1\r\nHost: exam", to: descriptor)
  let response = try receiveToEnd(from: descriptor)
  #expect(response.isEmpty)
  #expect(Date().timeIntervalSince(started) < 2.5)
  #expect(origin.receivedHosts().isEmpty)
}
