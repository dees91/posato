import Foundation

enum ExactHostPolicy {
  static func matches(host: String, selectedHosts: Set<String>) -> Bool {
    guard !isIPLiteral(host) else {
      return false
    }
    let normalized = normalizedHost(host)
    guard !normalized.isEmpty else {
      return false
    }
    return selectedHosts.contains { candidate in
      normalizedHost(candidate) == normalized
    }
  }

  static func normalizedHost(_ host: String) -> String {
    let lowered = host.lowercased()
    if lowered.hasSuffix("."), !lowered.hasSuffix("..") {
      return String(lowered.dropLast())
    }
    return lowered
  }

  static func isIPLiteral(_ host: String) -> Bool {
    let normalized = normalizedHost(host)
    if normalized.contains(":") {
      return true
    }
    let labels = normalized.split(separator: ".", omittingEmptySubsequences: false)
    return labels.count == 4
      && labels.allSatisfy { label in
        label.count > 0 && label.count <= 3 && label.allSatisfy(\.isNumber)
      }
  }
}
