import DeviceActivity
import Foundation
import ManagedSettings
import PosatoShared
import XCTest
@testable import Posato

final class FakeScheduleShieldStore: ScheduleShieldStore {
    var applied: (domains: Set<WebDomain>, applications: Set<ApplicationToken>)?
    var clears = 0

    func applySchedule(domains: Set<WebDomain>, applications: Set<ApplicationToken>) {
        applied = (domains, applications)
    }

    func clearSchedule() {
        clears += 1
        applied = nil
    }

    var holdsAnyShield: Bool {
        applied != nil
    }
}

final class FakeSchedulePoster: ScheduleNoticePoster {
    var posted: [String] = []
    var removed: [String] = []

    func post(identifier: String, title: String, body: String) {
        posted.append(identifier)
    }

    func removePending(identifier: String) {
        removed.append(identifier)
    }
}

final class FakeScheduleCenter: ScheduleActivityCenter {
    var installed: [DeviceActivityName: DeviceActivitySchedule] = [:]
    var starts: [DeviceActivityName] = []
    var stops: [[DeviceActivityName]] = []

    var monitoredActivities: [DeviceActivityName] {
        Array(installed.keys)
    }

    func monitoredSchedule(for activity: DeviceActivityName) -> DeviceActivitySchedule? {
        installed[activity]
    }

    func startMonitoringSchedule(_ activity: DeviceActivityName, during schedule: DeviceActivitySchedule) throws {
        starts.append(activity)
        installed[activity] = schedule
    }

    func stopMonitoringSchedules(_ activities: [DeviceActivityName]) {
        stops.append(activities)
        activities.forEach { installed[$0] = nil }
    }
}

final class ScheduleMonitorTests: XCTestCase {
    private let focusId = "000000000000400080000000000000a1"
    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Warsaw")!
        return calendar
    }

    private func local(_ year: Int, _ month: Int, _ day: Int, _ hour: Int, _ minute: Int = 0) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
    }

    private func schedule(
        weekdays: Int = 0b0011111,
        start: Int = 9 * 60,
        end: Int = 10 * 60,
        stopped: [String] = []
    ) -> ScheduleMonitorFile.Schedule {
        ScheduleMonitorFile.Schedule(
            id: focusId, weekdays: weekdays, startMinute: start, endMinute: end,
            stoppedDates: stopped, startTitle: "Scheduled pause started", startBody: "Focus, until 10:00."
        )
    }

    private func file(
        _ schedules: [ScheduleMonitorFile.Schedule],
        running: [ScheduleMonitorFile.Running] = [],
        notices: Bool = true
    ) -> ScheduleMonitorFile {
        ScheduleMonitorFile(
            version: ScheduleMonitor.fileVersion, schedules: schedules, running: running,
            domains: ["example.com"], applicationTokens: [],
            notices: ScheduleMonitorFile.Notices(enabled: notices, endTitle: "Pause over", endBody: "Available again.")
        )
    }

    private func isolatedFiles() throws -> ScheduleMonitorFileStore {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        return ScheduleMonitorFileStore(directoryURL: directory)
    }

    // MARK: - Rule (2026-09-28 is a Monday)

    func testWeekdayOfTheStartDecidesAndTheEndIsExclusive() {
        let focus = schedule()
        XCTAssertNil(ScheduleMonitorRule.active(focus, at: local(2026, 9, 28, 8, 59), calendar: calendar))
        XCTAssertEqual(ScheduleMonitorRule.active(focus, at: local(2026, 9, 28, 9), calendar: calendar)?.date, "2026-09-28")
        XCTAssertNil(ScheduleMonitorRule.active(focus, at: local(2026, 9, 28, 10), calendar: calendar))
        XCTAssertNil(ScheduleMonitorRule.active(focus, at: local(2026, 9, 27, 9, 30), calendar: calendar))
    }

    func testAPlanAcrossMidnightRunsFromItsStartDayIntoTheNextMorning() {
        let night = schedule(weekdays: 1 << 4, start: 22 * 60, end: 6 * 60)
        let saturday = ScheduleMonitorRule.active(night, at: local(2026, 9, 26, 5, 59), calendar: calendar)
        XCTAssertEqual(saturday?.date, "2026-09-25")
        XCTAssertEqual(saturday?.end, local(2026, 9, 26, 6))
    }

    func testAStoppedDateNeverRunsAndALateCallbackWithinTheMarginCounts() {
        XCTAssertNil(ScheduleMonitorRule.active(schedule(stopped: ["2026-09-28"]), at: local(2026, 9, 28, 9, 30), calendar: calendar))
        let early = local(2026, 9, 28, 8, 59).addingTimeInterval(30)
        XCTAssertNotNil(ScheduleMonitorRule.active(schedule(), at: early, calendar: calendar, leading: 60))
    }

    func testASkippedWallTimeStartsAtTheChangeItself() {
        // 2026-03-29 02:00 jumps to 03:00 in Warsaw.
        let sunday = schedule(weekdays: 1 << 6, start: 2 * 60 + 30, end: 4 * 60)
        let occurrence = ScheduleMonitorRule.occurrence(sunday, on: local(2026, 3, 29, 0), calendar: calendar)
        XCTAssertEqual(occurrence?.start, local(2026, 3, 29, 3))
    }

    func testAPinnedOccurrenceKeepsRunningAfterItsPlanMoved() {
        let moved = schedule(start: 11 * 60, end: 12 * 60)
        let pinned = ScheduleMonitorFile.Running(
            id: focusId, date: "2026-09-28",
            startEpoch: Int64(local(2026, 9, 28, 9).timeIntervalSince1970),
            endEpoch: Int64(local(2026, 9, 28, 12).timeIntervalSince1970)
        )
        let running = ScheduleMonitorRule.running(in: file([moved], running: [pinned]), at: local(2026, 9, 28, 9, 45), calendar: calendar)
        XCTAssertEqual(running.map(\.date), ["2026-09-28"])
    }

    // MARK: - Files

    func testTheTableRoundTripsAndAnotherVersionIsRefused() throws {
        let files = try isolatedFiles()
        let table = file([schedule()])
        try files.writeTable(table)
        XCTAssertEqual(files.readTable(), table)

        let other = ScheduleMonitorFile(
            version: 2, schedules: [], running: [], domains: [], applicationTokens: [],
            notices: ScheduleMonitorFile.Notices(enabled: false, endTitle: "", endBody: "")
        )
        try files.writeTable(other)
        XCTAssertNil(files.readTable())
    }

    func testStartRecordsAreKeptPerOccurrenceAndPruned() throws {
        let files = try isolatedFiles()
        let occurrence = ScheduleMonitorOccurrence(scheduleId: focusId, date: "2026-09-28", start: Date(), end: Date())
        try files.recordStarted(occurrence, at: Date())
        XCTAssertTrue(files.hasStarted(scheduleId: focusId, date: "2026-09-28"))
        XCTAssertEqual(files.startedOccurrences(), ["\(focusId):2026-09-28"])

        files.removeStarted(except: [])
        XCTAssertEqual(files.startedOccurrences(), [])
    }

    // MARK: - Events

    func testAStartAppliesTheScheduleStoreAndAnnouncesOnce() throws {
        let files = try isolatedFiles()
        try files.writeTable(file([schedule()]))
        let store = FakeScheduleShieldStore()
        let poster = FakeSchedulePoster()
        let activity = ScheduleMonitor.activityName(scheduleId: focusId)

        for _ in 0 ..< 2 {
            ScheduleMonitorEvents.handleIntervalStart(
                activity: activity, store: store, files: files, poster: poster,
                now: { self.local(2026, 9, 28, 9) }, calendar: calendar
            )
        }

        XCTAssertEqual(store.applied?.domains, PosatoWebDomains.domains(from: ["example.com"]))
        XCTAssertEqual(poster.posted, ["\(ScheduleMonitor.startNoticePrefix)\(focusId).2026-09-28"])
    }

    func testASkippedOccurrenceOrNoticesOffDoNotApplyOrPost() throws {
        let files = try isolatedFiles()
        try files.writeTable(file([schedule(stopped: ["2026-09-28"])]))
        let store = FakeScheduleShieldStore()
        let poster = FakeSchedulePoster()

        ScheduleMonitorEvents.handleIntervalStart(
            activity: ScheduleMonitor.activityName(scheduleId: focusId), store: store, files: files, poster: poster,
            now: { self.local(2026, 9, 28, 9) }, calendar: calendar
        )
        XCTAssertNil(store.applied)

        try files.writeTable(file([schedule()], notices: false))
        ScheduleMonitorEvents.handleIntervalStart(
            activity: ScheduleMonitor.activityName(scheduleId: focusId), store: store, files: files, poster: poster,
            now: { self.local(2026, 9, 28, 9) }, calendar: calendar
        )
        XCTAssertNotNil(store.applied)
        XCTAssertEqual(poster.posted, [])
    }

    func testAnEndClearsOnlyTheScheduleStoreAndSaysPauseOverWhenNoSessionHoldsShields() throws {
        let files = try isolatedFiles()
        try files.writeTable(file([schedule()]))
        let store = FakeScheduleShieldStore()
        store.applySchedule(domains: PosatoWebDomains.domains(from: ["example.com"]), applications: [])
        let session = FakeScheduleShieldStore()
        let poster = FakeSchedulePoster()

        ScheduleMonitorEvents.handleIntervalEnd(
            activity: ScheduleMonitor.activityName(scheduleId: focusId), store: store, sessionStore: session,
            files: files, poster: poster, now: { self.local(2026, 9, 28, 10) }, calendar: calendar
        )

        XCTAssertEqual(store.clears, 1)
        XCTAssertEqual(session.clears, 0)
        XCTAssertEqual(poster.posted, [ScheduleMonitor.endNoticeIdentifier])
    }

    func testAnEarlyEndFromARestartOrAnOverlapKeepsTheShields() throws {
        let files = try isolatedFiles()
        let overlapping = ScheduleMonitorFile.Schedule(
            id: "000000000000400080000000000000a2", weekdays: 0b0011111, startMinute: 9 * 60 + 30, endMinute: 11 * 60,
            stoppedDates: [], startTitle: "", startBody: ""
        )
        try files.writeTable(file([schedule(), overlapping]))
        let store = FakeScheduleShieldStore()
        store.applySchedule(domains: PosatoWebDomains.domains(from: ["example.com"]), applications: [])

        ScheduleMonitorEvents.handleIntervalEnd(
            activity: ScheduleMonitor.activityName(scheduleId: focusId), store: store, sessionStore: FakeScheduleShieldStore(),
            files: files, poster: FakeSchedulePoster(), now: { self.local(2026, 9, 28, 10) }, calendar: calendar
        )
        ScheduleMonitorEvents.handleIntervalEnd(
            activity: ScheduleMonitor.activityName(scheduleId: focusId), store: store, sessionStore: FakeScheduleShieldStore(),
            files: files, poster: FakeSchedulePoster(), now: { self.local(2026, 9, 28, 9, 20) }, calendar: calendar
        )

        XCTAssertEqual(store.clears, 0)
    }

    func testAnUnreadableTableClearsAtTheEnd() throws {
        let store = FakeScheduleShieldStore()
        store.applySchedule(domains: [], applications: [])
        ScheduleMonitorEvents.handleIntervalEnd(
            activity: ScheduleMonitor.activityName(scheduleId: focusId), store: store, sessionStore: FakeScheduleShieldStore(),
            files: try isolatedFiles(), poster: FakeSchedulePoster()
        )
        XCTAssertEqual(store.clears, 1)
    }

    // MARK: - Registration

    func testRegistrationWaitsForScreenTimeThenKeepsOneActivityPerPlanAndStopsRemovedOnes() throws {
        let center = FakeScheduleCenter()
        let files = try isolatedFiles()
        var approved = false
        let publisher = IosScheduleMonitorPublisher(
            files: files, center: { center }, authorized: { approved }, isCapable: true,
            storedMappings: { [] }, calendar: { self.calendar }, now: { self.local(2026, 9, 28, 8) }
        )
        let table = file([schedule()])

        publisher.reconcile(table)
        XCTAssertEqual(center.starts, [])

        approved = true
        publisher.reconcile(table)
        publisher.reconcile(table)
        XCTAssertEqual(center.starts, [ScheduleMonitor.activityName(scheduleId: focusId)])

        publisher.reconcile(file([]))
        XCTAssertEqual(center.stops, [[ScheduleMonitor.activityName(scheduleId: focusId)]])
    }

    func testARunningOccurrenceTheEditedPlanNoLongerEndsGetsATail() throws {
        let files = try isolatedFiles()
        let publisher = IosScheduleMonitorPublisher(
            files: files, center: { FakeScheduleCenter() }, authorized: { true }, isCapable: true,
            storedMappings: { [] }, calendar: { self.calendar }, now: { self.local(2026, 9, 28, 9, 30) }
        )
        let pinned = ScheduleMonitorFile.Running(
            id: focusId, date: "2026-09-28",
            startEpoch: Int64(local(2026, 9, 28, 9).timeIntervalSince1970),
            endEpoch: Int64(local(2026, 9, 28, 12).timeIntervalSince1970)
        )

        XCTAssertNotNil(publisher.tail(for: file([schedule(start: 11 * 60, end: 12 * 60)], running: [pinned])))
        let unchanged = ScheduleMonitorFile.Running(
            id: focusId, date: "2026-09-28",
            startEpoch: Int64(local(2026, 9, 28, 9).timeIntervalSince1970),
            endEpoch: Int64(local(2026, 9, 28, 10).timeIntervalSince1970)
        )
        XCTAssertNil(publisher.tail(for: file([schedule()], running: [unchanged])))
    }

    func testEveryPublishReconcilesSoALaterScreenTimeApprovalRegistersWithoutAChangedTable() throws {
        let center = FakeScheduleCenter()
        let files = try isolatedFiles()
        var approved = false
        let publisher = IosScheduleMonitorPublisher(
            files: files, center: { center }, authorized: { approved }, isCapable: true,
            storedMappings: { [] }, calendar: { self.calendar }, now: { self.local(2026, 9, 28, 8) }
        )
        let table = IosScheduleMonitorTable(
            schedules: [IosMonitorSchedule(
                id: focusId, weekdays: 0b0011111, startMinute: 540, endMinute: 600,
                stoppedDates: ["2026-09-28"], startTitle: "Scheduled pause started", startBody: "Focus, until 10:00."
            )],
            running: [], domains: ["example.com"], mappingIds: [], noticesEnabled: true,
            endTitle: "Pause over", endBody: "Available again.", manualSessionEndEpochSeconds: 0
        )

        publisher.publish(table: table)
        XCTAssertEqual(center.starts, [])
        XCTAssertEqual(files.readTable()?.schedules.first?.stoppedDates, ["2026-09-28"])

        approved = true
        publisher.publish(table: table)
        XCTAssertEqual(center.starts, [ScheduleMonitor.activityName(scheduleId: focusId)])
    }

    func testTheExtensionReadsDatesInTheGregorianCalendarWhateverTheDeviceUses() {
        XCTAssertEqual(ScheduleMonitor.calendar.identifier, .gregorian)
        var buddhist = Calendar(identifier: .buddhist)
        buddhist.timeZone = TimeZone(identifier: "Europe/Warsaw")!
        let day = buddhist.date(from: DateComponents(year: 2569, month: 9, day: 28))!
        XCTAssertEqual(ScheduleMonitorRule.dateText(day, calendar: ScheduleMonitor.calendar), "2026-09-28")
    }

    func testPauseOverWaitsForAManualSessionStillActive() throws {
        let files = try isolatedFiles()
        var table = file([schedule()])
        table = ScheduleMonitorFile(
            version: table.version, schedules: table.schedules, running: [], domains: table.domains, applicationTokens: [],
            notices: ScheduleMonitorFile.Notices(
                enabled: true, endTitle: "Pause over", endBody: "",
                manualSessionEnd: Int64(local(2026, 9, 28, 11).timeIntervalSince1970)
            )
        )
        try files.writeTable(table)
        let store = FakeScheduleShieldStore()
        store.applySchedule(domains: PosatoWebDomains.domains(from: ["example.com"]), applications: [])
        let poster = FakeSchedulePoster()

        ScheduleMonitorEvents.handleIntervalEnd(
            activity: ScheduleMonitor.activityName(scheduleId: focusId), store: store, sessionStore: FakeScheduleShieldStore(),
            files: files, poster: poster, now: { self.local(2026, 9, 28, 10) }, calendar: calendar
        )

        XCTAssertEqual(store.clears, 1)
        XCTAssertEqual(poster.posted, [])
    }

    func testAnOnTimeEndClearsEvenWhenAnotherPlanStartsAMinuteLater() throws {
        let files = try isolatedFiles()
        let next = ScheduleMonitorFile.Schedule(
            id: "000000000000400080000000000000a2", weekdays: 0b0011111, startMinute: 10 * 60 + 1, endMinute: 11 * 60,
            stoppedDates: [], startTitle: "", startBody: ""
        )
        try files.writeTable(file([schedule(), next]))
        let store = FakeScheduleShieldStore()
        store.applySchedule(domains: PosatoWebDomains.domains(from: ["example.com"]), applications: [])

        ScheduleMonitorEvents.handleIntervalEnd(
            activity: ScheduleMonitor.activityName(scheduleId: focusId), store: store, sessionStore: FakeScheduleShieldStore(),
            files: files, poster: FakeSchedulePoster(), now: { self.local(2026, 9, 28, 10) }, calendar: calendar
        )

        XCTAssertEqual(store.clears, 1)
    }

    func testAStartInsideAManualSessionThatEndsFirstWithdrawsTheAppsEarlierPauseOver() throws {
        let files = try isolatedFiles()
        let base = file([schedule()])
        let withManual = ScheduleMonitorFile(
            version: base.version, schedules: base.schedules, running: [], domains: base.domains, applicationTokens: [],
            notices: ScheduleMonitorFile.Notices(
                enabled: true, endTitle: "Pause over", endBody: "",
                manualSessionEnd: Int64(local(2026, 9, 28, 9, 30).timeIntervalSince1970)
            )
        )
        try files.writeTable(withManual)
        let poster = FakeSchedulePoster()

        ScheduleMonitorEvents.handleIntervalStart(
            activity: ScheduleMonitor.activityName(scheduleId: focusId), store: FakeScheduleShieldStore(), files: files, poster: poster,
            now: { self.local(2026, 9, 28, 9) }, calendar: calendar
        )

        XCTAssertEqual(poster.removed, [ScheduleMonitor.endNoticeIdentifier])
    }

    func testAFallBackOccurrenceLongerThanADayGetsAnEndAtTheCap() throws {
        // 2026-10-25 falls back from 03:00 to 02:00 in Warsaw, so Saturday 04:00 until Sunday 03:45 is 24 h 45 min.
        let saturday = ScheduleMonitorFile.Schedule(
            id: focusId, weekdays: 1 << 5, startMinute: 4 * 60, endMinute: 3 * 60 + 45,
            stoppedDates: [], startTitle: "", startBody: ""
        )
        let publisher = IosScheduleMonitorPublisher(
            files: try isolatedFiles(), center: { FakeScheduleCenter() }, authorized: { true }, isCapable: true,
            storedMappings: { [] }, calendar: { self.calendar }, now: { self.local(2026, 10, 24, 12) }
        )

        let cap = try XCTUnwrap(publisher.cap(for: file([saturday])))

        XCTAssertEqual(calendar.date(from: cap.intervalEnd), local(2026, 10, 24, 4).addingTimeInterval(24 * 60 * 60))
        XCTAssertNil(publisher.cap(for: file([schedule()])))
    }

    func testAFallBackOccurrenceRegistersItsCapWhenItStartsEvenWithoutTheApp() throws {
        let saturday = ScheduleMonitorFile.Schedule(
            id: focusId, weekdays: 1 << 5, startMinute: 4 * 60, endMinute: 3 * 60 + 45,
            stoppedDates: [], startTitle: "", startBody: ""
        )
        let files = try isolatedFiles()
        try files.writeTable(file([saturday]))
        let caps = RecordingCapRegistrar()

        ScheduleMonitorEvents.handleIntervalStart(
            activity: ScheduleMonitor.activityName(scheduleId: focusId), store: FakeScheduleShieldStore(), files: files,
            poster: FakeSchedulePoster(), caps: caps, now: { self.local(2026, 10, 24, 4) }, calendar: calendar
        )

        let cap = try XCTUnwrap(caps.registered.first)
        XCTAssertEqual(caps.registered.count, 1)
        XCTAssertEqual(calendar.date(from: cap.intervalEnd), local(2026, 10, 24, 4).addingTimeInterval(24 * 60 * 60))
        XCTAssertFalse(cap.repeats)

        let ordinary = RecordingCapRegistrar()
        try files.writeTable(file([schedule()]))
        ScheduleMonitorEvents.handleIntervalStart(
            activity: ScheduleMonitor.activityName(scheduleId: focusId), store: FakeScheduleShieldStore(), files: files,
            poster: FakeSchedulePoster(), caps: ordinary, now: { self.local(2026, 9, 28, 9) }, calendar: calendar
        )
        XCTAssertTrue(ordinary.registered.isEmpty)
    }
}

final class RecordingCapRegistrar: ScheduleCapRegistrar {
    var registered: [DeviceActivitySchedule] = []

    func register(cap: DeviceActivitySchedule) {
        registered.append(cap)
    }
}
