import Foundation
import PosatoShared
import Security
import XCTest

@testable import Posato

final class SynchronizableKeychainProviderTests: XCTestCase {
    private let recordName = "_sync005testrecord"
    private let account = "123e4567-e89b-42d3-a456-426614174000"
    private let accessGroup = "TESTTEAM01.app.posato.sync"

    private func binding(recordName: String? = nil) -> Data {
        SynchronizableKeychainProvider.deriveBinding(recordName: recordName ?? self.recordName)
    }

    private func makeProvider(
        source: FakeKeychainAccountSource? = nil,
        backend: KeychainSecItemBackend? = nil
    ) -> (SynchronizableKeychainProvider, FakeKeychainAccountSource, FakeSecItemBackend) {
        let accountSource = source ?? FakeKeychainAccountSource(resolutions: [.available(recordName)])
        let secItem = (backend as? FakeSecItemBackend) ?? FakeSecItemBackend()
        let provider = SynchronizableKeychainProvider(accountSource: accountSource, backend: backend ?? secItem, accessGroup: accessGroup)
        return (provider, accountSource, secItem)
    }

    private func itemValue(byte: UInt8 = 0x07) -> Data {
        Data(repeating: byte, count: SynchronizableKeychainProvider.itemLength)
    }

    func testAccessGroupCompositionRejectsMissingTeam() {
        XCTAssertNil(SynchronizableKeychainProvider.accessGroup(teamIdentifier: nil))
        XCTAssertNil(SynchronizableKeychainProvider.accessGroup(teamIdentifier: ""))
        XCTAssertEqual(SynchronizableKeychainProvider.accessGroup(teamIdentifier: "TEAM1"), "TEAM1.app.posato.sync")
        XCTAssertNil(SynchronizableKeychainProvider.accessGroup(bundle: Bundle.main, key: "PosatoAbsentTestKey"))
    }

    func testProductionInitFailsClosedWithoutTeam() {
        XCTAssertNil(SynchronizableKeychainProvider(accountSource: FakeKeychainAccountSource(), backend: FakeSecItemBackend(), teamIdentifier: nil))
        XCTAssertNil(SynchronizableKeychainProvider(accountSource: FakeKeychainAccountSource(), backend: FakeSecItemBackend(), teamIdentifier: ""))
        XCTAssertNotNil(
            SynchronizableKeychainProvider(accountSource: FakeKeychainAccountSource(), backend: FakeSecItemBackend(), teamIdentifier: "TEAM1")
        )
    }

    func testBindingDerivationMatchesIndependentGoldenVector() {
        let derived = SynchronizableKeychainProvider.deriveBinding(recordName: "_testRecord123")

        XCTAssertEqual(derived.count, 32)
        XCTAssertEqual(
            derived.keychainHex,
            "549eb9dcd174f04b01dd10ebd830450a7d5137a1dfd38954f416ece0e8c43581"
        )
        XCTAssertNotEqual(derived, SynchronizableKeychainProvider.deriveBinding(recordName: "_otherRecord999"))
        XCTAssertEqual(derived, SynchronizableKeychainProvider.deriveBinding(recordName: "_testRecord123"))
    }

    func testCanonicalAccountAcceptsOnlyLowercaseUuidText() {
        XCTAssertTrue(SynchronizableKeychainProvider.isCanonicalAccount(account))
        XCTAssertFalse(SynchronizableKeychainProvider.isCanonicalAccount(account.uppercased()))
        XCTAssertFalse(SynchronizableKeychainProvider.isCanonicalAccount("123e4567e89b42d3a456426614174000"))
        XCTAssertFalse(SynchronizableKeychainProvider.isCanonicalAccount("123e4567-e89b-42d3-a456-42661417400"))
        XCTAssertFalse(SynchronizableKeychainProvider.isCanonicalAccount(""))
    }

    func testSelectorCarriesExactFormatOneAttributes() {
        let backend = FakeSecItemBackend()
        let (provider, _, _) = makeProvider(backend: backend)
        let value = itemValue()

        backend.copyHandler = { _ in (errSecItemNotFound, nil) }
        XCTAssertTrue(provider.createItem(binding: binding(), account: account, value: value) == .created)
        backend.copyHandler = { _ in (errSecSuccess, Data(value) as AnyObject) }
        XCTAssertTrue(provider.readItem(binding: binding(), account: account).status == .found)
        backend.copyHandler = { _ in (errSecItemNotFound, nil) }
        XCTAssertTrue(provider.deleteItemAndVerifyAbsent(binding: binding(), account: account) == .deletedandabsent)

        let added = backend.added.last ?? [:]
        XCTAssertTrue(added[kSecClass as String] as? String == (kSecClassGenericPassword as String))
        XCTAssertEqual(added[kSecAttrService as String] as? String, "app.posato.sync.workspace-key.v1")
        XCTAssertEqual(added[kSecAttrAccount as String] as? String, account)
        XCTAssertEqual(added[kSecAttrAccessGroup as String] as? String, accessGroup)
        XCTAssertEqual(added[kSecAttrSynchronizable as String] as? Bool, true)
        XCTAssertTrue(added[kSecAttrAccessible as String] as? String == (kSecAttrAccessibleAfterFirstUnlock as String))
        XCTAssertEqual(added[kSecValueData as String] as? Data, value)

        let readQuery = backend.queried.first ?? [:]
        XCTAssertEqual(readQuery[kSecReturnData as String] as? Bool, true)
        XCTAssertTrue(readQuery[kSecMatchLimit as String] as? String == (kSecMatchLimitOne as String))
        XCTAssertNil(readQuery[kSecValueData as String])

        let deleteQuery = backend.deleted.first ?? [:]
        XCTAssertEqual(deleteQuery[kSecAttrAccount as String] as? String, account)
        XCTAssertNil(deleteQuery[kSecReturnData as String])
        XCTAssertNil(deleteQuery[kSecValueData as String])
    }

    func testCreateGatesValueLengthBeforeAnyQuery() {
        for count in [0, SynchronizableKeychainProvider.itemLength - 1, SynchronizableKeychainProvider.itemLength + 1] {
            let (provider, _, backend) = makeProvider()

            XCTAssertTrue(provider.createItem(binding: binding(), account: account, value: Data(repeating: 0x01, count: count)) == .integrityfailure)
            XCTAssertTrue(backend.added.isEmpty)
            XCTAssertTrue(backend.queried.isEmpty)
        }
    }

    func testOperationsRejectInvalidAccountWithoutQuery() {
        let (provider, _, backend) = makeProvider()

        XCTAssertTrue(provider.createItem(binding: binding(), account: "NOT-A-UUID", value: itemValue()) == .integrityfailure)
        XCTAssertTrue(provider.readItem(binding: binding(), account: "NOT-A-UUID").status == .integrityfailure)
        XCTAssertTrue(provider.deleteItemAndVerifyAbsent(binding: binding(), account: "NOT-A-UUID") == .integrityfailure)
        XCTAssertTrue(backend.added.isEmpty)
        XCTAssertTrue(backend.queried.isEmpty)
        XCTAssertTrue(backend.deleted.isEmpty)
    }

    func testMemoryRoundtripKeepsIdenticalRejectsDifferentAndDeletes() {
        let memory = MemorySecItemBackend()
        let source = FakeKeychainAccountSource(resolutions: [.available(recordName)])
        let provider = SynchronizableKeychainProvider(accountSource: source, backend: memory, accessGroup: accessGroup)
        let expected = binding()
        let first = itemValue(byte: 0x07)
        let second = itemValue(byte: 0x08)

        XCTAssertTrue(provider.createItem(binding: expected, account: account, value: first) == .created)
        XCTAssertEqual(provider.readItem(binding: expected, account: account).value, first)
        XCTAssertTrue(provider.createItem(binding: expected, account: account, value: first) == .alreadyexists)
        XCTAssertTrue(provider.createItem(binding: expected, account: account, value: second) == .integrityfailure)
        XCTAssertEqual(memory.stored[account], first)
        XCTAssertTrue(provider.deleteItemAndVerifyAbsent(binding: expected, account: account) == .deletedandabsent)
        XCTAssertTrue(provider.readItem(binding: expected, account: account).status == .missing)
        XCTAssertTrue(provider.deleteItemAndVerifyAbsent(binding: expected, account: account) == .deletedandabsent)
    }

    func testDuplicateReconcilesByExactReread() {
        let expected = binding()
        let value = itemValue()

        let identical = FakeSecItemBackend()
        identical.copyHandler = { _ in (errSecItemNotFound, nil) }
        identical.addHandler = { _ in errSecDuplicateItem }
        let identicalProvider = makeProvider(backend: identical).0
        identical.copyHandler = { _ in (errSecSuccess, Data(value) as AnyObject) }
        XCTAssertTrue(identicalProvider.createItem(binding: expected, account: account, value: value) == .alreadyexists)

        let different = FakeSecItemBackend()
        different.copyHandler = { _ in (errSecItemNotFound, nil) }
        different.addHandler = { _ in errSecDuplicateItem }
        let differentProvider = makeProvider(backend: different).0
        different.copyHandler = { _ in (errSecSuccess, Data(self.itemValue(byte: 0x08)) as AnyObject) }
        XCTAssertTrue(differentProvider.createItem(binding: expected, account: account, value: value) == .integrityfailure)

        let vanished = FakeSecItemBackend()
        vanished.copyHandler = { _ in (errSecItemNotFound, nil) }
        vanished.addHandler = { _ in errSecDuplicateItem }
        let vanishedProvider = makeProvider(backend: vanished).0
        XCTAssertTrue(vanishedProvider.createItem(binding: expected, account: account, value: value) == .retryable)

        let failed = FakeSecItemBackend()
        failed.copyHandler = { _ in (errSecItemNotFound, nil) }
        failed.addHandler = { _ in errSecDuplicateItem }
        let failedProvider = makeProvider(backend: failed).0
        failed.copyHandler = { _ in (errSecAuthFailed, nil) }
        XCTAssertTrue(failedProvider.createItem(binding: expected, account: account, value: value) == .retryable)
    }

    func testReadMapsProviderOutcomes() {
        let expected = binding()
        let value = itemValue()
        let outcomes: [(OSStatus, AnyObject?)] = [
            (errSecItemNotFound, nil),
            (errSecSuccess, Data(value) as AnyObject),
            (errSecSuccess, Data(repeating: 0x01, count: 3) as AnyObject),
            (errSecSuccess, "text" as AnyObject),
            (errSecAuthFailed, nil),
        ]
        let statuses: [IosKeychainReadStatus] = [.missing, .found, .integrityfailure, .integrityfailure, .retryable]
        for (outcome, status) in zip(outcomes, statuses) {
            let backend = FakeSecItemBackend()
            backend.copyHandler = { _ in outcome }
            let (provider, _, _) = makeProvider(backend: backend)
            let read = provider.readItem(binding: expected, account: account)

            XCTAssertTrue(read.status == status)
            if status == .found {
                XCTAssertEqual(read.value, value)
            } else {
                XCTAssertNil(read.value)
            }
        }
    }

    func testDeleteVerifiesAbsenceIndependently() {
        let expected = binding()

        let clean = FakeSecItemBackend()
        clean.deleteHandler = { _ in errSecSuccess }
        clean.copyHandler = { _ in (errSecItemNotFound, nil) }
        XCTAssertTrue(makeProvider(backend: clean).0.deleteItemAndVerifyAbsent(binding: expected, account: account) == .deletedandabsent)

        let idempotent = FakeSecItemBackend()
        idempotent.deleteHandler = { _ in errSecItemNotFound }
        idempotent.copyHandler = { _ in (errSecItemNotFound, nil) }
        XCTAssertTrue(makeProvider(backend: idempotent).0.deleteItemAndVerifyAbsent(binding: expected, account: account) == .deletedandabsent)

        let lingering = FakeSecItemBackend()
        lingering.deleteHandler = { _ in errSecSuccess }
        lingering.copyHandler = { _ in (errSecSuccess, Data(self.itemValue()) as AnyObject) }
        XCTAssertTrue(makeProvider(backend: lingering).0.deleteItemAndVerifyAbsent(binding: expected, account: account) == .integrityfailure)

        let unreadable = FakeSecItemBackend()
        unreadable.deleteHandler = { _ in errSecSuccess }
        unreadable.copyHandler = { _ in (errSecAuthFailed, nil) }
        XCTAssertTrue(makeProvider(backend: unreadable).0.deleteItemAndVerifyAbsent(binding: expected, account: account) == .retryable)

        let refused = FakeSecItemBackend()
        refused.deleteHandler = { _ in errSecAuthFailed }
        let (refusedProvider, _, refusedBackend) = makeProvider(backend: refused)
        XCTAssertTrue(refusedProvider.deleteItemAndVerifyAbsent(binding: expected, account: account) == .retryable)
        XCTAssertTrue(refusedBackend.queried.isEmpty)
    }

    func testUnavailablePreflightInvokesNoQuery() {
        for resolution in [KeychainAccountResolution.unavailable, .restricted, .undetermined] {
            let source = FakeKeychainAccountSource(resolutions: [resolution])
            let backend = FakeSecItemBackend()
            let provider = SynchronizableKeychainProvider(accountSource: source, backend: backend, accessGroup: accessGroup)

            XCTAssertTrue(provider.readItem(binding: binding(), account: account).status == .retryable)
            XCTAssertTrue(provider.createItem(binding: binding(), account: account, value: itemValue()) == .retryable)
            XCTAssertTrue(provider.deleteItemAndVerifyAbsent(binding: binding(), account: account) == .retryable)
            XCTAssertTrue(backend.queried.isEmpty)
            XCTAssertTrue(backend.added.isEmpty)
            XCTAssertTrue(backend.deleted.isEmpty)
        }
    }

    func testDifferentAccountPreflightInvokesNoQuery() {
        let source = FakeKeychainAccountSource(resolutions: [.available("_otherAccount999")])
        let backend = FakeSecItemBackend()
        let provider = SynchronizableKeychainProvider(accountSource: source, backend: backend, accessGroup: accessGroup)

        XCTAssertTrue(provider.readItem(binding: binding(), account: account).status == .accountchanged)
        XCTAssertTrue(provider.createItem(binding: binding(), account: account, value: itemValue()) == .accountchanged)
        XCTAssertTrue(provider.deleteItemAndVerifyAbsent(binding: binding(), account: account) == .accountchanged)
        XCTAssertTrue(backend.queried.isEmpty)
        XCTAssertTrue(backend.added.isEmpty)
        XCTAssertTrue(backend.deleted.isEmpty)
    }

    func testPostflightMismatchDiscardsBytesAndConfirmsNothing() {
        let readSource = FakeKeychainAccountSource(resolutions: [.available(recordName), .available("_otherAccount999")])
        let readBackend = FakeSecItemBackend()
        readBackend.copyHandler = { _ in (errSecSuccess, Data(self.itemValue()) as AnyObject) }
        let readProvider = SynchronizableKeychainProvider(accountSource: readSource, backend: readBackend, accessGroup: accessGroup)
        let read = readProvider.readItem(binding: binding(), account: account)

        XCTAssertTrue(read.status == .unknownoutcome)
        XCTAssertNil(read.value)

        let createSource = FakeKeychainAccountSource(resolutions: [.available(recordName), .available("_otherAccount999")])
        let createProvider = makeProvider(source: createSource).0

        XCTAssertTrue(createProvider.createItem(binding: binding(), account: account, value: itemValue()) == .unknownoutcome)

        let deleteSource = FakeKeychainAccountSource(resolutions: [.available(recordName), .unavailable])
        let deleteProvider = makeProvider(source: deleteSource).0

        XCTAssertTrue(deleteProvider.deleteItemAndVerifyAbsent(binding: binding(), account: account) == .unknownoutcome)
    }

    func testReadBytesAreNotRetainedAcrossCalls() {
        let backend = FakeSecItemBackend()
        let (provider, _, _) = makeProvider(backend: backend)
        let value = itemValue()

        backend.copyHandler = { _ in (errSecSuccess, Data(value) as AnyObject) }
        XCTAssertEqual(provider.readItem(binding: binding(), account: account).value, value)

        backend.copyHandler = { _ in (errSecAuthFailed, nil) }
        let retry = provider.readItem(binding: binding(), account: account)

        XCTAssertTrue(retry.status == .retryable)
        XCTAssertNil(retry.value)
    }

    func testAccountChangeSignalDuringOperationFailsUnknown() {
        let source = FakeKeychainAccountSource(resolutions: [.available(recordName)])
        let backend = FakeSecItemBackend()
        backend.copyHandler = { _ in
            NotificationCenter.default.post(name: .CKAccountChanged, object: nil)
            return (errSecSuccess, Data(self.itemValue()) as AnyObject)
        }
        let provider = SynchronizableKeychainProvider(accountSource: source, backend: backend, accessGroup: accessGroup)

        XCTAssertTrue(provider.readItem(binding: binding(), account: account).status == .unknownoutcome)
    }

    func testResolveBindingPassesAccountOutcomesThrough() {
        for (resolution, status) in [
            (KeychainAccountResolution.available(recordName), IosKeychainBindingStatus.available),
            (.unavailable, .unavailable),
            (.restricted, .restricted),
            (.undetermined, .undetermined),
        ] as [(KeychainAccountResolution, IosKeychainBindingStatus)] {
            let source = FakeKeychainAccountSource(resolutions: [resolution])
            let provider = SynchronizableKeychainProvider(accountSource: source, backend: FakeSecItemBackend(), accessGroup: accessGroup)
            let result = provider.resolveBinding()

            XCTAssertTrue(result.status == status)
            if status == .available {
                XCTAssertEqual(result.value, binding())
            } else {
                XCTAssertNil(result.value)
            }
        }
    }

    func testNewCarriersExposeNoSecretDescription() {
        XCTAssertEqual(IosKeychainBinding(status: .available, value: nil).description(), "IosKeychainBinding(redacted)")
        XCTAssertEqual(IosKeychainItemRead(status: .found, value: nil).description(), "IosKeychainItemRead(redacted)")
    }

#if !targetEnvironment(simulator)
    private var deviceAccount: String?
    private var deviceBinding: Data?

    override func tearDown() {
        if let account = deviceAccount, let binding = deviceBinding {
            deviceAccount = nil
            deviceBinding = nil
            if let provider = try? makeDeviceProvider() {
                _ = provider.deleteItemAndVerifyAbsent(binding: binding, account: account)
            }
        }
        super.tearDown()
    }

    func testDeviceWorkspaceKeyCycle() throws {
        continueAfterFailure = false
        let provider = try makeDeviceProvider()
        let resolution = provider.resolveBinding()

        XCTAssertTrue(resolution.status == .available)
        let binding = try XCTUnwrap(resolution.value)
        XCTAssertEqual(binding.count, 32)

        let account = UUID().uuidString.lowercased()
        deviceAccount = account
        deviceBinding = binding
        let first = try randomBytes(count: SynchronizableKeychainProvider.itemLength)
        var second = try randomBytes(count: SynchronizableKeychainProvider.itemLength)
        if second == first {
            second[0] ^= 0x01
        }

        XCTAssertTrue(provider.createItem(binding: binding, account: account, value: first) == .created)
        XCTAssertEqual(provider.readItem(binding: binding, account: account).value, first)
        XCTAssertTrue(provider.createItem(binding: binding, account: account, value: first) == .alreadyexists)
        XCTAssertTrue(provider.createItem(binding: binding, account: account, value: second) == .integrityfailure)
        XCTAssertEqual(provider.readItem(binding: binding, account: account).value, first)
        XCTAssertTrue(provider.deleteItemAndVerifyAbsent(binding: binding, account: account) == .deletedandabsent)
        XCTAssertTrue(provider.readItem(binding: binding, account: account).status == .missing)

        deviceAccount = nil
        deviceBinding = nil
    }

    private func makeDeviceProvider() throws -> SynchronizableKeychainProvider {
        let team = try XCTUnwrap(Bundle.main.object(forInfoDictionaryKey: SynchronizableKeychainProvider.teamKey) as? String)
        return try XCTUnwrap(
            SynchronizableKeychainProvider(accountSource: CloudKitAccountSource(), backend: SystemSecItemBackend(), teamIdentifier: team)
        )
    }

    private func randomBytes(count: Int) throws -> Data {
        var bytes = Data(count: count)
        let status: OSStatus = bytes.withUnsafeMutableBytes { buffer in
            guard let baseAddress = buffer.baseAddress else {
                return errSecParam
            }
            return SecRandomCopyBytes(kSecRandomDefault, count, baseAddress)
        }
        XCTAssertEqual(status, errSecSuccess)
        return bytes
    }
#endif
}

final class FakeKeychainAccountSource: KeychainAccountSource {
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

final class FakeSecItemBackend: KeychainSecItemBackend {
    var addHandler: ([String: Any]) -> OSStatus = { _ in errSecSuccess }
    var copyHandler: ([String: Any]) -> (OSStatus, AnyObject?) = { _ in (errSecItemNotFound, nil) }
    var deleteHandler: ([String: Any]) -> OSStatus = { _ in errSecSuccess }
    private(set) var added: [[String: Any]] = []
    private(set) var queried: [[String: Any]] = []
    private(set) var deleted: [[String: Any]] = []

    func add(_ attributes: [String: Any]) -> OSStatus {
        added.append(attributes)
        return addHandler(attributes)
    }

    func copyMatching(_ query: [String: Any]) -> (OSStatus, AnyObject?) {
        queried.append(query)
        return copyHandler(query)
    }

    func delete(_ query: [String: Any]) -> OSStatus {
        deleted.append(query)
        return deleteHandler(query)
    }
}

final class MemorySecItemBackend: KeychainSecItemBackend {
    private(set) var stored: [String: Data] = [:]

    func add(_ attributes: [String: Any]) -> OSStatus {
        guard let account = attributes[kSecAttrAccount as String] as? String,
            let value = attributes[kSecValueData as String] as? Data
        else {
            return errSecParam
        }
        guard stored[account] == nil else {
            return errSecDuplicateItem
        }
        stored[account] = value
        return errSecSuccess
    }

    func copyMatching(_ query: [String: Any]) -> (OSStatus, AnyObject?) {
        guard let account = query[kSecAttrAccount as String] as? String else {
            return (errSecParam, nil)
        }
        guard let value = stored[account] else {
            return (errSecItemNotFound, nil)
        }
        return (errSecSuccess, Data(value) as AnyObject)
    }

    func delete(_ query: [String: Any]) -> OSStatus {
        guard let account = query[kSecAttrAccount as String] as? String else {
            return errSecParam
        }
        guard stored.removeValue(forKey: account) != nil else {
            return errSecItemNotFound
        }
        return errSecSuccess
    }
}

private extension Data {
    var keychainHex: String {
        return map { String(format: "%02x", $0) }.joined()
    }
}
