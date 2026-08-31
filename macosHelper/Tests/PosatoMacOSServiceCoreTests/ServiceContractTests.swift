import Foundation
import Testing

@testable import PosatoMacOSServiceCore

@Test func givenServiceContractWhenLoadedThenIdentifiersAreFixed() {
  #expect(ServiceContract.helperIdentifier == "app.posato.macos.helper")
  #expect(ServiceContract.daemonIdentifier == "app.posato.macos.proxy-settings")
  #expect(
    ServiceContract.daemonPlistName
      == "app.posato.macos.proxy-settings.plist"
  )
}
