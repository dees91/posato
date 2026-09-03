import XCTest

/// Entry point invoked by `xcodebuild test-without-building`.
///
/// Environment contract (passed by the CLI through `TEST_RUNNER_` prefixes):
/// - `POSATO_SCENARIO_B64`: base64 of the scenario JSON document;
/// - `POSATO_BUNDLE_ID`: bundle identifier of the application under test.
final class DriverTests: XCTestCase {
  private static let defaultBundleIdentifier = "app.posato.ios"
  private static let scenarioVariable = "POSATO_SCENARIO_B64"
  private static let bundleVariable = "POSATO_BUNDLE_ID"

  private var issueSink: ((XCTIssue) -> Void)?

  override func record(_ issue: XCTIssue) {
    if let issueSink {
      issueSink(issue)
    } else {
      super.record(issue)
    }
  }

  func testRunScenario() {
    continueAfterFailure = true
    let environment = ProcessInfo.processInfo.environment
    let bundleIdentifier = environment[Self.bundleVariable] ?? Self.defaultBundleIdentifier
    let result: ScenarioResult
    switch Self.loadScenario(environment[Self.scenarioVariable]) {
    case .scenario(let scenario):
      result = run(scenario, bundleIdentifier: bundleIdentifier)
    case .missing:
      var pingResult = run(Self.pingScenario(), bundleIdentifier: bundleIdentifier)
      pingResult.ok = false
      pingResult.error = DriverError(
        .scenarioMissing,
        "\(Self.scenarioVariable) is not set; ran the built-in ping scenario"
      )
      result = pingResult
    case .invalid(let error):
      result = ScenarioResult(ok: false, steps: [], error: error)
      XCTFail(error.message)
    }
    attachResult(result)
  }

  private func run(_ scenario: Scenario, bundleIdentifier: String) -> ScenarioResult {
    let executor = ScenarioExecutor(scenario: scenario, bundleIdentifier: bundleIdentifier) {
      [weak self] attachment in
      self?.add(attachment)
    }
    issueSink = { issue in executor.recordIssue(issue) }
    defer { issueSink = nil }
    return executor.run()
  }

  private func attachResult(_ result: ScenarioResult) {
    guard let data = try? DriverJSON.encoder(pretty: true).encode(result) else {
      XCTFail("result.json could not be encoded")
      return
    }
    let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.json")
    attachment.name = "result.json"
    attachment.lifetime = .keepAlways
    add(attachment)
    NSLog("[posato-driver] result: %@", String(decoding: data, as: UTF8.self))
  }

  // MARK: - Scenario loading

  private enum LoadedScenario {
    case scenario(Scenario)
    case missing
    case invalid(DriverError)
  }

  private static func loadScenario(_ encoded: String?) -> LoadedScenario {
    guard let encoded, !encoded.isEmpty else {
      return .missing
    }
    guard let data = Data(base64Encoded: encoded, options: .ignoreUnknownCharacters) else {
      return .invalid(DriverError(.scenarioInvalid, "\(scenarioVariable) is not valid base64"))
    }
    do {
      let scenario = try DriverJSON.decoder().decode(Scenario.self, from: data)
      guard scenario.version == 1 else {
        return .invalid(
          DriverError(.scenarioInvalid, "unsupported scenario version \(scenario.version)")
        )
      }
      return .scenario(scenario)
    } catch {
      return .invalid(DriverError(.scenarioInvalid, "scenario JSON is invalid: \(error)"))
    }
  }

  private static func pingScenario() -> Scenario {
    Scenario(steps: [Step(action: "snapshot", name: "ping")])
  }
}
