import Foundation

enum BoundedProxyAuthority {
  static func absoluteAuthority(
    in absoluteTarget: String,
    defaultPort: UInt16
  ) -> (host: String, port: UInt16)? {
    guard let separator = absoluteTarget.range(of: "://") else {
      return nil
    }
    let authorityStart = separator.upperBound
    let authorityEnd =
      absoluteTarget[authorityStart...].firstIndex(where: { "/?#".contains($0) })
      ?? absoluteTarget.endIndex
    return parseAuthority(
      String(absoluteTarget[authorityStart..<authorityEnd]),
      defaultPort: defaultPort
    )
  }

  static func parseAuthority(
    _ rawAuthority: String,
    defaultPort: UInt16
  ) -> (host: String, port: UInt16)? {
    let authority = rawAuthority.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !authority.isEmpty else {
      return nil
    }
    if authority.hasPrefix("[") {
      return parseBracketedAuthority(authority, defaultPort: defaultPort)
    }
    guard authority.filter({ $0 == ":" }).count <= 1 else {
      return nil
    }
    guard let colon = authority.lastIndex(of: ":") else {
      guard isCanonicalASCIIHost(authority) else {
        return nil
      }
      return (authority, defaultPort)
    }
    let host = String(authority[..<colon])
    let rawPort = authority[authority.index(after: colon)...]
    guard isCanonicalASCIIHost(host), let port = UInt16(rawPort), port > 0 else {
      return nil
    }
    return (host, port)
  }

  static func sameAuthority(
    _ lhs: (host: String, port: UInt16),
    _ rhs: (host: String, port: UInt16)
  ) -> Bool {
    ExactHostPolicy.normalizedHost(lhs.host) == ExactHostPolicy.normalizedHost(rhs.host)
      && lhs.port == rhs.port
  }

  static func formattedAuthority(host: String, port: UInt16, defaultPort: UInt16) -> String {
    let formattedHost = host.contains(":") ? "[\(host)]" : host
    return port == defaultPort ? formattedHost : "\(formattedHost):\(port)"
  }

  static func stripIPv6Brackets(_ host: String) -> String {
    guard host.hasPrefix("["), host.hasSuffix("]") else {
      return host
    }
    return String(host.dropFirst().dropLast())
  }

  static func isLoopback(_ host: String) -> Bool {
    let normalized = ExactHostPolicy.normalizedHost(host)
    return normalized == "127.0.0.1" || normalized == "localhost" || normalized == "::1"
  }

  private static func parseBracketedAuthority(
    _ authority: String,
    defaultPort: UInt16
  ) -> (host: String, port: UInt16)? {
    guard let closing = authority.firstIndex(of: "]") else {
      return nil
    }
    let host = String(authority[authority.index(after: authority.startIndex)..<closing])
    let remainder = authority[authority.index(after: closing)...]
    guard isCanonicalASCIIHost(host) else {
      return nil
    }
    if remainder.isEmpty {
      return (host, defaultPort)
    }
    guard remainder.first == ":", let port = UInt16(remainder.dropFirst()), port > 0 else {
      return nil
    }
    return (host, port)
  }

  private static func isCanonicalASCIIHost(_ host: String) -> Bool {
    guard !host.isEmpty, host.utf8.count <= 253, host.unicodeScalars.allSatisfy(\.isASCII) else {
      return false
    }
    if host.contains(":") {
      return host.allSatisfy({ $0.isHexDigit || $0 == ":" || $0 == "." })
    }
    return isCanonicalDNSHost(host)
  }

  private static func isCanonicalDNSHost(_ host: String) -> Bool {
    let normalized = host.hasSuffix(".") && !host.hasSuffix("..") ? String(host.dropLast()) : host
    guard !normalized.isEmpty, !host.hasSuffix("..") else {
      return false
    }
    return normalized.split(separator: ".", omittingEmptySubsequences: false).allSatisfy { label in
      isCanonicalDNSLabel(label)
    }
  }

  private static func isCanonicalDNSLabel(_ label: Substring) -> Bool {
    guard !label.isEmpty, label.utf8.count <= 63,
      label.first?.isLetter == true || label.first?.isNumber == true,
      label.last?.isLetter == true || label.last?.isNumber == true
    else {
      return false
    }
    return label.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-") })
  }
}
