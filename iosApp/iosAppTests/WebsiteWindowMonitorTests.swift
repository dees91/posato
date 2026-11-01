import DeviceActivity
import Foundation
import ManagedSettings
import PosatoShared
import XCTest
@testable import Posato

/// The manual session's web filter as the monitor narrows it.
final class FakeManualFilter: ManualWebFilterStore {
    var blocked: [String]?
    var writes = 0

    func setBlockedHosts(_ hosts: [String]) {
        blocked = hosts.sorted()
        writes += 1
    }
}

/// TARGETS-013 on the iPhone: the monitor extension opens and closes website windows while Posato is closed, each
/// boundary through its own Device Activity (G21). The device's browser probes cannot sample the instants around a
/// boundary, so these injected-clock tests are the proof that an opening is never early and never consumed by an
/// early callback (product page, Per platform).
final class WebsiteWindowMonitorTests: XCTestCase {
    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Warsaw")!
        return calendar
    }

    /// 2026-09-28 (a Monday) in Warsaw.
    private func at(_ hour: Int, _ minute: Int, _ second: Double = 0) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 9, day: 28, hour: hour, minute: minute))!.addingTimeInterval(second)
    }

    private func millis(_ date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1000).rounded())
    }

    private func isolatedFiles() throws -> ScheduleMonitorFileStore {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        return ScheduleMonitorFileStore(directoryURL: directory)
    }

    /// A manual session from 10:00 to 11:00 holding a ruled `ruled.example` (30/5) and an unruled `control.example`.
    private func manualRecord(end: Date? = nil) -> ManualWindowRecord {
        let end = end ?? at(11, 0)
        return ManualWindowRecord(
            version: ManualWindowRecord.currentVersion,
            endEpochMillis: millis(end),
            held: ["control.example", "ruled.example"],
            plans: [WebsiteWindowPlan(domain: "ruled.example", segments: [.init(until: millis(end), period: 30, length: 5)])]
        )
    }

    private struct Fixture {
        let files: ScheduleMonitorFileStore
        let center: RecordingWindowCenter
        let filter: FakeManualFilter
    }

    /// A manual session the app applied at `appliedAt`, which registered the boundaries ahead.
    private func session(appliedAt: Date = Date.distantPast, end: Date? = nil) throws -> Fixture {
        let files = try isolatedFiles()
        try files.writeManualWindows(manualRecord(end: end))
        let fixture = Fixture(files: files, center: RecordingWindowCenter(), filter: FakeManualFilter())
        evaluate(fixture, nil, .evaluation, appliedAt == Date.distantPast ? at(10, 5) : appliedAt)
        return fixture
    }

    private func evaluate(
        _ fixture: Fixture,
        _ activity: DeviceActivityName?,
        _ callback: MonitorBoundary.Callback,
        _ time: Date,
        store: FakeScheduleShieldStore = FakeScheduleShieldStore()
    ) {
        ScheduleMonitorEvents.handleWindowCallback(
            activity: activity, callback: callback, store: store, sessionStore: FakeScheduleShieldStore(),
            manualFilter: fixture.filter, files: fixture.files, center: fixture.center, now: { time }, calendar: calendar
        )
    }

    private func fires(_ fixture: Fixture) -> [Date] {
        fixture.center.monitoredActivities.compactMap { WindowActivities.fireInstant(of: $0) }.sorted()
    }

    private func own(_ instant: Date) -> DeviceActivityName {
        WindowActivities.name(firingAt: instant)
    }

    // MARK: - The wall clock decides, never early

    func testTheRuleOpensExactlyAtItsBoundaryAndClosesAtItsEnd() {
        let rule = WebsiteWindowRule(period: 30, length: 5)
        XCTAssertFalse(WindowWallClock.isOpen(rule, at: at(10, 29, 59.999), calendar: calendar))
        XCTAssertTrue(WindowWallClock.isOpen(rule, at: at(10, 30), calendar: calendar))
        XCTAssertTrue(WindowWallClock.isOpen(rule, at: at(10, 34, 59.999), calendar: calendar))
        XCTAssertFalse(WindowWallClock.isOpen(rule, at: at(10, 35), calendar: calendar))
        XCTAssertEqual(WindowWallClock.nextChange(rule, after: at(10, 12), calendar: calendar), at(10, 30))
        XCTAssertEqual(WindowWallClock.nextChange(rule, after: at(10, 30), calendar: calendar), at(10, 35))
    }

    func testTheAppRegistersEveryBoundaryOfTheSessionAheadEachAsItsOwnActivity() throws {
        let fixture = try session()
        XCTAssertEqual(fires(fixture), [at(10, 30), at(10, 35), at(11, 0)])
        XCTAssertEqual(fixture.filter.blocked, ["control.example", "ruled.example"])
        let opening = try XCTUnwrap(fixture.center.installed[own(at(10, 30))])
        XCTAssertEqual(calendar.date(from: opening.intervalEnd), at(10, 30), "registered 25 min ahead, its end is the boundary")
        let close = try XCTUnwrap(fixture.center.installed[own(at(10, 35))])
        XCTAssertEqual(calendar.date(from: close.intervalEnd), at(10, 35))
    }

    func testABoundaryLessThanFifteenMinutesAheadStartsAtTheBoundaryNeverInThePast() throws {
        let fixture = try session(appliedAt: at(10, 25))
        let opening = try XCTUnwrap(fixture.center.installed[own(at(10, 30))])
        XCTAssertEqual(calendar.date(from: opening.intervalStart), at(10, 30))
        for registration in fixture.center.registrations {
            XCTAssertGreaterThanOrEqual(try XCTUnwrap(calendar.date(from: registration.schedule.intervalStart)), at(10, 25))
        }
    }

    func testCallbacksASecondAndAMillisecondBeforeAnOpeningStayBlockedAndOneAtTheOpeningOpens() throws {
        for early in [1.0, 0.001] {
            let fixture = try session()
            evaluate(fixture, nil, .evaluation, at(10, 30).addingTimeInterval(-early))
            XCTAssertEqual(fixture.filter.blocked, ["control.example", "ruled.example"], "early by \(early) s")
            evaluate(fixture, own(at(10, 30)), .end, at(10, 30))
            XCTAssertEqual(fixture.filter.blocked, ["control.example"])
        }
    }

    func testAnEarlyEndInsideOrOutsideTheMarginNeverOpensNorConsumesTheOpening() throws {
        for early in [30.0, 90.0] {
            let fixture = try session()
            fixture.center.touched = []
            evaluate(fixture, own(at(10, 30)), .end, at(10, 30).addingTimeInterval(-early))

            XCTAssertEqual(fixture.filter.blocked, ["control.example", "ruled.example"], "early by \(early) s")
            XCTAssertFalse(fixture.center.touched.contains(own(at(10, 30))), "the caller is never restarted")
            XCTAssertNotNil(fixture.center.installed[own(at(10, 30, 20))], "the opening's fallback, 20 s after it, is registered")
            evaluate(fixture, own(at(10, 30, 20)), .end, at(10, 30, 20.3))
            XCTAssertEqual(fixture.filter.blocked, ["control.example"])
        }
    }

    func testAnEndCallbackOfTheOpeningsActivityBeforeItsBoundaryIsConsumedWhateverItsInterval() throws {
        // The activity named for 10:30 ends at 10:29:30, as a registration ending early does: it no longer covers.
        let fixture = try session()
        fixture.center.installed[own(at(10, 30))] = WindowActivities.schedule(fire: at(10, 29, 30), at: at(10, 0), calendar: calendar)
        evaluate(fixture, own(at(10, 30)), .end, at(10, 29, 30))
        XCTAssertEqual(fixture.filter.blocked, ["control.example", "ruled.example"])
        XCTAssertNotNil(fixture.center.installed[own(at(10, 30, 20))], "the opening's fallback is registered")
    }

    func testAWindowCallbackNeverStopsAnActivityNotEvenOneThatHasFired() throws {
        // The opening's fallback, an interval starting at 10:30:20, has fired; the close's callback comes at 10:35.
        let fixture = try session()
        evaluate(fixture, own(at(10, 30)), .end, at(10, 29))
        evaluate(fixture, own(at(10, 30, 20)), .start, at(10, 30, 22))
        XCTAssertEqual(fixture.filter.blocked, ["control.example"])
        evaluate(fixture, own(at(10, 35)), .end, at(10, 35, 0.1))
        XCTAssertEqual(fixture.filter.blocked, ["control.example", "ruled.example"])
        XCTAssertTrue(fixture.center.stops.isEmpty, "a stop delivers an end whose callback waits for the compose lock")
    }

    func testTheAppStopsWindowActivitiesNoLongerWantedOnlyOutsideTheComposeLock() throws {
        let files = try isolatedFiles()
        let store = FakeEnforcementStore()
        let center = RecordingWindowCenter()
        center.lockDirectory = files.directoryURL
        var time = at(10, 5)
        let enforcer = IosManagedSettingsEnforcer(
            storeFactory: { store }, authorization: { .approved }, isCapable: true, storedMappings: { [] },
            scheduleStore: { FakeScheduleShieldStore() }, composeLock: files, center: { center }, now: { time }, calendar: { self.calendar }
        )
        let request = IosEnforcementRequest(
            domains: ["control.example", "ruled.example"], mappingIds: [],
            windows: [IosWindowPlan(domain: "ruled.example", segments: [IosWindowSegment(untilEpochMillis: millis(at(11, 0)), periodMinutes: 30, lengthMinutes: 5)])],
            sessionEndEpochMillis: millis(at(11, 0))
        )
        let applied = expectation(description: "applied")
        enforcer.apply(request: request) { _ in applied.fulfill() }
        wait(for: [applied], timeout: 2)
        XCTAssertFalse(center.installed.isEmpty)

        time = at(10, 12)
        let cleared = expectation(description: "cleared")
        enforcer.clear { _ in cleared.fulfill() }
        wait(for: [cleared], timeout: 2)

        XCTAssertFalse(center.stops.isEmpty, "the ended session's boundaries are stopped")
        XCTAssertTrue(center.stopsUnderLock.isEmpty, "never while the compose lock is held")
    }

    func testAFallbackCalledEarlyRegistersNothingMore() throws {
        let fixture = try session()
        evaluate(fixture, own(at(10, 30)), .end, at(10, 29))
        let registrations = fixture.center.registrations.count
        evaluate(fixture, own(at(10, 30, 20)), .end, at(10, 29, 30))
        XCTAssertEqual(fixture.center.registrations.count, registrations)
        XCTAssertEqual(fixture.filter.blocked, ["control.example", "ruled.example"])
    }

    func testAStartAtTheBeginningOfAnIntervalEndingAtItsBoundaryIsAnEvaluation() throws {
        let fixture = try session()
        let registrations = fixture.center.registrations.count
        evaluate(fixture, own(at(10, 30)), .start, at(10, 15, 2))
        XCTAssertEqual(fixture.center.registrations.count, registrations, "its boundary is still covered")
        XCTAssertEqual(fixture.filter.blocked, ["control.example", "ruled.example"])
    }

    // MARK: - Close before open, late, duplicate, stale

    func testARegistrationFailureForTheCloseKeepsTheWebsiteBlocked() throws {
        let files = try isolatedFiles()
        try files.writeManualWindows(manualRecord())
        let fixture = Fixture(files: files, center: RecordingWindowCenter(), filter: FakeManualFilter())
        fixture.center.refusing = [own(at(10, 35))]
        evaluate(fixture, nil, .evaluation, at(10, 5))

        evaluate(fixture, own(at(10, 30)), .end, at(10, 30))

        XCTAssertEqual(fixture.filter.blocked, ["control.example", "ruled.example"])
    }

    func testAnEarlyCloseWithinTheMarginBlocks() throws {
        let fixture = try session()
        evaluate(fixture, own(at(10, 30)), .end, at(10, 30))
        evaluate(fixture, own(at(10, 35)), .end, at(10, 33))
        XCTAssertEqual(fixture.filter.blocked, ["control.example"], "a minute and more before the close it stays open")
        XCTAssertNotNil(fixture.center.installed[own(at(10, 35, 20))], "an early close is covered by its fallback")
        evaluate(fixture, own(at(10, 35, 20)), .end, at(10, 34, 30))
        XCTAssertEqual(fixture.filter.blocked, ["control.example", "ruled.example"])
    }

    func testALateCallbackAppliesTheClockSoALateOpeningAfterItsWindowStaysBlocked() throws {
        let fixture = try session(end: at(11, 30))
        evaluate(fixture, own(at(10, 30)), .end, at(10, 36))
        XCTAssertEqual(fixture.filter.blocked, ["control.example", "ruled.example"])
        XCTAssertTrue(fires(fixture).contains(at(11, 0)))
    }

    func testADuplicateCallbackChangesNothing() throws {
        let fixture = try session()
        evaluate(fixture, own(at(10, 30)), .end, at(10, 30, 1))
        let registrations = fixture.center.registrations.count
        evaluate(fixture, own(at(10, 30)), .end, at(10, 30, 2))
        XCTAssertEqual(fixture.center.registrations.count, registrations)
        XCTAssertEqual(fixture.filter.blocked, ["control.example"])
    }

    func testAStaleCallbackOpensNothingAndKeepsTheBoundariesAhead() throws {
        let fixture = try session()
        evaluate(fixture, own(at(9, 0)), .end, at(10, 12))
        XCTAssertEqual(fixture.filter.blocked, ["control.example", "ruled.example"])
        XCTAssertEqual(fires(fixture), [at(10, 30), at(10, 35), at(11, 0)])
    }

    func testOpeningPosatoAfterAMissedCloseBlocksAgain() throws {
        let fixture = try session()
        evaluate(fixture, own(at(10, 30)), .end, at(10, 30))
        XCTAssertEqual(fixture.filter.blocked, ["control.example"])
        // The close's callback never comes; Posato opens at 10:38.
        evaluate(fixture, nil, .evaluation, at(10, 38))
        XCTAssertEqual(fixture.filter.blocked, ["control.example", "ruled.example"])
    }

    func testAfterTheSessionEndsTheMonitorLeavesTheSessionStoreAlone() throws {
        let fixture = try session()
        let writes = fixture.filter.writes
        evaluate(fixture, own(at(11, 0)), .end, at(11, 0, 5))
        XCTAssertEqual(fixture.filter.writes, writes)
    }

    func testTheActivityLimitKeepsAWebsiteWhoseCloseCannotBeRegisteredBlocked() throws {
        let files = try isolatedFiles()
        try files.writeManualWindows(manualRecord())
        let fixture = Fixture(files: files, center: RecordingWindowCenter(), filter: FakeManualFilter())
        for index in 0 ..< WindowActivities.platformLimit - WindowActivities.reserve - 1 {
            fixture.center.installed[DeviceActivityName("other.\(index)")] = WindowActivities.schedule(fire: at(12, 0), at: at(10, 0), calendar: calendar)
        }
        evaluate(fixture, nil, .evaluation, at(10, 5))
        XCTAssertEqual(fires(fixture), [at(10, 30)], "one slot left: only the opening")
        evaluate(fixture, own(at(10, 30)), .end, at(10, 30))
        XCTAssertEqual(fixture.filter.blocked, ["control.example", "ruled.example"], "no close registered, so it never opens")
    }

    // MARK: - Breaks and windows

    func testABreakCallbackNeverRegistersWindowActivities() throws {
        let fixture = try session()
        let registrations = fixture.center.registrations.count
        ScheduleMonitorEvents.handleBreakCallback(
            store: FakeScheduleShieldStore(), sessionStore: FakeScheduleShieldStore(), files: fixture.files, center: fixture.center,
            now: { self.at(10, 30) }, calendar: calendar
        )
        XCTAssertEqual(fixture.center.registrations.count, registrations)
    }

    func testAManualOnlySessionOpensWithNoScheduleTable() throws {
        let fixture = try session()
        XCTAssertNil(fixture.files.readTable())
        evaluate(fixture, own(at(10, 30)), .end, at(10, 30))
        XCTAssertEqual(fixture.filter.blocked, ["control.example"])
    }

    // MARK: - The schedule's store

    private func scheduledTable(
        schedules: [(id: String, set: String)], ruledSet: String, breakWindow: (Date, Date)? = nil, startMinute: Int = 9 * 60
    ) -> ScheduleMonitorFile {
        ScheduleMonitorFile(
            version: ScheduleMonitor.fileVersion,
            schedules: schedules.map {
                ScheduleMonitorFile.Schedule(
                    id: $0.id, weekdays: 0b1111111, startMinute: startMinute, endMinute: 12 * 60, stoppedDates: [],
                    startTitle: "", startBody: "", setId: $0.set
                )
            },
            running: [], domains: ["ruled.example"], applicationTokens: [],
            notices: ScheduleMonitorFile.Notices(enabled: false, endTitle: "", endBody: ""),
            sets: Set(schedules.map(\.set)).sorted().map { set in
                ScheduleMonitorFile.PauseSet(
                    id: set, domainIndexes: [0], tokenIndexes: [],
                    windows: set == ruledSet ? [ScheduleMonitorFile.SetWindow(domainIndex: 0, period: 30, length: 5)] : nil
                )
            },
            breaks: breakWindow.map { window in
                [ScheduleMonitorFile.Break(
                    id: schedules[0].id, date: "2026-09-28", startEpoch: Int64(window.0.timeIntervalSince1970),
                    endEpoch: Int64(window.1.timeIntervalSince1970), over: false
                )]
            } ?? []
        )
    }

    private func scheduled(_ table: ScheduleMonitorFile) throws -> Fixture {
        let files = try isolatedFiles()
        try files.writeTable(table)
        let fixture = Fixture(files: files, center: RecordingWindowCenter(), filter: FakeManualFilter())
        evaluate(fixture, nil, .evaluation, at(10, 5))
        return fixture
    }

    func testAScheduledPartOpensItsRuledWebsiteOnlyWhileNoUnruledPartHoldsIt() throws {
        let ruledSet = "000000000000400080000000000000b1"
        let unruledSet = "000000000000400080000000000000b2"
        let first = "000000000000400080000000000000a1"
        let second = "000000000000400080000000000000a2"

        let alone = try scheduled(scheduledTable(schedules: [(first, ruledSet)], ruledSet: ruledSet))
        XCTAssertTrue(fires(alone).contains(at(10, 30)) && fires(alone).contains(at(10, 35)))
        let aloneStore = FakeScheduleShieldStore()
        evaluate(alone, nil, .evaluation, at(10, 29, 59), store: aloneStore)
        XCTAssertEqual(aloneStore.applied?.domains, PosatoWebDomains.domains(from: ["ruled.example"]))
        evaluate(alone, own(at(10, 30)), .end, at(10, 30), store: aloneStore)
        XCTAssertEqual(aloneStore.applied?.domains, [])

        let both = try scheduled(scheduledTable(schedules: [(first, ruledSet), (second, unruledSet)], ruledSet: ruledSet))
        let bothStore = FakeScheduleShieldStore()
        evaluate(both, own(at(10, 30)), .end, at(10, 30), store: bothStore)
        XCTAssertEqual(bothStore.applied?.domains, PosatoWebDomains.domains(from: ["ruled.example"]))
    }

    /// Phase 3 review R2: a scheduled part's first start, with Posato closed and no record of what it holds yet,
    /// registers its window boundaries from the composition it applies before it decides what is open.
    private func startPart(_ table: ScheduleMonitorFile, id: String, at time: Date) throws -> (Fixture, FakeScheduleShieldStore) {
        let files = try isolatedFiles()
        try files.writeTable(table)
        let fixture = Fixture(files: files, center: RecordingWindowCenter(), filter: FakeManualFilter())
        let store = FakeScheduleShieldStore()
        ScheduleMonitorEvents.handleIntervalStart(
            activity: ScheduleMonitor.activityName(scheduleId: id), store: store, sessionStore: FakeScheduleShieldStore(), files: files,
            poster: FakeSchedulePoster(), center: fixture.center, now: { time }, calendar: calendar
        )
        return (fixture, store)
    }

    func testAFirstScheduledStartInsideAWindowOpensItsRuledWebsiteAtTheStartAndRegistersItsClose() throws {
        let ruledSet = "000000000000400080000000000000b1"
        let first = "000000000000400080000000000000a1"
        let (fixture, store) = try startPart(
            scheduledTable(schedules: [(first, ruledSet)], ruledSet: ruledSet, startMinute: 10 * 60 + 32), id: first, at: at(10, 32)
        )
        XCTAssertTrue(fires(fixture).contains(at(10, 35)), "the close is registered before the website opens")
        XCTAssertEqual(store.applied?.domains, [], "the part starts inside its window, so its ruled website is open at once")
        evaluate(fixture, own(at(10, 35)), .end, at(10, 35), store: store)
        XCTAssertEqual(store.applied?.domains, PosatoWebDomains.domains(from: ["ruled.example"]))
    }

    func testAnEarlyFirstScheduledStartRegistersTheOpeningAtTheStartAndKeepsTheWebsiteBlockedUntilThen() throws {
        let ruledSet = "000000000000400080000000000000b1"
        let first = "000000000000400080000000000000a1"
        let (fixture, store) = try startPart(
            scheduledTable(schedules: [(first, ruledSet)], ruledSet: ruledSet, startMinute: 10 * 60 + 30), id: first, at: at(10, 29, 58)
        )
        XCTAssertEqual(store.applied?.domains, PosatoWebDomains.domains(from: ["ruled.example"]), "never open before the opening")
        XCTAssertTrue(fires(fixture).contains(at(10, 30)) && fires(fixture).contains(at(10, 35)))
        evaluate(fixture, own(at(10, 30)), .end, at(10, 30), store: store)
        XCTAssertEqual(store.applied?.domains, [])
    }

    func testAPartOnABreakIsReleasedAndAWindowCallbackDoesNotBlockItAgain() throws {
        let ruledSet = "000000000000400080000000000000b1"
        let first = "000000000000400080000000000000a1"
        let fixture = try scheduled(scheduledTable(schedules: [(first, ruledSet)], ruledSet: ruledSet, breakWindow: (at(10, 25), at(10, 40))))
        fixture.center.installed[ScheduleMonitor.breakActivity] = ScheduleBreakActivity.schedule(deadline: at(10, 40), calendar: calendar)
        let store = FakeScheduleShieldStore()
        evaluate(fixture, own(at(10, 35)), .end, at(10, 35), store: store)
        XCTAssertNil(store.applied, "the part on its break composes nothing, so the window's close blocks nothing")
    }

    // MARK: - Plans the app hands over

    func testAnInvalidPlanKeepsItsWebsiteBlocked() throws {
        let end = at(11, 0)
        let invalid: [[WebsiteWindowPlan.Segment]] = [
            [.init(until: millis(at(10, 50)), period: 30, length: 5), .init(until: millis(at(10, 50)), period: 30, length: 5)],
            [.init(until: millis(at(11, 1)), period: 30, length: 5)],
            [.init(until: millis(end), period: 45, length: 5)],
            [.init(until: millis(end), period: 30, length: 0)],
            (1 ... 23).map { .init(until: millis(at(10, 0).addingTimeInterval(TimeInterval($0 * 60))), period: 30, length: 5) },
        ]
        for segments in invalid {
            let record = ManualWindowRecord(
                version: ManualWindowRecord.currentVersion, endEpochMillis: millis(end), held: ["ruled.example"],
                plans: [WebsiteWindowPlan(domain: "ruled.example", segments: segments)]
            )
            XCTAssertTrue(WindowEvaluation.openManual(record, at: at(10, 31), covered: { _ in true }, lead: 0, calendar: calendar).isEmpty)
        }
    }

    func testTheSharedVectorsMatchTheWindowClock() throws {
        // The one cross-target vector file every implementation reads (ADR 0006 amendment).
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("shared/src/commonTest/resources/website-window-vectors.json")
        let vectors = try JSONDecoder().decode(WindowVectors.self, from: Data(contentsOf: url))
        XCTAssertFalse(vectors.cases.isEmpty)
        for vector in vectors.cases {
            var zoned = Calendar(identifier: .gregorian)
            zoned.timeZone = try XCTUnwrap(TimeZone(identifier: vector.zone))
            let plan = WebsiteWindowPlan(
                domain: "vector.example",
                segments: vector.plan.map { .init(until: $0.until, period: $0.period, length: $0.length) }
            )
            let time = Date(timeIntervalSince1970: TimeInterval(vector.at) / 1000)
            XCTAssertEqual(plan.isOpen(at: time, calendar: zoned), vector.open, vector.name)
            XCTAssertEqual(plan.nextBoundary(after: time, calendar: zoned).map(millis), vector.nextBoundary, vector.name)
        }
    }

    func testTheAppRegistersTheCloseBeforeItOpensAWebsiteAndCountsTheOpenWebsiteAsHeld() throws {
        let files = try isolatedFiles()
        let store = FakeEnforcementStore()
        let center = RecordingWindowCenter()
        let enforcer = IosManagedSettingsEnforcer(
            storeFactory: { store },
            authorization: { .approved },
            isCapable: true,
            storedMappings: { [] },
            scheduleStore: { FakeScheduleShieldStore() },
            composeLock: files,
            center: { center },
            now: { self.at(10, 31) },
            calendar: { self.calendar }
        )
        let request = IosEnforcementRequest(
            domains: ["control.example", "ruled.example"], mappingIds: [],
            windows: [IosWindowPlan(domain: "ruled.example", segments: [IosWindowSegment(untilEpochMillis: millis(at(11, 0)), periodMinutes: 30, lengthMinutes: 5)])],
            sessionEndEpochMillis: millis(at(11, 0))
        )
        let applied = expectation(description: "applied")
        enforcer.apply(request: request) { outcome in
            XCTAssertEqual(outcome, .applied)
            applied.fulfill()
        }
        wait(for: [applied], timeout: 2)

        XCTAssertNotNil(center.installed[WindowActivities.name(firingAt: at(10, 35))])
        XCTAssertEqual(store.hosts, ["control.example", "www.control.example"])
        XCTAssertEqual(files.readManualWindows()?.held, ["control.example", "ruled.example"])
    }
}

/// The manual session's store as the enforcer writes it.
final class FakeEnforcementStore: IosEnforcementSettingsStore {
    var blockedWebFilter: WebContentSettings.FilterPolicy?
    var shieldedApplications: Set<ApplicationToken>?

    var hosts: [String] {
        guard case let .specific(domains) = blockedWebFilter else { return [] }
        return domains.compactMap(\.domain).sorted()
    }

    func clearAll() {
        blockedWebFilter = nil
        shieldedApplications = nil
    }
}

private struct WindowVectors: Decodable {
    struct Segment: Decodable {
        let until: Int64
        let period: Int
        let length: Int
    }

    struct Case: Decodable {
        let name: String
        let zone: String
        let at: Int64
        let plan: [Segment]
        let open: Bool
        let nextBoundary: Int64?
    }

    let cases: [Case]
}

/// Records every registration with its interval, as Device Activity sees it.
final class RecordingWindowCenter: ScheduleActivityCenter {
    var installed: [DeviceActivityName: DeviceActivitySchedule] = [:]
    var touched: [DeviceActivityName] = []
    var stops: [DeviceActivityName] = []
    /// Set to the store's directory to record stops made while the compose lock is held: a stop delivers the
    /// activity's end, whose callback waits for that lock (row 2 stage A, 12:35:00 to 12:35:31).
    var lockDirectory: URL?
    var stopsUnderLock: [DeviceActivityName] = []
    var registrations: [(activity: DeviceActivityName, schedule: DeviceActivitySchedule)] = []
    var refusing: Set<DeviceActivityName> = []

    var monitoredActivities: [DeviceActivityName] {
        Array(installed.keys)
    }

    func monitoredSchedule(for activity: DeviceActivityName) -> DeviceActivitySchedule? {
        installed[activity]
    }

    func startMonitoringSchedule(_ activity: DeviceActivityName, during schedule: DeviceActivitySchedule) throws {
        touched.append(activity)
        if refusing.contains(activity) {
            throw CocoaError(.featureUnsupported)
        }
        registrations.append((activity, schedule))
        installed[activity] = schedule
    }

    func stopMonitoringSchedules(_ activities: [DeviceActivityName]) {
        touched.append(contentsOf: activities)
        stops.append(contentsOf: activities)
        if let lockDirectory {
            let descriptor = open(lockDirectory.appendingPathComponent("compose.lock").path, O_CREAT | O_RDWR, 0o600)
            if descriptor >= 0 {
                if flock(descriptor, LOCK_EX | LOCK_NB) != 0 {
                    stopsUnderLock.append(contentsOf: activities)
                } else {
                    flock(descriptor, LOCK_UN)
                }
                close(descriptor)
            }
        }
        activities.forEach { installed[$0] = nil }
    }
}

/// TARGETS-013 row 2 stage A regression (2026-11-01): the one re-armed boundary activity re-registered itself from
/// its own end callback at the opening; the close it registered then (its 15-minute interval already begun) was not
/// monitored, and the website stayed open past its close. Every boundary is its own activity, registered ahead.
final class WebsiteWindowActivityRegressionTests: XCTestCase {
    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Warsaw")!
        return calendar
    }

    private func at(_ hour: Int, _ minute: Int, _ second: Double = 0) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 9, day: 28, hour: hour, minute: minute))!.addingTimeInterval(second)
    }

    private func millis(_ date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1000).rounded())
    }

    private func components(_ date: Date) -> DateComponents {
        calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
    }

    private func fires(_ center: RecordingWindowCenter, _ instant: Date) -> [DeviceActivityName] {
        center.monitoredActivities.filter { WindowActivities.fireInstant(of: $0) == instant }
    }

    func testTheOpeningCallbackNeverTouchesItsOwnActivityAndTheCloseIsMonitoredAheadByAnother() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        let files = ScheduleMonitorFileStore(directoryURL: directory)
        try files.writeManualWindows(ManualWindowRecord(
            version: ManualWindowRecord.currentVersion,
            endEpochMillis: millis(at(11, 0)),
            held: ["control.example", "ruled.example"],
            plans: [WebsiteWindowPlan(domain: "ruled.example", segments: [.init(until: millis(at(11, 0)), period: 30, length: 5)])]
        ))
        let center = RecordingWindowCenter()
        let filter = FakeManualFilter()
        func evaluate(_ activity: DeviceActivityName?, _ callback: MonitorBoundary.Callback, _ time: Date) {
            ScheduleMonitorEvents.handleWindowCallback(
                activity: activity, callback: callback, store: FakeScheduleShieldStore(), sessionStore: FakeScheduleShieldStore(),
                manualFilter: filter, files: files, center: center, now: { time }, calendar: calendar
            )
        }

        // The app evaluates at 10:05: the opening and the close are registered ahead, each on its own.
        evaluate(nil, .evaluation, at(10, 5))
        let opening = try XCTUnwrap(fires(center, at(10, 30)).first, "the opening at 10:30 is monitored")
        XCTAssertFalse(fires(center, at(10, 35)).isEmpty, "the close at 10:35 is monitored before the website opens")
        XCTAssertEqual(filter.blocked, ["control.example", "ruled.example"])

        // The opening's activity calls back at the opening.
        center.touched = []
        evaluate(opening, .end, at(10, 30, 0.479))
        XCTAssertFalse(center.touched.contains(opening), "a callback never restarts or stops its own activity")
        XCTAssertEqual(filter.blocked, ["control.example"])
        let close = fires(center, at(10, 35)).filter { $0 != opening }
        XCTAssertFalse(close.isEmpty, "another activity still fires at the close")

        // No registration starts its interval in the past.
        for registration in center.registrations {
            let start = try XCTUnwrap(calendar.date(from: registration.schedule.intervalStart))
            XCTAssertGreaterThanOrEqual(start, at(10, 5), "\(registration.activity.rawValue) starts in the past")
        }
    }
}
