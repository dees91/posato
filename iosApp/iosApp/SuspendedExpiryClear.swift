import CryptoKit
import DeviceActivity
import Foundation
import ManagedSettings

/// Fixed identity for the suspended-expiry path. The scheduler, the monitor
/// extension, and the foreign-activity no-op test all resolve through this
/// single constant so the literal cannot drift between targets.
enum SuspendedExpiryActivity {
    static let name = DeviceActivityName("app.posato.session.expiry")
    static let appGroupIdentifier = "group.app.posato.ios.session"
    static let recordSchemaVersion = 1

    /// Device Activity refuses intervals below the platform minimum. Shorter
    /// sessions report `below-platform-minimum` and rely on foreground expiry.
    static let minimumInterval: TimeInterval = 15 * 60
}

/// The one Managed Settings store Posato owns. Shared with the application
/// target so the enforcer and the monitor extension clear the same store.
enum PosatoManagedSettingsStore {
    static let name = ManagedSettingsStore.Name("app.posato.session")
}

/// Narrow store seam for the monitor extension. The extension clears the
/// named store only; it never reads selection tokens, domains, or schedules.
/// The method name differs from the enforcement seam on purpose: both extend
/// the same platform type in one module, so they must not redeclare it.
protocol SuspendedExpirySettingsStore: AnyObject {
    func clearOwnedSettings()
}

extension ManagedSettingsStore: SuspendedExpirySettingsStore {
    func clearOwnedSettings() {
        clearAllSettings()
    }
}

struct SuspendedExpiryPendingRecord: Codable, Equatable {
    let version: Int
    let sessionId: String
}

struct SuspendedExpiryClearedRecord: Codable, Equatable {
    let version: Int
    let sessionId: String
    let clearedAt: TimeInterval
}

enum SuspendedExpiryClearedRead {
    case absent
    case present(SuspendedExpiryClearedRecord)
    case failed
}

/// File-backed App Group record store for the suspended-expiry path. It keeps
/// its own `SuspendedExpiry` directory, separate from the selection store, and
/// follows the same conventions: atomic writes, protection available after
/// first unlock, backup exclusion, and bounded file sizes.
struct SuspendedExpiryRecordStore {
    private static let directoryName = "SuspendedExpiry"
    private static let pendingFileName = "pending-v1.json"
    private static let clearedFileName = "cleared-v1.json"
    private static let maximumFileBytes = 4096
    private static let maximumSessionIdCharacters = 256

    let directoryURL: URL
    private let fileManager: FileManager

    init(directoryURL: URL, fileManager: FileManager = .default) {
        self.directoryURL = directoryURL
        self.fileManager = fileManager
    }

    static func live(
        fileManager: FileManager = .default
    ) -> SuspendedExpiryRecordStore? {
        guard let container = fileManager.containerURL(
            forSecurityApplicationGroupIdentifier: SuspendedExpiryActivity.appGroupIdentifier
        ) else {
            return nil
        }
        return SuspendedExpiryRecordStore(
            directoryURL: container.appendingPathComponent(directoryName, isDirectory: true),
            fileManager: fileManager
        )
    }

    func writePending(sessionId: String) throws {
        let record = SuspendedExpiryPendingRecord(
            version: SuspendedExpiryActivity.recordSchemaVersion,
            sessionId: sessionId
        )
        try write(record, fileName: Self.pendingFileName)
    }

    func readPending() -> SuspendedExpiryPendingRecord? {
        guard let data = boundedContents(of: Self.pendingFileName),
              let record = try? JSONDecoder().decode(SuspendedExpiryPendingRecord.self, from: data),
              record.version == SuspendedExpiryActivity.recordSchemaVersion
        else {
            return nil
        }
        return record
    }

    func removePending() {
        try? fileManager.removeItem(at: directoryURL.appendingPathComponent(Self.pendingFileName))
    }

    func removeCleared(sessionId: String) {
        try? fileManager.removeItem(at: directoryURL.appendingPathComponent(Self.clearedFileName(sessionId)))
        if case let .present(record) = readClearedFile(Self.clearedFileName), record.sessionId == sessionId {
            try? fileManager.removeItem(at: directoryURL.appendingPathComponent(Self.clearedFileName))
        }
    }

    func writeCleared(sessionId: String, clearedAt: TimeInterval) throws {
        let record = SuspendedExpiryClearedRecord(
            version: SuspendedExpiryActivity.recordSchemaVersion,
            sessionId: sessionId,
            clearedAt: clearedAt
        )
        try write(record, fileName: Self.clearedFileName(sessionId))
    }

    func readCleared() -> SuspendedExpiryClearedRecord? {
        guard case let .present(record) = readClearedResult() else { return nil }
        return record
    }

    func readCleared(sessionId: String) -> SuspendedExpiryClearedRecord? {
        for name in [Self.clearedFileName(sessionId), Self.clearedFileName] {
            if case let .present(record) = readClearedFile(name), record.sessionId == sessionId { return record }
        }
        return nil
    }

    func readClearedResult(preferredSessionId: String? = nil) -> SuspendedExpiryClearedRead {
        do {
            if let preferredSessionId,
               case let .present(legacy) = readClearedFile(Self.clearedFileName),
               legacy.sessionId == preferredSessionId { return .present(legacy) }
            let names = try fileManager.contentsOfDirectory(atPath: directoryURL.path)
                .filter { $0.hasPrefix("cleared-") && $0.hasSuffix(".json") }.sorted()
            let preferred = preferredSessionId.map { Self.clearedFileName($0) }
            let ordered = preferred.map { [$0] + names.filter { $0 != preferred } } ?? names
            for name in ordered {
                let result = readClearedFile(name)
                if case .absent = result { continue }
                return result
            }
            return .absent
        } catch let error as CocoaError where error.code == .fileNoSuchFile || error.code == .fileReadNoSuchFile {
            return .absent
        } catch {
            return .failed
        }
    }

    private static func clearedFileName(_ sessionId: String) -> String {
        let digest = SHA256.hash(data: Data(sessionId.utf8)).map { String(format: "%02x", $0) }.joined()
        return "cleared-\(digest)-v1.json"
    }

    private func readClearedFile(_ name: String) -> SuspendedExpiryClearedRead {
        let url = directoryURL.appendingPathComponent(name)
        do {
            let attributes = try fileManager.attributesOfItem(atPath: url.path)
            guard let size = attributes[.size] as? NSNumber,
                  size.intValue <= Self.maximumFileBytes else { return .failed }
            let data = try Data(contentsOf: url)
            let record = try JSONDecoder().decode(SuspendedExpiryClearedRecord.self, from: data)
            guard record.version == SuspendedExpiryActivity.recordSchemaVersion,
                  Self.isValidSessionId(record.sessionId) else { return .failed }
            return .present(record)
        } catch let error as CocoaError where error.code == .fileNoSuchFile || error.code == .fileReadNoSuchFile {
            return .absent
        } catch {
            return .failed
        }
    }

    private func write<T: Encodable>(_ record: T, fileName: String) throws {
        try fileManager.createDirectory(
            at: directoryURL,
            withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication]
        )
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var mutableDirectory = directoryURL
        try mutableDirectory.setResourceValues(values)
        let data = try JSONEncoder().encode(record)
        try data.write(to: directoryURL.appendingPathComponent(fileName), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    private func boundedContents(of fileName: String) -> Data? {
        let url = directoryURL.appendingPathComponent(fileName)
        guard let attributes = try? fileManager.attributesOfItem(atPath: url.path),
              let size = attributes[.size] as? NSNumber,
              size.intValue <= Self.maximumFileBytes,
              let data = try? Data(contentsOf: url)
        else {
            return nil
        }
        return data
    }
}

extension SuspendedExpiryRecordStore {
    static func isValidSessionId(_ sessionId: String) -> Bool {
        !sessionId.isEmpty && sessionId.count <= maximumSessionIdCharacters
    }
}

/// Testable expiry-clear logic shared by the monitor extension and the test
/// bundle. Clears only the injected named store, never touches any other
/// store, and records the clear only when a pending schedule attributes it to
/// a session. Logs nothing.
enum SuspendedExpiryClear {
    static func handleIntervalEnd(
        activity: DeviceActivityName,
        store: SuspendedExpirySettingsStore,
        records: SuspendedExpiryRecordStore,
        now: () -> Date = Date.init
    ) {
        guard activity == SuspendedExpiryActivity.name else {
            return
        }
        store.clearOwnedSettings()
        guard let pending = records.readPending() else {
            return
        }
        records.removePending()
        try? records.writeCleared(sessionId: pending.sessionId, clearedAt: now().timeIntervalSince1970)
    }
}
