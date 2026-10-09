import Foundation
import Testing

@testable import PosatoProxySettingsDaemon

private final class ExitRecorder: @unchecked Sendable {
  private let lock = NSLock()
  private var count = 0

  var exits: Int {
    return lock.withLock { count }
  }

  func record() {
    lock.withLock { count += 1 }
  }
}

private func idleCoordinator(_ recorder: ExitRecorder) -> RequestCoordinator {
  return RequestCoordinator(
    persistence: MemoryOwnershipPersistence(),
    configuration: MemoryProxyConfiguration(),
    idleExit: DaemonIdleExit(
      startupGrace: .milliseconds(300),
      afterDisconnect: .milliseconds(30)
    ),
    terminate: recorder.record
  )
}

// launchd starts the daemon for a helper connection that reaches the delegate only after its
// code-signing check, which took more than a second on a busy Mac (MACOS-027).
@Test func givenAFreshDaemonWhenTheFirstConnectionIsLateThenItDoesNotExitFirst() async throws {
  let recorder = ExitRecorder()
  let coordinator = idleCoordinator(recorder)

  coordinator.scheduleIdleExit()
  try await Task.sleep(for: .milliseconds(120))
  coordinator.connectionOpened()
  try await Task.sleep(for: .milliseconds(400))

  #expect(recorder.exits == 0)
}

@Test func givenADisconnectWhenNothingElseArrivesThenTheDaemonExitsSoon() async throws {
  let recorder = ExitRecorder()
  let coordinator = idleCoordinator(recorder)

  coordinator.connectionOpened()
  coordinator.connectionInvalidated(state: ConnectionState())
  try await Task.sleep(for: .milliseconds(150))

  #expect(recorder.exits == 1)
}

@Test func givenAConnectionOpenedWhileTheIdleCheckIsDueThenTheDaemonStays() async throws {
  let recorder = ExitRecorder()
  let coordinator = idleCoordinator(recorder)
  coordinator.connectionOpened()
  coordinator.connectionInvalidated(state: ConnectionState())

  // The listener accepts a connection on its own thread after the idle check fell due, while the
  // daemon's queue is still busy with earlier work.
  coordinator.queue.async { Thread.sleep(forTimeInterval: 0.15) }
  try await Task.sleep(for: .milliseconds(70))
  coordinator.connectionOpened()
  try await Task.sleep(for: .milliseconds(250))

  #expect(recorder.exits == 0)
}
