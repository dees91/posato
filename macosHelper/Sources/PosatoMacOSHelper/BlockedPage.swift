import Foundation

enum BlockedPage {
  static func headline(
    sessionEndEpochMilliseconds: UInt64?,
    locale: Locale = PauseCopy.locale()
  ) -> String {
    let base = PauseCopy.phrase("pause.site", locale: locale, fallback: "This site is paused")
    guard let sessionEndEpochMilliseconds else { return base }
    let time = shortTime(sessionEndEpochMilliseconds, locale: locale)
    let template = PauseCopy.phrase(
      "pause.site.until", locale: locale, fallback: "This site is paused until %@")
    return String(format: template, locale: locale, time)
  }

  static func html(
    sessionEndEpochMilliseconds: UInt64?,
    locale: Locale = PauseCopy.locale()
  ) -> Data {
    let title = escapedText(
      headline(sessionEndEpochMilliseconds: sessionEndEpochMilliseconds, locale: locale))
    let returnText = escapedText(
      PauseCopy.phrase(
        "pause.return", locale: locale, fallback: "Return to Posato to change this session."))
    return page(title: title, support: returnText, language: PauseCopy.language(locale: locale))
  }

  private static func page(title: String, support: String, language: String) -> Data {
    let body = """
      <!doctype html>
      <html lang="\(language)">
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="color-scheme" content="light dark">
        <title>\(title) — Posato</title>
        <style>
          \(styles)
        </style>
      </head>
      <body>
        <main \(PauseCopy.pageMarker)>
          <div class="identity"><span class="mark" aria-hidden="true"></span>posato</div>
          <h1>\(title)</h1>
          <p>\(support)</p>
        </main>
      </body>
      </html>
      """
    return Data(body.utf8)
  }

  /// The window variant (TARGETS-013): the opening time is the only variable, and the page holds no host.
  static func opensHTML(
    hour: Int,
    minute: Int,
    locale: Locale = PauseCopy.locale(),
    calendar: Calendar = .current
  ) -> Data {
    let time = clock((hour, minute), locale, calendar)
    let template = PauseCopy.phrase(
      "pause.site.opens", locale: locale, fallback: "This site opens at %@")
    let title = escapedText(String(format: template, locale: locale, time))
    let support = escapedText(
      PauseCopy.phrase(
        "pause.opens.return", locale: locale,
        fallback: "It is paused until then. Return to Posato to change this session."))
    return page(title: title, support: support, language: PauseCopy.language(locale: locale))
  }

  static func opensResponse(hour: Int, minute: Int, locale: Locale, calendar: Calendar) -> Data {
    return response(
      status: "200 OK",
      contentType: "text/html; charset=utf-8",
      body: opensHTML(hour: hour, minute: minute, locale: locale, calendar: calendar)
    )
  }

  /// A wall-clock time of day in the locale's short style, with no date.
  private static func clock(_ time: (Int, Int), _ locale: Locale, _ calendar: Calendar) -> String {
    let (hour, minute) = time
    var components = DateComponents()
    components.calendar = calendar
    components.timeZone = calendar.timeZone
    components.year = 2001
    components.month = 1
    components.day = 1
    components.hour = hour
    components.minute = minute
    let formatter = DateFormatter()
    formatter.locale = locale
    formatter.timeZone = calendar.timeZone
    formatter.dateStyle = .none
    formatter.timeStyle = .short
    guard let date = calendar.date(from: components) else {
      return String(format: "%02d:%02d", hour, minute)
    }
    return formatter.string(from: date)
  }

  private static func shortTime(_ epochMilliseconds: UInt64, locale: Locale) -> String {
    let date = Date(timeIntervalSince1970: TimeInterval(epochMilliseconds) / 1_000)
    let formatter = DateFormatter()
    formatter.locale = locale
    formatter.dateStyle = .none
    formatter.timeStyle = .short
    return formatter.string(from: date)
  }

  private static let styles = """
    :root {
      color-scheme: light dark;
      --surface: #F4F3EB;
      --ink: #18231F;
      --primary: #2E5D50;
      --muted: #626B63;
      font-family: -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
      color: var(--ink);
      background: var(--surface);
    }
    @media (prefers-color-scheme: dark) {
      :root {
        --surface: #1C2520;
        --ink: #F2F2E9;
        --primary: #A7C3A2;
        --muted: #AFB9AE;
      }
    }
    @media (prefers-contrast: more) {
      :root { --muted: var(--ink); }
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      display: flex;
      padding: 3rem min(1.5rem, 6vw);
    }
    main {
      width: 100%;
      max-width: 37.5rem;
      margin: auto;
      overflow-wrap: anywhere;
    }
    .identity {
      display: flex;
      align-items: center;
      gap: .75rem;
      margin-bottom: 3rem;
      font-size: 1.5625rem;
      font-weight: 600;
      letter-spacing: -.0625rem;
    }
    .mark {
      position: relative;
      flex: 0 0 1.5rem;
      height: 2rem;
      color: var(--primary);
    }
    .mark::before, .mark::after {
      content: "";
      position: absolute;
      width: .4375rem;
      height: 1.5625rem;
      border-radius: 1rem;
      background: currentColor;
      transform: rotate(8deg);
    }
    .mark::before { bottom: 0; left: .125rem; }
    .mark::after { top: 0; right: .125rem; }
    h1 {
      margin: 0;
      max-width: 18ch;
      font-size: 2.375rem;
      line-height: 1.16;
      font-weight: 500;
      letter-spacing: -.09375rem;
      text-wrap: balance;
    }
    @media (max-width: 600px) {
      h1 {
        font-size: 1.875rem;
        line-height: 1.2;
        letter-spacing: -.0625rem;
      }
    }
    p {
      margin: 1.5rem 0 0;
      max-width: 32rem;
      color: var(--muted);
      font-size: 1rem;
      line-height: 1.5625;
    }
    """

  private static func escapedText(_ text: String) -> String {
    text.replacingOccurrences(of: "&", with: "&amp;")
      .replacingOccurrences(of: "<", with: "&lt;")
      .replacingOccurrences(of: ">", with: "&gt;")
  }

  static func httpResponse(sessionEndEpochMilliseconds: UInt64?) -> Data {
    return response(
      status: "200 OK",
      contentType: "text/html; charset=utf-8",
      body: html(sessionEndEpochMilliseconds: sessionEndEpochMilliseconds)
    )
  }

  static func connectRejection() -> Data {
    return response(status: "403 Forbidden", contentType: nil, body: Data())
  }

  private static func response(status: String, contentType: String?, body: Data) -> Data {
    var header = "HTTP/1.1 \(status)\r\nConnection: close\r\nCache-Control: no-store\r\n"
    if let contentType {
      header += "Content-Type: \(contentType)\r\n"
    }
    header += "Content-Length: \(body.count)\r\n\r\n"
    var encoded = Data(header.utf8)
    encoded.append(body)
    return encoded
  }
}
