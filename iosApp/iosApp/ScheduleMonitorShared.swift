import DeviceActivity
import Foundation
import ManagedSettings
import UserNotifications

/// Fixed identities for scheduled pauses. The app, the monitor extension and
/// the tests resolve names through these constants so they cannot drift.
enum ScheduleMonitor {
    /// The schedule's own store. Managed Settings combines named stores, so a
    /// schedule ending never lifts a manual session's shields, and the reverse.
    static let storeName = ManagedSettingsStore.Name("app.posato.schedule")
    static let activityPrefix = "app.posato.schedule."
    static let tailActivity = DeviceActivityName("app.posato.schedule.tail")
    /// A one-shot end at the 24-hour cap, for an occurrence whose wall-clock end is later (a fall-back night).
    static let capActivity = DeviceActivityName("app.posato.schedule.cap")
    static let fileVersion = 1
    static let startRecordVersion = 1
    /// Restarting or stopping monitoring calls the end early; a callback this
    /// close to an occurrence that still runs changes nothing.
    static let earlyCallbackMargin: TimeInterval = 60
    static let maximumOccurrence: TimeInterval = 24 * 60 * 60
    static let startNoticePrefix = "app.posato.ios.schedule.start."
    /// The app's own end notice uses this identifier, so a second one replaces it.
    static let endNoticeIdentifier = "app.posato.ios.session.end"

    /// Dates in the table are Gregorian, like the app's; the device may use another calendar.
    static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        return calendar
    }

    static func activityName(scheduleId: String) -> DeviceActivityName {
        DeviceActivityName(activityPrefix + scheduleId)
    }

    static func isScheduleActivity(_ activity: DeviceActivityName) -> Bool {
        activity.rawValue.hasPrefix(activityPrefix)
    }

    /// The schedule an activity starts, or nil for the tail and foreign activities.
    static func scheduleId(of activity: DeviceActivityName) -> String? {
        guard isScheduleActivity(activity), activity != tailActivity, activity != capActivity else { return nil }
        return String(activity.rawValue.dropFirst(activityPrefix.count))
    }
}

/// The versioned App Group table the app writes after every evaluation. It
/// stays on the device: it is excluded from backup and never synchronized.
struct ScheduleMonitorFile: Codable, Equatable {
    struct Schedule: Codable, Equatable {
        let id: String
        let weekdays: Int
        let startMinute: Int
        let endMinute: Int
        let stoppedDates: [String]
        let startTitle: String
        let startBody: String
    }

    struct Running: Codable, Equatable {
        let id: String
        let date: String
        let startEpoch: Int64
        let endEpoch: Int64
    }

    struct Notices: Codable, Equatable {
        let enabled: Bool
        let endTitle: String
        let endBody: String
        /// The end of a manual session active on this device, in epoch seconds; Pause over waits for it.
        var manualSessionEnd: Int64?
    }

    let version: Int
    let schedules: [Schedule]
    let running: [Running]
    let domains: [String]
    let applicationTokens: [Data]
    let notices: Notices
}

struct ScheduleMonitorOccurrence: Equatable {
    let scheduleId: String
    let date: String
    let start: Date
    let end: Date
}

/// The same occurrence rule the app's engine follows: the weekday of the start
/// decides, an end at or before the start is the next day, an occurrence lasts
/// at most 24 hours, a skipped or ended date never runs, and a skipped wall
/// time starts at the change itself.
enum ScheduleMonitorRule {
    static func active(
        _ schedule: ScheduleMonitorFile.Schedule,
        at now: Date,
        calendar: Calendar,
        leading margin: TimeInterval = 0
    ) -> ScheduleMonitorOccurrence? {
        let today = calendar.startOfDay(for: now)
        for offset in [0, -1] {
            guard let day = calendar.date(byAdding: .day, value: offset, to: today),
                  let occurrence = occurrence(schedule, on: day, calendar: calendar),
                  now >= occurrence.start.addingTimeInterval(-margin), now < occurrence.end
            else { continue }
            return occurrence
        }
        return nil
    }

    static func occurrence(
        _ schedule: ScheduleMonitorFile.Schedule,
        on day: Date,
        calendar: Calendar
    ) -> ScheduleMonitorOccurrence? {
        guard let bounds = wallClockBounds(schedule, on: day, calendar: calendar) else { return nil }
        let end = min(bounds.end, bounds.start.addingTimeInterval(ScheduleMonitor.maximumOccurrence))
        guard end > bounds.start else { return nil }
        return ScheduleMonitorOccurrence(scheduleId: schedule.id, date: dateText(day, calendar: calendar), start: bounds.start, end: end)
    }

    /// The 24-hour cap of the occurrence on `day` when its wall-clock end passes it (a fall-back night), or nil.
    static func overrunCap(
        _ schedule: ScheduleMonitorFile.Schedule,
        on day: Date,
        calendar: Calendar
    ) -> Date? {
        guard let bounds = wallClockBounds(schedule, on: day, calendar: calendar) else { return nil }
        let cap = bounds.start.addingTimeInterval(ScheduleMonitor.maximumOccurrence)
        return bounds.end > cap ? cap : nil
    }

    /// A one-shot interval ending at `end`, which Device Activity accepts only when it lasts the minimum interval.
    static func capSchedule(endingAt end: Date, calendar: Calendar) -> DeviceActivitySchedule {
        let fields: Set<Calendar.Component> = [.year, .month, .day, .hour, .minute, .second]
        return DeviceActivitySchedule(
            intervalStart: calendar.dateComponents(fields, from: end.addingTimeInterval(-SuspendedExpiryActivity.minimumInterval)),
            intervalEnd: calendar.dateComponents(fields, from: end),
            repeats: false
        )
    }

    /// The start and the wall-clock end Device Activity uses, before the 24-hour cap.
    static func wallClockBounds(
        _ schedule: ScheduleMonitorFile.Schedule,
        on day: Date,
        calendar: Calendar
    ) -> (start: Date, end: Date)? {
        // Calendar weekdays run Sunday = 1 ... Saturday = 7; the plan's bit 0 is Monday.
        let weekday = (calendar.component(.weekday, from: day) + 5) % 7
        guard schedule.weekdays & (1 << weekday) != 0,
              !schedule.stoppedDates.contains(dateText(day, calendar: calendar)),
              let start = time(on: day, minute: schedule.startMinute, calendar: calendar),
              let endDay = schedule.endMinute <= schedule.startMinute
                ? calendar.date(byAdding: .day, value: 1, to: day) : day,
              let end = time(on: endDay, minute: schedule.endMinute, calendar: calendar)
        else { return nil }
        return (start, end)
    }

    /// Every occurrence the table says runs at `now`: by the rule, or pinned by
    /// the app when it saw one start (an edit may have moved the plan since).
    static func running(
        in file: ScheduleMonitorFile,
        at now: Date,
        calendar: Calendar,
        leading margin: TimeInterval = 0
    ) -> [ScheduleMonitorOccurrence] {
        var found = file.schedules.compactMap { active($0, at: now, calendar: calendar, leading: margin) }
        for pinned in file.running {
            let start = Date(timeIntervalSince1970: TimeInterval(pinned.startEpoch))
            let end = Date(timeIntervalSince1970: TimeInterval(pinned.endEpoch))
            guard now < end, now >= start.addingTimeInterval(-margin),
                  !found.contains(where: { $0.scheduleId == pinned.id && $0.date == pinned.date })
            else { continue }
            found.append(ScheduleMonitorOccurrence(scheduleId: pinned.id, date: pinned.date, start: start, end: end))
        }
        return found
    }

    static func time(on day: Date, minute: Int, calendar: Calendar) -> Date? {
        calendar.date(
            bySettingHour: minute / 60,
            minute: minute % 60,
            second: 0,
            of: day,
            matchingPolicy: .nextTime,
            repeatedTimePolicy: .first,
            direction: .forward
        )
    }

    static func dateText(_ day: Date, calendar: Calendar) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: day)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }
}

/// App Group files for scheduled pauses: the table the app writes, and one
/// small start record per occurrence the extension started, so the two
/// writers never share a file. Same conventions as the suspended-expiry
/// records: atomic writes, protection after first unlock, no backup.
struct ScheduleMonitorFileStore {
    private static let directoryName = "ScheduleMonitor"
    private static let tableFileName = "table-v1.json"
    private static let maximumTableBytes = 256 * 1024
    private static let maximumRecordBytes = 4096

    let directoryURL: URL
    private let fileManager: FileManager

    init(directoryURL: URL, fileManager: FileManager = .default) {
        self.directoryURL = directoryURL
        self.fileManager = fileManager
    }

    static func live(fileManager: FileManager = .default) -> ScheduleMonitorFileStore? {
        guard let container = fileManager.containerURL(
            forSecurityApplicationGroupIdentifier: SuspendedExpiryActivity.appGroupIdentifier
        ) else {
            return nil
        }
        return ScheduleMonitorFileStore(
            directoryURL: container.appendingPathComponent(directoryName, isDirectory: true),
            fileManager: fileManager
        )
    }

    func writeTable(_ file: ScheduleMonitorFile) throws {
        let data = try JSONEncoder().encode(file)
        guard data.count <= Self.maximumTableBytes else { throw CocoaError(.fileWriteOutOfSpace) }
        try write(data, fileName: Self.tableFileName)
    }

    /// Nil when absent, too large, unreadable, or written by another version.
    func readTable() -> ScheduleMonitorFile? {
        guard let data = read(Self.tableFileName, limit: Self.maximumTableBytes),
              let file = try? JSONDecoder().decode(ScheduleMonitorFile.self, from: data),
              file.version == ScheduleMonitor.fileVersion
        else { return nil }
        return file
    }

    func recordStarted(_ occurrence: ScheduleMonitorOccurrence, at time: Date) throws {
        let record = StartRecord(
            version: ScheduleMonitor.startRecordVersion,
            id: occurrence.scheduleId,
            date: occurrence.date,
            startedAt: time.timeIntervalSince1970
        )
        try write(JSONEncoder().encode(record), fileName: Self.recordName(occurrence.scheduleId, occurrence.date))
    }

    func hasStarted(scheduleId: String, date: String) -> Bool {
        startRecord(Self.recordName(scheduleId, date)) != nil
    }

    /// Started occurrences as `<id>:<date>`, for the app's notice bits.
    func startedOccurrences() -> [String] {
        recordNames().compactMap { startRecord($0) }.map { "\($0.id):\($0.date)" }.sorted()
    }

    func removeStarted(except keep: Set<String>) {
        for name in recordNames() {
            guard let record = startRecord(name), !keep.contains("\(record.id):\(record.date)") else { continue }
            try? fileManager.removeItem(at: directoryURL.appendingPathComponent(name))
        }
    }

    private struct StartRecord: Codable {
        let version: Int
        let id: String
        let date: String
        let startedAt: TimeInterval
    }

    private static func recordName(_ scheduleId: String, _ date: String) -> String {
        "started-\(scheduleId)-\(date)-v1.json"
    }

    private func recordNames() -> [String] {
        ((try? fileManager.contentsOfDirectory(atPath: directoryURL.path)) ?? [])
            .filter { $0.hasPrefix("started-") && $0.hasSuffix("-v1.json") }
            .sorted()
    }

    private func startRecord(_ name: String) -> StartRecord? {
        guard let data = read(name, limit: Self.maximumRecordBytes),
              let record = try? JSONDecoder().decode(StartRecord.self, from: data),
              record.version == ScheduleMonitor.startRecordVersion
        else { return nil }
        return record
    }

    private func read(_ name: String, limit: Int) -> Data? {
        let url = directoryURL.appendingPathComponent(name)
        guard let attributes = try? fileManager.attributesOfItem(atPath: url.path),
              let size = attributes[.size] as? NSNumber, size.intValue <= limit
        else { return nil }
        return try? Data(contentsOf: url)
    }

    private func write(_ data: Data, fileName: String) throws {
        try fileManager.createDirectory(
            at: directoryURL,
            withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication]
        )
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var mutableDirectory = directoryURL
        try mutableDirectory.setResourceValues(values)
        try data.write(
            to: directoryURL.appendingPathComponent(fileName),
            options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication]
        )
    }
}

/// Web domains blocked for a host, with its `www.` counterpart, shared by the
/// manual enforcer and the schedule monitor.
enum PosatoWebDomains {
    static func domains(from hosts: [String]) -> Set<WebDomain> {
        var domains = Set<WebDomain>()
        for host in hosts {
            domains.insert(WebDomain(domain: host))
            if let counterpart = wwwCounterpart(host) {
                domains.insert(WebDomain(domain: counterpart))
            }
        }
        return domains
    }

    static func wwwCounterpart(_ host: String) -> String? {
        let labels = host.split(separator: ".", omittingEmptySubsequences: false)
        guard labels.count >= 2 else {
            return nil
        }
        if labels.first == "www" {
            guard labels.count >= 3 else {
                return nil
            }
            return labels.dropFirst().joined(separator: ".")
        }
        return "www.\(host)"
    }
}

/// Narrow seam over a named store for scheduled pauses. The names differ from
/// the other store seams on purpose: all of them extend the same platform type.
protocol ScheduleShieldStore: AnyObject {
    func applySchedule(domains: Set<WebDomain>, applications: Set<ApplicationToken>)
    func clearSchedule()
    var holdsAnyShield: Bool { get }
}

extension ManagedSettingsStore: ScheduleShieldStore {
    func applySchedule(domains: Set<WebDomain>, applications: Set<ApplicationToken>) {
        webContent.blockedByFilter = domains.isEmpty ? nil : .specific(domains)
        shield.applications = applications.isEmpty ? nil : applications
    }

    func clearSchedule() {
        clearAllSettings()
    }

    var holdsAnyShield: Bool {
        webContent.blockedByFilter != nil || !(shield.applications?.isEmpty ?? true)
    }
}

protocol ScheduleNoticePoster {
    func post(identifier: String, title: String, body: String)
    func removePending(identifier: String)
}

struct UserNotificationSchedulePoster: ScheduleNoticePoster {
    func removePending(identifier: String) {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [identifier])
    }

    func post(identifier: String, title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: identifier, content: content, trigger: nil))
    }
}

/// Registers the one-shot end at an occurrence's 24-hour cap. The extension
/// registers the nearest one when a fall-back occurrence starts and the next one
/// when a cap passes while another still runs, so the caps hold even when Posato
/// has not been opened for weeks.
protocol ScheduleCapRegistrar {
    func register(cap: DeviceActivitySchedule)
}

struct DeviceActivityCapRegistrar: ScheduleCapRegistrar {
    func register(cap: DeviceActivitySchedule) {
        try? DeviceActivityCenter().startMonitoring(ScheduleMonitor.capActivity, during: cap)
    }
}
