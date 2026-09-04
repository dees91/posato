import CryptoKit
import Foundation
import Testing

@testable import PosatoMacOSSync

@Test func givenRecordNameWhenDerivedThenBindingMatchesAdrVector() {
  var material = Data("iCloud.app.posato.sync|account-binding|v1".utf8)
  material.append(0)
  material.append(Data("synthetic-record".utf8))
  let expected = Data(SHA256.hash(data: material))

  #expect(AccountBinding.derive(recordName: "synthetic-record") == expected)
  #expect(expected.count == SyncLimits.bindingBytes)
}
