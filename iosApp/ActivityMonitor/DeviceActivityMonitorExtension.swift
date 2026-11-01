import DeviceActivity
import Foundation
import ManagedSettings

/// Device Activity monitor extension. For the suspended-expiry path it clears
/// only the session's named store at the interval end and writes one minimal
/// versioned App Group record. For scheduled pauses it applies and clears only
/// the schedule's named store from the table the app wrote. It reads nothing
/// from the shared Kotlin framework, and it logs nothing.
final class DeviceActivityMonitorExtension: DeviceActivityMonitor {
    override func intervalDidStart(for activity: DeviceActivityName) {
        if activity == ScheduleMonitor.breakActivity {
            return handleBreak()
        }
        if WindowActivities.isWindowActivity(activity) {
            return handleWindow(activity, .start)
        }
        guard ScheduleMonitor.isScheduleActivity(activity) else { return }
        ScheduleMonitorEvents.handleIntervalStart(
            activity: activity,
            store: ManagedSettingsStore(named: ScheduleMonitor.storeName),
            sessionStore: ManagedSettingsStore(named: PosatoManagedSettingsStore.name),
            files: ScheduleMonitorFileStore.live(),
            poster: UserNotificationSchedulePoster(),
            caps: DeviceActivityCapRegistrar(),
            center: DeviceActivityCenter()
        )
    }

    /// Either callback of the one break activity: an evaluation that re-applies a break that ended.
    private func handleBreak() {
        ScheduleMonitorEvents.handleBreakCallback(
            store: ManagedSettingsStore(named: ScheduleMonitor.storeName),
            sessionStore: ManagedSettingsStore(named: PosatoManagedSettingsStore.name),
            files: ScheduleMonitorFileStore.live(),
            center: DeviceActivityCenter()
        )
    }

    /// Either callback of a website window's boundary activity (TARGETS-013): both stores follow the clock and the
    /// boundaries ahead are topped up; the activity itself is never restarted or stopped here.
    private func handleWindow(_ activity: DeviceActivityName, _ callback: MonitorBoundary.Callback) {
        ScheduleMonitorEvents.handleWindowCallback(
            activity: activity,
            callback: callback,
            store: ManagedSettingsStore(named: ScheduleMonitor.storeName),
            sessionStore: ManagedSettingsStore(named: PosatoManagedSettingsStore.name),
            manualFilter: ManagedSettingsStore(named: PosatoManagedSettingsStore.name),
            files: ScheduleMonitorFileStore.live(),
            center: DeviceActivityCenter()
        )
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        if activity == ScheduleMonitor.breakActivity {
            return handleBreak()
        }
        if WindowActivities.isWindowActivity(activity) {
            return handleWindow(activity, .end)
        }
        if ScheduleMonitor.isScheduleActivity(activity) {
            ScheduleMonitorEvents.handleIntervalEnd(
                activity: activity,
                store: ManagedSettingsStore(named: ScheduleMonitor.storeName),
                sessionStore: ManagedSettingsStore(named: PosatoManagedSettingsStore.name),
                files: ScheduleMonitorFileStore.live(),
                poster: UserNotificationSchedulePoster(),
                caps: DeviceActivityCapRegistrar(),
                center: DeviceActivityCenter()
            )
            return
        }
        // Synchronous on purpose: the extension process can be suspended as
        // soon as this callback returns, so an async main-queue hop might
        // never run. Clearing the store and writing the small record are safe
        // from any thread.
        let store = ManagedSettingsStore(named: PosatoManagedSettingsStore.name)
        if let records = SuspendedExpiryRecordStore.live() {
            SuspendedExpiryClear.handleIntervalEnd(activity: activity, store: store, records: records)
        } else if activity == SuspendedExpiryActivity.name {
            store.clearAllSettings()
        }
    }
}
