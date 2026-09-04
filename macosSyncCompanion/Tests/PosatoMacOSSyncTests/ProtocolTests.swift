import Foundation
import Testing

@testable import PosatoMacOSSync

@Test func givenValidRequestWhenRoundTrippedThenFieldsArePreserved() throws {
  let message = testRequest(operation: .readItem, payload: Data([1, 2, 3]))
  let decoded = try SyncCodec.decode(try SyncCodec.encode(message))

  #expect(decoded == message)
  #expect(decoded.description == "SyncMessage(redacted)")
}

@Test func givenResponseWhenEncodedThenIdentityIsEchoed() throws {
  let request = testRequest(operation: .createItem)
  let response = request.respond(outcome: .created)
  let decoded = try SyncCodec.decode(try SyncCodec.encode(response))

  #expect(decoded.operation == request.operation)
  #expect(decoded.requestIdentifier == request.requestIdentifier)
  #expect(decoded.outcome == .created)
}

@Test func givenUnknownOperationWhenDecodedThenItIsRejected() throws {
  var encoded = try SyncCodec.encode(testRequest(operation: .readItem))
  encoded[6] = 99
  #expect(throws: SyncProtocolFailure.invalidOperation) {
    try SyncCodec.decode(encoded)
  }
}

@Test func givenUnknownVersionWhenDecodedThenItIsRejected() throws {
  var encoded = try SyncCodec.encode(testRequest(operation: .readItem))
  encoded[5] = 2
  #expect(throws: SyncProtocolFailure.invalidVersion) {
    try SyncCodec.decode(encoded)
  }
}

@Test func givenZeroRequestIdentityWhenDecodedThenItIsRejected() {
  var message = testRequest(operation: .resolveBinding)
  message.requestIdentifier = Data(count: SyncLimits.identifierBytes)
  #expect(throws: SyncProtocolFailure.invalidFrame) {
    try SyncCodec.decode(try SyncCodec.encode(message))
  }
}

@Test func givenOversizedPayloadWhenDecodedThenItIsRejected() {
  #expect(throws: SyncProtocolFailure.invalidFrame) {
    var encoded = try SyncCodec.encode(testRequest(operation: .readItem))
    encoded.append(contentsOf: [0, 1])
    _ = try SyncCodec.decode(encoded)
  }
}
