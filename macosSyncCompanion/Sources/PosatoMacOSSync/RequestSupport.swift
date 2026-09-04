import CloudKit
import Foundation

private final class AccountChangeFlag: @unchecked Sendable {
  private let lock = NSLock()
  private var marked = false

  func mark() {
    lock.lock()
    marked = true
    lock.unlock()
  }

  var isMarked: Bool {
    lock.lock()
    defer { lock.unlock() }
    return marked
  }
}

enum RequestSupport {
  static func observingAccountChange<Result>(
    name: Notification.Name,
    _ body: () -> Result
  ) -> (Result, Bool) {
    let flag = AccountChangeFlag()
    let observer = NotificationCenter.default.addObserver(
      forName: name,
      object: nil,
      queue: nil
    ) { _ in
      flag.mark()
    }
    defer { NotificationCenter.default.removeObserver(observer) }
    let result = body()
    return (result, flag.isMarked)
  }

  enum Preflight {
    case proceed
    case respond(SyncMessage)
  }

  static func preflight(
    _ request: SyncMessage,
    expected: Data,
    started: DispatchTime,
    accounts: any AccountBindingSource
  ) -> Preflight {
    switch accountBinding(request, started: started, accounts: accounts) {
    case .available(let current):
      if ItemCodec.constantTimeEquals(current, expected) {
        return .proceed
      }
      return .respond(request.respond(outcome: .accountChanged))
    case .unavailable, .restricted, .undetermined:
      return .respond(request.respond(outcome: .retryable))
    }
  }

  static func postflight(
    _ request: SyncMessage,
    expected: Data,
    started: DispatchTime,
    accounts: any AccountBindingSource
  ) -> Bool {
    switch accountBinding(request, started: started, accounts: accounts) {
    case .available(let current):
      return ItemCodec.constantTimeEquals(current, expected)
    case .unavailable, .restricted, .undetermined:
      return false
    }
  }

  static func accountBinding(
    _ request: SyncMessage,
    started: DispatchTime,
    accounts: any AccountBindingSource
  ) -> BindingNative {
    return accounts.resolve(
      deadlineMilliseconds: DeadlineBudget.remainingMilliseconds(
        started: started,
        budgetMilliseconds: request.deadlineMilliseconds
      )
    )
  }
}
