import ManagedSettings
import PosatoShared
import SwiftUI

@main
struct PosatoApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeRoot()
                .ignoresSafeArea(.all)
                // A scanned or shared pairing link fills the code in the folder sync section (ADR 0010).
                .onOpenURL { url in _ = PosatoLinksKt.openPosatoLink(url: url.absoluteString) }
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
        let pauseSetPicker = FilesPauseSetPicker()
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
            folderPicker: folderPicker,
            pauseSetFilePicker: pauseSetPicker
        )
        applicationMappingsProvider.presenter = controller
        folderPicker.presenter = controller
        pauseSetPicker.presenter = controller
        return controller
    }

    func updateUIViewController(
        _ uiViewController: UIViewController,
        context: Context
    ) {}
}
