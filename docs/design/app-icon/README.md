# Posato application icon

The maintainer selected **Forest**, proposal B, on 2026-09-14. The accepted
visual contract is in [DESIGN.md](../../../DESIGN.md#application-icon).

## Source artwork and exports

| Source | Export | Use |
| --- | --- | --- |
| [forest.svg](forest.svg) | `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/forest.png` | Default iOS appearance |
| [forest-dark.svg](forest-dark.svg) | `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/forest-dark.png` | Dark iOS appearance |
| [forest-tinted.svg](forest-tinted.svg) | `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/forest-tinted.png` | Monochrome iOS tinted source |
| [forest-macos.svg](forest-macos.svg) | `desktopApp/Config/Posato.icns` | macOS icon |

The SVGs are the editable source. iOS PNGs are 1024 × 1024 RGB images with no
alpha channel or baked corner mask. The macOS source places the rounded tile
inside a 1024 × 1024 canvas with 100 units of transparent padding. The ICNS
contains 16, 32, 128, 256, and 512 point representations at 1× and 2×.

Export the SVGs as sRGB PNGs at their declared size; preserve the macOS alpha
channel and remove the alpha channel from opaque iOS images. For macOS,
export each iconset representation and pack the directory with Apple's
`iconutil -c icns`. No application dependency or runtime artwork generator is
needed. The earlier [proposal board](proposals/comparison.svg) is retained as
selection provenance.

## Platform hand-off

`IOS-003` selects `AppIcon` in the Xcode project. `MACOS-008` selects
`desktopApp/Config/Posato.icns` in its desktop packaging. Those wiring files
are outside this task's write surface. Installed appearance must be verified
using those configurations; the export alone is not launch or release proof.

Apple's [asset-catalog icon guidance](https://developer.apple.com/documentation/xcode/configuring-your-app-icon)
was checked on 2026-09-14. The asset catalog supplies explicit default, dark,
and tinted iOS sources and retains the iOS 18 deployment baseline. No Icon
Composer project, extra target, or new deployment requirement is introduced.
