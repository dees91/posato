import Foundation

public enum BrowserDomainConfigureLimits {
  public static let maximumDomainCount = 1_024
  public static let minimumDomainLength = 3
  public static let maximumDomainLength = 253
  public static let minimumLabelLength = 1
  public static let maximumLabelLength = 63
}

public struct BrowserDomainConfigurePayload: Equatable, Sendable {
  public let domains: [String]
  public let sessionEndEpochMilliseconds: UInt64?

  public init(domains: [String], sessionEndEpochMilliseconds: UInt64?) throws {
    guard domains.count >= 1,
      domains.count <= BrowserDomainConfigureLimits.maximumDomainCount,
      Set(domains).count == domains.count,
      domains.allSatisfy(Self.isCanonicalExactDomain)
    else {
      throw WireProtocolFailure.invalidFrame
    }
    self.domains = domains
    self.sessionEndEpochMilliseconds = sessionEndEpochMilliseconds
  }

  public func encode() throws -> Data {
    var data = Data()
    data.appendBigEndian(UInt16(domains.count))
    for domain in domains {
      let encoded = Data(domain.utf8)
      guard encoded.count <= UInt8.max else {
        throw WireProtocolFailure.invalidFrame
      }
      data.append(UInt8(encoded.count))
      data.append(encoded)
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

  public static func decode(_ data: Data) throws -> BrowserDomainConfigurePayload {
    var cursor = WireDataCursor(data: data)
    let count = Int(try cursor.readUInt16())
    guard count >= 1, count <= BrowserDomainConfigureLimits.maximumDomainCount else {
      throw WireProtocolFailure.invalidFrame
    }
    var domains: [String] = []
    domains.reserveCapacity(count)
    for _ in 0..<count {
      let domainData = try cursor.readBoundedData(
        maximumBytes: BrowserDomainConfigureLimits.maximumDomainLength
      )
      guard let domain = String(data: domainData, encoding: .utf8) else {
        throw WireProtocolFailure.invalidFrame
      }
      domains.append(domain)
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
    return try BrowserDomainConfigurePayload(
      domains: domains,
      sessionEndEpochMilliseconds: sessionEndEpochMilliseconds
    )
  }

  private static func isCanonicalExactDomain(_ domain: String) -> Bool {
    let length = domain.utf8.count
    guard length >= BrowserDomainConfigureLimits.minimumDomainLength,
      length <= BrowserDomainConfigureLimits.maximumDomainLength,
      domain.allSatisfy({ character in
        character.isASCII
          && ((character.isLetter && character.isLowercase) || character.isNumber
            || character == "-" || character == ".")
      })
    else {
      return false
    }
    let labels = domain.split(separator: ".", omittingEmptySubsequences: false)
    guard labels.count >= 2, let last = labels.last, !last.allSatisfy(\.isNumber) else {
      return false
    }
    return labels.allSatisfy(isCanonicalDomainLabel)
  }

  private static func isCanonicalDomainLabel(_ label: Substring) -> Bool {
    let length = label.utf8.count
    guard length >= BrowserDomainConfigureLimits.minimumLabelLength,
      length <= BrowserDomainConfigureLimits.maximumLabelLength,
      let first = label.first, first.isLetter || first.isNumber,
      let last = label.last, last.isLetter || last.isNumber
    else {
      return false
    }
    return label.allSatisfy { character in
      character.isLetter || character.isNumber || character == "-"
    }
  }
}

extension BrowserDomainConfigurePayload: CustomStringConvertible, CustomDebugStringConvertible {
  public var description: String {
    return "BrowserDomainConfigurePayload(redacted)"
  }

  public var debugDescription: String {
    return description
  }
}

public struct BrowserDomainConfigureResponse: Equatable, Sendable {
  public let outcome: WireResponsePayload
  public let port: UInt16

  public init(outcome: WireResponsePayload, port: UInt16) throws {
    let successRequiresPort = outcome.outcome == .success
    guard successRequiresPort ? port > 0 : port == 0 else {
      throw WireProtocolFailure.invalidFrame
    }
    self.outcome = outcome
    self.port = port
  }

  public func encode() -> Data {
    var data = outcome.encode()
    data.appendBigEndian(port)
    return data
  }

  public static func decode(_ data: Data) throws -> BrowserDomainConfigureResponse {
    guard data.count == 7 else {
      throw WireProtocolFailure.invalidFrame
    }
    let outcome = try WireResponsePayload.decode(Data(data.prefix(5)))
    let port = data.suffix(2).reduce(UInt16(0)) { value, byte in
      (value << 8) | UInt16(byte)
    }
    return try BrowserDomainConfigureResponse(outcome: outcome, port: port)
  }
}

extension BrowserDomainConfigureResponse: CustomStringConvertible, CustomDebugStringConvertible {
  public var description: String {
    return "BrowserDomainConfigureResponse(redacted)"
  }

  public var debugDescription: String {
    return description
  }
}
