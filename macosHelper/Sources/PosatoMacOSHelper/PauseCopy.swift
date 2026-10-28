import Foundation

/// Pause-page copy for the helper. The helper is a separate process, so it reads the
/// Posato app's per-app language instead of only the system language. Strings live in
/// the helper bundle; a missing bundle keeps the English fallback.
enum PauseCopy {
  static let applicationBundleIdentifier = "app.posato.macos"
  static let pageMarker = "data-posato-page=\"paused-site\""

  static func locale(defaults: UserDefaults = .standard) -> Locale {
    let domain = defaults.persistentDomain(forName: applicationBundleIdentifier)
    let languages = domain?["AppleLanguages"] as? [String]
    if let tag = languages?.first, !tag.isEmpty {
      return Locale(identifier: tag)
    }
    return .current
  }

  static func language(locale: Locale) -> String {
    guard localizationBundle(locale: locale) != nil else { return "en" }
    return code(locale)
  }

  static func phrase(_ key: String, locale: Locale, fallback: String) -> String {
    guard let bundle = localizationBundle(locale: locale) else { return fallback }
    let resolved = bundle.localizedString(forKey: key, value: fallback, table: "Localizable")
    return resolved == key ? fallback : resolved
  }

  /// The language part of the locale, such as `pl` for `pl_PL`; `language` keeps it only when
  /// the helper bundle has that language's strings.
  private static func code(_ locale: Locale) -> String {
    let identifier = locale.identifier.lowercased()
    guard let language = identifier.split(whereSeparator: { $0 == "-" || $0 == "_" }).first,
      (2...3).contains(language.count), language.allSatisfy({ ("a"..."z").contains($0) })
    else {
      return "en"
    }
    return String(language)
  }

  private static func localizationBundle(locale: Locale) -> Bundle? {
    let directory = code(locale) + ".lproj"
    guard let url = Bundle.main.resourceURL?.appendingPathComponent(directory, isDirectory: true)
    else {
      return nil
    }
    return Bundle(url: url)
  }
}
