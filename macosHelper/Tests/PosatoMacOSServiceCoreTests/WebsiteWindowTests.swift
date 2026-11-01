import Darwin
import Foundation
import PosatoMacOSServiceCore
import Testing

@testable import PosatoMacOSHelper

// TARGETS-013: the helper follows each held website's window plan with its own clock (ADR 0004 and ADR 0005
// amendments). These tests fix the instants that the browser probes cannot sample: the helper must never open a
// website before its boundary, must move between segments at a part's end with no new request, and must keep a
// website blocked when its plan is invalid.

/// 2026-11-01 in UTC; the helper reads the wall clock in the calendar it is given.
private let day = Date(timeIntervalSince1970: 1_793_491_200)

private func at(_ hour: Int, _ minute: Int, _ second: Double = 0) -> Date {
  day.addingTimeInterval(TimeInterval(hour * 3600 + minute * 60) + second)
}

private func millis(_ date: Date) -> UInt64 {
  UInt64((date.timeIntervalSince1970 * 1000).rounded())
}

private var utc: Calendar {
  var calendar = Calendar(identifier: .gregorian)
  calendar.timeZone = TimeZone(identifier: "UTC")!
  return calendar
}

private func segment(until: Date, _ period: UInt8, _ length: UInt8) -> WindowSegmentWire {
  WindowSegmentWire(untilEpochMilliseconds: millis(until), period: period, length: length)
}

/// An unruled part ends at 10:10 and a ruled 30/5 part at 11:00: blocked, then the rule.
private let partEndPlan = DomainWindowPlanWire(
  domain: "example.com",
  segments: [segment(until: at(10, 10), 0, 0), segment(until: at(11, 0), 30, 5)]
)

private func policy(
  held: Set<String> = ["example.com", "example.org"],
  plans: [DomainWindowPlanWire] = [partEndPlan],
  end: Date = at(11, 0)
) -> HostWindowPolicy {
  HostWindowPolicy(held: held, plans: plans, endEpochMilliseconds: millis(end), calendar: { utc })
}

@Test func givenAPartEndInsideTheRequestWhenTheClockPassesItThenTheRuleAppliesWithoutANewRequest() {
  let windows = policy()

  #expect(windows.blockedNow(at: at(10, 5)) == ["example.com", "example.org"])
  #expect(windows.blockedNow(at: at(10, 29, 59.999)) == ["example.com", "example.org"])
  #expect(windows.blockedNow(at: at(10, 30)) == ["example.org"])
  #expect(windows.blockedNow(at: at(10, 34, 59.999)) == ["example.org"])
  #expect(windows.blockedNow(at: at(10, 35)) == ["example.com", "example.org"])
  #expect(windows.nextBoundary(after: at(10, 5)) == at(10, 10))
  #expect(windows.nextBoundary(after: at(10, 10)) == at(10, 30))
  #expect(windows.nextOpening(host: "example.com", after: at(10, 5)) == at(10, 30))
  #expect(windows.nextOpening(host: "www.example.com", after: at(10, 5)) == at(10, 30))
  #expect(windows.nextOpening(host: "example.org", after: at(10, 5)) == nil)
}

@Test func givenAnOpeningWhenEvaluatedAMillisecondEarlyThenTheWebsiteStaysBlocked() {
  let windows = policy(plans: [
    DomainWindowPlanWire(domain: "example.com", segments: [segment(until: at(11, 0), 30, 5)])
  ])

  #expect(windows.blockedNow(at: at(10, 29)).contains("example.com"))
  #expect(windows.blockedNow(at: at(10, 29, 59)).contains("example.com"))
  #expect(windows.blockedNow(at: at(10, 29, 59.999)).contains("example.com"))
  #expect(!windows.blockedNow(at: at(10, 30)).contains("example.com"))
}

@Test func givenTheLastSegmentEndsBeforeTheRequestWhenItPassesThenTheWebsiteIsBlockedUntilTheEnd() {
  let windows = policy(plans: [
    DomainWindowPlanWire(domain: "example.com", segments: [segment(until: at(10, 32), 30, 5)])
  ])

  #expect(!windows.blockedNow(at: at(10, 31)).contains("example.com"))
  #expect(windows.blockedNow(at: at(10, 32)).contains("example.com"))
  #expect(windows.blockedNow(at: at(11, 0, -1)).contains("example.com"))
  #expect(windows.nextOpening(host: "example.com", after: at(10, 32)) == nil)
}

@Test func givenAnInvalidPlanWhenInsideItsRuleThenTheWebsiteStaysBlocked() {
  let open = at(10, 31)
  let ruled = segment(until: at(11, 0), 30, 5)
  let invalid: [[DomainWindowPlanWire]] = [
    // Segments not strictly increasing.
    [DomainWindowPlanWire(domain: "example.com", segments: [segment(until: at(10, 50), 30, 5), segment(until: at(10, 50), 30, 5)])],
    // The last segment ends after the request.
    [DomainWindowPlanWire(domain: "example.com", segments: [segment(until: at(11, 1), 30, 5)])],
    // A rule outside the fixed pairs.
    [DomainWindowPlanWire(domain: "example.com", segments: [segment(until: at(11, 0), 45, 5)])],
    [DomainWindowPlanWire(domain: "example.com", segments: [segment(until: at(11, 0), 30, 7)])],
    [DomainWindowPlanWire(domain: "example.com", segments: [segment(until: at(11, 0), 30, 0)])],
    // A domain the request does not hold.
    [DomainWindowPlanWire(domain: "example.net", segments: [ruled])],
    // Two plans for one domain.
    [
      DomainWindowPlanWire(domain: "example.com", segments: [ruled]),
      DomainWindowPlanWire(domain: "example.com", segments: [ruled]),
    ],
    // More segments than the cap.
    [
      DomainWindowPlanWire(
        domain: "example.com",
        segments: (1...23).map { index in segment(until: at(10, 0).addingTimeInterval(TimeInterval(index * 60)), 30, 5) }
      )
    ],
    // No segment.
    [DomainWindowPlanWire(domain: "example.com", segments: [])],
  ]
  for plans in invalid {
    #expect(policy(plans: plans).blockedNow(at: open) == ["example.com", "example.org"])
  }
  #expect(
    HostWindowPolicy(held: ["example.com"], plans: [DomainWindowPlanWire(domain: "example.com", segments: [ruled])], endEpochMilliseconds: nil, calendar: { utc })
      .blockedNow(at: open) == ["example.com"]
  )
  #expect(policy(plans: [DomainWindowPlanWire(domain: "example.com", segments: [ruled])]).blockedNow(at: open) == ["example.org"])
}

@Test func givenAWebsiteAndItsWwwFormWhenOnlyOneIsRuledThenBothStayBlocked() {
  let windows = policy(
    held: ["example.com", "www.example.com"],
    plans: [DomainWindowPlanWire(domain: "example.com", segments: [segment(until: at(11, 0), 30, 5)])]
  )

  let blocked = windows.blockedNow(at: at(10, 31))
  #expect(ExactHostPolicy.matches(host: "example.com", selectedHosts: blocked))
  #expect(ExactHostPolicy.matches(host: "www.example.com", selectedHosts: blocked))
  #expect(windows.nextOpening(host: "example.com", after: at(10, 5)) == nil)
}

@Test func givenAPlanWhenEncodedThenTheHelperDecodesTheSameWindows() throws {
  let payload = try BrowserDomainConfigurePayload(
    domains: ["example.com", "example.org"],
    sessionEndEpochMilliseconds: 1_793_530_800_000,
    windows: [
      DomainWindowPlanWire(
        domain: "example.com",
        segments: [
          WindowSegmentWire(untilEpochMilliseconds: 1_793_527_800_000, period: 0, length: 0),
          WindowSegmentWire(untilEpochMilliseconds: 1_793_530_800_000, period: 30, length: 5),
        ]
      )
    ]
  )
  let encoded = try payload.encode()

  // The same bytes the desktop app's encoder writes (BrowserDomainConfigureProtocolTest).
  #expect(encoded.map { String(format: "%02x", $0) }.joined() == windowPayloadVector)
  #expect(try BrowserDomainConfigurePayload.decode(encoded) == payload)
}

@Test func givenAPayloadWithoutWindowsWhenDecodedThenEveryHeldWebsiteHasNoPlan() throws {
  let legacy = try BrowserDomainConfigurePayload(domains: ["example.com"], sessionEndEpochMilliseconds: 1_000).encode()

  let decoded = try BrowserDomainConfigurePayload.decode(legacy)

  #expect(decoded.windows.isEmpty)
  #expect(decoded.domains == ["example.com"])
}

@Test func givenATruncatedWindowSectionWhenDecodedThenTheFrameIsRejected() throws {
  let encoded = try BrowserDomainConfigurePayload(
    domains: ["example.com"],
    sessionEndEpochMilliseconds: 1_793_530_800_000,
    windows: [DomainWindowPlanWire(domain: "example.com", segments: [WindowSegmentWire(untilEpochMilliseconds: 1_793_530_800_000, period: 30, length: 5)])]
  ).encode()

  #expect(throws: WireProtocolFailure.invalidFrame) {
    try BrowserDomainConfigurePayload.decode(encoded.dropLast())
  }
}

/// `example.com` and `example.org`, the end, then one plan for `example.com`: blocked until 10:10 UTC, then 30/5.
let windowPayloadVector =
  "0002" + "0b6578616d706c652e636f6d" + "0b6578616d706c652e6f7267" + "01" + "000001a196c42780"
  + "01" + "0001" + "0b6578616d706c652e636f6d" + "02" + "000001a1969660c0" + "00" + "00" + "000001a196c42780" + "1e" + "05"

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
  let request = "GET http://example.com/page HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n"

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
    windows: policy(held: ["example.com"], plans: [
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
    request: "GET http://example.com/private-canary HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n"
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

  #expect(denied == String(decoding: BlockedPage.connectRejection(), as: UTF8.self))
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
  try await send("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n", to: descriptor)
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
  let proxy = BoundedHTTPProxy(selectedHosts: ["example.com"], pageLocale: Locale(identifier: "en_GB"))
  let port = try await runBlockingTestOperation { try proxy.start() }
  defer { proxy.stop() }
  func get(_ path: String) async throws -> String {
    try await sendLoopbackRequest(port: port, request: "GET \(path) HTTP/1.1\r\nHost: 127.0.0.1:\(port)\r\nConnection: close\r\n\r\n")
  }

  let page = try await get("/blocked/opens/1030")
  #expect(page.hasPrefix("HTTP/1.1 200 OK\r\n"))
  #expect(page.contains("Cache-Control: no-store"))
  #expect(page.contains("This site opens at 10:30"))
  #expect(page.contains("It is paused until then."))
  #expect(page.contains(PauseCopy.pageMarker))
  #expect(try await get("/blocked/opens/0000").contains("This site opens at 00:00"))
  #expect(try await get("/blocked/opens/2359").contains("This site opens at 23:59"))
  for rejected in ["/blocked/opens/2400", "/blocked/opens/1060", "/blocked/opens/103", "/blocked/opens/10300", "/blocked/opens/+930",
                   "/blocked/opens/10:3", "/blocked/opens/1030?x=1", "/blocked/opens/1030#x", "/blocked/opens/", "/blocked/other"] {
    #expect(try await get(rejected).isEmpty)
  }
}

// MARK: - The adapter writes the fixed opening path

@Test func givenARuledBlockedTabWhenPresentedThenTheOpeningPathIsWritten() {
  let runner = StubAppleEventRunner(readResult: "1\t2\thttps://example.com/secret")
  var adapter = BrowserPresentationAdapter(runner: runner, now: { 10 }, frontmostBrowser: { .safari })
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
  var adapter = BrowserPresentationAdapter(runner: runner, now: { 10 }, frontmostBrowser: { .safari })
  runner.writeResult = "http://127.0.0.1:4443/blocked"

  let status = adapter.presentBlockedPage(
    port: 4_443,
    selectedHosts: ["example.com"],
    opensAt: { _ in (hour: 10, minute: 30) }
  )

  #expect(status == .skipped)
}

@Test func givenAnOpenWebsiteWhenABlockedResourceIsDeniedThenItsTabIsLeftAlone() {
  let windows = policy(plans: [DomainWindowPlanWire(domain: "example.com", segments: [segment(until: at(11, 0), 30, 5)])])
  let runner = StubAppleEventRunner(readResult: "1\t2\thttps://example.com/")
  var adapter = BrowserPresentationAdapter(runner: runner, now: { 10 }, frontmostBrowser: { .safari })

  let status = adapter.presentBlockedPage(port: 4_443, selectedHosts: windows.blockedNow(at: at(10, 31)))

  #expect(status == .skipped)
  #expect(runner.writeSource == nil)
}
