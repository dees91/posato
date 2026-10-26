import Foundation
import Testing

@testable import PosatoMacOSServiceCore
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

// MACOS-031: on a busy Mac a restore held the daemon's queue for more than a second. The idle check
// that fell due behind it ran the moment the restore ended and ended the daemon, while a new helper's
// connection was still on its way to the listener; that helper lost its daemon and ended too.
@Test func givenAnIdleCheckDelayedByBusyWorkWhenAConnectionFollowsSoonThenTheDaemonStays()
  async throws
{
  let recorder = ExitRecorder()
  let coordinator = RequestCoordinator(
    persistence: MemoryOwnershipPersistence(),
    configuration: MemoryProxyConfiguration(),
    idleExit: DaemonIdleExit(startupGrace: .seconds(3), afterDisconnect: .milliseconds(500)),
    terminate: recorder.record
  )
  coordinator.connectionOpened()
  coordinator.connectionInvalidated(state: ConnectionState())

  let busy = DispatchSemaphore(value: 0)
  coordinator.queue.async { busy.wait() }
  try await Task.sleep(for: .milliseconds(1_500))
  busy.signal()
  try await Task.sleep(for: .milliseconds(200))
  coordinator.connectionOpened()
  let exited = try await eventually(within: 1) { recorder.exits > 0 }

  #expect(!exited)
}

// A helper that loses the connection owning an applied mutation is replaced by a new one, whose
// connection passes its code-signing check before it is counted; under load that took more than a
// second (MACOS-027). After restoring for a lost owner the daemon waits as long as after its start.
@Test func givenARestoreForALostOwnerWhenTheNextHelperConnectsLateThenTheDaemonStays() async throws
{
  let recorder = ExitRecorder()
  let coordinator = RequestCoordinator(
    persistence: MemoryOwnershipPersistence(),
    configuration: MemoryProxyConfiguration(),
    idleExit: DaemonIdleExit(startupGrace: .seconds(3), afterDisconnect: .milliseconds(100)),
    terminate: recorder.record
  )
  let owner = try applyingOwner()
  #expect(owner.shouldRestoreOnInvalidation())
  coordinator.connectionOpened()
  coordinator.connectionInvalidated(state: owner)

  try await Task.sleep(for: .milliseconds(1_000))
  coordinator.connectionOpened()
  let exited = try await eventually(within: 1) { recorder.exits > 0 }

  #expect(!exited)
}

// A pause that ends, or Posato that quits, sends Restore itself; the connection then owns nothing,
// and the daemon still exits soon after it, so an update relaunch reaches the new daemon.
@Test func givenAHelperRestoreWhenItsConnectionClosesThenTheDaemonExitsSoon() async throws {
  let recorder = ExitRecorder()
  let coordinator = idleCoordinator(recorder)
  let owner = try applyingOwner()
  owner.update(
    request: try wireRequest(.restore),
    response: WireResponsePayload(outcome: .success, serviceState: .ready, ownershipPhase: .idle),
    ownershipVerified: true
  )
  #expect(!owner.shouldRestoreOnInvalidation())
  coordinator.connectionOpened()
  coordinator.connectionInvalidated(state: owner)
  let exited = try await eventually(within: 1) { recorder.exits > 0 }

  #expect(exited)
}

private func wireRequest(_ operation: WireOperation) throws -> WireMessage {
  return try WireMessage(
    kind: .request,
    operation: operation,
    sequence: 1,
    deadlineMilliseconds: 5_000,
    connectionIdentifier: Data(repeating: 1, count: WireLimits.identifierBytes),
    sessionIdentifier: Data(repeating: 2, count: WireLimits.identifierBytes),
    requestIdentifier: Data(repeating: 3, count: WireLimits.identifierBytes),
    payload: Data()
  )
}

private func applyingOwner() throws -> ConnectionState {
  let owner = ConnectionState()
  owner.update(
    request: try wireRequest(.apply),
    response: WireResponsePayload(
      outcome: .success, serviceState: .ready, ownershipPhase: .applied),
    ownershipVerified: true
  )
  return owner
}
