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

/// Writes the schedule table the monitor extension reads, keeps one repeating
/// activity per enabled plan registered, and announces a running start the
/// extension has not recorded, such as one an edit began inside its interval.
/// Nothing is registered or announced before Screen Time is approved; approval
/// is the consent here.
final class IosScheduleMonitorPublisher: NSObject, IosScheduleMonitorProvider {
    private let files: ScheduleMonitorFileStore?
    private let center: () -> ScheduleActivityCenter
    private let authorized: () -> Bool
    private let isCapable: Bool
    private let storedMappings: () throws -> [StoredApplicationMapping]
    private let poster: ScheduleNoticePoster
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
            try ApplicationMappingSets.liveMigrated().loadAllSets()
        },
        poster: ScheduleNoticePoster = UserNotificationSchedulePoster(),
        calendar: @escaping () -> Calendar = { ScheduleMonitor.calendar },
        now: @escaping () -> Date = Date.init
    ) {
        self.files = files
        self.center = center
        self.authorized = authorized
        self.isCapable = isCapable
        self.storedMappings = storedMappings
        self.poster = poster
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
        announceStarts(file)
    }

    /// Records a start before announcing it, as the extension does, so each run is announced once by either.
    func announceStarts(_ file: ScheduleMonitorFile) {
        guard isCapable, authorized(), let files, !file.domains.isEmpty || !file.applicationTokens.isEmpty else { return }
        let time = now()
        for running in file.running where !files.hasStarted(scheduleId: running.id, date: running.date) {
            let start = Date(timeIntervalSince1970: TimeInterval(running.startEpoch))
            let end = Date(timeIntervalSince1970: TimeInterval(running.endEpoch))
            guard start <= time, time < end else { continue }
            // A start that could not be recorded is not announced, so a failing write never repeats the notice.
            guard (try? files.recordStarted(ScheduleMonitorOccurrence(scheduleId: running.id, date: running.date, start: start, end: end), at: time)) != nil,
                  file.notices.enabled,
                  let schedule = file.schedules.first(where: { $0.id == running.id })
            else { continue }
            poster.post(
                identifier: "\(ScheduleMonitor.startNoticePrefix)\(running.id).\(running.date)",
                title: schedule.startTitle,
                body: schedule.startBody
            )
        }
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

    /// Compares only the fields Posato sets, since the system may fill in others such as the calendar.
    /// The nearest occurrence in the next two weeks whose wall-clock end passes the 24-hour cap (a
    /// fall-back night) gets a one-shot end at the cap, so the extension ends it like the engine does.
    /// The extension also registers it when such an occurrence starts, which covers a phone where
    /// Posato was not opened within the two weeks.
    func cap(for file: ScheduleMonitorFile) -> DeviceActivitySchedule? {
        let calendar = calendar()
        let time = now()
        let today = calendar.startOfDay(for: time)
        var nearest: Date?
        for offset in -1 ... 14 {
            guard let day = calendar.date(byAdding: .day, value: offset, to: today) else { continue }
            for schedule in file.schedules {
                guard let cap = ScheduleMonitorRule.overrunCap(schedule, on: day, calendar: calendar) else { continue }
                if cap > time, cap < (nearest ?? .distantFuture) {
                    nearest = cap
                }
            }
        }
        return nearest.map { ScheduleMonitorRule.capSchedule(endingAt: $0, calendar: calendar) }
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
