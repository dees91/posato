import Foundation
import Testing

@testable import PosatoMacOSHelper
@testable import PosatoMacOSServiceCore

@MainActor
@Test func givenCancelledOrCapacityChoiceWhenSelectedThenOutcomeIsPreserved() throws {
  #expect(
    try ApplicationSelectionService(chooser: StubChooser(.cancelled)).select().outcome == .cancelled
  )
  #expect(
    try ApplicationSelectionService(chooser: StubChooser(.capacity)).select().outcome == .capacity)
}

@MainActor
@Test func givenPosatoAmongSelectionWhenSelectedThenWholeBatchIsRejected() throws {
  let candidates = [
    candidate(name: "Browser", identifier: "example.browser"),
    candidate(name: "Posato", identifier: ServiceContract.applicationIdentifier),
  ]
  let inspector = StubInspector(requirements: [Data([1]), Data([2])])

  let result = try ApplicationSelectionService(
    chooser: StubChooser(.selected(candidates)),
    inspector: inspector
  ).select()

  #expect(result.outcome == .selfSelection)
  #expect(result.applications.isEmpty)
}

@MainActor
@Test func givenDuplicateRequirementsWhenSelectedThenOnlyOneIdentityIsReturned() throws {
  let candidates = [
    candidate(name: "Browser", identifier: "example.browser"),
    candidate(name: "Browser copy", identifier: "example.browser.copy"),
  ]
  let requirement = Data([1, 2, 3])

  let result = try ApplicationSelectionService(
    chooser: StubChooser(.selected(candidates)),
    inspector: StubInspector(requirements: [requirement, requirement])
  ).select()

  #expect(result.outcome == .success)
  #expect(result.applications.count == 1)
}

@MainActor
@Test func givenSensitiveHelperValuesWhenRenderedThenTheyRemainRedacted() {
  let secret = "Secret Browser"
  let selected = candidate(name: secret, identifier: "secret.identifier")
  let choice = ApplicationChoice.selected([selected])

  for rendered in [
    String(describing: selected), String(reflecting: selected), String(describing: choice),
    String(reflecting: choice),
  ] {
    #expect(!rendered.contains(secret))
    #expect(!rendered.contains("secret.identifier"))
    #expect(rendered.contains("redacted"))
  }
}

@MainActor
private struct StubChooser: ApplicationChoosing {
  let choice: ApplicationChoice

  init(_ choice: ApplicationChoice) {
    self.choice = choice
  }

  func choose() -> ApplicationChoice {
    return choice
  }
}

private final class StubInspector: ApplicationIdentityInspecting {
  private var requirements: [Data]

  init(requirements: [Data] = []) {
    self.requirements = requirements
  }

  func designatedRequirement(for url: URL) throws -> Data {
    guard !requirements.isEmpty else {
      throw StubFailure.missingRequirement
    }
    return requirements.removeFirst()
  }
}

private enum StubFailure: Error {
  case missingRequirement
}

private func candidate(name: String, identifier: String) -> ApplicationSelectionCandidate {
  return ApplicationSelectionCandidate(
    url: URL(fileURLWithPath: "/Applications/\(name).app"),
    displayName: name,
    bundleIdentifier: identifier
  )
}
