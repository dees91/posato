import Foundation

public enum ProxyValue: Codable, Equatable, Sendable {
  case integer(Int64)
  case string(String)
}

public struct ProxyTuple: Codable, Equatable, Sendable {
  public var enabled: ProxyValue?
  public var host: ProxyValue?
  public var port: ProxyValue?

  public init(enabled: ProxyValue?, host: ProxyValue?, port: ProxyValue?) {
    self.enabled = enabled
    self.host = host
    self.port = port
  }

  var hasValidBaselineSemantics: Bool {
    let validEnabled: Bool
    switch enabled {
    case .integer(let value):
      validEnabled = value == 0 || value == 1
    case nil:
      validEnabled = true
    case .string:
      validEnabled = false
    }
    let validHost: Bool
    switch host {
    case .string(let value):
      validHost = value.utf8.count <= 2_048
    case nil:
      validHost = true
    case .integer:
      validHost = false
    }
    let validPort: Bool
    switch port {
    case .integer(let value):
      validPort = value >= 0 && value <= 65_535
    case nil:
      validPort = true
    case .string:
      validPort = false
    }
    return validEnabled && validHost && validPort
  }

  var hasValidAppliedSemantics: Bool {
    return enabled == .integer(1)
      && host == .string("127.0.0.1")
      && {
        if case .integer(let value) = port {
          return value > 0 && value <= 65_535
        }
        return false
      }()
  }

  var isEnabled: Bool {
    return enabled != nil && enabled != .integer(0)
  }
}

public struct ProxySnapshot: Equatable, Sendable {
  public let serviceIdentifier: String
  public let http: ProxyTuple
  public let https: ProxyTuple
  public let additionalProxyEnabled: Bool
  public let exceptions: ProxyExceptions

  public init(
    serviceIdentifier: String,
    http: ProxyTuple,
    https: ProxyTuple,
    additionalProxyEnabled: Bool = false,
    exceptions: ProxyExceptions = .absent
  ) {
    self.serviceIdentifier = serviceIdentifier
    self.http = http
    self.https = https
    self.additionalProxyEnabled = additionalProxyEnabled
    self.exceptions = exceptions
  }

  var hasEnabledProxy: Bool {
    return http.isEnabled || https.isEnabled || additionalProxyEnabled
  }
}

/// The exceptions group a version 2 record owns: digests and the appended suffix, never the list (ADR 0004,
/// MACOS-024 amendment).
public struct OwnedExceptions: Codable, Equatable, Sendable {
  public let baselinePresent: Bool
  public let baselineDigest: Data?
  public let appliedDigest: Data
  public let appended: [String]

  public init(
    baselinePresent: Bool,
    baselineDigest: Data?,
    appliedDigest: Data,
    appended: [String]
  ) {
    self.baselinePresent = baselinePresent
    self.baselineDigest = baselineDigest
    self.appliedDigest = appliedDigest
    self.appended = appended
  }

  /// What Apply owns and writes for a baseline list, or nil when the baseline is unreadable (D4).
  static func applying(
    to baseline: ProxyExceptions
  ) -> (owned: OwnedExceptions, target: ProxyExceptionsTarget)? {
    guard let entries = baseline.entries else {
      return nil
    }
    let appended = ProxyExceptions.missingLoopbackEntries(in: entries)
    let applied = entries + appended
    let owned = OwnedExceptions(
      baselinePresent: baseline != .absent,
      baselineDigest: baseline == .absent ? nil : ProxyExceptions.digest(entries),
      appliedDigest: ProxyExceptions.digest(applied),
      appended: appended
    )
    return (owned, appended.isEmpty ? .untouched : .set(applied))
  }

  var hasValidBounds: Bool {
    let fixed = ProxyExceptions.loopbackEntries
    let orderedSubsequence =
      appended.allSatisfy(fixed.contains)
      && appended == fixed.filter(appended.contains)
    let baselineShape =
      baselinePresent
      ? baselineDigest?.count == 32
        && (!appended.isEmpty || baselineDigest == appliedDigest)
      : baselineDigest == nil && appended == fixed
    return appliedDigest.count == 32 && orderedSubsequence && baselineShape
  }

  /// Whether `exceptions` is exactly the list Posato applied.
  func isApplied(_ exceptions: ProxyExceptions) -> Bool {
    guard case .list(let entries) = exceptions else {
      return false
    }
    return ProxyExceptions.digest(entries) == appliedDigest
  }

  /// Whether `exceptions` is exactly the baseline presence and list.
  func isBaseline(_ exceptions: ProxyExceptions) -> Bool {
    switch exceptions {
    case .absent:
      return !baselinePresent
    case .list(let entries):
      return baselinePresent && ProxyExceptions.digest(entries) == baselineDigest
    case .unreadable:
      return false
    }
  }

  /// The write that returns an applied list to its baseline, or nil when the list without the appended suffix does
  /// not match the baseline digest.
  func restoreTarget(from exceptions: ProxyExceptions) -> ProxyExceptionsTarget? {
    guard isApplied(exceptions), case .list(let entries) = exceptions,
      entries.count >= appended.count
    else {
      return nil
    }
    guard baselinePresent else {
      return .remove
    }
    let baseline = Array(entries.dropLast(appended.count))
    guard ProxyExceptions.digest(baseline) == baselineDigest else {
      return nil
    }
    return appended.isEmpty ? .untouched : .set(baseline)
  }
}

public struct OwnershipRecord: Codable, Equatable, Sendable {
  public static let schemaVersion = 2
  static let legacySchemaVersion = 1

  public let schema: Int
  public let sessionIdentifier: Data
  public let requestIdentifier: Data
  public let canonicalInputDigest: Data
  public let serviceIdentifier: String
  public var phase: OwnershipPhase
  public let baselineHTTP: ProxyTuple
  public let baselineHTTPS: ProxyTuple
  public let appliedHTTP: ProxyTuple
  public let appliedHTTPS: ProxyTuple
  /// Nil in a version 1 record, which owns no exceptions change; every rewrite keeps that meaning.
  public let exceptions: OwnedExceptions?

  public init(
    sessionIdentifier: Data,
    requestIdentifier: Data,
    canonicalInputDigest: Data,
    serviceIdentifier: String,
    phase: OwnershipPhase,
    baselineHTTP: ProxyTuple,
    baselineHTTPS: ProxyTuple,
    appliedHTTP: ProxyTuple,
    appliedHTTPS: ProxyTuple,
    exceptions: OwnedExceptions?
  ) {
    schema = exceptions == nil ? Self.legacySchemaVersion : Self.schemaVersion
    self.sessionIdentifier = sessionIdentifier
    self.requestIdentifier = requestIdentifier
    self.canonicalInputDigest = canonicalInputDigest
    self.serviceIdentifier = serviceIdentifier
    self.phase = phase
    self.baselineHTTP = baselineHTTP
    self.baselineHTTPS = baselineHTTPS
    self.appliedHTTP = appliedHTTP
    self.appliedHTTPS = appliedHTTPS
    self.exceptions = exceptions
  }

  public var hasValidBounds: Bool {
    let schemaMatches =
      schema == (exceptions == nil ? Self.legacySchemaVersion : Self.schemaVersion)
    return schemaMatches
      && exceptions?.hasValidBounds ?? true
      && sessionIdentifier.count == WireLimits.identifierBytes
      && sessionIdentifier.contains { $0 != 0 }
      && requestIdentifier.count == WireLimits.identifierBytes
      && requestIdentifier.contains { $0 != 0 }
      && canonicalInputDigest.count == 32
      && !serviceIdentifier.isEmpty
      && serviceIdentifier.utf8.count <= 256
      && !serviceIdentifier.utf8.contains(0)
      && phase != .idle
      && baselineHTTP.hasValidBaselineSemantics
      && baselineHTTPS.hasValidBaselineSemantics
      && appliedHTTP.hasValidAppliedSemantics
      && appliedHTTPS == appliedHTTP
  }
}

public protocol OwnershipPersistence: Sendable {
  func load() throws -> OwnershipRecord?
  func save(_ record: OwnershipRecord) throws
  func remove() throws
}

public protocol ProxyConfigurationAccess: Sendable {
  func currentPrimaryServiceIdentifier() throws -> String
  func snapshot(serviceIdentifier: String) throws -> ProxySnapshot
  /// True only when the service is confirmed gone: no service entry or set link in the
  /// preferences, read under their exclusive lock, and no proxy entity left for it in the dynamic
  /// store. A service that exists but cannot be read is never absent.
  func serviceIsConfirmedAbsent(serviceIdentifier: String) throws -> Bool
  func replaceTuples(
    expected: ProxySnapshot,
    http: ProxyTuple,
    https: ProxyTuple,
    exceptions: ProxyExceptionsTarget,
    requirePrimaryService: Bool
  ) throws -> ProxySnapshot
}

public enum ProxyOwnershipFailure: Error, Equatable {
  case conflict
  case invalidInput
  case recoveryRequired
  case unavailable
}
