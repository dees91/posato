import Foundation
import PosatoShared
import UIKit
import XCTest
@testable import Posato

final class ApplicationMappingsStoreTests: XCTestCase {
    private var directory: URL!

    override func setUpWithError() throws {
        directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: directory)
    }

    func testMissingFileLoadsEmptyAndSavedMappingsRoundTrip() throws {
        let store = ApplicationMappingsStore(fileURL: directory.appendingPathComponent("mappings.json"))
        XCTAssertEqual(try store.load(), [])

        let mappings = [StoredApplicationMapping(token: Data([1, 2, 3]))]
        try store.save(mappings)

        XCTAssertEqual(try store.load(), mappings)
    }

    func testLegacySlotFieldLoadsAndNextSaveOmitsIt() throws {
        let fileURL = directory.appendingPathComponent("mappings.json")
        let store = ApplicationMappingsStore(fileURL: fileURL)
        let legacy = Data(#"{"version":1,"mappings":[{"slot":7,"token":"AQID"}]}"#.utf8)
        try legacy.write(to: fileURL)

        let mappings = try store.load()
        XCTAssertEqual(mappings, [StoredApplicationMapping(token: Data([1, 2, 3]))])

        try store.save(mappings)
        let saved = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: fileURL)) as? [String: Any])
        let savedMappings = try XCTUnwrap(saved["mappings"] as? [[String: Any]])
        XCTAssertNil(try XCTUnwrap(savedMappings.first)["slot"])
    }

    func testCorruptFileIsRejected() throws {
        let fileURL = directory.appendingPathComponent("mappings.json")
        try Data("not-json".utf8).write(to: fileURL)

        assertCorruption { try ApplicationMappingsStore(fileURL: fileURL).load() }
    }

    func testUnrelatedTemporaryFileDoesNotAffectLoad() throws {
        let fileURL = directory.appendingPathComponent("mappings.json")
        let store = ApplicationMappingsStore(fileURL: fileURL)
        let mappings = [StoredApplicationMapping(token: Data([4, 5, 6]))]
        try store.save(mappings)
        try Data("partial".utf8).write(to: directory.appendingPathComponent("mappings.json.tmp"))

        XCTAssertEqual(try store.load(), mappings)
    }

    func testSaveFailureLeavesExistingFileReadable() throws {
        let fileURL = directory.appendingPathComponent("mappings.json")
        let store = ApplicationMappingsStore(fileURL: fileURL)
        let mappings = [StoredApplicationMapping(token: Data([7, 8, 9]))]
        try store.save(mappings)
        try FileManager.default.setAttributes([.immutable: true], ofItemAtPath: fileURL.path)
        defer { try? FileManager.default.setAttributes([.immutable: false], ofItemAtPath: fileURL.path) }

        XCTAssertThrowsError(try store.save([StoredApplicationMapping(token: Data([0]))]))
        XCTAssertEqual(try store.load(), mappings)
    }

    func testStructuralLimitsAndDuplicateTokensAreRejectedAsCorruption() throws {
        let store = ApplicationMappingsStore(fileURL: directory.appendingPathComponent("mappings.json"))

        assertCorruption { try store.save([StoredApplicationMapping(token: Data())]) }
        assertCorruption {
            try store.save([
                StoredApplicationMapping(token: Data([1])),
                StoredApplicationMapping(token: Data([1])),
            ])
        }
        assertCorruption {
            try store.save(
                (1...65).map { value in StoredApplicationMapping(token: Data([UInt8(value)])) }
            )
        }
        assertCorruption {
            try store.save([StoredApplicationMapping(token: Data(repeating: 1, count: 65_537))])
        }
    }

    func testCreatedStoreExcludesDirectoryFromBackupAndProtectsFile() throws {
        let storeDirectory = directory.appendingPathComponent("ApplicationMappings", isDirectory: true)
        let store = try ApplicationMappingsStore.create(in: storeDirectory)

        XCTAssertEqual(
            try storeDirectory.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup,
            true
        )
        try store.save([StoredApplicationMapping(token: Data([1]))])
#if !targetEnvironment(simulator)
        let attributes = try FileManager.default.attributesOfItem(atPath: store.fileURL.path)
        XCTAssertEqual(attributes[.protectionKey] as? FileProtectionType, .complete)
#endif
    }

    func testProviderRejectsInvalidOpaqueTokenAndDerivesStableDistinctIdentifiers() throws {
        let provider = IosFamilyControlsApplicationMappingsProvider()
        let first = Data([1, 2, 3])
        let second = Data([1, 2, 4])

        XCTAssertEqual(provider.identifier(for: first), provider.identifier(for: first))
        XCTAssertNotEqual(provider.identifier(for: first), provider.identifier(for: second))
        assertCorruption {
            try provider.validatedMappings([StoredApplicationMapping(token: first)])
        }
    }

    func testOperationCancellationBeforeInstallationRunsExactlyOnce() {
        let operation = ApplicationMappingsOperation()
        var cancellations = 0

        operation.cancel()
        operation.install { cancellations += 1 }
        operation.cancel()

        XCTAssertEqual(cancellations, 1)
    }

    func testChooseSessionCompletesAnActiveBeginBeforeStartingTheNext() {
        let session = ApplicationMappingsChooseSession()
        var first: IosApplicationMappingsOutcome?
        var second: IosApplicationMappingsOutcome?
        let replacement = sessionResponse(.pickerFailure)
        let firstGeneration = session.begin(replacingActiveWith: replacement) { first = $0.outcome }
        let secondGeneration = session.begin(replacingActiveWith: replacement) { second = $0.outcome }

        XCTAssertEqual(first, .pickerFailure)
        XCTAssertNil(second)
        XCTAssertFalse(session.complete(firstGeneration, with: sessionResponse(.cancelled)))
        XCTAssertTrue(session.complete(secondGeneration, with: sessionResponse(.cancelled)))
        XCTAssertEqual(second, .cancelled)
        XCTAssertFalse(session.isActive)
    }

    func testChooseSessionCancelDoesNotCompleteALaterRequest() {
        let session = ApplicationMappingsChooseSession()
        var first: IosApplicationMappingsOutcome?
        var second: IosApplicationMappingsOutcome?
        let replacement = sessionResponse(.pickerFailure)
        let firstGeneration = session.begin(replacingActiveWith: replacement) { first = $0.outcome }

        XCTAssertTrue(session.complete(firstGeneration, with: sessionResponse(.cancelled)))
        XCTAssertEqual(first, .cancelled)

        let secondGeneration = session.begin(replacingActiveWith: replacement) { second = $0.outcome }
        XCTAssertFalse(session.complete(firstGeneration, with: sessionResponse(.pickerFailure)))
        XCTAssertNil(second)
        XCTAssertTrue(session.isCurrent(secondGeneration))
    }

    func testPresentationGateFailsClosedWhenAlreadyPresentingOrOffWindow() {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 320, height: 480))
        let presenter = UIViewController()
        window.rootViewController = presenter
        window.makeKeyAndVisible()
        _ = presenter.view

        XCTAssertTrue(ApplicationMappingsPresentationGate.canPresent(from: presenter))

        let first = UIViewController()
        let presented = expectation(description: "presented")
        ApplicationMappingsPresentationGate.present(first, from: presenter, animated: false) { success in
            XCTAssertTrue(success)
            presented.fulfill()
        }
        wait(for: [presented], timeout: 1)
        XCTAssertFalse(ApplicationMappingsPresentationGate.canPresent(from: presenter))

        let second = UIViewController()
        var stacked = true
        ApplicationMappingsPresentationGate.present(second, from: presenter, animated: false) { success in
            stacked = success
        }
        XCTAssertFalse(stacked)
        XCTAssertTrue(presenter.presentedViewController === first)

        let detached = UIViewController()
        _ = detached.view
        XCTAssertFalse(ApplicationMappingsPresentationGate.canPresent(from: detached))
        var completed = false
        ApplicationMappingsPresentationGate.present(UIViewController(), from: detached, animated: false) { success in
            XCTAssertFalse(success)
            completed = true
        }
        XCTAssertTrue(completed)
    }

    func testSavingEmptyMappingsRecoversCorruptFile() throws {
        let fileURL = directory.appendingPathComponent("mappings.json")
        try Data("not-json".utf8).write(to: fileURL)
        let store = ApplicationMappingsStore(fileURL: fileURL)

        assertCorruption { try store.load() }
        try store.save([])
        XCTAssertEqual(try store.load(), [])
    }

    func testProviderClearRecoversCorruptStore() throws {
        let fileURL = directory.appendingPathComponent("mappings.json")
        try Data("not-json".utf8).write(to: fileURL)
        let store = ApplicationMappingsStore(fileURL: fileURL)
        let provider = IosFamilyControlsApplicationMappingsProvider(storeFactory: { store })

        var loadOutcome: IosApplicationMappingsOutcome?
        provider.load { loadOutcome = $0.outcome }
        XCTAssertEqual(loadOutcome, .corruption)

        var clearOutcome: IosApplicationMappingsOutcome?
        provider.clear { clearOutcome = $0.outcome }
        XCTAssertEqual(clearOutcome, .success)

        var recovered: IosApplicationMappingsResponse?
        provider.load { recovered = $0 }
        XCTAssertNotEqual(recovered?.outcome, .corruption)
        XCTAssertEqual(recovered?.mappings.count, 0)
    }

    private func sessionResponse(_ outcome: IosApplicationMappingsOutcome) -> IosApplicationMappingsResponse {
        return IosApplicationMappingsResponse(outcome: outcome, access: .unavailable, mappings: [])
    }

    private func assertCorruption(_ action: () throws -> Any) {
        XCTAssertThrowsError(try action()) { error in
            guard case ApplicationMappingsStoreError.corruption = error else {
                return XCTFail("Expected corruption, received \(type(of: error))")
            }
        }
    }
}
