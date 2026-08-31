import Foundation
import IOKit
import IOKit.pwr_mgt

enum SystemPowerAction: Equatable {
  case restore
  case allowSleep
}

enum SystemPowerPolicy {
  // Swift cannot import IOKit's iokit_common_msg macros. These are their stable ABI values.
  private static let canSystemSleep: UInt32 = 0xE000_0270
  private static let systemWillSleep: UInt32 = 0xE000_0280
  private static let systemHasPoweredOn: UInt32 = 0xE000_0300

  static func actions(for messageType: UInt32) -> [SystemPowerAction] {
    switch messageType {
    case canSystemSleep:
      return [.allowSleep]
    case systemWillSleep:
      return [.restore, .allowSleep]
    case systemHasPoweredOn:
      return [.restore]
    default:
      return []
    }
  }
}

enum SystemPowerMonitorFailure: Error {
  case registration
}

final class SystemPowerMonitor: @unchecked Sendable {
  private let queue: DispatchQueue
  private let restore: @Sendable () -> Void
  private var rootPort: io_connect_t = IO_OBJECT_NULL
  private var notificationPort: IONotificationPortRef?
  private var notifier: io_object_t = IO_OBJECT_NULL

  init(
    queue: DispatchQueue,
    restore: @escaping @Sendable () -> Void
  ) {
    self.queue = queue
    self.restore = restore
  }

  deinit {
    stop()
  }

  func start() throws {
    guard rootPort == IO_OBJECT_NULL else {
      return
    }
    var port: IONotificationPortRef?
    var registeredNotifier: io_object_t = IO_OBJECT_NULL
    let registeredRootPort = IORegisterForSystemPower(
      Unmanaged.passUnretained(self).toOpaque(),
      &port,
      handleSystemPowerNotification,
      &registeredNotifier
    )
    guard registeredRootPort != IO_OBJECT_NULL, let port else {
      if registeredNotifier != IO_OBJECT_NULL {
        IODeregisterForSystemPower(&registeredNotifier)
      }
      if let port {
        IONotificationPortDestroy(port)
      }
      if registeredRootPort != IO_OBJECT_NULL {
        IOServiceClose(registeredRootPort)
      }
      throw SystemPowerMonitorFailure.registration
    }
    rootPort = registeredRootPort
    notificationPort = port
    notifier = registeredNotifier
    IONotificationPortSetDispatchQueue(port, queue)
  }

  func stop() {
    var registeredNotifier = notifier
    let port = notificationPort
    let registeredRootPort = rootPort
    notifier = IO_OBJECT_NULL
    notificationPort = nil
    rootPort = IO_OBJECT_NULL

    if registeredNotifier != IO_OBJECT_NULL {
      IODeregisterForSystemPower(&registeredNotifier)
    }
    if let port {
      IONotificationPortSetDispatchQueue(port, nil)
      IONotificationPortDestroy(port)
    }
    if registeredRootPort != IO_OBJECT_NULL {
      IOServiceClose(registeredRootPort)
    }
  }

  fileprivate func handle(
    messageType: UInt32,
    argument: UnsafeMutableRawPointer?
  ) {
    for action in SystemPowerPolicy.actions(for: messageType) {
      switch action {
      case .restore:
        restore()
      case .allowSleep:
        IOAllowPowerChange(rootPort, Int(bitPattern: argument))
      }
    }
  }
}

private func handleSystemPowerNotification(
  _ reference: UnsafeMutableRawPointer?,
  _: io_service_t,
  messageType: UInt32,
  argument: UnsafeMutableRawPointer?
) {
  guard let reference else {
    return
  }
  Unmanaged<SystemPowerMonitor>
    .fromOpaque(reference)
    .takeUnretainedValue()
    .handle(messageType: messageType, argument: argument)
}
