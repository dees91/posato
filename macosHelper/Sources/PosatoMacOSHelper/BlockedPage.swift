import Foundation

enum BlockedPage {
  static func headline(sessionEndEpochMilliseconds: UInt64?) -> String {
    guard let sessionEndEpochMilliseconds else {
      return "This site is paused"
    }
    let date = Date(timeIntervalSince1970: TimeInterval(sessionEndEpochMilliseconds) / 1_000)
    let formatter = DateFormatter()
    formatter.locale = .current
    formatter.dateStyle = .none
    formatter.timeStyle = .short
    return "This site is paused until \(formatter.string(from: date))"
  }

  static func html(sessionEndEpochMilliseconds: UInt64?) -> Data {
    let title = headline(sessionEndEpochMilliseconds: sessionEndEpochMilliseconds)
    let body = """
      <!doctype html><html lang="en"><head><meta charset="utf-8">\
      <meta name="viewport" content="width=device-width">\
      <title>\(title)</title></head><body><main><h1>\(title)</h1>\
      <p>Return to Posato to change this session.</p></main></body></html>
      """
    return Data(body.utf8)
  }

  static func httpResponse(sessionEndEpochMilliseconds: UInt64?) -> Data {
    return response(
      status: "200 OK",
      contentType: "text/html; charset=utf-8",
      body: html(sessionEndEpochMilliseconds: sessionEndEpochMilliseconds)
    )
  }

  static func connectRejection() -> Data {
    return response(status: "403 Forbidden", contentType: nil, body: Data())
  }

  private static func response(status: String, contentType: String?, body: Data) -> Data {
    var header = "HTTP/1.1 \(status)\r\nConnection: close\r\nCache-Control: no-store\r\n"
    if let contentType {
      header += "Content-Type: \(contentType)\r\n"
    }
    header += "Content-Length: \(body.count)\r\n\r\n"
    var encoded = Data(header.utf8)
    encoded.append(body)
    return encoded
  }
}
