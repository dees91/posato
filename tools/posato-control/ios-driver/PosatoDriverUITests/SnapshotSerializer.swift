import XCTest

/// Maps `XCUIElement.ElementType` to the unified role vocabulary shared with
/// the desktop bridge and to a readable platform role name.
enum ElementRoles {
  static let knownRoles: Set<String> = [
    "button", "textField", "text", "group", "window", "progress", "other", "any",
  ]

  static func role(for type: XCUIElement.ElementType) -> String {
    switch type {
    case .button:
      return "button"
    case .textField, .secureTextField, .textView, .searchField:
      return "textField"
    case .staticText:
      return "text"
    case .progressIndicator, .activityIndicator:
      return "progress"
    case .window:
      return "window"
    case .group, .other, .cell, .scrollView, .table, .collectionView:
      return "group"
    default:
      return "other"
    }
  }

  static func platformRole(for type: XCUIElement.ElementType) -> String {
    "XCUIElementType." + (platformNames[type] ?? "raw\(type.rawValue)")
  }

  private static let platformNames: [XCUIElement.ElementType: String] = [
    .any: "any", .other: "other", .application: "application", .group: "group",
    .window: "window", .sheet: "sheet", .drawer: "drawer", .alert: "alert",
    .dialog: "dialog", .button: "button", .radioButton: "radioButton",
    .radioGroup: "radioGroup", .checkBox: "checkBox",
    .disclosureTriangle: "disclosureTriangle", .popUpButton: "popUpButton",
    .comboBox: "comboBox", .menuButton: "menuButton", .toolbarButton: "toolbarButton",
    .popover: "popover", .keyboard: "keyboard", .key: "key",
    .navigationBar: "navigationBar", .tabBar: "tabBar", .tabGroup: "tabGroup",
    .toolbar: "toolbar", .statusBar: "statusBar", .table: "table", .tableRow: "tableRow",
    .tableColumn: "tableColumn", .outline: "outline", .outlineRow: "outlineRow",
    .browser: "browser", .collectionView: "collectionView", .slider: "slider",
    .pageIndicator: "pageIndicator", .progressIndicator: "progressIndicator",
    .activityIndicator: "activityIndicator", .segmentedControl: "segmentedControl",
    .picker: "picker", .pickerWheel: "pickerWheel", .switch: "switch", .toggle: "toggle",
    .link: "link", .image: "image", .icon: "icon", .searchField: "searchField",
    .scrollView: "scrollView", .scrollBar: "scrollBar", .staticText: "staticText",
    .textField: "textField", .secureTextField: "secureTextField",
    .datePicker: "datePicker", .textView: "textView", .menu: "menu", .menuItem: "menuItem",
    .menuBar: "menuBar", .menuBarItem: "menuBarItem", .map: "map", .webView: "webView",
    .incrementArrow: "incrementArrow", .decrementArrow: "decrementArrow",
    .timeline: "timeline", .ratingIndicator: "ratingIndicator",
    .valueIndicator: "valueIndicator", .splitGroup: "splitGroup", .splitter: "splitter",
    .relevanceIndicator: "relevanceIndicator", .colorWell: "colorWell", .helpTag: "helpTag",
    .matte: "matte", .dockItem: "dockItem", .ruler: "ruler", .rulerMarker: "rulerMarker",
    .grid: "grid", .levelIndicator: "levelIndicator", .cell: "cell",
    .layoutArea: "layoutArea", .layoutItem: "layoutItem", .handle: "handle",
    .stepper: "stepper", .tab: "tab", .touchBar: "touchBar", .statusItem: "statusItem",
  ]
}

/// Converts an `XCUIElementSnapshot` tree into the serializable node model.
enum SnapshotSerializer {
  /// Serializes `snapshot` and its descendants.
  ///
  /// - Parameter maxDepth: number of levels below the root to include;
  ///   `nil` includes the whole tree and `0` returns the root alone.
  static func node(from snapshot: XCUIElementSnapshot, maxDepth: Int? = nil) -> SnapshotNode {
    node(from: snapshot, depth: 0, maxDepth: maxDepth)
  }

  static func texts(of attributes: XCUIElementAttributes) -> [String] {
    [attributes.label, valueText(attributes.value), attributes.placeholderValue]
      .compactMap { $0 }
      .filter { !$0.isEmpty }
  }

  /// Keyboard focus as reported by XCUITest's `hasKeyboardFocus` attribute,
  /// falling back to `hasFocus` when the attribute is unavailable.
  static func keyboardFocus(of candidate: Any) -> Bool {
    let selector = NSSelectorFromString("hasKeyboardFocus")
    if let object = candidate as? NSObject, object.responds(to: selector),
      let focused = object.value(forKey: "hasKeyboardFocus") as? Bool
    {
      return focused
    }
    return (candidate as? XCUIElementAttributes)?.hasFocus ?? false
  }

  static func valueText(_ value: Any?) -> String? {
    guard let value else { return nil }
    if let text = value as? String {
      return text
    }
    return String(describing: value)
  }

  private static func node(from snapshot: XCUIElementSnapshot, depth: Int, maxDepth: Int?)
    -> SnapshotNode
  {
    let includeChildren = maxDepth.map { depth < $0 } ?? true
    let children =
      includeChildren
      ? snapshot.children.map { node(from: $0, depth: depth + 1, maxDepth: maxDepth) }
      : []
    return SnapshotNode(
      role: ElementRoles.role(for: snapshot.elementType),
      platformRole: ElementRoles.platformRole(for: snapshot.elementType),
      id: nonEmpty(snapshot.identifier),
      label: nonEmpty(snapshot.label),
      value: valueText(snapshot.value).flatMap(nonEmpty),
      placeholder: snapshot.placeholderValue.flatMap(nonEmpty),
      enabled: snapshot.isEnabled,
      focused: keyboardFocus(of: snapshot),
      frame: frame(of: snapshot.frame),
      children: children
    )
  }

  private static func nonEmpty(_ text: String) -> String? {
    text.isEmpty ? nil : text
  }

  private static func frame(of rect: CGRect) -> SnapshotFrame {
    SnapshotFrame(
      x: rounded(rect.origin.x),
      y: rounded(rect.origin.y),
      w: rounded(rect.size.width),
      h: rounded(rect.size.height)
    )
  }

  private static func rounded(_ value: CGFloat) -> Double {
    (Double(value) * 100).rounded() / 100
  }
}
