# Posato prototype Compose design system

A reusable Compose Multiplatform library for the [native interaction prototype](../README.md),
with a native desktop component catalog. The historical HTML refinement at `bfc4a49`,
retained under `archive/mvp-interaction-flow-html`, is its
visual source. [The prototype design reference](../DESIGN.md) records the current
components and their native use. [The root DESIGN.md](../../../DESIGN.md) remains
the accepted production design authority.

This is a prototype library, not a replacement for the production design system
under `shared/`. Neither application depends on it. It does not implement blocking,
permissions, synchronization, persistence, domain validation, or the HTML reducer.
Exact geometry and sample values remain design-study choices.

## Run and verify

Use the repository's normal JDK, Android SDK, and Apple toolchain setup. From the
repository root:

```sh
./gradlew :prototypeDesignSystem:run
./gradlew :prototypeDesignSystem:verifyDesignSystem
```

The first command opens **Posato · Component Catalog** on Apple Silicon macOS.
The second runs ktlint and Detekt and compiles JVM, iOS device, iOS simulator,
and the Android preview target. The root `quality` gate includes this check.
No dependency versions or quality exceptions are introduced by this module.

The Android target exists for IDE preview tooling only; it is not a product
platform. Native compilation alone does not demonstrate iOS runtime behavior.

## Packages and ownership

| Package | Responsibility |
| --- | --- |
| `app.posato.prototype.designsystem` | Theme, tokens, reusable controls, and product presentation patterns |
| `app.posato.prototype.designsystem.workbench` | Inspection tools and simulated device chrome, never production app chrome |
| `app.posato.prototype.catalog` | Synthetic examples, demo state, catalog navigation, and previews |

The module has no dependency on `shared/`, `desktopApp/`, or `iosApp/`.
Consumers opt in with `implementation(project(":prototypeDesignSystem"))` only
when prototype reuse is intended. Only `:prototypeApp`, not a production app,
uses this library as its application design system.

## Foundations

- `PosatoPrototypeTheme` supplies Material 3 colors, typography, and shapes.
  `darkTheme` follows the system by default and can be overridden. `highContrast`
  strengthens secondary text and borders; it is an inspectable variant, not a
  certification of accessibility conformance.
- `PosatoPalette` names the light and dark semantic schemes. The light study uses
  moss `#2E5D50`, paper `#FFFEFA`, ink `#18231F`, and mist `#E4EBE5`; the dark study
  uses pale moss `#A7C3A2`, surface `#1C2520`, and ink `#F2F2E9`. Caution and critical
  states have separate foreground/container pairs.
- `PosatoTypography` uses scalable `sp` roles and the platform-resolved sans-serif
  family. It does not claim SF typography parity or automatic Apple Dynamic Type.
- `PosatoSpace` centralizes 4, 8, 12, 16, 24, 32, and 48 dp rhythm plus a 1 dp rule.
  `PosatoSize` owns control, icon, content-width, and prototype-frame dimensions.
  Controls have a minimum 44 dp target and may grow with content.
- `PosatoShapes` uses restrained rounded rectangles. The phone frame is the one
  explicitly prototype-only large-radius treatment. Components add no decorative
  shadows, gradients, progress rings, or animations.
- `PosatoMark`, `PosatoWordmark`, and `PosatoIntervalArtwork` draw the open interval
  directly in Compose. `PosatoIcons` contains the study's line icons and ellipsis; no bitmap,
  remote asset, icon font, or additional icon dependency is needed.

## Complete prototype component map

HTML classes below identify visual families, not public APIs to preserve.
Screen-specific combinations use slots instead of one wrapper per CSS class.

| HTML family / use | Compose API or composition |
| --- | --- |
| `brand`, `interval-mark`, `interval-art`, line icons | `PosatoMark`, `PosatoWordmark`, `PosatoIntervalArtwork`, `PosatoIcon`, `PosatoIcons` |
| Eyebrows, headings, body, secondary copy | `PosatoEyebrow`, `PosatoTitle`, `PosatoBody`, `PosatoCaption`, `PosatoHeading` |
| Primary, secondary, quiet, destructive, row/tool actions | `PosatoButton` with all five `PosatoButtonStyle` variants, including `Compact` |
| Appearance and inspection toggles | `PosatoToggleButton` |
| Duration presets and other exclusive choices | `PosatoDurationChoice`, `PosatoChoiceTile`, `PosatoChoiceGroup` |
| Application picker checkbox rows | `PosatoSelectionRow` |
| Website inputs and inline errors | `PosatoTextField`, `PosatoFieldMessage`; optional line limits and trailing-action slot, with `inputModifier` for field focus |
| Bounded custom-number selection | `PosatoNumberWheel` with caller-owned value/range, snapping scroll, one-unit arrows, keyboard and adjustable semantics |
| Website search with clear and keyboard submission | `PosatoSearchField` with caller-owned `TextFieldState` |
| `panel`, section headings, separators, action clusters | `PosatoPanel`, `PosatoSection`, `PosatoSectionHeader`, `PosatoDivider`, `PosatoActionRow` |
| `surface-list`, `surface-row`, item identity and tags | `PosatoItemList`, `PosatoItemRow`, `PosatoItemSymbol`, `PosatoBadge` |
| Clickable selection summaries and website rows | `PosatoDisclosureRow` with headline, supporting, leading, and trailing slots |
| Labelled per-item options | `PosatoItemMenu` and `PosatoItemMenuAction`, with icon slots, destructive color, and a dismiss callback |
| Ready/session state, warning, failure, recovery notices | `PosatoStatusLabel`, `PosatoNotice` with neutral, positive, caution, or critical tone |
| Session end-time and synchronization status | `PosatoEndTime`, `PosatoSyncFooter` |
| Privacy statements and local-device identity | `PosatoPrivacyPoint`, `PosatoDeviceLabel` |
| Catalog sidebar, adaptive catalog shell, onboarding progress | `PosatoSidebar`, `PosatoNavigationItem`, `PosatoSetupStep`, `PosatoAppScaffold` |
| Platform-specific application shell | `PosatoNavigationScaffold` with explicit bottom/sidebar placement and header, navigation, and adaptive body slots |
| iPhone main destinations | `PosatoBottomNavigation` and `PosatoBottomNavigationItem` with icon and label slots |
| Mac main destinations | `PosatoSidebarNavigationItem` with icon and label slots |
| Counted in-screen category tabs | `PosatoTabBar` shared segmented surface and `PosatoTab` with padded labels, optional counts, and disabled state |
| Hero, ready/empty presentation, confirmation layout | `PosatoHero`, `PosatoEmptyState`; compose headings, notices, and action rows for confirmation |
| Study header and preview toolbar | `PrototypeHeader`; `PosatoSectionHeader` action slot with choice and button controls |
| Mac/iPhone frame and simulated window/status bar | `PrototypeFrame`, `PrototypeWindowChrome`, `PrototypeDevice` |
| State inspector, facts, outcome | `PrototypeInspector`, `PrototypeStateFact` |
| Guided scenario tabs and step cards | `PosatoNavigationItem`, `PrototypeWalkthroughStep` |
| Moment shortcuts, simulation dock, Free play groups | `PrototypeMomentStrip`, `PosatoSection`, `PosatoActionRow`, compact buttons |
| Study footer and limits | `PrototypeFooter` |

Browser-only skip links, fullscreen API calls, live-region plumbing, and DOM
visibility attributes are not native widgets. Use focus order, semantics, caller
state, and native window APIs at their owning boundary. The catalog does not
pretend its demo callbacks implement the HTML scenarios.

The number wheel does not wrap at its bounds. The screen owns duration conversion
and dependent hour/minute ranges; the design system owns only integer selection.
The text-field container modifier covers the field and its message; use
`inputModifier` when focus must target the input instead of a trailing action.
The tab bar owns the shared mist surface, inset, spacing, and selection-group
semantics; callers own placement and segment widths. Segments use
centered padded labels with quiet counts, and selection adds a paper surface
and outline. Inactive segments keep normal text contrast inside the shared surface.
Disabled tabs retain native input semantics and reduce text opacity. The native
prototype keeps these segments for Websites/Apps. Main destinations use separate
bottom and sidebar items with icon/label slots, mist selection, and disabled semantics.
`PosatoNavigationScaffold` keeps placement independent of content width and retains
slot state when placement changes. The application owns destination selection,
onboarding/keyboard visibility, and safe-area insets; the scaffold adds no insets.
The desktop host reserves at least a phone-width body beside its sidebar. The
catalog's older width-adaptive `PosatoAppScaffold` remains independent.
Section-header actions align vertically
with the title, and the sync footer does not duplicate a preceding row separator.

## Reuse example

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoHeading
import app.posato.prototype.designsystem.PosatoPrototypeTheme
import app.posato.prototype.designsystem.PosatoTextField

@Composable
fun WebsiteStudy(
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
) {
    val input = rememberTextFieldState()
    PosatoPrototypeTheme {
        Column(modifier = modifier) {
            PosatoHeading(title = "Add a website", description = "Choose a little room to focus.")
            PosatoTextField(
                state = input,
                label = "Website domain",
                errorMessage = errorMessage,
                onSubmit = { onSave(input.text.toString()) },
            )
            PosatoButton(onClick = { onSave(input.text.toString()) }) {
                Text("Save website")
            }
        }
    }
}
```

The calling screen owns validation, errors, selection, time formatting, navigation,
and callbacks. Text fields accept framework `TextFieldState`; the library does not
store an alternate text value or copy it into an application model. Optional regions
use nullable composable slots. Content strings are supplied by the caller so the
library does not prescribe localization or product policy. Workbench chrome alone
contains deliberately synthetic study labels and a fixed sample clock.

Each public visual component applies its `modifier` to its root. Parent layouts
own placement and spacing. `PosatoAppScaffold` needs bounded window constraints and
switches at 600 dp: top navigation below that width, sidebar above it. Its movable
slots preserve remembered content state when crossing that breakpoint. The catalog
deliberately resets each section's sample state when navigating to another section.

## Catalog, states, and accessibility

The six catalog sections cover foundations, controls, forms, feedback, product
patterns, and prototype tools. Try the appearance switches, duration choices,
checkboxes, error presentation, frame switch, walkthrough progress, and action
buttons. A persistent feedback strip reports synthetic callback invocations.
Text fields are editable; no entry is saved or sent anywhere.
Forms include search and clearing; product patterns include bounded selection
summaries and item menus. The native app owns lazy lists, filtering, tab/scroll
state, and read-only detail presentation rather than embedding those policies in
the design-system primitives.

Two common-source previews use the same `CatalogPreviewDataProvider`: compact
390 × 1000 and expanded 1200 × 900. Each has 18 deterministic cases: six sections
in light, dark, and high-contrast/enlarged-text treatments. The runtime catalog also
allows these appearance options to be combined independently; larger text multiplies
the existing font scale by 1.6. This follows the existing
[Compose Multiplatform preview setup](https://kotlinlang.org/docs/multiplatform/compose-previews.html),
including the preview-only Android runtime dependency.

Controls use Material/foundation input behavior and expose selected, checked,
disabled, heading, and error semantics where applicable. Decorative icons use a
null content description; meaningful icon-only content needs a caller-supplied
description. Notices opt into polite live announcements with `announceChanges`;
avoid enabling it for static text or announcing the same change twice. Status is
communicated with text, not color alone.

Manually inspect compact and expanded layouts, light/dark appearance, increased
contrast, enlarged text, keyboard focus, field editing, and disabled controls.
Screenshots and accessibility trees are local evidence under ignored `build/verification/`,
not golden tests. This static presentation library adds no Compose UI/screenshot
test framework. Before any production adoption, verify real application behavior,
Apple assistive technologies, platform fonts/scaling, and native interaction rules
through the application's accepted verification route.
