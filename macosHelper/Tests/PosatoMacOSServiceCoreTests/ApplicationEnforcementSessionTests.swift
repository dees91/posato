import Foundation
import Testing

@testable import PosatoMacOSHelper

let stubRequirement = Data([7, 7, 7])

final class StubRunningApplication: RunningApplicationSnapshot {
  let processIdentifier: pid_t
  let bundleIdentifier: String?
  var isTerminated = false
  var terminateCalls = 0
  var forceTerminateCalls = 0
  var refuseGraceful = false

  init(processIdentifier: pid_t, bundleIdentifier: String? = "com.example.target") {
    self.processIdentifier = processIdentifier
    self.bundleIdentifier = bundleIdentifier
  }

  func terminate() -> Bool {
    terminateCalls += 1
    if refuseGraceful {
      return false
    }
    isTerminated = true
    return true
  }

  func forceTerminate() -> Bool {
    forceTerminateCalls += 1
    isTerminated = true
    return true
  }
}

final class StubApplicationListing: ApplicationSnapshotListing {
  var applications: [StubRunningApplication] = []

  func runningApplications() -> [any RunningApplicationSnapshot] {
    return applications
  }
}

final class StubRequirementMatcher: ApplicationRequirementMatching {
  var matched: [pid_t: Data] = [:]

  func matchingRequirement(
    processIdentifier: pid_t,
    requirements: Set<Data>
  ) -> Data? {
    guard let requirement = matched[processIdentifier],
      requirements.contains(requirement)
    else {
      return nil
    }
    return requirement
  }
}

final class StubNoticePosting: ApplicationNoticePosting {
  var posts: [UInt64?] = []

  func postPausedNotice(sessionEndEpochMilliseconds: UInt64?) {
    posts.append(sessionEndEpochMilliseconds)
  }
}

final class DroppedNoticePosting: ApplicationNoticePosting {
  func postPausedNotice(sessionEndEpochMilliseconds: UInt64?) {}
}

final class SessionStubs {
  let listing = StubApplicationListing()
  let matcher = StubRequirementMatcher()
  let notices = StubNoticePosting()

  func session() -> ApplicationEnforcementSession {
    return ApplicationEnforcementSession(
      requirements: [stubRequirement],
      sessionEndEpochMilliseconds: nil,
      listing: listing,
      matcher: matcher,
      notices: notices,
      now: { 0 }
    )
  }
}

@Test func givenRunningTargetWhenPolledThenItReceivesAGracefulRequest() {
  let stubs = SessionStubs()
  let session = stubs.session()
  let application = StubRunningApplication(processIdentifier: 101)
  stubs.listing.applications = [application]
  stubs.matcher.matched = [101: stubRequirement]

  session.poll(now: 0)

  #expect(application.terminateCalls == 1)
  #expect(application.forceTerminateCalls == 0)
}

@Test func givenTargetLaunchedLaterWhenPolledThenItIsTracked() {
  let stubs = SessionStubs()
  let session = stubs.session()
  let application = StubRunningApplication(processIdentifier: 102)
  stubs.matcher.matched = [102: stubRequirement]

  session.poll(now: 0)
  #expect(application.terminateCalls == 0)

  stubs.listing.applications = [application]
  session.poll(now: 2)

  #expect(application.terminateCalls == 1)
}

@Test func givenTargetStillRunningAfterGraceWhenPolledThenItIsForced() {
  let stubs = SessionStubs()
  let session = stubs.session()
  let application = StubRunningApplication(processIdentifier: 103)
  application.refuseGraceful = true
  stubs.listing.applications = [application]
  stubs.matcher.matched = [103: stubRequirement]

  session.poll(now: 0)
  session.poll(now: 3)
  #expect(application.forceTerminateCalls == 0)

  session.poll(now: 5)
  #expect(application.forceTerminateCalls == 1)
  #expect(stubs.notices.posts.isEmpty)
}

@Test func givenTargetExitsGracefullyWhenPolledThenNoForceFollows() {
  let stubs = SessionStubs()
  let session = stubs.session()
  let application = StubRunningApplication(processIdentifier: 104)
  stubs.listing.applications = [application]
  stubs.matcher.matched = [104: stubRequirement]

  session.poll(now: 0)
  session.poll(now: 6)

  #expect(application.terminateCalls == 1)
  #expect(application.forceTerminateCalls == 0)
  #expect(stubs.notices.posts.count == 1)
}

@Test func givenControlAppWhenPolledThenItIsUntouched() {
  let stubs = SessionStubs()
  let session = stubs.session()
  let application = StubRunningApplication(processIdentifier: 105)
  stubs.listing.applications = [application]

  session.poll(now: 0)
  session.poll(now: 9)

  #expect(application.terminateCalls == 0)
  #expect(application.forceTerminateCalls == 0)
  #expect(stubs.notices.posts.isEmpty)
}

@Test func givenPosatoBundleWhenMatchedThenItIsUntouched() {
  let stubs = SessionStubs()
  let session = stubs.session()
  let application = StubRunningApplication(
    processIdentifier: 106,
    bundleIdentifier: "app.posato.macos.helper"
  )
  stubs.listing.applications = [application]
  stubs.matcher.matched = [106: stubRequirement]

  session.poll(now: 0)
  session.poll(now: 9)

  #expect(application.terminateCalls == 0)
  #expect(application.forceTerminateCalls == 0)
}

@Test func givenRelaunchLoopWhenTerminatedThenNoticeDebouncesPerRequirement() {
  let stubs = SessionStubs()
  let session = stubs.session()
  stubs.matcher.matched = [201: stubRequirement, 202: stubRequirement, 203: stubRequirement]

  stubs.listing.applications = [StubRunningApplication(processIdentifier: 201)]
  session.poll(now: 0)
  session.poll(now: 0.5)
  #expect(stubs.notices.posts.count == 1)

  stubs.listing.applications = [StubRunningApplication(processIdentifier: 202)]
  session.poll(now: 0.75)
  session.poll(now: 1)
  #expect(stubs.notices.posts.count == 1)

  stubs.listing.applications = [StubRunningApplication(processIdentifier: 203)]
  session.poll(now: 2)
  session.poll(now: 3)
  #expect(stubs.notices.posts.count == 2)
}

@Test func givenDroppedNoticeWhenTargetRefusesGraceThenTerminationStands() {
  let stubs = SessionStubs()
  let session = ApplicationEnforcementSession(
    requirements: [stubRequirement],
    sessionEndEpochMilliseconds: nil,
    listing: stubs.listing,
    matcher: stubs.matcher,
    notices: DroppedNoticePosting(),
    now: { 0 }
  )
  let application = StubRunningApplication(processIdentifier: 107)
  application.refuseGraceful = true
  stubs.listing.applications = [application]
  stubs.matcher.matched = [107: stubRequirement]

  session.poll(now: 0)
  session.poll(now: 5)

  #expect(application.forceTerminateCalls == 1)
}

@Test func givenLiveSystemWhenListedThenGuiApplicationsArePresent() {
  let listing = ProcessApplicationListing()

  let snapshot = listing.runningApplications()

  #expect(!snapshot.isEmpty)
  #expect(snapshot.contains { $0.bundleIdentifier != nil && !$0.isTerminated })
}

@Test func givenStaleTrackedSnapshotWhenFreshShowsTerminatedThenNoticePosts() {
  // Production snapshots never refresh without a run loop: the held instance
  // still reports running while a fresh hydration for the same pid is dead.
  let stubs = SessionStubs()
  let session = stubs.session()
  let stale = StubRunningApplication(processIdentifier: 301)
  stale.refuseGraceful = true
  stubs.listing.applications = [stale]
  stubs.matcher.matched = [301: stubRequirement]

  session.poll(now: 0)
  #expect(stale.terminateCalls == 1)

  let fresh = StubRunningApplication(processIdentifier: 301)
  fresh.isTerminated = true
  stubs.listing.applications = [fresh]
  session.poll(now: 1)

  #expect(stubs.notices.posts.count == 1)
  stubs.listing.applications = []
  session.poll(now: 6)
  #expect(stale.forceTerminateCalls == 0)
  #expect(fresh.forceTerminateCalls == 0)
}

@Test func givenTrackedPidVanishesWhenPolledThenNoticePostsAndUntracks() {
  let stubs = SessionStubs()
  let session = stubs.session()
  let application = StubRunningApplication(processIdentifier: 302)
  application.refuseGraceful = true
  stubs.listing.applications = [application]
  stubs.matcher.matched = [302: stubRequirement]

  session.poll(now: 0)
  #expect(application.terminateCalls == 1)

  stubs.listing.applications = []
  session.poll(now: 1)

  #expect(stubs.notices.posts.count == 1)
  session.poll(now: 6)
  #expect(application.forceTerminateCalls == 0)
}

@Test func givenStoppedSessionWhenTimerWouldFireThenNoTerminationFollows() throws {
  let stubs = SessionStubs()
  let application = StubRunningApplication(processIdentifier: 108)
  application.refuseGraceful = true
  stubs.listing.applications = [application]
  stubs.matcher.matched = [108: stubRequirement]
  let session = ApplicationEnforcementSession(
    requirements: [stubRequirement],
    sessionEndEpochMilliseconds: nil,
    listing: stubs.listing,
    matcher: stubs.matcher,
    notices: stubs.notices,
    now: { ProcessInfo.processInfo.systemUptime }
  )

  session.start()
  Thread.sleep(forTimeInterval: 1.5)
  session.stop()
  let callsAfterStop = application.terminateCalls
  #expect(callsAfterStop > 0)
  Thread.sleep(forTimeInterval: 1.5)

  #expect(application.terminateCalls == callsAfterStop)
}
