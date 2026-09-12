import CloudKit
import Foundation

struct CloudStore: Sendable {
  var backend: any CloudBackend

  func fetchZone(timeout: TimeInterval) -> ZoneFetchNative {
    switch backend.fetchZone(timeout: timeout) {
    case .found:
      return .found
    case .missing:
      return .missing
    case .failed(let fault):
      return mapFault(fault, retryable: ZoneFetchNative.retryable, unknown: .unknownOutcome)
    }
  }

  func saveZone(timeout: TimeInterval) -> ZoneSaveNative {
    switch backend.fetchZone(timeout: timeout) {
    case .found:
      return .alreadyExists
    case .failed(let fault):
      return mapFault(fault, retryable: ZoneSaveNative.retryable, unknown: .unknownOutcome)
    case .missing:
      break
    }
    if backend.saveZone(timeout: timeout) != nil {
      return reconcileZoneSave(timeout: timeout)
    }
    return confirmZoneSave(timeout: timeout)
  }

  func readAnchor(timeout: TimeInterval) -> AnchorReadNative {
    switch backend.fetchRecord(name: CloudNames.anchorName, timeout: timeout) {
    case .found(let record):
      guard let fields = RecordCodec.validateAnchor(record) else {
        return .integrityFailure
      }
      return .found(fields)
    case .missing:
      return .missing
    case .zoneMissing:
      return .unknownOutcome
    case .failed(let fault):
      return mapFault(fault, retryable: AnchorReadNative.retryable, unknown: .unknownOutcome)
    }
  }

  func createAnchor(fields: Data, timeout: TimeInterval) -> AnchorCreateNative {
    guard fields.count == SyncLimits.anchorBytes else {
      return .integrityFailure
    }
    switch backend.fetchRecord(name: CloudNames.anchorName, timeout: timeout) {
    case .found(let record):
      guard RecordCodec.validateAnchor(record) != nil else {
        return .integrityFailure
      }
      return .conflict
    case .failed(let fault):
      return mapFault(fault, retryable: AnchorCreateNative.retryable, unknown: .unknownOutcome)
    case .missing:
      break
    case .zoneMissing:
      return .unknownOutcome
    }
    switch backend.saveRecord(makeAnchor(fields: fields), timeout: timeout) {
    case .saved:
      return .created
    case .conflicted:
      return .conflict
    case .failed(let fault):
      return mapFault(fault, retryable: AnchorCreateNative.retryable, unknown: .unknownOutcome)
    }
  }

  func saveBundle(identifier: Data, payload: Data, timeout: TimeInterval) -> BundleSaveNative {
    guard identifier.count == SyncLimits.bundleIdentifierBytes,
      !payload.isEmpty,
      payload.count <= SyncLimits.bundleBytes,
      let recordName = RecordCodec.uuidText(from: identifier)
    else {
      return .integrityFailure
    }
    switch compareBundle(name: recordName, payload: payload, timeout: timeout) {
    case .found(let identical):
      return identical ? .identical : .conflict
    case .absent:
      break
    case .unresolved(let native):
      return native
    }
    switch backend.saveRecord(makeBundle(name: recordName, payload: payload), timeout: timeout) {
    case .saved:
      return .created
    case .conflicted:
      return reconcileBundleSave(name: recordName, payload: payload, timeout: timeout)
    case .failed(let fault):
      return mapFault(fault, retryable: BundleSaveNative.retryable, unknown: .unknownOutcome)
    }
  }

  func fetchChanges(cursor: Data, timeout: TimeInterval) -> ChangeFetchNative {
    let tokenData: Data?
    if cursor.isEmpty {
      tokenData = nil
    } else if RecordCodec.unarchiveToken(cursor) != nil {
      tokenData = cursor
    } else {
      return .integrityFailure
    }
    let changes: BackendChanges
    switch backend.fetchChanges(tokenData: tokenData, timeout: timeout) {
    case .tokenExpired:
      return .tokenExpired
    case .zoneMissing:
      return .zoneMissing
    case .failed(let fault):
      return mapFault(fault, retryable: ChangeFetchNative.retryable, unknown: .unknownOutcome)
    case .fetched(let fetched):
      changes = fetched
    }
    guard let page = collectPage(changes.changed) else {
      return .integrityFailure
    }
    guard let archived = changes.token, !archived.isEmpty,
      archived.count <= SyncLimits.cursorBytes
    else {
      return .unknownOutcome
    }
    return .page(
      ChangePageNative(
        bundleIdentifier: page.0,
        bundle: page.1,
        moreComing: changes.moreComing,
        cursor: archived
      )
    )
  }

  func deleteWorkspaceRecords(timeout: TimeInterval) -> RecordDeleteNative {
    return RecordDeletion(backend: backend).deleteWorkspaceRecords(timeout: timeout)
  }

  func sweepBundlesIfAnchorMissing(timeout: TimeInterval) -> BundleSweepNative {
    return RecordDeletion(backend: backend).sweepBundlesIfAnchorMissing(timeout: timeout)
  }

  private enum BundleComparison {
    case found(Bool)
    case absent
    case unresolved(BundleSaveNative)
  }

  private func compareBundle(
    name: String,
    payload: Data,
    timeout: TimeInterval
  ) -> BundleComparison {
    switch backend.fetchRecord(name: name, timeout: timeout) {
    case .found(let record):
      guard let validated = RecordCodec.validateBundle(record) else {
        return .unresolved(.integrityFailure)
      }
      return .found(validated.1 == payload)
    case .missing:
      return .absent
    case .zoneMissing:
      return .unresolved(.unknownOutcome)
    case .failed(let fault):
      switch fault {
      case .retryable:
        return .unresolved(.retryable)
      case .unknown:
        return .unresolved(.unknownOutcome)
      }
    }
  }

  private func reconcileBundleSave(
    name: String,
    payload: Data,
    timeout: TimeInterval
  ) -> BundleSaveNative {
    switch compareBundle(name: name, payload: payload, timeout: timeout) {
    case .found(let identical):
      return identical ? .identical : .conflict
    case .absent, .unresolved:
      return .unknownOutcome
    }
  }

  private func makeAnchor(fields: Data) -> RawRecord {
    var outgoing = RawRecord(
      name: CloudNames.anchorName,
      type: CloudNames.anchorType,
      fields: [:],
      allKeys: CloudNames.anchorFields
    )
    for (index, field) in CloudNames.anchorFields.enumerated() {
      let start = index * SyncLimits.identifierBytes
      outgoing.fields[field] = fields.subdata(in: start..<(start + SyncLimits.identifierBytes))
    }
    return outgoing
  }

  private func makeBundle(name: String, payload: Data) -> RawRecord {
    return RawRecord(
      name: name,
      type: CloudNames.bundleType,
      fields: [CloudNames.bundlePayloadField: payload],
      allKeys: [CloudNames.bundlePayloadField]
    )
  }

  private func collectPage(_ changed: [RawRecord]) -> (Data?, Data?)? {
    var identifier: Data?
    var bundle: Data?
    for record in changed {
      if record.type == CloudNames.anchorType {
        continue
      }
      guard identifier == nil,
        let validated = RecordCodec.validateBundle(record)
      else {
        return nil
      }
      identifier = validated.0
      bundle = validated.1
    }
    return (identifier, bundle)
  }

  private func confirmZoneSave(timeout: TimeInterval) -> ZoneSaveNative {
    switch backend.fetchZone(timeout: timeout) {
    case .found:
      return .created
    case .missing, .failed:
      return .unknownOutcome
    }
  }

  private func reconcileZoneSave(timeout: TimeInterval) -> ZoneSaveNative {
    switch backend.fetchZone(timeout: timeout) {
    case .found:
      return .alreadyExists
    case .missing, .failed:
      return .unknownOutcome
    }
  }

  private func mapFault<Result>(
    _ fault: BackendFault,
    retryable: Result,
    unknown: Result
  ) -> Result {
    switch fault {
    case .retryable:
      return retryable
    case .unknown:
      return unknown
    }
  }
}
