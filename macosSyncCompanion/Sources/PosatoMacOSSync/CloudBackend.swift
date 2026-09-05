import CloudKit
import Foundation

enum BackendFault: Equatable, Sendable {
  case retryable
  case unknown
}

enum ZoneLookup: Equatable, Sendable {
  case found
  case missing
  case failed(BackendFault)
}

enum BackendLookup: Equatable, Sendable {
  case found(RawRecord)
  case missing
  case zoneMissing
  case failed(BackendFault)
}

enum BackendSave: Equatable, Sendable {
  case saved
  case conflicted
  case failed(BackendFault)
}

struct BackendChanges: Equatable, Sendable {
  var changed: [RawRecord]
  var deletedNames: [String]
  var token: Data?
  var moreComing: Bool
}

enum BackendChangesResult: Equatable, Sendable {
  case fetched(BackendChanges)
  case zoneMissing
  case failed(BackendFault)
}

protocol CloudBackend: Sendable {
  func fetchZone(timeout: TimeInterval) -> ZoneLookup
  func saveZone(timeout: TimeInterval) -> BackendFault?
  func fetchRecord(name: String, timeout: TimeInterval) -> BackendLookup
  func saveRecord(_ record: RawRecord, timeout: TimeInterval) -> BackendSave
  func fetchChanges(token: CKServerChangeToken?, timeout: TimeInterval) -> BackendChangesResult
  func deleteZone(timeout: TimeInterval) -> BackendFault?
}

final class ChangesCollector: @unchecked Sendable {
  private let changed = LockedBox<[RawRecord]>([])
  private let deleted = LockedBox<[String]>([])
  private let latestToken = LockedBox<CKServerChangeToken?>(nil)
  private let moreComing = LockedBox<Bool>(false)
  private let failure = LockedBox<NSError?>(nil)

  func recordChanged(_ result: Result<CKRecord, Error>) {
    switch result {
    case .success(let record):
      changed.mutate { $0.append(RawRecord.from(record)) }
    case .failure(let error):
      if failure.get() == nil {
        failure.set(error as NSError)
      }
    }
  }

  func recordDeleted(_ recordName: String) {
    deleted.mutate { $0.append(recordName) }
  }

  func tokenUpdated(_ token: CKServerChangeToken?) {
    if let token {
      latestToken.set(token)
    }
  }

  func zoneToken(_ token: CKServerChangeToken, moreComing more: Bool) {
    latestToken.set(token)
    moreComing.set(more)
  }

  func zoneFailure(_ error: Error) {
    failure.set(error as NSError)
  }

  func operationFailure(_ error: Error) {
    if failure.get() == nil {
      failure.set(error as NSError)
    }
  }

  func drain() -> BackendChangesResult {
    if let error = failure.get() {
      if CloudErrorMapper.isZoneAbsent(error) {
        return .zoneMissing
      }
      return CloudErrorMapper.isRetryable(error) ? .failed(.retryable) : .failed(.unknown)
    }
    guard let newToken = latestToken.get(), let archived = RecordCodec.archiveToken(newToken)
    else {
      return .failed(.unknown)
    }
    return .fetched(
      BackendChanges(
        changed: changed.get(),
        deletedNames: deleted.get(),
        token: archived,
        moreComing: moreComing.get()
      )
    )
  }
}

final class LockedBox<Value>: @unchecked Sendable {
  private let lock = NSLock()
  private var value: Value

  init(_ value: Value) {
    self.value = value
  }

  func set(_ value: Value) {
    lock.lock()
    defer { lock.unlock() }
    self.value = value
  }

  func get() -> Value {
    lock.lock()
    defer { lock.unlock() }
    return value
  }

  func mutate(_ transform: (inout Value) -> Void) {
    lock.lock()
    defer { lock.unlock() }
    transform(&value)
  }
}

struct CKCloudDatabase: CloudBackend, @unchecked Sendable {
  var database: CKDatabase
  var zoneID: CKRecordZone.ID

  init(
    database: CKDatabase? = nil,
    zoneID: CKRecordZone.ID = RecordCodec.zoneID()
  ) {
    if let database {
      self.database = database
    } else {
      self.database = CKContainer(identifier: SyncLimits.containerIdentifier).privateCloudDatabase
    }
    self.zoneID = zoneID
  }

  func fetchZone(timeout: TimeInterval) -> ZoneLookup {
    guard timeout > 0 else {
      return .failed(.unknown)
    }
    let box = LockedBox<ZoneLookup?>(nil)
    let done = DispatchSemaphore(value: 0)
    let operation = CKFetchRecordZonesOperation(recordZoneIDs: [zoneID])
    operation.perRecordZoneResultBlock = { _, result in
      switch result {
      case .success:
        box.set(.found)
      case .failure(let error):
        box.set(CloudErrorMapper.zoneLookup(from: error as NSError))
      }
    }
    operation.fetchRecordZonesResultBlock = { result in
      if case .failure(let error) = result, box.get() == nil {
        box.set(CloudErrorMapper.zoneLookup(from: error as NSError))
      }
      done.signal()
    }
    database.add(operation)
    if done.wait(timeout: .now() + timeout) == .timedOut {
      operation.cancel()
      return .failed(.unknown)
    }
    return box.get() ?? .failed(.unknown)
  }

  func saveZone(timeout: TimeInterval) -> BackendFault? {
    guard timeout > 0 else {
      return .unknown
    }
    let box = LockedBox<BackendFault?>(.unknown)
    let done = DispatchSemaphore(value: 0)
    let operation = CKModifyRecordZonesOperation(
      recordZonesToSave: [CKRecordZone(zoneID: zoneID)],
      recordZoneIDsToDelete: nil
    )
    operation.modifyRecordZonesResultBlock = { result in
      switch result {
      case .success:
        box.set(nil)
      case .failure(let error):
        let mapped = error as NSError
        box.set(CloudErrorMapper.isRetryable(mapped) ? .retryable : .unknown)
      }
      done.signal()
    }
    database.add(operation)
    if done.wait(timeout: .now() + timeout) == .timedOut {
      operation.cancel()
      return .unknown
    }
    return box.get()
  }

  func fetchRecord(name: String, timeout: TimeInterval) -> BackendLookup {
    guard timeout > 0 else {
      return .failed(.unknown)
    }
    let box = LockedBox<BackendLookup?>(nil)
    let done = DispatchSemaphore(value: 0)
    let operation = CKFetchRecordsOperation(
      recordIDs: [CKRecord.ID(recordName: name, zoneID: zoneID)]
    )
    operation.perRecordResultBlock = { _, result in
      switch result {
      case .success(let record):
        box.set(.found(RawRecord.from(record)))
      case .failure(let error):
        box.set(CloudErrorMapper.recordLookup(from: error as NSError))
      }
    }
    operation.fetchRecordsResultBlock = { result in
      if case .failure(let error) = result, box.get() == nil {
        box.set(CloudErrorMapper.recordLookup(from: error as NSError))
      }
      done.signal()
    }
    database.add(operation)
    if done.wait(timeout: .now() + timeout) == .timedOut {
      operation.cancel()
      return .failed(.unknown)
    }
    return box.get() ?? .failed(.unknown)
  }

  func saveRecord(_ record: RawRecord, timeout: TimeInterval) -> BackendSave {
    guard timeout > 0, let outgoing = record.makeRecord(zoneID: zoneID) else {
      return .failed(.unknown)
    }
    let box = LockedBox<BackendSave>(.failed(.unknown))
    let done = DispatchSemaphore(value: 0)
    let operation = CKModifyRecordsOperation(recordsToSave: [outgoing], recordIDsToDelete: nil)
    operation.savePolicy = .ifServerRecordUnchanged
    operation.modifyRecordsResultBlock = { result in
      switch result {
      case .success:
        box.set(.saved)
      case .failure(let error):
        let mapped = error as NSError
        if CloudErrorMapper.isServerRecordChanged(mapped) {
          box.set(.conflicted)
        } else if CloudErrorMapper.isRetryable(mapped) {
          box.set(.failed(.retryable))
        } else {
          box.set(.failed(.unknown))
        }
      }
      done.signal()
    }
    database.add(operation)
    if done.wait(timeout: .now() + timeout) == .timedOut {
      operation.cancel()
      return .failed(.unknown)
    }
    return box.get()
  }

  func fetchChanges(token: CKServerChangeToken?, timeout: TimeInterval) -> BackendChangesResult {
    guard timeout > 0 else {
      return .failed(.unknown)
    }
    let collector = ChangesCollector()
    let done = DispatchSemaphore(value: 0)
    let operation = makeChangesOperation(token: token, collector: collector, done: done)
    database.add(operation)
    if done.wait(timeout: .now() + timeout) == .timedOut {
      operation.cancel()
      return .failed(.unknown)
    }
    return collector.drain()
  }

  private func makeChangesOperation(
    token: CKServerChangeToken?,
    collector: ChangesCollector,
    done: DispatchSemaphore
  ) -> CKFetchRecordZoneChangesOperation {
    let configuration = CKFetchRecordZoneChangesOperation.ZoneConfiguration()
    configuration.previousServerChangeToken = token
    configuration.resultsLimit = 1
    let operation = CKFetchRecordZoneChangesOperation(
      recordZoneIDs: [zoneID],
      configurationsByRecordZoneID: [zoneID: configuration]
    )
    operation.recordWasChangedBlock = { _, result in
      collector.recordChanged(result)
    }
    operation.recordWithIDWasDeletedBlock = { recordID, _ in
      collector.recordDeleted(recordID.recordName)
    }
    operation.recordZoneChangeTokensUpdatedBlock = { _, updated, _ in
      collector.tokenUpdated(updated)
    }
    operation.recordZoneFetchResultBlock = { _, result in
      switch result {
      case .success(let (serverToken, _, more)):
        collector.zoneToken(serverToken, moreComing: more)
      case .failure(let error):
        collector.zoneFailure(error)
      }
    }
    operation.fetchRecordZoneChangesResultBlock = { result in
      if case .failure(let error) = result {
        collector.operationFailure(error)
      }
      done.signal()
    }
    return operation
  }

  func deleteZone(timeout: TimeInterval) -> BackendFault? {
    guard timeout > 0 else {
      return .unknown
    }
    let box = LockedBox<BackendFault?>(.unknown)
    let done = DispatchSemaphore(value: 0)
    let operation = CKModifyRecordZonesOperation(
      recordZonesToSave: nil,
      recordZoneIDsToDelete: [zoneID]
    )
    operation.modifyRecordZonesResultBlock = { result in
      switch result {
      case .success:
        box.set(nil)
      case .failure(let error):
        let mapped = error as NSError
        box.set(CloudErrorMapper.isRetryable(mapped) ? .retryable : .unknown)
      }
      done.signal()
    }
    database.add(operation)
    if done.wait(timeout: .now() + timeout) == .timedOut {
      operation.cancel()
      return .unknown
    }
    return box.get()
  }
}
