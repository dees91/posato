import Foundation
import PosatoMacOSServiceCore

/// Which held websites the helper blocks at an instant (TARGETS-013, ADR 0004 amendment).
struct HostWindowPolicy: Sendable {
  let held: Set<String>
  let plans: [DomainWindowPlanWire]
  let endEpochMilliseconds: UInt64?
  let calendar: @Sendable () -> Calendar

  init(
    held: Set<String>,
    plans: [DomainWindowPlanWire],
    endEpochMilliseconds: UInt64?,
    calendar: @escaping @Sendable () -> Calendar = { Calendar.current }
  ) {
    self.held = held
    self.plans = plans
    self.endEpochMilliseconds = endEpochMilliseconds
    self.calendar = calendar
  }

  func blockedNow(at time: Date) -> Set<String> {
    held
  }

  func nextBoundary(after time: Date) -> Date? {
    nil
  }

  func nextOpening(host: String, after time: Date) -> Date? {
    nil
  }
}
