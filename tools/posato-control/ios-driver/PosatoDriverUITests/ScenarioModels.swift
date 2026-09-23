import Foundation

/// Selects one element in the accessibility tree of the application under test.
///
/// All fields are optional; a query without constraints matches every element.
final class ElementQuery: Codable, Sendable {
  let id: String?
  let text: String?
  let textContains: String?
  let role: String?
  let index: Int?
  let within: ElementQuery?
  let near: ElementQuery?

  init(
    id: String? = nil,
    text: String? = nil,
    textContains: String? = nil,
    role: String? = nil,
    index: Int? = nil,
    within: ElementQuery? = nil,
    near: ElementQuery? = nil
  ) {
    self.id = id
    self.text = text
    self.textContains = textContains
    self.role = role
    self.index = index
    self.within = within
    self.near = near
  }
}

/// Launch options applied before the first step and by the `relaunch` step.
struct LaunchConfiguration: Codable {
  var terminateExisting = true
  var fresh = false
  var arguments: [String] = []
  var environment: [String: String] = [:]

  init() {}

  init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    terminateExisting =
      try container.decodeIfPresent(Bool.self, forKey: .terminateExisting) ?? true
    fresh = try container.decodeIfPresent(Bool.self, forKey: .fresh) ?? false
    arguments = try container.decodeIfPresent([String].self, forKey: .arguments) ?? []
    environment =
      try container.decodeIfPresent([String: String].self, forKey: .environment) ?? [:]
  }
}

/// Defaults shared by every step of a scenario.
struct ScenarioDefaults: Codable {
  var timeoutSeconds: Double = 10

  init() {}

  init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    timeoutSeconds = try container.decodeIfPresent(Double.self, forKey: .timeoutSeconds) ?? 10
  }
}

/// Evidence captured when a step fails.
struct FailureCapture: Codable {
  var screenshot = true
  var snapshot = true

  init() {}

  init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    screenshot = try container.decodeIfPresent(Bool.self, forKey: .screenshot) ?? true
    snapshot = try container.decodeIfPresent(Bool.self, forKey: .snapshot) ?? true
  }
}

/// One scenario step. The `action` field selects the behaviour; the remaining
/// fields are read only by the actions that need them.
struct Step: Codable {
  var action: String
  var name: String?
  var timeoutSeconds: Double?
  var state: String?
  var query: ElementQuery?
  var text: String?
  var clear: Bool?
  var submit: Bool?
  var key: String?
  var modifiers: [String]?
  var maxDepth: Int?
  var seconds: Double?
  var orientation: String?

  init(action: String, name: String? = nil, query: ElementQuery? = nil) {
    self.action = action
    self.name = name
    self.query = query
  }
}

/// The scenario document passed to the driver through `POSATO_SCENARIO_B64`.
struct Scenario: Codable {
  var version: Int
  var launch = LaunchConfiguration()
  var defaults = ScenarioDefaults()
  var onFailure = FailureCapture()
  var continueOnFailure = false
  var steps: [Step]

  init(version: Int = 1, steps: [Step]) {
    self.version = version
    self.steps = steps
  }

  init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    version = try container.decode(Int.self, forKey: .version)
    launch = try container.decodeIfPresent(LaunchConfiguration.self, forKey: .launch) ?? .init()
    defaults = try container.decodeIfPresent(ScenarioDefaults.self, forKey: .defaults) ?? .init()
    onFailure = try container.decodeIfPresent(FailureCapture.self, forKey: .onFailure) ?? .init()
    continueOnFailure =
      try container.decodeIfPresent(Bool.self, forKey: .continueOnFailure) ?? false
    steps = try container.decode([Step].self, forKey: .steps)
  }
}

/// Stable error codes shared with the command-line client.
enum DriverErrorCode: String {
  case elementNotFound = "ELEMENT_NOT_FOUND"
  case waitTimeout = "WAIT_TIMEOUT"
  case assertionFailed = "ASSERTION_FAILED"
  case unsupportedStep = "UNSUPPORTED_STEP"
  case scenarioMissing = "SCENARIO_MISSING"
  case scenarioInvalid = "SCENARIO_INVALID"
  case stepFailed = "STEP_FAILED"
}

/// A machine-readable failure reported in the result document.
struct DriverError: Codable, Error, Equatable {
  var code: String
  var message: String

  init(_ code: DriverErrorCode, _ message: String) {
    self.code = code.rawValue
    self.message = message
  }
}

/// Outcome of one executed step.
struct StepResult: Codable {
  var index: Int
  var name: String?
  var action: String
  var ok: Bool
  var durationMs: Int
  var artifacts: [String]
  var error: DriverError?

  func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(index, forKey: .index)
    try container.encode(name, forKey: .name)
    try container.encode(action, forKey: .action)
    try container.encode(ok, forKey: .ok)
    try container.encode(durationMs, forKey: .durationMs)
    try container.encode(artifacts, forKey: .artifacts)
    try container.encode(error, forKey: .error)
  }
}

/// The result document attached as `result.json`.
struct ScenarioResult: Codable {
  var ok: Bool
  var steps: [StepResult]
  var error: DriverError?

  func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(ok, forKey: .ok)
    try container.encode(steps, forKey: .steps)
    try container.encode(error, forKey: .error)
  }
}

/// Element bounds in points, in screen coordinates.
struct SnapshotFrame: Codable, Equatable {
  var x: Double
  var y: Double
  var w: Double
  var h: Double
}

/// One node of a serialized accessibility tree.
struct SnapshotNode: Codable, Equatable {
  var role: String
  var platformRole: String
  var id: String?
  var label: String?
  var value: String?
  var placeholder: String?
  var enabled: Bool
  var focused: Bool
  var frame: SnapshotFrame
  var children: [SnapshotNode]
}

/// JSON coding helpers with a stable key order.
enum DriverJSON {
  static func decoder() -> JSONDecoder {
    JSONDecoder()
  }

  static func encoder(pretty: Bool) -> JSONEncoder {
    let encoder = JSONEncoder()
    encoder.outputFormatting = pretty ? [.sortedKeys, .prettyPrinted] : [.sortedKeys]
    encoder.outputFormatting.insert(.withoutEscapingSlashes)
    return encoder
  }
}
