import Foundation
import PosatoMacOSServiceCore

final class ReplyBox: @unchecked Sendable {
  private let reply: (Data?) -> Void

  init(_ reply: @escaping (Data?) -> Void) {
    self.reply = reply
  }

  func callAsFunction(_ data: Data?) {
    reply(data)
  }
}

final class ConnectionState: @unchecked Sendable {
  private let lock = NSLock()
  private var ownsAppliedMutation = false

  func update(
    request: WireMessage,
    response: WireResponsePayload,
    ownershipVerified: Bool
  ) {
    let reconcilePayload =
      request.operation == .reconcile
      ? try? WireReconcilePayload.decode(request.payload) : nil
    lock.withLock {
      if WireLifecyclePolicy.ownsAppliedMutation(
        requestOperation: request.operation,
        reconcilePayload: reconcilePayload,
        ownershipVerified: ownershipVerified,
        response: response
      ) {
        ownsAppliedMutation = true
      } else if WireLifecyclePolicy.completesCleanup(
        requestOperation: request.operation,
        reconcilePayload: reconcilePayload,
        response: response
      ) {
        ownsAppliedMutation = false
      }
    }
  }

  func shouldRestoreOnInvalidation() -> Bool {
    return lock.withLock { ownsAppliedMutation }
  }
}

/// How long the on-demand daemon waits before it exits idle. launchd starts it for a connection
/// that reaches the listener only after its code-signing check, which took more than a second on a
/// busy Mac (MACOS-027), so the first check after start waits longer.
struct DaemonIdleExit: Sendable {
  let startupGrace: DispatchTimeInterval
  let afterDisconnect: DispatchTimeInterval

  static let production = DaemonIdleExit(startupGrace: .seconds(10), afterDisconnect: .seconds(1))
}

/// Connections counted on the listener's thread the moment it accepts them, so an idle check that
/// is already due on the daemon's queue still sees them.
final class OpenConnections: @unchecked Sendable {
  private let lock = NSLock()
  private var open = 0
  private var opened = false

  var count: Int {
    return lock.withLock { open }
  }

  var anyOpened: Bool {
    return lock.withLock { opened }
  }

  func accept() {
    lock.withLock {
      open += 1
      opened = true
    }
  }

  func close() {
    lock.withLock { open = max(0, open - 1) }
  }
}

final class RequestCoordinator: @unchecked Sendable {
  let queue = DispatchQueue(label: "app.posato.macos.proxy-settings.requests")
  let engine: ProxyOwnershipEngine
  let grants: StandingGrantPersistence
  let identity: SystemIdentity
  let rules: AuthorizationRules
  var leaseDeadline: DispatchTime?
  private let connections = OpenConnections()
  private let startedAt = DispatchTime.now()
  private var powerMonitor: SystemPowerMonitor?
  private let idleExit: DaemonIdleExit
  private let terminate: @Sendable () -> Void

  init(
    persistence: OwnershipPersistence = DurableOwnershipStore(),
    configuration: ProxyConfigurationAccess = SystemProxyConfiguration(),
    grants: StandingGrantPersistence = StandingGrantStore(),
    identity: SystemIdentity = SystemIdentityReader(),
    rules: AuthorizationRules = SystemAuthorizationRules(),
    idleExit: DaemonIdleExit = .production,
    terminate: @escaping @Sendable () -> Void = { exit(EXIT_SUCCESS) }
  ) {
    self.idleExit = idleExit
    self.terminate = terminate
    engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)
    self.grants = grants
    self.identity = identity
    self.rules = rules
  }

  func start() throws {
    let monitor = SystemPowerMonitor(queue: queue) { [weak self] in
      self?.restoreForPowerTransition()
    }
    try monitor.start()
    powerMonitor = monitor
    queue.async {
      self.recordCleanupAttempt(try? self.engine.reconcile())
      self.scheduleLeaseCheck()
    }
  }

  /// Called synchronously from the listener before the connection resumes.
  func connectionOpened() {
    connections.accept()
  }

  func perform(
    _ encoded: Data,
    peerUserID: UInt32?,
    deadline: DispatchTime,
    connectionState: ConnectionState,
    reply: ReplyBox
  ) {
    queue.async {
      reply(
        self.process(
          encoded,
          peerUserID: peerUserID,
          deadline: deadline,
          connectionState: connectionState
        )
      )
    }
  }

  func connectionInvalidated(state: ConnectionState) {
    connections.close()
    queue.async {
      if state.shouldRestoreOnInvalidation() {
        self.recordCleanupAttempt(try? self.engine.restore())
      } else {
        self.scheduleIdleExit()
      }
    }
  }

  func failureResponse(_ failure: FailureCategory) -> WireResponsePayload {
    return WireResponsePayload(
      outcome: .failure,
      serviceState: .ready,
      ownershipPhase: (try? engine.status()) ?? .idle,
      failure: failure
    )
  }

  func recoveryResponse(_ failure: FailureCategory) -> WireResponsePayload {
    return WireResponsePayload(
      outcome: .actionRequired,
      serviceState: .recoveryRequired,
      ownershipPhase: (try? engine.status()) ?? .recoveryRequired,
      actionRequired: .manualRecovery,
      failure: failure
    )
  }

  private func scheduleLeaseCheck() {
    queue.asyncAfter(deadline: .now() + .seconds(1)) {
      if let deadline = self.leaseDeadline, DispatchTime.now() >= deadline {
        self.recordCleanupAttempt(try? self.engine.restore())
      } else if self.leaseDeadline != nil {
        let phase = try? self.engine.maintain()
        if WireLifecyclePolicy.cleanupCompleted(phase) {
          self.leaseDeadline = nil
          self.scheduleIdleExit()
        } else if !WireLifecyclePolicy.leaseRemainsHealthy(afterMaintenance: phase) {
          self.leaseDeadline = .now()
        }
      }
      self.scheduleLeaseCheck()
    }
  }

  private func recordCleanupAttempt(_ phase: OwnershipPhase?) {
    leaseDeadline = WireLifecyclePolicy.cleanupCompleted(phase) ? nil : .now()
    scheduleIdleExit()
  }

  private func restoreForPowerTransition() {
    recordCleanupAttempt(try? engine.restore())
  }

  func scheduleIdleExit() {
    let delay = connections.anyOpened ? idleExit.afterDisconnect : idleExit.startupGrace
    queue.asyncAfter(deadline: .now() + delay) {
      let graceEnd = self.startedAt + self.idleExit.startupGrace
      let pastStartupGrace = self.connections.anyOpened || DispatchTime.now() >= graceEnd
      // The connection count is read last, after the ownership file, so a connection accepted
      // during that read still keeps the daemon running.
      let shouldExit =
        pastStartupGrace
        && self.leaseDeadline == nil
        && (try? self.engine.status()) == .idle
        && self.connections.count == 0
      if shouldExit {
        self.powerMonitor?.stop()
        self.powerMonitor = nil
        self.terminate()
      }
    }
  }
}
