import PosatoMacOSServiceCore
import ServiceManagement
import Testing

@testable import PosatoMacOSHelper

@Test func givenServiceManagementNotFoundWhenMappedThenSetupTreatsItAsNotRegistered() {
  #expect(serviceState(.notFound) == .notRegistered)
  #expect(serviceState(.notRegistered) == .notRegistered)
  #expect(serviceState(.enabled) == .ready)
  #expect(serviceState(.requiresApproval) == .approvalRequired)
}
