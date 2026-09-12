import CloudKit
import Foundation
import PosatoShared
import XCTest

@testable import Posato

/// Physical checklist driver for `SYNC-007` `AC-06`. Runs only on a
/// development-signed iPhone; every test skips on the Simulator.
///
/// Preconditions: the maintainer's iCloud account is signed in and the
/// network is reachable. The `PosatoSyncV1` zone is reused when present:
/// setup sweeps its workspace records first, so repeat runs need no manual
/// cleanup. The test aborts before any write when the zone still holds an
/// anchor or a bundle after the sweep, and deletes every workspace record in
/// teardown, leaving an empty zone behind. The zone itself is never deleted.
/// All fixtures are synthetic; only categorical checkpoint lines are printed.
final class CloudKitMailboxDeviceTests: XCTestCase {
    private var deviceStarted = false
    private var deviceBinding: Data?

    override func tearDown() {
        if deviceStarted, let binding = deviceBinding {
            deviceStarted = false
            deviceBinding = nil
            let provider = CloudKitMailboxProvider(
                accountSource: CloudKitAccountSource(),
                backend: CloudKitMailboxLiveBackend()
            )
            _ = provider.deleteWorkspaceRecords(binding: binding)
        }
        super.tearDown()
    }

    func testDeviceMailboxCycle() throws {
#if targetEnvironment(simulator)
        throw XCTSkip("Requires a development-signed iPhone")
#else
        continueAfterFailure = false
        let provider = CloudKitMailboxProvider(
            accountSource: CloudKitAccountSource(),
            backend: CloudKitMailboxLiveBackend()
        )
        let binding = try XCTUnwrap(resolveDeviceBinding())
        deviceBinding = binding
        print("POSATO_DEVICE_CHECKPOINT: mailbox cycle starting with an available binding.")

        if provider.fetchZone(binding: binding) == .missing {
            XCTAssertEqual(provider.saveZone(binding: binding), .created)
        } else {
            XCTAssertEqual(provider.deleteWorkspaceRecords(binding: binding), .deletedandabsent)
            XCTAssertEqual(provider.saveZone(binding: binding), .alreadyexists)
        }
        XCTAssertEqual(provider.fetchZone(binding: binding), .found)
        print("POSATO_DEVICE_CHECKPOINT: zone saved and confirmed.")
        deviceStarted = true

        let anchor = try randomBytes(count: MailboxCloudLimits.anchorBytes)
        XCTAssertEqual(provider.createAnchor(binding: binding, fields: anchor), .created)
        XCTAssertEqual(provider.createAnchor(binding: binding, fields: anchor), .conflict)
        print("POSATO_DEVICE_CHECKPOINT: anchor created with conflict on identical re-create.")

        let identifier = try randomBytes(count: MailboxCloudLimits.bundleIdentifierBytes)
        let payload = try randomBytes(count: 64)
        XCTAssertEqual(provider.saveBundle(binding: binding, identifier: identifier, payload: payload), .saved)
        XCTAssertEqual(provider.saveBundle(binding: binding, identifier: identifier, payload: payload), .identical)
        print("POSATO_DEVICE_CHECKPOINT: bundle saved with identical re-save.")

        let page = provider.fetchChanges(binding: binding, cursor: Data())
        XCTAssertEqual(page.status, .page)
        XCTAssertEqual(page.bundleIdentifier, identifier)
        XCTAssertEqual(page.bundlePayload, payload)
        XCTAssertNotNil(page.nextCursor)
        print("POSATO_DEVICE_CHECKPOINT: change fetch returned the bundle.")

        var other = payload
        other[0] ^= 0x01
        XCTAssertEqual(provider.saveBundle(binding: binding, identifier: identifier, payload: other), .conflict)
        print("POSATO_DEVICE_CHECKPOINT: different bytes rejected.")

        XCTAssertEqual(provider.deleteWorkspaceRecords(binding: binding), .deletedandabsent)
        XCTAssertEqual(provider.fetchZone(binding: binding), .found)
        deviceStarted = false
        deviceBinding = nil
        print("POSATO_DEVICE_CHECKPOINT: workspace records deleted with absence verified; zone kept.")
#endif
    }

    private func randomBytes(count: Int) throws -> Data {
        var bytes = Data(count: count)
        let status: OSStatus = bytes.withUnsafeMutableBytes { buffer in
            guard let baseAddress = buffer.baseAddress else {
                return errSecParam
            }
            return SecRandomCopyBytes(kSecRandomDefault, count, baseAddress)
        }
        guard status == errSecSuccess else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
        return bytes
    }
}

/// Binding resolution reuses the `SYNC-005` account source and derivation;
/// the mailbox provider itself never resolves bindings.
private func resolveDeviceBinding() -> Data? {
    guard case .available(let recordName) = CloudKitAccountSource().currentRecordName() else {
        return nil
    }
    return SynchronizableKeychainProvider.deriveBinding(recordName: recordName)
}
