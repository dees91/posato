import Foundation
import Testing

@testable import PosatoMacOSServiceCore

private let syntheticIdentifier = Data(repeating: 7, count: 16)

@Test func givenValidMessageWhenRoundTrippedThenAllFieldsArePreserved() throws {
  let message = try WireMessage(
    kind: .request,
    operation: .apply,
    sequence: 3,
    deadlineMilliseconds: 5_000,
    connectionIdentifier: syntheticIdentifier,
    sessionIdentifier: syntheticIdentifier,
    requestIdentifier: syntheticIdentifier,
    payload: Data([0x45, 0x69])
  )

  let decoded = try WireCodec.decode(
    WireCodec.encode(message),
    maximumBytes: WireLimits.maximumFrameBytes
  )

  #expect(decoded == message)
}

@Test func givenCrossLanguageFixtureWhenEncodedThenBytesRemainStable() throws {
  let message = try WireMessage(
    kind: .request,
    operation: .apply,
    sequence: 7,
    deadlineMilliseconds: 9_000,
    connectionIdentifier: Data(repeating: 1, count: 16),
    sessionIdentifier: Data(repeating: 2, count: 16),
    requestIdentifier: Data(repeating: 3, count: 16),
    payload: Data([0x45, 0x69])
  )
  let expected =
    "5053544f000103040000000700002328"
    + "01010101010101010101010101010101"
    + "02020202020202020202020202020202"
    + "03030303030303030303030303030303"
    + "000000024569"

  #expect(try WireCodec.encode(message).map { String(format: "%02x", $0) }.joined() == expected)
}

@Test func givenRequiredCapabilitiesWhenRoundTrippedThenTheyRemainExact() throws {
  let encoded = WireCapabilities.encode(WireLimits.requiredCapabilities)

  #expect(try WireCapabilities.decode(encoded) == WireLimits.requiredCapabilities)
  #expect(
    WireCapabilities.supports(
      WireLimits.requiredParentHelperCapabilities,
      required: WireLimits.requiredCapabilities
    )
  )
  #expect(!WireCapabilities.supports(0, required: WireLimits.requiredCapabilities))
  #expect(
    !WireCapabilities.supports(
      WireLimits.requiredCapabilities,
      required: WireLimits.requiredParentHelperCapabilities
    )
  )
  #expect(throws: WireProtocolFailure.invalidFrame) {
    try WireCapabilities.decode(Data())
  }
}

@Test func givenReconcileInputWhenRoundTrippedThenOperationAndDigestRemainExact() throws {
  let digest = WireCodec.canonicalInputDigest(operation: .apply, payload: Data([0, 80]))
  let payload = try WireReconcilePayload(
    originalOperation: .apply,
    canonicalInputDigest: digest
  )

  #expect(try WireReconcilePayload.decode(payload.encode()) == payload)
  #expect(throws: WireProtocolFailure.invalidFrame) {
    try WireReconcilePayload(
      originalOperation: .reconcile,
      canonicalInputDigest: digest
    )
  }
}

@Test func givenUnknownVersionWhenDecodedThenItIsRejected() throws {
  let message = try WireMessage(
    kind: .hello,
    operation: .none,
    sequence: 0,
    deadlineMilliseconds: 5_000,
    connectionIdentifier: Data(repeating: 0, count: 16),
    sessionIdentifier: Data(repeating: 0, count: 16),
    requestIdentifier: Data(repeating: 0, count: 16),
    payload: Data()
  )
  var encoded = try WireCodec.encode(message)
  encoded[5] = 2

  #expect(throws: WireProtocolFailure.invalidVersion) {
    try WireCodec.decode(encoded, maximumBytes: WireLimits.maximumFrameBytes)
  }
}

@Test func givenMismatchedPayloadLengthWhenDecodedThenItIsRejected() throws {
  let message = try WireMessage(
    kind: .request,
    operation: .status,
    sequence: 1,
    deadlineMilliseconds: 5_000,
    connectionIdentifier: syntheticIdentifier,
    sessionIdentifier: syntheticIdentifier,
    requestIdentifier: syntheticIdentifier,
    payload: Data()
  )
  var encoded = try WireCodec.encode(message)
  encoded[67] = 1

  #expect(throws: WireProtocolFailure.invalidFrame) {
    try WireCodec.decode(encoded, maximumBytes: WireLimits.maximumFrameBytes)
  }
}

@Test func givenSameCanonicalInputWhenDigestedThenTransportMetadataDoesNotChangeDigest() throws {
  let first = try WireMessage(
    kind: .request,
    operation: .apply,
    sequence: 1,
    deadlineMilliseconds: 1_000,
    connectionIdentifier: Data(repeating: 1, count: 16),
    sessionIdentifier: Data(repeating: 2, count: 16),
    requestIdentifier: Data(repeating: 3, count: 16),
    payload: Data([1, 2])
  )
  let second = try WireMessage(
    kind: .request,
    operation: .apply,
    sequence: 2,
    deadlineMilliseconds: 2_000,
    connectionIdentifier: Data(repeating: 4, count: 16),
    sessionIdentifier: Data(repeating: 5, count: 16),
    requestIdentifier: Data(repeating: 6, count: 16),
    payload: Data([1, 2])
  )

  #expect(first.canonicalInputDigest == second.canonicalInputDigest)
}

@Test func givenDifferentCanonicalInputWhenDigestedThenDigestChanges() throws {
  let first = try WireMessage(
    kind: .request,
    operation: .apply,
    sequence: 1,
    deadlineMilliseconds: 1_000,
    connectionIdentifier: syntheticIdentifier,
    sessionIdentifier: syntheticIdentifier,
    requestIdentifier: syntheticIdentifier,
    payload: Data([1, 2])
  )
  let second = try WireMessage(
    kind: .request,
    operation: .apply,
    sequence: 1,
    deadlineMilliseconds: 1_000,
    connectionIdentifier: syntheticIdentifier,
    sessionIdentifier: syntheticIdentifier,
    requestIdentifier: syntheticIdentifier,
    payload: Data([1, 3])
  )

  #expect(first.canonicalInputDigest != second.canonicalInputDigest)
}

@Test func givenApplicationFrameWhenDecodedForDaemonThenItIsRejected() throws {
  let message = try WireMessage(
    kind: .request,
    operation: .configureApplications,
    sequence: 1,
    deadlineMilliseconds: WireLimits.maximumConfigureDeadlineMilliseconds,
    connectionIdentifier: syntheticIdentifier,
    sessionIdentifier: syntheticIdentifier,
    requestIdentifier: syntheticIdentifier,
    payload: Data([0, 0])
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
