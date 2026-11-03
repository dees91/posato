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
let windowTestDay = Date(timeIntervalSince1970: 1_793_491_200)

func at(_ hour: Int, _ minute: Int, _ second: Double = 0) -> Date {
  windowTestDay.addingTimeInterval(TimeInterval(hour * 3600 + minute * 60) + second)
}

func millis(_ date: Date) -> UInt64 {
  UInt64((date.timeIntervalSince1970 * 1000).rounded())
}

var utc: Calendar {
  var calendar = Calendar(identifier: .gregorian)
  calendar.timeZone = TimeZone(identifier: "UTC")!
  return calendar
}

func segment(until: Date, _ period: UInt8, _ length: UInt8) -> WindowSegmentWire {
  WindowSegmentWire(untilEpochMilliseconds: millis(until), period: period, length: length)
}

/// An unruled part ends at 10:10 and a ruled 30/5 part at 11:00: blocked, then the rule.
let partEndPlan = DomainWindowPlanWire(
  domain: "example.com",
  segments: [segment(until: at(10, 10), 0, 0), segment(until: at(11, 0), 30, 5)]
)

func policy(
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
  let repeated = segment(until: at(10, 50), 30, 5)
  let overCap = (1...23).map { index in
    segment(until: at(10, 0).addingTimeInterval(TimeInterval(index * 60)), 30, 5)
  }
  let invalid: [[DomainWindowPlanWire]] = [
    // Segments not strictly increasing.
    [DomainWindowPlanWire(domain: "example.com", segments: [repeated, repeated])],
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
    [DomainWindowPlanWire(domain: "example.com", segments: overCap)],
    // No segment.
    [DomainWindowPlanWire(domain: "example.com", segments: [])],
  ]
  for plans in invalid {
    #expect(policy(plans: plans).blockedNow(at: open) == ["example.com", "example.org"])
  }
  #expect(
    HostWindowPolicy(
      held: ["example.com"],
      plans: [DomainWindowPlanWire(domain: "example.com", segments: [ruled])],
      endEpochMilliseconds: nil, calendar: { utc }
    )
    .blockedNow(at: open) == ["example.com"]
  )
  #expect(
    policy(plans: [DomainWindowPlanWire(domain: "example.com", segments: [ruled])]).blockedNow(
      at: open) == ["example.org"])
}

@Test func givenAWebsiteAndItsWwwFormWhenOnlyOneIsRuledThenBothStayBlocked() {
  let windows = policy(
    held: ["example.com", "www.example.com"],
    plans: [
      DomainWindowPlanWire(domain: "example.com", segments: [segment(until: at(11, 0), 30, 5)])
    ]
  )

  let blocked = windows.blockedNow(at: at(10, 31))
  #expect(ExactHostPolicy.matches(host: "example.com", selectedHosts: blocked))
  #expect(ExactHostPolicy.matches(host: "www.example.com", selectedHosts: blocked))
  #expect(windows.nextOpening(host: "example.com", after: at(10, 5)) == nil)
}

@Test func givenAPlanWhenEncodedThenTheHelperDecodesTheSameWindows() throws {
  let unruled = WindowSegmentWire(untilEpochMilliseconds: 1_793_527_800_000, period: 0, length: 0)
  let ruled = WindowSegmentWire(untilEpochMilliseconds: 1_793_530_800_000, period: 30, length: 5)
  let payload = try BrowserDomainConfigurePayload(
    domains: ["example.com", "example.org"],
    sessionEndEpochMilliseconds: 1_793_530_800_000,
    windows: [DomainWindowPlanWire(domain: "example.com", segments: [unruled, ruled])]
  )
  let encoded = try payload.encode()

  // The same bytes the desktop app's encoder writes (BrowserDomainConfigureProtocolTest).
  #expect(encoded.map { String(format: "%02x", $0) }.joined() == windowPayloadVector)
  #expect(try BrowserDomainConfigurePayload.decode(encoded) == payload)
}

@Test func givenAPayloadWithoutWindowsWhenDecodedThenEveryHeldWebsiteHasNoPlan() throws {
  let legacy = try BrowserDomainConfigurePayload(
    domains: ["example.com"], sessionEndEpochMilliseconds: 1_000
  ).encode()

  let decoded = try BrowserDomainConfigurePayload.decode(legacy)

  #expect(decoded.windows.isEmpty)
  #expect(decoded.domains == ["example.com"])
}

@Test func givenATruncatedWindowSectionWhenDecodedThenTheFrameIsRejected() throws {
  let ruled = WindowSegmentWire(untilEpochMilliseconds: 1_793_530_800_000, period: 30, length: 5)
  let encoded = try BrowserDomainConfigurePayload(
    domains: ["example.com"],
    sessionEndEpochMilliseconds: 1_793_530_800_000,
    windows: [DomainWindowPlanWire(domain: "example.com", segments: [ruled])]
  ).encode()

  #expect(throws: WireProtocolFailure.invalidFrame) {
    try BrowserDomainConfigurePayload.decode(encoded.dropLast())
  }
}

/// `example.com` and `example.org`, the end, then one plan for `example.com`: blocked until 10:10 UTC, then 30/5.
let windowPayloadVector =
  "0002" + "0b6578616d706c652e636f6d" + "0b6578616d706c652e6f7267" + "01" + "000001a196c42780"
  + "01" + "0001" + "0b6578616d706c652e636f6d" + "02" + "000001a1969660c0" + "00" + "00"
  + "000001a196c42780" + "1e" + "05"
