// Posato accessibility bridge for the macOS desktop application.
//
// A single-file helper that the `posato-control` CLI compiles on demand. It
// reads the accessibility tree of a running application, performs button
// presses, types text through keyboard events, and reports window identifiers
// and permission state. All output is JSON on standard output.

import AppKit
import ApplicationServices
import Foundation

struct BridgeError: Error {
  let code: String
  let message: String
}

struct Frame: Encodable {
  let x: Double
  let y: Double
  let w: Double
  let h: Double
}

struct Node: Encodable {
  let role: String
  let id: String?
  let label: String?
  let value: String?
  let enabled: Bool
  let focused: Bool
  let frame: Frame
  let platformRole: String
  let path: String
  let children: [Node]
}

struct WindowInfo: Encodable {
  let id: Int
  let layer: Int
  let name: String?
  let x: Double
  let y: Double
  let w: Double
  let h: Double
}

enum Bridge {
  static let keyCodes: [String: CGKeyCode] = [
    "return": 36, "enter": 76, "tab": 48, "escape": 53, "delete": 51, "space": 49,
    "up": 126, "down": 125, "left": 123, "right": 124,
    "a": 0, "s": 1, "d": 2, "f": 3, "h": 4, "g": 5, "z": 6, "x": 7, "c": 8, "v": 9,
    "b": 11, "q": 12, "w": 13, "e": 14, "r": 15, "y": 16, "t": 17, "1": 18, "2": 19,
    "3": 20, "4": 21, "6": 22, "5": 23, "9": 25, "7": 26, "8": 28, "0": 29,
    "o": 31, "u": 32, "i": 34, "p": 35, "l": 37, "j": 38, "k": 40, "n": 45, "m": 46,
  ]

  static func attribute(_ element: AXUIElement, _ name: String) -> AnyObject? {
    var value: AnyObject?
    let result = AXUIElementCopyAttributeValue(element, name as CFString, &value)
    return result == .success ? value : nil
  }

  static func string(_ element: AXUIElement, _ name: String) -> String? {
    guard let raw = attribute(element, name) else { return nil }
    if let text = raw as? String { return text.isEmpty ? nil : text }
    if let number = raw as? NSNumber { return number.stringValue }
    return nil
  }

  static func bool(_ element: AXUIElement, _ name: String) -> Bool? {
    attribute(element, name) as? Bool
  }

  static func frame(_ element: AXUIElement) -> Frame {
    var point = CGPoint.zero
    var size = CGSize.zero
    if let position = attribute(element, kAXPositionAttribute) {
      AXValueGetValue(position as! AXValue, .cgPoint, &point)
    }
    if let dimensions = attribute(element, kAXSizeAttribute) {
      AXValueGetValue(dimensions as! AXValue, .cgSize, &size)
    }
    return Frame(x: point.x, y: point.y, w: size.width, h: size.height)
  }

  static func unifiedRole(_ role: String) -> String {
    switch role {
    case kAXButtonRole, kAXPopUpButtonRole, kAXMenuButtonRole, kAXCheckBoxRole, kAXRadioButtonRole:
      return "button"
    case kAXTextFieldRole, kAXTextAreaRole, kAXComboBoxRole:
      return "textField"
    case kAXStaticTextRole:
      return "text"
    case kAXGroupRole, kAXScrollAreaRole, kAXListRole, kAXTableRole, kAXOutlineRole, kAXRowRole,
      kAXCellRole:
      return "group"
    case kAXWindowRole:
      return "window"
    case kAXProgressIndicatorRole, kAXBusyIndicatorRole:
      return "progress"
    default:
      return "other"
    }
  }

  static func children(_ element: AXUIElement) -> [AXUIElement] {
    attribute(element, kAXChildrenAttribute) as? [AXUIElement] ?? []
  }

  static func node(_ element: AXUIElement, path: String, depth: Int, maxDepth: Int) -> Node {
    let role = string(element, kAXRoleAttribute) ?? "AXUnknown"
    let subrole = string(element, kAXSubroleAttribute)
    let title = string(element, kAXTitleAttribute)
    let description = string(element, kAXDescriptionAttribute)
    let placeholder = string(element, kAXPlaceholderValueAttribute)
    let unified = unifiedRole(role)
    let rawValue = string(element, kAXValueAttribute)
    let label: String?
    let value: String?
    if unified == "text" {
      label = description ?? title ?? rawValue
      value = rawValue
    } else if unified == "group" || unified == "window" {
      label = title ?? description
      value = nil
    } else {
      label = title ?? description ?? placeholder
      value = rawValue
    }
    let childNodes: [Node]
    if depth < maxDepth {
      childNodes = children(element).enumerated().map { index, child in
        node(
          child, path: path.isEmpty ? "\(index)" : "\(path)/\(index)", depth: depth + 1,
          maxDepth: maxDepth)
      }
    } else {
      childNodes = []
    }
    return Node(
      role: unified,
      id: string(element, kAXIdentifierAttribute),
      label: label,
      value: value,
      enabled: bool(element, kAXEnabledAttribute) ?? true,
      focused: bool(element, kAXFocusedAttribute) ?? false,
      frame: frame(element),
      platformRole: subrole.map { "\(role)/\($0)" } ?? role,
      path: path,
      children: childNodes
    )
  }

  static func snapshot(pid: pid_t, maxDepth: Int) -> Node {
    let application = AXUIElementCreateApplication(pid)
    let windows = children(application).filter { string($0, kAXRoleAttribute) == kAXWindowRole }
    let windowNodes = windows.enumerated().map { index, window in
      node(window, path: "\(index)", depth: 1, maxDepth: maxDepth)
    }
    return Node(
      role: "other",
      id: nil,
      label: string(application, kAXTitleAttribute),
      value: nil,
      enabled: true,
      focused: false,
      frame: Frame(x: 0, y: 0, w: 0, h: 0),
      platformRole: kAXApplicationRole,
      path: "",
      children: windowNodes
    )
  }

  static func resolve(pid: pid_t, path: String) throws -> AXUIElement {
    let application = AXUIElementCreateApplication(pid)
    let windows = children(application).filter { string($0, kAXRoleAttribute) == kAXWindowRole }
    let indexes = path.split(separator: "/").compactMap { Int($0) }
    guard let first = indexes.first, windows.indices.contains(first) else {
      throw BridgeError(code: "ELEMENT_NOT_FOUND", message: "No window at path \(path).")
    }
    var current = windows[first]
    for index in indexes.dropFirst() {
      let next = children(current)
      guard next.indices.contains(index) else {
        throw BridgeError(
          code: "ELEMENT_NOT_FOUND",
          message: "Path \(path) no longer resolves; take a new snapshot.")
      }
      current = next[index]
    }
    return current
  }

  static func windows(pid: pid_t) -> [WindowInfo] {
    let options: CGWindowListOption = [.optionOnScreenOnly, .excludeDesktopElements]
    let list = CGWindowListCopyWindowInfo(options, kCGNullWindowID) as? [[String: Any]] ?? []
    return list.compactMap { info in
      guard (info[kCGWindowOwnerPID as String] as? pid_t) == pid,
        let number = info[kCGWindowNumber as String] as? Int
      else { return nil }
      let bounds = info[kCGWindowBounds as String] as? [String: Double] ?? [:]
      return WindowInfo(
        id: number,
        layer: info[kCGWindowLayer as String] as? Int ?? 0,
        name: info[kCGWindowName as String] as? String,
        x: bounds["X"] ?? 0,
        y: bounds["Y"] ?? 0,
        w: bounds["Width"] ?? 0,
        h: bounds["Height"] ?? 0
      )
    }
  }

  static func press(_ element: AXUIElement) throws {
    let result = AXUIElementPerformAction(element, kAXPressAction as CFString)
    guard result == .success else {
      throw BridgeError(
        code: "AX_ERROR", message: "AXPress failed with AXError \(result.rawValue).")
    }
  }

  static func postKey(_ code: CGKeyCode, flags: CGEventFlags, pid: pid_t) {
    guard let down = CGEvent(keyboardEventSource: nil, virtualKey: code, keyDown: true),
      let up = CGEvent(keyboardEventSource: nil, virtualKey: code, keyDown: false)
    else { return }
    down.flags = flags
    up.flags = flags
    down.postToPid(pid)
    up.postToPid(pid)
    usleep(20_000)
  }

  static func typeText(_ text: String, pid: pid_t) {
    for scalar in text.unicodeScalars {
      var units = Array(String(scalar).utf16)
      guard let down = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: true),
        let up = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: false)
      else { continue }
      down.keyboardSetUnicodeString(stringLength: units.count, unicodeString: &units)
      up.keyboardSetUnicodeString(stringLength: units.count, unicodeString: &units)
      down.postToPid(pid)
      up.postToPid(pid)
      usleep(8_000)
    }
  }

  static func flags(from names: [String]) -> CGEventFlags {
    var flags: CGEventFlags = []
    for name in names {
      switch name.lowercased() {
      case "cmd", "command": flags.insert(.maskCommand)
      case "shift": flags.insert(.maskShift)
      case "alt", "option": flags.insert(.maskAlternate)
      case "ctrl", "control": flags.insert(.maskControl)
      default: break
      }
    }
    return flags
  }

  static func key(named name: String, modifiers: [String], pid: pid_t) throws {
    guard let code = keyCodes[name.lowercased()] else {
      throw BridgeError(code: "SCENARIO_INVALID", message: "Unknown key '\(name)'.")
    }
    postKey(code, flags: flags(from: modifiers), pid: pid)
  }

  static func type(into element: AXUIElement, text: String, clear: Bool, submit: Bool, pid: pid_t)
    throws -> String?
  {
    try press(element)
    usleep(250_000)
    if clear {
      postKey(keyCodes["a"]!, flags: .maskCommand, pid: pid)
      postKey(keyCodes["delete"]!, flags: [], pid: pid)
      usleep(100_000)
    }
    typeText(text, pid: pid)
    usleep(200_000)
    if submit {
      postKey(keyCodes["return"]!, flags: [], pid: pid)
      usleep(100_000)
    }
    return string(element, kAXValueAttribute)
  }
}

func emit<T: Encodable>(_ value: T) {
  let encoder = JSONEncoder()
  encoder.outputFormatting = [.sortedKeys]
  let data = try! encoder.encode(value)
  FileHandle.standardOutput.write(data)
  FileHandle.standardOutput.write("\n".data(using: .utf8)!)
}

func fail(_ error: BridgeError) -> Never {
  emit(["error": ["code": error.code, "message": error.message]])
  exit(1)
}

func argument(_ index: Int, _ name: String) throws -> String {
  let arguments = CommandLine.arguments
  guard arguments.count > index else {
    throw BridgeError(code: "USAGE", message: "Missing argument <\(name)>.")
  }
  return arguments[index]
}

func pidArgument(_ index: Int) throws -> pid_t {
  guard let pid = pid_t(try argument(index, "pid")) else {
    throw BridgeError(code: "USAGE", message: "The pid must be an integer.")
  }
  return pid
}

do {
  let command = try argument(1, "command")
  switch command {
  case "permissions":
    let options =
      [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: false] as CFDictionary
    emit([
      "accessibility": AXIsProcessTrustedWithOptions(options),
      "screenRecording": CGPreflightScreenCaptureAccess(),
    ])
  case "request-permissions":
    let options =
      [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: true] as CFDictionary
    let accessibility = AXIsProcessTrustedWithOptions(options)
    let screen = CGRequestScreenCaptureAccess()
    emit(["accessibility": accessibility, "screenRecording": screen])
  case "windows":
    emit(Bridge.windows(pid: try pidArgument(2)))
  case "snapshot":
    let pid = try pidArgument(2)
    let maxDepth = CommandLine.arguments.count > 3 ? Int(CommandLine.arguments[3]) ?? 64 : 64
    emit(Bridge.snapshot(pid: pid, maxDepth: maxDepth))
  case "press":
    let pid = try pidArgument(2)
    try Bridge.press(try Bridge.resolve(pid: pid, path: try argument(3, "path")))
    emit(["ok": true])
  case "type":
    let pid = try pidArgument(2)
    let element = try Bridge.resolve(pid: pid, path: try argument(3, "path"))
    guard let data = Data(base64Encoded: try argument(4, "textBase64")),
      let text = String(data: data, encoding: .utf8)
    else {
      throw BridgeError(code: "USAGE", message: "The text must be base64-encoded UTF-8.")
    }
    let clear = (try? argument(5, "clear")) == "1"
    let submit = (try? argument(6, "submit")) == "1"
    let value = try Bridge.type(into: element, text: text, clear: clear, submit: submit, pid: pid)
    emit(["ok": "true", "value": value ?? ""])
  case "key":
    let pid = try pidArgument(2)
    let modifiers =
      CommandLine.arguments.count > 4
      ? CommandLine.arguments[4].split(separator: ",").map(String.init) : []
    try Bridge.key(named: try argument(3, "key"), modifiers: modifiers, pid: pid)
    emit(["ok": true])
  default:
    throw BridgeError(code: "USAGE", message: "Unknown command '\(command)'.")
  }
} catch let error as BridgeError {
  fail(error)
} catch {
  fail(BridgeError(code: "AX_ERROR", message: "\(error)"))
}
