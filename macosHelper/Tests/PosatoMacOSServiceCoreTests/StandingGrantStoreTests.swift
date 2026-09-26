import Darwin
import Foundation
import Testing

@testable import PosatoMacOSServiceCore

private struct EncodedRecord: Codable {
  let schemaVersion: Int
  let entries: [StandingGrantEntry]
}

private func entry(_ userID: UInt32) -> StandingGrantEntry {
  return StandingGrantEntry(
    userID: userID,
    accountIdentifier: UUID(),
    platformIdentifier: UUID(),
    grantedAt: Date(timeIntervalSince1970: 1_790_000_000)
  )
}

private func storeWriting(_ data: Data) throws -> StandingGrantStore {
  let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString)
  try FileManager.default.createDirectory(
    at: directory,
    withIntermediateDirectories: true,
    attributes: [.posixPermissions: 0o700]
  )
  let url = directory.appending(path: "apply-grant-v1.plist")
  try data.write(to: url)
  try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: url.path)
  return StandingGrantStore(url: url, expectedOwner: geteuid())
}

private func encoded(schemaVersion: Int, entries: [StandingGrantEntry]) throws -> Data {
  let encoder = PropertyListEncoder()
  encoder.outputFormat = .binary
  return try encoder.encode(EncodedRecord(schemaVersion: schemaVersion, entries: entries))
}

@Test func givenStoredGrantWhenReloadedThenTheSameRecordReturns() throws {
  let store = try storeWriting(Data([0]))
  let record = StandingGrantRecord(entries: [entry(501), entry(502)])

  try store.save(record)

  #expect(try store.load() == record)
}

@Test func givenUnusableGrantRecordWhenLoadedThenItIsRejected() throws {
  let unknownSchema = try encoded(schemaVersion: 2, entries: [entry(501)])
  let tooMany = try encoded(schemaVersion: 1, entries: (UInt32(600)..<UInt32(609)).map(entry))
  let empty = try encoded(schemaVersion: 1, entries: [])
  let duplicate = try encoded(schemaVersion: 1, entries: [entry(501), entry(501)])
  let undecodable = Data("not a property list".utf8)

  for data in [unknownSchema, tooMany, empty, duplicate, undecodable] {
    let store = try storeWriting(data)
    #expect(throws: DurableOwnershipFailure.invalidState) { try store.load() }
  }
}

@Test func givenInvalidGrantRecordWhenSavedThenNothingIsWritten() throws {
  let store = try storeWriting(Data([0]))
  try store.remove()

  #expect(throws: DurableOwnershipFailure.invalidState) {
    try store.save(StandingGrantRecord(entries: (UInt32(600)..<UInt32(609)).map(entry)))
  }
  #expect(try store.load() == nil)
}
