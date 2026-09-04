import CryptoKit
import Foundation

public enum WireLimits {
  public static let protocolMajor: UInt16 = 1
  public static let maximumFrameBytes = 512 * 1024
  public static let maximumXPCBytes = 64 * 1024
  public static let identifierBytes = 16
  public static let maximumDeadlineMilliseconds: UInt32 = 120_000
  public static let maximumConfigureDeadlineMilliseconds: UInt32 = 10_000
  public static let maximumSelectionDeadlineMilliseconds: UInt32 = 1_800_000
  public static let maximumOperationsPerConnection: UInt32 = 256
  public static let requiredCapabilities: UInt64 = 1
  public static let applicationSelectionCapability: UInt64 = 2
  public static let browserDomainConfigureCapability: UInt64 = 4
  public static let requiredParentHelperCapabilities: UInt64 =
    requiredCapabilities | applicationSelectionCapability | browserDomainConfigureCapability

  public static func maximumDeadlineMilliseconds(for operation: WireOperation) -> UInt32 {
    switch operation {
    case .selectApplications:
      return maximumSelectionDeadlineMilliseconds
    case .configureBrowserDomains:
      return maximumConfigureDeadlineMilliseconds
    case .none, .status, .enable, .repair, .apply, .restore, .disable, .remove, .reconcile, .renew:
      return maximumDeadlineMilliseconds
    }
  }
}

public enum WireCapabilities {
  public static func encode(_ capabilities: UInt64) -> Data {
    var data = Data()
    data.appendBigEndian(capabilities)
    return data
  }

  public static func decode(_ data: Data) throws -> UInt64 {
    guard data.count == 8 else {
      throw WireProtocolFailure.invalidFrame
    }
    return data.reduce(UInt64(0)) { value, byte in
      (value << 8) | UInt64(byte)
    }
  }

  public static func supports(_ offered: UInt64, required: UInt64) -> Bool {
    return offered & required == required
  }
}

public enum WireMessageKind: UInt8, Sendable {
  case hello = 1
  case welcome = 2
  case request = 3
  case response = 4
  case cancel = 5
}

public enum WireOperation: UInt8, Sendable {
  case none = 0
  case status = 1
  case enable = 2
  case repair = 3
  case apply = 4
  case restore = 5
  case disable = 6
  case remove = 7
  case reconcile = 8
  case renew = 9
  case selectApplications = 10
  case configureBrowserDomains = 11

  public var isHelperOnly: Bool {
    switch self {
    case .selectApplications, .configureBrowserDomains:
      return true
    case .none, .status, .enable, .repair, .apply, .restore, .disable, .remove, .reconcile, .renew:
      return false
    }
  }
}

public enum WireOutcome: UInt8, Sendable {
  case success = 1
  case conflict = 2
  case actionRequired = 3
  case unknownOutcome = 4
  case failure = 5
}

public enum ServiceState: UInt8, Codable, Sendable {
  case notRegistered = 1
  case approvalRequired = 2
  case ready = 3
  case unavailableOrIncompatible = 4
  case recoveryRequired = 5
}

public enum OwnershipPhase: UInt8, Codable, Sendable {
  case idle = 1
  case prepared = 2
  case applied = 3
  case restorePending = 4
  case recoveryRequired = 5
}

public enum ActionRequiredReason: UInt8, Sendable {
  case none = 0
  case backgroundApproval = 1
  case administratorAuthentication = 2
  case ruleRepair = 3
  case proxyRecovery = 4
  case manualRecovery = 5
  case incompatible = 6
}

public enum FailureCategory: UInt8, Sendable {
  case none = 0
  case invalidInput = 1
  case unavailable = 2
  case permission = 3
  case timeout = 4
  case integrity = 5
  case storage = 6
  case ipc = 7
  case lifecycle = 8
  case cancelled = 9
}

public enum WireProtocolFailure: Error, Equatable {
  case invalidFrame
  case invalidVersion
  case invalidKind
  case invalidOperation
  case invalidSequence
  case invalidDeadline
  case oversizedFrame
}

public struct WireMessage: Equatable, Sendable {
  public let kind: WireMessageKind
  public let operation: WireOperation
  public let sequence: UInt32
  public let deadlineMilliseconds: UInt32
  public let connectionIdentifier: Data
  public let sessionIdentifier: Data
  public let requestIdentifier: Data
  public var payload: Data

  public init(
    kind: WireMessageKind,
    operation: WireOperation,
    sequence: UInt32,
    deadlineMilliseconds: UInt32,
    connectionIdentifier: Data,
    sessionIdentifier: Data,
    requestIdentifier: Data,
    payload: Data
  ) throws {
    guard connectionIdentifier.count == WireLimits.identifierBytes,
      sessionIdentifier.count == WireLimits.identifierBytes,
      requestIdentifier.count == WireLimits.identifierBytes
    else {
      throw WireProtocolFailure.invalidFrame
    }
    let maximumDeadline = WireLimits.maximumDeadlineMilliseconds(for: operation)
    guard deadlineMilliseconds <= maximumDeadline else {
      throw WireProtocolFailure.invalidDeadline
    }
    guard payload.count <= WireLimits.maximumFrameBytes else {
      throw WireProtocolFailure.oversizedFrame
    }
    self.kind = kind
    self.operation = operation
    self.sequence = sequence
    self.deadlineMilliseconds = deadlineMilliseconds
    self.connectionIdentifier = connectionIdentifier
    self.sessionIdentifier = sessionIdentifier
    self.requestIdentifier = requestIdentifier
    self.payload = payload
  }

  public var canonicalInputDigest: Data {
    return WireCodec.canonicalInputDigest(operation: operation, payload: payload)
  }
}

public enum WireCodec {
  private static let magic: UInt32 = 0x5053_544F
  private static let headerBytes = 68

  public static func canonicalInputDigest(
    operation: WireOperation,
    payload: Data
  ) -> Data {
    var canonical = Data([operation.rawValue])
    canonical.append(payload)
    return Data(SHA256.hash(data: canonical))
  }

  public static func encode(_ message: WireMessage) throws -> Data {
    var encoded = Data()
    encoded.appendBigEndian(magic)
    encoded.appendBigEndian(WireLimits.protocolMajor)
    encoded.append(message.kind.rawValue)
    encoded.append(message.operation.rawValue)
    encoded.appendBigEndian(message.sequence)
    encoded.appendBigEndian(message.deadlineMilliseconds)
    encoded.append(message.connectionIdentifier)
    encoded.append(message.sessionIdentifier)
    encoded.append(message.requestIdentifier)
    encoded.appendBigEndian(UInt32(message.payload.count))
    encoded.append(message.payload)
    guard encoded.count <= WireLimits.maximumFrameBytes else {
      throw WireProtocolFailure.oversizedFrame
    }
    return encoded
  }

  public static func decode(
    _ encoded: Data,
    maximumBytes: Int,
    allowsHelperOnlyOperations: Bool = false,
    maximumDeadlineMilliseconds: UInt32 = WireLimits.maximumDeadlineMilliseconds
  ) throws -> WireMessage {
    guard encoded.count <= maximumBytes else {
      throw WireProtocolFailure.oversizedFrame
    }
    guard encoded.count >= headerBytes else {
      throw WireProtocolFailure.invalidFrame
    }
    var cursor = WireDataCursor(data: encoded)
    guard try cursor.readUInt32() == magic else {
      throw WireProtocolFailure.invalidFrame
    }
    guard try cursor.readUInt16() == WireLimits.protocolMajor else {
      throw WireProtocolFailure.invalidVersion
    }
    guard let kind = WireMessageKind(rawValue: try cursor.readUInt8()) else {
      throw WireProtocolFailure.invalidKind
    }
    guard let operation = WireOperation(rawValue: try cursor.readUInt8()) else {
      throw WireProtocolFailure.invalidOperation
    }
    guard allowsHelperOnlyOperations || !operation.isHelperOnly else {
      throw WireProtocolFailure.invalidOperation
    }
    let sequence = try cursor.readUInt32()
    let deadline = try cursor.readUInt32()
    guard deadline <= maximumDeadlineMilliseconds else {
      throw WireProtocolFailure.invalidDeadline
    }
    let connectionIdentifier = try cursor.readData(count: WireLimits.identifierBytes)
    let sessionIdentifier = try cursor.readData(count: WireLimits.identifierBytes)
    let requestIdentifier = try cursor.readData(count: WireLimits.identifierBytes)
    let payloadBytes = Int(try cursor.readUInt32())
    guard payloadBytes == cursor.remainingBytes else {
      throw WireProtocolFailure.invalidFrame
    }
    let payload = try cursor.readData(count: payloadBytes)
    return try WireMessage(
      kind: kind,
      operation: operation,
      sequence: sequence,
      deadlineMilliseconds: deadline,
      connectionIdentifier: connectionIdentifier,
      sessionIdentifier: sessionIdentifier,
      requestIdentifier: requestIdentifier,
      payload: payload
    )
  }
}

public struct WireResponsePayload: Equatable, Sendable {
  public let outcome: WireOutcome
  public let serviceState: ServiceState
  public let ownershipPhase: OwnershipPhase
  public let actionRequired: ActionRequiredReason
  public let failure: FailureCategory

  public init(
    outcome: WireOutcome,
    serviceState: ServiceState,
    ownershipPhase: OwnershipPhase,
    actionRequired: ActionRequiredReason = .none,
    failure: FailureCategory = .none
  ) {
    self.outcome = outcome
    self.serviceState = serviceState
    self.ownershipPhase = ownershipPhase
    self.actionRequired = actionRequired
    self.failure = failure
  }

  public func encode() -> Data {
    return Data([
      outcome.rawValue,
      serviceState.rawValue,
      ownershipPhase.rawValue,
      actionRequired.rawValue,
      failure.rawValue,
    ])
  }

  public static func decode(_ data: Data) throws -> WireResponsePayload {
    guard data.count == 5,
      let outcome = WireOutcome(rawValue: data[0]),
      let serviceState = ServiceState(rawValue: data[1]),
      let ownershipPhase = OwnershipPhase(rawValue: data[2]),
      let actionRequired = ActionRequiredReason(rawValue: data[3]),
      let failure = FailureCategory(rawValue: data[4])
    else {
      throw WireProtocolFailure.invalidFrame
    }
    return WireResponsePayload(
      outcome: outcome,
      serviceState: serviceState,
      ownershipPhase: ownershipPhase,
      actionRequired: actionRequired,
      failure: failure
    )
  }
}

public struct WireReconcilePayload: Equatable, Sendable {
  public let originalOperation: WireOperation
  public let canonicalInputDigest: Data

  public init(
    originalOperation: WireOperation,
    canonicalInputDigest: Data
  ) throws {
    guard Self.supportedOperations.contains(originalOperation),
      canonicalInputDigest.count == 32
    else {
      throw WireProtocolFailure.invalidFrame
    }
    self.originalOperation = originalOperation
    self.canonicalInputDigest = canonicalInputDigest
  }

  public func encode() -> Data {
    var data = Data([originalOperation.rawValue])
    data.append(canonicalInputDigest)
    return data
  }

  public static func decode(_ data: Data) throws -> WireReconcilePayload {
    guard data.count == 33,
      let operation = WireOperation(rawValue: data[0])
    else {
      throw WireProtocolFailure.invalidFrame
    }
    return try WireReconcilePayload(
      originalOperation: operation,
      canonicalInputDigest: Data(data.dropFirst())
    )
  }

  private static let supportedOperations: Set<WireOperation> = [
    .status, .enable, .repair, .apply, .restore, .disable, .remove,
  ]
}
