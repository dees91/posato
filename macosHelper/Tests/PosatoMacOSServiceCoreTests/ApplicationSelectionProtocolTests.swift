import Foundation
import Testing

@testable import PosatoMacOSServiceCore

@Test func givenValidSelectionWhenRoundTrippedThenIdentityRemainsExact() throws {
  let identity = try SelectedApplicationIdentity(
    displayName: "Browser",
    designatedRequirement: Data([1, 2, 3])
  )
  let payload = try ApplicationSelectionPayload(
    outcome: .success,
    applications: [identity]
  )

  #expect(try ApplicationSelectionPayload.decode(payload.encode()) == payload)
}

@Test func givenNonSuccessWithIdentitiesWhenCreatedThenItIsRejected() throws {
  let identity = try SelectedApplicationIdentity(
    displayName: "Browser",
    designatedRequirement: Data([1])
  )

  #expect(throws: WireProtocolFailure.invalidFrame) {
    try ApplicationSelectionPayload(outcome: .cancelled, applications: [identity])
  }
}

@Test func givenSelectionFrameWhenDecodedForDaemonThenItIsRejected() throws {
  let message = try WireMessage(
    kind: .request,
    operation: .selectApplications,
    sequence: 1,
    deadlineMilliseconds: WireLimits.maximumSelectionDeadlineMilliseconds,
    connectionIdentifier: Data(repeating: 1, count: 16),
    sessionIdentifier: Data(repeating: 2, count: 16),
    requestIdentifier: Data(repeating: 3, count: 16),
    payload: Data()
  )
  let encoded = try WireCodec.encode(message)

  #expect(throws: WireProtocolFailure.invalidOperation) {
    try WireCodec.decode(encoded, maximumBytes: WireLimits.maximumFrameBytes)
  }
  #expect(
    try WireCodec.decode(
      encoded,
      maximumBytes: WireLimits.maximumFrameBytes,
      allowsHelperOnlyOperations: true,
      maximumDeadlineMilliseconds: WireLimits.maximumSelectionDeadlineMilliseconds
    ) == message
  )
}

@Test func givenSensitiveSelectionValuesWhenRenderedThenTheyRemainRedacted() throws {
  let secret = "Secret Browser"
  let identity = try SelectedApplicationIdentity(
    displayName: secret,
    designatedRequirement: Data(secret.utf8)
  )
  let payload = try ApplicationSelectionPayload(outcome: .success, applications: [identity])

  for rendered in [
    String(describing: identity), String(reflecting: identity), String(describing: payload),
    String(reflecting: payload),
  ] {
    #expect(!rendered.contains(secret))
    #expect(rendered.contains("redacted"))
  }
}
