import Darwin
import Foundation

enum ParentLaunchEnvironment {
  static let javaOptionVariables: Set<String> = [
    "JAVA_TOOL_OPTIONS",
    "_JAVA_OPTIONS",
    "JDK_JAVA_OPTIONS",
  ]

  static func isCleanForParent() -> Bool {
    guard let arguments = processArguments(of: getppid()) else {
      return false
    }
    return isClean(processArguments: arguments)
  }

  static func isClean(processArguments: Data) -> Bool {
    let bytes = [UInt8](processArguments)
    let countBytes = MemoryLayout<Int32>.size
    guard bytes.count > countBytes else {
      return false
    }
    let argumentCount = bytes.prefix(countBytes).withUnsafeBytes { $0.loadUnaligned(as: Int32.self) }
    guard argumentCount >= 0 else {
      return false
    }
    let strings = bytes.dropFirst(countBytes).split(separator: 0, omittingEmptySubsequences: false)
    guard let executable = strings.first, !executable.isEmpty,
      strings.dropFirst().count(where: { !$0.isEmpty }) >= Int(argumentCount)
    else {
      return false
    }
    return !strings.dropFirst().contains { entry in
      guard let separator = entry.firstIndex(of: UInt8(ascii: "=")) else {
        return false
      }
      let name = String(decoding: entry[entry.startIndex..<separator], as: UTF8.self)
      return javaOptionVariables.contains(name)
    }
  }

  private static func processArguments(of processIdentifier: pid_t) -> Data? {
    var name: [Int32] = [CTL_KERN, KERN_PROCARGS2, processIdentifier]
    var size = 0
    guard sysctl(&name, 3, nil, &size, nil, 0) == 0, size > 0 else {
      return nil
    }
    var buffer = [UInt8](repeating: 0, count: size)
    guard sysctl(&name, 3, &buffer, &size, nil, 0) == 0 else {
      return nil
    }
    return Data(buffer.prefix(size))
  }
}
