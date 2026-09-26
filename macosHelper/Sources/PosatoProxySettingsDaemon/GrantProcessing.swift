import Foundation
import PosatoMacOSServiceCore

struct RequestContext {
  let peerUserID: UInt32?
  var ownershipVerified = false
  var grantState: WireGrantState?
}

extension RequestCoordinator {
  func requireExactRule(_ right: AuthorizationRight) throws {
    guard try rules.state(of: right) == .exact else {
      throw AuthorizationPolicyFailure.ruleUnavailable
    }
  }

  /// A store that cannot be read says nothing about the grant, so Status fails instead of reporting
  /// it off; only a record that fails its file or schema checks, and so can never authorize, reads
  /// as no grant.
  func grantState(peerUserID: UInt32?) throws -> WireGrantState {
    guard try rules.state(of: .standingApply) == .exact else {
      return []
    }
    let record: StandingGrantRecord?
    do {
      record = try grantRecordIsReadable() ? grants.load() : nil
    } catch {
      throw StandingGrantFailure.storage
    }
    guard let peerUserID, let record,
      StandingGrantPolicy.isGranted(record: record, peerUserID: peerUserID, identity: identity)
    else {
      return [.standingRightExact]
    }
    return [.standingRightExact, .granted]
  }

  /// Enable installs an absent rule, Repair also replaces a mismatched one, and writing either
  /// rule first deletes every grant: a missing or changed rule means a removal or tampering.
  func convergeRules(repair: Bool) throws {
    if repair, try !grantRecordIsReadable() {
      try removeGrantRecord()
    }
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

  /// Only a record that fails its file or schema checks is unusable; an I/O failure is not proof.
  private func grantRecordIsReadable() throws -> Bool {
    do {
      _ = try grants.load()
      return true
    } catch DurableOwnershipFailure.invalidFile, DurableOwnershipFailure.invalidState {
      return false
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

  func performGrantOperation(
    _ request: WireMessage,
    peerUserID: UInt32?
  ) throws -> OwnershipPhase {
    switch request.operation {
    case .prepareGrant:
      try requireEmptyPayload(request)
      return try performPrepareGrant()
    case .grant:
      return try performGrant(request, peerUserID: peerUserID)
    case .revokeGrant:
      try requireEmptyPayload(request)
      return try performRevokeGrant(peerUserID: peerUserID)
    default:
      throw ProxyOwnershipFailure.invalidInput
    }
  }

  private func performPrepareGrant() throws -> OwnershipPhase {
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

  private func performGrant(_ request: WireMessage, peerUserID: UInt32?) throws -> OwnershipPhase {
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
    let existing: StandingGrantRecord?
    do {
      existing = try grantRecordIsReadable() ? grants.load() : nil
    } catch {
      throw StandingGrantFailure.storage
    }
    let granted = try StandingGrantPolicy.granting(
      record: existing,
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
  private func performRevokeGrant(peerUserID: UInt32?) throws -> OwnershipPhase {
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
        let kept = StandingGrantPolicy.revoking(
          record: record, peerUserID: peerUserID, identity: identity)
        if let kept {
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
