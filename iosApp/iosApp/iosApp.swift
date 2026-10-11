import ManagedSettings
import PosatoShared
import SwiftUI

@main
struct PosatoApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeRoot()
                .ignoresSafeArea(.all)
        }
    }
}

private struct ComposeRoot: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let applicationMappingsProvider = IosFamilyControlsApplicationMappingsProvider()
        let accountSource = CloudKitAccountSource()
        let keychainProvider = SynchronizableKeychainProvider(
            accountSource: accountSource,
            backend: SystemSecItemBackend(),
            bundle: .main
        )
        let mailboxProvider = CloudKitMailboxProvider(
            accountSource: accountSource,
            backend: DeferredCloudKitMailboxBackend { CloudKitMailboxLiveBackend() }
        )
        let folderPicker = FilesFolderPicker()
        let controller = MainViewControllerKt.mainViewController(
            cryptoProvider: CryptoKitSyncProvider(),
            applicationMappingsProvider: applicationMappingsProvider,
            enforcementProvider: IosManagedSettingsEnforcer(
                scheduleStore: { ManagedSettingsStore(named: ScheduleMonitor.storeName) },
                composeLock: .live()
            ),
            suspendedExpiryProvider: SuspendedExpiryScheduler(),
            keychainProvider: keychainProvider,
            mailboxProvider: mailboxProvider,
            notificationProvider: SessionNotificationCenter(),
            scheduleEnforcementProvider: IosManagedSettingsEnforcer(storeFactory: {
                ManagedSettingsStore(named: ScheduleMonitor.storeName)
            }),
            scheduleMonitorProvider: IosScheduleMonitorPublisher(),
            folderPicker: folderPicker
        )
        applicationMappingsProvider.presenter = controller
        folderPicker.presenter = controller
        return controller
    }

    func updateUIViewController(
        _ uiViewController: UIViewController,
        context: Context
    ) {}
}
