import Foundation
import PosatoMacOSServiceCore
import Testing

@testable import PosatoMacOSHelper

private let otherRequirement = Data([8, 8, 8])

private final class MadeSessions {
  var entries: [(Set<Data>, UInt64?)] = []
}

private func stubbedSessionFactory(
  made: MadeSessions
) -> (Set<Data>, UInt64?) -> ApplicationEnforcementSession {
  return { requirements, endTime in
    made.entries.append((requirements, endTime))
    return ApplicationEnforcementSession(
      requirements: requirements,
      sessionEndEpochMilliseconds: endTime,
      listing: StubApplicationListing(),
      matcher: StubRequirementMatcher(),
      notices: StubNoticePosting(),
      now: { 0 }
    )
  }
}

private func configureMessage(payload: Data) throws -> WireMessage {
  return try WireMessage(
    kind: .request,
    operation: .configureApplications,
    sequence: 4,
    deadlineMilliseconds: 5_000,
    connectionIdentifier: Data(repeating: 1, count: 16),
    sessionIdentifier: Data(repeating: 2, count: 16),
    requestIdentifier: Data(repeating: 3, count: 16),
    payload: payload
  )
}

@Test func givenRequirementsWhenRoundTrippedThenPayloadIsPreserved() throws {
  let payload = try ApplicationEnforcementConfigurePayload(
    requirements: [stubRequirement, otherRequirement],
    sessionEndEpochMilliseconds: 1_750_000_000_000
  )

  let decoded = try ApplicationEnforcementConfigurePayload.decode(payload.encode())

  #expect(decoded == payload)
}

@Test func givenEmptySetWhenRoundTrippedThenClearIsPreserved() throws {
  let payload = try ApplicationEnforcementConfigurePayload(
    requirements: [],
    sessionEndEpochMilliseconds: nil
  )

  let decoded = try ApplicationEnforcementConfigurePayload.decode(payload.encode())

  #expect(decoded.requirements.isEmpty)
}

@Test func givenDuplicateRequirementsWhenDecodedThenTheyAreRejected() throws {
  let payload = try ApplicationEnforcementConfigurePayload(
    requirements: [stubRequirement],
    sessionEndEpochMilliseconds: nil
  )
  var encoded = try payload.encode()
  encoded[0] = 0
  encoded[1] = 2

  #expect(throws: WireProtocolFailure.invalidFrame) {
    try ApplicationEnforcementConfigurePayload.decode(encoded)
  }
}

@Test func givenOversizedRequirementWhenCreatedThenItIsRejected() {
  #expect(throws: WireProtocolFailure.invalidFrame) {
    try ApplicationEnforcementConfigurePayload(
      requirements: [
        Data(repeating: 9, count: ApplicationEnforcementLimits.maximumRequirementBytes + 1)
      ],
      sessionEndEpochMilliseconds: nil
    )
  }
}

@Test func givenFailureResponseWhenCreatedWithAcceptedCountThenItIsRejected() throws {
  let outcome = WireResponsePayload(
    outcome: .failure,
    serviceState: .ready,
    ownershipPhase: .idle,
    failure: .invalidInput
  )

  #expect(throws: WireProtocolFailure.invalidFrame) {
    try ApplicationEnforcementConfigureResponse(outcome: outcome, acceptedCount: 1)
  }
}

@Test func givenCanaryBytesWhenRenderedThenTheyStayRedacted() throws {
  let secret = Data("canary-requirement-blob".utf8)
  let payload = try ApplicationEnforcementConfigurePayload(
    requirements: [secret],
    sessionEndEpochMilliseconds: nil
  )
  let response = try ApplicationEnforcementConfigureResponse(
    outcome: WireResponsePayload(
      outcome: .success,
      serviceState: .ready,
      ownershipPhase: .idle
    ),
    acceptedCount: 1
  )
  let rendered = [payload.description, response.description]

  for text in rendered {
    #expect(!text.contains("canary-requirement-blob"))
  }
}

@Test func givenRequirementsWhenConfiguredThenAcceptedCountMatchesSetSize() throws {
  let payload = try ApplicationEnforcementConfigurePayload(
    requirements: [stubRequirement, otherRequirement],
    sessionEndEpochMilliseconds: nil
  ).encode()
  let request = try configureMessage(payload: payload)
  let made = MadeSessions()

  let handled = try ApplicationEnforcementRequestHandler.handleConfigure(
    request: request,
    receivedAt: .now(),
    serviceState: .ready,
    existing: nil,
    makeSession: stubbedSessionFactory(made: made)
  )
  defer { handled.session?.stop() }
  let response = try ApplicationEnforcementConfigureResponse.decode(handled.response.payload)

  #expect(response.outcome.outcome == .success)
  #expect(response.acceptedCount == 2)
  #expect(handled.session != nil)
  #expect(made.entries.count == 1)
  #expect(made.entries[0].0 == [stubRequirement, otherRequirement])
}

@Test func givenEmptySetWhenConfiguredThenSessionClearsWithSuccess() throws {
  let payload = try ApplicationEnforcementConfigurePayload(
    requirements: [],
    sessionEndEpochMilliseconds: nil
  ).encode()
  let request = try configureMessage(payload: payload)
  let made = MadeSessions()

  let handled = try ApplicationEnforcementRequestHandler.handleConfigure(
    request: request,
    receivedAt: .now(),
    serviceState: .ready,
    existing: nil,
    makeSession: stubbedSessionFactory(made: made)
  )
  let response = try ApplicationEnforcementConfigureResponse.decode(handled.response.payload)

  #expect(response.outcome.outcome == .success)
  #expect(response.acceptedCount == 0)
  #expect(handled.session == nil)
  #expect(made.entries.isEmpty)
}

@Test func givenGarbageWhenConfiguredThenExistingSessionKeepsObserving() throws {
  let request = try configureMessage(payload: Data([9, 9, 9]))
  let existing = ApplicationEnforcementSession(
    requirements: [],
    sessionEndEpochMilliseconds: nil,
    listing: StubApplicationListing(),
    matcher: StubRequirementMatcher(),
    notices: StubNoticePosting(),
    now: { 0 }
  )
  let made = MadeSessions()

  let handled = try ApplicationEnforcementRequestHandler.handleConfigure(
    request: request,
    receivedAt: .now(),
    serviceState: .ready,
    existing: existing,
    makeSession: stubbedSessionFactory(made: made)
  )
  let response = try ApplicationEnforcementConfigureResponse.decode(handled.response.payload)

  #expect(response.outcome.outcome == .failure)
  #expect(response.outcome.failure == .invalidInput)
  #expect(response.acceptedCount == 0)
  #expect(handled.session === existing)
  #expect(made.entries.isEmpty)
}
