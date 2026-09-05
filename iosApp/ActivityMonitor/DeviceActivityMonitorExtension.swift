import DeviceActivity
import Foundation
import ManagedSettings

/// Device Activity monitor extension for the suspended-expiry path. At the
/// interval-end callback for the Posato activity it clears only the Posato
/// named store and writes one minimal versioned App Group record. It reads no
/// selection tokens, no domains, and nothing from the shared Kotlin framework,
/// and it logs nothing.
final class DeviceActivityMonitorExtension: DeviceActivityMonitor {
    override func intervalDidEnd(for activity: DeviceActivityName) {
        performOnMain {
            let store = ManagedSettingsStore(named: PosatoManagedSettingsStore.name)
            if let records = SuspendedExpiryRecordStore.live() {
                SuspendedExpiryClear.handleIntervalEnd(activity: activity, store: store, records: records)
            } else if activity == SuspendedExpiryActivity.name {
                store.clearAllSettings()
            }
        }
    }

    private func performOnMain(_ action: @escaping () -> Void) {
        if Thread.isMainThread {
            action()
        } else {
            DispatchQueue.main.async(execute: action)
        }
    }
}
