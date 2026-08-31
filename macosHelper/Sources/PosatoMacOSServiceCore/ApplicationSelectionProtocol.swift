import Foundation

public enum ApplicationSelectionLimits {
  public static let maximumMappings = 64
  public static let maximumDisplayNameBytes = 256
  public static let maximumRequirementBytes = 4_096
}

public enum ApplicationSelectionOutcome: UInt8, Sendable {
  case success = 1
  case cancelled = 2
  case selfSelection = 3
  case invalidOrUnsigned = 4
  case capacity = 5
  case failure = 6
}

public struct SelectedApplicationIdentity: Equatable, Sendable {
  public let displayName: String
  public var designatedRequirement: Data

  public init(displayName: String, designatedRequirement: Data) throws {
    guard Self.isValidDisplayName(displayName),
      designatedRequirement.count > 0,
      designatedRequirement.count <= ApplicationSelectionLimits.maximumRequirementBytes
    else {
      throw WireProtocolFailure.invalidFrame
    }
    self.displayName = displayName
    self.designatedRequirement = designatedRequirement
  }

  private static func isValidDisplayName(_ displayName: String) -> Bool {
    return !displayName.isEmpty
      && displayName == displayName.trimmingCharacters(in: .whitespacesAndNewlines)
      && displayName.utf8.count <= ApplicationSelectionLimits.maximumDisplayNameBytes
      && displayName.unicodeScalars.allSatisfy { scalar in
        !CharacterSet.controlCharacters.contains(scalar)
      }
  }
}

extension SelectedApplicationIdentity: CustomStringConvertible, CustomDebugStringConvertible {
  public var description: String {
    return "SelectedApplicationIdentity(redacted)"
  }

  public var debugDescription: String {
    return description
  }
}

public struct ApplicationSelectionPayload: Equatable, Sendable {
  public let outcome: ApplicationSelectionOutcome
  public var applications: [SelectedApplicationIdentity]

  public init(
    outcome: ApplicationSelectionOutcome,
    applications: [SelectedApplicationIdentity] = []
  ) throws {
    let hasValidApplications =
      outcome == .success
      ? applications.count > 0
        && applications.count <= ApplicationSelectionLimits.maximumMappings
      : applications.isEmpty
    guard hasValidApplications else {
      throw WireProtocolFailure.invalidFrame
    }
    self.outcome = outcome
    self.applications = applications
  }

  public func encode() throws -> Data {
    var data = Data([outcome.rawValue])
    data.appendBigEndian(UInt16(applications.count))
    for application in applications {
      let displayName = Data(application.displayName.utf8)
      data.appendBigEndian(UInt16(displayName.count))
      data.append(displayName)
      data.appendBigEndian(UInt16(application.designatedRequirement.count))
      data.append(application.designatedRequirement)
    }
    guard data.count <= WireLimits.maximumFrameBytes - 68 else {
      throw WireProtocolFailure.oversizedFrame
    }
    return data
  }

  public static func decode(_ data: Data) throws -> ApplicationSelectionPayload {
    var cursor = ApplicationSelectionCursor(data: data)
    guard let outcome = ApplicationSelectionOutcome(rawValue: try cursor.readUInt8()) else {
      throw WireProtocolFailure.invalidFrame
    }
    let count = Int(try cursor.readUInt16())
    if outcome != .success {
      guard count == 0, cursor.remainingBytes == 0 else {
        throw WireProtocolFailure.invalidFrame
      }
      return try ApplicationSelectionPayload(outcome: outcome)
    }
    guard count > 0, count <= ApplicationSelectionLimits.maximumMappings else {
      throw WireProtocolFailure.invalidFrame
    }
    var applications: [SelectedApplicationIdentity] = []
    applications.reserveCapacity(count)
    for _ in 0..<count {
      let displayNameData = try cursor.readBoundedData(
        maximumBytes: ApplicationSelectionLimits.maximumDisplayNameBytes
      )
      guard let displayName = String(data: displayNameData, encoding: .utf8) else {
        throw WireProtocolFailure.invalidFrame
      }
      let requirement = try cursor.readBoundedData(
        maximumBytes: ApplicationSelectionLimits.maximumRequirementBytes
      )
      applications.append(
        try SelectedApplicationIdentity(
          displayName: displayName,
          designatedRequirement: requirement
        )
      )
    }
    guard cursor.remainingBytes == 0 else {
      throw WireProtocolFailure.invalidFrame
    }
    return try ApplicationSelectionPayload(outcome: .success, applications: applications)
  }
}

extension ApplicationSelectionPayload: CustomStringConvertible, CustomDebugStringConvertible {
  public var description: String {
    return "ApplicationSelectionPayload(redacted)"
  }

  public var debugDescription: String {
    return description
  }
}

private struct ApplicationSelectionCursor {
  let data: Data
  var offset = 0

  var remainingBytes: Int {
    return data.count - offset
  }

  mutating func readUInt8() throws -> UInt8 {
    guard remainingBytes >= 1 else {
      throw WireProtocolFailure.invalidFrame
    }
    defer { offset += 1 }
    return data[offset]
  }

  mutating func readUInt16() throws -> UInt16 {
    let bytes = try readData(count: 2)
    return bytes.reduce(UInt16(0)) { value, byte in
      (value << 8) | UInt16(byte)
    }
  }

  mutating func readBoundedData(maximumBytes: Int) throws -> Data {
    let count = Int(try readUInt16())
    guard count > 0, count <= maximumBytes else {
      throw WireProtocolFailure.invalidFrame
    }
    return try readData(count: count)
  }

  mutating func readData(count: Int) throws -> Data {
    guard count >= 0, remainingBytes >= count else {
      throw WireProtocolFailure.invalidFrame
    }
    defer { offset += count }
    return data.subdata(in: offset..<(offset + count))
  }
}
