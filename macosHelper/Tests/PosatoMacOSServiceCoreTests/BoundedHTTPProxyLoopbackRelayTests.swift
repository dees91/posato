import Darwin
import Foundation
import Testing

@testable import PosatoMacOSHelper

// MACOS-024 (ADR 0005 amendment, D7): loopback relays drop the idle timeout only once established and are capped at
// 32 pairs so stuck local streams cannot starve browser traffic. A VM run cannot hold 32 streams or a silent upstream
// reliably.

@Test func givenThirtyTwoOpenLoopbackTunnelsWhenAnotherOpensThenItIsRefusedWhileBrowserTrafficStillFlows()
  async throws
{
  let origin = try await LocalHTTPOrigin()
  defer { origin.stop() }
  let proxy = BoundedHTTPProxy(selectedHosts: ["example.com"]) { host, port in
    host == "example.org" ? ("127.0.0.1", origin.port) : (host, port)
  }
  let proxyPort = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }
  let connect = "CONNECT 127.0.0.1:\(origin.port) HTTP/1.1\r\nHost: 127.0.0.1:\(origin.port)\r\n\r\n"

  var tunnels: [Int32] = []
  defer { tunnels.forEach { Darwin.close($0) } }
  for _ in 0..<BoundedHTTPProxy.maximumLoopbackRelays {
    let descriptor = try await connectedLoopbackSocket(port: proxyPort)
    tunnels.append(descriptor)
    try await send(connect, to: descriptor)
    #expect(try await receiveLine(from: descriptor) == "HTTP/1.1 200 Connection Established\r\n")
  }
  let refused = try await connectedLoopbackSocket(port: proxyPort)
  defer { Darwin.close(refused) }
  try await send(connect, to: refused)

  #expect(try await receiveToEnd(from: refused).isEmpty)
  let browser = try await connectedLoopbackSocket(port: proxyPort)
  defer { Darwin.close(browser) }
  try await send("CONNECT example.org:443 HTTP/1.1\r\nHost: example.org:443\r\n\r\n", to: browser)
  #expect(try await receiveLine(from: browser) == "HTTP/1.1 200 Connection Established\r\n")
}

@Test func givenLoopbackRelayWithoutAFirstResponseByteWhenIdleThenItStillCloses() async throws {
  let silent = try SilentLoopbackListener()
  defer { silent.stop() }
  let proxy = BoundedHTTPProxy(selectedHosts: ["example.com"], idleTimeout: 0.5)
  let proxyPort = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }

  let descriptor = try await connectedLoopbackSocket(port: proxyPort)
  defer { Darwin.close(descriptor) }
  let started = Date()
  try await send(
    "GET http://127.0.0.1:\(silent.port)/stream HTTP/1.1\r\nHost: 127.0.0.1:\(silent.port)\r\n\r\n",
    to: descriptor
  )

  #expect(try await receiveToEnd(from: descriptor).isEmpty)
  #expect(Date().timeIntervalSince(started) < 5)
}

@Test func givenEstablishedLoopbackStreamWhenIdleLongerThanTheTimeoutThenItStaysOpen() async throws {
  let origin = try await LocalHTTPOrigin()
  defer { origin.stop() }
  let proxy = BoundedHTTPProxy(selectedHosts: ["example.com"], idleTimeout: 0.5)
  let proxyPort = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }

  let descriptor = try await connectedLoopbackSocket(port: proxyPort)
  defer { Darwin.close(descriptor) }
  try await send("CONNECT 127.0.0.1:\(origin.port) HTTP/1.1\r\nHost: 127.0.0.1:\(origin.port)\r\n\r\n", to: descriptor)
  #expect(try await receiveLine(from: descriptor) == "HTTP/1.1 200 Connection Established\r\n")
  _ = try await receiveLine(from: descriptor)
  try await runBlockingTestOperation { Thread.sleep(forTimeInterval: 1.5) }
  try await send("GET /late HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n", to: descriptor)

  #expect(try await receiveToEnd(from: descriptor).hasPrefix("HTTP/1.1 200 OK\r\n"))
}

/// Accepts TCP connections into the backlog and never reads or writes.
private final class SilentLoopbackListener: @unchecked Sendable {
  let port: UInt16
  private let descriptor: Int32

  init() throws {
    let listening = Darwin.socket(AF_INET, SOCK_STREAM, 0)
    var address = sockaddr_in()
    address.sin_family = sa_family_t(AF_INET)
    address.sin_addr.s_addr = inet_addr("127.0.0.1")
    address.sin_port = 0
    let bound = withUnsafePointer(to: &address) {
      $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
        Darwin.bind(listening, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
      }
    }
    guard bound == 0, Darwin.listen(listening, 8) == 0 else {
      Darwin.close(listening)
      throw POSIXError(.EADDRNOTAVAIL)
    }
    var length = socklen_t(MemoryLayout<sockaddr_in>.size)
    _ = withUnsafeMutablePointer(to: &address) {
      $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { Darwin.getsockname(listening, $0, &length) }
    }
    descriptor = listening
    port = UInt16(bigEndian: address.sin_port)
  }

  func stop() {
    Darwin.close(descriptor)
  }
}
