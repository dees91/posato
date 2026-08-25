# Apple Design Guidance

## Provenance

- **Reviewed:** 2026-08-25
- **Source class:** authoritative platform design documentation
- **Publisher:** Apple
- **Scope:** current Apple-platform design principles, typography, color,
  accessibility, platform conventions, and app-icon direction

## Reviewed sources

- [Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines)
- [Design principles](https://developer.apple.com/design/human-interface-guidelines/design-principles)
- [Typography](https://developer.apple.com/design/human-interface-guidelines/typography)
- [Color](https://developer.apple.com/design/human-interface-guidelines/color)
- [Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility)
- [App icons](https://developer.apple.com/design/human-interface-guidelines/app-icons)
- [Designing for iOS](https://developer.apple.com/design/human-interface-guidelines/designing-for-ios)
- [Designing for macOS](https://developer.apple.com/design/human-interface-guidelines/designing-for-macos)

## Source claims relevant to Gate 3

`source-claim`: Apple's current design principles emphasize purpose, agency,
responsibility, familiarity, flexibility, simplicity, craft, and appropriate
delight. Agency includes keeping people informed and helping them recover;
responsibility includes privacy and transparent permission rationale.

`source-claim`: Apple recommends system typography for legibility and platform
fit, a restrained number of typefaces and weights, layouts that accommodate
larger text, and avoiding essential-text truncation. The current recommended
default and minimum sizes are 17 and 11 points on iOS and 13 and 10 points on
macOS.

`source-claim`: color should use semantic meaning consistently, work in light,
dark, and increased-contrast appearances, and never be the sole carrier of
state or interactivity. System colors are the default application foundation;
custom brand colors need appearance variants and contrast verification.

`source-claim`: accessibility begins during design. Current recommended control
sizes are 44 by 44 points by default on iOS, with a 28 by 28 point minimum, and
28 by 28 points by default on macOS, with a 20 by 20 point minimum. Interfaces
should avoid unnecessarily timed content and support platform accessibility
features and input methods.

`source-claim`: iOS should keep the primary task prominent, limit simultaneous
controls, adapt to Dark Mode and Dynamic Type, and respect common one-handed
reach and navigation patterns. macOS should use resizable system windows,
menus, keyboard commands, and an information density appropriate to a larger
display rather than stretching a mobile layout.

`source-claim`: app icons should communicate one simple, recognizable concept,
use a minimal number of shapes, avoid nonessential text and screenshots, and
remain visually consistent across supported platforms.

## Evidence limits

These pages guide product and interface design; they do not select a Compose
API, architecture, target graph, or implementation technique. Apple may revise
visual materials, components, and platform conventions. Exact implementation
guidance and design resources must be rechecked against the selected toolchain
and deployment targets when the first UI slice is built.
