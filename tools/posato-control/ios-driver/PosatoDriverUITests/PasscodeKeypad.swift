import UIKit
import XCTest

/// Finds the system passcode keypad in a screenshot, for when its process does not answer
/// accessibility queries.
///
/// On the test iPhone (iOS 26.5, 2026-10-22) the "Enter iPhone Passcode" keypad of the Screen Time
/// consent flow exposes its keys to XCUITest in Dark appearance but not in Light (`observed`:
/// "Error getting main window kAXErrorServerNotFound" for its process), while it still draws the
/// standard keypad. The keypad is a 3 x 4 grid of round
/// keys, centred horizontally, whose sizes in points are fixed; only its vertical position depends
/// on the screen. This type measures that position in a fresh screenshot by looking for ten keys
/// that contrast with the background between them, so a screen without the keypad never matches.
/// The geometry is that of the test iPhone (375 x 812 pt); on another screen size the keypad may
/// not be found, and an optional `pressKeys` then passes as if no keypad had appeared.
struct PasscodeKeypad {
  /// Measured on the test iPhone's 375 x 812 pt keypad (2026-10-22): 75 pt keys, centres 103 pt
  /// apart in a row and 91 pt apart in a column, the "5" key at 52.35 % of the screen height.
  private static let columnSpacing: CGFloat = 103
  private static let rowSpacing: CGFloat = 91
  private static let middleRowFraction: CGFloat = 0.5235
  private static let searchRange: CGFloat = 80
  private static let searchStep: CGFloat = 2
  /// Inside a key, beside its glyph, and the background halfway to the next key.
  private static let insideOffset: CGFloat = 27
  private static let gapOffset: CGFloat = 51.5
  private static let minimumContrast: CGFloat = 10
  private static let maximumSpread: CGFloat = 25
  private static let plateauShare: CGFloat = 0.8
  private static let stillTolerance: CGFloat = 2

  /// Centres of the keys "0" to "9" in points.
  let centres: [Character: CGPoint]

  /// Whether two measurements put the keypad at the same place, so it is no longer moving.
  func matches(_ other: PasscodeKeypad) -> Bool {
    guard let mine = centres["5"], let theirs = other.centres["5"] else { return false }
    return abs(mine.y - theirs.y) <= Self.stillTolerance
  }

  /// The keypad in the current screenshot, or nil when the screen does not show one.
  static func locate() -> PasscodeKeypad? {
    // The screenshot's size in points: the runner's own UIScreen reports a legacy 320 x 480.
    let image = XCUIScreen.main.screenshot().image
    let screen = image.size
    guard screen.height > screen.width,
      let bitmap = Bitmap(image)
    else { return nil }
    let guess = screen.height * middleRowFraction
    var scored: [(middle: CGFloat, score: CGFloat)] = []
    var middle = guess - searchRange
    while middle <= guess + searchRange {
      let score = contrast(of: centres(width: screen.width, middle: middle), in: bitmap)
      if score >= minimumContrast {
        scored.append((middle, score))
      }
      middle += searchStep
    }
    guard let best = scored.map(\.score).max() else { return nil }
    // The score stays level while the samples move inside the keys, so the centre of that
    // plateau is the centre of the row.
    let plateau = scored.filter { $0.score >= best * plateauShare }.map(\.middle)
    let centre = plateau.reduce(0, +) / CGFloat(plateau.count)
    return PasscodeKeypad(centres: centres(width: screen.width, middle: centre))
  }

  private static func centres(width: CGFloat, middle: CGFloat) -> [Character: CGPoint] {
    let columns = [-columnSpacing, 0, columnSpacing].map { width / 2 + $0 }
    let rows = (-1...2).map { middle + CGFloat($0) * rowSpacing }
    var result: [Character: CGPoint] = ["0": CGPoint(x: columns[1], y: rows[3])]
    for digit in 1...9 {
      let position = digit - 1
      result[Character(String(digit))] = CGPoint(x: columns[position % 3], y: rows[position / 3])
    }
    return result
  }

  /// The smallest key-to-background contrast over the ten keys, or 0 when the keys or the
  /// background differ among themselves as a keypad's would not.
  private static func contrast(of centres: [Character: CGPoint], in bitmap: Bitmap) -> CGFloat {
    var keys: [CGFloat] = []
    var gaps: [CGFloat] = []
    for centre in centres.values {
      let inside = [-insideOffset, insideOffset].map {
        bitmap.luminance(at: CGPoint(x: centre.x + $0, y: centre.y))
      }
      let gap = [-gapOffset, gapOffset].map {
        bitmap.luminance(at: CGPoint(x: centre.x + $0, y: centre.y))
      }
      keys.append(contentsOf: inside)
      gaps.append(contentsOf: gap)
    }
    guard let keyLow = keys.min(), let keyHigh = keys.max(), let gapLow = gaps.min(),
      let gapHigh = gaps.max(), keyHigh - keyLow <= maximumSpread,
      gapHigh - gapLow <= maximumSpread
    else { return 0 }
    return max(keyLow - gapHigh, gapLow - keyHigh, 0)
  }
}

/// A screenshot's pixels, addressed in points.
private struct Bitmap {
  private static let patch = 1
  private let pixels: [UInt8]
  private let width: Int
  private let height: Int
  private let scale: CGFloat

  init?(_ image: UIImage) {
    guard let cgImage = image.cgImage, image.size.width > 0 else { return nil }
    let width = cgImage.width
    let height = cgImage.height
    var buffer = [UInt8](repeating: 0, count: width * height * 4)
    let drawn = buffer.withUnsafeMutableBytes { bytes -> Bool in
      guard
        let context = CGContext(
          data: bytes.baseAddress, width: width, height: height, bitsPerComponent: 8,
          bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
      else { return false }
      context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
      return true
    }
    guard drawn else { return nil }
    self.pixels = buffer
    self.width = width
    self.height = height
    self.scale = CGFloat(width) / image.size.width
  }

  /// The mean luminance, 0 to 255, of a small patch around a point; 0 outside the image.
  func luminance(at point: CGPoint) -> CGFloat {
    let centreX = Int((point.x * scale).rounded())
    let centreY = Int((point.y * scale).rounded())
    var total: CGFloat = 0
    var count: CGFloat = 0
    for y in (centreY - Self.patch)...(centreY + Self.patch) {
      for x in (centreX - Self.patch)...(centreX + Self.patch) {
        guard x >= 0, y >= 0, x < width, y < height else { continue }
        let offset = (y * width + x) * 4
        total +=
          0.299 * CGFloat(pixels[offset]) + 0.587 * CGFloat(pixels[offset + 1]) + 0.114
          * CGFloat(pixels[offset + 2])
        count += 1
      }
    }
    return count > 0 ? total / count : 0
  }
}
