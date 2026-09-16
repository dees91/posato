import Foundation
import Testing

@testable import PosatoMacOSHelper

@Test(arguments: [nil, 0, 1_800_000_000_000, UInt64.max] as [UInt64?])
func givenFixedPageWhenRenderedThenItHasNoActiveOrNetworkContent(end: UInt64?) throws {
  let html = try #require(
    String(data: BlockedPage.html(sessionEndEpochMilliseconds: end), encoding: .utf8))
  let forbidden = [
    "<script", "<form", "<input", "<button", "<iframe", "<object", "<embed",
    "<link", "<img", "<a ", "href=", "src=", "action=", "url(", "@import",
    "http:", "https:", "//", "javascript:", "data:", "http-equiv",
  ]
  for fragment in forbidden {
    #expect(!html.lowercased().contains(fragment))
  }
}

@Test(arguments: [nil, 0, 1_800_000_000_000, UInt64.max] as [UInt64?])
func givenFixedPageWhenEncodedThenLengthMatchesAndFitsPageSizeBudget(end: UInt64?) throws {
  let body = BlockedPage.html(sessionEndEpochMilliseconds: end)
  let response = BlockedPage.httpResponse(sessionEndEpochMilliseconds: end)
  let encoded = try #require(String(data: response, encoding: .utf8))
  let maximumPageResponseBytes = 16_384
  #expect(response.count <= maximumPageResponseBytes)
  #expect(encoded.hasPrefix("HTTP/1.1 200 OK\r\n"))
  #expect(encoded.contains("Content-Type: text/html; charset=utf-8\r\n"))
  #expect(encoded.contains("Cache-Control: no-store\r\n"))
  #expect(encoded.contains("Content-Length: \(body.count)\r\n\r\n"))
  #expect(response.suffix(body.count) == body)
}

@Test func givenSessionEndWhenRenderedThenItUsesTheCurrentLocaleTime() throws {
  let end: UInt64 = 1_800_000_000_000
  let formatter = DateFormatter()
  formatter.locale = .current
  formatter.dateStyle = .none
  formatter.timeStyle = .short
  let expected = formatter.string(from: Date(timeIntervalSince1970: TimeInterval(end) / 1_000))
  #expect(
    BlockedPage.headline(sessionEndEpochMilliseconds: end)
      == "This site is paused until \(expected)")
  let html = try #require(
    String(data: BlockedPage.html(sessionEndEpochMilliseconds: end), encoding: .utf8))
  #expect(html.contains(expected))
}
