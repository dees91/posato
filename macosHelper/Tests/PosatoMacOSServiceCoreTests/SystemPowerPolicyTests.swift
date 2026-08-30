import Testing

@testable import PosatoProxySettingsDaemon

@Test func givenSystemPowerMessagesWhenMappedThenRestorationPrecedesSleepAcknowledgement() {
  #expect(SystemPowerPolicy.actions(for: 0xE000_0270) == [.allowSleep])
  #expect(SystemPowerPolicy.actions(for: 0xE000_0280) == [.restore, .allowSleep])
  #expect(SystemPowerPolicy.actions(for: 0xE000_0300) == [.restore])
  #expect(SystemPowerPolicy.actions(for: 0) == [])
}
