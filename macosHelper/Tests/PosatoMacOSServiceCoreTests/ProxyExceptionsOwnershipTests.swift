import Foundation
import Testing

@testable import PosatoMacOSServiceCore

// MACOS-024 (ADR 0004 amendment): the exceptions list is a third owned group. A VM run covers one or two list
// shapes; these cases guard a person's list against silent rewriting, duplication, or loss.

@Test func givenOneLoopbackEntryWhenAppliedAndRestoredThenOnlyMissingOnesComeAndGo() throws {
  let baseline = ["*.local", "127.0.0.1", "169.254/16"]
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration(exceptions: .list(baseline))
  let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)

  #expect(try applyExceptionsSession(engine) == .applied)
  #expect(
    configuration.snapshotValue.exceptions
      == .list(["*.local", "127.0.0.1", "169.254/16", "localhost", "::1"])
  )

  #expect(try engine.restore() == .idle)
  #expect(configuration.snapshotValue.exceptions == .list(baseline))
  #expect(persistence.record == nil)
}

@Test func givenNoExceptionsKeyWhenAppliedAndRestoredThenTheKeyIsRemovedAgain() throws {
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration(exceptions: .absent)
  let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)

  _ = try applyExceptionsSession(engine)
  #expect(configuration.snapshotValue.exceptions == .list(ProxyExceptions.loopbackEntries))

  #expect(try engine.restore() == .idle)
  #expect(configuration.snapshotValue.exceptions == .absent)
}

@Test func givenDifferentlyCasedEntryWhenAppliedThenTheExactEntryIsStillAppended() throws {
  let configuration = MemoryProxyConfiguration(exceptions: .list(["LOCALHOST"]))
  let engine = ProxyOwnershipEngine(
    persistence: MemoryOwnershipPersistence(), configuration: configuration)

  _ = try applyExceptionsSession(engine)

  #expect(
    configuration.snapshotValue.exceptions == .list(["LOCALHOST", "localhost", "127.0.0.1", "::1"]))
}

@Test
func givenAllLoopbackEntriesPresentWhenListChangesThenEnforcementEnds() throws {
  let baseline = ["localhost", "127.0.0.1", "::1"]
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration(exceptions: .list(baseline))
  let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)
  _ = try applyExceptionsSession(engine)
  #expect(configuration.snapshotValue.exceptions == .list(baseline))
  let bypass = baseline + ["paused.example"]
  configuration.snapshotValue = snapshot(configuration.snapshotValue, exceptions: .list(bypass))

  #expect(try engine.maintain() == .recoveryRequired)

  #expect(configuration.snapshotValue.http == emptyTuple)
  #expect(configuration.snapshotValue.https == emptyTuple)
  #expect(configuration.snapshotValue.exceptions == .list(bypass))
  #expect(persistence.record?.phase == .recoveryRequired)
}

@Test func givenAppliedListChangedWhenMaintainedThenTuplesRestoreAndChangeIsKept() throws {
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration(exceptions: .list(["*.local"]))
  let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)
  _ = try applyExceptionsSession(engine)
  let changed = ProxyExceptions.list(["*.local", "localhost", "127.0.0.1", "::1", "paused.example"])
  configuration.snapshotValue = snapshot(configuration.snapshotValue, exceptions: changed)

  #expect(try engine.maintain() == .recoveryRequired)

  #expect(configuration.snapshotValue.http == emptyTuple)
  #expect(configuration.snapshotValue.exceptions == changed)
}

@Test func givenOutOfBoundsBaselineWhenAppliedThenNothingIsRecordedOrWritten() throws {
  let unreadable = SystemProxyConfiguration.exceptions(
    Array(repeating: "a.example", count: ProxyExceptions.maximumEntries + 1)
  )
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration(exceptions: unreadable)
  let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)

  #expect(throws: ProxyOwnershipFailure.unavailable) { try applyExceptionsSession(engine) }

  #expect(persistence.record == nil)
  #expect(configuration.snapshotValue.http == emptyTuple)
  #expect(configuration.snapshotValue.exceptions == unreadable)
}

@Test func givenVersionOneRecordWhenRestoredThenTuplesReturnAndExceptionsStayUntouched() throws {
  let applied = ProxyTuple(enabled: .integer(1), host: .string("127.0.0.1"), port: .integer(17_769))
  let current = ProxyExceptions.list(["*.local", "localhost"])
  let persistence = MemoryOwnershipPersistence()
  persistence.record = legacyRecord(phase: .applied, applied: applied)
  let configuration = MemoryProxyConfiguration(http: applied, https: applied, exceptions: current)
  let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)

  #expect(try engine.maintain() == .applied)
  #expect(try engine.restore() == .idle)

  #expect(configuration.snapshotValue.http == emptyTuple)
  #expect(configuration.snapshotValue.exceptions == current)
  #expect(persistence.record == nil)
}

@Test func givenVersionOneRecordWhenRewrittenThenItStaysVersionOne() throws {
  let applied = ProxyTuple(enabled: .integer(1), host: .string("127.0.0.1"), port: .integer(17_769))
  let persistence = MemoryOwnershipPersistence()
  persistence.record = legacyRecord(phase: .applied, applied: applied)
  persistence.failAfterSavingPhase = .restorePending
  let configuration = MemoryProxyConfiguration(
    http: applied, https: applied, exceptions: .list(["x.example"]))
  let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)

  #expect(throws: ProxyOwnershipFailure.unavailable) { try engine.restore() }

  #expect(persistence.record?.schema == 1)
  #expect(persistence.record?.exceptions == nil)
  #expect(persistence.record?.hasValidBounds == true)
}

@Test func givenRestoreCommittedBeforeRecordRemovalWhenReconciledThenIdle() throws {
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration(exceptions: .list(["*.local"]))
  let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)
  _ = try applyExceptionsSession(engine)
  let applied = try #require(persistence.record)
  _ = try engine.restore()
  var pending = applied
  pending.phase = .restorePending
  persistence.record = pending

  #expect(try engine.reconcile() == .idle)

  #expect(persistence.record == nil)
  #expect(configuration.snapshotValue.exceptions == .list(["*.local"]))
}

@Test func givenUnreadableListWhenRestoredThenTuplesReturnAndStateNeedsRecovery() throws {
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration(exceptions: .list(["*.local"]))
  let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)
  _ = try applyExceptionsSession(engine)
  let unreadable = SystemProxyConfiguration.exceptions(["*.local", 7])
  configuration.snapshotValue = snapshot(configuration.snapshotValue, exceptions: unreadable)

  #expect(try engine.restore() == .recoveryRequired)

  #expect(configuration.snapshotValue.http == emptyTuple)
  #expect(configuration.snapshotValue.https == emptyTuple)
  #expect(configuration.snapshotValue.exceptions == unreadable)
  #expect(persistence.record?.phase == .recoveryRequired)
}

@Test func givenCanonicallyEquivalentButDifferentBytesWhenRestoredThenItIsAConflict() throws {
  let composed = "caf\u{E9}.example"
  let decomposed = "cafe\u{301}.example"
  let persistence = MemoryOwnershipPersistence()
  let configuration = MemoryProxyConfiguration(exceptions: .list([composed]))
  let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)
  _ = try applyExceptionsSession(engine)
  let swapped = ProxyExceptions.list([decomposed] + ProxyExceptions.loopbackEntries)
  configuration.snapshotValue = snapshot(configuration.snapshotValue, exceptions: swapped)

  #expect(try engine.restore() == .recoveryRequired)

  #expect(configuration.snapshotValue.exceptions == swapped)
}

@Test func givenBaselinePrefixDigestMismatchWhenRestoredThenNothingIsRewritten() throws {
  let applied = ProxyTuple(enabled: .integer(1), host: .string("127.0.0.1"), port: .integer(17_769))
  let current = ["other.example"] + ProxyExceptions.loopbackEntries
  let persistence = MemoryOwnershipPersistence()
  persistence.record = OwnershipRecord(
    sessionIdentifier: sessionIdentifier,
    requestIdentifier: requestIdentifier,
    canonicalInputDigest: canonicalDigest,
    serviceIdentifier: "synthetic-service",
    phase: .applied,
    baselineHTTP: emptyTuple,
    baselineHTTPS: emptyTuple,
    appliedHTTP: applied,
    appliedHTTPS: applied,
    exceptions: OwnedExceptions(
      baselinePresent: true,
      baselineDigest: ProxyExceptions.digest(["*.local"]),
      appliedDigest: ProxyExceptions.digest(current),
      appended: ProxyExceptions.loopbackEntries
    )
  )
  let configuration = MemoryProxyConfiguration(
    http: applied, https: applied, exceptions: .list(current))
  let engine = ProxyOwnershipEngine(persistence: persistence, configuration: configuration)

  #expect(try engine.restore() == .recoveryRequired)

  #expect(configuration.snapshotValue.exceptions == .list(current))
}

private let sessionIdentifier = Data(repeating: 1, count: 16)
private let requestIdentifier = Data(repeating: 2, count: 16)
private let canonicalDigest = Data(repeating: 3, count: 32)

private func applyExceptionsSession(_ engine: ProxyOwnershipEngine) throws -> OwnershipPhase {
  return try engine.apply(
    sessionIdentifier: sessionIdentifier,
    requestIdentifier: requestIdentifier,
    canonicalInputDigest: canonicalDigest,
    port: 17_769
  )
}

private func snapshot(_ value: ProxySnapshot, exceptions: ProxyExceptions) -> ProxySnapshot {
  return ProxySnapshot(
    serviceIdentifier: value.serviceIdentifier,
    http: value.http,
    https: value.https,
    additionalProxyEnabled: value.additionalProxyEnabled,
    exceptions: exceptions
  )
}

private func legacyRecord(phase: OwnershipPhase, applied: ProxyTuple) -> OwnershipRecord {
  return OwnershipRecord(
    sessionIdentifier: sessionIdentifier,
    requestIdentifier: requestIdentifier,
    canonicalInputDigest: canonicalDigest,
    serviceIdentifier: "synthetic-service",
    phase: phase,
    baselineHTTP: emptyTuple,
    baselineHTTPS: emptyTuple,
    appliedHTTP: applied,
    appliedHTTPS: applied,
    exceptions: nil
  )
}
