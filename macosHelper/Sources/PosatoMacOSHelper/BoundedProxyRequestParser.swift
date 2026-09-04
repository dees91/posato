import Foundation

enum BoundedProxyRoute: Equatable {
  case blockedHTTP
  case blockedConnect
  case localBlockedPage
  case tunnel(host: String, port: UInt16, initialData: Data)
  case forward(host: String, port: UInt16, initialData: Data)
}

enum BoundedProxyRequestFraming: Equatable {
  case connect
  case fixedLength(Int)
}

enum BoundedProxyRequestParser {
  static let maximumRequestLength = 65_536
  static let headerTerminator = Data([13, 10, 13, 10])
  static let forwardedMethods = [
    "DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT",
  ]

  struct ParsedHeader {
    let headerEnd: Int
    let parts: [String]
    let headers: [String]
  }

  static func parseHeader(_ requestData: Data) -> ParsedHeader? {
    guard let headerEnd = requestData.range(of: headerTerminator)?.upperBound,
      headerEnd <= maximumRequestLength,
      let header = String(bytes: requestData[..<headerEnd], encoding: .utf8)
    else {
      return nil
    }
    let lines = header.components(separatedBy: "\r\n")
    guard let requestLine = lines.first else {
      return nil
    }
    let parts = requestLine.split(separator: " ", maxSplits: 2).map(String.init)
    guard parts.count == 3, parts[2] == "HTTP/1.1" || parts[2] == "HTTP/1.0" else {
      return nil
    }
    let headers = Array(lines.dropFirst().dropLast(2))
    guard headers.allSatisfy({ $0.contains(":") }) else {
      return nil
    }
    return ParsedHeader(headerEnd: headerEnd, parts: parts, headers: headers)
  }

  static func values(for headerName: String, in lines: [String]) -> [String] {
    lines.compactMap { line in
      let parts = line.split(separator: ":", maxSplits: 1, omittingEmptySubsequences: false)
      guard parts.count == 2,
        parts[0].trimmingCharacters(in: .whitespaces).caseInsensitiveCompare(headerName)
          == .orderedSame
      else {
        return nil
      }
      return parts[1].trimmingCharacters(in: .whitespaces)
    }
  }

  static func forwardedRequest(
    method: String,
    target: String,
    parsed: ParsedHeader,
    destination: (host: String, port: UInt16),
    body: Data
  ) -> Data {
    guard let components = URLComponents(string: target) else {
      return Data()
    }
    var originTarget = components.percentEncodedPath
    if originTarget.isEmpty {
      originTarget = "/"
    }
    if let query = components.percentEncodedQuery {
      originTarget += "?\(query)"
    }
    let filteredHeaders = parsed.headers.filter { line in
      guard let name = line.split(separator: ":", maxSplits: 1).first else {
        return false
      }
      return ![
        "host", "connection", "expect", "proxy-authorization", "proxy-connection",
        "content-length", "keep-alive", "te", "trailer", "upgrade",
      ].contains(name.lowercased())
    }
    var forwardedLines = ["\(method) \(originTarget) \(parsed.parts[2])"]
    let formattedHost = BoundedProxyAuthority.formattedAuthority(
      host: destination.host,
      port: destination.port,
      defaultPort: 80
    )
    forwardedLines.append("Host: \(formattedHost)")
    forwardedLines.append(contentsOf: filteredHeaders)
    if !body.isEmpty || !values(for: "Content-Length", in: parsed.headers).isEmpty {
      forwardedLines.append("Content-Length: \(body.count)")
    }
    forwardedLines.append("Connection: close")
    var forwarded = Data((forwardedLines.joined(separator: "\r\n") + "\r\n\r\n").utf8)
    forwarded.append(body)
    return forwarded
  }
}
