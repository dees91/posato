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
        files: ScheduleMonitorFileStore?,
        poster: ScheduleNoticePoster,
        now: () -> Date = Date.init,
        calendar: Calendar = ScheduleMonitor.calendar
    ) {
        guard ScheduleMonitor.isScheduleActivity(activity), let files, let file = files.readTable() else { return }
        let time = now()
        let running = ScheduleMonitorRule.running(
            in: file, at: time, calendar: calendar, leading: ScheduleMonitor.earlyCallbackMargin
        )
        guard !running.isEmpty else { return }
        let applications = Set(file.applicationTokens.compactMap { try? JSONDecoder().decode(ApplicationToken.self, from: $0) })
        let domains = PosatoWebDomains.domains(from: file.domains)
        guard !domains.isEmpty || !applications.isEmpty else { return }
        store.applySchedule(domains: domains, applications: applications)
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

    /// Clears the schedule's store unless an occurrence still runs, which also
    /// covers overlaps, extended pins and the early callback of a restart. An
    /// unreadable table clears, so the phone is never left restricted. Pause
    /// over follows only shields the schedule held, and only when no manual
    /// session still holds its own; it replaces the app's planned end notice.
    static func handleIntervalEnd(
        activity: DeviceActivityName,
        store: ScheduleShieldStore,
        sessionStore: ScheduleShieldStore,
        files: ScheduleMonitorFileStore?,
        poster: ScheduleNoticePoster,
        now: () -> Date = Date.init,
        calendar: Calendar = ScheduleMonitor.calendar
    ) {
        guard ScheduleMonitor.isScheduleActivity(activity) else { return }
        guard let files, let file = files.readTable() else {
            store.clearSchedule()
            return
        }
        let time = now()
        // Anything that still runs a minute from now keeps the shields.
        let stillRunning = ScheduleMonitorRule.running(
            in: file, at: time.addingTimeInterval(ScheduleMonitor.earlyCallbackMargin), calendar: calendar
        )
        guard stillRunning.isEmpty else { return }
        let held = store.holdsAnyShield
        store.clearSchedule()
        let sessionEnded = (file.notices.manualSessionEnd ?? 0) <= Int64(time.timeIntervalSince1970)
        if held, file.notices.enabled, sessionEnded, !sessionStore.holdsAnyShield {
            poster.post(identifier: ScheduleMonitor.endNoticeIdentifier, title: file.notices.endTitle, body: file.notices.endBody)
        }
    }
}
