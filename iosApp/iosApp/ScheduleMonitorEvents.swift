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
        center: ScheduleActivityCenter? = nil,
        now: () -> Date = Date.init,
        calendar: Calendar = ScheduleMonitor.calendar
    ) {
        guard ScheduleMonitor.isScheduleActivity(activity), let files else { return }
        let time = now()
        // The table is read inside the lock, so a callback that waited never composes from a table read before
        // the app wrote a new break.
        let composed = files.withComposeLock { () -> (file: ScheduleMonitorFile, running: [ScheduleMonitorOccurrence])? in
            guard let file = files.readTable() else { return nil }
            let running = ScheduleMonitorRule.running(
                in: file, at: time, calendar: calendar, leading: ScheduleMonitor.earlyCallbackMargin
            )
            guard !running.isEmpty else { return nil }
            let over = files.breaksOver()
            // A break is released only by a deadline registered before this callback, so arming here never covers one.
            let releasing = ScheduleBreakWindows.releasing(
                file, over: over, armed: ScheduleBreakActivity.armed(center: center, calendar: calendar), at: time
            )
            // Close before open: every window boundary is registered before any website of the part opens.
            let covered = center.map { center in
                windowTopUp(file: file, over: over, files: files, center: center, calling: nil, callback: .evaluation, at: time, calendar: calendar)
                    .covered
            }
            let applied = applyComposed(
                running: running, file: file, files: files, store: store, sessionStore: sessionStore, releasing: releasing,
                covered: covered, at: time, calendar: calendar
            )
            if let center {
                // The part records what it holds while composing; its window boundaries follow.
                _ = windowTopUp(file: file, over: over, files: files, center: center, calling: nil, callback: .evaluation, at: time, calendar: calendar)
            }
            return applied ? (file, running) : nil
        }
        guard let (file, running) = composed else { return }
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
    /// Parts in `releasing` are on a break: they stay running and keep their records, but only what they hold
    /// counts toward the limits, so their return always fits. Returns false when there is nothing to pause.
    @discardableResult
    static func applyComposed(
        running: [ScheduleMonitorOccurrence],
        file: ScheduleMonitorFile,
        files: ScheduleMonitorFileStore,
        store: ScheduleShieldStore,
        sessionStore: ScheduleShieldStore?,
        releasing: Set<String> = [],
        covered: ((Date) -> Bool)? = nil,
        at time: Date = Date(),
        calendar: Calendar = ScheduleMonitor.calendar
    ) -> Bool {
        let onBreak = running.filter { releasing.contains("\($0.scheduleId):\($0.date)") }
        let composing = running.filter { !releasing.contains("\($0.scheduleId):\($0.date)") }
        let parts = composing.map { occurrence -> PausePlanner.Part in
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
        var occupiedTokens = Set((sessionStore?.shieldedTokens ?? []).compactMap { try? encoder.encode($0) })
        var occupiedDomains = Set(sessionStore?.shieldedHosts ?? [])
        // A manual website open in its window is still held: an open window frees no room.
        if let record = files.readManualWindows(), time < record.end {
            occupiedDomains.formUnion(record.held)
        }
        for occurrence in onBreak {
            let held = files.held(scheduleId: occurrence.scheduleId, date: occurrence.date)
            occupiedDomains.formUnion(held?.domains ?? [])
            occupiedTokens.formUnion(held?.tokens ?? [])
        }
        let plan = PausePlanner.plan(parts, occupiedDomains: occupiedDomains, occupiedTokens: occupiedTokens)
        let applications = Set(plan.tokens.compactMap { try? JSONDecoder().decode(ApplicationToken.self, from: $0) })
        guard !plan.domains.isEmpty || !applications.isEmpty else { return false }
        // Held, then effective: windows narrow only what the store blocks now, never what the parts hold.
        let rules = ScheduledWindows.rules(held: plan.heldDomains, composing: composing, file: file)
        let open = ScheduledWindows.open(rules, at: time, covered: covered, lead: WindowEvaluation.closingLead, calendar: calendar)
        store.applySchedule(domains: PosatoWebDomains.domains(from: Array(plan.domains.subtracting(open))), applications: applications)
        for occurrence in composing {
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
        center: ScheduleActivityCenter? = nil,
        now: () -> Date = Date.init,
        calendar: Calendar = ScheduleMonitor.calendar
    ) {
        guard ScheduleMonitor.isScheduleActivity(activity) else { return }
        guard let files, let file = files.readTable() else {
            store.clearSchedule()
            return
        }
        let time = now()
        let own = ScheduleMonitor.scheduleId(of: activity)
        // A restart or stop of monitoring calls this plan's end early; its own occurrence still runs. This first
        // read only skips the lock for that case and changes nothing.
        let early = ScheduleMonitorRule.running(in: file, at: time, calendar: calendar)
        if ownStillRuns(early, own: own, at: time) {
            registerNextCap(running: early, file: file, caps: caps, at: time, calendar: calendar)
            return
        }
        // Everything that changes the store is decided again from the table read under the lock and done in that
        // same hold, so an occurrence the app published and applied meanwhile is never cleared.
        let outcome = files.withComposeLock { () -> EndOutcome in
            guard let table = files.readTable() else {
                store.clearSchedule()
                return .unreadable
            }
            let running = ScheduleMonitorRule.running(in: table, at: time, calendar: calendar)
            if ownStillRuns(running, own: own, at: time) {
                return .ownRuns(table, running)
            }
            // Another occurrence running now keeps the shields; one that starts later reapplies them itself.
            if running.contains(where: { $0.scheduleId != own }) {
                // Only items no remaining occurrence needs are released: the rest compose again without this one.
                let remaining = running.filter { $0.scheduleId != own }
                let over = files.breaksOver()
                let releasing = ScheduleBreakWindows.releasing(
                    table, over: over, armed: ScheduleBreakActivity.armed(center: center, calendar: calendar), at: time
                )
                let covered = center.map { center in
                    windowTopUp(file: table, over: over, files: files, center: center, calling: nil, callback: .evaluation, at: time, calendar: calendar)
                        .covered
                }
                // The remaining occurrences pause nothing, such as when their set is empty: nothing stays paused.
                if !applyComposed(
                    running: remaining, file: table, files: files, store: store, sessionStore: sessionStore, releasing: releasing,
                    covered: covered, at: time, calendar: calendar
                ) {
                    store.clearSchedule()
                }
                return .othersRun(table, running)
            }
            // An occurrence that ends during its break held its items for the person, so Pause over still follows.
            let onBreakAtEnd = ScheduleBreakWindows.open(table, over: files.breaksOver()).contains { window in
                window.id == own && window.start <= time && window.end >= time.addingTimeInterval(-ScheduleMonitor.earlyCallbackMargin)
            }
            let held = store.holdsAnyShield || onBreakAtEnd
            store.clearSchedule()
            return .ended(table, held: held)
        }
        switch outcome {
        case .unreadable:
            return
        case let .ownRuns(table, running), let .othersRun(table, running):
            // The one cap activity may have just fired for an overlapping occurrence; chain the next cap.
            registerNextCap(running: running, file: table, caps: caps, at: time, calendar: calendar)
        case let .ended(table, held):
            let sessionEnded = (table.notices.manualSessionEnd ?? 0) <= Int64(time.timeIntervalSince1970)
            if held, table.notices.enabled, sessionEnded, !sessionStore.holdsAnyShield {
                poster.post(identifier: ScheduleMonitor.endNoticeIdentifier, title: table.notices.endTitle, body: table.notices.endBody)
            }
        }
    }

    /// What an end callback did under the lock, with the table it decided from.
    private enum EndOutcome {
        case unreadable
        case ownRuns(ScheduleMonitorFile, [ScheduleMonitorOccurrence])
        case othersRun(ScheduleMonitorFile, [ScheduleMonitorOccurrence])
        case ended(ScheduleMonitorFile, held: Bool)
    }

    private static func ownStillRuns(_ running: [ScheduleMonitorOccurrence], own: String?, at time: Date) -> Bool {
        running.contains { $0.scheduleId == own && $0.end > time.addingTimeInterval(ScheduleMonitor.earlyCallbackMargin) }
    }

    /// Both callbacks of the break activity evaluate at their time and never clear. One at or after the
    /// registered deadline, less the early-callback margin, marks every window that ended as over on this device,
    /// registers the next open deadline, and composes, which re-applies the parts whose break ended. A deadline
    /// that cannot be registered ends every open break here, so no window is left without a return. An earlier
    /// callback, such as the start right after registration, only composes.
    static func handleBreakCallback(
        store: ScheduleShieldStore,
        sessionStore: ScheduleShieldStore,
        files: ScheduleMonitorFileStore?,
        center: ScheduleActivityCenter,
        now: () -> Date = Date.init,
        calendar: Calendar = ScheduleMonitor.calendar
    ) {
        guard let files else { return }
        let time = now()
        files.withComposeLock {
            guard let file = files.readTable() else { return }
            var armed = ScheduleBreakActivity.armed(center: center, calendar: calendar)
            var over = files.breaksOver()
            if let deadline = armed, time >= deadline.addingTimeInterval(-ScheduleMonitor.earlyCallbackMargin) {
                for window in ScheduleBreakWindows.open(file, over: over)
                where time >= window.end.addingTimeInterval(-ScheduleMonitor.earlyCallbackMargin) {
                    try? files.recordBreakOver(scheduleId: window.id, date: window.date)
                    over.insert(window.key)
                }
                if let next = ScheduleBreakWindows.nearestDeadline(file, over: over, at: time), next != deadline {
                    do {
                        try center.startMonitoringSchedule(
                            ScheduleMonitor.breakActivity, during: ScheduleBreakActivity.schedule(deadline: next, calendar: calendar)
                        )
                        armed = next
                    } catch {
                        for window in ScheduleBreakWindows.open(file, over: over) {
                            try? files.recordBreakOver(scheduleId: window.id, date: window.date)
                            over.insert(window.key)
                        }
                        armed = nil
                    }
                }
            }
            let running = ScheduleMonitorRule.running(in: file, at: time, calendar: calendar)
            guard !running.isEmpty else { return }
            let releasing = ScheduleBreakWindows.releasing(file, over: over, armed: armed, at: time)
            // Windows follow the boundaries registered now; the break activity never registers them.
            let fires = WindowActivities.covering(center: center, calling: nil, callback: .evaluation, calendar: calendar)
            applyComposed(
                running: running, file: file, files: files, store: store, sessionStore: sessionStore, releasing: releasing,
                covered: { WindowActivities.covers(fires, $0) }, at: time, calendar: calendar
            )
        }
    }

    /// A window activity's callback (TARGETS-013 G21), or Posato's own evaluation when `activity` is nil: it reads
    /// the clock, registers the boundaries ahead that no activity covers, and both stores follow the clock. It never
    /// restarts or stops an activity; it returns the stale ones, which only the app stops (`stopStale`).
    @discardableResult
    static func handleWindowCallback(
        activity: DeviceActivityName?,
        callback: MonitorBoundary.Callback,
        store: ScheduleShieldStore,
        sessionStore: ScheduleShieldStore,
        manualFilter: ManualWebFilterStore?,
        files: ScheduleMonitorFileStore?,
        center: ScheduleActivityCenter,
        now: () -> Date = Date.init,
        calendar: Calendar = ScheduleMonitor.calendar,
        log: (String) -> Void = { _ in }
    ) -> [DeviceActivityName] {
        // Red stub (TARGETS-013 G21): nothing is evaluated yet.
        []
    }

    /// The window boundaries wanted at `time`: the manual session's and the running scheduled parts'.
    static func windowBoundaries(
        file: ScheduleMonitorFile?,
        over: Set<String>,
        files: ScheduleMonitorFileStore,
        center: ScheduleActivityCenter?,
        at time: Date,
        calendar: Calendar
    ) -> [Date] {
        let armed = center.flatMap { ScheduleBreakActivity.armed(center: $0, calendar: calendar) }
        let scheduled = ScheduledWindows.rulesNow(file: file, files: files, over: over, armed: armed, at: time, calendar: calendar)
        let all = WindowEvaluation.manualBoundaries(files.readManualWindows(), after: time, calendar: calendar)
            + ScheduledWindows.boundaries(scheduled, after: time, calendar: calendar)
        return Array(Set(all)).sorted()
    }

    struct WindowTopUp {
        let fires: [Date]
        /// For the app to stop after it released the compose lock (`stopStale`); a callback never stops one.
        let stale: [DeviceActivityName]

        /// Whether an activity is registered for a close at that instant.
        var covered: (Date) -> Bool {
            { [fires] boundary in WindowActivities.covers(fires, boundary) }
        }
    }

    /// Registers the wanted window boundaries ahead (`WindowActivities.topUp`). Called under the compose lock.
    static func windowTopUp(
        file: ScheduleMonitorFile?,
        over: Set<String>,
        files: ScheduleMonitorFileStore,
        center: ScheduleActivityCenter,
        calling: DeviceActivityName?,
        callback: MonitorBoundary.Callback,
        at time: Date,
        calendar: Calendar,
        log: (String) -> Void = { _ in }
    ) -> WindowTopUp {
        let wanted = windowBoundaries(file: file, over: over, files: files, center: center, at: time, calendar: calendar)
        let outcome = WindowActivities.topUp(wanted: wanted, center: center, calling: calling, callback: callback, at: time, calendar: calendar, log: log)
        return WindowTopUp(fires: outcome.fires, stale: outcome.stale)
    }

    /// Stops window activities the app's evaluation found stale. Called by the app only, never under the compose
    /// lock and never from a callback: a stop delivers the activity's end, whose callback waits for that lock.
    static func stopStale(_ stale: [DeviceActivityName], center: ScheduleActivityCenter) {
        guard !stale.isEmpty else { return }
        center.stopMonitoringSchedules(stale)
    }

    /// The manual session's web filter blocks every held website except those open now, while the session runs;
    /// after its end, or with no record, it is left as it is. Called under the compose lock.
    static func applyManualWindows(
        manualFilter: ManualWebFilterStore?,
        files: ScheduleMonitorFileStore,
        covered: @escaping (Date) -> Bool,
        at time: Date,
        calendar: Calendar
    ) {
        guard let manualFilter, let record = files.readManualWindows(), time < record.end else { return }
        let open = WindowEvaluation.openManual(record, at: time, covered: covered, lead: WindowEvaluation.closingLead, calendar: calendar)
        manualFilter.setBlockedHosts(record.held.filter { !open.contains($0) })
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
