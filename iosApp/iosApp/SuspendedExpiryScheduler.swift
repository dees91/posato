import DeviceActivity
import FamilyControls
import Foundation
import PosatoShared

/// Injectable Device Activity center seam so schedule validation and stop
/// idempotence run on the Simulator without real monitoring. A plain protocol:
/// the platform center is a value type.
protocol SuspendedExpiryMonitoring {
    func startMonitoring(_ activity: DeviceActivityName, during schedule: DeviceActivitySchedule) throws
    func stopMonitoring(_ activities: [DeviceActivityName])
}

extension DeviceActivityCenter: SuspendedExpiryMonitoring {
    func startMonitoring(_ activity: DeviceActivityName, during schedule: DeviceActivitySchedule) throws {
        try startMonitoring(activity, during: schedule, events: [:])
    }
}

/// Swift scheduler for the suspended-expiry path. Starts one non-repeating
/// monitoring schedule from session start to session end under the fixed
/// Posato activity name, stops it on clear or early end, and is safe to
/// repeat. All Apple failures map to opaque platform-neutral outcomes; no
/// Apple error text crosses into Kotlin, logs, or diagnostics.
final class SuspendedExpiryScheduler: NSObject, IosSuspendedExpiryProvider {
    private let monitoring: SuspendedExpiryMonitoring
    private let authorization: () -> EnforcementAuthorization
    private let isCapable: Bool
    private let records: () -> SuspendedExpiryRecordStore?
    private let calendar: Calendar

    private static var defaultCapable: Bool {
#if targetEnvironment(simulator) || !POSATO_FAMILY_CONTROLS_DEVELOPMENT
        return false
#else
        return true
#endif
    }

    init(
        monitoring: SuspendedExpiryMonitoring = DeviceActivityCenter(),
        authorization: @escaping () -> EnforcementAuthorization = {
            EnforcementAuthorization(status: AuthorizationCenter.shared.authorizationStatus)
        },
        isCapable: Bool = defaultCapable,
        records: @escaping () -> SuspendedExpiryRecordStore? = {
            SuspendedExpiryRecordStore.live()
        },
        calendar: Calendar = .current
    ) {
        self.monitoring = monitoring
        self.authorization = authorization
        self.isCapable = isCapable
        self.records = records
        self.calendar = calendar
    }

    func schedule(
        request: IosSuspendedExpiryRequest,
        completion: @escaping (IosSuspendedExpiryOutcome) -> Void
    ) {
        guard isCapable, let store = records() else {
            completion(.unavailable)
            return
        }
        guard authorization().refusal == nil else {
            completion(.authorizationRequired)
            return
        }
        guard SuspendedExpiryRecordStore.isValidSessionId(request.sessionId) else {
            completion(.platformFailure)
            return
        }
        let start = Date(timeIntervalSince1970: TimeInterval(request.startEpochSeconds))
        let end = Date(timeIntervalSince1970: TimeInterval(request.endEpochSeconds))
        // An end before the start has a negative duration, so the single
        // minimum-duration rule below covers both cases with the same outcome.
        guard end.timeIntervalSince(start) >= SuspendedExpiryActivity.minimumInterval else {
            completion(.belowPlatformMinimum)
            return
        }
        do {
            try store.writePending(sessionId: request.sessionId)
            // Device Activity matches daily wall-clock components, not absolute
            // instants, and fires the one-shot expiry at the next occurrence.
            // The product never promises the exact wall-clock instant.
            let schedule = DeviceActivitySchedule(
                intervalStart: calendar.dateComponents([.hour, .minute], from: start),
                intervalEnd: calendar.dateComponents([.hour, .minute], from: end),
                repeats: false
            )
            try monitoring.startMonitoring(SuspendedExpiryActivity.name, during: schedule)
            completion(.scheduled)
        } catch {
            store.removePending()
            completion(.platformFailure)
        }
    }

    func cancel(handler: @escaping (IosSuspendedExpiryOutcome) -> Void) {
        monitoring.stopMonitoring([SuspendedExpiryActivity.name])
        records()?.removePending()
        handler(.cancelled)
    }

    func readReconciliation(handler: @escaping (IosExpiryReconciliation) -> Void) {
        guard records()?.readCleared() != nil else {
            handler(.unknown)
            return
        }
        handler(.expired)
    }
}
