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

struct CloudKitAccountBindingSource: AccountBindingSource {
  func resolve(deadlineMilliseconds: UInt32) -> BindingNative {
    let timeout = TimeInterval(deadlineMilliseconds) / 1_000
    guard timeout > 0 else {
      return .undetermined
    }
    let container = CKContainer(identifier: SyncLimits.containerIdentifier)
    switch accountStatus(container: container, timeout: timeout) {
    case .noAccount:
      return .unavailable
    case .restricted:
      return .restricted
    case .undetermined:
      return .undetermined
    case .available:
      break
    }
    guard let recordName = userRecordName(container: container, timeout: timeout) else {
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
