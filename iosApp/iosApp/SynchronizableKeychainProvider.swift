import CloudKit
import CryptoKit
import Foundation
import PosatoShared
import Security

enum KeychainAccountResolution: Equatable {
    case available(String)
    case unavailable
    case restricted
    case undetermined
}

protocol KeychainAccountSource {
    func currentRecordName() -> KeychainAccountResolution
}

protocol KeychainSecItemBackend {
    func add(_ attributes: [String: Any]) -> OSStatus
    func copyMatching(_ query: [String: Any]) -> (OSStatus, AnyObject?)
    func delete(_ query: [String: Any]) -> OSStatus
}

final class CloudKitAccountSource: KeychainAccountSource {
    private let containerIdentifier: String
    private let timeout: TimeInterval

    init(containerIdentifier: String = "iCloud.app.posato.sync", timeout: TimeInterval = 10) {
        self.containerIdentifier = containerIdentifier
        self.timeout = timeout
    }

    func currentRecordName() -> KeychainAccountResolution {
        let container = CKContainer(identifier: containerIdentifier)
        switch accountStatus(container: container) {
        case .available:
            guard let recordID = userRecordID(container: container) else {
                return .undetermined
            }
            return .available(recordID.recordName)
        case .noAccount:
            return .unavailable
        case .restricted:
            return .restricted
        case .couldNotDetermine, nil:
            return .undetermined
        @unknown default:
            return .undetermined
        }
    }

    private func accountStatus(container: CKContainer) -> CKAccountStatus? {
        let box = CloudKitBox<CKAccountStatus>()
        let semaphore = DispatchSemaphore(value: 0)
        container.accountStatus { status, error in
            if error == nil {
                box.set(status)
            }
            semaphore.signal()
        }
        guard semaphore.wait(timeout: .now() + timeout) == .success else {
            return nil
        }
        return box.get()
    }

    private func userRecordID(container: CKContainer) -> CKRecord.ID? {
        let box = CloudKitBox<CKRecord.ID>()
        let semaphore = DispatchSemaphore(value: 0)
        container.fetchUserRecordID { recordID, error in
            if error == nil, let recordID = recordID {
                box.set(recordID)
            }
            semaphore.signal()
        }
        guard semaphore.wait(timeout: .now() + timeout) == .success else {
            return nil
        }
        return box.get()
    }
}

private final class AccountChangeFlag: @unchecked Sendable {
    private let lock = NSLock()
    private var marked = false

    func mark() {
        lock.lock()
        defer {
            lock.unlock()
        }
        marked = true
    }

    var isMarked: Bool {
        lock.lock()
        defer {
            lock.unlock()
        }
        return marked
    }
}

private final class CloudKitBox<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Value?

    func set(_ value: Value) {
        lock.lock()
        defer {
            lock.unlock()
        }
        if self.value == nil {
            self.value = value
        }
    }

    func get() -> Value? {
        lock.lock()
        defer {
            lock.unlock()
        }
        return value
    }
}

final class SystemSecItemBackend: KeychainSecItemBackend {
    func add(_ attributes: [String: Any]) -> OSStatus {
        SecItemAdd(attributes as CFDictionary, nil)
    }

    func copyMatching(_ query: [String: Any]) -> (OSStatus, AnyObject?) {
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        return (status, result)
    }

    func delete(_ query: [String: Any]) -> OSStatus {
        SecItemDelete(query as CFDictionary)
    }
}

final class SynchronizableKeychainProvider: IosKeychainProvider {
    static let service = "app.posato.sync.workspace-key.v1"
    static let itemLength = 84
    static let teamKey = "PosatoDevelopmentTeam"
    static let accessGroupSuffix = "app.posato.sync"

    private static let bindingDomainPrefix = "iCloud.app.posato.sync|account-binding|v1"

    private let accountSource: KeychainAccountSource
    private let backend: KeychainSecItemBackend
    private let accessGroup: String

    init(accountSource: KeychainAccountSource, backend: KeychainSecItemBackend, accessGroup: String) {
        self.accountSource = accountSource
        self.backend = backend
        self.accessGroup = accessGroup
    }

    convenience init?(accountSource: KeychainAccountSource, backend: KeychainSecItemBackend, teamIdentifier: String?) {
        guard let accessGroup = Self.accessGroup(teamIdentifier: teamIdentifier) else {
            return nil
        }
        self.init(accountSource: accountSource, backend: backend, accessGroup: accessGroup)
    }

    convenience init?(accountSource: KeychainAccountSource, backend: KeychainSecItemBackend, bundle: Bundle = .main) {
        self.init(
            accountSource: accountSource,
            backend: backend,
            teamIdentifier: bundle.object(forInfoDictionaryKey: Self.teamKey) as? String
        )
    }

    static func accessGroup(teamIdentifier: String?) -> String? {
        guard let teamIdentifier = teamIdentifier, !teamIdentifier.isEmpty else {
            return nil
        }
        return teamIdentifier + "." + accessGroupSuffix
    }

    static func accessGroup(bundle: Bundle, key: String = teamKey) -> String? {
        accessGroup(teamIdentifier: bundle.object(forInfoDictionaryKey: key) as? String)
    }

    static func deriveBinding(recordName: String) -> Data {
        var derivation = Data(bindingDomainPrefix.utf8)
        derivation.append(0x00)
        derivation.append(contentsOf: recordName.utf8)
        return Data(SHA256.hash(data: derivation))
    }

    static func isCanonicalAccount(_ account: String) -> Bool {
        guard account.count == 36 else {
            return false
        }
        for (index, character) in account.enumerated() {
            if index == 8 || index == 13 || index == 18 || index == 23 {
                guard character == "-" else {
                    return false
                }
            } else {
                guard "0123456789abcdef".contains(character) else {
                    return false
                }
            }
        }
        return true
    }

    func resolveBinding() -> IosKeychainBinding {
        switch accountSource.currentRecordName() {
        case .available(let recordName):
            return IosKeychainBinding(status: .available, value: Self.deriveBinding(recordName: recordName))
        case .unavailable:
            return IosKeychainBinding(status: .unavailable, value: nil)
        case .restricted:
            return IosKeychainBinding(status: .restricted, value: nil)
        case .undetermined:
            return IosKeychainBinding(status: .undetermined, value: nil)
        }
    }

    func readItem(binding: Data, account: String) -> IosKeychainItemRead {
        switch preflight(expectedBinding: binding) {
        case .proceed:
            break
        case .retryable:
            return IosKeychainItemRead(status: .retryable, value: nil)
        case .accountChanged:
            return IosKeychainItemRead(status: .accountchanged, value: nil)
        }
        guard Self.isCanonicalAccount(account) else {
            return IosKeychainItemRead(status: .integrityfailure, value: nil)
        }
        let (flag, observer) = observingAccountChange()
        let result = performRead(account: account, expectedBinding: binding, changeFlag: flag)
        NotificationCenter.default.removeObserver(observer)
        guard !flag.isMarked else {
            return IosKeychainItemRead(status: .unknownoutcome, value: nil)
        }
        return result
    }

    func createItem(binding: Data, account: String, value: Data) -> IosKeychainCreateStatus {
        switch preflight(expectedBinding: binding) {
        case .proceed:
            break
        case .retryable:
            return .retryable
        case .accountChanged:
            return .accountchanged
        }
        guard Self.isCanonicalAccount(account), value.count == Self.itemLength else {
            return .integrityfailure
        }
        let (flag, observer) = observingAccountChange()
        let result = performCreate(account: account, value: value, expectedBinding: binding)
        NotificationCenter.default.removeObserver(observer)
        guard !flag.isMarked else {
            return .unknownoutcome
        }
        return result
    }

    func deleteItemAndVerifyAbsent(binding: Data, account: String) -> IosKeychainDeleteStatus {
        switch preflight(expectedBinding: binding) {
        case .proceed:
            break
        case .retryable:
            return .retryable
        case .accountChanged:
            return .accountchanged
        }
        guard Self.isCanonicalAccount(account) else {
            return .integrityfailure
        }
        let (flag, observer) = observingAccountChange()
        let result = performDelete(account: account, expectedBinding: binding)
        NotificationCenter.default.removeObserver(observer)
        guard !flag.isMarked else {
            return .unknownoutcome
        }
        return result
    }

    private func performRead(account: String, expectedBinding: Data, changeFlag: AccountChangeFlag) -> IosKeychainItemRead {
        let (status, data) = backend.copyMatching(readQuery(account: account))
        switch status {
        case errSecSuccess:
            guard var observed = data as? Data else {
                return confirmRead(IosKeychainItemRead(status: .integrityfailure, value: nil), expectedBinding: expectedBinding)
            }
            defer {
                observed.resetBytes(in: 0..<observed.count)
            }
            guard observed.count == Self.itemLength else {
                return confirmRead(IosKeychainItemRead(status: .integrityfailure, value: nil), expectedBinding: expectedBinding)
            }
            guard postflightMatches(expectedBinding), !changeFlag.isMarked else {
                return IosKeychainItemRead(status: .unknownoutcome, value: nil)
            }
            return IosKeychainItemRead(status: .found, value: NSData(data: observed) as Data)
        case errSecItemNotFound:
            return confirmRead(IosKeychainItemRead(status: .missing, value: nil), expectedBinding: expectedBinding)
        default:
            return IosKeychainItemRead(status: .retryable, value: nil)
        }
    }

    private func performCreate(account: String, value: Data, expectedBinding: Data) -> IosKeychainCreateStatus {
        let (readStatus, existing) = backend.copyMatching(readQuery(account: account))
        if readStatus == errSecSuccess {
            guard let existingData = existing as? Data, existingData.count == Self.itemLength else {
                return confirmCreate(.integrityfailure, expectedBinding: expectedBinding)
            }
            if existingData == value {
                return confirmCreate(.alreadyexists, expectedBinding: expectedBinding)
            }
            return confirmCreate(.integrityfailure, expectedBinding: expectedBinding)
        }
        guard readStatus == errSecItemNotFound else {
            return .retryable
        }
        let addStatus = backend.add(addQuery(account: account, value: value))
        if addStatus == errSecDuplicateItem {
            return reconcileDuplicate(account: account, value: value, expectedBinding: expectedBinding)
        }
        guard addStatus == errSecSuccess else {
            return .retryable
        }
        return confirmCreate(.created, expectedBinding: expectedBinding)
    }

    private func performDelete(account: String, expectedBinding: Data) -> IosKeychainDeleteStatus {
        let deleteStatus = backend.delete(exactQuery(account: account))
        guard deleteStatus == errSecSuccess || deleteStatus == errSecItemNotFound else {
            return .retryable
        }
        let (readStatus, existing) = backend.copyMatching(readQuery(account: account))
        if readStatus == errSecItemNotFound {
            return confirmDelete(.deletedandabsent, expectedBinding: expectedBinding)
        }
        if readStatus == errSecSuccess, existing == nil {
            return confirmDelete(.deletedandabsent, expectedBinding: expectedBinding)
        }
        guard readStatus == errSecSuccess else {
            return .retryable
        }
        return confirmDelete(.integrityfailure, expectedBinding: expectedBinding)
    }

    private enum GateDecision {
        case proceed
        case retryable
        case accountChanged
    }

    private func preflight(expectedBinding: Data) -> GateDecision {
        switch accountSource.currentRecordName() {
        case .available(let recordName):
            return Self.deriveBinding(recordName: recordName) == expectedBinding ? .proceed : .accountChanged
        case .unavailable, .restricted, .undetermined:
            return .retryable
        }
    }

    private func postflightMatches(_ expectedBinding: Data) -> Bool {
        guard case .available(let recordName) = accountSource.currentRecordName() else {
            return false
        }
        return Self.deriveBinding(recordName: recordName) == expectedBinding
    }

    private func observingAccountChange() -> (flag: AccountChangeFlag, observer: NSObjectProtocol) {
        let flag = AccountChangeFlag()
        let observer = NotificationCenter.default.addObserver(forName: .CKAccountChanged, object: nil, queue: nil) { _ in
            flag.mark()
        }
        return (flag, observer)
    }

    private func confirmRead(_ read: IosKeychainItemRead, expectedBinding: Data) -> IosKeychainItemRead {
        guard postflightMatches(expectedBinding) else {
            return IosKeychainItemRead(status: .unknownoutcome, value: nil)
        }
        return read
    }

    private func confirmCreate(_ status: IosKeychainCreateStatus, expectedBinding: Data) -> IosKeychainCreateStatus {
        guard postflightMatches(expectedBinding) else {
            return .unknownoutcome
        }
        return status
    }

    private func confirmDelete(_ status: IosKeychainDeleteStatus, expectedBinding: Data) -> IosKeychainDeleteStatus {
        guard postflightMatches(expectedBinding) else {
            return .unknownoutcome
        }
        return status
    }

    private func reconcileDuplicate(account: String, value: Data, expectedBinding: Data) -> IosKeychainCreateStatus {
        let (readStatus, existing) = backend.copyMatching(readQuery(account: account))
        guard readStatus == errSecSuccess, let existingData = existing as? Data, existingData.count == Self.itemLength else {
            return .retryable
        }
        if existingData == value {
            return confirmCreate(.alreadyexists, expectedBinding: expectedBinding)
        }
        return confirmCreate(.integrityfailure, expectedBinding: expectedBinding)
    }

    private func exactQuery(account: String) -> [String: Any] {
        return [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.service,
            kSecAttrAccount as String: account,
            kSecAttrAccessGroup as String: accessGroup,
            kSecAttrSynchronizable as String: kCFBooleanTrue as Any,
        ]
    }

    private func readQuery(account: String) -> [String: Any] {
        var query = exactQuery(account: account)
        query[kSecReturnData as String] = kCFBooleanTrue
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        return query
    }

    private func addQuery(account: String, value: Data) -> [String: Any] {
        var query = exactQuery(account: account)
        query[kSecValueData as String] = value
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        return query
    }
}
