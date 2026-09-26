import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings
import PosatoShared
import XCTest
@testable import Posato

/// Physical checklist driver for `IOS-002`. Runs only on a development-signed
/// iPhone; every test skips on the Simulator.
///
/// Usage: run `testSetupExpiryWindow` first, force-quit the Posato app on the
/// phone, wait past the printed interval end while using the phone, then run
/// `testVerifyExpiryAfterForceQuit`. All fixtures are synthetic.
final class SuspendedExpiryDeviceTests: XCTestCase {
    private let sessionId = "ios-002-device-check"
    private let cancelledSessionId = "ios-002-device-check-cancelled"
    private let domain = "example.com"
    private let foreignDomain = "example.net"
    private let foreignStoreName = ManagedSettingsStore.Name("app.posato.session-test-foreign")

    func testSetupExpiryWindow() throws {
#if targetEnvironment(simulator)
        throw XCTSkip("Requires a development-signed iPhone")
#else
        guard AuthorizationCenter.shared.authorizationStatus == .approved else {
            throw XCTSkip("Requires granted Screen Time authorization")
        }

        let enforcer = IosManagedSettingsEnforcer()
        XCTAssertEqual(
            try apply(domains: [domain], enforcer: enforcer),
            .applied
        )

        let foreign = ManagedSettingsStore(named: foreignStoreName)
        foreign.webContent.blockedByFilter = .specific([WebDomain(domain: foreignDomain)])

        let scheduler = SuspendedExpiryScheduler()
        let now = Date()

        // Cancel row: schedule, cancel immediately, prove the cancel stuck and
        // the restriction survived it.
        XCTAssertEqual(
            try schedule(
                sessionId: cancelledSessionId,
                start: now.addingTimeInterval(60),
                end: now.addingTimeInterval(1_020),
                scheduler: scheduler
            ),
            .scheduled
        )
        XCTAssertEqual(try cancel(scheduler: scheduler), .cancelled)
        XCTAssertNil(try recordStore().readPending())
        let probe = ManagedSettingsStore(named: IosEnforcementStoreName.posato)
        XCTAssertEqual(probe.webContent.blockedByFilter, .specific([WebDomain(domain: domain)]))

        // Expiry row: schedule the real window starting now, the way the
        // session integration will call it, so monitoring begins inside an
        // interval that has already started by seconds.
        let start = now
        let end = now.addingTimeInterval(960)
        XCTAssertEqual(
            try schedule(sessionId: sessionId, start: start, end: end, scheduler: scheduler),
            .scheduled
        )
        XCTAssertEqual(try recordStore().readPending()?.sessionId, sessionId)

        print("POSATO_DEVICE_CHECKPOINT: restriction applied, window ends \(Int(end.timeIntervalSince1970)).")
        print("POSATO_DEVICE_CHECKPOINT: force-quit the Posato app now, keep using the phone, then run testVerifyExpiryAfterForceQuit.")
#endif
    }

    func testVerifyExpiryAfterForceQuit() throws {
#if targetEnvironment(simulator)
        throw XCTSkip("Requires a development-signed iPhone")
#else
        // Named store must be clear while the foreign store survives.
        let probe = ManagedSettingsStore(named: IosEnforcementStoreName.posato)
        XCTAssertNil(probe.webContent.blockedByFilter)
        // Setup applies no applications, so this nil is structural rather than
        // proof the extension cleared shields; the web-content, reconciliation,
        // and session-id assertions below carry that proof.
        XCTAssertNil(probe.shield.applications)

        let foreign = ManagedSettingsStore(named: foreignStoreName)
        XCTAssertEqual(foreign.webContent.blockedByFilter, .specific([WebDomain(domain: foreignDomain)]))
        foreign.clearAllSettings()

        // Reconciliation must read the clear attributed to our session.
        // Read the raw record first: reporting through the adapter consumes
        // it, so the order below is load-bearing.
        let cleared = try XCTUnwrap(try recordStore().readCleared())
        XCTAssertEqual(cleared.sessionId, sessionId)
        // The clear must come from this checklist run, not a stale record.
        XCTAssertGreaterThan(cleared.clearedAt, Date().timeIntervalSince1970 - 3_600)
        let scheduler = SuspendedExpiryScheduler()
        XCTAssertEqual(try reconcile(sessionId: sessionId, scheduler: scheduler), .expired)
        XCTAssertNil(try recordStore().readCleared())

        // Leave no monitoring behind.
        XCTAssertEqual(try cancel(scheduler: scheduler), .cancelled)
        print("POSATO_DEVICE_CHECKPOINT: expiry verified, monitoring cancelled.")
#endif
    }

    /// `IOS-006` probe and regression check, unattended: repeats the relaunch
    /// sequence (cancel, clear, apply, schedule for the same session) inside
    /// a window that is already running, then waits for the real end. The
    /// restriction must survive the restart callback window and still be
    /// cleared, with a record, after the real end. Takes about 6 minutes.
    func testRelaunchSequenceInsideARunningWindowKeepsRestrictionsUntilTheRealEnd() throws {
#if targetEnvironment(simulator)
        throw XCTSkip("Requires a development-signed iPhone")
#else
        let authorizationDeadline = Date().addingTimeInterval(15)
        while AuthorizationCenter.shared.authorizationStatus != .approved, Date() < authorizationDeadline {
            RunLoop.current.run(until: Date().addingTimeInterval(1))
        }
        guard AuthorizationCenter.shared.authorizationStatus == .approved else {
            XCTFail("Requires granted Screen Time authorization; run screen-time-consent.json first")
            return
        }
        let records = try recordStore()
        guard case .absent = records.readPendingResult(),
              !DeviceActivityCenter().activities.contains(SuspendedExpiryActivity.name)
        else {
            XCTFail("Posato expiry monitoring is active; end the session before running the probe")
            return
        }

        let sessionId = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
        let enforcer = IosManagedSettingsEnforcer()
        let scheduler = SuspendedExpiryScheduler()
        defer {
            _ = try? cancel(scheduler: scheduler)
            records.removeCleared(sessionId: sessionId)
            ManagedSettingsStore(named: IosEnforcementStoreName.posato).clearAllSettings()
            XCTAssertNil(records.readCleared(sessionId: sessionId))
        }

        let now = Date()
        let start = now.addingTimeInterval(-600)
        let end = now.addingTimeInterval(360)
        XCTAssertEqual(try apply(domains: [domain], enforcer: enforcer), .applied)
        XCTAssertEqual(try schedule(sessionId: sessionId, start: start, end: end, scheduler: scheduler), .scheduled)

        XCTAssertEqual(try cancel(scheduler: scheduler), .cancelled)
        XCTAssertEqual(try clear(enforcer: enforcer), .cleared)
        XCTAssertEqual(try apply(domains: [domain], enforcer: enforcer), .applied)
        XCTAssertEqual(try schedule(sessionId: sessionId, start: start, end: end, scheduler: scheduler), .scheduled)

        let probe = ManagedSettingsStore(named: IosEnforcementStoreName.posato)
        let restartDeadline = Date().addingTimeInterval(120)
        while Date() < restartDeadline {
            let lifted = probe.webContent.blockedByFilter == nil
            let recorded = records.readCleared(sessionId: sessionId) != nil
            if lifted || recorded {
                let elapsed = Int(Date().timeIntervalSince(now))
                print("POSATO_DEVICE_CHECKPOINT: restart callback after \(elapsed) s, lifted=\(lifted), recorded=\(recorded).")
                XCTFail("The restart delivered an interval end inside the running window")
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(5))
        }
        print("POSATO_DEVICE_CHECKPOINT: restriction kept through the restart callback window.")

        let endDeadline = end.addingTimeInterval(300)
        while Date() < endDeadline, records.readCleared(sessionId: sessionId) == nil {
            RunLoop.current.run(until: Date().addingTimeInterval(10))
        }
        XCTAssertNil(probe.webContent.blockedByFilter)
        XCTAssertEqual(records.readCleared(sessionId: sessionId)?.sessionId, sessionId)
        print("POSATO_DEVICE_CHECKPOINT: real end cleared the restriction and recorded the session.")
#endif
    }

#if !targetEnvironment(simulator)
    private func clear(enforcer: IosManagedSettingsEnforcer) throws -> IosEnforcementOutcome {
        var result: IosEnforcementOutcome?
        enforcer.clear { result = $0 }
        return try XCTUnwrap(result)
    }

    private func recordStore() throws -> SuspendedExpiryRecordStore {
        try XCTUnwrap(SuspendedExpiryRecordStore.live(), "App Group container must be reachable on device")
    }

    private func apply(domains: [String], enforcer: IosManagedSettingsEnforcer) throws -> IosEnforcementOutcome {
        var result: IosEnforcementOutcome?
        enforcer.apply(request: IosEnforcementRequest(domains: domains, mappingIds: [])) { result = $0 }
        return try XCTUnwrap(result)
    }

    private func schedule(
        sessionId: String,
        start: Date,
        end: Date,
        scheduler: SuspendedExpiryScheduler
    ) throws -> IosSuspendedExpiryOutcome {
        var result: IosSuspendedExpiryOutcome?
        scheduler.schedule(
            request: IosSuspendedExpiryRequest(
                sessionId: sessionId,
                startEpochSeconds: Int64(start.timeIntervalSince1970),
                endEpochSeconds: Int64(end.timeIntervalSince1970)
            )
        ) { result = $0 }
        return try XCTUnwrap(result)
    }

    private func cancel(scheduler: SuspendedExpiryScheduler) throws -> IosSuspendedExpiryOutcome {
        var result: IosSuspendedExpiryOutcome?
        scheduler.cancel { result = $0 }
        return try XCTUnwrap(result)
    }

    private func reconcile(sessionId: String, scheduler: SuspendedExpiryScheduler) throws -> IosExpiryReconciliation {
        var result: IosExpiryReconciliation?
        scheduler.readReconciliation(sessionId: sessionId) { result = $0 }
        return try XCTUnwrap(result)
    }
#endif
}
