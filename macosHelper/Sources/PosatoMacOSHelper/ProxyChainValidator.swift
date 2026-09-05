import CoreFoundation
import Foundation

enum ProxyChainHop: Equatable {
  case loopback(port: UInt16)
  case direct
  case other
}

protocol ProxyChainResolving {
  func hops(url: URL, settings: CFDictionary?) -> [ProxyChainHop]
}

struct SystemProxyChainResolver: ProxyChainResolving {
  func hops(url: URL, settings: CFDictionary?) -> [ProxyChainHop] {
    let proxySettings = settings ?? CFNetworkCopySystemProxySettings()?.takeRetainedValue()
    guard let proxySettings,
      let proxies = CFNetworkCopyProxiesForURL(url as CFURL, proxySettings).takeRetainedValue()
        as? [[AnyHashable: Any]]
    else {
      return [.direct]
    }
    return proxies.map(Self.hop)
  }

  private static func hop(_ proxy: [AnyHashable: Any]) -> ProxyChainHop {
    let type = proxy[kCFProxyTypeKey] as? String
    if type == (kCFProxyTypeNone as String) {
      return .direct
    }
    let host = proxy[kCFProxyHostNameKey] as? String
    let port = (proxy[kCFProxyPortNumberKey] as? NSNumber)?.uint16Value
    if host == "127.0.0.1", let port, port > 0 {
      return .loopback(port: port)
    }
    return .other
  }
}

struct ProxyChainValidator {
  var resolver: ProxyChainResolving = SystemProxyChainResolver()

  func containsOnlyLoopback(
    selectedHosts: Set<String>,
    port: UInt16,
    settings: CFDictionary?
  ) -> Bool {
    for host in selectedHosts {
      for scheme in ["http", "https"] {
        guard let url = URL(string: "\(scheme)://\(host)/") else {
          return false
        }
        let hops = resolver.hops(url: url, settings: settings)
        guard hops == [.loopback(port: port)] else {
          return false
        }
      }
    }
    return !selectedHosts.isEmpty
  }

  func overlayLoopbackSettings(_ settings: CFDictionary?, port: UInt16) -> CFDictionary {
    let mutable = NSMutableDictionary(dictionary: (settings as NSDictionary?) ?? NSDictionary())
    mutable["HTTPEnable"] = 1
    mutable["HTTPProxy"] = "127.0.0.1"
    mutable["HTTPPort"] = NSNumber(value: port)
    mutable["HTTPSEnable"] = 1
    mutable["HTTPSProxy"] = "127.0.0.1"
    mutable["HTTPSPort"] = NSNumber(value: port)
    mutable["SOCKSEnable"] = 0
    mutable["ProxyAutoConfigEnable"] = 0
    mutable["ProxyAutoDiscoveryEnable"] = 0
    return mutable
  }
}
