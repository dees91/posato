import Foundation
import Network
import SystemConfiguration

struct NetworkCompatibilityFacts: Equatable {
  var managedProxyOverlay: Bool
  var vpnOrRelayActive: Bool
  var pathUnsatisfied: Bool
}

protocol NetworkCompatibilityReading {
  func facts() -> NetworkCompatibilityFacts
}

struct SystemNetworkCompatibilityReader: NetworkCompatibilityReading {
  func facts() -> NetworkCompatibilityFacts {
    return NetworkCompatibilityFacts(
      managedProxyOverlay: hasManagedProxyOverlay(),
      vpnOrRelayActive: hasVPNOrRelay(),
      pathUnsatisfied: hasUnsatisfiedPath()
    )
  }

  private func hasManagedProxyOverlay() -> Bool {
    guard let live = CFNetworkCopySystemProxySettings()?.takeRetainedValue() as NSDictionary?
    else {
      return true
    }
    let enabledKeys = [
      "HTTPEnable", "HTTPSEnable", "SOCKSEnable", "ProxyAutoConfigEnable",
      "ProxyAutoDiscoveryEnable",
    ]
    return enabledKeys.contains { key in
      (live[key] as? NSNumber)?.intValue == 1
    }
  }

  private func hasVPNOrRelay() -> Bool {
    guard
      let store = SCDynamicStoreCreate(nil, "Posato network compatibility" as CFString, nil, nil),
      let global = SCDynamicStoreCopyValue(store, "State:/Network/Global/IPv4" as CFString)
        as? [String: Any],
      let primary = global["PrimaryInterface"] as? String
    else {
      return true
    }
    let lowered = primary.lowercased()
    if lowered.hasPrefix("utun") || lowered.hasPrefix("ipsec") || lowered.hasPrefix("ppp") {
      return true
    }
    return false
  }

  private func hasUnsatisfiedPath() -> Bool {
    let monitor = NWPathMonitor()
    let semaphore = DispatchSemaphore(value: 0)
    let box = PathBox()
    monitor.pathUpdateHandler = { path in
      box.unsatisfied = path.status != .satisfied
      semaphore.signal()
    }
    let queue = DispatchQueue(label: "app.posato.macos.helper.path")
    monitor.start(queue: queue)
    _ = semaphore.wait(timeout: .now() + 1)
    monitor.cancel()
    return box.unsatisfied
  }
}

private final class PathBox: @unchecked Sendable {
  private let lock = NSLock()
  private var storage = true

  var unsatisfied: Bool {
    get {
      lock.lock()
      defer { lock.unlock() }
      return storage
    }
    set {
      lock.lock()
      storage = newValue
      lock.unlock()
    }
  }
}

struct NetworkCompatibility {
  var reader: NetworkCompatibilityReading

  func activationIsAllowed() -> Bool {
    let facts = reader.facts()
    return !facts.managedProxyOverlay && !facts.vpnOrRelayActive && !facts.pathUnsatisfied
  }
}
