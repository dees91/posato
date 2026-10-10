---
schema: design-md/v1
name: "Posato"
sources:
  - type: source-code
    path: "shared/src/commonMain/kotlin/app/posato/core/designsystem"
    platform: cross-platform
  - type: source-code
    path: "prototypes/mvp-interaction-flow/DESIGN.md"
    platform: cross-platform
  - type: source-code
    path: "docs/product/mvp-scope.md"
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
  responsive: medium
  interaction: high
  accessibility: medium
tokens:
  colors:
    light-surface: "#FFFEFA"
    light-ink: "#18231F"
    light-primary: "#2E5D50"
    light-container: "#E4EBE5"
    dark-surface: "#1C2520"
    dark-ink: "#F2F2E9"
    dark-primary: "#A7C3A2"
    dark-container: "#334436"
  typography:
    application:
      family: "Platform-resolved system sans-serif"
      size: "Compose sp tokens; system font scale is preserved"
      weight: "Regular 400, Medium 500, Semibold 600"
      lineHeight: "Explicit token values where declared"
  spacing:
    hairline: 1
    tiny: 4
    small: 8
    medium: 12
    large: 16
    section: 24
    spacious: 32
    canvas: 48
  radii:
    extra-small: 6
    small: 8
    medium: 10
    large: 14
    extra-large: 24
    macos-native-window: 20
  shadows: {}
---

# Posato Design System

## Design Intent

This is the accepted design authority for the Apple MVP in progress.
The maintainer accepted the native prototype and its design-system adoption on
2026-09-07. The current contract supersedes the former minimal shell-only
geometry, platform-color-only theme, and top segmented navigation. The original
2026-08-25 brand promise, privacy boundary, and respectful tone remain.

The reference is the native prototype at `27da7bd`. Product components are
adapted into `shared/core/designsystem`; the real app does not depend on a
prototype module. Preserve visual hierarchy, palette, typography, geometry,
and interaction patterns practically one-to-one while adapting code to the
existing feature ViewModels and real service outcomes.

`user-confirmed` (2026-10-05, `DESIGN-004`): the interface stays one shared
Compose Multiplatform layer that adapts to each platform, rather than a layer
per platform, because Android and Linux applications are planned. iOS takes the
system's navigation, controls, and gestures; the Mac keeps its drawn controls;
Material is the Android flavour. [Platform adaptation](#platform-adaptation)
holds the rules; where an older passage below differs, that section wins.

### Positioning

Posato helps self-directed adults interrupt automatic use of selected websites
and apps across their own Apple devices, without surveillance or a product
account. It is not parental control, employee monitoring, administrator security,
or a productivity-scoring system.

- Brand promise: **a quiet pause between impulse and action.**
- Product line: **A little space. For what matters.**
- Character: calm, respectful, candid, precise, composed.
- Avoid shame, urgency, streaks, scores, gamification, and claims of perfect prevention.

### Current implementation boundary

The actual app contains Session, Paused items and the inactive Schedules UI
shell, real local persistence,
native application-selection boundaries, the local session timer, one
explicit **Sync with iCloud** control on the Session screen, and, on macOS
only, a **This Mac** helper-setup section below it. Session-driven
enforcement is not connected to these screens. Never show the prototype's
demo clock, invented synchronization time, mock application names, onboarding
success, simulated permission outcome, or inspection overlay in the real app.

On a first install the app opens a six-step first-run flow before the primary
destinations: purpose, privacy, Sync with iCloud, this-device permission,
first website, and a summary read back from the services. Purpose and privacy
cannot be skipped; every service step offers a defer action that leaves the
device local-only or unpermitted. Completion is one local row that an upgrade
migration seeds for databases that already hold product state. Nothing
reaches iCloud, the synchronizable Keychain, a system prompt, or the helper
before the person's explicit step action. A consented fresh join may then
continue through bounded foreground checks until adoption or a definitive
failure; no waiting record survives relaunch. No sentence promises when a
session or a website change arrives on another device; what linking combines
is stated next to the link action.

The UI says that data is saved on this device. A running timer is not evidence
that a restriction is active. Future flows below are requirements, not current
controls.

The **Sync with iCloud** control requests consent to link this device to the
private iCloud workspace. Next to it, one sentence states the consequence:
"Linking combines the websites saved on your devices and shares an active
session. App choices stay on each device." Linking adds this device's
websites to the workspace and removes nothing local; a website a peer
removed before this device linked comes back for both. Linking publishes a
still-active local session once, with its original identifier and end, and
never backfills ended sessions. Once linked, **Sync now** requests an
exchange; launch, foreground, and exact-domain commits also offer an
exchange. Status reports local-only, pending, syncing, completed local
attempt, retryable, waiting for the key, or action required. A completed
attempt makes no claim about receipt on another device, and no sentence
promises delivery timing, latency, or waking the other device. There is no
synchronization time or device list. A completed exchange converges exact
domains, the application group name, and bounded sessions into the visible
state; application selections stay on the device that made them. A received
session is adopted by identity with the receiving device's own frozen
summary, never carries app selections, and never grants permission. When the
merged
websites would exceed the 1,024-device limit, or the shared set reaches its
2,048 capacity, the status reports action required with a reason naming the
limit; recovery is removing websites on any device and choosing Sync now.

A linked device also offers **Remove workspace** with destructive confirmation:
it deletes the iCloud workspace and undelivered device changes, preserves local
websites, and requires other devices to remove their old workspace and link
again. The removing device can then use **Sync with iCloud** to start again.
On the Mac, while a removal runs the row reads "Removing workspace…" with an
activity indicator and the note "An older workspace can take a few minutes.",
both actions stay disabled, and the state survives closing and reopening the
window. The iPhone shows its usual running state.

## Platform Adaptation

Two platform facts in `PlatformTheme` decide the flavour: whether the host uses
the iOS chrome (iOS and iPadOS) and whether it uses Material's ripple (Android).
Shared components branch on them; feature screens branch only where a whole
interaction differs. The palette, typography roles, spacing, copy, and brand
stay common.

### iOS and iPadOS

- **Stack.** A push slides the whole screen, bar included, over the one below,
  which drifts a third of the width and dims. The edge swipe follows the finger
  and its release velocity carries into a critically damped spring; a push, pop
  and swipe interrupt one another from where the screen is. Under Reduce Motion
  screens change without a slide. The screen below stays composed but unplaced,
  so a swipe starts at once and the screen keeps its state; it is not drawn,
  touched, or exposed to accessibility while parked.
- **Bars.** Every screen has a 44 pt navigation bar. Back is the mirrored
  interval mark (`PosatoBackMark`, the brand's back sign by maintainer choice)
  with the previous screen's title, read as "Back to <title>". The mark and the
  trailing actions line up with the content edge; the title is centred. Room
  goes to the actions first, then the title, and the back label shortens last.
  Bar and tab text follow the reading size up to 1.3 times. A hairline appears
  once content scrolls under the bar.
- **Titles.** Each destination's root has a large title (34 / 41, bold) that
  scrolls into the bar. Session's large title is the wordmark. A quiet lead line
  under the large title replaces the eyebrow and heading pair; eyebrows do not
  appear on iOS. **About Posato** is the info symbol in Session's bar.
- **Insets.** Screens span their pane; content sits in a column capped at 820
  and centred, 24 from its edges (`PosatoBarInset`), with the first content 16
  below the bar. A push therefore moves the whole pane.
- **Tab bar.** The destinations' tab bar belongs to them: a pushed shell screen
  such as About covers it, and the keyboard rises over it rather than removing
  it. A bar **Done** appears while the keyboard is up and clears focus. The
  selected tab is primary with a semibold label and no pill; the others use the
  muted text color, 70 % of it in dark. On a wide iPad in portrait the tabs
  gather within 560 in the middle.
- **iPad in landscape.** A 224 sidebar on the grouped surface with a hairline
  toward the screens: the wordmark where a large title stands, the destinations
  as rows in the text color (the chosen one on a quiet fill in the accent), then
  **On this iPad** and **About Posato**. About chosen there is a root screen with
  a large title. One view tree serves both orientations, so turning the iPad
  keeps every screen's state.
- **System controls.** Row and screen menus are the system menu behind the
  `ellipsis.circle` symbol, with SF Symbols; choices such as **Pause set** are
  the system pop-up button; questions are the system alert, with the
  destructive answer in red and the safe one bold; schedule times are compact
  system time pickers that follow the device's 12- or 24-hour clock; a pause's
  length is the system countdown wheel (5 minutes to 23 hours 59 minutes, its
  limit); duration presets are a segmented control. When UIKit cannot present a
  system alert, the same question is drawn in the app after a second, so no
  screen waits for an answer that cannot come. Native views fill their
  interop hole with the surface color and cap their text at the third
  accessibility size where they cannot grow.
- **Rows.** Lists are flat rows with dividers, never cards. A tap opens or edits
  the row. Secondary actions slide out under a left swipe, the destructive one in
  the error color, and are also reachable as accessibility custom actions; a row
  carries no button of its own besides a switch. A schedule row keeps its switch
  at the trailing edge, and the schedule's editor offers **Skip next** and a
  red **Delete schedule** at its foot, so no action lives only under a swipe.
- **Drawn controls.** The switch is iOS-sized (51 × 31) with a white thumb in
  light; weekday choices are 44 round toggles with the day's initial, read by
  full name; progress without an end is a spoked indicator.

### Mac

The Mac keeps the drawn controls: its menus behind a drawn ellipsis, drawn time
wheels with Increase and Decrease buttons, dialogs drawn in the palette, in-page
links such as **Back to schedules**, the sidebar, and eyebrows over headings.

### Both Apple hosts

No ripple. Pressed content dims to half (`PosatoPressDim`), and a 2 dp ring in
the accent marks keyboard focus only when focus came from the keyboard. Text
actions have no container and no side padding: the label sits on the content
edge, keeps a 44 target, dims while pressed, and underlines under keyboard
focus. One text field frame serves every field: an outlined container with the
label as its hint, never a floating label.

### Android

Material components, Material's ripple, and its bottom sheet keep their own
behavior. The Android preview is not a product host.

## Foundations

### Semantic palette

The accepted application palette is the prototype's warm light surface and
deep green dark surface, expressed as Material 3 semantic tokens. The historical
brand-board Paper `#F5F2EA`, Clay `#A54B35`, and Mist `#DCE4DF` remain
brand provenance, not replacements for the current application colors.

| Role | Light | Dark |
| --- | --- | --- |
| surface | `#FFFEFA` | `#1C2520` |
| onSurface | `#18231F` | `#F2F2E9` |
| primary / secondary | `#2E5D50` | `#A7C3A2` |
| onPrimary / onSecondary | `#FFFFFF` | `#18231F` |
| primaryContainer | `#E4EBE5` | `#334436` |
| secondaryContainer | `#EDF2EA` | `#2A3A2E` |
| onSurfaceVariant | `#626B63` | `#AFB9AE` |
| surfaceContainer / surfaceVariant | `#F6F6F0` | `#232E26` |
| surfaceContainerLow | `#F8F8F2` | `#202A23` |
| outline | `#A3AFA2` | `#6D816E` |
| outlineVariant | `#DEDFD5` | `#39453B` |
| tertiary (caution) | `#805811` | `#EDC780` |
| tertiaryContainer | `#FBF3DF` | `#352E20` |
| error | `#A93228` | `#FFA69B` |
| errorContainer | `#FFF0EC` | `#392823` |
| background (outer canvas) | `#EEEEE8` | `#141B17` |

The complete ColorScheme mapping is in
[PosatoPalette.kt](shared/src/commonMain/kotlin/app/posato/core/designsystem/PosatoPalette.kt).
Ordinary application content uses surface, not the outer canvas color.
Safe-area regions use the same surface as the application.

Increase Contrast maps muted text to onSurface, quiet outlines to outline,
and outline to onSurface. iOS observes the system darker-colors notification;
macOS reads the native setting when the window opens or regains activation.
Dark appearance follows the system. These integrations are not a claim that
every control state has passed a measured contrast or assistive-technology audit.

### Typography

Use the existing platform-resolved sans-serif rendering, without bundling a
new font. Explicit text dimensions are Compose sp; do not replace LocalDensity's
font scale with a fixed value. The prototype's simulated Larger text switch is
not copied into the app.

| Token | Size / line height (sp) | Weight |
| --- | --- | --- |
| displaySmall | 40 / 46 | Medium |
| headlineLarge | 38 / 44 | Medium |
| headlineMedium | 30 / 36 | Medium |
| titleLarge | 25 / 32 | Semibold |
| titleMedium | 20 / 27 | Medium |
| titleSmall | 14 / 21 | Medium |
| bodyLarge | 16 / 25 | Regular |
| bodyMedium | 14 / 23 | Regular |
| bodySmall | 12 / 19 | Regular |
| labelLarge | 13 / 20 | Medium |
| labelMedium | 12 / 18 | Medium |
| labelSmall | 11 / 16 | Semibold |

labelSmall is 11 / 16 so the smallest label stays readable. iOS adds three bar
roles: BarTitle 17 / 22 semibold and BarAction 17 / 22 regular, both -0.43 sp,
and LargeTitle 34 / 41 bold, +0.37 sp.

Headline letter spacing is -1.8, -1.5, and -1 sp for displaySmall,
headlineLarge, and headlineMedium; titleLarge is -1 sp; eyebrow labelSmall
is +1.2 sp. Unspecified typography roles, including headlineSmall used for
section titles and wheel values, retain the pinned Material typography default.
Use the same roles as the reference rather than approximating them by eye.

### Spacing, shape, borders, and elevation

All geometry below uses Compose dp; native window radius uses Apple points.

- Spacing: 1, 4, 8, 12, 16, 24, 32, 48.
- Shapes: extraSmall 6, small 8, medium 10, large 14, extraLarge 24.
- Interactive minimum: 44 on both app hosts.
- Standard button padding: horizontal 18, vertical 11; compact padding 12 / 8.
- Buttons stay flat during hover, focus, and press. On the Apple hosts press
  dims the content and keyboard focus draws a ring; Android uses the ripple.
  No state adds elevation.
- Standard icons 18; prominent/navigation icons 24; item-symbol surface 36.
- Wordmark mark 24 × 32; decorative interval artwork 122 × 144.
- Primary sidebar 224; content maximum 820; compact breakpoint 600.
- Phone reference / duration-wheel maximum width 390; menu minimum width 200.
- Hairline borders/dividers: 1 using outlineVariant.
- Disabled content alpha: 0.5.
- Do not add card stacks, generic dashboard shadows, or gratuitous dividers.

The complete token definitions are in
[PosatoTokens.kt](shared/src/commonMain/kotlin/app/posato/core/designsystem/PosatoTokens.kt).
The smaller Sidebar token belongs to the alternate reusable scaffold, not the
current primary navigation shell. Phone is a sizing reference, not a frame.

### Identity and iconography

The open-interval mark uses two asymmetric, softly rounded, forward-leaning
forms. The lowercase posato wordmark and sparse larger interval artwork are
code-native, not raster images. Artwork is decorative and omitted in compact
hero layouts.

PosatoIcons supplies consistent vector strokes for pause, items, globe,
applications, search, arrows, edit, remove, close, and state symbols. Essential
icons have a visible label or accessibility description. Native pickers keep
their own recognizable system labels; the shared iOS UI must not invent app names.

### Application icon

`user-confirmed` (2026-09-14): the maintainer chose **Forest**, proposal B
from `DESIGN-002`. The default icon uses a moss `#2E5D50` field with sage
`#A7C3A2` and warm-white `#FFFEFA` interval forms, preserving the mark's
rounded ends, offset, and eight-degree rotation. The dark version uses the
dark surface with sage and warm-white forms; the tinted source is monochrome.

The [source artwork](docs/design/app-icon/README.md) accompanies the exports.
iOS receives opaque 1024 × 1024 default, dark, and tinted sources in
`iosApp/iosApp/Assets.xcassets/AppIcon.appiconset`. macOS receives
`desktopApp/Config/Posato.icns`, with the same default artwork inside a rounded
tile and transparent outer padding. Platform wiring remains with `IOS-003`
and `MACOS-008`; supplying assets does not prove installed icon appearance.

### About Posato and licenses

`user-confirmed` (2026-09-14): **About Posato** sits beside the wordmark on
iPhone and below **On this Mac** in the Mac sidebar after setup. Since
`DESIGN-004` it is the info symbol in Session's bar on iOS, and an entry in the
iPad's landscape sidebar.
The Mac device label and About text align with the primary navigation labels;
a 4 dp gap keeps the device label and About action together.
Session, Paused items and Schedules are the primary destinations. About Posato
opens a secondary content screen with the app's short purpose, installed
version, and a **Licenses** disclosure. **Back** returns to the preceding
primary destination. On iPhone, the bottom navigation gives way to this
secondary flow. On Mac, the sidebar stays available, with no primary
destination selected.

The version comes from the running application's metadata, without a second
hand-maintained version string. An unpackaged or incomplete build reports
**Version unavailable** rather than inventing a version. The Android preview
target is not a product host and uses that unavailable case.

Licenses lists **License**, **Notice**, and **Third-party notices** using
existing disclosure rows. Each opens the complete scrollable document with
selectable text, a heading, and a persistent **Back to licenses** action (on
iOS the bar's back button).
Third-party notices render Markdown headings, emphasis, inline code, and lists.
The component table uses three columns on wide panes and labeled cells in each
row on narrow panes. Document links appear as labeled Read buttons beneath their
paragraphs, so keyboard and screen-reader users can open the bundled document
on either platform.
The other two legal files retain their original plain-text formatting.
**Back to About Posato** returns from the list to the information screen.
Content uses the existing surface, typography, spacing, and quiet-button
tokens. Loading and an unavailable document have explicit text; a read failure
offers **Try again**. These documents work offline and contain no device or
account data. Build-time resources come directly from the root `LICENSE`,
`NOTICE`, and `THIRD_PARTY_NOTICES.md`, without hand-maintained copies.

### Mac updates

`user-confirmed` (2026-09-23, `MACOS-011` D3): updates add no destination.
Only a Mac build with a configured update feed shows them; iOS and unpackaged
builds show nothing.

- **Consent.** The first time the destinations appear after first-run setup,
  a native alert asks **Check for updates automatically?** with **Check
  Automatically** and **Don’t Check**. It explains that Posato asks GitHub once
  a day whether a newer version exists, that nothing is downloaded or installed
  without a choice, and that About Posato changes it. It is asked once; the
  answer lives only in the updater's local settings, which are the source of
  truth for the toggle below.
- **About Posato.** An **Updates** section sits above Licenses: a selection row
  **Check for updates automatically** with its explanation, and a quiet
  **Check for Updates…** button, disabled while the updater is busy.
- **Application menu.** **Check for Updates…** follows **About Posato** and
  runs the same manual check.
- **Installation.** Sparkle's standard windows handle found updates, download,
  and Ready to Install. **Install Update** first shows **Preparing to update…**
  while Posato confirms that blocking has stopped. A refusal alert, **The update
  can’t be installed now**, names the reason: an active session, another open
  copy of Posato, or unconfirmed cleanup.

## Components

All product components live in
[app.posato.core.designsystem](shared/src/commonMain/kotlin/app/posato/core/designsystem).
They consume the single PosatoTheme and accept state/callback/content slots;
they do not own policy persistence or construct feature ViewModels.

| Family | Reusable components |
| --- | --- |
| Identity | PosatoMark, PosatoWordmark, PosatoIntervalArtwork |
| Text | PosatoEyebrow, PosatoTitle, PosatoBody, PosatoCaption, PosatoHeading |
| Layout | PosatoPanel, PosatoDivider, PosatoActionRow, PosatoSection, PosatoSectionHeader |
| Shell | PosatoNavigationScaffold, PosatoAppScaffold, PosatoSidebar |
| iOS chrome | PosatoBarScreen, PosatoNavigationBar, PosatoBarButton, PosatoLargeTitle, PosatoLead, PosatoBackMark, CupertinoNavDisplay (behind PosatoNavStack) |
| Main navigation | PosatoBottomNavigation, PosatoBottomNavigationItem, PosatoSidebarNavigationItem |
| Choices/navigation | PosatoTabBar, PosatoTab, PosatoNavigationItem, PosatoChoiceGroup, PosatoSetupStep, PosatoDeviceLabel |
| Actions/input | PosatoButton, PosatoTextField, PosatoFieldMessage, PosatoSearchField, PosatoNumberWheel |
| Items | PosatoItemList, PosatoItemRow, PosatoItemSymbol, PosatoBadge, PosatoDisclosureRow |
| Menus | PosatoItemMenu, PosatoMenuItem, PosatoSwipeRow, PosatoSwipeAction, PosatoAlert |
| Selection | PosatoToggleButton, PosatoChoiceTile, PosatoSelectionRow, PosatoDurationChoice, PosatoSwitchRow, PosatoDayToggle, PosatoPickerRow, PosatoTimeRow |
| Status/content | PosatoNotice, PosatoStatusLabel, PosatoHero, PosatoEmptyState, PosatoActivityIndicator |
| Session/support | PosatoEndTime, PosatoSyncFooter, PosatoPrivacyPoint |
| Icons | PosatoIcons, PosatoIcon |

The complete product component set is retained for subsequent MVP slices,
including currently unused support patterns. Prototype catalog/workbench,
fake-device wrappers, reducer, mock datasets, and control overlay stay outside
the product modules.

### Buttons and input

Primary buttons use primary/onPrimary; secondary and quiet actions remain
visually subordinate. Action rows wrap with 12 horizontal / 8 vertical spacing.
Do not substitute raw default-styled Material controls where a Posato pattern exists.

Text fields share one frame: an outlined container with the label as its hint,
no floating label, supporting/error copy, and UI-owned TextFieldState. Website entry is multiline (up to two visible lines),
with inline Add and a URI keyboard/Go action. Return submits; Shift-Return adds
a line break on desktop. Add remains easy to repeat without dismissing focus.

### Nested tab bar

The Websites / Apps control is a segmented tray on surfaceContainer, large
radius, 4 padding, and 4 between segments. A selected segment uses
surfaceContainerLowest in light and surfaceContainerHighest in dark, medium
radius, and primary text.
Inactive segments retain normal onSurface text, not low-contrast disabled styling.
Labels/counts are centered vertically and horizontally; counts are muted.
The tray's outer edge aligns with the surrounding content.

Use this component for peer choices within a destination. Session / Paused items / Schedules
uses the platform-specific primary navigation, not a second nested tab bar.

`user-confirmed` (2026-09-29, `NAV-001`): screens inside a destination form a
stack, and the platform's back returns one screen exactly as that screen's
explicit **Back**, **Cancel**, or **Back to …** action does: the interactive
edge swipe on iPhone and iPad; Command-[, Escape, and the trackpad's two-finger
swipe between pages on the Mac. Search's **Back to adding** is part of the
same stack. In a focused text field the first Escape only leaves the field and
keeps what was typed; the next Escape goes back (`user-confirmed`,
2026-09-30). Back never switches destination or leaves the application, and it
is unavailable while a start, end, save, or system approval or password prompt
is in progress. On iOS the screen slides with the finger and settles with its
velocity, as [Platform adaptation](#ios-and-ipados) describes, and without a
slide under Reduce Motion. The Mac changes screens without a transition; the
trackpad swipe changes the screen as the fingers lift, once macOS counts the
swipe.
Onboarding stays forward-only.

### Rows, menus, and notices

Rows align a 36 symbol surface, readable title/supporting text, and trailing
action. Separators belong to rows; do not stack another separator immediately
after a final row. Disclosure rows expose a clear chevron and a button role.

On the Mac the ellipsis is a 44 circular control with a 24 icon and a quiet
background. The open menu is at least 200 wide, uses surface, large radius, and
a subtle outline. Edit and Remove are full menu actions; Remove uses the error
color. On iOS the ellipsis opens the system menu, and list rows use a tap and
swipe actions instead of a per-row menu.

Notices use Neutral, Positive, Caution, or Critical tone. State must be named
in text and offer a precise available action. Loading, denied/restricted access,
corruption, retryable failure, and saved-but-not-enabled choices stay distinct.

## Layout and Responsive Behavior

### iOS

The app fills the real device viewport, without a fake phone frame.
Respect safe drawing and keyboard insets. Bars, large titles, the tab bar, and
the keyboard follow [Platform adaptation](#ios-and-ipados): the wordmark is
Session's large title, Session / Paused items / Schedules lives in the tab bar,
and the keyboard rises over the tab bar while a bar Done clears focus.

`user-confirmed` (2026-09-22, `IOS-004`): iPad follows these rules and names
itself **iPad** wherever the iPhone names itself. In landscape, iPad moves
the primary destinations into a 224 sidebar: the wordmark on top, then the
destinations, then **On this iPad** and **About Posato**. The sidebar stays
visible while the keyboard is up and during About. iPad portrait and iPhone in
either orientation keep the tab bar. Rotation keeps the selected destination,
the open screens, and entered text.

Content uses the 24 bar inset. Lists consume the remaining height and scroll
independently of entry, category tabs, and the bar. Selected-item details are a
pushed screen with **Back to Session** and **Edit**, the category tabs, and an
always visible website filter.

### macOS

Use a real resizable native window, initially 1060 × 780, with minimum width
614 and a 224 sidebar. Main content is centered within its available pane,
capped at 820 including inner horizontal padding of 48 in expanded layout
(24 below the 600 breakpoint). Session uses that inset vertically; Paused items
uses 12 vertically.
The wordmark has room below native traffic lights.

The native title remains Posato for accessibility/window identity while the
visible title strip is hidden. Full-content transparent titlebar, native traffic
lights, dragging, resizing, and fullscreen behavior remain; there are no fake
window buttons. The AppKit frame uses a 20-point corner radius, zero in fullscreen.
The small window library is packaged and signed with the application.

Selected-item details use a dialog, not an imitation phone sheet. Compact mode
changes content arrangement, not the Mac sidebar into mobile navigation.

#### Menu bar presence

`user-confirmed` (`MACOS-012`, 2026-09-26): Posato stays running
when its window closes and lives in the menu bar.

- **Status item.** A monochrome template of the Posato pause mark that
  follows the menu bar's appearance. It has no color, animation, badge, or
  countdown in the bar. Only a session whose restrictions are confirmed
  active uses the filled variant. The item's accessibility description
  names Posato and the state, for example "Posato, restrictions active" or
  "Posato, restrictions not active".
- **Menu.** The first line names the state, and there is at most one
  primary action. Every other item routes into the window's existing flow.
  - No session: No session active · Start a session… · Open Posato · Quit
    Posato.
  - Active and enforcing: Session active until *time* · *n* min left · End
    session early… · Open Posato · Quit Posato.
  - Active, not enforcing: Restrictions not active on this Mac · Resume
    restrictions… (only when resuming can apply them) · End session early… ·
    Open Posato · Quit Posato.
  - Updating or maintenance: Update in progress · Open Posato · Quit Posato.

  An ellipsis marks an item that opens the window at that flow. The menu
  never ends a session, grants permission, or raises an administrator prompt
  by itself. Remaining time refreshes when the menu opens and at most once a
  minute while it is open.
- **Window.** The close button and **Close Window** (Cmd-W, in the **Window**
  menu) hide the window.
  Posato is a regular application with a Dock icon and menus while its
  window is open, and an accessory with only the status item while it is
  closed. Opening Posato again from Finder, Spotlight, or **Open Posato**
  shows the window in the destination and state it had.
- **First close during a session.** One notice, shown once per device, with
  **OK** and **Quit Posato**. When this Mac's restrictions are confirmed
  active: "Posato is still running. Blocking continues while Posato is in
  the menu bar." Otherwise: "Posato is still running in the menu bar.
  Restrictions not active on this Mac." A running timer alone never selects
  the first text.
- **Quit.** **Quit Posato** and Cmd-Q quit at once when no session is active.
  During a session a confirmation reads **Quit Posato?** "Blocking stops on
  this Mac until you open Posato and resume it. The session stays active on
  your other devices." with **Keep Posato open** (default) and **Quit**. The
  first sentence appears only while restrictions are confirmed active.
  Logout, restart, and update relaunch never wait on it.
- **Open at login.** In This Mac options, a switch **Open Posato at login**,
  off by default. The delivered flow keeps it in This Mac; `ONBOARDING-004`
  adds the first-run offer specified below. A login launch
  keeps the window closed and never asks for approval on its own.
  **Remove from this Mac** also turns the switch off, so no login item
  remains after Posato is moved to the Trash.
- **Start without the password.** `user-confirmed` (2026-09-26,
  `MACOS-014`, ADR 0004 amendment): in This Mac options, below **Open
  Posato at login**, a switch **Start sessions without the password**, off
  by default. It appears only while the helper is ready and its daemon
  supports the grant. Turning it on asks an administrator once; turning it
  off needs no password. Supporting text: "An administrator approves this
  once. Restrictions apply only during a pause you start or a schedule you
  set." (`SCHEDULE-002` slice 6, integration contract 5)
  The switch shows only what the helper confirms. It is disabled with "You
  can change this after the session ends." while a session is active,
  starting, or changing enforcement. When its state cannot be confirmed, it
  is disabled with "Posato could not confirm this setting. Check again." With
  the switch on, starting a session and **Resume restrictions** apply
  without a prompt. The menu, a login launch, and synchronization still
  never apply by themselves.
- **Session notice.** The `RESUME_REQUIRED` notice no longer blames closing
  the app. It reads "Restrictions not active on this Mac." It keeps
  **Resume restrictions**, because quitting, sleep, a login launch, and a
  session received from another device all lead there.

### Reflow

Essential status, end time, warning, and primary action must remain accessible
at larger text sizes. Prefer wrapped action rows, expanding row height, and
scrolling rather than clipping labels or forcing fixed-height cards. The full Apple accessibility
text-size range and all supported window sizes still require native verification;
a normal-size screenshot alone does not prove coverage.

## Current Screens and Interactions

### macOS browser pause page

The fixed local page uses the application surface, ink, primary, and muted
text tokens in light and dark appearance, with a system sans-serif stack.
The open-interval mark and lowercase wordmark lead one left-aligned column,
centered in the viewport with a 600 CSS-pixel maximum width at default text
size. The mark preserves the application's asymmetric 24 × 32 geometry.

The headline reads **This site is paused until {local time}**, or **This site
is paused** when the session end is unavailable. Supporting text reads
“Return to Posato to change this session.” It is a plain instruction. The
page offers no session controls or app-activation link. The displayed end is
the session end at page load; there is no countdown, refresh, or animation.

Use rem-based typography and spacing, wrapping content, and natural vertical
scrolling at enlarged text sizes or short viewports. Increased contrast uses
the primary text color for supporting copy. The decorative mark is hidden
from accessibility; the page has one main landmark and one level-one heading.
All styling and mark geometry are inline, with no scripts, network assets, or
attempted/selected target in the page, as required by ADR 0005.

### posato.app website

The public site at `posato.app` has a product page, the limits, the privacy
policy, a support page, and a not-found page. It uses the pause page's surface, ink,
primary, and muted tokens, plus surfaceContainer and outlineVariant, in light
and dark appearance, with the same system sans-serif stack, lowercase
wordmark, and CSS open-interval mark. Content is one left-aligned column with
an 820 CSS-pixel maximum and a 600-pixel compact breakpoint; navigation and
footer links keep a 44-pixel minimum height.

The product page reuses the README and store-listing copy: **Pause. Then
choose.**, the three steps, the limits summary, privacy, and an availability
note. `user-confirmed` (2026-09-17, `RELEASE-002`): the hero and the availability
note carry the maintainer-provided "Download for Mac" badge artwork, recolored
to the ink surface (`#18231F`) with a `#6D816E` outline and a 12-pixel radius,
self-hosted and linked to the latest GitHub Release. The matching App Store
badge sits beside it and links to the App Store listing since 2026-09-24; before
that it was unlinked at reduced opacity with a "Coming soon" caption. Apple badge guidelines are not reviewed for now. The
header ends with a GitHub mark linking to the public repository, and the support
page adds GitHub Issues and private vulnerability reporting. The hero plays the showcase demo rendered from `video/`
as a silent, looping, self-hosted video in a plain outlined panel, with a
poster for the first paint and under Reduce Motion; compact layouts omit it
(`user-confirmed`, 2026-09-16, replacing the earlier iPhone hero capture that
repeated the frame of the step below it). The step sections show real Mac and
iPhone captures with synthetic choices from `video/public/`, set in generic
CSS device frames so they stand apart from either appearance: the Mac window
sits on a moss-to-sage wallpaper inside a graphite display bezel, the iPhone
capture inside a rounded graphite phone bezel, each with a 1-pixel outline.
The demo draws the same bezels, so the hero panel adds none. Frames are not
Apple artwork and add no base or shadow.
The policy page renders `PRIVACY.md` without typographic substitution, the
limits page renders the Limits section of the accepted limits document, and the
favicon is the Forest icon source. Pages have no scripts, inline styles,
cookies, analytics, web fonts, or third-party assets; all media, including the
hero video, its poster, the step captures, and the download badge, is
self-hosted.

### First-install onboarding

`user-confirmed` (2026-09-09): adopt the reviewed onboarding UI proposal
in PR #44. The six steps and existing service/persistence behavior remain.

- Show one current-step label and an `n of 6` caption instead of the full
  step list. Each new step starts at the top of its own content.
- Welcome uses the existing interval artwork, “A little space. For what
  matters.”, a short explanation, and **Make some space**.
- Privacy uses “Your choices stay yours.” and three icon-led rows for
  browsing-history exclusion, encryption before optional iCloud upload, and
  device-local app choices. Supporting copy is subordinate to each row title.
- iCloud remains optional. An unlinked device offers **Sync with iCloud** and
  **Not now**, with the websites clause of the linking sentence woven into
  the existing content. A linked device offers **Continue** and secondary **Sync now**;
  “Connected to iCloud” describes linking, never delivery to other devices.
  Waiting, retryable, syncing, and action-required states keep their real meaning.
  A consented fresh join waiting for its workspace key offers primary
  **Continue** and secondary **Check again**. Show a short waiting status,
  separate Apple-prerequisite explanation, and “You can continue setup while
  you wait.” Locally saved choices and pending sync remain separate facts
  in the summary. Manual recheck and foreground continue the same consented
  account/workspace; neither creates a new workspace. Waiting is process-local:
  relaunch returns to explicit Sync with iCloud. Apple owns any device approval.
- Permission rationale names websites and apps on both platforms. No item is
  paused until a session starts. **Continue** replaces the request action only
  after the returned access/helper state is ready. **Not now** defers setup;
  unavailable versions never suggest a settings change can enable them.
  On Mac, the permission step shows “Checking Mac setup…” or “Enabling the
  background helper…” as soon as that call starts. **Not now** stays usable
  during the Mac helper call and does not cancel it; a late result updates
  Summary and Session without advancing or starting enforcement. Duplicate
  Enable and Check again stay disabled while the call runs. iPhone Screen Time
  request still disables **Not now** until the system sheet returns.
- Website entry uses “What pulls you away?”, the existing domain form and
  validation, and **Continue** after a saved addition. Before an addition,
  **Not now** keeps an empty setup possible. `user-confirmed` (2026-09-23,
  `ONBOARDING-003`): entry keeps focus after each submission, so the next
  website needs no click. Below the field, a caption states the saved total
  from policy state, separate from the feedback about the last submission; it
  follows policy changes while the step is visible.
- Summary leads with saved choices, device access, and local/iCloud scope.
  Its saved-website count follows policy changes while the step is visible;
  a failed read keeps the last valid count. Website-entry focus responds only
  to the person's successful submission, never to a remote count change.
  When applicable, a notice names the remaining setup and its existing route.
  **Go to Session** opens Session; it does not start a pause.
- Compact pages keep actions beneath independently scrolling content, with a
  full-width primary button. The iPhone wordmark hides while typing. Expanded
  pages keep actions directly after content in a column capped at 600 dp
  inside the existing 820 dp outer canvas. `user-confirmed` (2026-09-23,
  `ONBOARDING-003`): the expanded content scrolls on its own, so actions stay
  above the keyboard or window edge, and several expanded actions share one
  wrapping action row in primary, secondary, quiet order. `user-confirmed`
  (2026-09-23, `ONBOARDING-003`): the keyboard must not cover the website
  field. While it is up, expanded pages use 24 dp vertical margins and gap
  before the actions, and the website step scrolls the whole field and its
  saved total into view. Use the existing typography, spacing, colors,
  safe-area handling, and native permission presentation.

### Pause notifications

`user-confirmed` (2026-09-27, `NOTIFY-001`, delegated night mandate): local
notices only.

- **Pause over:** "Your websites and apps are available again." It is
  scheduled at the planned end and withdrawn if the pause ends early.
- **Pause started:** only for a pause started on another device: "A pause
  started on another device." The Mac adds "Open Posato to block on this Mac
  too.", because a received pause waits for **Resume restrictions**.
- **Permission:** asked once, right after the person's first pause on the
  device, never at launch.
- **Preference:** About Posato has a **Notifications** section with one
  switch, **Pause notifications**, on by default: "A notice when a pause
  ends, or when one starts on another device." When the system has denied
  notifications, the switch is disabled and reads "Notifications are turned
  off for Posato in System Settings."

### Release 1.2 setup and schedules

`user-confirmed` (2026-09-26, PR #92): accepted for `ONBOARDING-004` and
`SCHEDULE-002`, pending delivery. The
[product scope](docs/product/schedules-and-mac-setup.md) owns schedule
behavior and the authorization decisions still required by `SCHEDULE-001`.

`user-confirmed` (2026-09-26, latest PR #92 follow-up): use one guided
**Set up Posato on this Mac** screen. The main action is **Set up Posato**.
Explain its effects in one panel with plain descriptions, without switches or
separate requirement cards:

- Block chosen websites and applications.
- Start quietly at login without opening a window. The app stays in the menu bar.
- Start manual pauses and schedules without repeated passwords. A short caption
  explains automatic starts on sign-in or wake during a scheduled interval.

Keep the explanation compact enough to read its effects above the setup action
at the normal desktop window size. Settings details can scroll below it.

Keep the existing six-step onboarding. The setup screen is reused from
onboarding, Session's **Finish setup**, Schedules and the dismissible upgrade
offer. Advanced status, revocation and removal stay in This Mac settings.
This supersedes the separate onboarding opt-ins and the earlier **Required for
schedules** checklist. The existing This Mac switches describe current service
behavior until the unified flow is implemented; they are not extra choices in
the new primary setup flow. With `ONBOARDING-004` they remain in This Mac as
individual controls below the one setup action.

The intended connected flow shows progress, names the current system action
when approval is needed, and resumes only the missing step after interruption.
Re-read actual state after operations and after returning from System Settings.
Only verified completion of every requirement may show **This Mac is ready**.
Do not promise exactly one password entry. **Not now** leaves editing and sync
available, with one persistent notice and **Finish setup** in Session. Unknown
state asks for a check. Do not repeatedly reopen a modal or prompt when a
schedule is due. After completion, Start and Add schedule open their forms.

`user-confirmed` (delegated night mandate, 2026-09-26; `ONBOARDING-004`):
**Set up Posato** runs blocking, then opening at login, then starts without a
password, skipping finished steps. While it runs, each effect line carries its
status (working, waiting for approval in System Settings, waiting for the Mac
password, done, not finished yet) and one caption names the single thing to do
next. The approval caption says to turn on Posato under **Allow in the
Background** and that macOS may ask for the password. An interrupted, declined
or cancelled run says "Setup did not finish. Posato continues where it
stopped." with **Try again**. **This Mac is ready.** appears only after every
step was verified; an older helper that cannot keep the permission never
counts as ready.

- **Onboarding** shows the action with a quiet **Not now** (or **Continue**
  when blocking already works); after completion, **Continue** is primary.
  The individual helper controls sit collapsed under **Blocking settings**.
- **Session** keeps **Finish setup** while blocking is missing. When blocking
  works but opening at login or the password step is known to be missing, one
  compact **Set up Posato on this Mac.** card appears below **Start a
  session** with a secondary **Set up Posato** and **Not now**, so Start stays
  the one primary action. Dismissal and verified completion are
  remembered on this Mac, so the card appears at most until one of them.
  Unread or unknown states never raise it.
- **This Mac** leads with **Set up Posato** once blocking works and opening at
  login or the password step is still missing (while blocking is missing,
  Session's **Finish setup** carries the action); its switches, **Check
  again** and removal stay below and are disabled while setup runs. During a session the
  action is disabled with "You can finish setup after the session ends."
- Check, enable, remove, the grant switch and setup never run at once, and a
  session that starts mid-run stops setup before the password step.

Do not begin password-requiring setup during a session or while enforcement
starts or changes. Login registration alone authorizes no restrictions.
Automatic Apply still needs the independently reviewed ADR amendment owned by
`SCHEDULE-001`; the existing manual Start/Resume grant is not sufficient.

The UI shell adds **Schedules** beside **Session** and **Paused items** in
each platform's existing adaptive navigation. `SCHEDULE-002` connects its
actions and replaces the availability notice with real readiness.

- The empty state explains recurring pauses. On a Mac that is not set up,
  **Set up this Mac** opens the shared setup shell and **Add schedule** stays
  hidden; a verified Mac offers **Add schedule** directly. iPhone always
  offers **Add schedule** (`SCHEDULE-002` slice 3).
- The Mac setup screen has the same explanation and the working **Set up
  Posato** action as onboarding. **Back to schedules** returns without
  creating anything. There is no additional Continue-to-schedule
  prerequisite button.
- The list shows each schedule's name, weekdays, hours, enabled state and
  next run. On iOS a row edits on a tap, its switch turns it on or off, and a
  left swipe reveals **Skip next** and **Delete**; deleting asks with the system
  alert. **Add schedule** moves to the bar once a schedule exists; the empty
  state keeps it as its first step. The Mac keeps the row menu. Show local readiness or a specific problem separately from the
  enabled switch. Missing permission offers a direct setup action, such as
  **Set up this Mac**, while keeping the plan available to other devices.
- The editor has a name, weekday selection, start and end times, and an
  enabled switch. On iOS the weekdays are round toggles and **Starts** and
  **Ends** are rows with the system's compact time picker; an end before the
  start reads "Next day". Use the established form controls and validation style.
  Mac creation requires completed local setup. iPhone may still save a plan
  before local permission. Explain that automatic starts need consent and
  readiness on each device before they can run.
- **Skip next session** applies to the next occurrence. Show the skipped
  occurrence and the resulting next run so the action is understandable.
  An active scheduled session identifies its schedule and keeps **End
  early** in the existing session flow. Neither action disables the plan.
- Local Mac readiness requires the helper, login launch and explicit consent
  to automatic blocking without a password prompt. A revoked requirement
  shows setup required without deleting the shared plan. Never ask for an
  administrator password when a schedule is due. Show actual local
  restrictions independently of the timetable or another device's state.

Reuse the established typography, colors, spacing and native controls.
Weekday choices, switches and actions need accessible names and states.
Keep schedule details and setup actions readable with large text and narrow
windows through wrapping and scrolling under the existing reflow contract.
`SCHEDULE-001` settles time-zone presentation, overlaps and manual-session
conflicts before the editor and active-session details are implemented.

### Release 1.3 pause sets

Accepted with `SCHEDULE-003` (`user-confirmed`, 2026-09-30) for
`SCHEDULE-004`; the
[pause set rules](docs/product/pause-sets-decisions.md) own the behavior.
On delivery this section supersedes the **Paused items** destination name
and the single-selection wording in Session, Paused items, onboarding, and
the **Sync with iCloud** sentence above. Planned Polish term: "zestaw".

- **Destination.** `user-confirmed` (2026-09-30): **Pause sets** replaces
  **Paused items** in the bottom navigation and the sidebar. Its root is a
  list, even with one set: each row shows the name, a **Default** badge,
  the website count and "Apps on this Mac: 3" (or iPhone, iPad; "none
  chosen"), and the schedules that use it. **New set** is the primary
  action; at 10 sets it is disabled with "You can have up to 10 pause
  sets."
- **Set screen.** Opening a set pushes today's Websites / Apps screen onto
  the destination's stack, titled with the set's name, with **Back to pause
  sets**. Website entry, search, editing, limits, and the app picker keep
  their current rules; the app caption says the choice is for this set on
  this device. The row menu and the set screen's ellipsis offer **Rename**,
  **Make default**, and **Delete**. On iOS a row offers **Rename** and
  **Delete** under a left swipe, and the bar's menu offers all three; the bar
  carries **New set**.
- **New set and Rename** use one name field with **Save** and **Cancel**;
  empty names and more than 80 bytes are refused inline. On iOS this is the
  system alert with a text field, presented again after a refused name. A new set opens on
  its Websites tab. The first set shows as "My set" until renamed.
- **Delete.** A destructive confirmation names the set. When schedules use
  it, the dialog lists them and offers **Move to <set> and delete** for each
  other set (`DESIGN-004`; before it, **Change their set** and a set choice);
  while a running pause uses it, Delete is disabled with "You
  can delete this set after the pause ends." The default set offers no
  Delete; its caption says "Make another set the default to delete this
  one."
- **Choosing a set.** A **Pause set** row opens a list of sets with their
  counts; the default is preselected and marked. On iOS it is the system pop-up
  button and menu.
  - Session setup shows it above the duration presets. Review names "Set:
    Work" with the set's websites and this device's apps.
  - The schedule editor shows it below the name. Schedule rows add "Set:
    Work".
- **Readiness.** With websites but no apps chosen here, Review and the
  schedule row say "Apps aren't chosen for this set on this Mac. The pause
  includes its websites." with **Choose apps**, and Start stays available.
  With neither, Start is replaced by **Add websites or apps**, which opens
  the set, and a schedule shows "Nothing to pause on this Mac". A missing
  set shows "This schedule's set was deleted. Choose a set.", "This set is
  over the limit of 10. Delete a set to use it.", or "Waiting for this set
  from your other devices." An addition beyond this device's limits during
  a pause says it is not paused yet; nothing already paused is released.
- **Running pause.** The active summary names each part: "Set: Work, until
  17:00" and "Evening (schedule), Set: Leisure, until 22:00". **End early**
  keeps one meaning and ends every part. Copy near edits of a set in use
  says "Added items pause now. Removed items stay paused until the pause
  using this set ends."
- **Sync.** The linking sentence, in Session and in onboarding, becomes
  "Linking combines the pause sets and websites saved on your devices and
  shares an active session. App choices stay on each device." The first Pause sets screen on a linked
  workspace says once: "Update Posato on your other devices to keep them in
  sync."

Rows, menus, dialogs, and the set choice reuse the existing item, menu,
selection, and notice components. Names and counts wrap under large text;
badges and counts have accessible labels.

### Session

- Inactive: NO SESSION ACTIVE (sentence case on iOS: No session active), Room
  for what matters., one primary start action.
  Without effective items, Choose paused items routes to the editor.
- Setup: YOUR NEXT PAUSE, How much space do you need?, one choice group of six
  quick lengths (25 min, 45 min, 1 h, 2 h, 4 h, 8 h) followed by **Until end of
  day** with its end (`ends 00:00`), which wraps to its own line only in a
  narrow window, then Hours / Minutes wheels with explicit Increase / Decrease
  buttons. On iOS setup is pushed as **New pause** with a segmented control for
  the lengths (labels `25m` … `8h`, read in full as "25 minutes" … "8 hours"),
  a one-segment control for **Until end of day**, and the system countdown
  wheel, without the eyebrow or Cancel. The set and the iOS layout are
  `user-confirmed` (2026-10-09, `SESSION-007`). The single Mac group with
  **Until end of day**, its visible labels (25 min … 8 h), and the iOS reflow
  to two rows of three when large text does not fit six are `user-confirmed`
  (2026-10-10, by questionnaire in `DOCS-005`).
- **Until end of day** is an end time, not a length: the pause ends at the next
  local midnight. It is shown only while that midnight is 5 minutes to 24 hours
  away, and Start keeps the midnight that Review showed, refusing as too short
  if it is nearly reached. The wheels show the time left until midnight; turning
  them or choosing a length clears the choice. When the choice is no longer
  offered, or the setup stays open past midnight, it is cleared and the length
  returns to 25 minutes; Review and a late Start then refuse as too short, or
  as already passed after midnight, instead of starting a short or next-day
  pause. This reset is `user-confirmed` (2026-10-10, by questionnaire in
  `DOCS-005`).
  Review names a length
  in hours and minutes ("8 hours · you stay in control") or **Until end of
  day**.
- Bounds: five minutes through 24 hours. Zero hours restricts minutes to 5–59;
  24 hours restricts minutes to zero. Changing hours clamps the total safely.
- Wheels snap, support arrow keys and Home/End on desktop, and expose a range
  and adjustable value to accessibility. Controls grow with wheel text.
- Review: ONE LAST LOOK, resolved end time, real selection/authorization state,
  Start this pause and Change duration. On iOS Review is pushed over New pause,
  and its bar's back replaces Change duration. A missing effective selection or mapping
  load failure cannot be presented as ready.
- Active: SESSION ACTIVE, timer end and remaining duration, End session early,
  compact item summary. Copy explicitly describes a local timer.
- Early end: Ready to return?, End session, Keep this pause. On iOS this is the
  system alert.
- Ended/expired: inactive state plus the real early-end or expiration message.
- Summary: two disclosure rows, not every website/app. Quiet Add or edit websites
  and Manage apps actions route directly to the corresponding category under
  Paused items. During an active session, Items at session start names the frozen
  summary, and preceding copy explains that the actions edit current Paused items.
  Its detail list distinguishes start-set websites from the current app selection.
  The read-only list has category tabs, a category-specific editing route,
  Filter list to reveal Filter selected websites, and Close list (on iOS the
  pushed screen described under Layout). Filtering
  changes only the visible list. iOS shows opaque application counts; Mac shows
  actual local names.
- Device setup uses collapsed **iCloud** and **This Mac** rows with short,
  real-state summaries. This Mac is macOS-only. Expanding a row reveals its
  explanation and controls; it never starts a helper check or sync attempt.
  A pending fresh join shows **Check again** inside iCloud options.
  Sync now and Remove workspace live inside iCloud options, with the existing
  removal confirmation. When unlinked, the expanded iCloud options carry the
  full linking sentence as a caption next to the action. Setup controls are
  secondary to the Session action.
- This Mac (macOS only, after iCloud): `user-confirmed` (2026-09-26, PR #92
  unified setup, superseding the on-demand read): Session reads the helper
  state once, quietly, when it first appears, with no progress label or
  announcement, so a ready Mac never shows **Finish setup**. Before that
  answer arrives nothing is gated. **Check Mac setup** inside the expanded
  options stays available for an explicit read. It runs one status read
  when no helper request is outstanding; after a lost reply, **Check again**
  finishes that original request instead of starting a new one. The section
  then names the real helper state with one precise action: **Enable on this
  Mac** when the helper is not enabled, including when Service Management
  reports the daemon as not found after a background-item database reset;
  **Open System Settings** plus **Check again** when background approval is
  required; "Background helper enabled" when ready; and **Check again** when
  the last request did not finish, the helper could not be checked, or it is
  registered but could not start. An Enable whose registration attempt fails
  and leaves the service unregistered reports that setup could not be
  completed instead of repeating not enabled. Each state carries its own short
  collapsed summary; an unfinished request and a helper that is registered but
  cannot start are not merged into one attention summary.
  In-app copy does not tell the person to remove the helper from Login Items:
  that would unregister it without confirmed Idle cleanup. Restarting Posato
  does not repair a broken service registration.
  `user-confirmed` (2026-09-11): every known state except not enabled keeps a
  quiet **Check again** inside its options; **Enable on this Mac** already
  registers and re-reads the status, so that state shows only that action. When
  the same result comes back again in a state whose action cannot change it —
  the helper could not be checked or enabled, or it is registered but cannot
  start — the options add one sentence naming the repeat and offering a restart
  of this Mac as the next step, without claiming it repairs the registration.
  An unfinished request does not carry that sentence, because its retry really
  does reconcile the original request.
  While a call runs the row reads "Checking Mac setup…" or
  "Enabling the background helper…" and the duplicate actions are disabled.
  The Mac host sends native
  accessibility announcements for progress and the returned result, including
  an unchanged result after rechecking and the repeated-result sentence when
  it is shown. No time or success claim.
  `user-confirmed` (2026-09-15, MACOS-009): the removal entry point sits here,
  and removal is refused during a session.
  - **When offered.** Once a check or a removal result names the helper as
    ready, awaiting approval, not checkable, or with an unfinished request, the
    options add a
    quiet error-colored **Remove from this Mac**. It is not offered before a
    check, while the helper is not enabled, or while it is registered but
    cannot start.
  - **Confirmation.** A destructive dialog names what removal does:
    - proxy settings are restored, and the administrator rules and any
      permission to start sessions without the password are removed;
    - the background helper turns off, and website and app pauses stop on this
      Mac until it is enabled again;
    - paused items stay saved.

    **Keep the helper** cancels.
  - **During a session.** While a session is active, starting, or changing
    enforcement, the action is disabled and "Removal is available after the
    session ends." is shown. An open confirmation closes.
  - **Progress.** While removal runs the row reads "Removing the background
    helper…".
  - **Verified removal.** A Positive notice says the helper is removed and
    Posato can be moved to the Trash. The row then offers **Enable on this
    Mac** again.
  - **Other results.** A Caution notice names the next action without claiming
    removal:
    - remove again;
    - remove again to finish the unfinished request;
    - check again when the helper could not be reached;
    - allow background approval, then remove;
    - enable first, because cleanup cannot be confirmed;
    - check proxy settings, then remove;
    - the existing registered-but-cannot-start copy when the helper cannot
      start.

    While a failure that reached the daemon is shown, **Check again** is
    hidden. Such a failure never suggests it, because it could reinstall the
    removed rule.
- `user-confirmed` (2026-09-26, PR #92 correction): while inactive, a helper
  read that names a state other than ready keeps a persistent setup notice and
  **Finish setup** in place of Start, even if This Mac is expanded. The Finish
  setup screen shows the existing This Mac controls expanded, so the needed
  action is visible without another tap. A read that has not answered yet
  gates nothing.
  This supersedes the 2026-09-11 informational-only notice. Active enforcement
  keeps its own status and recovery actions rather than an older helper read.

Main navigation stays available during active sessions, and Paused items editing
retains its existing availability. Do not add an unrelated active-session lock.

### Paused items: websites

- Add websites is the primary input. Search is a quiet secondary action below it.
- Commas/newlines separate entries; domain names and HTTP(S) URLs are accepted.
  Only canonical exact hosts reach policy storage; paths/query/fragment do not.
  One stored host is what was typed. Matching also pauses its `www` variant;
  other subdomains stay separate. Entry copy and each row caption say so.
- Limits: 65,536 UTF-16 code units per batch, 1,024 per trimmed entry, 1,024
  unique domains in the policy. Credentials, other schemes, IPs, wildcards,
  malformed hosts, and overflow are rejected.
- One batch creates one policy revision for all valid new domains. Duplicates
  are counted; rejected entries remain editable. Failed writes keep the entire
  submitted draft; a late acknowledgement cannot erase newer text.
- Search temporarily replaces entry; Back to adding restores the draft.
  Changing query resets list position. Counts and Search align vertically.
- A lazy list provides row menus; the list has the accessibility name Saved websites.
  Edit opens Website domain with Save changes and Cancel.
- Add/search/edit drafts belong to UI state and survive destination changes.
  They are not persisted across application relaunch.
- If an unfinished website edit is open when Session routes to website adding,
  keep its draft available through Resume website edit; disable other website
  row changes until that edit is resumed and finished or canceled.

### Paused items: applications

Choose apps is the primary action next to the device-scope caption.
The native picker controls recognizable application selection. Mac lists names
with row Remove menus; iOS shows only a private selection count and Clear selection.
Unavailable, permission-required, denied, restricted, and corrupted states remain real.

A successful nonempty selection creates the singleton Applications group only
when absent. Existing custom group names are preserved. Cancel or an empty
first selection creates no group. If native choices save but group metadata
fails, retain them and offer Enable selected apps after resolving the failure.
Reloading alone does not silently activate retained choices.
Clearing choices keeps group metadata. Manual group-name CRUD is not exposed
in the new UI.

## Future Product Contracts

These patterns exist in the accepted product direction but are not simulated
as working controls in the current MVP.

- Onboarding explains purpose/privacy, invokes Sync with iCloud, waits for an
  existing workspace key, and asks permissions in context.
- Synchronization distinguishes local-only, pending, syncing, completed local
  attempt, retryable failure, waiting for workspace key, and action required.
  Sync now never claims every other device received a change.
- Session-driven restrictions report actual enforcement state and failures;
  a timer or saved policy alone is not enforcement proof.
- Platform-owned blocked presentation explains paused until, offers a route
  to Posato, and does not retain browsing history or full attempted URLs.

## Accessibility and Motion

Target 44-point actions on both hosts, meaningful labels/roles, logical focus,
keyboard navigation, VoiceOver/Voice Control/Switch Control, and non-color state
cues. Preserve system font scale and expose wheel range adjustment. Content
text follows the full text-size range; only fixed-height chrome is capped, the
iOS bars and tab bar at 1.3 times and native pickers at the third accessibility
size. Native permissions remain system-owned; Posato never grants its own
authorization.

Honor Reduce Motion and keep motion functional: no ticking animation, urgency,
auto-dismissal of decision-critical information, or distracting decorative loops.
Do not claim complete accessibility from default framework semantics.

WCAG 2.1 AA is the contrast reference. Historical brand-board contrast numbers
do not prove this different semantic palette, disabled-alpha states, or native
controls. Full high-contrast, large-text, assistive-technology, and reduced-motion
coverage remains a verification obligation, not a label of production readiness.

## Reproduction and Evidence

1. Start in the real shared design-system package and use the current theme.
2. Preserve the frozen prototype's visual role/token mapping; adapt state and
   events to existing ViewModels rather than importing its reducer.
3. Keep real failure, authorization, timer, and device-scope meanings.
4. Verify the application through tools/posato-control and its maintained
   verify-posato feature map. Capture matching screens, keyboard states,
   long lists, menus, and session transitions.
5. Keep runtime screenshots, identifiers, logs, and database evidence ignored
   under build/verification; do not commit them as documentation assets.
6. Record verified limits and durable changes in the relevant wiki authority
   routing without promoting an untested inference to a product decision.

### Provenance and limits

- `user-confirmed`: the accepted native prototype is the visual reference, its
  product design system is adopted in one PR, and existing ViewModels remain.
- `observed`: product source contains the semantic palette, reusable components,
  platform-specific navigation, real-data screens, and native window adaptation.
- `superseded`: minimal shell-only copy, platform-color-only styling, manual
  group-name UI, and top-level Session / Paused items segmented navigation.
- `open`: complete physical assistive-technology coverage, extreme text-size
  reflow, full-state measured contrast, final platform icon assets, and future
  synchronization/enforcement surfaces.

### Native prototype reference

[The prototype design record](prototypes/mvp-interaction-flow/DESIGN.md) remains
frozen at the adoption reference. It records the mock flows and design evidence,
including the workbench/overlay; those are not product capabilities.
The root DESIGN.md is the continuing MVP authority. Further product design work
updates this file and the shared components, not both runtimes in parallel.
