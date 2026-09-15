import Foundation

enum BlockedPage {
  static func headline(sessionEndEpochMilliseconds: UInt64?) -> String {
    guard let sessionEndEpochMilliseconds else {
      return "This site is paused"
    }
    let date = Date(timeIntervalSince1970: TimeInterval(sessionEndEpochMilliseconds) / 1_000)
    let formatter = DateFormatter()
    formatter.locale = .current
    formatter.dateStyle = .none
    formatter.timeStyle = .short
    return "This site is paused until \(formatter.string(from: date))"
  }

  static func html(sessionEndEpochMilliseconds: UInt64?) -> Data {
    let title = escapedText(headline(sessionEndEpochMilliseconds: sessionEndEpochMilliseconds))
    let body = """
      <!doctype html>
      <html lang="en">
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
        <main>
          <div class="identity"><span class="mark" aria-hidden="true"></span>posato</div>
          <h1>\(title)</h1>
          <p>Return to Posato to change this session.</p>
        </main>
      </body>
      </html>
      """
    return Data(body.utf8)
  }

  private static let styles = """
    :root {
      color-scheme: light dark;
      --surface: #FFFEFA;
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
