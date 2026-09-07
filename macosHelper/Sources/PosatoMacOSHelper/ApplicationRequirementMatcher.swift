import Foundation
import Security

/// A process incarnation: pid plus start time, so pid recycling cannot hide
/// a target behind a stale negative entry.
struct ProcessIncarnation: Hashable {
  let processIdentifier: pid_t
  let startTime: UInt64
}

/// Bounded negative cache for requirement validation. Only misses are stored;
/// a new incarnation re-validates. Callers must validate one fixed
/// requirement set per cache lifetime; the session guarantees it.
struct ProcessMissCache {
  private let capacity: Int
  private var misses: Set<ProcessIncarnation> = []

  init(capacity: Int = 2_048) {
    self.capacity = capacity
  }

  func isKnownMiss(_ incarnation: ProcessIncarnation) -> Bool {
    return misses.contains(incarnation)
  }

  mutating func recordMiss(_ incarnation: ProcessIncarnation) {
    if misses.count >= capacity {
      misses.removeAll()
    }
    misses.insert(incarnation)
  }
}

final class SecurityApplicationMatcher: ApplicationRequirementMatching {
  private var parsedRequirements: [Data: SecRequirement] = [:]
  private var missCache: ProcessMissCache
  private let processStartTime: (pid_t) -> UInt64?

  /// The session validates one fixed requirement set on one queue, which the
  /// negative cache assumes.
  init(
    missCache: ProcessMissCache = ProcessMissCache(),
    processStartTime: @escaping (pid_t) -> UInt64? = SecurityApplicationMatcher.liveStartTime
  ) {
    self.missCache = missCache
    self.processStartTime = processStartTime
  }

  func matchingRequirement(
    processIdentifier: pid_t,
    requirements: Set<Data>
  ) -> Data? {
    guard processIdentifier > 1 else {
      return nil
    }
    let incarnation = processStartTime(processIdentifier).map {
      ProcessIncarnation(processIdentifier: processIdentifier, startTime: $0)
    }
    if let incarnation, missCache.isKnownMiss(incarnation) {
      return nil
    }
    let attributes = [kSecGuestAttributePid as String: processIdentifier] as CFDictionary
    var code: SecCode?
    guard SecCodeCopyGuestWithAttributes(nil, attributes, [], &code) == errSecSuccess,
      let code
    else {
      return nil
    }
    for requirementData in requirements {
      if let parsed = parsedRequirements[requirementData] {
        if SecCodeCheckValidity(code, [], parsed) == errSecSuccess {
          return requirementData
        }
        continue
      }
      var created: SecRequirement?
      guard
        SecRequirementCreateWithData(requirementData as CFData, [], &created) == errSecSuccess,
        let created
      else {
        continue
      }
      parsedRequirements[requirementData] = created
      if SecCodeCheckValidity(code, [], created) == errSecSuccess {
        return requirementData
      }
    }
    if let incarnation {
      missCache.recordMiss(incarnation)
    }
    return nil
  }

  static func liveStartTime(processIdentifier: pid_t) -> UInt64? {
    var info = proc_bsdinfo()
    let bytes = proc_pidinfo(
      processIdentifier,
      PROC_PIDTBSDINFO,
      0,
      &info,
      Int32(MemoryLayout<proc_bsdinfo>.size)
    )
    guard bytes == MemoryLayout<proc_bsdinfo>.size else {
      return nil
    }
    return info.pbi_start_tvsec * 1_000_000 + info.pbi_start_tvusec
  }
}
