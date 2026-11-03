import Foundation
import PosatoMacOSServiceCore

/// Which held websites the helper blocks at an instant (TARGETS-013, ADR 0004 amendment). A held website is open
/// only inside a segment with a rule and inside that rule's window, read from the wall clock in the current zone at
/// each evaluation; after its last segment, without a plan, or with an invalid plan, it is blocked until the request
/// ends. Retention and the limits never read this: an open window frees nothing.
struct HostWindowPolicy: Sendable {
  /// 21 running parts can cover a website, so a valid plan has at most 22 segments.
  static let maximumSegments = 22
  static let periods: Set<UInt8> = [30, 60, 120]
  static let lengths: Set<UInt8> = [5, 10, 15]
  /// Two days of minutes: further than any clock change can push the next change of a rule.
  private static let maximumScanMinutes = 2 * 1_440
  /// More steps than any valid plan has boundaries in a day.
  private static let maximumSteps = 1_024

  let held: Set<String>
  let endEpochMilliseconds: UInt64?
  let calendar: @Sendable () -> Calendar
  /// One valid plan per held website.
  private let valid: [String: [WindowSegmentWire]]

  init(
    held: Set<String>,
    plans: [DomainWindowPlanWire],
    endEpochMilliseconds: UInt64?,
    calendar: @escaping @Sendable () -> Calendar = { Calendar.current }
  ) {
    self.held = held
    self.endEpochMilliseconds = endEpochMilliseconds
    self.calendar = calendar
    let grouped = Dictionary(grouping: plans, by: \.domain)
    var valid: [String: [WindowSegmentWire]] = [:]
    for (domain, candidates) in grouped where held.contains(domain) && candidates.count == 1 {
      if Self.isValid(candidates[0].segments, end: endEpochMilliseconds) {
        valid[domain] = candidates[0].segments
      }
    }
    self.valid = valid
  }

  /// Whether any held website has a valid plan, so the helper needs a boundary timer at all.
  var hasPlans: Bool {
    !valid.isEmpty
  }

  /// Fails closed only for invalid data: a plan whose segments have merely passed is valid.
  static func isValid(_ segments: [WindowSegmentWire], end: UInt64?) -> Bool {
    guard let end, !segments.isEmpty, segments.count <= maximumSegments,
      zip(segments, segments.dropFirst()).allSatisfy({
        $0.untilEpochMilliseconds < $1.untilEpochMilliseconds
      }),
      let last = segments.last, last.untilEpochMilliseconds <= end
    else {
      return false
    }
    return segments.allSatisfy { segment in
      (segment.period == 0 && segment.length == 0)
        || (periods.contains(segment.period) && lengths.contains(segment.length))
    }
  }

  /// The held websites blocked at `time`: every held website except those whose valid plan is open.
  func blockedNow(at time: Date) -> Set<String> {
    let calendar = calendar()
    return held.filter { domain in !isOpen(domain, at: time, calendar: calendar) }
  }

  /// The next instant after `time` at which any held website's window opens, closes, or its segment ends.
  func nextBoundary(after time: Date) -> Date? {
    let calendar = calendar()
    return valid.values.compactMap { segments in
      Self.nextBoundary(segments, after: time, calendar: calendar)
    }.min()
  }

  /// The next instant after `time` at which `host` opens inside the request: every held entry of its `www` class
  /// open together. Nil when one of them never opens again in the request.
  func nextOpening(host: String, after time: Date) -> Date? {
    let normalized = ExactHostPolicy.normalizedHost(host)
    let entries = held.filter { stored in
      stored == normalized || ExactHostPolicy.wwwCounterpart(stored) == normalized
    }
    guard !entries.isEmpty, entries.allSatisfy({ valid[$0] != nil }) else {
      return nil
    }
    let calendar = calendar()
    var instant = time
    for _ in 0..<Self.maximumSteps {
      guard
        let next = entries.compactMap({
          Self.nextBoundary(valid[$0] ?? [], after: instant, calendar: calendar)
        }).min()
      else {
        return nil
      }
      if entries.allSatisfy({ isOpen($0, at: next, calendar: calendar) }) {
        return next
      }
      instant = next
    }
    return nil
  }

  private func isOpen(_ domain: String, at time: Date, calendar: Calendar) -> Bool {
    guard let segments = valid[domain], let segment = Self.segment(segments, at: time),
      let period = Self.period(segment)
    else {
      return false
    }
    return Self.wallMinute(time, calendar: calendar) % period < Int(segment.length)
  }

  private static func segment(_ list: [WindowSegmentWire], at time: Date) -> WindowSegmentWire? {
    let millis = UInt64(max(0, (time.timeIntervalSince1970 * 1_000).rounded(.down)))
    return list.first { $0.untilEpochMilliseconds > millis }
  }

  private static func period(_ segment: WindowSegmentWire) -> Int? {
    segment.period == 0 ? nil : Int(segment.period)
  }

  private static func until(_ segment: WindowSegmentWire) -> Date {
    Date(timeIntervalSince1970: TimeInterval(segment.untilEpochMilliseconds) / 1_000)
  }

  private static func nextBoundary(
    _ segments: [WindowSegmentWire], after time: Date, calendar: Calendar
  ) -> Date? {
    guard let segment = segment(segments, at: time) else {
      return nil
    }
    guard let period = period(segment) else {
      return until(segment)
    }
    return min(
      until(segment),
      nextChange(period: period, length: Int(segment.length), after: time, calendar: calendar))
  }

  static func wallMinute(_ time: Date, calendar: Calendar) -> Int {
    let parts = calendar.dateComponents([.hour, .minute], from: time)
    return (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
  }

  /// The first instant after `time` at which the rule's openness differs; across a clock change the minutes are
  /// read one by one, so a skipped window never occurs and a repeated one opens again.
  static func nextChange(period: Int, length: Int, after time: Date, calendar: Calendar) -> Date {
    let open = { (instant: Date) in wallMinute(instant, calendar: calendar) % period < length }
    let minuteStart = Date(
      timeIntervalSince1970: (time.timeIntervalSince1970 / 60).rounded(.down) * 60)
    let state = open(time)
    let phase = wallMinute(time, calendar: calendar) % period
    let candidate = minuteStart.addingTimeInterval(
      TimeInterval((state ? length - phase : period - phase) * 60))
    let sameOffset =
      calendar.timeZone.secondsFromGMT(for: minuteStart)
      == calendar.timeZone.secondsFromGMT(for: candidate)
    if sameOffset, open(candidate) != state {
      return candidate
    }
    var probe = minuteStart.addingTimeInterval(60)
    for _ in 0..<maximumScanMinutes {
      if open(probe) != state {
        return probe
      }
      probe = probe.addingTimeInterval(60)
    }
    return probe
  }
}
