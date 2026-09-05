import PosatoPrototype
import SwiftUI

@main
struct PosatoPrototypeApp: App {
    var body: some Scene {
        WindowGroup {
            PrototypeRoot().ignoresSafeArea(.all)
        }
    }
}

private struct PrototypeRoot: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        return PrototypeViewControllerKt.prototypeViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
