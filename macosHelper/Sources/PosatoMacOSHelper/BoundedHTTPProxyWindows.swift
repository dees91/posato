import Foundation
import Network

/// The listener follows each held website's window plan (TARGETS-013, ADR 0004 and ADR 0005 amendments).
extension BoundedHTTPProxy {
  static let windowRecheckInterval: TimeInterval = 60
  /// Fires just after the boundary, so the evaluation reads the new state.
  static let windowBoundarySlack: TimeInterval = 0.05

  /// What the listener denies now: the held websites except those open in their window.
  func blockedHosts() -> Set<String> {
    windows?.blockedNow(at: clock()) ?? selectedHosts
  }

  /// The local time `host` opens next inside the request, for the window page; nil without one.
  func openingTime(for host: String) -> (hour: Int, minute: Int)? {
    guard let opening = windows?.nextOpening(host: host, after: clock()) else {
      return nil
    }
    let parts = pageCalendar().dateComponents([.hour, .minute], from: opening)
    guard let hour = parts.hour, let minute = parts.minute else {
      return nil
    }
    return (hour, minute)
  }

  /// Ends the relays of every website that is blocked now; runs at each window boundary and is safe to call at
  /// any time, because a relay to a blocked website should not exist.
  func evaluateWindows() {
    queue.sync { endBlockedRelays() }
  }

  func endBlockedRelays() {
    guard windows != nil else {
      return
    }
    let blocked = blockedHosts()
    for (identifier, relay) in relayHosts
    where ExactHostPolicy.matches(host: relay.host, selectedHosts: blocked) {
      if let upstream = directConnections[identifier] {
        finish(relay.client, upstream)
      }
      relayHosts.removeValue(forKey: identifier)
    }
  }

  /// A wall-clock deadline at the next window boundary, which keeps counting through sleep, capped at a minute so
  /// a clock or time-zone change is caught soon; each fire ends the relays of websites that closed and re-arms.
  func armWindowTimer() {
    guard let windows, windows.hasPlans else {
      return
    }
    let timer = DispatchSource.makeTimerSource(queue: queue)
    timer.setEventHandler { [weak self] in
      guard let self else {
        return
      }
      self.endBlockedRelays()
      self.scheduleWindowTimer()
    }
    windowTimer = timer
    scheduleWindowTimer()
    timer.resume()
  }

  private func scheduleWindowTimer() {
    guard let timer = windowTimer else {
      return
    }
    let now = clock()
    let next =
      windows?.nextBoundary(after: now) ?? now.addingTimeInterval(Self.windowRecheckInterval)
    let delay = min(
      max(next.timeIntervalSince(now), 0) + Self.windowBoundarySlack, Self.windowRecheckInterval)
    timer.schedule(wallDeadline: .now() + delay, leeway: .milliseconds(50))
  }

}
