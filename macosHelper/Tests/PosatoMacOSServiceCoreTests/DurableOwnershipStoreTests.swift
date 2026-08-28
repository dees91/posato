import Darwin
import Foundation
import Testing

@testable import PosatoMacOSServiceCore

private func syntheticRecord(
  appliedHTTP: ProxyTuple? = nil,
  appliedHTTPS: ProxyTuple? = nil,
  phase: OwnershipPhase = .prepared
) -> OwnershipRecord {
  let emptyTuple = ProxyTuple(enabled: nil, host: nil, port: nil)
  let validApplied = ProxyTuple(
    enabled: .integer(1),
    host: .string("127.0.0.1"),
    port: .integer(17_769)
  )
  return OwnershipRecord(
    sessionIdentifier: Data(repeating: 1, count: 16),
    requestIdentifier: Data(repeating: 2, count: 16),
    canonicalInputDigest: Data(repeating: 3, count: 32),
    serviceIdentifier: "synthetic-service",
    phase: phase,
    baselineHTTP: emptyTuple,
    baselineHTTPS: emptyTuple,
    appliedHTTP: appliedHTTP ?? validApplied,
    appliedHTTPS: appliedHTTPS ?? appliedHTTP ?? validApplied
  )
}

@Test func givenSemanticallyInvalidRecordWhenLoadedThenItIsRejected() throws {
  let directory = FileManager.default.temporaryDirectory
    .appending(path: UUID().uuidString)
  try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
  let stateURL = directory.appending(path: "ownership-v1.plist")
  let invalidTuple = ProxyTuple(
    enabled: .integer(1),
    host: .string("external.invalid"),
    port: .integer(80)
  )
  let encoder = PropertyListEncoder()
  encoder.outputFormat = .binary
  try encoder.encode(
    syntheticRecord(appliedHTTP: invalidTuple, appliedHTTPS: invalidTuple)
  ).write(to: stateURL)
  try FileManager.default.setAttributes(
    [.posixPermissions: 0o600],
    ofItemAtPath: stateURL.path
  )
  let store = DurableOwnershipStore(stateURL: stateURL, expectedOwner: geteuid())

  #expect(throws: DurableOwnershipFailure.invalidState) {
    try store.load()
  }
}

@Test func givenIdleRecordWhenSavedThenItIsRejected() throws {
  let stateURL = FileManager.default.temporaryDirectory
    .appending(path: UUID().uuidString)
    .appending(path: "ownership-v1.plist")
  let store = DurableOwnershipStore(stateURL: stateURL, expectedOwner: geteuid())

  #expect(throws: DurableOwnershipFailure.invalidState) {
    try store.save(syntheticRecord(phase: .idle))
  }
}

@Test func givenValidRecordWhenSavedAndLoadedThenItRoundTrips() throws {
  let directory = FileManager.default.temporaryDirectory
    .appending(path: UUID().uuidString)
  let stateURL = directory.appending(path: "ownership-v1.plist")
  let store = DurableOwnershipStore(stateURL: stateURL, expectedOwner: geteuid())
  let record = syntheticRecord()

  try store.save(record)

  #expect(try store.load() == record)
  try store.remove()
  #expect(try store.load() == nil)
}

@Test func givenGroupReadableStateWhenLoadedThenItIsRejected() throws {
  let directory = FileManager.default.temporaryDirectory
    .appending(path: UUID().uuidString)
  let stateURL = directory.appending(path: "ownership-v1.plist")
  let store = DurableOwnershipStore(stateURL: stateURL, expectedOwner: geteuid())
  try store.save(syntheticRecord())
  try FileManager.default.setAttributes(
    [.posixPermissions: 0o640],
    ofItemAtPath: stateURL.path
  )

  #expect(throws: DurableOwnershipFailure.invalidFile) {
    try store.load()
  }
}
