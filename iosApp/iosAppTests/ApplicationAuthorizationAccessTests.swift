import PosatoShared
import XCTest
@testable import Posato

final class ApplicationAuthorizationAccessTests: XCTestCase {
    func testEachAnswerMapsToItsAccess() {
        XCTAssertEqual(ApplicationAuthorizationAccess.access(for: .approved), .ready)
        XCTAssertEqual(ApplicationAuthorizationAccess.access(for: .notDetermined), .authorizationRequired)
        XCTAssertEqual(ApplicationAuthorizationAccess.access(for: .denied), .authorizationDenied)
        XCTAssertEqual(ApplicationAuthorizationAccess.access(for: .restricted), .restricted)
        XCTAssertEqual(ApplicationAuthorizationAccess.access(for: .unavailable), .unavailable)
    }

    func testAuthorizationRequestCompletesWithoutPresentingAPicker() {
        let provider = IosFamilyControlsApplicationMappingsProvider()
        provider.presenter = nil
        let completed = expectation(description: "authorization request completes")

        var outcome: IosApplicationMappingsOutcome?
        var access: IosApplicationMappingsAccess?
        _ = provider.requestAuthorization { response in
            outcome = response.outcome
            access = response.access
            completed.fulfill()
        }

        wait(for: [completed], timeout: 5)
        XCTAssertEqual(outcome, .unavailable)
        XCTAssertEqual(access, .unavailable)
    }
}
