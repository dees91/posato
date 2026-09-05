import AppKit
import Foundation
import PosatoMacOSServiceCore
import Security
import UserNotifications

protocol RunningApplicationSnapshot: AnyObject {
  var processIdentifier: pid_t { get }
  var bundleIdentifier: String? { get }
  var isTerminated: Bool { get }
  func terminate() -> Bool
  func forceTerminate() -> Bool
}

extension NSRunningApplication: RunningApplicationSnapshot {}

protocol ApplicationSnapshotListing {
  /// Returns nil when enumeration fails. The session skips that tick: a
  /// partial listing is indistinguishable from mass process exit.
  func runningApplications() -> [any RunningApplicationSnapshot]?
}

struct ProcessApplicationListing: ApplicationSnapshotListing {
  /// `NSWorkspace.runningApplications` does not refresh in a process without
  /// a run loop, so the helper enumerates process identifiers directly and
  /// hydrates each one on demand. The snapshot is fresh on every poll.
  func runningApplications() -> [any RunningApplicationSnapshot]? {
    for _ in 0..<3 {
      let needed = proc_listpids(UInt32(PROC_ALL_PIDS), 0, nil, 0)
      guard needed > 0 else {
        return nil
      }
      var identifiers = [pid_t](repeating: 0, count: Int(needed) / MemoryLayout<pid_t>.size + 1)
      let capacity = identifiers.count * MemoryLayout<pid_t>.size
      let bytes = identifiers.withUnsafeMutableBytes { buffer in
        proc_listpids(UInt32(PROC_ALL_PIDS), 0, buffer.baseAddress, Int32(buffer.count))
      }
      guard bytes > 0, Int(bytes) < capacity else {
        continue
      }
      let count = Int(bytes) / MemoryLayout<pid_t>.size
      return identifiers.prefix(count).compactMap { identifier in
        guard identifier > 1 else {
          return nil
        }
        return NSRunningApplication(processIdentifier: identifier)
      }
    }
    return nil
  }
}

protocol ApplicationRequirementMatching {
  func matchingRequirement(
    processIdentifier: pid_t,
    requirements: Set<Data>
  ) -> Data?
}

struct SecurityApplicationMatcher: ApplicationRequirementMatching {
  func matchingRequirement(
    processIdentifier: pid_t,
    requirements: Set<Data>
  ) -> Data? {
    guard processIdentifier > 1 else {
      return nil
    }
    let attributes = [kSecGuestAttributePid as String: processIdentifier] as CFDictionary
    var code: SecCode?
    guard SecCodeCopyGuestWithAttributes(nil, attributes, [], &code) == errSecSuccess,
      let code
    else {
      return nil
    }
    for requirementData in requirements {
      var requirement: SecRequirement?
      guard
        SecRequirementCreateWithData(requirementData as CFData, [], &requirement) == errSecSuccess,
        let requirement
      else {
        continue
      }
      if SecCodeCheckValidity(code, [], requirement) == errSecSuccess {
        return requirementData
      }
    }
    return nil
  }
}

protocol ApplicationNoticePosting {
  func postPausedNotice(sessionEndEpochMilliseconds: UInt64?)
}

struct UserNotificationPausedNotice: ApplicationNoticePosting, @unchecked Sendable {
  // UNUserNotificationCenter is safe to use from any queue; the unchecked
  // conformance keeps the value usable inside its own @Sendable callbacks.
  private let center: UNUserNotificationCenter

  init(center: UNUserNotificationCenter = .current()) {
    self.center = center
  }

  func postPausedNotice(sessionEndEpochMilliseconds: UInt64?) {
    center.getNotificationSettings { settings in
      switch settings.authorizationStatus {
      case .authorized, .provisional, .ephemeral:
        self.add(sessionEndEpochMilliseconds: sessionEndEpochMilliseconds)
      case .notDetermined:
        center.requestAuthorization(options: [.alert]) { granted, _ in
          if granted {
            self.add(sessionEndEpochMilliseconds: sessionEndEpochMilliseconds)
          }
        }
      case .denied:
        break
      @unknown default:
        break
      }
    }
  }

  private func add(sessionEndEpochMilliseconds: UInt64?) {
    let content = UNMutableNotificationContent()
    content.title = PausedAppNotice.headline(
      sessionEndEpochMilliseconds: sessionEndEpochMilliseconds)
    content.body = "Return to Posato to change this session."
    let request = UNNotificationRequest(
      identifier: "app.posato.macos.helper.paused",
      content: content,
      trigger: nil
    )
    center.add(request)
  }
}

enum PausedAppNotice {
  static func headline(sessionEndEpochMilliseconds: UInt64?) -> String {
    guard let sessionEndEpochMilliseconds else {
      return "This app is paused"
    }
    let date = Date(timeIntervalSince1970: TimeInterval(sessionEndEpochMilliseconds) / 1_000)
    let formatter = DateFormatter()
    formatter.locale = .current
    formatter.dateStyle = .none
    formatter.timeStyle = .short
    return "This app is paused until \(formatter.string(from: date))"
  }
}

private struct TrackedApplication {
  var application: any RunningApplicationSnapshot
  let requirement: Data
  var gracefulRequestedAt: TimeInterval?
}

final class ApplicationEnforcementSession: @unchecked Sendable {
  private let requirements: Set<Data>
  private let sessionEndEpochMilliseconds: UInt64?
  private let listing: ApplicationSnapshotListing
  private let matcher: ApplicationRequirementMatching
  private let notices: ApplicationNoticePosting
  private let now: () -> TimeInterval
  private let queue = DispatchQueue(label: "app.posato.macos.helper.applications")
  private let graceSeconds: TimeInterval = 5
  private let pollSeconds: TimeInterval = 1
  private let noticeDebounceSeconds: TimeInterval = 1
  private var timer: DispatchSourceTimer?
  private var tracked: [pid_t: TrackedApplication] = [:]
  private var lastNotice: [Data: TimeInterval] = [:]

  init(
    requirements: Set<Data>,
    sessionEndEpochMilliseconds: UInt64?,
    listing: ApplicationSnapshotListing = ProcessApplicationListing(),
    matcher: ApplicationRequirementMatching = SecurityApplicationMatcher(),
    notices: ApplicationNoticePosting = UserNotificationPausedNotice(),
    now: @escaping () -> TimeInterval = { ProcessInfo.processInfo.systemUptime }
  ) {
    self.requirements = requirements
    self.sessionEndEpochMilliseconds = sessionEndEpochMilliseconds
    self.listing = listing
    self.matcher = matcher
    self.notices = notices
    self.now = now
  }

  deinit {
    timer?.cancel()
  }

  func start() {
    queue.async { [weak self] in
      guard let self, self.timer == nil else {
        return
      }
      let timer = DispatchSource.makeTimerSource(queue: self.queue)
      timer.schedule(deadline: .now() + self.pollSeconds, repeating: self.pollSeconds)
      timer.setEventHandler { [weak self] in
        self?.poll()
      }
      self.timer = timer
      timer.activate()
      self.poll()
    }
  }

  /// Terminal for the session: the timer is cancelled and every tracked
  /// process is dropped synchronously, so no termination can happen after
  /// this returns.
  func stop() {
    queue.sync { [weak self] in
      self?.timer?.cancel()
      self?.timer = nil
      self?.tracked.removeAll()
    }
  }

  func poll() {
    poll(now: now())
  }

  func poll(now: TimeInterval) {
    // A failed listing reads as mass exit, so the whole tick is skipped.
    guard let running = listing.runningApplications() else {
      return
    }
    // Held snapshots never refresh in a process without a run loop, so every
    // tick re-resolves tracked entries against a fresh listing pass. A pid
    // that vanishes from enumeration is treated as terminated.
    var live: [pid_t: any RunningApplicationSnapshot] = [:]
    for application in running {
      live[application.processIdentifier] = application
      guard !isPosato(application), tracked[application.processIdentifier] == nil else {
        continue
      }
      if let requirement = matcher.matchingRequirement(
        processIdentifier: application.processIdentifier,
        requirements: requirements
      ) {
        tracked[application.processIdentifier] = TrackedApplication(
          application: application,
          requirement: requirement,
          gracefulRequestedAt: nil
        )
      }
    }
    for identifier in Array(tracked.keys) {
      refresh(identifier: identifier, live: live, now: now)
    }
  }

  private func refresh(
    identifier: pid_t,
    live: [pid_t: any RunningApplicationSnapshot],
    now: TimeInterval
  ) {
    guard var entry = tracked[identifier] else {
      return
    }
    guard let current = live[identifier] else {
      postDebouncedNotice(requirement: entry.requirement, now: now)
      tracked.removeValue(forKey: identifier)
      return
    }
    guard
      matcher.matchingRequirement(
        processIdentifier: identifier,
        requirements: [entry.requirement]
      ) != nil
    else {
      // The occupant changed or validation blinked: drop it silently. Never
      // terminate or announce on identity doubt; a still-present target is
      // re-tracked with a fresh grace on the next tick.
      tracked.removeValue(forKey: identifier)
      return
    }
    entry.application = current
    if current.isTerminated {
      postDebouncedNotice(requirement: entry.requirement, now: now)
      tracked.removeValue(forKey: identifier)
      return
    }
    if let requestedAt = entry.gracefulRequestedAt {
      if now - requestedAt >= graceSeconds {
        _ = entry.application.forceTerminate()
      }
    } else {
      // A refused graceful request keeps the same force deadline; only the grace bounds work loss.
      _ = entry.application.terminate()
      entry.gracefulRequestedAt = now
    }
    tracked[identifier] = entry
  }

  private func postDebouncedNotice(requirement: Data, now: TimeInterval) {
    if let previous = lastNotice[requirement], now - previous < noticeDebounceSeconds {
      return
    }
    lastNotice[requirement] = now
    notices.postPausedNotice(sessionEndEpochMilliseconds: sessionEndEpochMilliseconds)
  }

  private func isPosato(_ application: any RunningApplicationSnapshot) -> Bool {
    let identifier = ServiceContract.applicationIdentifier
    return application.bundleIdentifier == identifier
      || application.bundleIdentifier?.hasPrefix("\(identifier).") == true
  }
}
