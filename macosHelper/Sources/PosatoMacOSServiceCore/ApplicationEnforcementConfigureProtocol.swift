import Foundation

public enum ApplicationEnforcementLimits {
  public static let maximumApplications = ApplicationSelectionLimits.maximumMappings
  public static let maximumRequirementBytes = ApplicationSelectionLimits.maximumRequirementBytes
}

public struct ApplicationEnforcementConfigurePayload: Equatable, Sendable {
  public let requirements: [Data]
  public let sessionEndEpochMilliseconds: UInt64?

  public init(requirements: [Data], sessionEndEpochMilliseconds: UInt64?) throws {
    guard requirements.count <= ApplicationEnforcementLimits.maximumApplications,
      Set(requirements).count == requirements.count,
      requirements.allSatisfy({
        !$0.isEmpty && $0.count <= ApplicationEnforcementLimits.maximumRequirementBytes
      })
    else {
      throw WireProtocolFailure.invalidFrame
    }
    self.requirements = requirements
    self.sessionEndEpochMilliseconds = sessionEndEpochMilliseconds
  }

  public func encode() throws -> Data {
    var data = Data()
    data.appendBigEndian(UInt16(requirements.count))
    for requirement in requirements {
      data.appendBigEndian(UInt16(requirement.count))
      data.append(requirement)
    }
    if let sessionEndEpochMilliseconds {
      data.append(1)
      data.appendBigEndian(sessionEndEpochMilliseconds)
    } else {
      data.append(0)
    }
    guard data.count <= WireLimits.maximumFrameBytes - 68 else {
      throw WireProtocolFailure.oversizedFrame
    }
    return data
  }

  public static func decode(_ data: Data) throws -> ApplicationEnforcementConfigurePayload {
    var cursor = WireDataCursor(data: data)
    let count = Int(try cursor.readUInt16())
    guard count >= 0, count <= ApplicationEnforcementLimits.maximumApplications else {
      throw WireProtocolFailure.invalidFrame
    }
    var requirements: [Data] = []
    requirements.reserveCapacity(count)
    for _ in 0..<count {
      // Requirement blobs reach 4_096 bytes, so the length is two bytes;
      // the shared one-byte readBoundedData cannot address them.
      let length = Int(try cursor.readUInt16())
      guard length > 0, length <= ApplicationEnforcementLimits.maximumRequirementBytes else {
        throw WireProtocolFailure.invalidFrame
      }
      requirements.append(try cursor.readData(count: length))
    }
    let hasEndTime = try cursor.readUInt8()
    let sessionEndEpochMilliseconds: UInt64?
    switch hasEndTime {
    case 0:
      sessionEndEpochMilliseconds = nil
    case 1:
      sessionEndEpochMilliseconds = try cursor.readUInt64()
    default:
      throw WireProtocolFailure.invalidFrame
    }
    guard cursor.remainingBytes == 0 else {
      throw WireProtocolFailure.invalidFrame
    }
    return try ApplicationEnforcementConfigurePayload(
      requirements: requirements,
      sessionEndEpochMilliseconds: sessionEndEpochMilliseconds
    )
  }
}

extension ApplicationEnforcementConfigurePayload: CustomStringConvertible {
  public var description: String {
    return "ApplicationEnforcementConfigurePayload(redacted)"
  }
}

extension ApplicationEnforcementConfigurePayload: CustomDebugStringConvertible {
  public var debugDescription: String {
    return description
  }
}

public struct ApplicationEnforcementConfigureResponse: Equatable, Sendable {
  public let outcome: WireResponsePayload
  public let acceptedCount: UInt16

  public init(outcome: WireResponsePayload, acceptedCount: UInt16) throws {
    guard outcome.outcome == .success || acceptedCount == 0 else {
      throw WireProtocolFailure.invalidFrame
    }
    self.outcome = outcome
    self.acceptedCount = acceptedCount
  }

  public func encode() -> Data {
    var data = outcome.encode()
    data.appendBigEndian(acceptedCount)
    return data
  }

  public static func decode(_ data: Data) throws -> ApplicationEnforcementConfigureResponse {
    guard data.count == 7 else {
      throw WireProtocolFailure.invalidFrame
    }
    let outcome = try WireResponsePayload.decode(Data(data.prefix(5)))
    let acceptedCount = data.suffix(2).reduce(UInt16(0)) { value, byte in
      (value << 8) | UInt16(byte)
    }
    return try ApplicationEnforcementConfigureResponse(
      outcome: outcome, acceptedCount: acceptedCount)
  }
}

extension ApplicationEnforcementConfigureResponse: CustomStringConvertible {
  public var description: String {
    return "ApplicationEnforcementConfigureResponse(redacted)"
  }
}

extension ApplicationEnforcementConfigureResponse: CustomDebugStringConvertible {
  public var debugDescription: String {
    return description
  }
}
