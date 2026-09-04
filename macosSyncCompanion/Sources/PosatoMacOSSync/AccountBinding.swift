import CloudKit
import CryptoKit
import Foundation

enum BindingNative: Equatable, Sendable {
  case available(Data)
  case unavailable
  case restricted
  case undetermined
}

protocol AccountBindingSource: Sendable {
  func resolve(deadlineMilliseconds: UInt32) -> BindingNative
}

enum AccountBinding {
  static func derive(recordName: String) -> Data {
    var material = Data(SyncLimits.bindingPrefix.utf8)
    material.append(0)
    material.append(Data(recordName.utf8))
    return Data(SHA256.hash(data: material))
  }
}

enum DeadlineBudget {
  static func remainingMilliseconds(started: DispatchTime, budgetMilliseconds: UInt32) -> UInt32 {
    let elapsedNanoseconds = DispatchTime.now().uptimeNanoseconds &- started.uptimeNanoseconds
    let budgetNanoseconds = UInt64(budgetMilliseconds) * 1_000_000
    guard elapsedNanoseconds < budgetNanoseconds else {
      return 0
    }
    return UInt32((budgetNanoseconds - elapsedNanoseconds) / 1_000_000)
  }

  static func remainingSeconds(started: DispatchTime, budgetMilliseconds: UInt32) -> TimeInterval {
    let remaining = remainingMilliseconds(
      started: started,
      budgetMilliseconds: budgetMilliseconds
    )
    return TimeInterval(remaining) / 1_000
  }
}

struct CloudKitAccountBindingSource: AccountBindingSource {
  func resolve(deadlineMilliseconds: UInt32) -> BindingNative {
    guard deadlineMilliseconds > 0 else {
      return .undetermined
    }
    let started = DispatchTime.now()
    let container = CKContainer(identifier: SyncLimits.containerIdentifier)
    switch accountStatus(
      container: container,
      timeout: DeadlineBudget.remainingSeconds(
        started: started,
        budgetMilliseconds: deadlineMilliseconds
      )
    ) {
    case .noAccount:
      return .unavailable
    case .restricted:
      return .restricted
    case .undetermined:
      return .undetermined
    case .available:
      break
    }
    guard
      let recordName = userRecordName(
        container: container,
        timeout: DeadlineBudget.remainingSeconds(
          started: started,
          budgetMilliseconds: deadlineMilliseconds
        )
      )
    else {
      return .undetermined
    }
    let binding = AccountBinding.derive(recordName: recordName)
    guard binding.count == SyncLimits.bindingBytes else {
      return .undetermined
    }
    return .available(binding)
  }

  private enum CloudStatus {
    case available
    case noAccount
    case restricted
    case undetermined
  }

  private func accountStatus(
    container: CKContainer,
    timeout: TimeInterval
  ) -> CloudStatus {
    guard timeout > 0 else {
      return .undetermined
    }
    let box = BindingBox<CKAccountStatus>()
    let lock = DispatchSemaphore(value: 0)
    container.accountStatus { status, error in
      box.set(error == nil ? status : nil)
      lock.signal()
    }
    guard lock.wait(timeout: .now() + timeout) == .success else {
      return .undetermined
    }
    switch box.get() {
    case .available:
      return .available
    case .noAccount:
      return .noAccount
    case .restricted:
      return .restricted
    case .couldNotDetermine, .temporarilyUnavailable, .none:
      return .undetermined
    @unknown default:
      return .undetermined
    }
  }

  private func userRecordName(container: CKContainer, timeout: TimeInterval) -> String? {
    guard timeout > 0 else {
      return nil
    }
    let box = BindingBox<CKRecord.ID>()
    let lock = DispatchSemaphore(value: 0)
    container.fetchUserRecordID { recordID, error in
      box.set(error == nil ? recordID : nil)
      lock.signal()
    }
    guard lock.wait(timeout: .now() + timeout) == .success else {
      return nil
    }
    return box.get()?.recordName
  }
}

private final class BindingBox<Value>: @unchecked Sendable {
  private let lock = NSLock()
  private var value: Value?

  func set(_ value: Value?) {
    lock.lock()
    defer { lock.unlock() }
    guard self.value == nil else {
      return
    }
    self.value = value
  }

  func get() -> Value? {
    lock.lock()
    defer { lock.unlock() }
    return value
  }
}
