import XCTest

/// Executes scenario steps one at a time against the application under test.
///
/// Step failures never fail the XCTest; they are reported through the
/// returned `ScenarioResult`. XCUITest interaction issues raised while a step
/// runs must be forwarded to `recordIssue(_:)` so that they become step
/// failures instead of test failures.
final class ScenarioExecutor {
  typealias Attach = (XCTAttachment) -> Void

  private static let focusTimeout: TimeInterval = 2
  private static let pollInterval: TimeInterval = 0.25
  private static let settleInterval: TimeInterval = 0.5
  private static let scrollAttempts = 10
  private static let revealAttempts = 3
  private static let keyboardSettle: TimeInterval = 0.3
  private static let extraDeletes = 3
  private static let rowWeight: CGFloat = 3
  private static let scrollSettle: TimeInterval = 0.5

  private let scenario: Scenario
  private let app: XCUIApplication
  private let attach: Attach
  private var recordedIssues: [String] = []

  init(scenario: Scenario, bundleIdentifier: String, attach: @escaping Attach) {
    self.scenario = scenario
    self.app = XCUIApplication(bundleIdentifier: bundleIdentifier)
    self.attach = attach
  }

  func recordIssue(_ issue: XCTIssue) {
    recordedIssues.append(issue.compactDescription)
  }

  func run() -> ScenarioResult {
    if let error = launch() {
      return ScenarioResult(ok: false, steps: [], error: error)
    }
    var results: [StepResult] = []
    var firstFailure: DriverError?
    for (index, step) in scenario.steps.enumerated() {
      let result = execute(step, index: index)
      results.append(result)
      guard !result.ok else { continue }
      if firstFailure == nil, let error = result.error {
        let label = result.name.map { " (\($0))" } ?? ""
        firstFailure = DriverError(
          DriverErrorCode(rawValue: error.code) ?? .stepFailed,
          "step \(index)\(label) \(step.action): \(error.message)"
        )
      }
      if !scenario.continueOnFailure {
        break
      }
    }
    return ScenarioResult(ok: firstFailure == nil, steps: results, error: firstFailure)
  }

  // MARK: - Launch

  private func launch() -> DriverError? {
    recordedIssues = []
    let configuration = scenario.launch
    let running = app.state == .runningForeground || app.state == .runningBackground
    let relaunch = !running || configuration.terminateExisting || configuration.fresh
    if running, relaunch {
      app.terminate()
    }
    app.launchArguments = configuration.arguments
    app.launchEnvironment = configuration.environment
    if relaunch {
      app.launch()
    } else {
      app.activate()
    }
    if let issue = recordedIssues.first {
      return DriverError(.stepFailed, "launch failed: \(issue)")
    }
    guard app.state == .runningForeground else {
      return DriverError(.stepFailed, "launch failed: application state \(app.state.rawValue)")
    }
    return nil
  }

  // MARK: - Step dispatch

  private func execute(_ step: Step, index: Int) -> StepResult {
    let start = Date()
    recordedIssues = []
    var artifacts: [String] = []
    var failure: DriverError?
    do {
      try perform(step, index: index, artifacts: &artifacts)
      if let issue = recordedIssues.first {
        throw DriverError(.stepFailed, issue)
      }
    } catch let error as DriverError {
      failure = error
    } catch {
      failure = DriverError(.stepFailed, String(describing: error))
    }
    if failure != nil {
      captureFailure(index: index, artifacts: &artifacts)
    }
    let durationMs = Int((Date().timeIntervalSince(start) * 1000).rounded())
    return StepResult(
      index: index,
      name: step.name,
      action: step.action,
      ok: failure == nil,
      durationMs: durationMs,
      artifacts: artifacts,
      error: failure
    )
  }

  private func perform(_ step: Step, index: Int, artifacts: inout [String]) throws {
    let timeout = step.timeoutSeconds ?? scenario.defaults.timeoutSeconds
    switch step.action {
    case "waitFor":
      try waitFor(step, timeout: timeout)
    case "tap":
      tap(try existingElement(step.query, timeout: timeout, action: step.action))
    case "type":
      try type(step, timeout: timeout)
    case "press":
      try press(step)
    case "assert":
      try assert(step)
    case "screenshot":
      artifacts.append(attachScreenshot(named: "screenshot-\(index)-\(step.name ?? "screenshot")"))
    case "snapshot":
      let root = try step.query.map {
        try existingElement($0, timeout: timeout, action: step.action)
      }
      let name = "snapshot-\(index)-\(step.name ?? "snapshot")"
      artifacts.append(try attachSnapshot(named: name, root: root ?? app, maxDepth: step.maxDepth))
    case "sleep":
      guard let seconds = step.seconds else {
        throw DriverError(.scenarioInvalid, "sleep requires seconds")
      }
      Thread.sleep(forTimeInterval: seconds)
    case "scrollTo":
      try scrollTo(step)
    case "terminate":
      app.terminate()
    case "relaunch":
      if let error = launch() {
        throw error
      }
    default:
      throw DriverError(.unsupportedStep, "unknown action '\(step.action)'")
    }
  }

  // MARK: - Actions

  private func waitFor(_ step: Step, timeout: TimeInterval) throws {
    guard let state = step.state else {
      throw DriverError(.scenarioInvalid, "waitFor requires state")
    }
    if state == "settled" {
      try waitForSettled(timeout: timeout)
      return
    }
    guard ["exists", "absent", "enabled", "disabled"].contains(state) else {
      throw DriverError(.unsupportedStep, "unknown waitFor state '\(state)'")
    }
    let satisfied = poll(timeout: timeout) {
      Self.holds(state, self.resolve(step.query, action: step.action))
    }
    guard satisfied else {
      throw DriverError(
        .waitTimeout,
        "element did not become \(state) within \(timeout) s: \(describe(step.query))"
      )
    }
  }

  private func waitForSettled(timeout: TimeInterval) throws {
    let deadline = Date().addingTimeInterval(timeout)
    var previous = SnapshotSerializer.node(from: try app.snapshot())
    while Date() < deadline {
      Thread.sleep(forTimeInterval: Self.settleInterval)
      let current = SnapshotSerializer.node(from: try app.snapshot())
      if current == previous {
        return
      }
      previous = current
    }
    throw DriverError(.waitTimeout, "the application did not settle within \(timeout) s")
  }

  private func tap(_ element: XCUIElement) {
    if element.isHittable {
      element.tap()
    } else {
      element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    }
  }

  private func type(_ step: Step, timeout: TimeInterval) throws {
    guard let text = step.text else {
      throw DriverError(.scenarioInvalid, "type requires text")
    }
    let field = try existingElement(step.query, timeout: timeout, action: step.action)
    let currentValue = SnapshotSerializer.valueText(field.value) ?? ""
    let hasText = !currentValue.isEmpty && currentValue != field.placeholderValue
    let clear = step.clear == true && hasText
    try focus(field, atTrailingEdge: clear)
    // Type through the application so that the field query is not re-resolved
    // after focusing changed its attributes (Compose appends the value to the
    // label of a focused field).
    if clear {
      // The first keystrokes can be dropped while the keyboard animates in, so
      // wait briefly and send a few extra deletes; deleting past the start is harmless.
      Thread.sleep(forTimeInterval: Self.keyboardSettle)
      let deletes = currentValue.count + Self.extraDeletes
      app.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: deletes))
    }
    app.typeText(text)
    if step.submit == true {
      app.typeText("\n")
    }
  }

  private func focus(_ field: XCUIElement, atTrailingEdge: Bool) throws {
    if SnapshotSerializer.keyboardFocus(of: try field.snapshot()) {
      return
    }
    let offset = CGVector(dx: atTrailingEdge ? 0.9 : 0.5, dy: 0.5)
    field.coordinate(withNormalizedOffset: offset).tap()
    _ = poll(timeout: Self.focusTimeout) { self.app.keyboards.count > 0 }
  }

  private func press(_ step: Step) throws {
    guard let key = step.key else {
      throw DriverError(.scenarioInvalid, "press requires key")
    }
    switch key {
    case "home":
      XCUIDevice.shared.press(.home)
    case "return":
      app.typeText("\n")
    case "delete":
      app.typeText(XCUIKeyboardKey.delete.rawValue)
    case "space":
      app.typeText(" ")
    case "escape":
      try typeKey(.escape, named: key)
    case "tab":
      try typeKey(.tab, named: key)
    case "volumeUp", "volumeDown":
      try pressVolume(key)
    default:
      throw DriverError(.unsupportedStep, "unknown key '\(key)'")
    }
  }

  private func pressVolume(_ key: String) throws {
    #if targetEnvironment(simulator)
      throw DriverError(.unsupportedStep, "key '\(key)' is not available on the Simulator")
    #else
      XCUIDevice.shared.press(key == "volumeUp" ? .volumeUp : .volumeDown)
    #endif
  }

  private func typeKey(_ key: XCUIKeyboardKey, named name: String) throws {
    recordedIssues = []
    app.typeText(key.rawValue)
    if let issue = recordedIssues.first {
      recordedIssues = []
      throw DriverError(.unsupportedStep, "key '\(name)' is not available on iOS: \(issue)")
    }
  }

  private func assert(_ step: Step) throws {
    guard let state = step.state else {
      throw DriverError(.scenarioInvalid, "assert requires state")
    }
    guard ["exists", "absent", "enabled", "disabled"].contains(state) else {
      throw DriverError(.unsupportedStep, "unknown assert state '\(state)'")
    }
    guard Self.holds(state, resolve(step.query, action: step.action)) else {
      throw DriverError(
        .assertionFailed, "expected element to be \(state): \(describe(step.query))")
    }
  }

  private func scrollTo(_ step: Step) throws {
    for _ in 0..<Self.scrollAttempts {
      if let element = resolve(step.query, action: step.action), element.exists, element.isHittable
      {
        return
      }
      app.swipeUp()
    }
    throw DriverError(
      .elementNotFound,
      "element not reached after \(Self.scrollAttempts) swipes: \(describe(step.query))"
    )
  }

  // MARK: - Evidence

  private func captureFailure(index: Int, artifacts: inout [String]) {
    if scenario.onFailure.screenshot {
      artifacts.append(attachScreenshot(named: "failure-\(index)-screenshot"))
    }
    if scenario.onFailure.snapshot,
      let name = try? attachSnapshot(named: "failure-\(index)-snapshot", root: app, maxDepth: nil)
    {
      artifacts.append(name)
    }
  }

  private func attachScreenshot(named name: String) -> String {
    let fileName = "\(name).png"
    let attachment = XCTAttachment(
      data: XCUIScreen.main.screenshot().pngRepresentation,
      uniformTypeIdentifier: "public.png"
    )
    attachment.name = fileName
    attachment.lifetime = .keepAlways
    attach(attachment)
    return fileName
  }

  private func attachSnapshot(named name: String, root: XCUIElement, maxDepth: Int?) throws
    -> String
  {
    let fileName = "\(name).json"
    let node = SnapshotSerializer.node(from: try root.snapshot(), maxDepth: maxDepth)
    let data = try DriverJSON.encoder(pretty: false).encode(node)
    let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.json")
    attachment.name = fileName
    attachment.lifetime = .keepAlways
    attach(attachment)
    return fileName
  }

  // MARK: - Element resolution

  /// The element for `query`, waiting up to `timeout` for it to exist.
  ///
  /// Compose drops the accessibility label of content clipped at the screen
  /// edge, so when a `near` anchor is visible but the target is not, the
  /// content is scrolled a few times to bring the anchor toward the centre.
  private func existingElement(_ query: ElementQuery?, timeout: TimeInterval, action: String) throws
    -> XCUIElement
  {
    guard let query else {
      throw DriverError(.scenarioInvalid, "\(action) requires a query")
    }
    try validateRoles(query)
    var element = resolve(query, action: action)
    var reveals = 0
    let found = poll(timeout: timeout) {
      if let current = element, current.exists { return true }
      if let near = query.near, reveals < Self.revealAttempts, self.app.keyboards.count == 0,
        let anchor = self.resolve(near, action: action), anchor.exists
      {
        reveals += 1
        self.reveal(anchor)
      }
      element = self.resolve(query, action: action)
      return element?.exists ?? false
    }
    guard found, let resolved = element else {
      throw DriverError(.elementNotFound, "no element matches \(describe(query))")
    }
    return resolved
  }

  /// Scrolls so that `anchor` moves toward the vertical centre of the screen.
  private func reveal(_ anchor: XCUIElement) {
    let screenMidY = app.frame.midY
    let anchorMidY = anchor.frame.midY
    if anchorMidY > screenMidY {
      app.swipeUp()
    } else {
      app.swipeDown()
    }
    Thread.sleep(forTimeInterval: Self.scrollSettle)
  }

  /// The element for `query`, or `nil` when the query cannot be resolved right now.
  private func resolve(_ query: ElementQuery?, action: String) -> XCUIElement? {
    try? element(for: query, action: action)
  }

  private func element(for query: ElementQuery?, action: String) throws -> XCUIElement {
    guard let query else {
      throw DriverError(.scenarioInvalid, "\(action) requires a query")
    }
    try validateRole(query.role)
    let base = try query.within.map { try scope(for: $0) } ?? app
    let matches = base.descendants(matching: .any).matching(
      Self.predicate(for: query, includeRole: true))
    if let near = query.near {
      let anchor = try element(for: near, action: action)
      return try nearest(of: matches, to: anchor, query: query)
    }
    if let index = query.index {
      return matches.element(boundBy: index)
    }
    return matches.firstMatch
  }

  /// Resolves the scope of a `within` query: the anchor is the first element
  /// matching the inner query's text and identifier constraints; with a role,
  /// the scope is the deepest element of that role containing the anchor,
  /// otherwise the anchor itself.
  private func scope(for inner: ElementQuery) throws -> XCUIElement {
    try validateRole(inner.role)
    let base = try inner.within.map { try scope(for: $0) } ?? app
    let anchorPredicate = Self.predicate(for: inner, includeRole: false)
    guard let role = inner.role, role != "any" else {
      let anchors = base.descendants(matching: .any).matching(anchorPredicate)
      return inner.index.map { anchors.element(boundBy: $0) } ?? anchors.firstMatch
    }
    let containers = base.descendants(matching: .any).matching(Self.rolePredicate(role))
      .containing(anchorPredicate)
    guard let deepest = containers.allElementsBoundByIndex.last else {
      throw DriverError(
        .elementNotFound, "no \(role) contains an element matching \(describe(inner))")
    }
    return deepest
  }

  /// The match closest to `anchor` by frame centre; `index` picks a farther one.
  private func nearest(of matches: XCUIElementQuery, to anchor: XCUIElement, query: ElementQuery)
    throws -> XCUIElement
  {
    guard anchor.exists else {
      throw DriverError(.elementNotFound, "no element matches \(describe(query.near))")
    }
    let anchorFrame = anchor.frame
    let sorted = matches.allElementsBoundByIndex.sorted {
      Self.distance($0.frame, anchorFrame) < Self.distance($1.frame, anchorFrame)
    }
    let index = query.index ?? 0
    guard sorted.indices.contains(index) else {
      throw DriverError(.elementNotFound, "no element matches \(describe(query))")
    }
    return sorted[index]
  }

  /// Row-biased distance: elements on the same line as the anchor win over
  /// elements in neighbouring rows, and vertical stacking still resolves the
  /// closest control below or above a header.
  private static func distance(_ a: CGRect, _ b: CGRect) -> CGFloat {
    abs(a.midY - b.midY) * Self.rowWeight + abs(a.midX - b.midX)
  }

  /// Validates the role of `query` and of its nested `near` and `within` queries up front, so a
  /// typo fails immediately instead of polling to the timeout.
  private func validateRoles(_ query: ElementQuery) throws {
    try validateRole(query.role)
    if let near = query.near { try validateRoles(near) }
    if let within = query.within { try validateRoles(within) }
  }

  private func validateRole(_ role: String?) throws {
    if let role, !ElementRoles.knownRoles.contains(role) {
      throw DriverError(.unsupportedStep, "unknown role '\(role)'")
    }
  }

  private static func predicate(for query: ElementQuery, includeRole: Bool) -> NSPredicate {
    NSPredicate { candidate, _ in
      guard let attributes = candidate as? XCUIElementAttributes else { return false }
      return matches(attributes, query: query, includeRole: includeRole)
    }
  }

  private static func rolePredicate(_ role: String) -> NSPredicate {
    NSPredicate { candidate, _ in
      guard let attributes = candidate as? XCUIElementAttributes else { return false }
      return ElementRoles.role(for: attributes.elementType) == role
    }
  }

  private static func matches(
    _ attributes: XCUIElementAttributes, query: ElementQuery, includeRole: Bool
  ) -> Bool {
    if includeRole, let role = query.role, role != "any",
      ElementRoles.role(for: attributes.elementType) != role
    {
      return false
    }
    if let id = query.id, attributes.identifier != id {
      return false
    }
    let texts = SnapshotSerializer.texts(of: attributes)
    if let text = query.text, !texts.contains(text) {
      return false
    }
    if let fragment = query.textContains, !texts.contains(where: { $0.contains(fragment) }) {
      return false
    }
    return true
  }

  private static func holds(_ state: String, _ element: XCUIElement?) -> Bool {
    let exists = element?.exists ?? false
    switch state {
    case "exists":
      return exists
    case "absent":
      return !exists
    case "enabled":
      return exists && element?.isEnabled == true
    case "disabled":
      return exists && element?.isEnabled == false
    default:
      return false
    }
  }

  private func describe(_ query: ElementQuery?) -> String {
    guard let query else { return "<no query>" }
    var parts: [String] = []
    if let id = query.id { parts.append("id=\"\(id)\"") }
    if let text = query.text { parts.append("text=\"\(text)\"") }
    if let fragment = query.textContains { parts.append("textContains=\"\(fragment)\"") }
    if let role = query.role { parts.append("role=\(role)") }
    if let index = query.index { parts.append("index=\(index)") }
    if let within = query.within { parts.append("within=\(describe(within))") }
    if let near = query.near { parts.append("near=\(describe(near))") }
    return "{" + parts.joined(separator: ", ") + "}"
  }

  // MARK: - Polling

  private func poll(timeout: TimeInterval, until condition: () -> Bool) -> Bool {
    let deadline = Date().addingTimeInterval(timeout)
    while true {
      if condition() {
        return true
      }
      if Date() >= deadline {
        return false
      }
      Thread.sleep(forTimeInterval: Self.pollInterval)
    }
  }
}
