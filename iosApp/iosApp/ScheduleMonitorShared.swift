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
    /// Registered by 1.2 for an occurrence an edit moved; it is only stopped now, never registered.
    static let tailActivity = DeviceActivityName("app.posato.schedule.tail")
    /// A one-shot end at the 24-hour cap, for an occurrence whose wall-clock end is later (a fall-back night).
    static let capActivity = DeviceActivityName("app.posato.schedule.cap")
    /// Version 2 lists the pause sets, each schedule's set and what each running occurrence holds; a
    /// version-1 table from 1.2 is read as the first set until the app writes version 2.
    static let fileVersion = 2
    static let legacyFileVersion = 1
    /// The first pause set's identifier: sixteen zero bytes as lowercase hex.
    static let firstSetId = String(repeating: "0", count: 32)
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
        /// The schedule's pause set; absent in a version-1 table, which means the first set.
        var setId: String?
    }

    /// One pause set a schedule or a running occurrence uses: its websites and indexes into the table's tokens.
    struct PauseSet: Codable, Equatable {
        let id: String
        let domains: [String]
        let tokenIndexes: [Int]
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
    /// Version 1: the paused websites. Version 2: unused, every set lists its own.
    let domains: [String]
    /// Version 1: the paused apps. Version 2: every token the sets name, each once.
    let applicationTokens: [Data]
    let notices: Notices
    var sets: [PauseSet]?
}

extension ScheduleMonitorFile {
    /// Whether any plan here could pause something: a version-2 set with items, or a version-1 table's own items.
    var pausesAnything: Bool {
        guard let sets else { return !domains.isEmpty || !applicationTokens.isEmpty }
        return sets.contains { !$0.domains.isEmpty || !$0.tokenIndexes.isEmpty }
    }

    /// The websites and app tokens of the set `scheduleId` uses: in a version-1 table, the table's own items.
    func setItems(scheduleId: String) -> (domains: [String], tokens: [Data]) {
        guard let sets else { return (domains, applicationTokens) }
        let setId = schedules.first { $0.id == scheduleId }?.setId ?? ScheduleMonitor.firstSetId
        guard let set = sets.first(where: { $0.id == setId }) else { return ([], []) }
        return (set.domains, set.tokenIndexes.compactMap { applicationTokens.indices.contains($0) ? applicationTokens[$0] : nil })
    }
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

    /// Every occurrence the table says runs at `now`: by the rule, or listed by
    /// the app as running, such as a stopped date that resumed from the natural
    /// end the app observed.
    static func running(
        in file: ScheduleMonitorFile,
        at now: Date,
        calendar: Calendar,
        leading margin: TimeInterval = 0
    ) -> [ScheduleMonitorOccurrence] {
        var found = file.schedules.compactMap { active($0, at: now, calendar: calendar, leading: margin) }
        for listed in file.running {
            let start = Date(timeIntervalSince1970: TimeInterval(listed.startEpoch))
            let end = Date(timeIntervalSince1970: TimeInterval(listed.endEpoch))
            guard now < end, now >= start.addingTimeInterval(-margin),
                  !found.contains(where: { $0.scheduleId == listed.id && $0.date == listed.date })
            else { continue }
            found.append(ScheduleMonitorOccurrence(scheduleId: listed.id, date: listed.date, start: start, end: end))
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
    private static let tableFileName = "table-v2.json"
    private static let legacyTableFileName = "table-v1.json"
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

    /// Writes the version-2 table, then deletes the version-1 table in the same step. A crash between the
    /// two leaves both, and the version-2 table wins.
    func writeTable(_ file: ScheduleMonitorFile) throws {
        let data = try JSONEncoder().encode(file)
        guard data.count <= Self.maximumTableBytes else { throw CocoaError(.fileWriteOutOfSpace) }
        try write(data, fileName: Self.tableFileName)
        try? fileManager.removeItem(at: directoryURL.appendingPathComponent(Self.legacyTableFileName))
    }

    /// The version-2 table, or, while none was written, a valid version-1 table. Nil when absent, too large,
    /// unreadable, or written by another version; once a version-2 table exists, an unreadable one never
    /// falls back to version 1.
    func readTable() -> ScheduleMonitorFile? {
        if fileManager.fileExists(atPath: directoryURL.appendingPathComponent(Self.tableFileName).path) {
            return decode(Self.tableFileName, version: ScheduleMonitor.fileVersion)
        }
        return decode(Self.legacyTableFileName, version: ScheduleMonitor.legacyFileVersion)
    }

    /// Writes a 1.2 table as 1.2 did, so tests can start from what an App Store update leaves behind.
    func writeLegacyTable(_ file: ScheduleMonitorFile) throws {
        try write(JSONEncoder().encode(file), fileName: Self.legacyTableFileName)
    }

    private func decode(_ name: String, version: Int) -> ScheduleMonitorFile? {
        guard let data = read(name, limit: Self.maximumTableBytes),
              let file = try? JSONDecoder().decode(ScheduleMonitorFile.self, from: data),
              file.version == version
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

    /// What the composer paused for an occurrence, in the app or the extension, kept until the occurrence ends.
    struct HeldRecord: Codable, Equatable {
        let version: Int
        let id: String
        let date: String
        let domains: [String]
        let tokens: [Data]
    }

    func recordHeld(_ occurrence: ScheduleMonitorOccurrence, domains: [String], tokens: [Data]) throws {
        let record = HeldRecord(version: Self.heldRecordVersion, id: occurrence.scheduleId, date: occurrence.date, domains: domains, tokens: tokens)
        try write(JSONEncoder().encode(record), fileName: Self.heldName(occurrence.scheduleId, occurrence.date))
    }

    func held(scheduleId: String, date: String) -> HeldRecord? {
        guard let data = read(Self.heldName(scheduleId, date), limit: Self.maximumHeldBytes),
              let record = try? JSONDecoder().decode(HeldRecord.self, from: data),
              record.version == Self.heldRecordVersion
        else { return nil }
        return record
    }

    func removeHeld(except keep: Set<String>) {
        for name in heldNames() {
            guard let data = read(name, limit: Self.maximumHeldBytes),
                  let record = try? JSONDecoder().decode(HeldRecord.self, from: data),
                  !keep.contains("\(record.id):\(record.date)")
            else { continue }
            try? fileManager.removeItem(at: directoryURL.appendingPathComponent(name))
        }
    }

    /// Runs `body` while holding the App Group lock the app and the extension share, so one composes and
    /// applies the schedule's store at a time.
    @discardableResult
    func withComposeLock<T>(_ body: () -> T) -> T {
        try? fileManager.createDirectory(at: directoryURL, withIntermediateDirectories: true)
        let descriptor = open(directoryURL.appendingPathComponent("compose.lock").path, O_CREAT | O_RDWR, 0o600)
        guard descriptor >= 0 else { return body() }
        defer { close(descriptor) }
        flock(descriptor, LOCK_EX)
        defer { flock(descriptor, LOCK_UN) }
        return body()
    }

    private static let heldRecordVersion = 2
    private static let maximumHeldBytes = 512 * 1024

    private static func heldName(_ scheduleId: String, _ date: String) -> String {
        "held-\(scheduleId)-\(date)-v2.json"
    }

    private func heldNames() -> [String] {
        ((try? fileManager.contentsOfDirectory(atPath: directoryURL.path)) ?? [])
            .filter { $0.hasPrefix("held-") && $0.hasSuffix("-v2.json") }
            .sorted()
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
    /// The hosts the store's web filter blocks now, `www` forms included; they count toward the 50 web domains.
    var shieldedHosts: [String] { get }
    var shieldedTokens: Set<ApplicationToken> { get }
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

    var shieldedHosts: [String] {
        guard case let .specific(domains) = webContent.blockedByFilter else { return [] }
        return domains.compactMap(\.domain)
    }

    var shieldedTokens: Set<ApplicationToken> {
        shield.applications ?? []
    }
}

/// The same admission rule as the app's `planPause`: what parts already hold stays and always fits; a part that
/// holds nothing yet takes its websites in alphabetical order while they fit, and a running part's additions
/// come all together or not at all. The web filter counts each host with its `www` form, over every store.
enum PausePlanner {
    static let maximumWebDomains = 50
    static let maximumApplications = 50

    struct Part {
        let id: String
        let start: Date
        var currentDomains: Set<String> = []
        var currentTokens: Set<Data> = []
        var heldDomains: Set<String> = []
        var heldTokens: Set<Data> = []
    }

    struct Plan {
        let domains: Set<String>
        let tokens: Set<Data>
        let heldDomains: [String: Set<String>]
        let heldTokens: [String: Set<Data>]
        let deferred: [String: Int]
    }

    static func plan(_ parts: [Part], occupiedDomains: Set<String>, occupiedTokens: Set<Data> = []) -> Plan {
        let ordered = parts.sorted { ($0.start, $0.id) < ($1.start, $1.id) }
        var domains = ordered.reduce(into: Set<String>()) { $0.formUnion($1.heldDomains) }
        var tokens = ordered.reduce(into: Set<Data>()) { $0.formUnion($1.heldTokens) }
        var deferred: [String: Int] = [:]
        for part in ordered {
            let joining = part.heldDomains.isEmpty && part.heldTokens.isEmpty
            let newDomains = admit(part.currentDomains.subtracting(domains), joining: joining, order: { $0 }) { added in
                PosatoWebDomains.domains(from: Array(domains.union(occupiedDomains).union(added))).count <= maximumWebDomains
            }
            let newTokens = admit(part.currentTokens.subtracting(tokens), joining: joining, order: { $0.base64EncodedString() }) { added in
                tokens.union(occupiedTokens).union(added).count <= maximumApplications
            }
            domains.formUnion(newDomains.admitted)
            tokens.formUnion(newTokens.admitted)
            deferred[part.id] = newDomains.waiting + newTokens.waiting
        }
        var heldDomains: [String: Set<String>] = [:]
        var heldTokens: [String: Set<Data>] = [:]
        for part in ordered {
            heldDomains[part.id] = part.heldDomains.union(part.currentDomains.intersection(domains))
            heldTokens[part.id] = part.heldTokens.union(part.currentTokens.intersection(tokens))
        }
        return Plan(domains: domains, tokens: tokens, heldDomains: heldDomains, heldTokens: heldTokens, deferred: deferred)
    }

    private static func admit<Item: Hashable>(
        _ candidates: Set<Item>,
        joining: Bool,
        order: (Item) -> String,
        fits: (Set<Item>) -> Bool
    ) -> (admitted: Set<Item>, waiting: Int) {
        guard !candidates.isEmpty else { return ([], 0) }
        guard joining else { return fits(candidates) ? (candidates, 0) : ([], candidates.count) }
        var admitted = Set<Item>()
        for candidate in candidates.sorted(by: { order($0) < order($1) }) where fits(admitted.union([candidate])) {
            admitted.insert(candidate)
        }
        return (admitted, candidates.count - admitted.count)
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
