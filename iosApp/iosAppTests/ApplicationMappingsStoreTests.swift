import Foundation
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

        let mappings = [StoredApplicationMapping(slot: 2, token: Data([1, 2, 3]))]
        try store.save(mappings)

        XCTAssertEqual(try store.load(), mappings)
    }

    func testCorruptFileIsRejected() throws {
        let fileURL = directory.appendingPathComponent("mappings.json")
        try Data("not-json".utf8).write(to: fileURL)

        assertCorruption { try ApplicationMappingsStore(fileURL: fileURL).load() }
    }

    func testUnrelatedTemporaryFileDoesNotAffectLoad() throws {
        let fileURL = directory.appendingPathComponent("mappings.json")
        let store = ApplicationMappingsStore(fileURL: fileURL)
        let mappings = [StoredApplicationMapping(slot: 1, token: Data([4, 5, 6]))]
        try store.save(mappings)
        try Data("partial".utf8).write(to: directory.appendingPathComponent("mappings.json.tmp"))

        XCTAssertEqual(try store.load(), mappings)
    }

    func testSaveFailureLeavesExistingFileReadable() throws {
        let fileURL = directory.appendingPathComponent("mappings.json")
        let store = ApplicationMappingsStore(fileURL: fileURL)
        let mappings = [StoredApplicationMapping(slot: 1, token: Data([7, 8, 9]))]
        try store.save(mappings)
        try FileManager.default.setAttributes([.immutable: true], ofItemAtPath: fileURL.path)
        defer { try? FileManager.default.setAttributes([.immutable: false], ofItemAtPath: fileURL.path) }

        XCTAssertThrowsError(try store.save([StoredApplicationMapping(slot: 2, token: Data([0]))]))
        XCTAssertEqual(try store.load(), mappings)
    }

    func testStructuralLimitsAndDuplicateTokensAreRejectedAsCorruption() throws {
        let store = ApplicationMappingsStore(fileURL: directory.appendingPathComponent("mappings.json"))

        assertCorruption { try store.save([StoredApplicationMapping(slot: 1, token: Data())]) }
        assertCorruption {
            try store.save([
                StoredApplicationMapping(slot: 1, token: Data([1])),
                StoredApplicationMapping(slot: 1, token: Data([2])),
            ])
        }
        assertCorruption {
            try store.save([
                StoredApplicationMapping(slot: 1, token: Data([1])),
                StoredApplicationMapping(slot: 2, token: Data([1])),
            ])
        }
        assertCorruption {
            try store.save(
                (1...65).map { slot in StoredApplicationMapping(slot: slot, token: Data([UInt8(slot)])) }
            )
        }
        assertCorruption {
            try store.save([StoredApplicationMapping(slot: 1, token: Data(repeating: 1, count: 65_537))])
        }
    }

    func testCreatedStoreExcludesDirectoryFromBackupAndProtectsFile() throws {
        let storeDirectory = directory.appendingPathComponent("ApplicationMappings", isDirectory: true)
        let store = try ApplicationMappingsStore.create(in: storeDirectory)

        XCTAssertEqual(
            try storeDirectory.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup,
            true
        )
        try store.save([StoredApplicationMapping(slot: 1, token: Data([1]))])
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
            try provider.validatedMappings([StoredApplicationMapping(slot: 1, token: first)])
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

    private func assertCorruption(_ action: () throws -> Any) {
        XCTAssertThrowsError(try action()) { error in
            guard case ApplicationMappingsStoreError.corruption = error else {
                return XCTFail("Expected corruption, received \(type(of: error))")
            }
        }
    }
}
