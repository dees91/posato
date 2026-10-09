#if POSATO_VERIFICATION
  import CloudKit
  import Foundation
  import Security

  /// Verification-only operations under the ADR 0007 amendment of 2026-10-09.
  /// They exist only in a companion built with the `POSATO_VERIFICATION`
  /// condition; a release companion rejects their codes as unknown operations.
  enum VerificationSeams {
    /// Present in every verification companion and absent from release ones;
    /// the packaging and DMG checks look for it. Every response carries it.
    static let marker = Data("posato-verification-seams-v1".utf8)
    static let deleteZoneCode: UInt8 = 201
    /// Byte offset of the operation code in a frame: magic (4), then major (2).
    static let operationOffset = 6
  }

  protocol VerificationEnvironment: Sendable {
    /// The `com.apple.developer.icloud-container-environment` entitlement;
    /// absent (nil) means the Development environment.
    func containerEnvironment() -> String?
    /// Whether this process is signed with an Apple Development leaf
    /// certificate rather than Developer ID.
    func signedForDevelopment() -> Bool
  }

  struct SelfVerificationEnvironment: VerificationEnvironment {
    func containerEnvironment() -> String? {
      guard let task = SecTaskCreateFromSelf(nil) else {
        return "unknown"
      }
      var error: Unmanaged<CFError>?
      let value = SecTaskCopyValueForEntitlement(
        task,
        "com.apple.developer.icloud-container-environment" as CFString,
        &error
      )
      if error != nil {
        // An unreadable entitlement is never taken for Development.
        return "unknown"
      }
      guard let value else {
        return nil
      }
      return (value as? String) ?? "unknown"
    }

    func signedForDevelopment() -> Bool {
      var code: SecCode?
      guard SecCodeCopySelf([], &code) == errSecSuccess, let code else {
        return false
      }
      var staticCode: SecStaticCode?
      guard SecCodeCopyStaticCode(code, [], &staticCode) == errSecSuccess, let staticCode else {
        return false
      }
      var information: CFDictionary?
      guard
        SecCodeCopySigningInformation(
          staticCode,
          SecCSFlags(rawValue: kSecCSSigningInformation),
          &information
        ) == errSecSuccess,
        let details = information as? [String: Any],
        let certificates = details[kSecCodeInfoCertificates as String] as? [SecCertificate],
        let leaf = certificates.first,
        let summary = SecCertificateCopySubjectSummary(leaf) as String?
      else {
        return false
      }
      return summary.hasPrefix("Apple Development:")
    }
  }

  protocol ZoneDeleting: Sendable {
    func deleteZone(timeout: TimeInterval) -> BackendFault?
  }

  extension CKCloudDatabase: ZoneDeleting {
    func deleteZone(timeout: TimeInterval) -> BackendFault? {
      guard timeout > 0 else {
        return .unknown
      }
      let box = LockedBox<BackendFault?>(.unknown)
      let done = DispatchSemaphore(value: 0)
      let operation = CKModifyRecordZonesOperation(
        recordZonesToSave: nil,
        recordZoneIDsToDelete: [zoneID]
      )
      operation.modifyRecordZonesResultBlock = { result in
        switch result {
        case .success:
          box.set(nil)
        case .failure(let error):
          let mapped = error as NSError
          box.set(CloudErrorMapper.isRetryable(mapped) ? .retryable : .unknown)
        }
        done.signal()
      }
      database.add(operation)
      if done.wait(timeout: .now() + timeout) == .timedOut {
        operation.cancel()
        return .unknown
      }
      return box.get()
    }
  }

  /// Frames a verification request like a product one, so the header is
  /// validated by the product codec; only the operation byte differs.
  enum VerificationCodec {
    static func decode(_ encoded: Data) throws -> SyncMessage? {
      let index = encoded.startIndex + VerificationSeams.operationOffset
      guard encoded.count > VerificationSeams.operationOffset,
        encoded[index] == VerificationSeams.deleteZoneCode
      else {
        return nil
      }
      var product = Data(encoded)
      product[product.startIndex + VerificationSeams.operationOffset] =
        SyncOperation.fetchZone.rawValue
      return try SyncCodec.decode(product)
    }

    static func encode(_ response: SyncMessage) throws -> Data {
      var encoded = try SyncCodec.encode(response)
      encoded[encoded.startIndex + VerificationSeams.operationOffset] =
        VerificationSeams.deleteZoneCode
      return encoded
    }
  }

  struct VerificationDependencies: Sendable {
    var entitlements: any EntitlementReader
    var environment: any VerificationEnvironment
    var accounts: any AccountBindingSource
    var clouds: CloudStore
    var zones: any ZoneDeleting
    var accountChangeName: Notification.Name = .CKAccountChanged
  }

  enum VerificationHandler {
    /// Deletes the exact Posato zone of the signed-in account. Refuses outside
    /// the Development environment, on a non-Development signature, and while
    /// the zone's anchor is present. The app's handler has already checked,
    /// under its synchronization lock, that no local workspace is established.
    static func deleteZone(
      _ request: SyncMessage,
      dependencies: VerificationDependencies
    ) -> SyncMessage {
      let started = DispatchTime.now()
      if let refusal = refusal(request, dependencies: dependencies) {
        return respond(request, refusal)
      }
      guard let binding = CloudRequestCodec.bindingOnly(request.payload) else {
        return respond(request, .integrityFailure)
      }
      switch RequestSupport.preflight(
        request,
        expected: binding,
        started: started,
        accounts: dependencies.accounts
      ) {
      case .proceed:
        break
      case .respond(let message):
        return respond(message, message.outcome ?? .unknownOutcome)
      }
      if let outcome = anchorGate(request, started: started, dependencies: dependencies) {
        return respond(request, outcome)
      }
      let (fault, changed) = RequestSupport.observingAccountChange(
        name: dependencies.accountChangeName
      ) {
        dependencies.zones.deleteZone(timeout: remaining(request, started: started))
      }
      let stillBound = RequestSupport.postflight(
        request,
        expected: binding,
        started: started,
        accounts: dependencies.accounts
      )
      guard !changed, stillBound else {
        return respond(request, .unknownOutcome)
      }
      return respond(request, deletionOutcome(fault))
    }

    private static func refusal(
      _ request: SyncMessage,
      dependencies: VerificationDependencies
    ) -> SyncOutcome? {
      let cloudKit = SyncLimits.cloudkitCapability
      guard request.capabilities & cloudKit == cloudKit,
        let entitlements = dependencies.entitlements.load(),
        EntitlementGuard.accessGroup(from: entitlements) != nil
      else {
        return .unknownOutcome
      }
      guard dependencies.environment.containerEnvironment() == nil,
        dependencies.environment.signedForDevelopment()
      else {
        return .restricted
      }
      return nil
    }

    /// Nil when the anchor is missing and the zone may be deleted.
    private static func anchorGate(
      _ request: SyncMessage,
      started: DispatchTime,
      dependencies: VerificationDependencies
    ) -> SyncOutcome? {
      let lookup = dependencies.clouds.backend.fetchRecord(
        name: CloudNames.anchorName,
        timeout: remaining(request, started: started)
      )
      switch lookup {
      case .missing:
        return nil
      case .zoneMissing:
        // Already absent: nothing was deleted, so no wait follows.
        return .missing
      case .found:
        return .anchorPresent
      case .failed(let fault):
        return fault == .retryable ? .retryable : .unknownOutcome
      }
    }

    private static func deletionOutcome(_ fault: BackendFault?) -> SyncOutcome {
      switch fault {
      case nil:
        return .deletedAndAbsent
      case .retryable:
        return .retryable
      case .unknown:
        return .unknownOutcome
      }
    }

    private static func remaining(_ request: SyncMessage, started: DispatchTime) -> TimeInterval {
      return DeadlineBudget.remainingSeconds(
        started: started,
        budgetMilliseconds: request.deadlineMilliseconds
      )
    }

    private static func respond(_ request: SyncMessage, _ outcome: SyncOutcome) -> SyncMessage {
      return request.respond(outcome: outcome, payload: VerificationSeams.marker)
    }
  }
#endif
