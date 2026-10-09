#if POSATO_VERIFICATION
  import Foundation
  import Testing

  @testable import PosatoMacOSSync

  // The zone deletion is a verification-only seam (ADR 0007 amendment of
  // 2026-10-09). A wrong refusal deletes a real zone, and E2E cannot run it
  // against Production or a Developer ID build on purpose, so each refusal is
  // proved here.

  struct FakeVerificationEnvironment: VerificationEnvironment {
    var environment: String?
    var development = true

    func containerEnvironment() -> String? {
      return environment
    }

    func signedForDevelopment() -> Bool {
      return development
    }
  }

  final class FakeZones: ZoneDeleting, @unchecked Sendable {
    var fault: BackendFault?
    private(set) var deleteCalls = 0

    func deleteZone(timeout: TimeInterval) -> BackendFault? {
      deleteCalls += 1
      return fault
    }
  }

  private func verificationDependencies(
    backend: FakeCloudBackend,
    zones: FakeZones,
    environment: FakeVerificationEnvironment = FakeVerificationEnvironment()
  ) -> VerificationDependencies {
    return VerificationDependencies(
      entitlements: FakeEntitlements(value: provisionedEntitlements()),
      environment: environment,
      accounts: FakeAccounts(.available(syntheticBinding)),
      clouds: CloudStore(backend: backend),
      zones: zones,
    )
  }

  private func deleteZoneRequest() -> SyncMessage {
    return cloudRequest(operation: .fetchZone, payload: syntheticBinding)
  }

  @Test func givenAnchorPresentWhenDeletingZoneThenItIsRefused() {
    let backend = FakeCloudBackend()
    backend.records[CloudNames.anchorName] = testAnchorRecord()
    let zones = FakeZones()

    let response = VerificationHandler.deleteZone(
      deleteZoneRequest(),
      dependencies: verificationDependencies(backend: backend, zones: zones)
    )

    #expect(response.outcome == .anchorPresent)
    #expect(zones.deleteCalls == 0)
  }

  @Test func givenProductionEnvironmentWhenDeletingZoneThenItIsRefused() {
    let zones = FakeZones()

    let response = VerificationHandler.deleteZone(
      deleteZoneRequest(),
      dependencies: verificationDependencies(
        backend: FakeCloudBackend(),
        zones: zones,
        environment: FakeVerificationEnvironment(environment: "Production")
      )
    )

    #expect(response.outcome == .restricted)
    #expect(zones.deleteCalls == 0)
  }

  @Test func givenDeveloperIdSignatureWhenDeletingZoneThenItIsRefused() {
    let backend = FakeCloudBackend()
    let zones = FakeZones()

    let response = VerificationHandler.deleteZone(
      deleteZoneRequest(),
      dependencies: verificationDependencies(
        backend: backend,
        zones: zones,
        environment: FakeVerificationEnvironment(development: false)
      )
    )

    #expect(response.outcome == .restricted)
    #expect(zones.deleteCalls == 0)
    #expect(backend.fetchRecordCalls == 0)
  }

  @Test func givenAnchorMissingInDevelopmentWhenDeletingZoneThenTheZoneIsDeleted() {
    let zones = FakeZones()

    let response = VerificationHandler.deleteZone(
      deleteZoneRequest(),
      dependencies: verificationDependencies(backend: FakeCloudBackend(), zones: zones)
    )

    #expect(response.outcome == .deletedAndAbsent)
    #expect(zones.deleteCalls == 1)
    #expect(response.payload == VerificationSeams.marker)
  }

  @Test func givenZoneAlreadyAbsentWhenDeletingZoneThenNothingIsDeleted() {
    let backend = FakeCloudBackend()
    backend.zoneAbsentRecords = true
    let zones = FakeZones()

    let response = VerificationHandler.deleteZone(
      deleteZoneRequest(),
      dependencies: verificationDependencies(backend: backend, zones: zones)
    )

    #expect(response.outcome == .missing)
    #expect(zones.deleteCalls == 0)
  }

  @Test func givenVerificationFrameWhenDecodingThenOnlyItsCodeIsAccepted() throws {
    var request = deleteZoneRequest()
    request.requestIdentifier = testIdentifier(4)
    var frame = try SyncCodec.encode(request)
    #expect(try VerificationCodec.decode(frame) == nil)
    frame[VerificationSeams.operationOffset] = VerificationSeams.deleteZoneCode
    #expect(try VerificationCodec.decode(frame)?.payload == syntheticBinding)
    #expect(throws: SyncProtocolFailure.invalidOperation) { try SyncCodec.decode(frame) }
  }
#endif
