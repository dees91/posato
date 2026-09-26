import Foundation

public struct WireGrantState: OptionSet, Equatable, Sendable {
  public let rawValue: UInt8

  public init(rawValue: UInt8) {
    self.rawValue = rawValue
  }

  public static let standingRightExact = WireGrantState(rawValue: 1)
  public static let granted = WireGrantState(rawValue: 2)
}

public enum WireStatusRequest {
  public static let includeGrantState = Data([1])
}

extension WireResponsePayload {
  /// A Status that asked for the grant state carries one more byte, and only on success; every
  /// other reply keeps the fixed five bytes an older helper decodes.
  public static func decodeStatus(
    _ data: Data
  ) throws -> (response: WireResponsePayload, grantState: WireGrantState?) {
    guard data.count == 5 || data.count == 6 else {
      throw WireProtocolFailure.invalidFrame
    }
    let response = try decode(Data(data.prefix(5)))
    let grantState = data.count == 6 ? WireGrantState(rawValue: data[data.startIndex + 5]) : nil
    return (response, grantState)
  }
}
