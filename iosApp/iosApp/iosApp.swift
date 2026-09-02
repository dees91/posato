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
        let controller = MainViewControllerKt.mainViewController(
            cryptoProvider: CryptoKitSyncProvider(),
            applicationMappingsProvider: applicationMappingsProvider
        )
        applicationMappingsProvider.presenter = controller
        return controller
    }

    func updateUIViewController(
        _ uiViewController: UIViewController,
        context: Context
    ) {}
}
