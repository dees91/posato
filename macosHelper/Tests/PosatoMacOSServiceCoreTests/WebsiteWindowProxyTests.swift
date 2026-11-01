import Darwin
import Foundation
import PosatoMacOSServiceCore
import Testing

@testable import PosatoMacOSHelper

// TARGETS-013: the listener and the adapter follow the window plan per request (ADR 0005 amendment); the instants
// and plans are those of WebsiteWindowTests.

// MARK: - The proxy follows the plan per request

private final class TestClock: @unchecked Sendable {
  private let lock = NSLock()
  private var value: Date

  init(_ value: Date) {
    self.value = value
  }

  var now: Date {
    lock.withLock { value }
  }

  func set(_ date: Date) {
    lock.withLock { value = date }
  }
}

@Test func givenARuledHostWhenItsSegmentOpensThenTheSameProxyForwardsIt() async throws {
  let origin = try await LocalHTTPOrigin()
  defer { origin.stop() }
  let clock = TestClock(at(10, 5))
  let proxy = BoundedHTTPProxy(
    selectedHosts: ["example.com", "example.org"],
    sessionEndEpochMilliseconds: millis(at(11, 0)),
    resolveUpstream: { _, _ in ("127.0.0.1", origin.port) },
    windows: policy(),
    clock: { clock.now }
  )
  let port = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }
  let request =
    "GET http://example.com/page HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n"

  let before = try await sendLoopbackRequest(port: port, request: request)
  clock.set(at(10, 30))
  let inside = try await sendLoopbackRequest(port: port, request: request)
  clock.set(at(10, 35))
  let after = try await sendLoopbackRequest(port: port, request: request)

  #expect(before.contains(PauseCopy.pageMarker))
  #expect(inside.hasSuffix("allowed-control"))
  #expect(after.contains(PauseCopy.pageMarker))
  #expect(origin.receivedHosts() == ["example.com"])
}

@Test func givenAnInvalidPlanWhenInsideItsRuleThenTheProxyStillShowsThePage() async throws {
  let origin = try await LocalHTTPOrigin()
  defer { origin.stop() }
  let proxy = BoundedHTTPProxy(
    selectedHosts: ["example.com"],
    sessionEndEpochMilliseconds: millis(at(11, 0)),
    resolveUpstream: { _, _ in ("127.0.0.1", origin.port) },
    windows: policy(
      held: ["example.com"],
      plans: [
        DomainWindowPlanWire(domain: "example.com", segments: [segment(until: at(11, 5), 30, 5)])
      ]),
    clock: { at(10, 31) }
  )
  let port = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }

  let response = try await sendLoopbackRequest(
    port: port,
    request: "GET http://example.com/ HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n"
  )

  #expect(response.contains(PauseCopy.pageMarker))
  #expect(origin.receivedHosts().isEmpty)
}

@Test func givenABlockedRuledHostWhenRequestedInCleartextThenThePageSaysWhenItOpens() async throws {
  let proxy = BoundedHTTPProxy(
    selectedHosts: ["example.com", "example.org"],
    sessionEndEpochMilliseconds: millis(at(11, 0)),
    windows: policy(),
    clock: { at(10, 5) },
    pageCalendar: { utc },
    pageLocale: Locale(identifier: "en_GB")
  )
  let port = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }

  let ruled = try await sendLoopbackRequest(
    port: port,
    request:
      "GET http://example.com/private-canary HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n"
  )
  let unruled = try await sendLoopbackRequest(
    port: port,
    request: "GET http://example.org/ HTTP/1.1\r\nHost: example.org\r\nConnection: close\r\n\r\n"
  )

  #expect(ruled.contains("This site opens at 10:30"))
  #expect(!ruled.contains("example.com"))
  #expect(!ruled.contains("private-canary"))
  #expect(unruled.contains("This site is paused"))
  #expect(!unruled.contains("opens at"))
}

@Test func givenABlockedRuledHostWhenCONNECTIsDeniedThenTheRejectionStaysGeneric() async throws {
  let proxy = BoundedHTTPProxy(
    selectedHosts: ["example.com", "example.org"],
    sessionEndEpochMilliseconds: millis(at(11, 0)),
    windows: policy(),
    clock: { at(10, 5) }
  )
  let port = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }

  let denied = try await sendLoopbackRequest(
    port: port,
    request: "CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n"
  )

  #expect(denied == String(bytes: BlockedPage.connectRejection(), encoding: .utf8))
}

@Test func givenAnOpenTunnelWhenTheWindowClosesThenTheHelperEndsIt() async throws {
  let origin = try await LocalHTTPOrigin()
  defer { origin.stop() }
  let clock = TestClock(at(10, 31))
  let proxy = BoundedHTTPProxy(
    selectedHosts: ["example.com", "example.org"],
    sessionEndEpochMilliseconds: millis(at(11, 0)),
    resolveUpstream: { _, _ in ("127.0.0.1", origin.port) },
    windows: policy(),
    clock: { clock.now }
  )
  let port = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }
  let descriptor = try await connectedLoopbackSocket(port: port)
  defer { Darwin.close(descriptor) }
  try await send(
    "CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n", to: descriptor)
  let established = try await receiveLine(from: descriptor)
  _ = try await receiveLine(from: descriptor)

  clock.set(at(10, 35))
  proxy.evaluateWindows()
  let started = Date()
  let rest = try await receiveToEnd(from: descriptor)

  #expect(established == "HTTP/1.1 200 Connection Established\r\n")
  #expect(rest.isEmpty)
  #expect(Date().timeIntervalSince(started) < 2)
}

@Test func givenTheOpeningPathWhenRequestedThenOnlyFourDigitTimesServeThePage() async throws {
  let proxy = BoundedHTTPProxy(
    selectedHosts: ["example.com"], pageLocale: Locale(identifier: "en_GB"))
  let port = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }
  func get(_ path: String) async throws -> String {
    try await sendLoopbackRequest(
      port: port,
      request: "GET \(path) HTTP/1.1\r\nHost: 127.0.0.1:\(port)\r\nConnection: close\r\n\r\n")
  }

  let page = try await get("/blocked/opens/1030")
  #expect(page.hasPrefix("HTTP/1.1 200 OK\r\n"))
  #expect(page.contains("Cache-Control: no-store"))
  #expect(page.contains("This site opens at 10:30"))
  #expect(page.contains("It is paused until then."))
  #expect(page.contains(PauseCopy.pageMarker))
  #expect(try await get("/blocked/opens/0000").contains("This site opens at 00:00"))
  #expect(try await get("/blocked/opens/2359").contains("This site opens at 23:59"))
  for rejected in [
    "/blocked/opens/2400", "/blocked/opens/1060", "/blocked/opens/103", "/blocked/opens/10300",
    "/blocked/opens/+930",
    "/blocked/opens/10:3", "/blocked/opens/1030?x=1", "/blocked/opens/1030#x", "/blocked/opens/",
    "/blocked/other",
  ] {
    #expect(try await get(rejected).isEmpty)
  }
}

// MARK: - The adapter writes the fixed opening path

@Test func givenARuledBlockedTabWhenPresentedThenTheOpeningPathIsWritten() {
  let runner = StubAppleEventRunner(readResult: "1\t2\thttps://example.com/secret")
  var adapter = BrowserPresentationAdapter(
    runner: runner, now: { 10 }, frontmostBrowser: { .safari })
  runner.writeResult = "http://127.0.0.1:4443/blocked/opens/1030"

  let status = adapter.presentBlockedPage(
    port: 4_443,
    selectedHosts: ["example.com"],
    opensAt: { host in host == "example.com" ? (hour: 10, minute: 30) : nil }
  )

  #expect(status == .presented)
  #expect(runner.wroteURL?.contains("to \"http://127.0.0.1:4443/blocked/opens/1030\"") == true)
}

@Test func givenTheBrowserLandsElsewhereWhenTheOpeningPathWasWrittenThenItIsNotPresented() {
  let runner = StubAppleEventRunner(readResult: "1\t2\thttps://example.com/")
  var adapter = BrowserPresentationAdapter(
    runner: runner, now: { 10 }, frontmostBrowser: { .safari })
  runner.writeResult = "http://127.0.0.1:4443/blocked"

  let status = adapter.presentBlockedPage(
    port: 4_443,
    selectedHosts: ["example.com"],
    opensAt: { _ in (hour: 10, minute: 30) }
  )

  #expect(status == .skipped)
}

@Test func givenAnOpenWebsiteWhenABlockedResourceIsDeniedThenItsTabIsLeftAlone() {
  let windows = policy(plans: [
    DomainWindowPlanWire(domain: "example.com", segments: [segment(until: at(11, 0), 30, 5)])
  ])
  let runner = StubAppleEventRunner(readResult: "1\t2\thttps://example.com/")
  var adapter = BrowserPresentationAdapter(
    runner: runner, now: { 10 }, frontmostBrowser: { .safari })

  let status = adapter.presentBlockedPage(
    port: 4_443, selectedHosts: windows.blockedNow(at: at(10, 31)))

  #expect(status == .skipped)
  #expect(runner.writeSource == nil)
}
