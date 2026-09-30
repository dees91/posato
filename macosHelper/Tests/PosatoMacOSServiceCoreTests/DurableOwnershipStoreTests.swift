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
    appliedHTTPS: appliedHTTPS ?? appliedHTTP ?? validApplied,
    exceptions: nil
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

// A record the previous daemon (schema 1, before MACOS-024) wrote, generated from its types. The new daemon must still
// load it as owning no exceptions change; a regression here strands an update or a manual replace that meets a
// running session, which no VM run produces.
private let versionOneRecordXML = """
<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd"><plist version="1.0"><dict><key>appliedHTTP</key><dict><key>enabled</key><dict><key>integer</key><dict><key>_0</key><integer>1</integer></dict></dict><key>host</key><dict><key>string</key><dict><key>_0</key><string>127.0.0.1</string></dict></dict><key>port</key><dict><key>integer</key><dict><key>_0</key><integer>17769</integer></dict></dict></dict><key>appliedHTTPS</key><dict><key>enabled</key><dict><key>integer</key><dict><key>_0</key><integer>1</integer></dict></dict><key>host</key><dict><key>string</key><dict><key>_0</key><string>127.0.0.1</string></dict></dict><key>port</key><dict><key>integer</key><dict><key>_0</key><integer>17769</integer></dict></dict></dict><key>baselineHTTP</key><dict/><key>baselineHTTPS</key><dict/><key>canonicalInputDigest</key><data>
	AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM=
	</data><key>phase</key><integer>3</integer><key>requestIdentifier</key><data>
	AgICAgICAgICAgICAgICAg==
	</data><key>schema</key><integer>1</integer><key>serviceIdentifier</key><string>synthetic-service</string><key>sessionIdentifier</key><data>
	AQEBAQEBAQEBAQEBAQEBAQ==
	</data></dict></plist>
"""

@Test func givenRecordWrittenByVersionOneDaemonWhenLoadedThenItOwnsNoExceptionsChange() throws {
  let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString)
  try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
  let stateURL = directory.appending(path: "ownership-v1.plist")
  try Data(versionOneRecordXML.utf8).write(to: stateURL)
  try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: stateURL.path)
  let store = DurableOwnershipStore(stateURL: stateURL, expectedOwner: geteuid())

  let record = try #require(try store.load())

  #expect(record.schema == 1)
  #expect(record.phase == .applied)
  #expect(record.exceptions == nil)
}
