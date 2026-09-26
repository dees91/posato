import Foundation
import PosatoMacOSServiceCore

extension RequestCoordinator {
  func requireExactRule(_ right: AuthorizationRight) throws {
    guard try rules.state(of: right) == .exact else {
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
  }

  func grantState(peerUserID: UInt32?) -> WireGrantState {
    guard (try? rules.state(of: .standingApply)) == .exact else {
      return []
    }
    guard let peerUserID, let record = try? grants.load(),
      StandingGrantPolicy.isGranted(record: record, peerUserID: peerUserID, identity: identity)
    else {
      return [.standingRightExact]
    }
    return [.standingRightExact, .granted]
  }

  /// Enable installs an absent rule, Repair also replaces a mismatched one, and writing either
  /// rule first deletes every grant: a missing or changed rule means a removal or tampering.
  func convergeRules(repair: Bool) throws {
    for right in AuthorizationRight.allCases {
      switch try rules.state(of: right) {
      case .exact:
        continue
      case .mismatched where !repair:
        throw AuthorizationPolicyFailure.ruleUnavailable
      case .absent, .mismatched:
        try removeGrantRecord()
        try rules.write(right)
      }
    }
  }

  func removeGrantRecord() throws {
    try grants.remove()
    let remaining: StandingGrantRecord?
    do {
      remaining = try grants.load()
    } catch {
      throw DurableOwnershipFailure.unavailable
    }
    guard remaining == nil else {
      throw DurableOwnershipFailure.unavailable
    }
  }

  func requireStandingGrant(peerUserID: UInt32?) throws {
    guard let peerUserID,
      (try? rules.state(of: .standingApply)) == .exact,
      let record = try? grants.load(),
      StandingGrantPolicy.isGranted(record: record, peerUserID: peerUserID, identity: identity)
    else {
      throw StandingGrantFailure.unavailable
    }
  }

  func performPrepareGrant() throws -> OwnershipPhase {
    try requireExactRule(.apply)
    switch try rules.state(of: .standingApply) {
    case .exact:
      break
    case .absent:
      try removeGrantRecord()
      try rules.write(.standingApply)
    case .mismatched:
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
    return try engine.status()
  }

  func performGrant(_ request: WireMessage, peerUserID: UInt32?) throws -> OwnershipPhase {
    guard request.payload.count == AuthorizationPolicy.externalFormBytes else {
      throw ProxyOwnershipFailure.invalidInput
    }
    try requireExactRule(.apply)
    try requireExactRule(.standingApply)
    var authorizationData = request.payload
    try rules.validateAndDestroy(.standingApply, externalForm: &authorizationData)
    guard let peerUserID else {
      throw StandingGrantFailure.unavailable
    }
    let granted = try StandingGrantPolicy.granting(
      record: try? grants.load(),
      peerUserID: peerUserID,
      identity: identity,
      now: Date()
    )
    do {
      try grants.save(granted)
    } catch {
      throw StandingGrantFailure.storage
    }
    return try engine.status()
  }

  /// Revoke needs no administrator and only ever takes back the caller's own grant; a record that
  /// cannot be read is deleted whole.
  func performRevokeGrant(peerUserID: UInt32?) throws -> OwnershipPhase {
    guard let peerUserID else {
      throw StandingGrantFailure.unavailable
    }
    do {
      let record: StandingGrantRecord?
      do {
        record = try grants.load()
      } catch DurableOwnershipFailure.invalidFile, DurableOwnershipFailure.invalidState {
        try removeGrantRecord()
        return try engine.status()
      }
      if let record {
        if let kept = StandingGrantPolicy.revoking(record: record, peerUserID: peerUserID) {
          try grants.save(kept)
        } else {
          try removeGrantRecord()
        }
      }
    } catch {
      throw StandingGrantFailure.storage
    }
    return try engine.status()
  }
}
