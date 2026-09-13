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
            // A cleared record for another session is never this schedule's
            // to delete: it survives until Kotlin persists it as a retained
            // terminal fact and acknowledges it (see
            // displacedClearedSessionId). Only the acknowledgement consumes.
            try store.writePending(sessionId: request.sessionId)
            // Absolute one-shot components: hour/minute alone cannot tell a
            // 24-hour session's identical ends apart and wrap at midnight.
            // The product never promises the exact wall-clock instant.
            let components: Set<Calendar.Component> = [.year, .month, .day, .hour, .minute]
            let schedule = DeviceActivitySchedule(
                intervalStart: calendar.dateComponents(components, from: start),
                intervalEnd: calendar.dateComponents(components, from: end),
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

    func readReconciliation(sessionId: String, handler: @escaping (IosExpiryReconciliation) -> Void) {
        // Fail closed: only a cleared record for this exact session reads as
        // its expiry. Anything else is unknown, never active. Reporting never
        // consumes the record: the Kotlin side banks the terminal fact first
        // and acknowledges afterwards, so a restart between the two still
        // observes the expiry instead of losing it.
        guard SuspendedExpiryRecordStore.isValidSessionId(sessionId),
              let store = records(),
              let cleared = store.readCleared(sessionId: sessionId),
              cleared.sessionId == sessionId
        else {
            handler(.unknown)
            return
        }
        handler(.expired)
    }

    func displacedClearedSessionId(currentSessionId: String, handler: @escaping (ExpiryDisplacement) -> Void) {
        guard SuspendedExpiryRecordStore.isValidSessionId(currentSessionId), let store = records() else {
            handler(ExpiryDisplacement(outcome: .failed, sessionId: nil))
            return
        }
        switch store.readClearedResult(preferredSessionId: currentSessionId) {
        case .absent:
            handler(ExpiryDisplacement(outcome: .absent, sessionId: nil))
        case .failed:
            handler(ExpiryDisplacement(outcome: .failed, sessionId: nil))
        case let .present(cleared):
            handler(ExpiryDisplacement(
                outcome: .present,
                sessionId: cleared.sessionId
            ))
        }
    }

    func acknowledgeReconciliation(sessionId: String, handler: @escaping (KotlinBoolean) -> Void) {
        // Consumes the cleared record, but only when it still belongs to this
        // exact session: another session's record is never removed here.
        guard SuspendedExpiryRecordStore.isValidSessionId(sessionId),
              let store = records(),
              let cleared = store.readCleared(sessionId: sessionId),
              cleared.sessionId == sessionId
        else {
            handler(KotlinBoolean(bool: false))
            return
        }
        store.removeCleared(sessionId: sessionId)
        handler(KotlinBoolean(bool: store.readCleared(sessionId: sessionId) == nil))
    }
}
