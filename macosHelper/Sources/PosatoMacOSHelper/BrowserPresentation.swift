import AppKit
import Foundation
import PosatoMacOSServiceCore

enum BrowserPresentationStatus: Equatable {
  case presented
  case skipped
  case permissionDenied
}

enum SupportedBrowser: String, CaseIterable {
  case safari
  case chrome

  var bundleIdentifier: String {
    switch self {
    case .safari:
      return "com.apple.Safari"
    case .chrome:
      return "com.google.Chrome"
    }
  }
}

protocol AppleEventRunning {
  func run(_ source: String) throws -> String
}

enum AppleEventRunnerError: Error {
  case permissionDenied
  case failed
}

struct NSAppleScriptRunner: AppleEventRunning {
  func run(_ source: String) throws -> String {
    var error: NSDictionary?
    guard let script = NSAppleScript(source: source) else {
      throw AppleEventRunnerError.failed
    }
    let result = script.executeAndReturnError(&error)
    if let error {
      let number = error[NSAppleScript.errorNumber] as? Int ?? 0
      if number == -1743 || number == -1744 {
        throw AppleEventRunnerError.permissionDenied
      }
      throw AppleEventRunnerError.failed
    }
    return result.stringValue ?? ""
  }
}

struct BrowserPresentationAdapter {
  var runner: AppleEventRunning
  var now: () -> TimeInterval
  var frontmostBrowser: () -> SupportedBrowser?
  private var lastPresented: [SupportedBrowser: TimeInterval] = [:]
  private let minimumInterval: TimeInterval = 1

  init(
    runner: AppleEventRunning = NSAppleScriptRunner(),
    now: @escaping () -> TimeInterval = { ProcessInfo.processInfo.systemUptime },
    frontmostBrowser: (() -> SupportedBrowser?)? = nil
  ) {
    self.runner = runner
    self.now = now
    self.frontmostBrowser = frontmostBrowser ?? { Self.systemFrontmostBrowser() }
  }

  mutating func presentBlockedPage(
    port: UInt16,
    selectedHosts: Set<String>,
    opensAt: (String) -> (hour: Int, minute: Int)? = { _ in nil }
  ) -> BrowserPresentationStatus {
    guard let browser = frontmostBrowser() else {
      return .skipped
    }
    let instant = now()
    if let previous = lastPresented[browser], instant - previous < minimumInterval {
      return .skipped
    }
    do {
      let captured = try runner.run(browser.readScript)
      let parts = captured.split(separator: "\t", omittingEmptySubsequences: false).map(String.init)
      guard parts.count == 3, let currentURL = parts.last,
        Self.isNumeric(parts[0]), Self.isNumeric(parts[1])
      else {
        return .skipped
      }
      guard let host = blockedHost(urlString: currentURL, selectedHosts: selectedHosts) else {
        return .skipped
      }
      // Only the adapter chooses the window form, after reducing the tab to its host; the time is the only variable.
      let path =
        opensAt(host).map { String(format: "/blocked/opens/%02d%02d", $0.hour, $0.minute) }
        ?? "/blocked"
      let destination = "http://127.0.0.1:\(port)\(path)"
      let written = try runner.run(
        browser.writeScript(windowID: parts[0], tabReference: parts[1], url: destination)
      )
      guard isBlockedPage(urlString: written, port: port, path: path) else {
        return .skipped
      }
      lastPresented[browser] = instant
      activateParent()
      return .presented
    } catch AppleEventRunnerError.permissionDenied {
      return .permissionDenied
    } catch {
      return .skipped
    }
  }

  private static func isNumeric(_ value: String) -> Bool {
    return !value.isEmpty && value.utf8.allSatisfy { byte in byte >= 0x30 && byte <= 0x39 }
  }

  private static func systemFrontmostBrowser() -> SupportedBrowser? {
    let active = SupportedBrowser.allCases.flatMap { browser in
      NSRunningApplication.runningApplications(withBundleIdentifier: browser.bundleIdentifier)
        .filter(\.isActive)
        .map { _ in browser }
    }
    guard active.count == 1 else {
      return nil
    }
    return active[0]
  }

  /// The tab's canonical host when it is blocked now, or nil.
  private func blockedHost(urlString: String, selectedHosts: Set<String>) -> String? {
    guard let components = URLComponents(string: urlString),
      let host = components.host,
      ExactHostPolicy.matches(host: host, selectedHosts: selectedHosts)
    else {
      return nil
    }
    return ExactHostPolicy.normalizedHost(host)
  }

  private func isBlockedPage(urlString: String, port: UInt16, path: String) -> Bool {
    guard let components = URLComponents(string: urlString),
      components.scheme?.lowercased() == "http",
      components.host == "127.0.0.1",
      components.port == Int(port) || (components.port == nil && port == 80),
      components.path == path,
      components.query == nil,
      components.fragment == nil
    else {
      return false
    }
    return true
  }

  private func activateParent() {
    let running = NSRunningApplication.runningApplications(
      withBundleIdentifier: ServiceContract.applicationIdentifier
    )
    running.first?.activate()
  }
}

extension SupportedBrowser {
  var readScript: String {
    switch self {
    case .safari:
      return """
        tell application "Safari"
          if not (exists front window) then return ""
          set theWindow to front window
          set theTab to current tab of theWindow
          return (id of theWindow as text) & "\t" & (index of theTab as text) & "\t" & (URL of theTab as text)
        end tell
        """
    case .chrome:
      return """
        tell application "Google Chrome"
          if not (exists front window) then return ""
          set theWindow to front window
          set theTab to active tab of theWindow
          return (id of theWindow as text) & "\t" & (id of theTab as text) & "\t" & (URL of theTab as text)
        end tell
        """
    }
  }

  /// Safari tabs expose no `id`, so the Safari reference is the tab index inside its window;
  /// Chrome tabs are addressed by their stable `id`. Both values are digits captured by `readScript`.
  func writeScript(windowID: String, tabReference: String, url: String) -> String {
    switch self {
    case .safari:
      return """
        tell application "Safari"
          set URL of tab \(tabReference) of window id \(windowID) to "\(url)"
          return URL of tab \(tabReference) of window id \(windowID)
        end tell
        """
    case .chrome:
      return """
        tell application "Google Chrome"
          set URL of tab id \(tabReference) of window id \(windowID) to "\(url)"
          return URL of tab id \(tabReference) of window id \(windowID)
        end tell
        """
    }
  }
}
