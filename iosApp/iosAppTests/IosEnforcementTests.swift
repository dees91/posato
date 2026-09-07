import FamilyControls
import ManagedSettings
import PosatoShared
import XCTest
@testable import Posato

final class FakeEnforcementSettingsStore: IosEnforcementSettingsStore {
    var storedFilter: WebContentSettings.FilterPolicy?
    var storedApplications: Set<ApplicationToken>?
    var writes = 0
    var clears = 0
    var lieOnRead = false
    var corruptApplicationsOnRead = false

    var blockedWebFilter: WebContentSettings.FilterPolicy? {
        get {
            lieOnRead ? WebContentSettings.FilterPolicy.none : storedFilter
        }
        set {
            writes += 1
            storedFilter = newValue
        }
    }

    var shieldedApplications: Set<ApplicationToken>? {
        get {
            corruptApplicationsOnRead ? Set() : storedApplications
        }
        set {
            writes += 1
            storedApplications = newValue
        }
    }

    func clearAll() {
        clears += 1
        lieOnRead = false
        storedFilter = nil
        storedApplications = nil
    }
}

final class IosEnforcementTests: XCTestCase {
    func testIncapableBuildAppliesNothingAndClearsNothing() throws {
        let store = FakeEnforcementSettingsStore()
        let enforcer = IosManagedSettingsEnforcer(
            storeFactory: { store },
            authorization: { .approved },
            isCapable: false,
            storedMappings: {
                XCTFail("Incapable apply must not open the selection store")
                return []
            }
        )

        XCTAssertEqual(try apply(domains: ["example.com"], enforcer: enforcer), .unavailable)
        XCTAssertEqual(try clear(enforcer: enforcer), .unavailable)
        XCTAssertEqual(store.writes, 0)
        XCTAssertEqual(store.clears, 0)
    }

    func testEmptyApplyRefusesWithoutWriting() throws {
        let store = FakeEnforcementSettingsStore()
        let enforcer = capableEnforcer(store: store)

        XCTAssertEqual(try apply(domains: [], enforcer: enforcer), .nothingToEnforce)
        XCTAssertEqual(store.writes, 0)
        XCTAssertEqual(store.clears, 0)
    }

    func testUnauthorizedApplyWritesNothing() throws {
        let expectations: [(EnforcementAuthorization, IosEnforcementOutcome)] = [
            (.notDetermined, .authorizationRequired),
            (.denied, .authorizationDenied),
            (.restricted, .restricted),
        ]

        for (authorization, outcome) in expectations {
            let store = FakeEnforcementSettingsStore()
            let enforcer = capableEnforcer(store: store, authorization: authorization)

            XCTAssertEqual(try apply(domains: ["example.com"], enforcer: enforcer), outcome, "authorization \(authorization)")
            XCTAssertEqual(store.writes, 0, "authorization \(authorization)")
        }
    }

    func testPlatformAuthorizationMapsToEnforcementAuthorization() {
        XCTAssertEqual(EnforcementAuthorization(status: .approved), .approved)
        XCTAssertEqual(EnforcementAuthorization(status: .notDetermined), .notDetermined)
        XCTAssertEqual(EnforcementAuthorization(status: .denied), .denied)
        XCTAssertNil(EnforcementAuthorization.approved.refusal)
    }

    func testUnknownIdentifierFailsBeforeAnyWrite() throws {
        let store = FakeEnforcementSettingsStore()
        let enforcer = capableEnforcer(store: store, storedMappings: [])

        XCTAssertEqual(
            try apply(domains: ["example.com"], mappingIds: [String(repeating: "a", count: 64)], enforcer: enforcer),
            .selectionMissing
        )
        XCTAssertEqual(store.writes, 0)
        XCTAssertNil(store.storedFilter)
        XCTAssertNil(store.storedApplications)
    }

    func testWebsitesOnlyApplySetsFilterAndLeavesApplicationsUnset() throws {
        let store = FakeEnforcementSettingsStore()
        let enforcer = capableEnforcer(store: store, storedMappings: [])

        XCTAssertEqual(try apply(domains: ["example.com"], enforcer: enforcer), .applied)
        XCTAssertEqual(store.storedFilter, .specific([WebDomain(domain: "example.com")]))
        XCTAssertNil(store.storedApplications)
    }

    func testVerifyMismatchRollsBackToAnEmptyOwnedStore() throws {
        for corruptApplications in [false, true] {
            let store = FakeEnforcementSettingsStore()
            store.lieOnRead = !corruptApplications
            store.corruptApplicationsOnRead = corruptApplications
            let enforcer = capableEnforcer(store: store, storedMappings: [])

            XCTAssertEqual(
                try apply(domains: ["example.com"], enforcer: enforcer),
                .platformFailure,
                "corruptApplications \(corruptApplications)"
            )
            XCTAssertEqual(store.clears, 1, "corruptApplications \(corruptApplications)")
            XCTAssertNil(store.storedFilter, "corruptApplications \(corruptApplications)")
            XCTAssertNil(store.storedApplications, "corruptApplications \(corruptApplications)")
        }
    }

    func testMigrationRemigratesAPrivateSelectionOverAnEmptyGroup() throws {
        let privateDirectory = try temporaryDirectory()
        let groupContainer = try temporaryDirectory()
        defer {
            try? FileManager.default.removeItem(at: privateDirectory)
            try? FileManager.default.removeItem(at: groupContainer)
        }
        let privateStore = try ApplicationMappingsStore.create(in: privateDirectory.appendingPathComponent("Private"))
        var migrated = try ApplicationMappingsStore.migrate(
            privateStore: privateStore,
            groupContainer: groupContainer
        )
        XCTAssertEqual(try migrated.load(), [])
        XCTAssertFalse(FileManager.default.fileExists(atPath: privateStore.fileURL.path))

        let mappings = [StoredApplicationMapping(token: Data([9, 9, 9]))]
        try privateStore.save(mappings)

        migrated = try ApplicationMappingsStore.migrate(
            privateStore: privateStore,
            groupContainer: groupContainer
        )
        XCTAssertEqual(try migrated.load(), mappings)
        XCTAssertFalse(FileManager.default.fileExists(atPath: privateStore.fileURL.path))
    }

    func testClearIsIdempotentAndLeavesAForeignStoreIntact() throws {
        let owned = FakeEnforcementSettingsStore()
        owned.storedFilter = .specific([WebDomain(domain: "example.com")])
        let foreign = FakeEnforcementSettingsStore()
        foreign.storedFilter = .specific([WebDomain(domain: "example.net")])
        let enforcer = capableEnforcer(store: owned)

        XCTAssertEqual(try clear(enforcer: enforcer), .cleared)
        XCTAssertEqual(try clear(enforcer: enforcer), .cleared)
        XCTAssertEqual(owned.clears, 2)
        XCTAssertNil(owned.storedFilter)
        XCTAssertEqual(foreign.storedFilter, .specific([WebDomain(domain: "example.net")]))
        XCTAssertEqual(foreign.clears, 0)
        XCTAssertEqual(foreign.writes, 0)
    }

    func testMigrationCopiesPrivateStoreToGroupAndRemovesTheOriginal() throws {
        let privateDirectory = try temporaryDirectory()
        let groupContainer = try temporaryDirectory()
        defer {
            try? FileManager.default.removeItem(at: privateDirectory)
            try? FileManager.default.removeItem(at: groupContainer)
        }
        let privateStore = try ApplicationMappingsStore.create(in: privateDirectory.appendingPathComponent("Private"))
        let mappings = [StoredApplicationMapping(token: Data([1, 2, 3]))]
        try privateStore.save(mappings)

        let migrated = try ApplicationMappingsStore.migrate(
            privateStore: privateStore,
            groupContainer: groupContainer
        )

        XCTAssertTrue(migrated.fileURL.path.hasPrefix(groupContainer.path))
        XCTAssertEqual(try migrated.load(), mappings)
        XCTAssertFalse(FileManager.default.fileExists(atPath: privateStore.fileURL.path))

        let rerun = try ApplicationMappingsStore.migrate(
            privateStore: privateStore,
            groupContainer: groupContainer
        )
        XCTAssertEqual(rerun.fileURL, migrated.fileURL)
        XCTAssertEqual(try rerun.load(), mappings)
    }

    func testMigrationRefusesACorruptSourceAndCopiesNothing() throws {
        let privateDirectory = try temporaryDirectory()
        let groupContainer = try temporaryDirectory()
        defer {
            try? FileManager.default.removeItem(at: privateDirectory)
            try? FileManager.default.removeItem(at: groupContainer)
        }
        let privateStore = try ApplicationMappingsStore.create(in: privateDirectory.appendingPathComponent("Private"))
        try Data("not-json".utf8).write(to: privateStore.fileURL)

        XCTAssertThrowsError(
            try ApplicationMappingsStore.migrate(privateStore: privateStore, groupContainer: groupContainer)
        ) { error in
            guard case ApplicationMappingsStoreError.corruption = error else {
                return XCTFail("Expected corruption, received \(type(of: error))")
            }
        }
        XCTAssertTrue(FileManager.default.fileExists(atPath: privateStore.fileURL.path))
        XCTAssertFalse(
            FileManager.default.fileExists(
                atPath: groupContainer.appendingPathComponent("ApplicationMappings/mappings-v1.json").path
            )
        )
    }

    func testRealStoreApplyClearCycle() throws {
#if targetEnvironment(simulator)
        throw XCTSkip("Requires a development-signed iPhone with authorization and a selection")
#else
        let context = try realStoreContext()
        defer {
            context.foreign.clearAllSettings()
            context.probe.clearAllSettings()
        }

        XCTAssertEqual(
            try apply(domains: ["example.com"], mappingIds: context.identifiers, enforcer: context.enforcer),
            .applied
        )
        XCTAssertEqual(context.probe.webContent.blockedByFilter, .specific([WebDomain(domain: "example.com")]))
        XCTAssertEqual(context.probe.shield.applications, context.tokens)

        XCTAssertEqual(try clear(enforcer: context.enforcer), .cleared)
        XCTAssertEqual(try clear(enforcer: context.enforcer), .cleared)
        XCTAssertNil(context.probe.webContent.blockedByFilter)
        XCTAssertNil(context.probe.shield.applications)
        XCTAssertEqual(context.foreign.webContent.blockedByFilter, .specific([WebDomain(domain: "example.net")]))
#endif
    }

    func testManualCheckpointHoldAppliedRealSet() throws {
#if targetEnvironment(simulator)
        throw XCTSkip("Requires a development-signed iPhone with authorization and a selection")
#else
        let context = try realStoreContext()
        defer {
            context.foreign.clearAllSettings()
            context.probe.clearAllSettings()
        }

        XCTAssertEqual(
            try apply(domains: ["example.com"], mappingIds: context.identifiers, enforcer: context.enforcer),
            .applied
        )
        print("POSATO_MANUAL_CHECKPOINT: restrictions active for 150 seconds.")
        print("POSATO_MANUAL_CHECKPOINT: check Safari blocked presentation, the shielded app, then unselected controls.")
        Thread.sleep(forTimeInterval: 150)
        print("POSATO_MANUAL_CHECKPOINT: holding done, clearing.")

        XCTAssertEqual(try clear(enforcer: context.enforcer), .cleared)
        XCTAssertNil(context.probe.webContent.blockedByFilter)
        XCTAssertNil(context.probe.shield.applications)
#endif
    }

    func testPostRevokeClearEmptiesOwnedStore() throws {
#if targetEnvironment(simulator)
        throw XCTSkip("Requires a development-signed iPhone")
#else
        let probe = ManagedSettingsStore(named: IosEnforcementStoreName.posato)
        defer {
            probe.clearAllSettings()
        }

        XCTAssertEqual(try clear(enforcer: IosManagedSettingsEnforcer()), .cleared)
        XCTAssertNil(probe.webContent.blockedByFilter)
        XCTAssertNil(probe.shield.applications)
#endif
    }

#if !targetEnvironment(simulator)
    private struct RealStoreContext {
        let enforcer: IosManagedSettingsEnforcer
        let probe: ManagedSettingsStore
        let foreign: ManagedSettingsStore
        let identifiers: [String]
        let tokens: Set<ApplicationToken>
    }

    private func realStoreContext() throws -> RealStoreContext {
        guard AuthorizationCenter.shared.authorizationStatus == .approved else {
            throw XCTSkip("Requires granted Screen Time authorization with at least one selected application")
        }
        guard let selection = try? ApplicationMappingsStore.liveMigrated().load(), !selection.isEmpty else {
            throw XCTSkip("Requires a stored application selection from the mappings flow")
        }
        let decoder = JSONDecoder()
        let tokens = try Set(selection.map { mapping in
            try decoder.decode(ApplicationToken.self, from: mapping.token)
        })
        let identifiers = selection.map { ApplicationTokenIdentity.identifier(for: $0.token) }
        let foreign = ManagedSettingsStore(named: ManagedSettingsStore.Name("app.posato.session-test-foreign"))
        foreign.webContent.blockedByFilter = .specific([WebDomain(domain: "example.net")])
        return RealStoreContext(
            enforcer: IosManagedSettingsEnforcer(),
            probe: ManagedSettingsStore(named: IosEnforcementStoreName.posato),
            foreign: foreign,
            identifiers: identifiers,
            tokens: tokens
        )
    }
#endif

    private func capableEnforcer(
        store: FakeEnforcementSettingsStore,
        authorization: EnforcementAuthorization = .approved,
        storedMappings: [StoredApplicationMapping] = []
    ) -> IosManagedSettingsEnforcer {
        IosManagedSettingsEnforcer(
            storeFactory: { store },
            authorization: { authorization },
            isCapable: true,
            storedMappings: { storedMappings }
        )
    }

    private func apply(
        domains: [String],
        mappingIds: [String] = [],
        enforcer: IosManagedSettingsEnforcer
    ) throws -> IosEnforcementOutcome {
        var result: IosEnforcementOutcome?
        enforcer.apply(request: IosEnforcementRequest(domains: domains, mappingIds: mappingIds)) { result = $0 }
        return try XCTUnwrap(result)
    }

    private func clear(enforcer: IosManagedSettingsEnforcer) throws -> IosEnforcementOutcome {
        var result: IosEnforcementOutcome?
        enforcer.clear { result = $0 }
        return try XCTUnwrap(result)
    }

    func testStatusReadsLiveStoreState() throws {
        let store = FakeEnforcementSettingsStore()
        let enforcer = capableEnforcer(store: store)

        XCTAssertEqual(try status(enforcer: enforcer), .cleared)
        XCTAssertEqual(try apply(domains: ["example.com"], enforcer: enforcer), .applied)
        XCTAssertEqual(try status(enforcer: enforcer), .applied)
        XCTAssertEqual(try clear(enforcer: enforcer), .cleared)
        XCTAssertEqual(try status(enforcer: enforcer), .cleared)
    }

    func testStatusReportsCapabilityAndAuthorization() throws {
        let incapable = IosManagedSettingsEnforcer(
            storeFactory: { FakeEnforcementSettingsStore() },
            authorization: { .approved },
            isCapable: false,
            storedMappings: { [] }
        )
        XCTAssertEqual(try status(enforcer: incapable), .unavailable)

        let unauthorized = capableEnforcer(store: FakeEnforcementSettingsStore(), authorization: .notDetermined)
        XCTAssertEqual(try status(enforcer: unauthorized), .authorizationRequired)
    }

    private func status(enforcer: IosManagedSettingsEnforcer) throws -> IosEnforcementOutcome {
        var result: IosEnforcementOutcome?
        enforcer.status { result = $0 }
        return try XCTUnwrap(result)
    }

    private func temporaryDirectory() throws -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
}
