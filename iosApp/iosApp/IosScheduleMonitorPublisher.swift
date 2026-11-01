import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings
import PosatoShared

/// Writes the schedule table the monitor extension reads, keeps one repeating
/// activity per enabled plan registered, and announces a running start the
/// extension has not recorded, such as one an edit began inside its interval.
/// It also composes and applies the schedule's store for the app with the
/// extension's rule, so both record what each occurrence holds the same way.
/// Nothing is registered, announced or applied before Screen Time is approved;
/// approval is the consent here.
final class IosScheduleMonitorPublisher: NSObject, IosScheduleMonitorProvider {
    private let files: ScheduleMonitorFileStore?
    private let center: () -> ScheduleActivityCenter
    private let authorized: () -> Bool
    private let isCapable: Bool
    private let storedMappings: () throws -> [StoredApplicationMapping]
    private let scheduleStore: () -> ScheduleShieldStore
    private let sessionStore: () -> ScheduleShieldStore
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
        scheduleStore: @escaping () -> ScheduleShieldStore = { ManagedSettingsStore(named: ScheduleMonitor.storeName) },
        sessionStore: @escaping () -> ScheduleShieldStore = { ManagedSettingsStore(named: PosatoManagedSettingsStore.name) },
        poster: ScheduleNoticePoster = UserNotificationSchedulePoster(),
        calendar: @escaping () -> Calendar = { ScheduleMonitor.calendar },
        now: @escaping () -> Date = Date.init
    ) {
        self.files = files
        self.center = center
        self.authorized = authorized
        self.isCapable = isCapable
        self.storedMappings = storedMappings
        self.scheduleStore = scheduleStore
        self.sessionStore = sessionStore
        self.poster = poster
        self.calendar = calendar
        self.now = now
    }

    func publish(table: IosScheduleMonitorTable) {
        var tokenIndex = TokenIndex(mappings: table.sets.isEmpty ? [] : (try? storedMappings()) ?? [])
        var domainIndex = ListIndex<String>()
        let sets = table.sets.map {
            ScheduleMonitorFile.PauseSet(
                id: $0.id,
                domainIndexes: domainIndex.indexes(for: $0.domains),
                tokenIndexes: tokenIndex.indexes(for: $0.mappingIds)
            )
        }
        var file = ScheduleMonitorFile(
            version: ScheduleMonitor.fileVersion,
            schedules: table.schedules.map {
                ScheduleMonitorFile.Schedule(
                    id: $0.id,
                    weekdays: Int($0.weekdays),
                    startMinute: Int($0.startMinute),
                    endMinute: Int($0.endMinute),
                    stoppedDates: $0.stoppedDates,
                    startTitle: $0.startTitle,
                    startBody: $0.startBody,
                    setId: $0.setId
                )
            },
            running: table.running.map {
                ScheduleMonitorFile.Running(id: $0.id, date: $0.date, startEpoch: $0.startEpochSeconds, endEpoch: $0.endEpochSeconds)
            },
            domains: domainIndex.items,
            applicationTokens: tokenIndex.tokens,
            notices: ScheduleMonitorFile.Notices(
                enabled: table.noticesEnabled,
                endTitle: table.endTitle,
                endBody: table.endBody,
                manualSessionEnd: table.manualSessionEndEpochSeconds > 0 ? table.manualSessionEndEpochSeconds : nil
            ),
            sets: sets,
            breaks: table.breaks.map {
                ScheduleMonitorFile.Break(id: $0.id, date: $0.date, startEpoch: $0.startEpochSeconds, endEpoch: $0.endEpochSeconds, over: $0.over)
            }
        )
        // The table is written before any registration, so a callback always reads the plans it belongs to.
        // It is rewritten only when it changed, under the lock the extension composes with; registrations are
        // reconciled every time. A table that could not be written is removed and leaves the registrations as
        // they are: no plan starts with the previous table's items, and applying the schedule reports a platform
        // failure. Without the lock no break is written, so nothing is released for one.
        if let files {
            let write = { (table: ScheduleMonitorFile) -> Bool in
                guard files.readTable() != table else { return true }
                return (try? files.writeTable(table)) != nil
            }
            let written = files.withComposeLockIfAvailable { write(file) }
            if written == nil {
                file.breaks = []
            }
            guard written ?? write(file) else { return }
        }
        let running = Set(file.running.map { "\($0.id):\($0.date)" })
        files?.removeStarted(except: running)
        // What an occurrence holds is kept while it runs, including one the extension started just before its time.
        let started = ScheduleMonitorRule.running(in: file, at: now(), calendar: calendar(), leading: ScheduleMonitor.earlyCallbackMargin)
        let kept = running.union(started.map { "\($0.scheduleId):\($0.date)" })
        files?.removeHeld(except: kept)
        files?.removeBreakOver(except: kept)
        reconcile(file)
        announceStarts(file)
    }

    /// Composes the occurrences the published table says run now and applies them, under the lock the extension
    /// takes, so the two never apply over each other. Nothing left to pause clears the schedule's store.
    func applySchedule(handler: @escaping (IosEnforcementOutcome) -> Void) {
        performOnMain {
            guard self.isCapable else { return handler(.unavailable) }
            guard self.authorized() else { return handler(.authorizationRequired) }
            guard let files = self.files, let file = files.readTable() else { return handler(.platformFailure) }
            let store = self.scheduleStore()
            let time = self.now()
            // The clear belongs to the same lock as the composition, so a callback that waited for the lock never
            // has its return erased by a release decided before it.
            let compose = { (breaks: Bool) -> Bool in
                let table = files.readTable() ?? file
                let running = ScheduleMonitorRule.running(in: table, at: time, calendar: self.calendar())
                let releasing = breaks ? self.releasing(table, files: files, at: time) : []
                let applied = ScheduleMonitorEvents.applyComposed(
                    running: running, file: table, files: files, store: store, sessionStore: self.sessionStore(), releasing: releasing
                )
                if !applied {
                    store.clearSchedule()
                }
                return applied
            }
            // Without the lock nothing is released for a break.
            let applied = files.withComposeLockIfAvailable { compose(true) } ?? compose(false)
            handler(applied ? .applied : .nothingToEnforce)
        }
    }

    /// Records a start before announcing it, as the extension does, so each run is announced once by either.
    func announceStarts(_ file: ScheduleMonitorFile) {
        guard isCapable, authorized(), let files, file.pausesAnything else { return }
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

    /// Registers the break activity for the nearest open deadline, the new one included, before a break is
    /// recorded. False takes no break: nothing is recorded, authored, or released.
    func armBreak(deadlineEpochSeconds: Int64) -> Bool {
        guard isCapable, authorized(), let files else { return false }
        let deadline = Date(timeIntervalSince1970: TimeInterval(deadlineEpochSeconds))
        let armed = files.withComposeLockIfAvailable { () -> Bool in
            let center = center()
            let pending = files.readTable().flatMap { ScheduleBreakWindows.nearestDeadline($0, over: files.breaksOver(), at: now()) }
            let wanted = ScheduleBreakActivity.schedule(deadline: min(deadline, pending ?? deadline), calendar: calendar())
            if let current = center.monitoredSchedule(for: ScheduleMonitor.breakActivity), Self.sameInterval(current, wanted) {
                return true
            }
            do {
                try center.startMonitoringSchedule(ScheduleMonitor.breakActivity, during: wanted)
                return true
            } catch {
                return false
            }
        }
        return armed ?? false
    }

    /// The occurrences a break releases on this device now, as `<id>:<date>`: only windows a registered
    /// deadline covers, so a phone whose arming failed shows no break.
    func coveredBreaks() -> [String] {
        guard let files, let file = files.readTable() else { return [] }
        return releasing(file, files: files, at: now()).sorted()
    }

    private func releasing(_ file: ScheduleMonitorFile, files: ScheduleMonitorFileStore, at time: Date) -> Set<String> {
        guard isCapable, authorized() else { return [] }
        let armed = ScheduleBreakActivity.armed(center: center(), calendar: calendar())
        return ScheduleBreakWindows.releasing(file, over: files.breaksOver(), armed: armed, at: time)
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
        // Kept while any break is open, so it is never stopped as stale; computed from the windows, never from
        // what is registered.
        if let deadline = ScheduleBreakWindows.nearestDeadline(file, over: files?.breaksOver() ?? [], at: now()) {
            wanted[ScheduleMonitor.breakActivity] = ScheduleBreakActivity.schedule(deadline: deadline, calendar: calendar())
        }
        let stale = center.monitoredActivities.filter { ScheduleMonitor.isScheduleActivity($0) && wanted[$0] == nil }
        if !stale.isEmpty {
            center.stopMonitoringSchedules(stale)
        }
        var breakRefused = false
        for (activity, schedule) in wanted {
            // Restarting calls the end early, so an unchanged plan is left alone.
            if let current = center.monitoredSchedule(for: activity), Self.sameInterval(current, schedule) { continue }
            do {
                try center.startMonitoringSchedule(activity, during: schedule)
            } catch {
                breakRefused = breakRefused || activity == ScheduleMonitor.breakActivity
            }
        }
        if breakRefused {
            retireBreaks(center)
        }
        writeReadout(center)
    }

    /// A break deadline that cannot be registered while an earlier registration still covers an open window ends
    /// every open break on this phone, as a failed chain does in the extension: the windows are recorded as over,
    /// that registration is stopped, and every running part is composed, all under the compose lock, so no
    /// registration releases them later. A phone where no registration covers a window has released nothing:
    /// it records nothing and arms on the next evaluation, as a receiving phone whose first arming failed.
    private func retireBreaks(_ center: ScheduleActivityCenter) {
        guard let files else { return }
        let time = now()
        files.withComposeLock {
            guard let table = files.readTable() else { return }
            let windows = ScheduleBreakWindows.open(table, over: files.breaksOver())
            let armed = ScheduleBreakActivity.armed(center: center, calendar: calendar())
            guard windows.contains(where: { ScheduleBreakWindows.covered($0, armed: armed) }) else { return }
            for window in windows {
                try? files.recordBreakOver(scheduleId: window.id, date: window.date)
            }
            center.stopMonitoringSchedules([ScheduleMonitor.breakActivity])
            let store = scheduleStore()
            let running = ScheduleMonitorRule.running(in: table, at: time, calendar: calendar())
            if !ScheduleMonitorEvents.applyComposed(running: running, file: table, files: files, store: store, sessionStore: sessionStore()) {
                store.clearSchedule()
            }
        }
    }

    /// What Device Activity monitors for Posato after this evaluation, in the app's own container: no backup,
    /// never synchronized, read only by the verification driver to prove the activity budget and the break's return.
    private func writeReadout(_ center: ScheduleActivityCenter) {
        guard let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else { return }
        var directory = support.appendingPathComponent("ScheduleMonitor", isDirectory: true)
        let names = center.monitoredActivities.map(\.rawValue).sorted()
        let deadline = ScheduleBreakActivity.armed(center: center, calendar: calendar()).map { Int64($0.timeIntervalSince1970) }
        let readout = MonitorReadout(version: 1, at: Int64(now().timeIntervalSince1970), count: names.count, activities: names, breakDeadline: deadline)
        guard let data = try? JSONEncoder().encode(readout),
              (try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)) != nil
        else { return }
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? directory.setResourceValues(values)
        try? data.write(to: directory.appendingPathComponent("monitor-v1.json"), options: .atomic)
    }

    private struct MonitorReadout: Encodable {
        let version: Int
        let at: Int64
        let count: Int
        let activities: [String]
        let breakDeadline: Int64?
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

    private func performOnMain(_ action: @escaping () -> Void) {
        if Thread.isMainThread {
            action()
        } else {
            DispatchQueue.main.async(execute: action)
        }
    }
}

/// Values listed once, in first-seen order, and the indexes a set names them by.
private struct ListIndex<Item: Hashable> {
    private(set) var items: [Item] = []
    private var positions: [Item: Int] = [:]

    mutating func indexes(for values: [Item]) -> [Int] {
        values.map { value in
            if let index = positions[value] {
                return index
            }
            items.append(value)
            positions[value] = items.count - 1
            return items.count - 1
        }
    }
}

/// The table's app tokens, each once, and the indexes a set names them by.
private struct TokenIndex {
    let mappings: [StoredApplicationMapping]
    private(set) var tokens: [Data] = []

    init(mappings: [StoredApplicationMapping]) {
        self.mappings = mappings
    }

    mutating func indexes(for mappingIds: [String]) -> [Int] {
        mappingIds.compactMap { identifier in
            guard let token = ApplicationTokenIdentity.token(matching: identifier, in: mappings) else { return nil }
            if let index = tokens.firstIndex(of: token) {
                return index
            }
            tokens.append(token)
            return tokens.count - 1
        }
    }
}
