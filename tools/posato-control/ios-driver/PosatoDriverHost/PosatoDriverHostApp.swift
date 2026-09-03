import SwiftUI

/// Minimal host application required by the UI-testing bundle.
///
/// The driver never exercises this application; XCUITest only needs a
/// signed host so that the runner can attach to the Posato application by
/// bundle identifier.
@main
struct PosatoDriverHostApp: App {
  var body: some Scene {
    WindowGroup {
      Text("Posato driver host")
    }
  }
}
