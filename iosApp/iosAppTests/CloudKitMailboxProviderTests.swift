import CloudKit
import Foundation
import PosatoShared
import XCTest

@testable import Posato

final class FakeMailboxAccountSource: KeychainAccountSource {
    var resolutions: [KeychainAccountResolution]
    private(set) var calls = 0

    init(resolutions: [KeychainAccountResolution] = [.undetermined]) {
        self.resolutions = resolutions
    }

    func currentRecordName() -> KeychainAccountResolution {
        calls += 1
        if resolutions.count > 1 {
            return resolutions.removeFirst()
        }
        return resolutions[0]
    }
}

final class FakeMailboxBackend: CloudKitMailboxBackend {
    private(set) var calls: [String] = []
    private(set) var cancelled = false
    var gate: DispatchSemaphore?
    var entrySignal: DispatchSemaphore?

    var zoneHandler: (CKRecordZone.ID) -> MailboxZoneLookup = { _ in .missing }
    var saveZoneHandler: (CKRecordZone.ID) -> NSError? = { _ in nil }
    var recordHandler: (CKRecord.ID) -> MailboxRecordLookup = { _ in .missing }
    var saveRecordHandler: (CKRecord) -> MailboxRecordSave = { _ in .saved }
    var changesHandler: (CKRecordZone.ID, Data?) -> MailboxChangesResult = { _, _ in
        .failed(NSError(domain: CKError.errorDomain, code: CKError.internalError.rawValue))
    }
    var deleteRecordsHandler: ([CKRecord.ID]) -> NSError? = { _ in nil }
    private(set) var deletedIDs: [[CKRecord.ID]] = []

    func fetchZone(zoneID: CKRecordZone.ID, timeout: TimeInterval) -> MailboxZoneLookup {
        calls.append("fetchZone")
        waitGate()
        return zoneHandler(zoneID)
    }

    func saveZone(zoneID: CKRecordZone.ID, timeout: TimeInterval) -> NSError? {
        calls.append("saveZone")
        waitGate()
        return saveZoneHandler(zoneID)
    }

    func fetchRecord(id: CKRecord.ID, timeout: TimeInterval) -> MailboxRecordLookup {
        calls.append("fetchRecord")
        waitGate()
        return recordHandler(id)
    }

    func saveRecordIfAbsent(_ record: CKRecord, timeout: TimeInterval) -> MailboxRecordSave {
        calls.append("saveRecord")
        waitGate()
        return saveRecordHandler(record)
    }

    func fetchChanges(zoneID: CKRecordZone.ID, tokenData: Data?, timeout: TimeInterval) -> MailboxChangesResult {
        calls.append("fetchChanges")
        waitGate()
        return changesHandler(zoneID, tokenData)
    }

    func deleteRecords(ids: [CKRecord.ID], timeout: TimeInterval) -> NSError? {
        calls.append("deleteRecords")
        waitGate()
        deletedIDs.append(ids)
        return deleteRecordsHandler(ids)
    }

    func cancelInflight() {
        cancelled = true
        gate?.signal()
    }

    private func waitGate() {
        entrySignal?.signal()
        if let gate {
            _ = gate.wait(timeout: .now() + 30)
        }
    }
}

final class CloudKitMailboxProviderTests: XCTestCase {
    private let recordName = "_sync007testrecord"
    private let zoneID = MailboxRecordCodec.zoneID()
    private let identifier = Data([0x12, 0x3E, 0x45, 0x67, 0xE8, 0x9B, 0x42, 0xD3, 0xA4, 0x56, 0x42, 0x66, 0x14, 0x17, 0x40, 0x00])

    private func binding(recordName: String? = nil) -> Data {
        SynchronizableKeychainProvider.deriveBinding(recordName: recordName ?? self.recordName)
    }

    private func makeProvider(
        source: FakeMailboxAccountSource? = nil,
        backend: FakeMailboxBackend? = nil
    ) -> (CloudKitMailboxProvider, FakeMailboxAccountSource, FakeMailboxBackend) {
        let accountSource = source ?? FakeMailboxAccountSource(resolutions: [.available(recordName)])
        let cloud = backend ?? FakeMailboxBackend()
        let provider = CloudKitMailboxProvider(accountSource: accountSource, backend: cloud)
        return (provider, accountSource, cloud)
    }

    private func anchorFields() -> Data {
        var fields = Data()
        fields.append(Data(repeating: 0x11, count: 16))
        fields.append(Data(repeating: 0x22, count: 16))
        fields.append(Data(repeating: 0x33, count: 16))
        return fields
    }

    private func anchorRecord(fields: Data? = nil, name: String = "workspace", type: String = "PosatoWorkspaceV1", keys: [String]? = nil) -> CKRecord {
        let record = CKRecord(recordType: type, recordID: CKRecord.ID(recordName: name, zoneID: zoneID))
        let values = fields ?? anchorFields()
        let names = keys ?? ["workspaceId", "transportEpochId", "keyEpochId"]
        for (index, key) in names.enumerated() {
            record[key] = values.subdata(in: (index * 16)..<((index + 1) * 16)) 
        }
        return record
    }

    private func bundleRecord(
        identifier: Data? = nil,
        payload: Data = Data([0x09]),
        name: String? = nil,
        type: String = "PosatoEncryptedBundleV1"
    ) -> CKRecord {
        let identifier = identifier ?? self.identifier
        let record = CKRecord(
            recordType: type,
            recordID: CKRecord.ID(recordName: name ?? MailboxRecordCodec.uuidText(from: identifier)!, zoneID: zoneID)
        )
        record["payload"] = payload 
        return record
    }

    private func ckError(_ code: CKError.Code) -> NSError {
        return NSError(domain: CKError.errorDomain, code: code.rawValue)
    }

    private func partialFailure(_ items: [(String, CKError.Code)]) -> NSError {
        var partials = [AnyHashable: Any]()
        for (name, code) in items {
            let id = CKRecord.ID(recordName: name, zoneID: zoneID)
            partials[id] = ckError(code)
        }
        return NSError(
            domain: CKError.errorDomain,
            code: CKError.partialFailure.rawValue,
            userInfo: [CKPartialErrorsByItemIDKey: partials]
        )
    }

    // MARK: - Error table (mirrors the companion CloudErrorMapper)

    func testRetryableCodesMatchCompanionTable() {
        for code: CKError.Code in [.networkFailure, .networkUnavailable, .serviceUnavailable, .requestRateLimited, .quotaExceeded, .zoneBusy] {
            XCTAssertTrue(MailboxErrorMapper.isRetryable(ckError(code)), "expected retryable: \(code)")
            XCTAssertFalse(MailboxErrorMapper.isZoneAbsent(ckError(code)))
            XCTAssertFalse(MailboxErrorMapper.isServerRecordChanged(ckError(code)))
        }
    }

    func testZoneAbsentCodesMatchCompanionTable() {
        for code: CKError.Code in [.zoneNotFound, .userDeletedZone] {
            XCTAssertTrue(MailboxErrorMapper.isZoneAbsent(ckError(code)), "expected zone absent: \(code)")
            XCTAssertFalse(MailboxErrorMapper.isRetryable(ckError(code)))
        }
    }

    func testServerRecordChangedStandsApart() {
        XCTAssertTrue(MailboxErrorMapper.isServerRecordChanged(ckError(.serverRecordChanged)))
        XCTAssertFalse(MailboxErrorMapper.isRetryable(ckError(.serverRecordChanged)))
        XCTAssertFalse(MailboxErrorMapper.isZoneAbsent(ckError(.serverRecordChanged)))
    }

    func testUnknownItemStandsApart() {
        XCTAssertTrue(MailboxErrorMapper.isUnknownItem(ckError(.unknownItem)))
        XCTAssertFalse(MailboxErrorMapper.isRetryable(ckError(.unknownItem)))
    }

    func testRecordDeleteToleratesUnknownItemOnlyPartial() {
        XCTAssertNil(MailboxErrorMapper.recordDelete(from: partialFailure([("gone", .unknownItem)])))
    }

    func testRecordDeleteToleratesDirectUnknownItem() {
        XCTAssertNil(MailboxErrorMapper.recordDelete(from: ckError(.unknownItem)))
    }

    func testRecordDeleteToleratesDirectZoneAbsent() {
        XCTAssertNil(MailboxErrorMapper.recordDelete(from: ckError(.zoneNotFound)))
    }

    func testRecordDeleteReportsRealErrorAlongsideUnknownItem() {
        // The third batch member succeeded and is simply absent from the
        // partial-failure dictionary; the surviving unknown item is
        // tolerated while the real error is reported whole.
        let error = partialFailure([("gone", .unknownItem), ("broken", .internalError)])
        XCTAssertEqual(MailboxErrorMapper.recordDelete(from: error), error)
    }

    func testRecordDeleteReportsSingleRealPartialEntry() {
        let error = partialFailure([("broken", .internalError)])
        XCTAssertEqual(MailboxErrorMapper.recordDelete(from: error), error)
    }

    func testRecordDeletePreservesRetryableAlongsideUnknownItem() {
        let error = partialFailure([("gone", .unknownItem), ("slow", .networkFailure)])
        XCTAssertEqual(MailboxErrorMapper.recordDelete(from: error), error)
    }

    func testDeleteOperationIsNonAtomic() {
        let id = CKRecord.ID(recordName: "workspace", zoneID: zoneID)
        let operation = CloudKitMailboxLiveBackend.makeDeleteOperation(ids: [id])
        XCTAssertFalse(operation.isAtomic)
        XCTAssertEqual(operation.recordIDsToDelete, [id])
    }

    func testRemainingCodesMapToUnknown() {
        for code: CKError.Code in [.changeTokenExpired, .notAuthenticated, .permissionFailure, .unknownItem, .serverRecordChanged, .internalError, .alreadyShared, .missingEntitlement] {
            if code == .unknownItem || code == .serverRecordChanged {
                continue
            }
            let error = ckError(code)
            XCTAssertFalse(MailboxErrorMapper.isRetryable(error), "expected unknown: \(code)")
            XCTAssertFalse(MailboxErrorMapper.isZoneAbsent(error), "expected unknown: \(code)")
        }
        let foreign = NSError(domain: "other", code: 1)
        XCTAssertFalse(MailboxErrorMapper.isRetryable(foreign))
        XCTAssertFalse(MailboxErrorMapper.isZoneAbsent(foreign))
        XCTAssertFalse(MailboxErrorMapper.isServerRecordChanged(foreign))
        XCTAssertFalse(MailboxErrorMapper.isUnknownItem(foreign))
    }

    func testSingleEntryPartialFailureUnwraps() {
        let wrapped = NSError(
            domain: CKError.errorDomain,
            code: CKError.partialFailure.rawValue,
            userInfo: [CKPartialErrorsByItemIDKey: ["item": ckError(.networkFailure)]]
        )
        XCTAssertTrue(MailboxErrorMapper.isRetryable(wrapped))
        let wrappedZone = NSError(
            domain: CKError.errorDomain,
            code: CKError.partialFailure.rawValue,
            userInfo: [CKPartialErrorsByItemIDKey: ["item": ckError(.zoneNotFound)]]
        )
        XCTAssertTrue(MailboxErrorMapper.isZoneAbsent(wrappedZone))
        let wrappedChange = NSError(
            domain: CKError.errorDomain,
            code: CKError.partialFailure.rawValue,
            userInfo: [CKPartialErrorsByItemIDKey: ["item": ckError(.serverRecordChanged)]]
        )
        XCTAssertTrue(MailboxErrorMapper.isServerRecordChanged(wrappedChange))
    }

    func testMultiEntryPartialFailureDoesNotUnwrap() {
        let wrapped = NSError(
            domain: CKError.errorDomain,
            code: CKError.partialFailure.rawValue,
            userInfo: [CKPartialErrorsByItemIDKey: ["a": ckError(.networkFailure), "b": ckError(.networkFailure)]]
        )
        XCTAssertFalse(MailboxErrorMapper.isRetryable(wrapped))
        XCTAssertFalse(MailboxErrorMapper.isZoneAbsent(wrapped))
    }

    // MARK: - Codec

    func testUuidTextMatchesGoldenVector() {
        XCTAssertEqual(MailboxRecordCodec.uuidText(from: identifier), "123e4567-e89b-42d3-a456-426614174000")
        XCTAssertEqual(MailboxRecordCodec.identifierBytes(from: "123e4567-e89b-42d3-a456-426614174000"), identifier)
        XCTAssertNil(MailboxRecordCodec.uuidText(from: Data(repeating: 0x01, count: 15)))
        XCTAssertFalse(MailboxRecordCodec.isCanonicalUUID("123E4567-E89B-42D3-A456-426614174000"))
        XCTAssertFalse(MailboxRecordCodec.isCanonicalUUID("123e4567e89b42d3a456426614174000"))
        XCTAssertFalse(MailboxRecordCodec.isCanonicalUUID(""))
    }

    func testAnchorValidationAcceptsOnlyExactShape() {
        let zoneID = self.zoneID
        XCTAssertEqual(MailboxRecordCodec.validateAnchor(anchorRecord(), zoneID: zoneID), anchorFields())
        XCTAssertNil(MailboxRecordCodec.validateAnchor(anchorRecord(name: "other"), zoneID: zoneID))
        XCTAssertNil(MailboxRecordCodec.validateAnchor(anchorRecord(type: "Other"), zoneID: zoneID))
        XCTAssertNil(MailboxRecordCodec.validateAnchor(anchorRecord(keys: ["workspaceId", "transportEpochId"]), zoneID: zoneID))
        let short = anchorRecord()
        short["workspaceId"] = Data(repeating: 0x11, count: 15) 
        XCTAssertNil(MailboxRecordCodec.validateAnchor(short, zoneID: zoneID))
        let foreignZone = CKRecordZone.ID(zoneName: "Other", ownerName: CKCurrentUserDefaultName)
        let foreign = CKRecord(
            recordType: "PosatoWorkspaceV1",
            recordID: CKRecord.ID(recordName: "workspace", zoneID: foreignZone)
        )
        for (index, key) in ["workspaceId", "transportEpochId", "keyEpochId"].enumerated() {
            foreign[key] = anchorFields().subdata(in: (index * 16)..<((index + 1) * 16)) 
        }
        XCTAssertNil(MailboxRecordCodec.validateAnchor(foreign, zoneID: zoneID))
    }

    func testBundleValidationAcceptsOnlyExactShape() {
        let zoneID = self.zoneID
        let validated = MailboxRecordCodec.validateBundle(bundleRecord(), zoneID: zoneID)
        XCTAssertEqual(validated?.0, identifier)
        XCTAssertEqual(validated?.1, Data([0x09]))
        XCTAssertNil(MailboxRecordCodec.validateBundle(bundleRecord(name: "not-a-uuid"), zoneID: zoneID))
        XCTAssertNil(MailboxRecordCodec.validateBundle(bundleRecord(type: "Other"), zoneID: zoneID))
        XCTAssertNil(MailboxRecordCodec.validateBundle(bundleRecord(payload: Data()), zoneID: zoneID))
        XCTAssertNil(MailboxRecordCodec.validateBundle(bundleRecord(payload: Data(repeating: 0x01, count: 65_537)), zoneID: zoneID))
        let extra = bundleRecord()
        extra["other"] = Data([0x01]) 
        XCTAssertNil(MailboxRecordCodec.validateBundle(extra, zoneID: zoneID))
    }

    // MARK: - Zone

    func testZoneFetchMapsOutcomes() {
        for (lookup, expected): (MailboxZoneLookup, IosCloudZoneFetchStatus) in [
            (.found, .found),
            (.missing, .missing),
            (.failed(ckError(.networkFailure)), .retryable),
            (.failed(ckError(.permissionFailure)), .unknownoutcome),
        ] {
            let backend = FakeMailboxBackend()
            backend.zoneHandler = { _ in lookup }
            let (provider, _, _) = makeProvider(backend: backend)
            XCTAssertEqual(provider.fetchZone(binding: binding() ), expected)
        }
    }

    func testZoneSaveReconcilesThroughExactFetch() {
        let backend = FakeMailboxBackend()
        backend.zoneHandler = { _ in .found }
        let (existing, _, _) = makeProvider(backend: backend)
        XCTAssertEqual(existing.saveZone(binding: binding() ), .alreadyexists)
        XCTAssertEqual(backend.calls.filter { $0 == "saveZone" }.count, 0)

        var saves = 0
        let reconciling = FakeMailboxBackend()
        reconciling.zoneHandler = { _ in saves == 0 ? .missing : .found }
        reconciling.saveZoneHandler = { _ in saves += 1; return NSError(domain: CKError.errorDomain, code: CKError.networkFailure.rawValue) }
        let (lost, _, _) = makeProvider(backend: reconciling)
        XCTAssertEqual(lost.saveZone(binding: binding()), .alreadyexists)

        for (lookup, expected): (MailboxZoneLookup, IosCloudZoneSaveStatus) in [
            (.failed(ckError(.networkFailure)), .retryable),
            (.failed(ckError(.permissionFailure)), .unknownoutcome),
        ] {
            let failing = FakeMailboxBackend()
            failing.zoneHandler = { _ in lookup }
            let (refused, _, _) = makeProvider(backend: failing)
            XCTAssertEqual(refused.saveZone(binding: binding()), expected)
            XCTAssertEqual(failing.calls.filter { $0 == "saveZone" }.count, 0)
        }

        let confirming = FakeMailboxBackend()
        confirming.zoneHandler = { _ in .missing }
        confirming.saveZoneHandler = { _ in nil }
        let (created, _, _) = makeProvider(backend: confirming)
        XCTAssertEqual(created.saveZone(binding: binding() ), .unknownoutcome)
    }

    // MARK: - Anchor

    func testAnchorReadMapsOutcomes() {
        let fields = anchorFields()
        let backend = FakeMailboxBackend()
        backend.recordHandler = { _ in .found(self.anchorRecord()) }
        let (provider, _, _) = makeProvider(backend: backend)
        let read = provider.readAnchor(binding: binding() )
        XCTAssertEqual(read.status, .found)
        XCTAssertEqual(read.fields as Data?, fields)

        let missing = FakeMailboxBackend()
        missing.recordHandler = { _ in .missing }
        let (absent, _, _) = makeProvider(backend: missing)
        XCTAssertEqual(absent.readAnchor(binding: binding() ).status, .missing)

        let broken = FakeMailboxBackend()
        broken.recordHandler = { _ in .found(self.anchorRecord(name: "other")) }
        let (invalid, _, _) = makeProvider(backend: broken)
        let rejected = invalid.readAnchor(binding: binding() )
        XCTAssertEqual(rejected.status, .integrityfailure)
        XCTAssertNil(rejected.fields)
    }

    func testAnchorCreateConflictsOnExistingWhetherIdenticalOrDifferent() {
        let fields = anchorFields()
        let backend = FakeMailboxBackend()
        backend.recordHandler = { _ in .found(self.anchorRecord()) }
        let (provider, _, _) = makeProvider(backend: backend)
        XCTAssertEqual(provider.createAnchor(binding: binding() , fields: fields ), .conflict)
        XCTAssertEqual(backend.calls.filter { $0 == "saveRecord" }.count, 0)

        var other = fields
        other[0] ^= 0x01
        let differing = FakeMailboxBackend()
        differing.recordHandler = { _ in .found(self.anchorRecord(fields: other)) }
        let (second, _, _) = makeProvider(backend: differing)
        XCTAssertEqual(second.createAnchor(binding: binding() , fields: fields ), .conflict)
    }

    func testAnchorCreateGatesFieldsBeforeAnyCall() {
        let (provider, _, backend) = makeProvider()
        XCTAssertEqual(
            provider.createAnchor(binding: binding() , fields: Data(repeating: 0x01, count: 47) ),
            .integrityfailure
        )
        XCTAssertTrue(backend.calls.isEmpty)
    }

    // MARK: - Bundle

    func testBundleSaveGatesSizesBeforeAnyCall() {
        let (provider, _, backend) = makeProvider()
        XCTAssertEqual(
            provider.saveBundle(binding: binding() , identifier: Data(repeating: 0x01, count: 15) , payload: Data([0x01]) ),
            .integrityfailure
        )
        XCTAssertEqual(
            provider.saveBundle(binding: binding() , identifier: identifier , payload: Data() ),
            .integrityfailure
        )
        XCTAssertEqual(
            provider.saveBundle(binding: binding() , identifier: identifier , payload: Data(repeating: 0x01, count: 65_537) ),
            .integrityfailure
        )
        XCTAssertTrue(backend.calls.isEmpty)
    }

    func testBundleSaveComparesBeforeSaving() {
        let payload = Data([0x01, 0x02])
        let backend = FakeMailboxBackend()
        backend.recordHandler = { _ in .found(self.bundleRecord(identifier: self.identifier, payload: payload)) }
        let (provider, _, _) = makeProvider(backend: backend)
        XCTAssertEqual(
            provider.saveBundle(binding: binding() , identifier: identifier , payload: payload ),
            .identical
        )
        XCTAssertEqual(backend.calls.filter { $0 == "saveRecord" }.count, 0)

        var other = payload
        other.append(0x03)
        let differing = FakeMailboxBackend()
        differing.recordHandler = { _ in .found(self.bundleRecord(identifier: self.identifier, payload: other)) }
        let (conflicting, _, _) = makeProvider(backend: differing)
        XCTAssertEqual(
            conflicting.saveBundle(binding: binding() , identifier: identifier , payload: payload ),
            .conflict
        )
    }

    func testBundleSaveReconcilesLostSave() {
        let payload = Data([0x01])
        let backend = FakeMailboxBackend()
        var saves = 0
        backend.recordHandler = { _ in saves == 0 ? .missing : .found(self.bundleRecord(identifier: self.identifier, payload: payload)) }
        backend.saveRecordHandler = { _ in saves += 1; return .conflicted }
        let (provider, _, _) = makeProvider(backend: backend)
        XCTAssertEqual(
            provider.saveBundle(binding: binding() , identifier: identifier , payload: payload ),
            .identical
        )
    }

    // MARK: - Changes

    func testFetchChangesGatesCursorBeforeAnyCall() {
        let (provider, _, backend) = makeProvider()
        let page = provider.fetchChanges(binding: binding() , cursor: Data(repeating: 0x01, count: 16_385) )
        XCTAssertEqual(page.status, .integrityfailure)
        XCTAssertNil(page.nextCursor)
        XCTAssertTrue(backend.calls.isEmpty)
    }

    func testFetchChangesExposesValidatedPage() {
        let payload = Data([0x07, 0x08])
        let backend = FakeMailboxBackend()
        backend.changesHandler = { _, token in
            XCTAssertNil(token)
            return .fetched(
                MailboxChanges(
                    changed: [self.anchorRecord(), self.bundleRecord(identifier: self.identifier, payload: payload)],
                    deletedNames: [],
                    tokenData: Data([0xAA]),
                    moreComing: true
                )
            )
        }
        let (provider, _, _) = makeProvider(backend: backend)
        let page = provider.fetchChanges(binding: binding() , cursor: Data() )
        XCTAssertEqual(page.status, .page)
        XCTAssertTrue(page.moreChanges)
        XCTAssertEqual(page.nextCursor as Data?, Data([0xAA]))
        XCTAssertEqual(page.bundleIdentifier as Data?, identifier)
        XCTAssertEqual(page.bundlePayload as Data?, payload)
    }

    func testFetchChangesRejectsSecondBundleAndAdvancesPastDeletions() {
        let crowded = FakeMailboxBackend()
        crowded.changesHandler = { _, _ in
            .fetched(
                MailboxChanges(
                    changed: [
                        self.bundleRecord(payload: Data([0x01])),
                        self.bundleRecord(identifier: Data(repeating: 0x02, count: 16), payload: Data([0x02])),
                    ],
                    deletedNames: [],
                    tokenData: Data([0xAA]),
                    moreComing: false
                )
            )
        }
        let (first, _, _) = makeProvider(backend: crowded)
        XCTAssertEqual(first.fetchChanges(binding: binding() , cursor: Data() ).status, .integrityfailure)

        let deleting = FakeMailboxBackend()
        deleting.changesHandler = { _, _ in
            .fetched(MailboxChanges(changed: [], deletedNames: ["gone"], tokenData: Data([0xAA]), moreComing: false))
        }
        let (second, _, _) = makeProvider(backend: deleting)
        let deletion = second.fetchChanges(binding: binding() , cursor: Data() )
        XCTAssertEqual(deletion.status, .page)
        XCTAssertFalse(deletion.moreChanges)
        XCTAssertEqual(deletion.nextCursor as Data?, Data([0xAA]))
        XCTAssertNil(deletion.bundleIdentifier)
        XCTAssertNil(deletion.bundlePayload)

        let tokenless = FakeMailboxBackend()
        tokenless.changesHandler = { _, _ in
            .fetched(MailboxChanges(changed: [], deletedNames: [], tokenData: nil, moreComing: false))
        }
        let (third, _, _) = makeProvider(backend: tokenless)
        let page = third.fetchChanges(binding: binding() , cursor: Data() )
        XCTAssertEqual(page.status, .unknownoutcome)
        XCTAssertNil(page.nextCursor)
    }

    // MARK: - Delete

    func testExpiredTokenReturnsDistinctOutcomeWithoutPageData() {
        let backend = FakeMailboxBackend()
        backend.changesHandler = { _, _ in .failed(self.ckError(.changeTokenExpired)) }
        let (provider, _, _) = makeProvider(backend: backend)
        let page = provider.fetchChanges(binding: binding(), cursor: Data())
        XCTAssertEqual(page.status, .tokenexpired)
        XCTAssertNil(page.nextCursor)
        XCTAssertNil(page.bundlePayload)
    }

    func testDeleteWorkspaceRecordsDeletesBundlesBeforeAnchor() {
        let backend = FakeMailboxBackend()
        let first = bundleRecord(payload: Data([0x01]))
        let secondRecord = bundleRecord(
            identifier: Data(repeating: 0x02, count: 16),
            payload: Data([0x02])
        )
        var fetches = 0
        backend.changesHandler = { _, _ in
            fetches += 1
            if fetches == 1 {
                return .fetched(
                    MailboxChanges(
                        changed: [first, secondRecord],
                        deletedNames: [],
                        tokenData: Data([0xAA]),
                        moreComing: false
                    )
                )
            }
            return .fetched(
                MailboxChanges(changed: [], deletedNames: [], tokenData: Data([0xAB]), moreComing: false)
            )
        }
        backend.recordHandler = { _ in .missing }
        let (provider, _, _) = makeProvider(backend: backend)
        XCTAssertEqual(provider.deleteWorkspaceRecords(binding: binding() ), .deletedandabsent)
        XCTAssertEqual(backend.deletedIDs.count, 2)
        XCTAssertEqual(backend.deletedIDs[0].map(\.recordName), [first.recordID.recordName, secondRecord.recordID.recordName])
        XCTAssertEqual(backend.deletedIDs[1].map(\.recordName), ["workspace"])
    }

    func testDeleteWorkspaceRecordsSkipsForeignRecordsWithoutValidation() {
        let backend = FakeMailboxBackend()
        let mystery = bundleRecord(name: "mystery", type: "MysteryType")
        let bundle = bundleRecord(payload: Data([0x01]))
        var fetches = 0
        backend.changesHandler = { _, _ in
            fetches += 1
            if fetches == 1 {
                return .fetched(
                    MailboxChanges(
                        changed: [mystery, bundle],
                        deletedNames: [],
                        tokenData: Data([0xAA]),
                        moreComing: false
                    )
                )
            }
            return .fetched(
                MailboxChanges(changed: [mystery], deletedNames: [], tokenData: Data([0xAB]), moreComing: false)
            )
        }
        backend.recordHandler = { _ in .missing }
        let (provider, _, _) = makeProvider(backend: backend)
        XCTAssertEqual(provider.deleteWorkspaceRecords(binding: binding() ), .deletedandabsent)
        XCTAssertEqual(backend.deletedIDs[0].map(\.recordName), [bundle.recordID.recordName])
    }

    func testDeleteWorkspaceRecordsReportsRemainingBundle() {
        let backend = FakeMailboxBackend()
        backend.changesHandler = { _, _ in
            .fetched(
                MailboxChanges(
                    changed: [self.bundleRecord(payload: Data([0x01]))],
                    deletedNames: [],
                    tokenData: Data([0xAA]),
                    moreComing: false
                )
            )
        }
        backend.recordHandler = { _ in .missing }
        let (provider, _, _) = makeProvider(backend: backend)
        XCTAssertEqual(provider.deleteWorkspaceRecords(binding: binding() ), .unknownoutcome)
    }

    func testDeleteWorkspaceRecordsReportsPresentAnchor() {
        let backend = FakeMailboxBackend()
        backend.changesHandler = { _, _ in
            .fetched(MailboxChanges(changed: [], deletedNames: [], tokenData: Data([0xAA]), moreComing: false))
        }
        backend.recordHandler = { _ in .found(self.anchorRecord()) }
        let (provider, _, _) = makeProvider(backend: backend)
        XCTAssertEqual(provider.deleteWorkspaceRecords(binding: binding() ), .unknownoutcome)
    }

    func testSweepDeletesEnumeratedBundlesWhenAnchorIsMissing() {
        let backend = FakeMailboxBackend()
        let bundle = bundleRecord(payload: Data([0x01]))
        backend.changesHandler = { _, _ in
            .fetched(
                MailboxChanges(
                    changed: [bundle],
                    deletedNames: [],
                    tokenData: Data([0xAA]),
                    moreComing: false
                )
            )
        }
        backend.recordHandler = { _ in .missing }
        let (provider, _, _) = makeProvider(backend: backend)
        XCTAssertEqual(provider.sweepBundlesIfAnchorMissing(binding: binding() ), .swept)
        XCTAssertEqual(backend.deletedIDs.count, 1)
        XCTAssertEqual(backend.deletedIDs[0].map(\.recordName), [bundle.recordID.recordName])
    }

    func testSweepDeletesNothingWhenAnchorAppears() {
        let backend = FakeMailboxBackend()
        backend.changesHandler = { _, _ in
            .fetched(
                MailboxChanges(
                    changed: [self.bundleRecord(payload: Data([0x01]))],
                    deletedNames: [],
                    tokenData: Data([0xAA]),
                    moreComing: false
                )
            )
        }
        backend.recordHandler = { _ in .found(self.anchorRecord()) }
        let (provider, _, _) = makeProvider(backend: backend)
        XCTAssertEqual(provider.sweepBundlesIfAnchorMissing(binding: binding() ), .anchorpresent)
        XCTAssertTrue(backend.deletedIDs.isEmpty)
    }

    func testDeleteProceedsWhileOwnAnchorPresent() {
        // Full removal runs on READY, so the own anchor is present during
        // the drain: bundle sets must still be deleted, and only the anchor
        // delete plus the emptiness re-scan decide the outcome.
        let backend = FakeMailboxBackend()
        let bundle = bundleRecord(payload: Data([0x01]))
        var fetches = 0
        backend.changesHandler = { _, _ in
            fetches += 1
            if fetches == 1 {
                return .fetched(
                    MailboxChanges(
                        changed: [bundle],
                        deletedNames: [],
                        tokenData: Data([0xA1]),
                        moreComing: false
                    )
                )
            }
            return .fetched(
                MailboxChanges(changed: [], deletedNames: [], tokenData: Data([0xA2]), moreComing: false)
            )
        }
        var anchorDeleted = false
        backend.deleteRecordsHandler = { ids in
            if ids.map(\.recordName) == ["workspace"] {
                anchorDeleted = true
            }
            return nil
        }
        backend.recordHandler = { _ in anchorDeleted ? .missing : .found(self.anchorRecord()) }
        let (provider, _, _) = makeProvider(backend: backend)
        XCTAssertEqual(provider.deleteWorkspaceRecords(binding: binding() ), .deletedandabsent)
        XCTAssertEqual(backend.deletedIDs.count, 2)
        XCTAssertEqual(backend.deletedIDs[0].map(\.recordName), [bundle.recordID.recordName])
        XCTAssertEqual(backend.deletedIDs[1].map(\.recordName), ["workspace"])
    }

    func testDeleteAbortsWhenAnchorSurvivesAnchorDelete() {
        // An anchor that is still found after its delete means a concurrent
        // workspace won the race: the drain still removes every enumerated
        // set, but the outcome is unknown instead of deleted.
        let backend = FakeMailboxBackend()
        let first = bundleRecord(payload: Data([0x01]))
        let secondRecord = bundleRecord(
            identifier: Data(repeating: 0x02, count: 16),
            payload: Data([0x02])
        )
        var fetches = 0
        backend.changesHandler = { _, _ in
            fetches += 1
            if fetches == 1 {
                return .fetched(
                    MailboxChanges(
                        changed: [first],
                        deletedNames: [],
                        tokenData: Data([0xA1]),
                        moreComing: true
                    )
                )
            }
            return .fetched(
                MailboxChanges(
                    changed: [secondRecord],
                    deletedNames: [],
                    tokenData: Data([0xA2]),
                    moreComing: false
                )
            )
        }
        backend.recordHandler = { _ in .found(self.anchorRecord()) }
        let (provider, _, _) = makeProvider(backend: backend)
        XCTAssertEqual(provider.deleteWorkspaceRecords(binding: binding() ), .unknownoutcome)
        XCTAssertEqual(backend.deletedIDs.count, 3)
        XCTAssertEqual(backend.deletedIDs[0].map(\.recordName), [first.recordID.recordName])
        XCTAssertEqual(backend.deletedIDs[1].map(\.recordName), [secondRecord.recordID.recordName])
        XCTAssertEqual(backend.deletedIDs[2].map(\.recordName), ["workspace"])
    }

    func testSweepAbortsRemainingSetsWhenAnchorAppearsMidPass() {
        let backend = FakeMailboxBackend()
        let first = bundleRecord(payload: Data([0x01]))
        let secondRecord = bundleRecord(
            identifier: Data(repeating: 0x02, count: 16),
            payload: Data([0x02])
        )
        var fetches = 0
        backend.changesHandler = { _, _ in
            fetches += 1
            if fetches == 1 {
                return .fetched(
                    MailboxChanges(
                        changed: [first],
                        deletedNames: [],
                        tokenData: Data([0xA1]),
                        moreComing: true
                    )
                )
            }
            return .fetched(
                MailboxChanges(
                    changed: [secondRecord],
                    deletedNames: [],
                    tokenData: Data([0xA2]),
                    moreComing: false
                )
            )
        }
        var anchorReads = 0
        backend.recordHandler = { _ in
            anchorReads += 1
            if anchorReads == 1 {
                return .missing
            }
            return .found(self.anchorRecord())
        }
        let (provider, _, _) = makeProvider(backend: backend)
        XCTAssertEqual(provider.sweepBundlesIfAnchorMissing(binding: binding() ), .anchorpresent)
        XCTAssertEqual(backend.deletedIDs.count, 1)
        XCTAssertEqual(backend.deletedIDs[0].map(\.recordName), [first.recordID.recordName])
    }

    func testDeleteVerificationSpanningCallsDeletesAnchorOnce() {
        // Verification alone exceeds the ten-page bound: the second call
        // continues verifying from the carried phase instead of deleting
        // the anchor again, and completes.
        let backend = FakeMailboxBackend()
        let bundle = bundleRecord(payload: Data([0x01]))
        func page(names: [CKRecord] = [], token: UInt8, more: Bool) -> MailboxChanges {
            MailboxChanges(changed: names, deletedNames: [], tokenData: Data([token]), moreComing: more)
        }
        var pages: [Data: MailboxChanges] = [Data([1]): page(token: 2, more: true)]
        for index: UInt8 in 2...9 {
            pages[Data([index])] = page(token: index + 1, more: true)
        }
        pages[Data([10])] = page(token: 11, more: false)
        backend.changesHandler = { _, tokenData in
            guard let tokenData else {
                return .fetched(page(names: [bundle], token: 1, more: false))
            }
            return .fetched(pages[tokenData]!)
        }
        var anchorDeleted = false
        backend.deleteRecordsHandler = { ids in
            if ids.map(\.recordName) == ["workspace"] {
                anchorDeleted = true
            }
            return nil
        }
        backend.recordHandler = { _ in anchorDeleted ? .missing : .found(self.anchorRecord()) }
        let (provider, _, _) = makeProvider(backend: backend)
        let data = binding()
        XCTAssertEqual(provider.deleteWorkspaceRecords(binding: data), .retryable)
        XCTAssertEqual(provider.deleteWorkspaceRecords(binding: data), .deletedandabsent)
        XCTAssertEqual(backend.deletedIDs.count, 2)
        XCTAssertEqual(backend.deletedIDs[0].map(\.recordName), [bundle.recordID.recordName])
        XCTAssertEqual(backend.deletedIDs[1].map(\.recordName), ["workspace"])
    }

    func testDeleteResumedVerificationAbortsOnPresentAnchor() {
        // A workspace established after the anchor delete must abort the
        // resumed verification: no further record is touched.
        let backend = FakeMailboxBackend()
        let bundle = bundleRecord(payload: Data([0x01]))
        func page(names: [CKRecord] = [], token: UInt8, more: Bool) -> MailboxChanges {
            MailboxChanges(changed: names, deletedNames: [], tokenData: Data([token]), moreComing: more)
        }
        var pages: [Data: MailboxChanges] = [Data([1]): page(token: 2, more: true)]
        for index: UInt8 in 2...9 {
            pages[Data([index])] = page(token: index + 1, more: true)
        }
        pages[Data([10])] = page(token: 11, more: false)
        backend.changesHandler = { _, tokenData in
            guard let tokenData else {
                return .fetched(page(names: [bundle], token: 1, more: false))
            }
            return .fetched(pages[tokenData]!)
        }
        var anchorDeleted = false
        backend.deleteRecordsHandler = { ids in
            if ids.map(\.recordName) == ["workspace"] {
                anchorDeleted = true
            }
            return nil
        }
        backend.recordHandler = { _ in anchorDeleted ? .missing : .found(self.anchorRecord()) }
        let (provider, _, _) = makeProvider(backend: backend)
        let data = binding()
        XCTAssertEqual(provider.deleteWorkspaceRecords(binding: data), .retryable)
        // A concurrent fresh attempt publishes a new anchor before the
        // resumed verification runs.
        anchorDeleted = false
        let deletesBefore = backend.deletedIDs.count
        XCTAssertEqual(provider.deleteWorkspaceRecords(binding: data), .unknownoutcome)
        XCTAssertEqual(backend.deletedIDs.count, deletesBefore)
    }

    func testDeleteResumesAcrossCallsAfterPageBound() {
        let backend = FakeMailboxBackend()
        var fetchTokens: [Data?] = []
        var fetches = 0
        backend.changesHandler = { _, tokenData in
            fetchTokens.append(tokenData)
            fetches += 1
            if fetches <= 12 {
                return .fetched(
                    MailboxChanges(
                        changed: [],
                        deletedNames: ["tombstone-\(fetches)"],
                        tokenData: Data([UInt8(fetches)]),
                        moreComing: true
                    )
                )
            }
            return .fetched(
                MailboxChanges(changed: [], deletedNames: [], tokenData: Data([0xFF]), moreComing: false)
            )
        }
        backend.recordHandler = { _ in .missing }
        let (provider, _, _) = makeProvider(backend: backend)
        let data = binding()
        XCTAssertEqual(provider.deleteWorkspaceRecords(binding: data), .retryable)
        XCTAssertEqual(provider.deleteWorkspaceRecords(binding: data), .deletedandabsent)
        // Twelve deletion-only pages exceed the ten-page bound, so the first
        // call stops with work remaining and the second call resumes from
        // the tenth page's token instead of restarting. Verification
        // continues from the drain's end token, so the anchor is deleted
        // exactly once across both calls.
        XCTAssertEqual(fetches, 14)
        XCTAssertNil(fetchTokens[0])
        XCTAssertEqual(fetchTokens[10], Data([10]))
        XCTAssertEqual(fetchTokens[12], Data([12]))
        XCTAssertEqual(fetchTokens[13], Data([0xFF]))
        XCTAssertEqual(backend.deletedIDs.count, 1)
        XCTAssertEqual(backend.deletedIDs[0].map(\.recordName), ["workspace"])
    }

    // MARK: - Binding gates

    func testUnavailablePreflightPerformsNoCall() {
        for resolution in [KeychainAccountResolution.unavailable, .restricted, .undetermined] {
            let source = FakeMailboxAccountSource(resolutions: [resolution])
            let backend = FakeMailboxBackend()
            let provider = CloudKitMailboxProvider(accountSource: source, backend: backend)
            let data = binding() 
            XCTAssertEqual(provider.fetchZone(binding: data), .retryable)
            XCTAssertEqual(provider.saveZone(binding: data), .retryable)
            XCTAssertEqual(provider.readAnchor(binding: data).status, .retryable)
            XCTAssertEqual(provider.createAnchor(binding: data, fields: anchorFields() ), .retryable)
            XCTAssertEqual(
                provider.saveBundle(binding: data, identifier: identifier , payload: Data([0x01]) ),
                .retryable
            )
            XCTAssertEqual(provider.fetchChanges(binding: data, cursor: Data() ).status, .retryable)
            XCTAssertEqual(provider.deleteWorkspaceRecords(binding: data), .retryable)
            XCTAssertTrue(backend.calls.isEmpty)
        }
    }

    func testPreflightMismatchPerformsNoCall() {
        let source = FakeMailboxAccountSource(resolutions: [.available("other-record")])
        let backend = FakeMailboxBackend()
        let provider = CloudKitMailboxProvider(accountSource: source, backend: backend)
        let data = binding() 
        XCTAssertEqual(provider.fetchZone(binding: data), .accountchanged)
        XCTAssertEqual(provider.readAnchor(binding: data).status, .accountchanged)
        XCTAssertTrue(backend.calls.isEmpty)
    }

    func testPostflightMismatchExposesNothing() {
        let source = FakeMailboxAccountSource(
            resolutions: [.available(recordName), .available("other-record"), .available(recordName), .available("other-record")]
        )
        let backend = FakeMailboxBackend()
        backend.recordHandler = { _ in .found(self.anchorRecord()) }
        backend.changesHandler = { _, _ in
            .fetched(MailboxChanges(changed: [], deletedNames: [], tokenData: Data([0xAA]), moreComing: false))
        }
        let provider = CloudKitMailboxProvider(accountSource: source, backend: backend)
        let read = provider.readAnchor(binding: binding() )
        XCTAssertEqual(read.status, .unknownoutcome)
        XCTAssertNil(read.fields)
        let page = provider.fetchChanges(binding: binding() , cursor: Data() )
        XCTAssertEqual(page.status, .unknownoutcome)
        XCTAssertNil(page.nextCursor)
        XCTAssertNil(page.bundleIdentifier)
        XCTAssertNil(page.bundlePayload)
    }

    func testAccountChangeSignalDiscardsResult() {
        let backend = FakeMailboxBackend()
        backend.zoneHandler = { _ in
            NotificationCenter.default.post(name: .CKAccountChanged, object: nil)
            return .found
        }
        let (provider, _, _) = makeProvider(backend: backend)
        XCTAssertEqual(provider.fetchZone(binding: binding() ), .unknownoutcome)
    }

    func testCancellationReportsUnknownOutcome() {
        let backend = FakeMailboxBackend()
        backend.gate = DispatchSemaphore(value: 0)
        backend.zoneHandler = { _ in .found }
        let (provider, _, _) = makeProvider(backend: backend)
        let finished = DispatchSemaphore(value: 0)
        let result = LockedResult<IosCloudZoneFetchStatus>()
        let entered = DispatchSemaphore(value: 0)
        backend.entrySignal = entered
        DispatchQueue.global().async {
            result.set(provider.fetchZone(binding: self.binding() ))
            finished.signal()
        }
        XCTAssertEqual(entered.wait(timeout: .now() + 30), .success)
        provider.cancelInflight()
        XCTAssertEqual(finished.wait(timeout: .now() + 30), .success)
        XCTAssertEqual(result.get(), .unknownoutcome)
        XCTAssertTrue(backend.cancelled)
    }

    func testNewCarriersExposeNoSecretDescription() {
        XCTAssertEqual(IosCloudAnchorRead(status: .found, fields: nil).description(), "IosCloudAnchorRead(redacted)")
        XCTAssertEqual(
            IosCloudChangePage(status: .page, moreChanges: false, nextCursor: nil, bundleIdentifier: nil, bundlePayload: nil).description(),
            "IosCloudChangePage(redacted)"
        )
    }
}

private final class LockedResult<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Value?

    func set(_ value: Value) {
        lock.lock()
        defer { lock.unlock() }
        self.value = value
    }

    func get() -> Value? {
        lock.lock()
        defer { lock.unlock() }
        return value
    }
}

final class DeferredCloudKitMailboxBackendTests: XCTestCase {
    func testConstructionIsDeferredUntilFirstCall() {
        var constructions = 0
        let deferred = DeferredCloudKitMailboxBackend {
            constructions += 1
            return FakeMailboxBackend()
        }

        XCTAssertEqual(constructions, 0)
        _ = deferred.fetchZone(zoneID: MailboxRecordCodec.zoneID(), timeout: 1)
        XCTAssertEqual(constructions, 1)
        _ = deferred.fetchZone(zoneID: MailboxRecordCodec.zoneID(), timeout: 1)
        XCTAssertEqual(constructions, 1)
    }

    func testCancelBeforeConstructionIsANoOp() {
        var constructions = 0
        let deferred = DeferredCloudKitMailboxBackend {
            constructions += 1
            return FakeMailboxBackend()
        }

        deferred.cancelInflight()

        XCTAssertEqual(constructions, 0)
    }
}
