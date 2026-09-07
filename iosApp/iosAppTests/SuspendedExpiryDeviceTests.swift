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

        // Expiry row: schedule the real window.
        let start = now.addingTimeInterval(60)
        let end = now.addingTimeInterval(1_020)
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
        let scheduler = SuspendedExpiryScheduler()
        XCTAssertEqual(try reconcile(scheduler: scheduler), .expired)
        let cleared = try XCTUnwrap(try recordStore().readCleared())
        XCTAssertEqual(cleared.sessionId, sessionId)
        // The clear must come from this checklist run, not a stale record.
        XCTAssertGreaterThan(cleared.clearedAt, Date().timeIntervalSince1970 - 3_600)

        // Leave no monitoring behind.
        XCTAssertEqual(try cancel(scheduler: scheduler), .cancelled)
        print("POSATO_DEVICE_CHECKPOINT: expiry verified, monitoring cancelled.")
#endif
    }

#if !targetEnvironment(simulator)
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

    private func reconcile(scheduler: SuspendedExpiryScheduler) throws -> IosExpiryReconciliation {
        var result: IosExpiryReconciliation?
        scheduler.readReconciliation { result = $0 }
        return try XCTUnwrap(result)
    }
#endif
}
