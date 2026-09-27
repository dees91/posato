import DeviceActivity
import FamilyControls
import Foundation
import PosatoShared

/// Narrow seam over Device Activity monitoring for scheduled pauses.
protocol ScheduleActivityCenter {
    var monitoredActivities: [DeviceActivityName] { get }
    func monitoredSchedule(for activity: DeviceActivityName) -> DeviceActivitySchedule?
    func startMonitoringSchedule(_ activity: DeviceActivityName, during schedule: DeviceActivitySchedule) throws
    func stopMonitoringSchedules(_ activities: [DeviceActivityName])
}

extension DeviceActivityCenter: ScheduleActivityCenter {
    var monitoredActivities: [DeviceActivityName] {
        activities
    }

    func monitoredSchedule(for activity: DeviceActivityName) -> DeviceActivitySchedule? {
        schedule(for: activity)
    }

    func startMonitoringSchedule(_ activity: DeviceActivityName, during schedule: DeviceActivitySchedule) throws {
        try startMonitoring(activity, during: schedule)
    }

    func stopMonitoringSchedules(_ activities: [DeviceActivityName]) {
        stopMonitoring(activities)
    }
}

/// Writes the schedule table the monitor extension reads and keeps one
/// repeating activity per enabled plan registered, plus a one-shot tail for a
/// running occurrence whose end the plan no longer gives. Nothing is
/// registered before Screen Time is approved; approval is the consent here.
final class IosScheduleMonitorPublisher: NSObject, IosScheduleMonitorProvider {
    private let files: ScheduleMonitorFileStore?
    private let center: () -> ScheduleActivityCenter
    private let authorized: () -> Bool
    private let isCapable: Bool
    private let storedMappings: () throws -> [StoredApplicationMapping]
    private let calendar: () -> Calendar
    private let now: () -> Date

    private static var defaultCapable: Bool {
#if targetEnvironment(simulator) || !POSATO_FAMILY_CONTROLS
        return false
#else
        return true
#endif
    }

    init(
        files: ScheduleMonitorFileStore? = .live(),
        center: @escaping () -> ScheduleActivityCenter = { DeviceActivityCenter() },
        authorized: @escaping () -> Bool = {
            EnforcementAuthorization(status: AuthorizationCenter.shared.authorizationStatus) == .approved
        },
        isCapable: Bool = defaultCapable,
        storedMappings: @escaping () throws -> [StoredApplicationMapping] = {
            try ApplicationMappingsStore.liveMigrated().load()
        },
        calendar: @escaping () -> Calendar = { ScheduleMonitor.calendar },
        now: @escaping () -> Date = Date.init
    ) {
        self.files = files
        self.center = center
        self.authorized = authorized
        self.isCapable = isCapable
        self.storedMappings = storedMappings
        self.calendar = calendar
        self.now = now
    }

    func publish(table: IosScheduleMonitorTable) {
        let file = ScheduleMonitorFile(
            version: ScheduleMonitor.fileVersion,
            schedules: table.schedules.map {
                ScheduleMonitorFile.Schedule(
                    id: $0.id,
                    weekdays: Int($0.weekdays),
                    startMinute: Int($0.startMinute),
                    endMinute: Int($0.endMinute),
                    stoppedDates: $0.stoppedDates,
                    startTitle: $0.startTitle,
                    startBody: $0.startBody
                )
            },
            running: table.running.map {
                ScheduleMonitorFile.Running(id: $0.id, date: $0.date, startEpoch: $0.startEpochSeconds, endEpoch: $0.endEpochSeconds)
            },
            domains: table.domains,
            applicationTokens: tokens(for: table.mappingIds),
            notices: ScheduleMonitorFile.Notices(
                enabled: table.noticesEnabled,
                endTitle: table.endTitle,
                endBody: table.endBody,
                manualSessionEnd: table.manualSessionEndEpochSeconds > 0 ? table.manualSessionEndEpochSeconds : nil
            )
        )
        // The table is written before any registration, so a callback always reads the plans it belongs to.
        // It is rewritten only when it changed; registrations are reconciled every time.
        if files?.readTable() != file {
            try? files?.writeTable(file)
        }
        files?.removeStarted(except: Set(file.running.map { "\($0.id):\($0.date)" }))
        reconcile(file)
    }

    func startedOccurrences() -> [String] {
        files?.startedOccurrences() ?? []
    }

    func reconcile(_ file: ScheduleMonitorFile) {
        guard isCapable, authorized() else { return }
        let center = center()
        var wanted: [DeviceActivityName: DeviceActivitySchedule] = [:]
        for schedule in file.schedules {
            wanted[ScheduleMonitor.activityName(scheduleId: schedule.id)] = Self.repeating(schedule)
        }
        if let tail = tail(for: file) {
            wanted[ScheduleMonitor.tailActivity] = tail
        }
        if let cap = cap(for: file) {
            wanted[ScheduleMonitor.capActivity] = cap
        }
        let stale = center.monitoredActivities.filter { ScheduleMonitor.isScheduleActivity($0) && wanted[$0] == nil }
        if !stale.isEmpty {
            center.stopMonitoringSchedules(stale)
        }
        for (activity, schedule) in wanted {
            // Restarting calls the end early, so an unchanged plan is left alone.
            if let current = center.monitoredSchedule(for: activity), Self.sameInterval(current, schedule) { continue }
            try? center.startMonitoringSchedule(activity, during: schedule)
        }
    }

    static func repeating(_ schedule: ScheduleMonitorFile.Schedule) -> DeviceActivitySchedule {
        DeviceActivitySchedule(
            intervalStart: DateComponents(hour: schedule.startMinute / 60, minute: schedule.startMinute % 60),
            intervalEnd: DateComponents(hour: schedule.endMinute / 60, minute: schedule.endMinute % 60),
            repeats: true
        )
    }

    /// A running occurrence that no plan interval ends any more (its start or days were edited) gets a
    /// one-shot activity ending at its pinned end, at least 15 minutes long as Device Activity requires.
    func tail(for file: ScheduleMonitorFile) -> DeviceActivitySchedule? {
        let calendar = calendar()
        let time = now()
        let orphaned = file.running.filter { pinned in
            let end = Date(timeIntervalSince1970: TimeInterval(pinned.endEpoch))
            guard end > time, let schedule = file.schedules.first(where: { $0.id == pinned.id }) else { return end > time }
            let planned = ScheduleMonitorRule.active(schedule, at: time, calendar: calendar)
            return planned?.end != end
        }
        guard let latest = orphaned.map({ Date(timeIntervalSince1970: TimeInterval($0.endEpoch)) }).max() else { return nil }
        let start = min(time, latest.addingTimeInterval(-SuspendedExpiryActivity.minimumInterval))
        let fields: Set<Calendar.Component> = [.year, .month, .day, .hour, .minute, .second]
        return DeviceActivitySchedule(
            intervalStart: calendar.dateComponents(fields, from: start),
            intervalEnd: calendar.dateComponents(fields, from: latest),
            repeats: false
        )
    }

    /// Compares only the fields Posato sets, since the system may fill in others such as the calendar.
    /// The nearest occurrence in the next two weeks whose wall-clock end passes the 24-hour cap (a
    /// fall-back night) gets a one-shot end at the cap, so the extension ends it like the engine does.
    func cap(for file: ScheduleMonitorFile) -> DeviceActivitySchedule? {
        let calendar = calendar()
        let time = now()
        let today = calendar.startOfDay(for: time)
        var nearest: Date?
        for offset in -1 ... 14 {
            guard let day = calendar.date(byAdding: .day, value: offset, to: today) else { continue }
            for schedule in file.schedules {
                guard let bounds = ScheduleMonitorRule.wallClockBounds(schedule, on: day, calendar: calendar) else { continue }
                let cap = bounds.start.addingTimeInterval(ScheduleMonitor.maximumOccurrence)
                if bounds.end > cap, cap > time, cap < (nearest ?? .distantFuture) {
                    nearest = cap
                }
            }
        }
        guard let end = nearest else { return nil }
        let fields: Set<Calendar.Component> = [.year, .month, .day, .hour, .minute, .second]
        return DeviceActivitySchedule(
            intervalStart: calendar.dateComponents(fields, from: end.addingTimeInterval(-SuspendedExpiryActivity.minimumInterval)),
            intervalEnd: calendar.dateComponents(fields, from: end),
            repeats: false
        )
    }

    static func sameInterval(_ left: DeviceActivitySchedule, _ right: DeviceActivitySchedule) -> Bool {
        let fields: [Calendar.Component] = [.year, .month, .day, .hour, .minute, .second]
        func matches(_ first: DateComponents, _ second: DateComponents) -> Bool {
            fields.allSatisfy { first.value(for: $0) == second.value(for: $0) }
        }
        return left.repeats == right.repeats && matches(left.intervalStart, right.intervalStart) && matches(left.intervalEnd, right.intervalEnd)
    }

    private func tokens(for mappingIds: [String]) -> [Data] {
        guard !mappingIds.isEmpty, let mappings = try? storedMappings() else { return [] }
        return mappingIds.compactMap { ApplicationTokenIdentity.token(matching: $0, in: mappings) }
    }
}
