import Foundation

public enum BrowserDomainConfigureLimits {
  public static let maximumDomainCount = 1_024
  public static let minimumDomainLength = 3
  public static let maximumDomainLength = 253
  public static let minimumLabelLength = 1
  public static let maximumLabelLength = 63
}

/// One segment of a held website's window plan (TARGETS-013, ADR 0004 amendment): until an instant (exclusive), with
/// a rule of `period` and `length` minutes, or none when both are 0. The wire carries any byte; the helper decides
/// validity, so an invalid plan leaves its website blocked instead of failing the request.
public struct WindowSegmentWire: Equatable, Sendable {
  public let untilEpochMilliseconds: UInt64
  public let period: UInt8
  public let length: UInt8

  public init(untilEpochMilliseconds: UInt64, period: UInt8, length: UInt8) {
    self.untilEpochMilliseconds = untilEpochMilliseconds
    self.period = period
    self.length = length
  }
}

/// When a held website may be left open: its ordered segments up to the request's end.
public struct DomainWindowPlanWire: Equatable, Sendable {
  public let domain: String
  public let segments: [WindowSegmentWire]

  public init(domain: String, segments: [WindowSegmentWire]) {
    self.domain = domain
    self.segments = segments
  }
}

public struct BrowserDomainConfigurePayload: Equatable, Sendable {
  typealias Plan = DomainWindowPlanWire

  public let domains: [String]
  public let sessionEndEpochMilliseconds: UInt64?
  public let windows: [DomainWindowPlanWire]

  public init(
    domains: [String], sessionEndEpochMilliseconds: UInt64?, windows: [DomainWindowPlanWire] = []
  ) throws {
    guard domains.count >= 1,
      domains.count <= BrowserDomainConfigureLimits.maximumDomainCount,
      Set(domains).count == domains.count,
      domains.allSatisfy(Self.isCanonicalExactDomain)
    else {
      throw WireProtocolFailure.invalidFrame
    }
    self.domains = domains
    self.sessionEndEpochMilliseconds = sessionEndEpochMilliseconds
    self.windows = windows
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
    // The window section is optional: without it every held website has no plan.
    if !windows.isEmpty {
      guard windows.count <= domains.count else {
        throw WireProtocolFailure.invalidFrame
      }
      data.append(1)
      data.appendBigEndian(UInt16(windows.count))
      for plan in windows {
        let encoded = Data(plan.domain.utf8)
        guard encoded.count <= UInt8.max, plan.segments.count <= UInt8.max else {
          throw WireProtocolFailure.invalidFrame
        }
        data.append(UInt8(encoded.count))
        data.append(encoded)
        data.append(UInt8(plan.segments.count))
        for segment in plan.segments {
          data.appendBigEndian(segment.untilEpochMilliseconds)
          data.append(segment.period)
          data.append(segment.length)
        }
      }
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
    let windows = cursor.remainingBytes == 0 ? [] : try decodeWindows(&cursor, limit: count)
    guard cursor.remainingBytes == 0 else {
      throw WireProtocolFailure.invalidFrame
    }
    return try BrowserDomainConfigurePayload(
      domains: domains,
      sessionEndEpochMilliseconds: sessionEndEpochMilliseconds,
      windows: windows
    )
  }

  /// The framing is checked here; whether a plan is valid is the helper's decision (ADR 0004 amendment).
  private static func decodeWindows(_ cursor: inout WireDataCursor, limit: Int) throws -> [Plan] {
    guard try cursor.readUInt8() == 1 else {
      throw WireProtocolFailure.invalidFrame
    }
    let count = Int(try cursor.readUInt16())
    guard count >= 1, count <= limit else {
      throw WireProtocolFailure.invalidFrame
    }
    var plans: [DomainWindowPlanWire] = []
    plans.reserveCapacity(count)
    for _ in 0..<count {
      let domainData = try cursor.readBoundedData(
        maximumBytes: BrowserDomainConfigureLimits.maximumDomainLength)
      guard let domain = String(data: domainData, encoding: .utf8) else {
        throw WireProtocolFailure.invalidFrame
      }
      let segmentCount = Int(try cursor.readUInt8())
      var segments: [WindowSegmentWire] = []
      segments.reserveCapacity(segmentCount)
      for _ in 0..<segmentCount {
        let until = try cursor.readUInt64()
        let period = try cursor.readUInt8()
        let length = try cursor.readUInt8()
        segments.append(
          WindowSegmentWire(untilEpochMilliseconds: until, period: period, length: length))
      }
      plans.append(DomainWindowPlanWire(domain: domain, segments: segments))
    }
    return plans
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
