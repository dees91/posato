import DeviceActivity
import Foundation
import ManagedSettings
import PosatoShared
import XCTest
@testable import Posato

final class FakeExpirySettingsStore: SuspendedExpirySettingsStore {
    var clears = 0

    func clearOwnedSettings() {
        clears += 1
    }
}

final class FakeExpiryMonitoring: SuspendedExpiryMonitoring {
    struct StartedSchedule {
        let activity: DeviceActivityName
        let schedule: DeviceActivitySchedule
    }

    var started: [StartedSchedule] = []
    var stopped: [[DeviceActivityName]] = []
    var startError: Error?
    private(set) var activities: [DeviceActivityName] = []

    func startMonitoring(_ activity: DeviceActivityName, during schedule: DeviceActivitySchedule) throws {
        if let startError {
            throw startError
        }
        started.append(StartedSchedule(activity: activity, schedule: schedule))
        if !activities.contains(activity) {
            activities.append(activity)
        }
    }

    func stopMonitoring(_ activities: [DeviceActivityName]) {
        stopped.append(activities)
        self.activities.removeAll { activities.contains($0) }
    }
}

private struct FakeMonitoringError: Error {}

final class SuspendedExpiryTests: XCTestCase {
    // MARK: - Interval-end clear

    func testForeignActivityClearsNothingAndWritesNoRecord() throws {
        let store = FakeExpirySettingsStore()
        let records = try isolatedRecordStore()
        try writePending(records, sessionId: "session")

        SuspendedExpiryClear.handleIntervalEnd(
            activity: DeviceActivityName("foreign.activity"),
            store: store,
            records: records
        )

        XCTAssertEqual(store.clears, 0)
        XCTAssertNotNil(records.readPending())
        XCTAssertNil(records.readCleared())
    }

    func testMatchingActivityClearsOnlyTheInjectedStore() throws {
        let owned = FakeExpirySettingsStore()
        let foreign = FakeExpirySettingsStore()
        let records = try isolatedRecordStore()
        try writePending(records, sessionId: "session")

        handleIntervalEnd(store: owned, records: records, now: 1_700_003_600)

        XCTAssertEqual(owned.clears, 1)
        XCTAssertEqual(foreign.clears, 0)
    }

    func testMatchingActivityWritesClearedRecordAndConsumesPending() throws {
        let store = FakeExpirySettingsStore()
        let records = try isolatedRecordStore()
        try writePending(records, sessionId: "session")

        handleIntervalEnd(store: store, records: records, now: 1_700_003_600)

        XCTAssertEqual(store.clears, 1)
        XCTAssertNil(records.readPending())
        let cleared = try XCTUnwrap(records.readCleared())
        XCTAssertEqual(cleared.version, 1)
        XCTAssertEqual(cleared.sessionId, "session")
        XCTAssertEqual(cleared.clearedAt, 1_700_003_600, accuracy: 0.001)
    }

    func testMatchingActivityWithoutPendingClearsNothing() throws {
        let store = FakeExpirySettingsStore()
        let records = try isolatedRecordStore()

        handleIntervalEnd(store: store, records: records, now: 1_700_003_600)

        XCTAssertEqual(store.clears, 0)
        XCTAssertNil(records.readCleared())
    }

    // MARK: - Early interval end (IOS-006)

    func testCallbackInsideTheWindowClearsNothingAndKeepsPending() throws {
        let store = FakeExpirySettingsStore()
        let records = try isolatedRecordStore()
        try writePending(records, sessionId: "session")

        handleIntervalEnd(store: store, records: records, now: 1_700_001_000)

        XCTAssertEqual(store.clears, 0)
        XCTAssertEqual(records.readPending()?.sessionId, "session")
        XCTAssertNil(records.readCleared())
    }

    func testCallbackWithinTheLastMinuteOrAfterTheEndClearsAndRecords() throws {
        for now: Int64 in [1_700_003_541, 1_700_003_600, 1_700_003_840] {
            let store = FakeExpirySettingsStore()
            let records = try isolatedRecordStore()
            try writePending(records, sessionId: "session")

            handleIntervalEnd(store: store, records: records, now: now)

            XCTAssertEqual(store.clears, 1, "now \(now)")
            XCTAssertNil(records.readPending(), "now \(now)")
            XCTAssertEqual(records.readCleared()?.sessionId, "session", "now \(now)")
        }
    }

    func testLegacyPendingFromVersionOneClearsAtAnyTime() throws {
        let store = FakeExpirySettingsStore()
        let records = try isolatedRecordStore()
        try writeRawPending(records, json: "{\"version\":1,\"sessionId\":\"session\"}")
        XCTAssertEqual(records.readPending()?.sessionId, "session")

        handleIntervalEnd(store: store, records: records, now: 1_700_001_000)

        XCTAssertEqual(store.clears, 1)
        XCTAssertNil(records.readPending())
        XCTAssertEqual(records.readCleared()?.sessionId, "session")
    }

    func testUnreadablePendingClearsWithoutRecord() throws {
        let unreadable = [
            "not-json",
            "{\"version\":2,\"sessionId\":\"session\"}",
            "{\"version\":3,\"sessionId\":\"session\",\"endEpochSeconds\":1700003600}",
        ]
        for json in unreadable {
            let store = FakeExpirySettingsStore()
            let records = try isolatedRecordStore()
            try writeRawPending(records, json: json)

            handleIntervalEnd(store: store, records: records, now: 1_700_001_000)

            XCTAssertEqual(store.clears, 1, json)
            XCTAssertNil(records.readCleared(), json)
        }
    }

    func testTimeZoneChangesNeverSkipTheRealEnd() throws {
        let end: Int64 = 1_700_003_600
        let cases: [(offset: Int, earlyCallback: Int64, realCallback: Int64)] = [
            (-7_200, end - 600, end),
            (7_200, end - 7_200 - 600, end - 7_200),
        ]
        for testCase in cases {
            var moved = Calendar(identifier: .gregorian)
            moved.timeZone = try XCTUnwrap(TimeZone(secondsFromGMT: testCase.offset))

            let early = FakeExpirySettingsStore()
            let earlyRecords = try isolatedRecordStore()
            try writePending(earlyRecords, sessionId: "session", end: end)
            handleIntervalEnd(store: early, records: earlyRecords, now: testCase.earlyCallback, calendar: moved)
            XCTAssertEqual(early.clears, 0, "offset \(testCase.offset)")

            let real = FakeExpirySettingsStore()
            let realRecords = try isolatedRecordStore()
            try writePending(realRecords, sessionId: "session", end: end)
            handleIntervalEnd(store: real, records: realRecords, now: testCase.realCallback, calendar: moved)
            XCTAssertEqual(real.clears, 1, "offset \(testCase.offset)")
            XCTAssertEqual(realRecords.readCleared()?.sessionId, "session", "offset \(testCase.offset)")
        }
    }

    func testCalendarChangeNeverSkipsTheRealEnd() throws {
        var buddhist = Calendar(identifier: .buddhist)
        buddhist.timeZone = try XCTUnwrap(TimeZone(secondsFromGMT: 0))

        let resolvedInThePast = FakeExpirySettingsStore()
        let pastRecords = try isolatedRecordStore()
        try writePending(pastRecords, sessionId: "session")
        handleIntervalEnd(store: resolvedInThePast, records: pastRecords, now: 1_700_001_000, calendar: buddhist)
        XCTAssertEqual(resolvedInThePast.clears, 1)

        let resolvedInTheFuture = FakeExpirySettingsStore()
        let futureRecords = try isolatedRecordStore()
        try writePending(futureRecords, sessionId: "session", calendar: buddhist)
        handleIntervalEnd(store: resolvedInTheFuture, records: futureRecords, now: 1_700_001_000)
        XCTAssertEqual(resolvedInTheFuture.clears, 0)
        handleIntervalEnd(store: resolvedInTheFuture, records: futureRecords, now: 1_700_003_600)
        XCTAssertEqual(resolvedInTheFuture.clears, 1)
    }

    func testReplacementCallbackInsideTheWindowKeepsTheReplacementScheduled() throws {
        let monitoring = FakeExpiryMonitoring()
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: monitoring, records: { records })
        XCTAssertEqual(
            try schedule(sessionId: "session", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler),
            .scheduled
        )
        XCTAssertEqual(try cancel(scheduler: scheduler), .cancelled)
        XCTAssertEqual(
            try schedule(sessionId: "session", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler),
            .scheduled
        )

        let store = FakeExpirySettingsStore()
        handleIntervalEnd(store: store, records: records, now: 1_700_001_000)

        XCTAssertEqual(store.clears, 0)
        XCTAssertEqual(try reconcile(sessionId: "session", scheduler: scheduler), .unknown)
        XCTAssertTrue(try isScheduled(sessionId: "session", scheduler: scheduler))
    }

    func testSchedulerAndClearShareOneActivityName() throws {
        let monitoring = FakeExpiryMonitoring()
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: monitoring, records: { records })

        XCTAssertEqual(
            try schedule(sessionId: "session", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler),
            .scheduled
        )
        let started = try XCTUnwrap(monitoring.started.first)
        XCTAssertEqual(started.activity, SuspendedExpiryActivity.name)

        let store = FakeExpirySettingsStore()
        SuspendedExpiryClear.handleIntervalEnd(activity: started.activity, store: store, records: records)
        XCTAssertEqual(store.clears, 1)
        XCTAssertEqual(try XCTUnwrap(records.readCleared()).sessionId, "session")
    }

    // MARK: - Record version handling

    func testClearedReadRejectsNewerVersionCorruptionAndAbsence() throws {
        let records = try isolatedRecordStore()

        XCTAssertNil(records.readCleared())

        try records.writeCleared(sessionId: "session", clearedAt: 1_700_000_000)
        XCTAssertNotNil(records.readCleared())

        let clearedURL = try clearedRecordURL(records)
        try Data("{\"version\":999,\"sessionId\":\"session\",\"clearedAt\":1700000000}".utf8).write(to: clearedURL)
        XCTAssertNil(records.readCleared())

        try Data("not-json".utf8).write(to: clearedURL)
        XCTAssertNil(records.readCleared())

        XCTAssertNil(records.readPending())
    }

    func testRecordStoreKeepsItsOwnDirectory() throws {
        let records = try isolatedRecordStore()
        try writePending(records, sessionId: "session")
        try records.writeCleared(sessionId: "session", clearedAt: 1_700_000_000)

        XCTAssertTrue(records.directoryURL.lastPathComponent == "SuspendedExpiry")
        let names = try FileManager.default.contentsOfDirectory(atPath: records.directoryURL.path).sorted()
        XCTAssertEqual(names.count, 2)
        XCTAssertTrue(names.contains("pending-v1.json"))
        XCTAssertEqual(names.filter { $0.hasPrefix("cleared-") && $0.hasSuffix(".json") }.count, 1)
    }

    // MARK: - Schedule validation

    func testIncapableBuildSchedulesNothing() throws {
        let monitoring = FakeExpiryMonitoring()
        let records = try isolatedRecordStore()
        let scheduler = SuspendedExpiryScheduler(
            monitoring: monitoring,
            authorization: { .approved },
            isCapable: false,
            records: { records }
        )

        XCTAssertEqual(
            try schedule(sessionId: "session", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler),
            .unavailable
        )
        XCTAssertTrue(monitoring.started.isEmpty)
        XCTAssertNil(records.readPending())
    }

    func testMissingRecordStoreReportsUnavailable() throws {
        let monitoring = FakeExpiryMonitoring()
        let scheduler = SuspendedExpiryScheduler(
            monitoring: monitoring,
            authorization: { .approved },
            isCapable: true,
            records: { nil }
        )

        XCTAssertEqual(
            try schedule(sessionId: "session", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler),
            .unavailable
        )
        XCTAssertTrue(monitoring.started.isEmpty)
    }

    func testUnauthorizedScheduleWritesNothing() throws {
        let expectations: [EnforcementAuthorization] = [.notDetermined, .denied, .restricted]

        for authorization in expectations {
            let monitoring = FakeExpiryMonitoring()
            let records = try isolatedRecordStore()
            let scheduler = capableScheduler(monitoring: monitoring, authorization: authorization, records: { records })

            XCTAssertEqual(
                try schedule(sessionId: "session", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler),
                .authorizationRequired,
                "authorization \(authorization)"
            )
            XCTAssertTrue(monitoring.started.isEmpty, "authorization \(authorization)")
            XCTAssertNil(records.readPending(), "authorization \(authorization)")
        }
    }

    func testShortOrReversedIntervalStartsNothing() throws {
        let cases: [(Int64, Int64)] = [
            (1_700_003_600, 1_700_000_000),
            (1_700_000_000, 1_700_000_000),
            (1_700_000_000, 1_700_000_899),
        ]

        for (start, end) in cases {
            let monitoring = FakeExpiryMonitoring()
            let records = try isolatedRecordStore()
            let scheduler = capableScheduler(monitoring: monitoring, records: { records })

            XCTAssertEqual(
                try schedule(sessionId: "session", start: start, end: end, scheduler: scheduler),
                .belowPlatformMinimum,
                "interval \(start)-\(end)"
            )
            XCTAssertTrue(monitoring.started.isEmpty, "interval \(start)-\(end)")
            XCTAssertNil(records.readPending(), "interval \(start)-\(end)")
        }
    }

    func testEmptySessionIdRefusesBeforeAnyWrite() throws {
        let monitoring = FakeExpiryMonitoring()
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: monitoring, records: { records })

        XCTAssertEqual(
            try schedule(sessionId: "", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler),
            .platformFailure
        )
        XCTAssertTrue(monitoring.started.isEmpty)
        XCTAssertNil(records.readPending())
    }

    func testMonitoringThrowRemovesPendingAndReportsPlatformFailure() throws {
        let monitoring = FakeExpiryMonitoring()
        monitoring.startError = FakeMonitoringError()
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: monitoring, records: { records })

        XCTAssertEqual(
            try schedule(sessionId: "session", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler),
            .platformFailure
        )
        XCTAssertTrue(monitoring.started.isEmpty)
        XCTAssertNil(records.readPending())
    }

    func testSuccessfulScheduleStartsOneNonRepeatingMonitoringSchedule() throws {
        let monitoring = FakeExpiryMonitoring()
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: monitoring, records: { records })

        XCTAssertEqual(
            try schedule(sessionId: "session", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler),
            .scheduled
        )
        XCTAssertEqual(monitoring.started.count, 1)
        XCTAssertEqual(monitoring.started.first?.activity, SuspendedExpiryActivity.name)
        // 1700000000 is 22:13 UTC and 1700003600 is 23:13 UTC on 2023-11-14.
        // Full date components keep a 24-hour session's identical clock times
        // apart and remove the midnight-wrap ambiguity.
        XCTAssertEqual(
            monitoring.started.first?.schedule,
            DeviceActivitySchedule(
                intervalStart: DateComponents(year: 2023, month: 11, day: 14, hour: 22, minute: 13),
                intervalEnd: DateComponents(year: 2023, month: 11, day: 14, hour: 23, minute: 13),
                repeats: false
            )
        )
        XCTAssertEqual(records.readPending()?.sessionId, "session")
    }

    func testScheduleWritesThePendingIntervalEnd() throws {
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: FakeExpiryMonitoring(), records: { records })

        XCTAssertEqual(
            try schedule(sessionId: "session", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler),
            .scheduled
        )

        let pending = try XCTUnwrap(records.readPending())
        XCTAssertEqual(pending.version, 2)
        XCTAssertEqual(pending.intervalEnd, DateComponents(year: 2023, month: 11, day: 14, hour: 23, minute: 13))
        XCTAssertEqual(pending.endEpochSeconds, 1_700_003_600)
    }

    func testIsScheduledOnlyForTheMatchingPendingWithActiveMonitoring() throws {
        let monitoring = FakeExpiryMonitoring()
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: monitoring, records: { records })
        XCTAssertFalse(try isScheduled(sessionId: "session", scheduler: scheduler))

        XCTAssertEqual(
            try schedule(sessionId: "session", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler),
            .scheduled
        )
        XCTAssertTrue(try isScheduled(sessionId: "session", scheduler: scheduler))
        XCTAssertFalse(try isScheduled(sessionId: "other-session", scheduler: scheduler))

        monitoring.stopMonitoring([SuspendedExpiryActivity.name])
        XCTAssertFalse(try isScheduled(sessionId: "session", scheduler: scheduler))

        XCTAssertEqual(
            try schedule(sessionId: "session", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler),
            .scheduled
        )
        XCTAssertEqual(try cancel(scheduler: scheduler), .cancelled)
        XCTAssertFalse(try isScheduled(sessionId: "session", scheduler: scheduler))
    }

    func testIsScheduledAdoptsALegacyPendingWithActiveMonitoring() throws {
        let monitoring = FakeExpiryMonitoring()
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: monitoring, records: { records })
        XCTAssertEqual(
            try schedule(sessionId: "session", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler),
            .scheduled
        )
        try writeRawPending(records, json: "{\"version\":1,\"sessionId\":\"session\"}")

        XCTAssertTrue(try isScheduled(sessionId: "session", scheduler: scheduler))
    }

    // MARK: - Stop and reconciliation

    func testStopIsIdempotentAndLeavesNoMonitoringBehind() throws {
        let monitoring = FakeExpiryMonitoring()
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: monitoring, records: { records })

        XCTAssertEqual(try cancel(scheduler: scheduler), .cancelled)
        XCTAssertEqual(
            try schedule(sessionId: "session", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler),
            .scheduled
        )
        XCTAssertEqual(try cancel(scheduler: scheduler), .cancelled)
        XCTAssertEqual(try cancel(scheduler: scheduler), .cancelled)

        XCTAssertEqual(monitoring.stopped.count, 3)
        XCTAssertTrue(monitoring.stopped.allSatisfy { $0 == [SuspendedExpiryActivity.name] })
        XCTAssertNil(records.readPending())
    }

    func testReconciliationReadsExpiredOnlyOnExactVersionMatch() throws {
        let monitoring = FakeExpiryMonitoring()
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: monitoring, records: { records })

        XCTAssertEqual(try reconcile(sessionId: "session", scheduler: scheduler), .unknown)

        try records.writeCleared(sessionId: "session", clearedAt: 1_700_003_600)
        XCTAssertEqual(try reconcile(sessionId: "session", scheduler: scheduler), .expired)

        let clearedURL = try clearedRecordURL(records)
        try Data("{\"version\":999,\"sessionId\":\"session\",\"clearedAt\":1700003600}".utf8).write(to: clearedURL)
        XCTAssertEqual(try reconcile(sessionId: "session", scheduler: scheduler), .unknown)
    }

    func testReconciliationIgnoresAnotherSessionsClearAndKeepsItsOwn() throws {
        let monitoring = FakeExpiryMonitoring()
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: monitoring, records: { records })

        try records.writeCleared(sessionId: "other-session", clearedAt: 1_700_003_600)
        XCTAssertEqual(try reconcile(sessionId: "session", scheduler: scheduler), .unknown)
        XCTAssertNotNil(records.readCleared())

        try records.writeCleared(sessionId: "session", clearedAt: 1_700_003_600)
        XCTAssertEqual(try reconcile(sessionId: "session", scheduler: scheduler), .expired)
        XCTAssertNotNil(records.readCleared())
        XCTAssertEqual(try reconcile(sessionId: "session", scheduler: scheduler), .expired)
    }

    func testAcknowledgeConsumesOnlyTheMatchingClear() throws {
        let monitoring = FakeExpiryMonitoring()
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: monitoring, records: { records })

        XCTAssertFalse(try acknowledge(sessionId: "session", scheduler: scheduler))

        try records.writeCleared(sessionId: "other-session", clearedAt: 1_700_003_600)
        XCTAssertFalse(try acknowledge(sessionId: "session", scheduler: scheduler))
        XCTAssertNotNil(records.readCleared())

        try records.writeCleared(sessionId: "session", clearedAt: 1_700_003_600)
        XCTAssertTrue(try acknowledge(sessionId: "session", scheduler: scheduler))
        XCTAssertNotNil(records.readCleared(sessionId: "other-session"))
        XCTAssertNil(records.readCleared(sessionId: "session"))
        XCTAssertEqual(try reconcile(sessionId: "session", scheduler: scheduler), .unknown)
        XCTAssertFalse(try acknowledge(sessionId: "session", scheduler: scheduler))
    }

    func testSchedulePreservesAForeignClearedRecord() throws {
        let monitoring = FakeExpiryMonitoring()
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: monitoring, records: { records })
        try records.writeCleared(sessionId: "earlier-session", clearedAt: 1_699_000_000)

        XCTAssertEqual(
            try schedule(sessionId: "session", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler),
            .scheduled
        )
        // The new schedule must never silently drop the superseded signal:
        // only Kotlin's persist-then-acknowledge consumes it.
        XCTAssertEqual(records.readCleared()?.sessionId, "earlier-session")
        XCTAssertEqual(records.readPending()?.sessionId, "session")
    }

    func testDisplacementSurfacesForeignClearWithoutConsuming() throws {
        let monitoring = FakeExpiryMonitoring()
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: monitoring, records: { records })

        XCTAssertNil(displaced(currentSessionId: "session", scheduler: scheduler))

        try records.writeCleared(sessionId: "earlier-session", clearedAt: 1_699_000_000)
        XCTAssertEqual(displaced(currentSessionId: "session", scheduler: scheduler), "earlier-session")
        XCTAssertNotNil(records.readCleared())
        XCTAssertEqual(displaced(currentSessionId: "earlier-session", scheduler: scheduler), "earlier-session")
        XCTAssertNil(displaced(currentSessionId: "", scheduler: scheduler))

        XCTAssertTrue(try acknowledge(sessionId: "earlier-session", scheduler: scheduler))
        XCTAssertNil(displaced(currentSessionId: "session", scheduler: scheduler))
    }

    func testDisplacementDistinguishesMissingFromUnreadableRecords() throws {
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: FakeExpiryMonitoring(), records: { records })
        XCTAssertEqual(displacementOutcome(scheduler), .absent)
        try FileManager.default.createDirectory(at: records.directoryURL, withIntermediateDirectories: true)
        let url = records.directoryURL.appendingPathComponent("cleared-v1.json")
        for bytes in [Data("invalid".utf8), Data(repeating: 65, count: 4097)] {
            try bytes.write(to: url)
            XCTAssertEqual(displacementOutcome(scheduler), .failed)
        }
        try FileManager.default.removeItem(at: url)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: false)
        XCTAssertEqual(displacementOutcome(scheduler), .failed)
        let unavailable = capableScheduler(monitoring: FakeExpiryMonitoring(), records: { nil })
        XCTAssertEqual(displacementOutcome(unavailable), .failed)
    }

    private func displacementOutcome(_ scheduler: SuspendedExpiryScheduler) -> ExpiryDisplacementOutcome? {
        var result: ExpiryDisplacementOutcome?
        scheduler.displacedClearedSessionId(currentSessionId: "session") { result = $0.outcome }
        return result
    }

    func testLatePreviousExpiryAndReplacementExpirySurviveRestartIndependently() throws {
        let records = try isolatedRecordStore()
        let scheduler = capableScheduler(monitoring: FakeExpiryMonitoring(), records: { records })
        try writePending(records, sessionId: "earlier-session")
        let captured = try XCTUnwrap(records.readPending())
        XCTAssertNil(displaced(currentSessionId: "session", scheduler: scheduler))
        XCTAssertEqual(try schedule(sessionId: "session", start: 1_700_000_000, end: 1_700_003_600, scheduler: scheduler), .scheduled)
        try records.writeCleared(sessionId: captured.sessionId, clearedAt: 1_700_000_000)
        SuspendedExpiryClear.handleIntervalEnd(activity: SuspendedExpiryActivity.name, store: FakeExpirySettingsStore(), records: records)
        let reopened = SuspendedExpiryRecordStore(directoryURL: records.directoryURL)
        let recovered = capableScheduler(monitoring: FakeExpiryMonitoring(), records: { reopened })
        XCTAssertEqual(try reconcile(sessionId: "earlier-session", scheduler: recovered), .expired)
        XCTAssertEqual(try reconcile(sessionId: "session", scheduler: recovered), .expired)
        XCTAssertEqual(displaced(currentSessionId: "session", scheduler: recovered), "session")
        XCTAssertTrue(try acknowledge(sessionId: "session", scheduler: recovered))
        XCTAssertEqual(try reconcile(sessionId: "earlier-session", scheduler: recovered), .expired)
        XCTAssertTrue(try acknowledge(sessionId: "earlier-session", scheduler: recovered))
        XCTAssertNil(displaced(currentSessionId: "session", scheduler: recovered))
    }

    func testLegacyClearedRecordSurvivesNewExpiryAndIsIndependentlyAcknowledged() throws {
        let records = try isolatedRecordStore()
        try FileManager.default.createDirectory(at: records.directoryURL, withIntermediateDirectories: true)
        let legacy = SuspendedExpiryClearedRecord(version: 1, sessionId: "legacy-session", clearedAt: 1_700_000_000)
        try JSONEncoder().encode(legacy).write(to: records.directoryURL.appendingPathComponent("cleared-v1.json"))
        try records.writeCleared(sessionId: "session", clearedAt: 1_700_003_600)
        let scheduler = capableScheduler(monitoring: FakeExpiryMonitoring(), records: { records })
        XCTAssertEqual(try reconcile(sessionId: "legacy-session", scheduler: scheduler), .expired)
        XCTAssertTrue(try acknowledge(sessionId: "legacy-session", scheduler: scheduler))
        XCTAssertEqual(try reconcile(sessionId: "session", scheduler: scheduler), .expired)
    }

    // MARK: - Helpers

    private static func utcCalendar() -> Calendar {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)!
        return utc
    }

    private func handleIntervalEnd(
        store: FakeExpirySettingsStore,
        records: SuspendedExpiryRecordStore,
        now: Int64,
        calendar: Calendar = SuspendedExpiryTests.utcCalendar()
    ) {
        SuspendedExpiryClear.handleIntervalEnd(
            activity: SuspendedExpiryActivity.name,
            store: store,
            records: records,
            now: { Date(timeIntervalSince1970: TimeInterval(now)) },
            calendar: calendar
        )
    }

    private func writePending(
        _ records: SuspendedExpiryRecordStore,
        sessionId: String,
        end: Int64 = 1_700_003_600,
        calendar: Calendar = SuspendedExpiryTests.utcCalendar()
    ) throws {
        let components = calendar.dateComponents(
            [.year, .month, .day, .hour, .minute],
            from: Date(timeIntervalSince1970: TimeInterval(end))
        )
        try records.writePending(sessionId: sessionId, intervalEnd: components, endEpochSeconds: end)
    }

    private func writeRawPending(_ records: SuspendedExpiryRecordStore, json: String) throws {
        try FileManager.default.createDirectory(at: records.directoryURL, withIntermediateDirectories: true)
        try Data(json.utf8).write(to: records.directoryURL.appendingPathComponent("pending-v1.json"))
    }

    private func isScheduled(sessionId: String, scheduler: SuspendedExpiryScheduler) throws -> Bool {
        var result: KotlinBoolean?
        scheduler.isScheduled(sessionId: sessionId) { result = $0 }
        return try XCTUnwrap(result).boolValue
    }

    private func clearedRecordURL(_ records: SuspendedExpiryRecordStore) throws -> URL {
        let urls = try FileManager.default.contentsOfDirectory(at: records.directoryURL, includingPropertiesForKeys: nil)
        return try XCTUnwrap(urls.first { $0.lastPathComponent.hasPrefix("cleared-") })
    }

    private func capableScheduler(
        monitoring: FakeExpiryMonitoring,
        authorization: EnforcementAuthorization = .approved,
        records: @escaping () -> SuspendedExpiryRecordStore?
    ) -> SuspendedExpiryScheduler {
        SuspendedExpiryScheduler(
            monitoring: monitoring,
            authorization: { authorization },
            isCapable: true,
            records: records,
            calendar: Self.utcCalendar()
        )
    }

    private func schedule(
        sessionId: String,
        start: Int64,
        end: Int64,
        scheduler: SuspendedExpiryScheduler
    ) throws -> IosSuspendedExpiryOutcome {
        var result: IosSuspendedExpiryOutcome?
        scheduler.schedule(
            request: IosSuspendedExpiryRequest(sessionId: sessionId, startEpochSeconds: start, endEpochSeconds: end)
        ) { result = $0 }
        return try XCTUnwrap(result)
    }

    private func cancel(scheduler: SuspendedExpiryScheduler) throws -> IosSuspendedExpiryOutcome {
        var result: IosSuspendedExpiryOutcome?
        scheduler.cancel { result = $0 }
        return try XCTUnwrap(result)
    }

    private func reconcile(sessionId: String, scheduler: SuspendedExpiryScheduler) throws -> IosExpiryReconciliation {
        var result: IosExpiryReconciliation?
        scheduler.readReconciliation(sessionId: sessionId) { result = $0 }
        return try XCTUnwrap(result)
    }

    private func acknowledge(sessionId: String, scheduler: SuspendedExpiryScheduler) throws -> Bool {
        var result: KotlinBoolean?
        scheduler.acknowledgeReconciliation(sessionId: sessionId) { result = $0 }
        return try XCTUnwrap(result).boolValue
    }

    private func displaced(currentSessionId: String, scheduler: SuspendedExpiryScheduler) -> String? {
        var result: String??
        scheduler.displacedClearedSessionId(currentSessionId: currentSessionId) { result = $0.sessionId }
        return result ?? nil
    }

    private func isolatedRecordStore() throws -> SuspendedExpiryRecordStore {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
            .appendingPathComponent("SuspendedExpiry", isDirectory: true)
        addTeardownBlock {
            try? FileManager.default.removeItem(at: directory.deletingLastPathComponent())
        }
        return SuspendedExpiryRecordStore(directoryURL: directory)
    }
}
