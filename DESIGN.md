---
schema: design-md/v1
name: "Posato"
sources:
  - type: source-code
    path: "prototypes/mvp-interaction-flow/compose/src/commonMain/kotlin/app/posato/prototype/designsystem/PosatoPalette.kt"
    platform: cross-platform
  - type: source-code
    path: "prototypes/mvp-interaction-flow/compose/src/commonMain/kotlin/app/posato/prototype/designsystem/PosatoTypography.kt"
    platform: cross-platform
  - type: source-code
    path: "prototypes/mvp-interaction-flow/compose/src/commonMain/kotlin/app/posato/prototype/designsystem/PosatoTokens.kt"
    platform: cross-platform
  - type: source-code
    path: "prototypes/mvp-interaction-flow/app/src/commonMain/kotlin/app/posato/prototype/ui/PrototypeApplicationLayout.kt"
    platform: cross-platform
  - type: source-code
    path: "prototypes/mvp-interaction-flow/app/src/commonMain/kotlin/app/posato/prototype/ui/PrototypeDestinationNavigation.kt"
    platform: cross-platform
  - type: source-code
    path: "prototypes/mvp-interaction-flow/app/src/jvmMain/kotlin/app/posato/prototype/Main.kt"
    platform: desktop
  - type: source-code
    path: "prototypes/mvp-interaction-flow/README.md"
    platform: cross-platform
  - type: source-code
    path: "docs/wiki/topics/brand-and-design-baseline.md"
    platform: cross-platform
  - type: source-code
    path: "docs/product/mvp-scope.md"
    platform: cross-platform
  - type: source-code
    path: "docs/product/product-identity.md"
    platform: cross-platform
  - type: source-code
    path: "docs/wiki/sources/apple-design-guidance.md"
    platform: cross-platform
confidence:
  overall: high
  colors: high
  typography: high
  spacing: high
  components: high
  responsive: high
  interaction: high
  accessibility: low
tokens:
  colors:
    brand-ink: "#18231F"
    brand-paper: "#F5F2EA"
    brand-moss: "#2E5D50"
    brand-clay: "#A54B35"
    brand-mist: "#DCE4DF"
    dark-canvas-reference: "#121A17"
    dark-text-reference: "#F7F5EF"
    dark-moss-reference: "#76B29E"
    dark-clay-reference: "#E49A82"
    prototype-light-surface: "#FFFEFA"
    prototype-light-text: "#18231F"
    prototype-light-muted-text: "#626B63"
    prototype-light-primary: "#2E5D50"
    prototype-light-selection: "#E4EBE5"
    prototype-light-container: "#F6F6F0"
    prototype-light-container-low: "#F8F8F2"
    prototype-light-outline: "#A3AFA2"
    prototype-light-divider: "#DEDFD5"
    prototype-light-caution: "#805811"
    prototype-light-caution-container: "#FBF3DF"
    prototype-light-error: "#A93228"
    prototype-light-error-container: "#FFF0EC"
    prototype-dark-surface: "#1C2520"
    prototype-dark-text: "#F2F2E9"
    prototype-dark-muted-text: "#AFB9AE"
    prototype-dark-primary: "#A7C3A2"
    prototype-dark-selection: "#334436"
    prototype-dark-container: "#232E26"
    prototype-dark-container-low: "#202A23"
    prototype-dark-outline: "#6D816E"
    prototype-dark-divider: "#39453B"
    prototype-dark-caution: "#EDC780"
    prototype-dark-caution-container: "#352E20"
    prototype-dark-error: "#FFA69B"
    prototype-dark-error-container: "#392823"
  typography:
    production-intent:
      family: "San Francisco through platform semantic text styles"
      size: "Semantic; 17pt default on iOS and 13pt default on macOS"
      weight: "Regular 400, Medium 500, Semibold 600"
      lineHeight: "Platform semantic"
    prototype-title-compact:
      family: "Framework-resolved default font"
      size: "30sp"
      weight: 500
      lineHeight: "36sp"
    prototype-title-expanded:
      family: "Framework-resolved default font"
      size: "38sp"
      weight: 500
      lineHeight: "44sp"
    prototype-body:
      family: "Platform-resolved sans-serif"
      size: "14sp"
      weight: "Unspecified; resolved from context"
      lineHeight: "23sp"
    prototype-field:
      family: "Platform-resolved sans-serif"
      size: "16sp"
      weight: "Unspecified; resolved from context"
      lineHeight: "25sp"
    prototype-caption:
      family: "Platform-resolved sans-serif"
      size: "12sp"
      weight: "Unspecified; resolved from context"
      lineHeight: "19sp"
  spacing:
    hairline: "1dp"
    tiny: "4dp"
    small: "8dp"
    medium: "12dp"
    large: "16dp"
    section: "24dp"
    spacious: "32dp"
    canvas: "48dp"
  radii:
    extra-small: "6dp"
    small: "8dp"
    medium: "10dp"
    large: "14dp"
    extra-large: "24dp"
  shadows: {}
  breakpoints:
    prototype-content-expanded: "600dp"
---

# Posato Design System

## Design Intent

This is the canonical Posato design reference: the accepted Apple MVP brand
and product principles, followed by the implemented native prototype design as
of **2026-09-06**, documented at the maintainer's request. It describes what
exists, not a proposed redesign. The Gate 3 foundation was accepted on 2026-08-25.

### Scope and reading convention

Unless explicitly labelled **production**, concrete geometry, component states,
screen copy, navigation, and interaction descriptions below refer to the current
[native prototype](prototypes/mvp-interaction-flow/README.md). Its shared
[component library](prototypes/mvp-interaction-flow/compose/README.md) is the
implementation reference. Token values are source-declared, not estimates from
screenshots; keep their Compose `dp`/`sp` units even on Apple hosts.

This update records the prototype's implemented design, including the latest
maintainer-approved navigation and entry refinements. It does not import the
prototype into production, replace platform-owned pickers or blocked screens,
or turn mock permissions, time, iCloud, and enforcement into implemented services.
The [MVP scope](docs/product/mvp-scope.md), accepted production semantics below,
and architecture decisions retain their authority. Final platform assets and
full accessibility conformance remain separate work.

### Positioning

Posato helps self-directed people interrupt automatic use of selected websites
and apps with calm, time-bounded limits across their own Apple devices, without
surveillance or a product account.

The primary audience is an adult who owns a Mac and iPhone, recognizes an
automatic digital habit, and wants deliberate friction under their own control.
Posato is not parental control, employee monitoring, administrator security, or
a productivity-scoring system.

### Brand promise and line

- Brand promise: **a quiet pause between impulse and action.**
- Product line: **Pause. Then choose.**

Posato promises a clear and respectful interruption, not perfect prevention.
It reports time, device scope, synchronization, and failure without overstating
what the application or cloud has accomplished.

### Character and tone

The character is calm, respectful, candid, precise, and composed. It must not
be punitive, paternalistic, moralizing, alarmist, gamified, clinical, or framed
around productivity guilt.

Use short, concrete sentences. State the current condition first, its
consequence second, and an available action third. Never congratulate, shame,
score, or diagnose the person.

### Information hierarchy and density

Every primary product surface answers these questions in order:

1. Is a session active?
2. When does the current state end, or what requires attention?
3. What is paused on this device?
4. Is local work pending, or did the latest local sync attempt complete?
5. What is the single most useful action now?

Use one dominant status, one primary action, and progressively disclosed
details. Do not turn these answers into a dashboard of equal cards. A countdown
is supporting information, not an animated spectacle.

## Foundations

### Color

The visual language is warm paper, dark green ink, moss actions, and quiet mist
selection. Brand references and current application surfaces are distinct:
the prototype uses a brighter paper canvas and a slightly lighter mist than
the original brand board.

| Token | Value | Role |
| --- | --- | --- |
| Brand Ink | `#18231F` | Primary brand text and dark neutral |
| Brand Paper | `#F5F2EA` | Warm light brand surface |
| Brand Moss | `#2E5D50` | Primary brand color and application tint |
| Brand Clay | `#A54B35` | Sparse secondary emphasis, never a generic error color |
| Brand Mist | `#DCE4DF` | Quiet supporting surface or illustration field |

The following are the prototype's explicit semantic overrides in
`PosatoPalette.kt`. Roles not overridden retain framework defaults; the root
screen uses `surface`, not the darker `background` token.

| Semantic role | Light | Dark | Current use |
| --- | --- | --- | --- |
| `surface` | `#FFFEFA` | `#1C2520` | Screen, selected category segment, menu |
| `onSurface`, `onBackground` | `#18231F` | `#F2F2E9` | Main text |
| `onSurfaceVariant` | `#626B63` | `#AFB9AE` | Body copy, captions, inactive destinations |
| `primary`, `secondary`, `surfaceTint` | `#2E5D50` | `#A7C3A2` | Actions, selection text, identity |
| `onPrimary`, `onSecondary`, `onTertiary`, `onError` | `#FFFFFF` | `#18231F` | Text on solid semantic fills |
| `primaryContainer` | `#E4EBE5` | `#334436` | Destination selection, category track, wheel selection |
| `onPrimaryContainer`, `onSecondaryContainer` | `#2E5D50` | `#A7C3A2` | Selected and positive foregrounds |
| `secondaryContainer` | `#EDF2EA` | `#2A3A2E` | Checked rows, positive notices |
| `surfaceContainer`, `surfaceVariant` | `#F6F6F0` | `#232E26` | Sidebar, item-symbol tile, neutral notice |
| `surfaceContainerLow` | `#F8F8F2` | `#202A23` | Resting row-options button |
| `outline` | `#A3AFA2` | `#6D816E` | Field borders |
| `outlineVariant` | `#DEDFD5` | `#39453B` | Dividers, quiet outlines |
| `tertiary`, `onTertiaryContainer` | `#805811` | `#EDC780` | Caution foreground |
| `tertiaryContainer` | `#FBF3DF` | `#352E20` | Caution notice |
| `error`, `onErrorContainer` | `#A93228` | `#FFA69B` | Invalid input, removal, destructive confirmation |
| `errorContainer` | `#FFF0EC` | `#392823` | Critical notice |
| `background` | `#EEEEE8` | `#141B17` | Supporting theme background, not the app canvas |
| `inverseSurface` | `#18231F` | `#F2F2E9` | Inverse theme role |
| `inverseOnSurface` | `#FFFEFA` | `#1C2520` | Text on inverse surfaces |

**More contrast** maps muted text to `onSurface`, quiet outlines to the normal
outline, and the normal outline to `onSurface`. It is an explicit inspection
variant, not proof that the operating system's Increase Contrast is integrated.
**System** appearance follows system light/dark; **Light** and **Dark** override it.

Production retains the semantic platform-color requirement. Original dark brand
references (`#121A17`, `#F7F5EF`, `#76B29E`, `#E49A82`) are provenance, not the
current prototype dark palette. Brand Clay is not the prototype's error color.

### Typography

The prototype declares scalable semantic roles in `PosatoTypography.kt`.
Body roles request `FontFamily.SansSerif`; other roles use the framework-resolved
default font. No custom font is bundled and SF family parity is not established.
Medium and Semibold roles provide hierarchy; body roles do not explicitly
override font weight.

| Role | Size / line height | Weight | Letter spacing |
| --- | --- | --- | --- |
| `displaySmall` | 40 / 46 sp | 500 | -1.8 sp |
| `headlineLarge` | 38 / 44 sp | 500 | -1.5 sp |
| `headlineMedium` | 30 / 36 sp | 500 | -1 sp |
| `titleLarge` | 25 / 32 sp | 600 | -1 sp |
| `titleMedium` | 20 / 27 sp | 500 | Unspecified |
| `titleSmall` | 14 / 21 sp | 500 | Unspecified |
| `bodyLarge` | 16 / 25 sp | Unspecified | Unspecified |
| `bodyMedium` | 14 / 23 sp | Unspecified | Unspecified |
| `bodySmall` | 12 / 19 sp | Unspecified | Unspecified |
| `labelLarge` | 13 / 20 sp | 500 | Unspecified |
| `labelMedium` | 12 / 18 sp | 500 | Unspecified |
| `labelSmall` | 10 / 16 sp | 600 | 1.2 sp |

The main heading is `headlineMedium` in compact content and `headlineLarge`
in expanded content. Eyebrows use moss `labelSmall`, normally uppercase.
Descriptions use muted `bodyMedium`; captions use muted `bodySmall`. The lowercase
wordmark uses `titleLarge`. Fields and category labels use `bodyLarge`; category
labels add Medium weight and counts use muted `labelMedium`. Bottom destination
labels use `labelMedium`; sidebar labels use Medium `bodyMedium`.

`headlineSmall`, used by the management heading and wheel values, is inherited
from Material 3 rather than redefined as a Posato token. Unstyled screen text
likewise follows its ambient text style. Do not treat every framework role as a
custom type decision.

**Larger text** multiplies the current font scale by **1.35**. This inspection
mode and `sp` sizing do not demonstrate full Apple Dynamic Type coverage.
Production still requires platform semantic typography, full text-size support,
and the accepted San Francisco direction. Custom font licensing remains deferred.

### Spacing, shape, borders, and elevation

The prototype now has a concrete rhythm in `PosatoTokens.kt`:

| Token | Value | Typical use |
| --- | --- | --- |
| `Hairline` | 1 dp | Divider and quiet border |
| `Tiny` | 4 dp | Caption gap, segment inset/gap, bottom icon-to-label gap |
| `Small` | 8 dp | Input-to-guidance gap, picker-row gap, bottom-nav outer vertical padding |
| `Medium` | 12 dp | Heading rhythm, row gaps/insets, section spacing |
| `Large` | 16 dp | Notice/panel/end-time padding |
| `Section` | 24 dp | Compact content/header inset, major content gaps |
| `Spacious` | 32 dp | Catalog pattern separation |
| `Canvas` | 48 dp | Expanded content inset |

Radii are **6 / 8 / 10 / 14 / 24 dp**. Badges use 6; fields, item-symbol
tiles, notices, duration presets, and disclosure click surfaces use 8; buttons, selected category
segments, checked rows, and end-time panels use 10; category tracks, destination
items, panels, and menus use 14. The 24 dp extra-large role is declared but is
not a universal screen-card radius. Circular options buttons and rounded-stroke
identity artwork are deliberate exceptions.

Control minimums are **44 dp**, normal icons **18 dp**, large icons **24 dp**,
item-symbol tiles **36 dp**, and options menus at least **200 dp** wide.
These are source-native dimensions, not a claim about every Apple point target.

Use outlines and whitespace rather than elevation to organize the screen.
There is no custom shadow, gradient, blur, or decorative animation token.
Material menus, sheets, dialogs, chips, and interaction indications retain
framework behavior where not overridden; this is not a claim of zero rendered
elevation or zero animation. The catalog-only simulated phone radius is **38 dp**;
it must not wrap the actual iPhone or desktop app.

### Production implementation boundary

TARGETS-001, the first reviewed product screen, establishes the reusable
application foundation: one root `PosatoTheme` backed only by Material 3 and
adapted from the existing platform semantic colors and text styles. Feature
screens consume that theme instead of creating their own theme boundary.

This foundation does not create a generic wrapper for every Material 3
component. Promote a feature component or add spacing, shape, border, or
elevation tokens only after repeated screen evidence establishes a shared
contract.

`SESSION-001` established the first explicit design-system consolidation checkpoint.
Its session setup, review, active, early-end, expiry, and action-required
states must be reviewed alongside the implemented Paused items screen, this
contract, and the disposable interaction prototype. Promote only patterns that
repeat in production screens into `app.posato.core.designsystem`, and adopt exact
production tokens or responsive rules only with reviewed macOS and iOS evidence.
The prototype values documented here are not automatic production adoption.
The checkpoint does not require a comprehensive component
library, copy prototype geometry, or separate design-system implementation
from the `SESSION-001` vertical product slice.

### Iconography and imagery

The prototype draws its own code-native line icons on a **24 × 24** viewport
with **1.6** stroke width and rounded caps/joins. It has pause, list, apps, globe,
cloud, clock, check, device, arrow/chevron, search, close, edit, and removal symbols.
They are not SF Symbols. Icons accompanying text are decorative to accessibility;
icon-only actions carry a specific accessible name.

The implemented study mark is an **open interval**: two softly weighted,
asymmetric vertical forms separated by deliberate negative space, with a subtle
forward shift. Its box is **24 × 32 dp**, with **7 × 25 dp** round-ended strokes,
**2 dp** horizontal insets, and **8°** rotation. It sits **12 dp** from lowercase
`posato`. The larger artwork uses **122 × 144 dp**, **36 × 103 dp** strokes,
**17 dp** insets, and **10°** rotation, with one mist and one moss stroke.

The options ellipsis is three filled dots at viewport x = 4, 12, and 20,
y = 12, radius 1.5, rendered inside a 44 dp target; it is not a compressed
text glyph. Production still calls for SF Symbols for familiar actions and
separately reviewed final app-icon assets.

Avoid shields, padlocks, stop signs, warning tape, flames, dopamine imagery,
achievement rings, and generic productivity illustrations. This study mark is
implemented geometry, not a finalized marketing identity or platform app icon.

## Components

The prototype implements the following component families. Anatomy and
interaction are the design contract for reproducing the study; Compose names
identify the existing reference implementation, not a requirement for every
future renderer. Use slots for icons, labels, supporting text, and actions;
screens own selection, form values, and navigation policy.

### Main destination navigation

**Session** and **Paused items** are permanent destinations after onboarding.
The former uses a pause icon; the latter a list icon. Selected destinations have
a mist fill, moss icon/text, and 14 dp corners. Inactive destinations have a
transparent fill and muted but readable icon/text. Disabled destinations reduce
foreground opacity to 50% and cannot be activated; inactive is not disabled.

`PosatoBottomNavigation` and `PosatoBottomNavigationItem` place icons above
labels on iPhone. `PosatoSidebarNavigationItem` places icons before labels on
Mac. All expose selection semantics and retain text labels. Paused items stays
disabled during an active session; read-only inspection remains available from
Session. The normal management screen has no **Done** action.

### In-screen category switcher

`PosatoTabBar` and `PosatoTab` are used for **Websites [count] / Apps [count]**.
The full-width track has a mist fill, 14 dp corners, 4 dp inset, and 4 dp gap.
Equal-width segments use centered labels with 12 dp padding, a quiet inline
count with an 8 dp gap, and a minimum 44 dp height that grows with content. Selection adds a
paper surface, 1 dp quiet outline, 10 dp corners, and moss label. The unselected
label uses normal text contrast; counts remain muted. Disabled text is 50% opacity.

Align the **outer track**, not the label glyphs, with the heading, field, toolbar,
and row dividers. The whole segment is selectable. There is no underline,
separate count badge, or near-disabled inactive label. This category control
does not replace platform-specific main navigation.

### Actions and choice controls

`PosatoButton` has five variants with 10 dp corners and a 44 dp minimum height:

| Variant | Container / label | Border | Use |
| --- | --- | --- | --- |
| Primary | Moss / `onPrimary` | None | Start, review, save, add, choose apps |
| Secondary | Transparent / main text | Quiet 1 dp | Retry or secondary setup action |
| Quiet | Transparent / moss | None | Cancel, change duration, search, request early end |
| Destructive | Error / `onError` | None | Confirm early termination |
| Compact | Transparent / main text | Quiet 1 dp | Workbench and compact secondary controls |

Normal content padding is 18 dp horizontal and 11 dp vertical; Compact uses
12 / 8 dp. Disabled container/content alpha is multiplied by 0.5. Action groups
wrap with 12 dp horizontal and 8 dp vertical gaps instead of forcing one line.

Checked application rows (`PosatoSelectionRow`) combine a checkbox and label
inside one selectable surface: pale positive fill and moss outline when checked,
paper and quiet outline otherwise, with 12 dp padding and 8 dp between rows.
The checkbox is not a second independent tap target.

The library also has bordered `PosatoChoiceTile`/`PosatoDurationChoice` samples
and FilterChip-based `PosatoToggleButton` inspection controls. The current time
screen uses compact **25 min / 45 min / 60 min** `PosatoNavigationItem` presets,
not the older tall numeric duration tiles shown in historical screenshots.

### Duration wheels

The **Hours** and **Minutes** `PosatoNumberWheel` columns share a row, at most
390 dp wide, with a 24 dp gap. Each has a caption, an increase arrow, three
visible number rows, and a decrease arrow. The selected center row has a mist
background, 10 dp corners, and moss text; neighbouring values are muted.
Values are zero-padded to two digits. Row height follows the wheel text line
height plus 12 dp, never less than 44 dp; arrows have 44 × 44 dp minimum targets.

Scroll with snapping, tap a neighbouring value, or use arrows for **one-unit**
changes. Keyboard Up/Down increments/decrements; Home/End selects the current
column's first/last value. Values stop at bounds instead of wrapping. The
prototype supports whole-minute totals from **5 minutes to 24 hours**: at zero
hours minutes range from 5 to 59, at 24 hours only zero minutes is valid, and
otherwise minutes range from 0 to 59. Changing hours clamps the total if needed.
Presets and wheels stay coordinated; **Ends at [time]** updates immediately.

### Fields, repeated entry, and search

`PosatoTextField` is an outlined, 8 dp-corner field with a floating label,
optional placeholder/trailing action, and an external message 8 dp below it.
Errors replace supporting guidance and expose error semantics. Field focus is
separate from the surrounding field/message container and trailing button.

Website management starts with **Add websites**, not search. Its trailing **Add**
button is primary and disabled only for blank input. The field grows to two
visible lines; additional text scrolls within it. Guidance says **Add one, or
paste several separated by commas or new lines.** Keep that guidance separate
from the toolbar count below it.

Return/Go or Add submits without leaving the field. On Mac, Shift–Return
inserts a line break. Domains and website URLs can be pasted together; valid
exact hosts append in input order, duplicates are skipped, and invalid entries
remain for correction. Feedback summarizes added/already-listed/to-fix counts.
Do not reopen an add dialog for each site or erase a newer draft with old feedback.

**Search** is a quiet, icon-labelled toolbar action. It reveals
`PosatoSearchField`: one line, leading search icon, optional 44 dp **Clear search**
button, and search keyboard submission that dismisses the keyboard. **Back to
adding** clears the filter and restores the entry draft. Filtering ignores case
and surrounding whitespace; clearing/changing the query returns the website
list to its beginning. Read-only selection details expose search directly.

### Lists, summaries, and row menus

Use flat rows and quiet 1 dp separators, not a card per item. `PosatoItemRow`
and `PosatoDisclosureRow` have 12 dp vertical padding and 12 dp between the
leading symbol, flexible text region, and trailing content. Headline/supporting
text has a 4 dp gap. Long labels may wrap; rows grow rather than hide full domains.
The symbol is a moss 18 dp icon in a 36 dp neutral tile with 8 dp corners.

Session and review show exactly **two summary rows**: website count and local
application count. With one item the subtitle names it; otherwise it explains
shared-domain or local-device scope and says **view all**. A chevron signals
read-only disclosure. Opening a summary does not navigate to editing or change
the current session. **Close list** returns from the sheet/dialog.

The management toolbar vertically centers the count/scope text and its action.
It has 12 dp top and 8 dp bottom spacing inside the browser. **Choose apps** is
the primary Apps action next to **On this [device] only**. The category switcher,
entry/search, and toolbar stay above independently scrolling lazy results.

Website rows open the editor on row click. Their circular 44 dp options button
uses a 24 dp ellipsis, a quiet resting fill, and mist while its menu is open.
`PosatoItemMenu` has a paper surface, 14 dp corners, 1 dp quiet border, and a
200 dp minimum width. Icon-led **Edit** uses normal text; **Remove** uses error
text/icon. Menu actions are at least 44 dp high with 16 dp horizontal and 4 dp
vertical padding. Choosing one dismisses the menu. App rows offer removal of
the local choice, not a website editor. Removal is immediate in this prototype;
there is no invented confirmation or undo step.

### Supporting presentation patterns

| Family | Anatomy and rule | Reference components |
| --- | --- | --- |
| Heading | Eyebrow, prominent heading, muted explanation with 12 dp gaps | `PosatoHeading`, `PosatoEyebrow`, `PosatoTitle`, `PosatoBody`, `PosatoCaption` |
| Section header | Flexible title/supporting text plus vertically centered action; wraps when constrained | `PosatoSectionHeader`, `PosatoSection` |
| End time | Clock icon, prominent **Until [time]**, supporting duration; 16 dp padding, quiet outline | `PosatoEndTime` |
| Sync footer | Cloud icon, truthful device-local message, optional action below; no additional separator | `PosatoSyncFooter` |
| Notice | Neutral, positive, caution, or critical fill/foreground; optional icon and action; 16 dp padding | `PosatoNotice` |
| Status label | 6 dp semantic dot plus explicit text, 8 dp gap | `PosatoStatusLabel` |
| Hero | Flexible heading and optional right-side artwork on expanded content only | `PosatoHero` |
| Empty state | Title, explanation, optional relevant action; 12 dp gaps | `PosatoEmptyState` |
| Panel | Paper, quiet outline, 14 dp corners, 16 dp padding; optional separated header | `PosatoPanel` |
| Privacy and scope | Plain icon-led rows and device labels, not badges implying enforcement | `PosatoPrivacyPoint`, `PosatoDeviceLabel` |
| Badge | Quiet outline, muted caption, 6 dp corners, 8 / 4 dp padding; not used for category counts | `PosatoBadge` |

`PosatoItemList`, `PosatoDivider`, `PosatoActionRow`, and `PosatoChoiceGroup`
provide layout/grouping primitives. `PosatoAppScaffold` and `PosatoSidebar`
remain the catalog's older width-adaptive navigation; the native application
uses `PosatoNavigationScaffold`. Setup steps and `workbench/` inspection frames,
headers, walkthrough steps, facts, and footers are catalog/inspection tools,
not extra controls or device frames around the native app. The
[complete component map](prototypes/mvp-interaction-flow/compose/README.md#complete-prototype-component-map)
distinguishes these families and their APIs.

### Retained production semantics

The current synthetic picker is not a change to the accepted product policy.
In production, the visible name for the singleton semantic application policy is
**Application group**. Its name may synchronize, but application choices remain
local to each device. On macOS, a present application group exposes **Choose
applications**, followed by a plain device-local list with individual Remove
actions. On iOS, the shared screen shows only the selected application count
because opaque Screen Time tokens do not expose useful product-owned names; the
system picker remains responsible for recognizable native labels and detailed
review. The iOS action changes to **Review applications** after the first
selection, and permission-required, denied, restricted, and unavailable states
remain visibly distinct. Cancellation keeps the previous list. Removing the semantic group
retains the local list and says so explicitly; recreating the group makes the
retained choices effective again. Where a native selection producer exists but
nothing is chosen yet, the shared UI states **Apps still need to be chosen on
this device.** Where the running build has no such producer, it states
**Choosing apps is not available in this version of Posato.** In both cases it presents
no fake or speculative selection action, and it keeps any choices already made
on the device.

#### Production session setup and review

Session setup collects duration or end time. Review shows the resolved end time,
effective local items, and any missing permission or mapping before the start
action. It does not use urgency, scoring, or commitment theater.

#### Production active-session surface

The active surface leads with **Session active until [time]**, shows effective
local items and truthful local sync state, and offers a clearly labeled route
to **End session early**. Early termination uses a clear confirmation, not a
hidden, tiny, timed, or gesture-only control.

#### Production synchronization status and action

Synchronization distinguishes local-only, pending local work, syncing, last
completed local attempt, retryable failure, waiting for the iCloud Keychain
workspace key, and action-required failure. **Sync now** is a manual action.
Completion never claims that every other device received the change.

#### Production action-required notice

The notice names the bounded problem, preserves the last valid state, and gives
one exact repair or retry action. Permission loss, invalid local selection,
helper or proxy failure, network or quota failure, and delayed key delivery are
different states rather than one generic error.

#### Production blocked presentation

The platform-owned blocked presentation says that the selected site or app is
paused, gives the session end time when available, and provides a route to
Posato. It does not display or retain browsing history, full attempted URLs, or
application-usage timelines.

## Layout and Responsive Behavior

### Native application shell

The hosts render the application directly, without a simulated phone frame,
HTML viewport, inspector column, or platform toggle in the product surface.
Navigation placement follows the host, not the content breakpoint:

| Rule | iPhone | macOS desktop |
| --- | --- | --- |
| Main navigation | Bottom bar beneath the body | Persistent 224 dp sidebar beside the body |
| Identity | Wordmark header above the body | Wordmark at the top of the sidebar |
| Navigation layout | Two equal-width icon-over-label destinations | Two stacked icon-and-label destinations |
| Device scope | Expressed in the relevant content | Additional quiet device label below sidebar navigation |
| Window | Fits the native screen and safe area | Standard decorated, resizable system window; initially 1060 × 780 dp |
| Minimum width | Supplied by the host | 614 logical window pixels; 224 sidebar + 390 body |
| During text entry | Header and bottom navigation hide while the keyboard is visible | Sidebar remains present |

The bottom bar has a full-width 1 dp top divider, 24 dp horizontal and 8 dp
vertical outer padding, and an 8 dp gap between destinations. Each destination
has 8 dp padding, a 24 dp icon, a 4 dp icon/label gap, and a 12/18 sp medium
label. Its height follows content and the minimum target; it is not a fixed
height that clips larger labels.

The sidebar uses `surfaceContainer`, with 24 dp header padding. Its navigation
region uses 12 dp horizontal and 8 dp vertical padding, 8 dp between items, and
12 dp item padding. An 18 dp icon and medium-weight body label sit 12 dp apart.
The device caption is inset to align with the wordmark and item contents.
Onboarding has no main destination controls; it does not offer a shortcut
around setup. The Mac retains the identity and device scope in its sidebar.

The SwiftUI root lets Compose own insets. Compose applies safe-drawing and IME
padding once at the application root. The iPhone bottom bar is outside the
scrolling body; it does not cover the last row. While typing in compact item
management, the repeated page heading also disappears to preserve room for
entry and list results. Native window chrome is not part of the Posato palette.

### Content canvas and reflow

- The body, after subtracting any sidebar, selects Compact below **600 dp**
  and Expanded at **600 dp or above**. Narrowing the Mac changes the body
  layout, not the navigation placement. The corresponding total Mac width is
  approximately 824 logical pixels, not 600.
- Content is top-centered and capped at **820 dp including padding**.
  Horizontal padding is 24 dp in Compact and 48 dp in Expanded. At the Mac
  minimum, the 390 dp body leaves 342 dp inside compact horizontal padding.
- Session, onboarding, editor, and interruption surfaces use a vertically
  scrolling outer column with 24 dp slot spacing and vertical padding equal
  to their horizontal padding. Screen-specific groups retain their own rhythm;
  the duration form, for example, uses 16 dp between groups. Sparse content
  stays grouped with its action, without a flexible spacer pushing it away.
- Paused items and setup targets use 12 dp vertical padding. Their heading,
  category switcher, entry field, and toolbar sit above a weighted lazy list.
  The rows scroll independently; adding and filtering remain accessible.
- Section headers align metadata and actions vertically when they fit in a
  row, then reflow at constrained width. Action groups wrap instead of
  shrinking labels or touch targets.
- The large decorative interval artwork accompanies the expanded session hero
  but is omitted from the compact session overview. The text, status, and
  action retain priority over decoration.

There is no bespoke minimum desktop height. Very short windows, landscape
phones, localization expansion, and the full accessibility-size range still
need separate layout validation; the observed minimum-width behavior is not a
claim that every possible window size is supported.

### Read-only details and overlays

Session summaries open **Selected items** at the chosen website or app
category. On iPhone this is a fully expanded modal bottom sheet; on desktop it
is a dialog using a rounded surface, 24 dp outer inset, and an 820 dp maximum
content width. The content has 24 dp padding, a title and **Close list** action,
the same category component, direct website search, and a lazy list. It has no
add, edit, remove, or app-selection controls. Initial focus is requested on the
close action. This review surface does not leave the current session step.

The separate prototype-control overlay uses a fully expanded iPhone bottom
sheet or a right-aligned desktop dialog panel capped at 440 dp. It is not a
third main destination. Appearance settings, including larger text, also apply
inside dialogs and sheets.

## Interaction and Motion

### Screen inventory and hierarchy

The current mock application has sixteen named surfaces. These names describe
prototype state, not a required production navigation architecture.

| Surface | Visible hierarchy and principal action |
| --- | --- |
| Welcome | Interval artwork; “A little space. For what matters.”; purpose; **Make some space** |
| Privacy | “Your choices stay yours.”; three privacy/scope rows; **Continue with iCloud**; explicit mock-service caption |
| Workspace check | “Finding your space.”; checking explanation and result panel; the workbench supplies the result |
| Key waiting | “One more moment.”; preserve the existing workspace; waiting notice; **Check again**, with simulated key-arrival explanation |
| Permission | “A small permission. A useful pause.”; platform-specific rationale; **Allow on this device**; mock-permission caption |
| Setup targets | The item browser; **Finish setup**; explanation when the walkthrough selection is incomplete |
| Website editor | Add/edit heading; focused domain field; local validation; **Save website** and **Cancel** |
| Application picker | Device-local scope explanation; four synthetic checkbox choices; **Save selection** and **Cancel** |
| Paused items | Page title; Websites/Apps switcher; entry or picker action; metadata/search toolbar; lazy rows; no redundant **Done** action |
| Home | “NO SESSION ACTIVE”; “Room for what matters.”; supporting copy; **Start a session**; “What will be paused” summaries; local sync footer |
| Session setup | “YOUR NEXT PAUSE”; “How much space do you need?”; presets; hour/minute wheels; resolved end time; **Review session** and **Cancel** |
| Session review | “ONE LAST LOOK”; “Your pause, your choice.” or “A small step first.”; end-time panel; any required repair; **Start this pause**, **Change duration**; scope summaries |
| Active session | “SESSION ACTIVE”; “A little room. Just for you.” or repair-led heading; end time; any required repair; **End session early**; scope summaries; local sync footer |
| Early end | “YOU’RE IN CONTROL”; “Ready to end this pause?”; scope and sync explanation; end time; **Keep session** and destructive **End session early** |
| Blocked preview | “A moment to choose.”; interval artwork; pause/end-time explanation; **Open Posato**; simulated-surface caption |
| Recovery | “NEEDS YOUR ATTENTION”; “Let’s get your pause ready.”; reassurance about saved choices; actionable repair notice; **Back to session** |

The normal entry moment is ready/Home with one website and one local app. The
**Long list** inspection moment supplies 50 synthetic websites and four local
apps to examine list density, navigation, and repeated entry. A main Session
screen never expands into all fifty rows.

### Session and recovery behavior

The ordinary path is Home → duration → review → active. Choosing a 25-, 45-,
or 60-minute preset updates the same duration used by the hour/minute wheels.
The end-time preview changes with that value and is preserved through review,
active state, and early-end cancellation. Next-day end times are identified.
The 5-minute to 24-hour bounds and fixed 17:45 demo clock are mock parameters,
not newly accepted MVP policy.

Home offers **Choose paused items** instead of **Start a session** when no
effective local items exist. Review disables starting while a required repair
remains. A caution notice explains the issue and provides **Restore
permission**, **Choose apps**, or **Choose paused items** as applicable. The
app-mapping repair remains reachable from Session during an active pause even
though ordinary Paused items navigation is disabled.

Early end is a dedicated confirmation surface, not an immediate stop, hidden
gesture, or generic alert. **Keep session** is primary; termination is the
explicit destructive action. Returning from confirmation preserves the active
session and its end time. Normal expiry and the blocked presentation are
workbench-driven simulations; the prototype does not restrict another app or
observe a browsing attempt.

The onboarding walkthrough currently requires both a website and a local app
before **Finish setup** is enabled. That is a walkthrough fixture, not an
accepted requirement that every real user configure both item types.

### Management, entry, and retained state

Adding websites is the default website mode. Paste one domain or several
comma/newline-separated entries, then choose **Add** or submit from the
keyboard. Valid new domains append in input order; existing and within-batch
duplicates are counted without adding another row. Invalid entries remain in
the draft for correction, accompanied by a result summary. Successful repeated
entry retains focus; on desktop, Shift+Return inserts a line break.

**Search** temporarily replaces entry with a case-insensitive website filter;
**Back to adding** clears the query and restores the entry mode. Clearing or
changing the query returns the list to the top. Empty collections and no-match
results are distinct states. App selection is the prominent action on the
Apps section, not hidden behind a row menu or displaced by website search.

The website draft, category, search mode/query, and separate website/app list
positions survive main-destination changes, an editor round trip, and desktop
resize within the same mock moment. Opening and simply dismissing prototype
controls also preserves them. Choosing a new moment or relaunching resets the
in-memory study; there is no persistence promise.

Website editing validates before replacing the last valid entry. Pasted URLs
are reduced to their domain for this mock; paths and queries are not displayed
as policy. Cancel leaves the prior entry unchanged. The synthetic app picker
saves or cancels a set of device-local choices. Row-menu **Remove** currently
removes an item immediately without a confirmation or undo. These behaviors
describe the prototype, not final production normalization, deletion, or
Apple-picker contracts.

### Local sync language and prototype controls

The footer distinguishes local-only, pending, syncing, completed, retryable
failure, waiting for a key, and action-required states. Completion says **Last
sync completed on this device at [time]**. A pending local change can offer
**Sync now**; retryable failure offers **Retry sync**. Neither proves another
device received or applied the change. There is no real CloudKit, Keychain,
permission prompt, database, application picker, or enforcement in this app.

Open the hidden workbench by long-pressing the **posato** wordmark, using its
**Open prototype controls** accessibility action, or pressing **⌘⇧P** on Mac.
It provides Moments, Walkthrough, Free play, and State, plus System/Light/Dark,
More contrast, and Larger text appearance options. Appearance changes stay in
the overlay; applying a moment or action returns to the application. Close it
with its close control, desktop Escape/⌘⇧P, or the platform sheet/dialog
dismissal interaction. Desktop dismissal requests focus back on the wordmark.

Strict application actions and guided scenarios retain their state guards.
Free play may prepare prerequisites to make an isolated inspection action
possible. It is not a product shortcut around permission or session policy.

### Feedback and language

Prefer **No session active**, **Session active until [time]**, **This site is
paused until [time]**, **End session early**, **Last sync completed on this
device at [time]**, **waiting**, and **needs your attention**.

Avoid **locked in**, **access denied** in ordinary product copy, **give up**,
**break focus**, **everything is synced**, **detox**, **addiction**,
**discipline**, **streak**, and language that describes a person as a failure.

### Motion

There is no custom motion token set, route transition choreography, animated
countdown, or ambient decoration. Number wheels use scroll-and-snap behavior;
framework controls, menus, and sheets retain their default interaction and
transition behavior. Do not derive a duration or easing constant from a still
screenshot. Framework animation is not proof of Reduce Motion support.

The production intent remains restrained, functional motion that honors Reduce
Motion. Do not animate every countdown tick, create urgency, auto-dismiss
decision-critical information, or announce every background retry.

## Accessibility

### Implemented aids in the prototype

- Shared interactive controls use a minimum 44 dp target on both hosts; icon
  drawing size is separate from hit-area size.
- Destination and category controls expose selection semantics. Number wheels
  expose their unit/value and adjustable actions, alongside visible step
  arrows and keyboard operation. Icon-only row menus have item-specific labels.
- Headings, invalid fields, selected choices, and meaningful notice/result text
  have explicit semantics where supplied by the components. Repair notices
  use a polite live region; color is accompanied by explanatory text.
- Website submission, search clearing, menu actions, overlay dismissal, and
  duration adjustment have keyboard paths. Opening editors requests field
  focus; desktop prototype-control dismissal requests wordmark focus.
- More contrast strengthens muted text and outlines; Larger text multiplies
  the current Compose font scale by 1.35. Wrapping actions, adaptive section
  headers, scrollable surfaces, and flexible navigation height support this
  inspection mode.

These are source-observed aids, not full accessibility certification. Native
inspection has exercised representative keyboard, dark/large-text, and
keyboard-visible layouts; it has not established exhaustive assistive-
technology coverage or production Dynamic Type behavior.

### Retained production requirements and open checks

Use the accepted Apple accessibility guidance and WCAG 2.1 AA contrast reference
for real states in light, dark, and increased contrast. The accepted target
intent is 44 × 44 points on iOS and 28 × 28 points on macOS; the prototype's
shared 44 dp minimum is its implementation choice, not a new platform rule.

Verify VoiceOver, Voice Control, Switch Control, Full Keyboard Access, focus
order/restoration, contrast, Reduce Motion, and content reflow across supported
native text sizes. Essential end times, reasons, and actions must remain
available. Present a truthful permission rationale before the real system
prompt; a mock allowance is not permission evidence. Keep early termination
deliberate through understandable copy and confirmation, never inaccessible
targets, gestures, hidden focus, or time pressure.

## Evidence, Inferences, and Unknowns

### Observed

- `Observed`: the maintainer accepted the complete Gate 3 direction on
  2026-08-25 after viewing the rendered brand board and requested this
  `DESIGN.md` representation.
- `Observed`: `docs/wiki/topics/brand-and-design-baseline.md` explicitly
  declares positioning, audience, tone, palette, typography, icon direction,
  hierarchy, accessibility constraints, flows, and PR #1 copy.
- `Observed`: `docs/product/mvp-scope.md` fixes the Apple-first outcome,
  intentional early termination, truthful synchronization boundary, and
  privacy non-goals that this design preserves.
- `Observed`: the maintained Apple-guidance source records the platform
  authorities used for the accepted baseline; this extraction does not claim
  to refresh or revalidate that external guidance.
- `Observed` / `user-confirmed` (2026-09-06): the maintainer requested an
  accurate description of the current prototype in this document after
  refining duration entry, batch website entry, list/menu hierarchy, category
  controls, and native main navigation.
- `Observed`: the [Compose design-system sources](prototypes/mvp-interaction-flow/compose/src/commonMain/kotlin/app/posato/prototype/designsystem/)
  declare the palettes, type roles, spacing, shapes, semantics, and components
  described here. The [screen sources](prototypes/mvp-interaction-flow/app/src/commonMain/kotlin/app/posato/prototype/ui/)
  determine which components the current application actually uses.
- `Observed`: the [desktop host](prototypes/mvp-interaction-flow/app/src/jvmMain/kotlin/app/posato/prototype/Main.kt)
  supplies the native window and minimum width; the [SwiftUI host](prototypes/mvp-interaction-flow/iosApp/PosatoPrototype/PosatoPrototypeApp.swift)
  embeds the Compose controller with Compose-owned insets.
- `Observed`: original-resolution native captures show the warm light iPhone
  surface with bottom navigation and the compact, larger-text desktop body
  with a persistent sidebar at minimum width. Prior native interaction
  evidence and its limits are recorded in the
  [prototype source page](docs/wiki/sources/mvp-interaction-prototype.md).
  This documentation extraction did not rerun the app or perform new flow QA.

### Derived

- `Derived`: the retained brand-reference contrast calculations are 14.45:1
  for Ink on Paper, 6.71:1 for Moss on Paper, 7.51:1 for white on Moss,
  5.13:1 for Clay on Paper, and 5.74:1 for white on Clay. They are not a
  contrast audit of the different prototype palette, alpha states, or controls.
- `Derived`: 224 dp sidebar plus the 390 dp compact body explains the desktop
  host's 614 logical-pixel minimum. Adding the sidebar to the 600 dp body
  breakpoint explains the approximately 824-wide expanded-layout threshold.
- `Derived`: raster analysis of the inspected iPhone capture found `#FFFEFA`
  as the dominant surface and `#E4EBE5` as the selected-navigation fill,
  consistent with source. Desktop window shadows, native traffic-light
  controls, and anti-aliased near-colors are not additional design tokens.
- `Derived`: the accepted information questions reduce to one status-led
  hierarchy rather than an equal-weight dashboard because state, end or repair
  condition, local scope, sync state, and action have an explicit priority.

### Inferred

- `Inferred`: the open-interval mark best expresses the accepted promise when
  it reads as space deliberately opened rather than a barrier imposed.
- `Inferred`: bounded summaries, add-first management, and a separate search
  mode reduce the visual and interaction cost of large lists. Native probes
  establish behavior, not broad user preference or measured usability gains.
- `Inferred`: the quiet shared selection treatment connects the main
  navigation and category controls without requiring identical placement.

### Unknown

- `Unknown`: final production logo/app-icon assets, licensed custom typeface,
  illustration system, and marketing design. The prototype mark geometry and
  wordmark styling are known; they are not final asset approval.
- `Unknown`: which prototype tokens and layouts will be adopted or adapted by
  production under the accepted consolidation boundary.
- `Unknown`: complete native assistive-technology output, localization and
  extreme-size reflow, measured contrast across every state, and OS reduced-
  motion behavior. Known source semantics do not close these checks.
- `Unknown`: final platform-owned blocked/shield presentation and picker
  rendering within each enforcement mechanism's actual constraints.

### Differences that must remain explicit

The accepted production direction calls for platform semantic surfaces, system
type behavior, and SF Symbols where suitable. The mock deliberately implements
a bespoke Compose palette, default Compose sans-serif roles, custom vectors,
and a manual larger-text multiplier. These are concrete prototype choices, not
proof of native color, font, icon, or accessibility parity. The older dark
brand references also differ from the current prototype's dark palette.

Catalog-only samples include alternative frame/scaffold and choice patterns;
their presence does not make them current app navigation or form geometry.
Historical HTML/browser observations remain historical: CSS viewport sizes and
axe results cannot establish native dp layout or VoiceOver behavior. The HTML
target is no longer maintained. High extraction confidence means the source
description is well supported, not that the prototype is production-ready.

## Reproduction Guidelines

1. When reproducing this prototype, start from its semantic palette, type
   roles, and reusable Compose components. Preserve native units; do not sample
   screen pixels into invented dp/sp tokens or substitute the older brand
   palette for the current app palette.
2. Keep the platform shell: iPhone bottom navigation, Mac sidebar and system
   window, constrained body, safe-area/keyboard handling, and independent list
   scrolling. Do not ship a catalog device frame or workbench as app chrome.
3. Use the quiet filled selection language consistently. Category switchers
   retain a shared track around inactive segments, not raw underlined Material
   tabs. Main navigation retains its transparent inactive state. Inset text
   naturally inside each control.
4. Preserve the status-led Session hierarchy and bounded two-category summary.
   Review long lists in read-only details; manage them in Paused items.
5. Keep frequent work direct: inline batch website entry, visible **Choose
   apps**, optional search, and duration wheels with step arrows and presets.
   Do not restore a bare custom-minute input or a dialog for every new website.
6. Keep one clear primary action per task context, generous usable targets,
   wrapping action groups, restrained dividers, and icon/label row menus. Avoid
   a second adjacent divider before the sync footer or metadata that reads as
   part of the field guidance.
7. Preserve truthful local scope, repair, and sync language. Do not introduce
   scoring, shame, surveillance, automatic-sync promises, or remote-delivery
   claims. Keep synthetic pickers, the manual clock, and control-overlay
   behavior visibly within the prototype boundary.
8. Treat production adoption as an explicit consolidation decision, not a
   wholesale prototype import. Retain platform service, privacy, accessibility,
   and semantic theme contracts; verify the real app before claiming parity.
9. Reconcile tokens, call sites, and rendered evidence after a meaningful
   design change. Update the relevant values, behavior, evidence limits, and
   unknowns here; retain supporting experiment history in the wiki.
