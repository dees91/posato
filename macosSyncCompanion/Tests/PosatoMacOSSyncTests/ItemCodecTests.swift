import Foundation
import Testing

@testable import PosatoMacOSSync

@Test func givenIsoHdlcVectorWhenHashedThenChecksumMatchesKotlin() {
  let bytes = Data("123456789".utf8)
  #expect(ItemCodec.crc32(bytes, length: 9) == 0xCBF4_3926)
}

@Test func givenWorkspaceIdentifierWhenRenderedThenAccountIsCanonical() throws {
  let account = try #require(ItemCodec.accountText(from: testIdentifier(1)))
  #expect(account == "00000000-0000-4000-8000-000000000001")
  #expect(ItemCodec.isCanonicalAccount(account))
  #expect(!ItemCodec.isCanonicalAccount("00000000-0000-4000-8000-00000000000A"))
  #expect(ItemCodec.identifierBytes(from: account) == testIdentifier(1))
}

@Test func givenValidItemWhenValidatedThenAccountMustMatchWorkspace() throws {
  let item = testItem()
  let account = try #require(ItemCodec.accountText(from: testIdentifier(1)))
  #expect(ItemCodec.validateItem(item, account: account) == item)
  #expect(ItemCodec.validateItem(item, account: testAccount(2)) == nil)
}

@Test func givenTamperedChecksumWhenValidatedThenItemIsRejected() {
  var item = testItem()
  item[83] ^= 1
  #expect(ItemCodec.validateItem(item, account: testAccount()) == nil)
}

@Test func givenMismatchedLengthWhenComparedThenConstantTimeEqualsIsFalse() {
  #expect(ItemCodec.constantTimeEquals(Data([1, 2]), Data([1, 2])))
  #expect(!ItemCodec.constantTimeEquals(Data([1, 2]), Data([1, 3])))
  #expect(!ItemCodec.constantTimeEquals(Data([1, 2]), Data([1])))
}
