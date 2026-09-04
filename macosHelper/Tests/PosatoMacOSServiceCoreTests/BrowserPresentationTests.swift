import Foundation
import Testing

@testable import PosatoMacOSHelper

@Test func givenSelectedFrontTabWhenPresentedThenTheFixedPageIsWritten() {
  let runner = StubAppleEventRunner(readResult: "1\t2\thttps://example.com/secret")
  var adapter = BrowserPresentationAdapter(
    runner: runner,
    now: { 10 },
    frontmostBrowser: { .safari }
  )
  runner.writeResult = "http://127.0.0.1:4443/blocked"

  let status = adapter.presentBlockedPage(port: 4_443, selectedHosts: ["example.com"])

  #expect(status == .presented)
  #expect(runner.wroteURL?.contains("127.0.0.1:4443/blocked") == true)
  #expect(runner.wroteURL?.contains("example.com") == false)
}

@Test func givenMismatchedTabWhenPresentedThenNoWriteOccurs() {
  let runner = StubAppleEventRunner(readResult: "1\t2\thttps://example.org/")
  var adapter = BrowserPresentationAdapter(
    runner: runner,
    now: { 10 },
    frontmostBrowser: { .safari }
  )

  let status = adapter.presentBlockedPage(port: 4_443, selectedHosts: ["example.com"])

  #expect(status == .skipped)
  #expect(runner.writeSource == nil)
}

@Test func givenRateLimitWhenPresentedTwiceThenTheSecondAttemptIsSkipped() {
  let runner = StubAppleEventRunner(readResult: "1\t2\thttps://example.com/")
  runner.writeResult = "http://127.0.0.1:4443/blocked"
  var clock: TimeInterval = 10
  var adapter = BrowserPresentationAdapter(
    runner: runner,
    now: { clock },
    frontmostBrowser: { .safari }
  )

  _ = adapter.presentBlockedPage(port: 4_443, selectedHosts: ["example.com"])
  runner.writeSource = nil
  clock += 0.5
  let second = adapter.presentBlockedPage(port: 4_443, selectedHosts: ["example.com"])

  #expect(second == .skipped)
}

@Test func givenAutomationDeniedWhenPresentedThenPermissionIsReportedWithoutAWrite() {
  let runner = StubAppleEventRunner(readResult: "1\t2\thttps://example.com/")
  runner.readError = AppleEventRunnerError.permissionDenied
  var adapter = BrowserPresentationAdapter(
    runner: runner,
    now: { 10 },
    frontmostBrowser: { .safari }
  )

  let status = adapter.presentBlockedPage(port: 4_443, selectedHosts: ["example.com"])

  #expect(status == .permissionDenied)
  #expect(runner.writeSource == nil)
}

@Test func givenPresentationTypesWhenRenderedThenHostsStayRedacted() {
  let status = BrowserPresentationStatus.presented
  #expect(!String(describing: status).contains("example.com"))
}

private final class StubAppleEventRunner: AppleEventRunning {
  var readResult: String
  var writeResult = ""
  var readError: Error?
  var writeSource: String?
  var wroteURL: String? {
    writeSource
  }

  init(readResult: String) {
    self.readResult = readResult
  }

  func run(_ source: String) throws -> String {
    if source.contains("return (id of") {
      if let readError {
        throw readError
      }
      return readResult
    }
    writeSource = source
    return writeResult
  }
}
