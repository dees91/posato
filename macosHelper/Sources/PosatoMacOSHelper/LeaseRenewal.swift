import Foundation
import PosatoMacOSServiceCore

final class LeaseRenewalChannel: @unchecked Sendable {
  private let requirement: String
  private var connection: DaemonConnection?

  init(requirement: String) {
    self.requirement = requirement
  }

  func perform(_ request: WireMessage) throws -> WireMessage {
    if connection?.hasCapacity != true {
      let replacement = try DaemonConnection(requirement: requirement)
      connection?.invalidate()
      connection = replacement
    }
    guard let connection else {
      throw PipeFailure.unavailable
    }
    return try connection.perform(request)
  }

  func invalidate() {
    connection?.invalidate()
    connection = nil
  }
}

final class LeaseRenewalGate: @unchecked Sendable {
  private let lock = NSLock()
  private var isActive = true

  func run(_ operation: () -> Void) {
    lock.withLock {
      guard isActive else {
        return
      }
      operation()
    }
  }

  func retire() {
    lock.withLock {
      isActive = false
    }
  }
}

/// ADR 0004: a renewal that only times out, as when a busy Mac delays the daemon for seconds, is
/// retried on the next period while the daemon's lease may still run; the helper gives up once no
/// renewal sent within the lease has succeeded.
final class LeaseRenewalWindow: @unchecked Sendable {
  private var lastSuccessSentAt: DispatchTime

  init(startedAt: DispatchTime) {
    lastSuccessSentAt = startedAt
  }

  func recordSuccess(sentAt: DispatchTime) {
    lastSuccessSentAt = sentAt
  }

  func allowsRetry(at now: DispatchTime) -> Bool {
    return now < lastSuccessSentAt + .milliseconds(WireLimits.leaseMilliseconds)
  }
}

final class LeaseRenewer: @unchecked Sendable {
  private let timer: DispatchSourceTimer
  private let gate = LeaseRenewalGate()
  private let channel: LeaseRenewalChannel

  init(
    ownershipConnection: DaemonConnection,
    daemonRequirement: String,
    request: WireMessage
  ) {
    channel = LeaseRenewalChannel(requirement: daemonRequirement)
    let window = LeaseRenewalWindow(startedAt: .now())
    timer = DispatchSource.makeTimerSource(
      queue: DispatchQueue(label: "app.posato.macos.helper.lease")
    )
    timer.schedule(deadline: .now() + .seconds(5), repeating: .seconds(5))
    timer.setEventHandler { [channel, gate] in
      // One renewal per period, never a loop: retirement waits on this gate.
      gate.run {
        let sentAt = DispatchTime.now()
        do {
          let renew = try WireMessage(
            kind: .request,
            operation: .renew,
            sequence: request.sequence,
            deadlineMilliseconds: 5_000,
            connectionIdentifier: request.connectionIdentifier,
            sessionIdentifier: request.sessionIdentifier,
            requestIdentifier: request.requestIdentifier,
            payload: Data()
          )
          let response = try channel.perform(renew)
          let payload = try WireResponsePayload.decode(response.payload)
          guard WireLifecyclePolicy.keepsLeaseHealthy(afterRenewal: payload) else {
            throw PipeFailure.unavailable
          }
          window.recordSuccess(sentAt: sentAt)
        } catch PipeFailure.timedOut where window.allowsRetry(at: .now()) {
          return
        } catch {
          channel.invalidate()
          ownershipConnection.invalidate()
          exit(EXIT_FAILURE)
        }
      }
    }
    timer.resume()
  }

  func cancelAndWait() {
    timer.cancel()
    gate.retire()
    channel.invalidate()
  }
}
