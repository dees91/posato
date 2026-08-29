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

final class RequestCoordinator: @unchecked Sendable {
  let queue = DispatchQueue(label: "app.posato.macos.proxy-settings.requests")
  let engine = ProxyOwnershipEngine(
    persistence: DurableOwnershipStore(),
    configuration: SystemProxyConfiguration()
  )
  var leaseDeadline: DispatchTime?
  private var activeConnections = 0

  func start() {
    queue.async {
      self.recordCleanupAttempt(try? self.engine.reconcile())
      self.scheduleLeaseCheck()
    }
  }

  func connectionOpened() {
    queue.async {
      self.activeConnections += 1
    }
  }

  func perform(
    _ encoded: Data,
    deadline: DispatchTime,
    connectionState: ConnectionState,
    reply: ReplyBox
  ) {
    queue.async {
      reply(
        self.process(
          encoded,
          deadline: deadline,
          connectionState: connectionState
        )
      )
    }
  }

  func connectionInvalidated(state: ConnectionState) {
    queue.async {
      self.activeConnections = max(0, self.activeConnections - 1)
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

  private func scheduleIdleExit() {
    queue.asyncAfter(deadline: .now() + .seconds(1)) {
      let shouldExit =
        self.activeConnections == 0
        && self.leaseDeadline == nil
        && (try? self.engine.status()) == .idle
      if shouldExit {
        exit(EXIT_SUCCESS)
      }
    }
  }
}
