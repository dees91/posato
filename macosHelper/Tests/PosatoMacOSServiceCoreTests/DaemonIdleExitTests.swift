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
      startupGrace: .seconds(3),
      afterDisconnect: .milliseconds(20)
    ),
    terminate: recorder.record
  )
}

/// Waits until [condition] holds or [seconds] pass, so a loaded host only slows the test.
private func eventually(within seconds: Double, _ condition: () -> Bool) async throws -> Bool {
  let deadline = Date().addingTimeInterval(seconds)
  while Date() < deadline {
    if condition() {
      return true
    }
    try await Task.sleep(for: .milliseconds(20))
  }
  return condition()
}

// launchd starts the daemon for a helper connection that reaches the delegate only after its
// code-signing check, which took more than a second on a busy Mac (MACOS-027). The connection
// comes later than the short post-disconnect delay but within the startup grace.
@Test func givenAFreshDaemonWhenTheFirstConnectionIsLateThenItDoesNotExitFirst() async throws {
  let recorder = ExitRecorder()
  let coordinator = idleCoordinator(recorder)

  coordinator.scheduleIdleExit()
  try await Task.sleep(for: .milliseconds(300))
  coordinator.connectionOpened()
  let exited = try await eventually(within: 4) { recorder.exits > 0 }

  #expect(!exited)
}

@Test func givenADisconnectWhenNothingElseArrivesThenTheDaemonExitsSoon() async throws {
  let recorder = ExitRecorder()
  let coordinator = idleCoordinator(recorder)

  coordinator.connectionOpened()
  coordinator.connectionInvalidated(state: ConnectionState())
  let exited = try await eventually(within: 10) { recorder.exits > 0 }

  #expect(exited)
}

@Test func givenAConnectionOpenedWhileTheIdleCheckIsDueThenTheDaemonStays() async throws {
  let recorder = ExitRecorder()
  let coordinator = idleCoordinator(recorder)
  coordinator.connectionOpened()
  coordinator.connectionInvalidated(state: ConnectionState())

  // The daemon's queue is busy with earlier work while the idle check falls due behind it, and
  // the listener accepts a connection on its own thread before that work ends.
  let busy = DispatchSemaphore(value: 0)
  coordinator.queue.async { busy.wait() }
  try await Task.sleep(for: .milliseconds(200))
  coordinator.connectionOpened()
  busy.signal()
  coordinator.queue.sync {}
  let exited = try await eventually(within: 1) { recorder.exits > 0 }

  #expect(!exited)
}
