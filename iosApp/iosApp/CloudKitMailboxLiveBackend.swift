import CloudKit
import Foundation

private final class InflightOperation: @unchecked Sendable {
    private let lock = NSLock()
    private var current: CKDatabaseOperation?

    func set(_ operation: CKDatabaseOperation?) {
        lock.lock()
        defer { lock.unlock() }
        current = operation
    }

    func cancel() {
        lock.lock()
        let operation = current
        lock.unlock()
        operation?.cancel()
    }
}

private final class MailboxBox<Value>: @unchecked Sendable {
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
}

private final class MailboxChangesCollector: @unchecked Sendable {
    private let lock = NSLock()
    private var changed: [CKRecord] = []
    private var deleted: [String] = []
    private var latestToken: CKServerChangeToken?
    private var moreComing = false
    private var failure: NSError?

    func recordChanged(_ result: Result<CKRecord, Error>) {
        lock.lock()
        defer { lock.unlock() }
        switch result {
        case .success(let record):
            changed.append(record)
        case .failure(let error):
            if failure == nil {
                failure = error as NSError
            }
        }
    }

    func recordDeleted(_ recordName: String) {
        lock.lock()
        defer { lock.unlock() }
        deleted.append(recordName)
    }

    func zoneToken(_ token: CKServerChangeToken, moreComing more: Bool) {
        lock.lock()
        defer { lock.unlock() }
        latestToken = token
        moreComing = more
    }

    func zoneFailure(_ error: Error) {
        lock.lock()
        defer { lock.unlock() }
        failure = error as NSError
    }

    func operationFailure(_ error: Error) {
        lock.lock()
        defer { lock.unlock() }
        if failure == nil {
            failure = error as NSError
        }
    }

    func drain() -> MailboxChangesResult {
        lock.lock()
        defer { lock.unlock() }
        if let error = failure {
            if MailboxErrorMapper.isZoneAbsent(error) {
                return .zoneMissing
            }
            return .failed(error)
        }
        guard let token = latestToken,
            let archived = MailboxRecordCodec.archiveToken(token)
        else {
            return .failed(NSError(domain: CKError.errorDomain, code: CKError.internalError.rawValue))
        }
        return .fetched(
            MailboxChanges(
                changed: changed,
                deletedNames: deleted,
                tokenData: archived,
                moreComing: moreComing
            )
        )
    }
}

/// Live `CloudKitMailboxBackend` over the account's private database. Each
/// call runs one `CKOperation` under the 30-second budget; a timeout or an
/// explicit `cancelInflight` cancels the operation and reports an
/// unclassified failure, which the provider maps to `unknown-outcome`.
final class CloudKitMailboxLiveBackend: CloudKitMailboxBackend {
    private let database: CKDatabase
    private let inflight = InflightOperation()

    init(
        database: CKDatabase? = nil,
        containerIdentifier: String = MailboxCloudLimits.containerIdentifier
    ) {
        if let database {
            self.database = database
        } else {
            self.database = CKContainer(identifier: containerIdentifier).privateCloudDatabase
        }
    }

    func cancelInflight() {
        inflight.cancel()
    }

    func fetchZone(zoneID: CKRecordZone.ID, timeout: TimeInterval) -> MailboxZoneLookup {
        guard timeout > 0 else {
            return .failed(NSError(domain: CKError.errorDomain, code: CKError.internalError.rawValue))
        }
        let box = MailboxBox<MailboxZoneLookup?>(nil)
        let done = DispatchSemaphore(value: 0)
        let operation = CKFetchRecordZonesOperation(recordZoneIDs: [zoneID])
        operation.perRecordZoneResultBlock = { _, result in
            switch result {
            case .success:
                box.set(.found)
            case .failure(let error):
                box.set(self.mapZoneLookup(error as NSError))
            }
        }
        operation.fetchRecordZonesResultBlock = { result in
            if case .failure(let error) = result, box.get() == nil {
                box.set(self.mapZoneLookup(error as NSError))
            }
            done.signal()
        }
        return run(
            operation,
            timeout: timeout,
            done: done,
            onTimeout: .failed(Self.timeoutFault())
        ) {
            box.get() ?? .failed(NSError(domain: CKError.errorDomain, code: CKError.internalError.rawValue))
        }
    }

    func saveZone(zoneID: CKRecordZone.ID, timeout: TimeInterval) -> NSError? {
        guard timeout > 0 else {
            return NSError(domain: CKError.errorDomain, code: CKError.internalError.rawValue)
        }
        let box = MailboxBox<NSError?>(NSError(domain: CKError.errorDomain, code: CKError.internalError.rawValue))
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
                box.set(error as NSError)
            }
            done.signal()
        }
        return run(
            operation,
            timeout: timeout,
            done: done,
            onTimeout: Self.timeoutFault()
        ) {
            box.get()
        }
    }

    func fetchRecord(id: CKRecord.ID, timeout: TimeInterval) -> MailboxRecordLookup {
        guard timeout > 0 else {
            return .failed(NSError(domain: CKError.errorDomain, code: CKError.internalError.rawValue))
        }
        let box = MailboxBox<MailboxRecordLookup?>(nil)
        let done = DispatchSemaphore(value: 0)
        let operation = CKFetchRecordsOperation(recordIDs: [id])
        operation.perRecordResultBlock = { _, result in
            switch result {
            case .success(let record):
                box.set(.found(record))
            case .failure(let error):
                box.set(self.mapRecordLookup(error as NSError))
            }
        }
        operation.fetchRecordsResultBlock = { result in
            if case .failure(let error) = result, box.get() == nil {
                box.set(self.mapRecordLookup(error as NSError))
            }
            done.signal()
        }
        return run(
            operation,
            timeout: timeout,
            done: done,
            onTimeout: .failed(Self.timeoutFault())
        ) {
            box.get() ?? .failed(NSError(domain: CKError.errorDomain, code: CKError.internalError.rawValue))
        }
    }

    func saveRecordIfAbsent(_ record: CKRecord, timeout: TimeInterval) -> MailboxRecordSave {
        guard timeout > 0 else {
            return .failed(NSError(domain: CKError.errorDomain, code: CKError.internalError.rawValue))
        }
        let box = MailboxBox(MailboxRecordSave.failed(NSError(domain: CKError.errorDomain, code: CKError.internalError.rawValue)))
        let done = DispatchSemaphore(value: 0)
        let operation = CKModifyRecordsOperation(recordsToSave: [record], recordIDsToDelete: nil)
        operation.savePolicy = .ifServerRecordUnchanged
        operation.modifyRecordsResultBlock = { result in
            switch result {
            case .success:
                box.set(.saved)
            case .failure(let error):
                box.set(self.mapRecordSave(error as NSError))
            }
            done.signal()
        }
        return run(
            operation,
            timeout: timeout,
            done: done,
            onTimeout: .failed(Self.timeoutFault())
        ) {
            box.get()
        }
    }

    func fetchChanges(zoneID: CKRecordZone.ID, tokenData: Data?, timeout: TimeInterval) -> MailboxChangesResult {
        guard timeout > 0 else {
            return .failed(NSError(domain: CKError.errorDomain, code: CKError.internalError.rawValue))
        }
        let token: CKServerChangeToken?
        if let tokenData, !tokenData.isEmpty {
            guard let unarchived = MailboxRecordCodec.unarchiveToken(tokenData) else {
                return .invalidCursor
            }
            token = unarchived
        } else {
            token = nil
        }
        let collector = MailboxChangesCollector()
        let done = DispatchSemaphore(value: 0)
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
            if let updated {
                collector.zoneToken(updated, moreComing: false)
            }
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
        return run(
            operation,
            timeout: timeout,
            done: done,
            onTimeout: .failed(Self.timeoutFault())
        ) {
            collector.drain()
        }
    }

    func deleteZone(zoneID: CKRecordZone.ID, timeout: TimeInterval) -> NSError? {
        guard timeout > 0 else {
            return NSError(domain: CKError.errorDomain, code: CKError.internalError.rawValue)
        }
        let box = MailboxBox<NSError?>(NSError(domain: CKError.errorDomain, code: CKError.internalError.rawValue))
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
                box.set(error as NSError)
            }
            done.signal()
        }
        return run(
            operation,
            timeout: timeout,
            done: done,
            onTimeout: Self.timeoutFault()
        ) {
            box.get()
        }
    }

    /// A timeout cancels the operation and reports the explicit failure
    /// instead of reading partial callback state: progress callbacks may
    /// already have stored a token, which must never surface as a caught-up
    /// page for a fetch that never completed.
    private func run<T>(
        _ operation: CKDatabaseOperation,
        timeout: TimeInterval,
        done: DispatchSemaphore,
        onTimeout: T,
        produce: () -> T
    ) -> T {
        inflight.set(operation)
        defer { inflight.set(nil) }
        database.add(operation)
        if done.wait(timeout: .now() + timeout) == .timedOut {
            operation.cancel()
            return onTimeout
        }
        return produce()
    }

    private static func timeoutFault() -> NSError {
        return NSError(domain: CKError.errorDomain, code: CKError.internalError.rawValue)
    }

    private func mapZoneLookup(_ error: NSError) -> MailboxZoneLookup {
        if MailboxErrorMapper.isZoneAbsent(error) {
            return .missing
        }
        return .failed(error)
    }

    private func mapRecordLookup(_ error: NSError) -> MailboxRecordLookup {
        let unwrapped = MailboxErrorMapper.unwrapSinglePartial(error)
        if MailboxErrorMapper.isZoneAbsent(unwrapped) {
            return .zoneMissing
        }
        if MailboxErrorMapper.isUnknownItem(unwrapped) {
            return .missing
        }
        return .failed(error)
    }

    private func mapRecordSave(_ error: NSError) -> MailboxRecordSave {
        if MailboxErrorMapper.isServerRecordChanged(error) {
            return .conflicted
        }
        return .failed(error)
    }
}
