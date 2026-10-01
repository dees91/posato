import DeviceActivity
import Foundation
import ManagedSettings

/// What the monitor extension does for scheduled pauses, testable with
/// injected stores, files and a poster. It reads only the app's table, writes
/// only start records, touches only the schedule's store, and logs nothing.
enum ScheduleMonitorEvents {
    /// Applies the paused items when an occurrence of the plan runs now and
    /// announces each newly started occurrence once. A late callback resolves
    /// to the occurrence that runs; one after the end does nothing.
    static func handleIntervalStart(
        activity: DeviceActivityName,
        store: ScheduleShieldStore,
        sessionStore: ScheduleShieldStore? = nil,
        files: ScheduleMonitorFileStore?,
        poster: ScheduleNoticePoster,
        caps: ScheduleCapRegistrar? = nil,
        now: () -> Date = Date.init,
        calendar: Calendar = ScheduleMonitor.calendar
    ) {
        guard ScheduleMonitor.isScheduleActivity(activity), let files, let file = files.readTable() else { return }
        let time = now()
        let running = ScheduleMonitorRule.running(
            in: file, at: time, calendar: calendar, leading: ScheduleMonitor.earlyCallbackMargin
        )
        guard !running.isEmpty else { return }
        let applied = files.withComposeLock {
            applyComposed(running: running, file: file, files: files, store: store, sessionStore: sessionStore)
        }
        guard applied else { return }
        registerNextCap(running: running, file: file, caps: caps, at: time, calendar: calendar)
        // A manual session ending inside this pause must not say Pause over early; the end of the
        // combined pause is announced here instead.
        if let manualEnd = file.notices.manualSessionEnd,
           let latest = running.map(\.end).max(),
           Int64(latest.timeIntervalSince1970) > manualEnd,
           manualEnd > Int64(time.timeIntervalSince1970) {
            poster.removePending(identifier: ScheduleMonitor.endNoticeIdentifier)
        }
        for occurrence in running where !files.hasStarted(scheduleId: occurrence.scheduleId, date: occurrence.date) {
            try? files.recordStarted(occurrence, at: time)
            guard file.notices.enabled,
                  let schedule = file.schedules.first(where: { $0.id == occurrence.scheduleId })
            else { continue }
            poster.post(
                identifier: "\(ScheduleMonitor.startNoticePrefix)\(occurrence.scheduleId).\(occurrence.date)",
                title: schedule.startTitle,
                body: schedule.startBody
            )
        }
    }

    /// Composes the running occurrences, each with its own set and what it already holds, within what the
    /// manual session's store leaves of the device's limits; applies the union and records what each now holds.
    /// Returns false when there is nothing to pause.
    @discardableResult
    static func applyComposed(
        running: [ScheduleMonitorOccurrence],
        file: ScheduleMonitorFile,
        files: ScheduleMonitorFileStore,
        store: ScheduleShieldStore,
        sessionStore: ScheduleShieldStore?
    ) -> Bool {
        let parts = running.map { occurrence -> PausePlanner.Part in
            let items = file.setItems(scheduleId: occurrence.scheduleId)
            let held = files.held(scheduleId: occurrence.scheduleId, date: occurrence.date)
            return PausePlanner.Part(
                id: "\(occurrence.scheduleId):\(occurrence.date)",
                start: occurrence.start,
                currentDomains: Set(items.domains),
                currentTokens: Set(items.tokens),
                heldDomains: Set(held?.domains ?? []),
                heldTokens: Set(held?.tokens ?? [])
            )
        }
        let encoder = JSONEncoder()
        let occupiedTokens = Set((sessionStore?.shieldedTokens ?? []).compactMap { try? encoder.encode($0) })
        let plan = PausePlanner.plan(parts, occupiedDomains: Set(sessionStore?.shieldedHosts ?? []), occupiedTokens: occupiedTokens)
        let applications = Set(plan.tokens.compactMap { try? JSONDecoder().decode(ApplicationToken.self, from: $0) })
        guard !plan.domains.isEmpty || !applications.isEmpty else { return false }
        store.applySchedule(domains: PosatoWebDomains.domains(from: Array(plan.domains)), applications: applications)
        for occurrence in running {
            let id = "\(occurrence.scheduleId):\(occurrence.date)"
            try? files.recordHeld(
                occurrence,
                domains: (plan.heldDomains[id] ?? []).sorted(),
                tokens: Array(plan.heldTokens[id] ?? [])
            )
        }
        return true
    }

    /// Clears the schedule's store unless an occurrence still runs, which also
    /// covers overlaps, resumed occurrences and the early callback of a restart. An
    /// unreadable table clears, so the phone is never left restricted. Pause
    /// over follows only shields the schedule held, and only when no manual
    /// session still holds its own; it replaces the app's planned end notice.
    static func handleIntervalEnd(
        activity: DeviceActivityName,
        store: ScheduleShieldStore,
        sessionStore: ScheduleShieldStore,
        files: ScheduleMonitorFileStore?,
        poster: ScheduleNoticePoster,
        caps: ScheduleCapRegistrar? = nil,
        now: () -> Date = Date.init,
        calendar: Calendar = ScheduleMonitor.calendar
    ) {
        guard ScheduleMonitor.isScheduleActivity(activity) else { return }
        guard let files, let file = files.readTable() else {
            store.clearSchedule()
            return
        }
        let time = now()
        let running = ScheduleMonitorRule.running(in: file, at: time, calendar: calendar)
        let own = ScheduleMonitor.scheduleId(of: activity)
        // A restart or stop of monitoring calls this plan's end early; its own occurrence still runs.
        let ownStillRuns = running.contains { $0.scheduleId == own && $0.end > time.addingTimeInterval(ScheduleMonitor.earlyCallbackMargin) }
        // Another occurrence running now keeps the shields; one that starts later reapplies them itself.
        let othersRun = running.contains { $0.scheduleId != own }
        if ownStillRuns {
            registerNextCap(running: running, file: file, caps: caps, at: time, calendar: calendar)
            return
        }
        if othersRun {
            // Only items no remaining occurrence needs are released: the rest compose again without this one.
            let remaining = running.filter { $0.scheduleId != own }
            let applied = files.withComposeLock {
                applyComposed(running: remaining, file: file, files: files, store: store, sessionStore: sessionStore)
            }
            // The remaining occurrences pause nothing, such as when their set is empty: nothing stays paused.
            if !applied {
                store.clearSchedule()
            }
            // The one cap activity may have just fired for an overlapping occurrence; chain the next cap.
            registerNextCap(running: running, file: file, caps: caps, at: time, calendar: calendar)
            return
        }
        let held = store.holdsAnyShield
        store.clearSchedule()
        let sessionEnded = (file.notices.manualSessionEnd ?? 0) <= Int64(time.timeIntervalSince1970)
        if held, file.notices.enabled, sessionEnded, !sessionStore.holdsAnyShield {
            poster.post(identifier: ScheduleMonitor.endNoticeIdentifier, title: file.notices.endTitle, body: file.notices.endBody)
        }
    }

    /// Registers the nearest 24-hour cap among the running fall-back occurrences. Every cap shares one
    /// activity name, and a registration replaces the previous one, so only the nearest is kept; its end
    /// callback registers the next while another occurrence still runs.
    private static func registerNextCap(
        running: [ScheduleMonitorOccurrence],
        file: ScheduleMonitorFile,
        caps: ScheduleCapRegistrar?,
        at time: Date,
        calendar: Calendar
    ) {
        guard let caps else { return }
        let next = running.compactMap { occurrence -> Date? in
            guard let schedule = file.schedules.first(where: { $0.id == occurrence.scheduleId }) else { return nil }
            return ScheduleMonitorRule.overrunCap(schedule, on: calendar.startOfDay(for: occurrence.start), calendar: calendar)
        }
        .filter { $0 > time.addingTimeInterval(ScheduleMonitor.earlyCallbackMargin) }
        .min()
        if let next {
            caps.register(cap: ScheduleMonitorRule.capSchedule(endingAt: next, calendar: calendar))
        }
    }
}
