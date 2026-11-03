import Foundation

extension BoundedProxyRequestParser {
  static func framing(requestData: Data) -> BoundedProxyRequestFraming? {
    guard let parsed = parseHeader(requestData) else {
      return nil
    }
    let method = parsed.parts[0].uppercased()
    let contentLengths = values(for: "Content-Length", in: parsed.headers)
    let expectations = values(for: "Expect", in: parsed.headers)
    guard values(for: "Transfer-Encoding", in: parsed.headers).isEmpty else {
      return nil
    }
    if method == "CONNECT" {
      guard contentLengths.isEmpty else {
        return nil
      }
      return .connect
    }
    guard contentLengths.count <= 1 else {
      return nil
    }
    guard
      expectations.isEmpty
        || (expectations.count == 1
          && expectations[0].caseInsensitiveCompare("100-continue") == .orderedSame)
    else {
      return nil
    }
    return fixedLengthFraming(parsed: parsed, contentLengths: contentLengths)
  }

  static func route(
    requestData: Data,
    selectedHosts: Set<String>,
    listenerPort: UInt16,
    opening: @escaping (String) -> (hour: Int, minute: Int)? = { _ in nil }
  ) -> BoundedProxyRoute? {
    guard let parsed = parseHeader(requestData) else {
      return nil
    }
    let hostValues = values(for: "Host", in: parsed.headers)
    guard hostValues.count == 1 else {
      return nil
    }
    let method = parsed.parts[0].uppercased()
    let target = parsed.parts[1]
    if method == "CONNECT" {
      return connectRoute(
        ConnectRouteInput(
          requestData: requestData,
          parsed: parsed,
          target: target,
          hostValue: hostValues[0],
          selectedHosts: selectedHosts,
          listenerPort: listenerPort
        )
      )
    }
    guard case .fixedLength(let requiredLength) = framing(requestData: requestData),
      requestData.count == requiredLength
    else {
      return nil
    }
    let body = Data(requestData[parsed.headerEnd...])
    if target.hasPrefix("/") {
      return originFormRoute(
        OriginRouteInput(
          method: method,
          target: target,
          hostValue: hostValues[0],
          body: body,
          listenerPort: listenerPort
        )
      )
    }
    return absoluteFormRoute(
      AbsoluteRouteInput(
        method: method,
        target: target,
        hostValue: hostValues[0],
        parsed: parsed,
        body: body,
        selectedHosts: selectedHosts,
        listenerPort: listenerPort,
        opening: opening
      )
    )
  }

  private struct ConnectRouteInput {
    let requestData: Data
    let parsed: ParsedHeader
    let target: String
    let hostValue: String
    let selectedHosts: Set<String>
    let listenerPort: UInt16
  }

  private struct OriginRouteInput {
    let method: String
    let target: String
    let hostValue: String
    let body: Data
    let listenerPort: UInt16
  }

  private struct AbsoluteRouteInput {
    let method: String
    let target: String
    let hostValue: String
    let parsed: ParsedHeader
    let body: Data
    let selectedHosts: Set<String>
    let listenerPort: UInt16
    let opening: (String) -> (hour: Int, minute: Int)?
  }

  /// Exactly four ASCII digits forming a local time from 0000 to 2359.
  static func openingTime(path: String) -> (hour: Int, minute: Int)? {
    let prefix = "/blocked/opens/"
    guard path.hasPrefix(prefix) else {
      return nil
    }
    let digits = Array(path.utf8.dropFirst(prefix.utf8.count))
    guard digits.count == 4, digits.allSatisfy({ $0 >= 0x30 && $0 <= 0x39 }) else {
      return nil
    }
    let values = digits.map { Int($0) - 0x30 }
    let hour = values[0] * 10 + values[1]
    let minute = values[2] * 10 + values[3]
    guard hour <= 23, minute <= 59 else {
      return nil
    }
    return (hour, minute)
  }

  private static func fixedLengthFraming(
    parsed: ParsedHeader,
    contentLengths: [String]
  ) -> BoundedProxyRequestFraming? {
    let contentLength: Int
    if let rawLength = contentLengths.first {
      guard let parsedLength = Int(rawLength), parsedLength >= 0 else {
        return nil
      }
      contentLength = parsedLength
    } else {
      contentLength = 0
    }
    let (requiredLength, overflow) = parsed.headerEnd.addingReportingOverflow(contentLength)
    guard !overflow, requiredLength <= maximumRequestLength else {
      return nil
    }
    return .fixedLength(requiredLength)
  }

  private static func connectRoute(_ input: ConnectRouteInput) -> BoundedProxyRoute? {
    guard framing(requestData: input.requestData) == .connect,
      let destination = BoundedProxyAuthority.parseAuthority(input.target, defaultPort: 443),
      let headerHost = BoundedProxyAuthority.parseAuthority(input.hostValue, defaultPort: 443),
      BoundedProxyAuthority.sameAuthority(destination, headerHost),
      !(BoundedProxyAuthority.isLoopback(destination.host)
        && destination.port == input.listenerPort)
    else {
      return nil
    }
    if ExactHostPolicy.matches(host: destination.host, selectedHosts: input.selectedHosts) {
      return .blockedConnect
    }
    if let addresses = BoundedProxyAuthority.relayLoopbackAddresses(destination.host) {
      return .loopbackTunnel(
        addresses: addresses,
        port: destination.port,
        initialData: Data(input.requestData[input.parsed.headerEnd...])
      )
    }
    guard destination.port == 443 else {
      return nil
    }
    return .tunnel(
      host: destination.host,
      port: destination.port,
      initialData: Data(input.requestData[input.parsed.headerEnd...])
    )
  }

  private static func originFormRoute(_ input: OriginRouteInput) -> BoundedProxyRoute? {
    guard input.method == "GET",
      let destination = BoundedProxyAuthority.parseAuthority(input.hostValue, defaultPort: 80),
      BoundedProxyAuthority.isLoopback(destination.host),
      destination.port == input.listenerPort,
      input.body.isEmpty
    else {
      return nil
    }
    if input.target == "/blocked" {
      return .localBlockedPage
    }
    guard let time = openingTime(path: input.target) else {
      return nil
    }
    return .localOpensPage(hour: time.hour, minute: time.minute)
  }

  private static func absoluteFormRoute(_ input: AbsoluteRouteInput) -> BoundedProxyRoute? {
    guard forwardedMethods.contains(input.method),
      let components = URLComponents(string: input.target),
      components.scheme?.lowercased() == "http",
      components.user == nil,
      components.password == nil,
      components.fragment == nil,
      let destination = BoundedProxyAuthority.absoluteAuthority(in: input.target, defaultPort: 80),
      let componentHost = components.host.map(BoundedProxyAuthority.stripIPv6Brackets),
      ExactHostPolicy.normalizedHost(componentHost)
        == ExactHostPolicy.normalizedHost(destination.host),
      let headerHost = BoundedProxyAuthority.parseAuthority(input.hostValue, defaultPort: 80),
      BoundedProxyAuthority.sameAuthority(destination, headerHost),
      values(for: "Upgrade", in: input.parsed.headers).isEmpty
    else {
      return nil
    }
    if ExactHostPolicy.matches(host: destination.host, selectedHosts: input.selectedHosts) {
      if let time = input.opening(destination.host) {
        return .blockedHTTPOpens(hour: time.hour, minute: time.minute)
      }
      return .blockedHTTP
    }
    let isOwnListener =
      BoundedProxyAuthority.isLoopback(destination.host)
      && destination.port == input.listenerPort
    if isOwnListener {
      return nil
    }
    let initialData = forwardedRequest(
      method: input.method,
      target: input.target,
      parsed: input.parsed,
      destination: destination,
      body: input.body
    )
    if let addresses = BoundedProxyAuthority.relayLoopbackAddresses(destination.host) {
      return .loopbackForward(
        addresses: addresses, port: destination.port, initialData: initialData)
    }
    guard destination.port == 80 else {
      return nil
    }
    return .forward(host: destination.host, port: destination.port, initialData: initialData)
  }
}
