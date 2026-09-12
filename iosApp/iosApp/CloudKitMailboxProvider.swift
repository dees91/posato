import CloudKit
import Foundation
import PosatoShared

enum MailboxCloudLimits {
    static let containerIdentifier = "iCloud.app.posato.sync"
    static let operationTimeout: TimeInterval = 30
    static let zoneName = "PosatoSyncV1"
    static let anchorType = "PosatoWorkspaceV1"
    static let anchorName = "workspace"
    static let anchorFields = ["workspaceId", "transportEpochId", "keyEpochId"]
    static let bundleType = "PosatoEncryptedBundleV1"
    static let bundlePayloadField = "payload"
    static let anchorBytes = 48
    static let identifierBytes = 16
    static let bundleIdentifierBytes = 16
    static let uuidTextBytes = 36
    static let bundleBytes = 65_536
    static let cursorBytes = 16_384
    static let bindingBytes = 32
    static let recordDeleteBatchSize = 100
}

enum MailboxZoneLookup: Equatable {
    case found
    case missing
    case failed(NSError)
}

enum MailboxRecordLookup: Equatable {
    case found(CKRecord)
    case missing
    case zoneMissing
    case failed(NSError)

    static func == (lhs: MailboxRecordLookup, rhs: MailboxRecordLookup) -> Bool {
        switch (lhs, rhs) {
        case (.found(let left), .found(let right)):
            return left.recordID == right.recordID
        case (.missing, .missing), (.zoneMissing, .zoneMissing):
            return true
        case (.failed(let left), .failed(let right)):
            return left.domain == right.domain && left.code == right.code
        default:
            return false
        }
    }
}

enum MailboxRecordSave: Equatable {
    case saved
    case conflicted
    case failed(NSError)

    static func == (lhs: MailboxRecordSave, rhs: MailboxRecordSave) -> Bool {
        switch (lhs, rhs) {
        case (.saved, .saved), (.conflicted, .conflicted):
            return true
        case (.failed(let left), .failed(let right)):
            return left.domain == right.domain && left.code == right.code
        default:
            return false
        }
    }
}

struct MailboxChanges: Equatable {
    var changed: [CKRecord]
    var deletedNames: [String]
    var tokenData: Data?
    var moreComing: Bool

    static func == (lhs: MailboxChanges, rhs: MailboxChanges) -> Bool {
        return lhs.changed.map(\.recordID) == rhs.changed.map(\.recordID)
            && lhs.deletedNames == rhs.deletedNames
            && lhs.tokenData == rhs.tokenData
            && lhs.moreComing == rhs.moreComing
    }
}

enum MailboxChangesResult: Equatable {
    case fetched(MailboxChanges)
    case zoneMissing
    case invalidCursor
    case failed(NSError)

    static func == (lhs: MailboxChangesResult, rhs: MailboxChangesResult) -> Bool {
        switch (lhs, rhs) {
        case (.fetched(let left), .fetched(let right)):
            return left == right
        case (.zoneMissing, .zoneMissing), (.invalidCursor, .invalidCursor):
            return true
        case (.failed(let left), .failed(let right)):
            return left.domain == right.domain && left.code == right.code
        default:
            return false
        }
    }
}

protocol CloudKitMailboxBackend {
    func fetchZone(zoneID: CKRecordZone.ID, timeout: TimeInterval) -> MailboxZoneLookup
    func saveZone(zoneID: CKRecordZone.ID, timeout: TimeInterval) -> NSError?
    func fetchRecord(id: CKRecord.ID, timeout: TimeInterval) -> MailboxRecordLookup
    func saveRecordIfAbsent(_ record: CKRecord, timeout: TimeInterval) -> MailboxRecordSave
    func fetchChanges(zoneID: CKRecordZone.ID, tokenData: Data?, timeout: TimeInterval) -> MailboxChangesResult
    func deleteRecords(ids: [CKRecord.ID], timeout: TimeInterval) -> NSError?
    func cancelInflight()
}

/// Mirrors the macOS companion `CloudErrorMapper` table one to one so both
/// platforms return identical outcomes: six retryable codes, two zone-absent
/// codes, a distinct server-record-changed signal, a distinct unknown-item
/// signal, single-entry partial-failure unwrap, and unknown for the rest
/// (including change-token expiry, unauthenticated, and permission failures).
enum MailboxErrorMapper {
    static func isTokenExpired(_ error: NSError) -> Bool {
        let unwrapped = unwrapSinglePartial(error)
        return unwrapped.domain == CKError.errorDomain && unwrapped.code == CKError.changeTokenExpired.rawValue
    }

    static func isRetryable(_ error: NSError) -> Bool {
        let unwrapped = unwrapSinglePartial(error)
        guard unwrapped.domain == CKError.errorDomain,
            let code = CKError.Code(rawValue: unwrapped.code)
        else {
            return false
        }
        switch code {
        case .networkFailure,
            .networkUnavailable,
            .serviceUnavailable,
            .requestRateLimited,
            .quotaExceeded,
            .zoneBusy:
            return true
        default:
            return false
        }
    }

    static func isServerRecordChanged(_ error: NSError) -> Bool {
        return unwrapSinglePartial(error).domain == CKError.errorDomain
            && unwrapSinglePartial(error).code == CKError.serverRecordChanged.rawValue
    }

    static func isZoneAbsent(_ error: NSError) -> Bool {
        let unwrapped = unwrapSinglePartial(error)
        guard unwrapped.domain == CKError.errorDomain,
            let code = CKError.Code(rawValue: unwrapped.code)
        else {
            return false
        }
        switch code {
        case .zoneNotFound, .userDeletedZone:
            return true
        default:
            return false
        }
    }

    static func isUnknownItem(_ error: NSError) -> Bool {
        return error.domain == CKError.errorDomain
            && CKError.Code(rawValue: error.code) == .unknownItem
    }

    static func recordDelete(from error: NSError) -> NSError? {
        if isZoneAbsent(error) {
            return nil
        }
        var sawRetryable = false
        for item in partialErrors(error) {
            if isUnknownItem(item) || isZoneAbsent(item) {
                continue
            }
            if isRetryable(item) {
                sawRetryable = true
            } else {
                return error
            }
        }
        return sawRetryable ? error : nil
    }

    private static func partialErrors(_ error: NSError) -> [NSError] {
        guard error.domain == CKError.errorDomain,
            error.code == CKError.partialFailure.rawValue,
            let partial = error.userInfo[CKPartialErrorsByItemIDKey] as? [AnyHashable: Any]
        else {
            return [error]
        }
        let items = partial.values.compactMap { $0 as? NSError }
        return items.isEmpty ? [error] : items
    }

    static func unwrapSinglePartial(_ error: NSError) -> NSError {
        guard error.domain == CKError.errorDomain,
            error.code == CKError.partialFailure.rawValue,
            let partial = error.userInfo[CKPartialErrorsByItemIDKey] as? [AnyHashable: Any],
            partial.count == 1,
            let inner = partial.values.first as? NSError
        else {
            return error
        }
        return inner
    }
}

enum MailboxRecordCodec {
    static func zoneID() -> CKRecordZone.ID {
        return CKRecordZone.ID(zoneName: MailboxCloudLimits.zoneName, ownerName: CKCurrentUserDefaultName)
    }

    static func anchorRecordID(zoneID: CKRecordZone.ID) -> CKRecord.ID {
        return CKRecord.ID(recordName: MailboxCloudLimits.anchorName, zoneID: zoneID)
    }

    static func bundleRecordID(identifier: Data, zoneID: CKRecordZone.ID) -> CKRecord.ID? {
        guard let name = uuidText(from: identifier) else {
            return nil
        }
        return CKRecord.ID(recordName: name, zoneID: zoneID)
    }

    static func uuidText(from identifier: Data) -> String? {
        guard identifier.count == MailboxCloudLimits.bundleIdentifierBytes else {
            return nil
        }
        let hex = Array("0123456789abcdef".utf8)
        var text = [UInt8]()
        text.reserveCapacity(MailboxCloudLimits.uuidTextBytes)
        for index in 0..<16 {
            let byte = identifier[index]
            text.append(hex[Int(byte >> 4)])
            text.append(hex[Int(byte & 0x0F)])
            if index == 3 || index == 5 || index == 7 || index == 9 {
                text.append(UInt8(ascii: "-"))
            }
        }
        return String(bytes: text, encoding: .utf8)
    }

    static func isCanonicalUUID(_ text: String) -> Bool {
        guard text.utf8.count == MailboxCloudLimits.uuidTextBytes else {
            return false
        }
        let scalars = Array(text.unicodeScalars)
        let dashes = [8, 13, 18, 23]
        for (index, scalar) in scalars.enumerated() {
            if dashes.contains(index) {
                if scalar != "-" {
                    return false
                }
            } else if !isLowerHex(scalar) {
                return false
            }
        }
        return true
    }

    static func identifierBytes(from text: String) -> Data? {
        guard isCanonicalUUID(text) else {
            return nil
        }
        var bytes = Data(count: MailboxCloudLimits.bundleIdentifierBytes)
        var offset = 0
        var index = text.startIndex
        while index < text.endIndex {
            if text[index] == "-" {
                index = text.index(after: index)
                continue
            }
            let next = text.index(after: index)
            guard next < text.endIndex,
                let high = hexValue(text[index]),
                let low = hexValue(text[next])
            else {
                return nil
            }
            bytes[offset] = UInt8((high << 4) | low)
            offset += 1
            index = text.index(after: next)
        }
        return offset == MailboxCloudLimits.bundleIdentifierBytes ? bytes : nil
    }

    /// Returns the combined 48 anchor bytes when the record carries exactly
    /// the anchor type, name, field set, and 16-byte fields.
    static func validateAnchor(_ record: CKRecord, zoneID: CKRecordZone.ID) -> Data? {
        guard record.recordID.zoneID.zoneName == zoneID.zoneName,
            record.recordType == MailboxCloudLimits.anchorType,
            record.recordID.recordName == MailboxCloudLimits.anchorName,
            Set(record.allKeys()) == Set(MailboxCloudLimits.anchorFields)
        else {
            return nil
        }
        var combined = Data()
        for field in MailboxCloudLimits.anchorFields {
            guard let value = record[field] as? Data, value.count == MailboxCloudLimits.identifierBytes else {
                return nil
            }
            combined.append(value)
        }
        guard combined.count == MailboxCloudLimits.anchorBytes else {
            return nil
        }
        return combined
    }

    /// Returns the identifier bytes and payload when the record carries
    /// exactly the bundle type, a canonical UUID name, and a bounded payload
    /// whose name round-trips back to the same identifier.
    static func validateBundle(_ record: CKRecord, zoneID: CKRecordZone.ID) -> (Data, Data)? {
        guard record.recordID.zoneID.zoneName == zoneID.zoneName,
            record.recordType == MailboxCloudLimits.bundleType,
            isCanonicalUUID(record.recordID.recordName),
            Set(record.allKeys()) == Set([MailboxCloudLimits.bundlePayloadField]),
            let payload = record[MailboxCloudLimits.bundlePayloadField] as? Data,
            !payload.isEmpty,
            payload.count <= MailboxCloudLimits.bundleBytes,
            let identifier = identifierBytes(from: record.recordID.recordName),
            uuidText(from: identifier) == record.recordID.recordName
        else {
            return nil
        }
        return (identifier, payload)
    }

    static func archiveToken(_ token: CKServerChangeToken) -> Data? {
        guard
            let data = try? NSKeyedArchiver.archivedData(
                withRootObject: token,
                requiringSecureCoding: true
            ),
            data.count <= MailboxCloudLimits.cursorBytes
        else {
            return nil
        }
        return data
    }

    static func unarchiveToken(_ data: Data) -> CKServerChangeToken? {
        guard !data.isEmpty, data.count <= MailboxCloudLimits.cursorBytes else {
            return nil
        }
        return try? NSKeyedUnarchiver.unarchivedObject(ofClass: CKServerChangeToken.self, from: data)
    }

    private static func isLowerHex(_ scalar: Unicode.Scalar) -> Bool {
        return (scalar >= "0" && scalar <= "9") || (scalar >= "a" && scalar <= "f")
    }

    private static func hexValue(_ character: Character) -> Int? {
        guard let ascii = character.asciiValue else {
            return nil
        }
        switch character {
        case "0"..."9":
            return Int(ascii - UInt8(ascii: "0"))
        case "a"..."f":
            return Int(ascii - UInt8(ascii: "a") + 10)
        default:
            return nil
        }
    }
}

enum MailboxZoneFetch: Equatable {
    case found
    case missing
    case retryable
    case unknownOutcome
}

enum MailboxZoneSave: Equatable {
    case created
    case alreadyExists
    case retryable
    case unknownOutcome
}

enum MailboxAnchorRead: Equatable {
    case found(Data)
    case missing
    case retryable
    case unknownOutcome
    case integrityFailure

    static func == (lhs: MailboxAnchorRead, rhs: MailboxAnchorRead) -> Bool {
        switch (lhs, rhs) {
        case (.found(let left), .found(let right)):
            return left == right
        case (.missing, .missing),
            (.retryable, .retryable),
            (.unknownOutcome, .unknownOutcome),
            (.integrityFailure, .integrityFailure):
            return true
        default:
            return false
        }
    }
}

enum MailboxAnchorCreate: Equatable {
    case created
    case conflict
    case retryable
    case unknownOutcome
    case integrityFailure
}

enum MailboxBundleSave: Equatable {
    case created
    case identical
    case conflict
    case retryable
    case unknownOutcome
    case integrityFailure
}

struct MailboxPage: Equatable {
    var bundleIdentifier: Data?
    var bundle: Data?
    var moreComing: Bool
    var cursor: Data
}

enum MailboxChangeFetch: Equatable {
    case tokenExpired
    case page(MailboxPage)
    case zoneMissing
    case retryable
    case unknownOutcome
    case integrityFailure

    static func == (lhs: MailboxChangeFetch, rhs: MailboxChangeFetch) -> Bool {
        switch (lhs, rhs) {
        case (.page(let left), .page(let right)):
            return left == right
        case (.zoneMissing, .zoneMissing),
            (.retryable, .retryable),
            (.unknownOutcome, .unknownOutcome),
            (.integrityFailure, .integrityFailure):
            return true
        default:
            return false
        }
    }
}

enum MailboxRecordDelete: Equatable {
    case deletedAndAbsent
    case retryable
    case unknownOutcome
}

enum MailboxBundleSweep: Equatable {
    case swept
    case anchorPresent
    case retryable
    case unknownOutcome
}

/// Mirrors the macOS companion `CloudStore` decision flow over the injected
/// backend: zone save always reconciles through the exact fetch, an existing
/// anchor is a conflict whether byte-identical or different, bundle save is
/// create-only with identical-bytes idempotence, zone absence on record reads
/// stays unknown, and the anchor create postflight never re-reads.
struct MailboxStore {
    var backend: any CloudKitMailboxBackend
    var zoneID: CKRecordZone.ID

    func fetchZone(timeout: TimeInterval) -> MailboxZoneFetch {
        switch backend.fetchZone(zoneID: zoneID, timeout: timeout) {
        case .found:
            return .found
        case .missing:
            return .missing
        case .failed(let error):
            return MailboxErrorMapper.isRetryable(error) ? .retryable : .unknownOutcome
        }
    }

    func saveZone(timeout: TimeInterval) -> MailboxZoneSave {
        switch backend.fetchZone(zoneID: zoneID, timeout: timeout) {
        case .found:
            return .alreadyExists
        case .failed(let error):
            return MailboxErrorMapper.isRetryable(error) ? .retryable : .unknownOutcome
        case .missing:
            break
        }
        if backend.saveZone(zoneID: zoneID, timeout: timeout) != nil {
            return reconcileZoneSave(expected: .alreadyExists, timeout: timeout)
        }
        return reconcileZoneSave(expected: .created, timeout: timeout)
    }

    func readAnchor(timeout: TimeInterval) -> MailboxAnchorRead {
        switch backend.fetchRecord(id: MailboxRecordCodec.anchorRecordID(zoneID: zoneID), timeout: timeout) {
        case .found(let record):
            guard let fields = MailboxRecordCodec.validateAnchor(record, zoneID: zoneID) else {
                return .integrityFailure
            }
            return .found(fields)
        case .missing:
            return .missing
        case .zoneMissing:
            return .unknownOutcome
        case .failed(let error):
            return MailboxErrorMapper.isRetryable(error) ? .retryable : .unknownOutcome
        }
    }

    func createAnchor(fields: Data, timeout: TimeInterval) -> MailboxAnchorCreate {
        guard fields.count == MailboxCloudLimits.anchorBytes else {
            return .integrityFailure
        }
        switch backend.fetchRecord(id: MailboxRecordCodec.anchorRecordID(zoneID: zoneID), timeout: timeout) {
        case .found(let record):
            guard MailboxRecordCodec.validateAnchor(record, zoneID: zoneID) != nil else {
                return .integrityFailure
            }
            return .conflict
        case .failed(let error):
            return MailboxErrorMapper.isRetryable(error) ? .retryable : .unknownOutcome
        case .missing:
            break
        case .zoneMissing:
            return .unknownOutcome
        }
        switch backend.saveRecordIfAbsent(makeAnchor(fields: fields), timeout: timeout) {
        case .saved:
            return .created
        case .conflicted:
            return .conflict
        case .failed(let error):
            return MailboxErrorMapper.isRetryable(error) ? .retryable : .unknownOutcome
        }
    }

    func saveBundle(identifier: Data, payload: Data, timeout: TimeInterval) -> MailboxBundleSave {
        guard identifier.count == MailboxCloudLimits.bundleIdentifierBytes,
            !payload.isEmpty,
            payload.count <= MailboxCloudLimits.bundleBytes,
            let recordName = MailboxRecordCodec.uuidText(from: identifier)
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
        switch backend.saveRecordIfAbsent(makeBundle(name: recordName, payload: payload), timeout: timeout) {
        case .saved:
            return .created
        case .conflicted:
            return reconcileBundleSave(name: recordName, payload: payload, timeout: timeout)
        case .failed(let error):
            return MailboxErrorMapper.isRetryable(error) ? .retryable : .unknownOutcome
        }
    }

    func fetchChanges(cursor: Data, timeout: TimeInterval) -> MailboxChangeFetch {
        if !cursor.isEmpty, cursor.count > MailboxCloudLimits.cursorBytes {
            return .integrityFailure
        }
        switch backend.fetchChanges(zoneID: zoneID, tokenData: cursor.isEmpty ? nil : cursor, timeout: timeout) {
        case .zoneMissing:
            return .zoneMissing
        case .invalidCursor:
            return .integrityFailure
        case .failed(let error):
            if MailboxErrorMapper.isTokenExpired(error) { return .tokenExpired }
            return MailboxErrorMapper.isRetryable(error) ? .retryable : .unknownOutcome
        case .fetched(let fetched):
            return collectPage(fetched)
        }
    }

    func deleteWorkspaceRecords(timeout: TimeInterval) -> MailboxRecordDelete {
        switch traverseBundleNames(timeout: timeout) {
        case .zoneMissing:
            return .deletedAndAbsent
        case .retryable:
            return .retryable
        case .unknownOutcome:
            return .unknownOutcome
        case .names(let names):
            for chunk in names.chunked(into: MailboxCloudLimits.recordDeleteBatchSize) {
                if let error = backend.deleteRecords(
                    ids: chunk.map { CKRecord.ID(recordName: $0, zoneID: zoneID) },
                    timeout: timeout
                ) {
                    return MailboxErrorMapper.isRetryable(error) ? .retryable : .unknownOutcome
                }
            }
        }
        if let error = backend.deleteRecords(
            ids: [MailboxRecordCodec.anchorRecordID(zoneID: zoneID)],
            timeout: timeout
        ) {
            return MailboxErrorMapper.isRetryable(error) ? .retryable : .unknownOutcome
        }
        switch backend.fetchRecord(id: MailboxRecordCodec.anchorRecordID(zoneID: zoneID), timeout: timeout) {
        case .missing, .zoneMissing:
            break
        case .found:
            return .unknownOutcome
        case .failed(let error):
            return MailboxErrorMapper.isRetryable(error) ? .retryable : .unknownOutcome
        }
        switch traverseBundleNames(timeout: timeout) {
        case .zoneMissing:
            return .deletedAndAbsent
        case .retryable:
            return .retryable
        case .unknownOutcome:
            return .unknownOutcome
        case .names(let remaining):
            return remaining.isEmpty ? .deletedAndAbsent : .unknownOutcome
        }
    }

    func sweepBundlesIfAnchorMissing(timeout: TimeInterval) -> MailboxBundleSweep {
        let names: [String]
        switch traverseBundleNames(timeout: timeout) {
        case .zoneMissing:
            return .swept
        case .retryable:
            return .retryable
        case .unknownOutcome:
            return .unknownOutcome
        case .names(let enumerated):
            names = enumerated
        }
        switch backend.fetchRecord(id: MailboxRecordCodec.anchorRecordID(zoneID: zoneID), timeout: timeout) {
        case .missing:
            break
        case .found:
            return .anchorPresent
        case .zoneMissing:
            return .swept
        case .failed(let error):
            return MailboxErrorMapper.isRetryable(error) ? .retryable : .unknownOutcome
        }
        for chunk in names.chunked(into: MailboxCloudLimits.recordDeleteBatchSize) {
            if let error = backend.deleteRecords(
                ids: chunk.map { CKRecord.ID(recordName: $0, zoneID: zoneID) },
                timeout: timeout
            ) {
                return MailboxErrorMapper.isRetryable(error) ? .retryable : .unknownOutcome
            }
        }
        return .swept
    }

    private enum BundleNameTraversal {
        case names([String])
        case zoneMissing
        case retryable
        case unknownOutcome
    }

    private func traverseBundleNames(timeout: TimeInterval) -> BundleNameTraversal {
        var tokenData: Data?
        var names: [String] = []
        var restarted = false
        var previous: Data?
        while true {
            switch backend.fetchChanges(zoneID: zoneID, tokenData: tokenData, timeout: timeout) {
            case .zoneMissing:
                return .zoneMissing
            case .invalidCursor:
                return .unknownOutcome
            case .failed(let error):
                if MailboxErrorMapper.isTokenExpired(error) {
                    if restarted {
                        return .retryable
                    }
                    restarted = true
                    tokenData = nil
                    names = []
                    previous = nil
                    continue
                }
                return MailboxErrorMapper.isRetryable(error) ? .retryable : .unknownOutcome
            case .fetched(let fetched):
                for record in fetched.changed where record.recordType == MailboxCloudLimits.bundleType {
                    names.append(record.recordID.recordName)
                }
                guard let token = fetched.tokenData, !token.isEmpty,
                    token.count <= MailboxCloudLimits.cursorBytes
                else {
                    return .unknownOutcome
                }
                if !fetched.moreComing {
                    return .names(names)
                }
                guard token != previous else {
                    return .unknownOutcome
                }
                previous = token
                tokenData = token
            }
        }
    }

    private enum BundleComparison {
        case found(Bool)
        case absent
        case unresolved(MailboxBundleSave)
    }

    private func compareBundle(
        name: String,
        payload: Data,
        timeout: TimeInterval
    ) -> BundleComparison {
        switch backend.fetchRecord(id: CKRecord.ID(recordName: name, zoneID: zoneID), timeout: timeout) {
        case .found(let record):
            guard let validated = MailboxRecordCodec.validateBundle(record, zoneID: zoneID) else {
                return .unresolved(.integrityFailure)
            }
            return .found(validated.1 == payload)
        case .missing:
            return .absent
        case .zoneMissing:
            return .unresolved(.unknownOutcome)
        case .failed(let error):
            return .unresolved(MailboxErrorMapper.isRetryable(error) ? .retryable : .unknownOutcome)
        }
    }

    private func reconcileBundleSave(
        name: String,
        payload: Data,
        timeout: TimeInterval
    ) -> MailboxBundleSave {
        switch compareBundle(name: name, payload: payload, timeout: timeout) {
        case .found(let identical):
            return identical ? .identical : .conflict
        case .absent, .unresolved:
            return .unknownOutcome
        }
    }

    private func collectPage(_ changes: MailboxChanges) -> MailboxChangeFetch {
        var identifier: Data?
        var bundle: Data?
        for record in changes.changed {
            if record.recordType == MailboxCloudLimits.anchorType {
                continue
            }
            guard identifier == nil,
                let validated = MailboxRecordCodec.validateBundle(record, zoneID: zoneID)
            else {
                return .integrityFailure
            }
            identifier = validated.0
            bundle = validated.1
        }
        guard let token = changes.tokenData, !token.isEmpty,
            token.count <= MailboxCloudLimits.cursorBytes
        else {
            return .unknownOutcome
        }
        return .page(
            MailboxPage(
                bundleIdentifier: identifier,
                bundle: bundle,
                moreComing: changes.moreComing,
                cursor: token
            )
        )
    }

    private func reconcileZoneSave(expected: MailboxZoneSave, timeout: TimeInterval) -> MailboxZoneSave {
        switch backend.fetchZone(zoneID: zoneID, timeout: timeout) {
        case .found:
            return expected
        case .missing, .failed:
            return .unknownOutcome
        }
    }

    private func makeAnchor(fields: Data) -> CKRecord {
        let record = CKRecord(
            recordType: MailboxCloudLimits.anchorType,
            recordID: MailboxRecordCodec.anchorRecordID(zoneID: zoneID)
        )
        for (index, field) in MailboxCloudLimits.anchorFields.enumerated() {
            let start = index * MailboxCloudLimits.identifierBytes
            record[field] = fields.subdata(in: start..<(start + MailboxCloudLimits.identifierBytes)) 
        }
        return record
    }

    private func makeBundle(name: String, payload: Data) -> CKRecord {
        let record = CKRecord(
            recordType: MailboxCloudLimits.bundleType,
            recordID: CKRecord.ID(recordName: name, zoneID: zoneID)
        )
        record[MailboxCloudLimits.bundlePayloadField] = payload 
        return record
    }
}

private final class MailboxGeneration: @unchecked Sendable {
    private let lock = NSLock()
    private var value: UInt64 = 0

    func current() -> UInt64 {
        lock.lock()
        defer { lock.unlock() }
        return value
    }

    @discardableResult
    func advance() -> UInt64 {
        lock.lock()
        defer { lock.unlock() }
        value &+= 1
        return value
    }
}

final class CloudKitMailboxProvider: IosCloudKitMailboxProvider {
    private let accountSource: KeychainAccountSource
    private let backend: any CloudKitMailboxBackend
    private let zoneID: CKRecordZone.ID
    private let timeout: TimeInterval
    private let generation = MailboxGeneration()

    init(
        accountSource: KeychainAccountSource,
        backend: any CloudKitMailboxBackend,
        zoneID: CKRecordZone.ID = MailboxRecordCodec.zoneID(),
        timeout: TimeInterval = MailboxCloudLimits.operationTimeout
    ) {
        self.accountSource = accountSource
        self.backend = backend
        self.zoneID = zoneID
        self.timeout = timeout
    }

    func cancelInflight() {
        generation.advance()
        backend.cancelInflight()
    }

    func fetchZone(binding: Data) -> IosCloudZoneFetchStatus {
        switch preflight(expectedBinding: binding as Data) {
        case .proceed:
            break
        case .retryable:
            return .retryable
        case .accountChanged:
            return .accountchanged
        }
        let mark = generation.current()
        let (flag, observer) = observingAccountChange()
        let result = store().fetchZone(timeout: timeout)
        NotificationCenter.default.removeObserver(observer)
        guard !flag.isMarked, generation.current() == mark else {
            return .unknownoutcome
        }
        switch result {
        case .found:
            return confirm(.found, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .missing:
            return confirm(.missing, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .retryable:
            return confirm(.retryable, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .unknownOutcome:
            return .unknownoutcome
        }
    }

    func saveZone(binding: Data) -> IosCloudZoneSaveStatus {
        switch preflight(expectedBinding: binding as Data) {
        case .proceed:
            break
        case .retryable:
            return .retryable
        case .accountChanged:
            return .accountchanged
        }
        let mark = generation.current()
        let (flag, observer) = observingAccountChange()
        let result = store().saveZone(timeout: timeout)
        NotificationCenter.default.removeObserver(observer)
        guard !flag.isMarked, generation.current() == mark else {
            return .unknownoutcome
        }
        switch result {
        case .created:
            return confirm(.created, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .alreadyExists:
            return confirm(.alreadyexists, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .retryable:
            return confirm(.retryable, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .unknownOutcome:
            return .unknownoutcome
        }
    }

    func readAnchor(binding: Data) -> IosCloudAnchorRead {
        switch preflight(expectedBinding: binding as Data) {
        case .proceed:
            break
        case .retryable:
            return IosCloudAnchorRead(status: .retryable, fields: nil)
        case .accountChanged:
            return IosCloudAnchorRead(status: .accountchanged, fields: nil)
        }
        let mark = generation.current()
        let (flag, observer) = observingAccountChange()
        let result = store().readAnchor(timeout: timeout)
        NotificationCenter.default.removeObserver(observer)
        guard !flag.isMarked, generation.current() == mark else {
            return IosCloudAnchorRead(status: .unknownoutcome, fields: nil)
        }
        switch result {
        case .found(let found):
            guard postflightMatches(binding as Data) else {
                var discarded = found
                discarded.resetBytes(in: 0..<discarded.count)
                return IosCloudAnchorRead(status: .unknownoutcome, fields: nil)
            }
            return IosCloudAnchorRead(status: .found, fields: found )
        case .missing:
            return confirmRead(.missing, expectedBinding: binding as Data)
        case .retryable:
            return confirmRead(.retryable, expectedBinding: binding as Data)
        case .unknownOutcome:
            return IosCloudAnchorRead(status: .unknownoutcome, fields: nil)
        case .integrityFailure:
            return confirmRead(.integrityfailure, expectedBinding: binding as Data)
        }
    }

    func createAnchor(binding: Data, fields: Data) -> IosCloudAnchorCreateStatus {
        guard (fields as Data).count == MailboxCloudLimits.anchorBytes else {
            return .integrityfailure
        }
        switch preflight(expectedBinding: binding as Data) {
        case .proceed:
            break
        case .retryable:
            return .retryable
        case .accountChanged:
            return .accountchanged
        }
        let mark = generation.current()
        let (flag, observer) = observingAccountChange()
        let result = store().createAnchor(fields: fields as Data, timeout: timeout)
        NotificationCenter.default.removeObserver(observer)
        guard !flag.isMarked, generation.current() == mark else {
            return .unknownoutcome
        }
        switch result {
        case .created:
            return confirm(.created, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .conflict:
            return confirm(.conflict, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .retryable:
            return confirm(.retryable, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .unknownOutcome:
            return .unknownoutcome
        case .integrityFailure:
            return confirm(.integrityfailure, expectedBinding: binding as Data, unknown: .unknownoutcome)
        }
    }

    func saveBundle(binding: Data, identifier: Data, payload: Data) -> IosCloudBundleSaveStatus {
        let identifierData = identifier as Data
        let payloadData = payload as Data
        guard identifierData.count == MailboxCloudLimits.bundleIdentifierBytes,
            !payloadData.isEmpty,
            payloadData.count <= MailboxCloudLimits.bundleBytes
        else {
            return .integrityfailure
        }
        switch preflight(expectedBinding: binding as Data) {
        case .proceed:
            break
        case .retryable:
            return .retryable
        case .accountChanged:
            return .accountchanged
        }
        let mark = generation.current()
        let (flag, observer) = observingAccountChange()
        let result = store().saveBundle(identifier: identifierData, payload: payloadData, timeout: timeout)
        NotificationCenter.default.removeObserver(observer)
        guard !flag.isMarked, generation.current() == mark else {
            return .unknownoutcome
        }
        switch result {
        case .created:
            return confirm(.saved, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .identical:
            return confirm(.identical, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .conflict:
            return confirm(.conflict, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .retryable:
            return confirm(.retryable, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .unknownOutcome:
            return .unknownoutcome
        case .integrityFailure:
            return confirm(.integrityfailure, expectedBinding: binding as Data, unknown: .unknownoutcome)
        }
    }

    func fetchChanges(binding: Data, cursor: Data) -> IosCloudChangePage {
        let cursorData = cursor as Data
        guard cursorData.count <= MailboxCloudLimits.cursorBytes else {
            return IosCloudChangePage(
                status: .integrityfailure,
                moreChanges: false,
                nextCursor: nil,
                bundleIdentifier: nil,
                bundlePayload: nil
            )
        }
        switch preflight(expectedBinding: binding as Data) {
        case .proceed:
            break
        case .retryable:
            return IosCloudChangePage(
                status: .retryable,
                moreChanges: false,
                nextCursor: nil,
                bundleIdentifier: nil,
                bundlePayload: nil
            )
        case .accountChanged:
            return IosCloudChangePage(
                status: .accountchanged,
                moreChanges: false,
                nextCursor: nil,
                bundleIdentifier: nil,
                bundlePayload: nil
            )
        }
        let mark = generation.current()
        let (flag, observer) = observingAccountChange()
        let result = store().fetchChanges(cursor: cursorData, timeout: timeout)
        NotificationCenter.default.removeObserver(observer)
        guard !flag.isMarked, generation.current() == mark else {
            return IosCloudChangePage(
                status: .unknownoutcome,
                moreChanges: false,
                nextCursor: nil,
                bundleIdentifier: nil,
                bundlePayload: nil
            )
        }
        switch result {
        case .page(let page):
            guard postflightMatches(binding as Data) else {
                return IosCloudChangePage(
                    status: .unknownoutcome,
                    moreChanges: false,
                    nextCursor: nil,
                    bundleIdentifier: nil,
                    bundlePayload: nil
                )
            }
            return IosCloudChangePage(
                status: .page,
                moreChanges: page.moreComing,
                nextCursor: page.cursor ,
                bundleIdentifier: page.bundleIdentifier ,
                bundlePayload: page.bundle 
            )
        case .tokenExpired:
            return confirmPage(.tokenexpired, expectedBinding: binding as Data)
        case .zoneMissing:
            return confirmPage(.zonemissing, expectedBinding: binding as Data)
        case .retryable:
            return confirmPage(.retryable, expectedBinding: binding as Data)
        case .unknownOutcome:
            return IosCloudChangePage(
                status: .unknownoutcome,
                moreChanges: false,
                nextCursor: nil,
                bundleIdentifier: nil,
                bundlePayload: nil
            )
        case .integrityFailure:
            return confirmPage(.integrityfailure, expectedBinding: binding as Data)
        }
    }

    func deleteWorkspaceRecords(binding: Data) -> IosCloudRecordDeleteStatus {
        switch preflight(expectedBinding: binding as Data) {
        case .proceed:
            break
        case .retryable:
            return .retryable
        case .accountChanged:
            return .accountchanged
        }
        let mark = generation.current()
        let (flag, observer) = observingAccountChange()
        let result = store().deleteWorkspaceRecords(timeout: timeout)
        NotificationCenter.default.removeObserver(observer)
        guard !flag.isMarked, generation.current() == mark else {
            return .unknownoutcome
        }
        switch result {
        case .deletedAndAbsent:
            return confirm(.deletedandabsent, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .retryable:
            return confirm(.retryable, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .unknownOutcome:
            return .unknownoutcome
        }
    }

    func sweepBundlesIfAnchorMissing(binding: Data) -> IosCloudBundleSweepStatus {
        switch preflight(expectedBinding: binding as Data) {
        case .proceed:
            break
        case .retryable:
            return .retryable
        case .accountChanged:
            return .accountchanged
        }
        let mark = generation.current()
        let (flag, observer) = observingAccountChange()
        let result = store().sweepBundlesIfAnchorMissing(timeout: timeout)
        NotificationCenter.default.removeObserver(observer)
        guard !flag.isMarked, generation.current() == mark else {
            return .unknownoutcome
        }
        switch result {
        case .swept:
            return confirm(.swept, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .anchorPresent:
            return confirm(.anchorpresent, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .retryable:
            return confirm(.retryable, expectedBinding: binding as Data, unknown: .unknownoutcome)
        case .unknownOutcome:
            return .unknownoutcome
        }
    }

    private func store() -> MailboxStore {
        return MailboxStore(backend: backend, zoneID: zoneID)
    }

    private enum GateDecision {
        case proceed
        case retryable
        case accountChanged
    }

    private func preflight(expectedBinding: Data) -> GateDecision {
        switch accountSource.currentRecordName() {
        case .available(let recordName):
            return SynchronizableKeychainProvider.deriveBinding(recordName: recordName) == expectedBinding
                ? .proceed : .accountChanged
        case .unavailable, .restricted, .undetermined:
            return .retryable
        }
    }

    private func postflightMatches(_ expectedBinding: Data) -> Bool {
        guard case .available(let recordName) = accountSource.currentRecordName() else {
            return false
        }
        return SynchronizableKeychainProvider.deriveBinding(recordName: recordName) == expectedBinding
    }

    private func observingAccountChange() -> (flag: AccountChangeFlag, observer: NSObjectProtocol) {
        let flag = AccountChangeFlag()
        let observer = NotificationCenter.default.addObserver(forName: .CKAccountChanged, object: nil, queue: nil) { _ in
            flag.mark()
        }
        return (flag, observer)
    }

    private func confirm<T>(_ value: T, expectedBinding: Data, unknown: T) -> T {
        guard postflightMatches(expectedBinding) else {
            return unknown
        }
        return value
    }

    private func confirmRead(_ status: IosCloudAnchorReadStatus, expectedBinding: Data) -> IosCloudAnchorRead {
        guard postflightMatches(expectedBinding) else {
            return IosCloudAnchorRead(status: .unknownoutcome, fields: nil)
        }
        return IosCloudAnchorRead(status: status, fields: nil)
    }

    private func confirmPage(_ status: IosCloudChangeFetchStatus, expectedBinding: Data) -> IosCloudChangePage {
        guard postflightMatches(expectedBinding) else {
            return IosCloudChangePage(
                status: .unknownoutcome,
                moreChanges: false,
                nextCursor: nil,
                bundleIdentifier: nil,
                bundlePayload: nil
            )
        }
        return IosCloudChangePage(
            status: status,
            moreChanges: false,
            nextCursor: nil,
            bundleIdentifier: nil,
            bundlePayload: nil
        )
    }

}

extension Array {
    fileprivate func chunked(into size: Int) -> [[Element]] {
        guard size > 0 else {
            return isEmpty ? [] : [self]
        }
        var chunks: [[Element]] = []
        var index = startIndex
        while index < endIndex {
            let next = self.index(index, offsetBy: size, limitedBy: endIndex) ?? endIndex
            chunks.append(Array(self[index..<next]))
            index = next
        }
        return chunks
    }
}

private final class AccountChangeFlag: @unchecked Sendable {
    private let lock = NSLock()
    private var marked = false

    func mark() {
        lock.lock()
        defer { lock.unlock() }
        marked = true
    }

    var isMarked: Bool {
        lock.lock()
        defer { lock.unlock() }
        return marked
    }
}
