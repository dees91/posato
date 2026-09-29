import Foundation

enum FirefoxExtensionStatus {
  static func pingResponse() -> Data {
    return response(body: "ok")
  }

  static func statusResponse(seenEpochMilliseconds: UInt64?) -> Data {
    if let seen = seenEpochMilliseconds {
      return response(body: "seen:\(seen)")
    }
    return response(body: "unseen")
  }

  private static func response(body: String) -> Data {
    let encoded = Data(body.utf8)
    let header =
      "HTTP/1.1 200 OK\r\nConnection: close\r\nCache-Control: no-store\r\nContent-Type: text/plain\r\n"
      + "Content-Length: \(encoded.count)\r\n\r\n"
    var data = Data(header.utf8)
    data.append(encoded)
    return data
  }
}
