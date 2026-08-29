import Foundation

public struct WireSequenceValidator: Sendable {
  private var expected: UInt32 = 1

  public init() {}

  public mutating func accept(_ sequence: UInt32) -> Bool {
    guard sequence == expected,
      sequence <= WireLimits.maximumOperationsPerConnection
    else {
      return false
    }
    expected += 1
    return true
  }
}

public enum WireDeadline {
  public static func remainingMilliseconds(
    receivedUptimeNanoseconds: UInt64,
    currentUptimeNanoseconds: UInt64,
    budgetMilliseconds: UInt32
  ) throws -> UInt32 {
    guard currentUptimeNanoseconds >= receivedUptimeNanoseconds else {
      throw WireProtocolFailure.invalidDeadline
    }
    let elapsedMilliseconds =
      (currentUptimeNanoseconds - receivedUptimeNanoseconds) / 1_000_000
    guard elapsedMilliseconds < UInt64(budgetMilliseconds) else {
      throw WireProtocolFailure.invalidDeadline
    }
    return budgetMilliseconds - UInt32(elapsedMilliseconds)
  }
}

public enum WireLifecyclePolicy {
  public static func effectiveOperation(
    requestOperation: WireOperation,
    reconcilePayload: WireReconcilePayload?
  ) -> WireOperation {
    return reconcilePayload?.originalOperation ?? requestOperation
  }

  public static func ownsAppliedMutation(
    requestOperation: WireOperation,
    reconcilePayload: WireReconcilePayload?,
    ownershipVerified: Bool,
    response: WireResponsePayload
  ) -> Bool {
    let operation = effectiveOperation(
      requestOperation: requestOperation,
      reconcilePayload: reconcilePayload
    )
    guard operation == .apply, ownershipVerified, response.outcome != .conflict else {
      return false
    }
    return response.ownershipPhase != .idle
  }

  public static func cleanupCompleted(_ phase: OwnershipPhase?) -> Bool {
    return phase == .idle
  }

  public static func leaseRemainsHealthy(
    afterMaintenance phase: OwnershipPhase?
  ) -> Bool {
    return phase == .applied
  }

  public static func keepsLeaseHealthy(
    afterRenewal response: WireResponsePayload
  ) -> Bool {
    return response.outcome == .success && response.ownershipPhase == .applied
  }

  public static func failClosedApplyResponse(
    requestOperation: WireOperation,
    reconcilePayload: WireReconcilePayload?,
    ownershipVerified: Bool,
    response: WireResponsePayload
  ) -> WireResponsePayload {
    guard
      effectiveOperation(
        requestOperation: requestOperation,
        reconcilePayload: reconcilePayload
      ) == .apply,
      !ownershipVerified,
      response.ownershipPhase != .idle,
      response.outcome != .conflict
    else {
      return response
    }
    return WireResponsePayload(
      outcome: .conflict,
      serviceState: response.serviceState,
      ownershipPhase: response.ownershipPhase,
      actionRequired: response.actionRequired,
      failure: response.failure
    )
  }

  public static func unreconciledServiceResponse(
    serviceState: ServiceState
  ) -> WireResponsePayload {
    return WireResponsePayload(
      outcome: .actionRequired,
      serviceState: serviceState,
      ownershipPhase: .recoveryRequired,
      actionRequired: serviceState == .approvalRequired ? .backgroundApproval : .manualRecovery,
      failure: .lifecycle
    )
  }

  public static func reconcilesExistingOwnership(
    requestOperation: WireOperation,
    reconcilePayload: WireReconcilePayload?
  ) -> Bool {
    switch effectiveOperation(
      requestOperation: requestOperation,
      reconcilePayload: reconcilePayload
    ) {
    case .enable, .repair, .restore, .disable, .remove:
      return true
    default:
      return false
    }
  }

  public static func completesCleanup(
    requestOperation: WireOperation,
    reconcilePayload: WireReconcilePayload?,
    response: WireResponsePayload
  ) -> Bool {
    return reconcilesExistingOwnership(
      requestOperation: requestOperation,
      reconcilePayload: reconcilePayload
    )
      && response.outcome == .success
      && response.ownershipPhase == .idle
  }

  public static func shouldUnregister(
    requestOperation: WireOperation,
    reconcilePayload: WireReconcilePayload?,
    response: WireResponsePayload
  ) -> Bool {
    let operation = effectiveOperation(
      requestOperation: requestOperation,
      reconcilePayload: reconcilePayload
    )
    return (operation == .disable || operation == .remove)
      && completesCleanup(
        requestOperation: requestOperation,
        reconcilePayload: reconcilePayload,
        response: response
      )
  }
}
