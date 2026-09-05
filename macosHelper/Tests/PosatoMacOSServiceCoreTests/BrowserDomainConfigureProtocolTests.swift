import Foundation
import Testing

@testable import PosatoMacOSServiceCore

private let syntheticIdentifier = Data(repeating: 7, count: 16)

@Test func givenConfigureFrameWhenDecodedForDaemonThenItIsRejected() throws {
  let message = try WireMessage(
    kind: .request,
    operation: .configureBrowserDomains,
    sequence: 1,
    deadlineMilliseconds: WireLimits.maximumConfigureDeadlineMilliseconds,
    connectionIdentifier: Data(repeating: 1, count: 16),
    sessionIdentifier: Data(repeating: 2, count: 16),
    requestIdentifier: Data(repeating: 3, count: 16),
    payload: try BrowserDomainConfigurePayload(
      domains: ["example.com"],
      sessionEndEpochMilliseconds: nil
    ).encode()
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
      maximumDeadlineMilliseconds: WireLimits.maximumConfigureDeadlineMilliseconds
    ) == message
  )
}

@Test func givenConfigureDeadlineWhenCreatedThenLifecycleMaximumIsRejected() throws {
  #expect(throws: WireProtocolFailure.invalidDeadline) {
    try WireMessage(
      kind: .request,
      operation: .configureBrowserDomains,
      sequence: 1,
      deadlineMilliseconds: WireLimits.maximumDeadlineMilliseconds,
      connectionIdentifier: syntheticIdentifier,
      sessionIdentifier: syntheticIdentifier,
      requestIdentifier: syntheticIdentifier,
      payload: Data()
    )
  }
  let message = try WireMessage(
    kind: .request,
    operation: .configureBrowserDomains,
    sequence: 1,
    deadlineMilliseconds: WireLimits.maximumConfigureDeadlineMilliseconds,
    connectionIdentifier: syntheticIdentifier,
    sessionIdentifier: syntheticIdentifier,
    requestIdentifier: syntheticIdentifier,
    payload: Data()
  )
  #expect(message.deadlineMilliseconds == WireLimits.maximumConfigureDeadlineMilliseconds)
}

@Test func givenCanonicalDomainsWhenEncodedThenThePayloadRoundTrips() throws {
  let payload = try BrowserDomainConfigurePayload(
    domains: ["example.com", "xn--nxasmq6b.example"],
    sessionEndEpochMilliseconds: 1_725_000_000_000
  )

  #expect(try BrowserDomainConfigurePayload.decode(payload.encode()) == payload)
}

@Test func givenEmptyOrDuplicateDomainsWhenCreatedThenTheyAreRejected() {
  #expect(throws: WireProtocolFailure.invalidFrame) {
    try BrowserDomainConfigurePayload(domains: [], sessionEndEpochMilliseconds: nil)
  }
  #expect(throws: WireProtocolFailure.invalidFrame) {
    try BrowserDomainConfigurePayload(
      domains: ["example.com", "example.com"],
      sessionEndEpochMilliseconds: nil
    )
  }
}

@Test func givenSensitiveConfigureValuesWhenRenderedThenTheyRemainRedacted() throws {
  let secret = "secret.example"
  let payload = try BrowserDomainConfigurePayload(
    domains: [secret],
    sessionEndEpochMilliseconds: 42
  )
  let response = try BrowserDomainConfigureResponse(
    outcome: WireResponsePayload(
      outcome: .success,
      serviceState: .ready,
      ownershipPhase: .idle
    ),
    port: 17_769
  )

  for rendered in [
    String(describing: payload), String(reflecting: payload),
    String(describing: response), String(reflecting: response),
  ] {
    #expect(!rendered.contains(secret))
    #expect(rendered.contains("redacted"))
  }
}

@Test func givenSuccessfulConfigureResponseWhenRoundTrippedThenPortIsPreserved() throws {
  let response = try BrowserDomainConfigureResponse(
    outcome: WireResponsePayload(
      outcome: .success,
      serviceState: .ready,
      ownershipPhase: .idle
    ),
    port: 4_443
  )

  #expect(try BrowserDomainConfigureResponse.decode(response.encode()) == response)
}

@Test func givenFailedConfigureResponseWhenPortIsNonzeroThenItIsRejected() {
  #expect(throws: WireProtocolFailure.invalidFrame) {
    try BrowserDomainConfigureResponse(
      outcome: WireResponsePayload(
        outcome: .failure,
        serviceState: .ready,
        ownershipPhase: .idle,
        failure: .invalidInput
      ),
      port: 1
    )
  }
}

@Test func givenParentOfferingOnlyLifecycleAndSelectionWhenCheckedThenConfigureIsMissing() {
  let stale: UInt64 =
    WireLimits.requiredCapabilities | WireLimits.applicationSelectionCapability

  #expect(
    !WireCapabilities.supports(
      stale,
      required: WireLimits.requiredParentHelperCapabilities
    )
  )
  #expect(
    WireCapabilities.supports(
      WireLimits.requiredParentHelperCapabilities,
      required: stale
    )
  )
}
