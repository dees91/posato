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

### Positioning

Posato helps self-directed adults interrupt automatic use of selected websites
and apps across their own Apple devices, without surveillance or a product
account. It is not parental control, employee monitoring, administrator security,
or a productivity-scoring system.

- Brand promise: **a quiet pause between impulse and action.**
- Product line: **Pause. Then choose.**
- Character: calm, respectful, candid, precise, composed.
- Avoid shame, urgency, streaks, scores, gamification, and claims of perfect prevention.

### Current implementation boundary

The actual app contains Session and Paused items, real local persistence,
native application-selection boundaries, the local session timer, one
explicit **Sync with iCloud** control on the Session screen, and, on macOS
only, a **This Mac** helper-setup section below it. Session-driven
enforcement is not connected to these screens. Never show the prototype's
demo clock, invented synchronization time, mock application names, onboarding
success, simulated permission outcome, or inspection overlay in the real app.

On a first install the app opens a six-step first-run flow before the two
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
| labelSmall | 10 / 16 | Semibold |

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
- Buttons stay flat during hover, focus, and press; state indication follows
  the rounded button shape without adding elevation.
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

`user-confirmed` (2026-09-14): a quiet **About Posato** button sits beside the
wordmark on iPhone and below **On this Mac** in the Mac sidebar after setup.
The Mac device label and About text align with the primary navigation labels;
a 4 dp gap keeps the device label and About action together.
Session and Paused items remain the two primary destinations. About Posato
opens a secondary content screen with the app's short purpose, installed
version, and a **Licenses** disclosure. **Back** returns to the preceding
primary destination. On iPhone, the bottom navigation gives way to this
secondary flow. On Mac, the sidebar stays available, with neither primary
destination selected.

The version comes from the running application's metadata, without a second
hand-maintained version string. An unpackaged or incomplete build reports
**Version unavailable** rather than inventing a version. The Android preview
target is not a product host and uses that unavailable case.

Licenses lists **License**, **Notice**, and **Third-party notices** using
existing disclosure rows. Each opens the complete scrollable document with
selectable text, a heading, and a persistent **Back to licenses** action.
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
| Main navigation | PosatoBottomNavigation, PosatoBottomNavigationItem, PosatoSidebarNavigationItem |
| Choices/navigation | PosatoTabBar, PosatoTab, PosatoNavigationItem, PosatoChoiceGroup, PosatoSetupStep, PosatoDeviceLabel |
| Actions/input | PosatoButton, PosatoTextField, PosatoFieldMessage, PosatoSearchField, PosatoNumberWheel |
| Items | PosatoItemList, PosatoItemRow, PosatoItemSymbol, PosatoBadge, PosatoDisclosureRow |
| Menus | PosatoItemMenu, PosatoItemMenuAction |
| Selection | PosatoToggleButton, PosatoChoiceTile, PosatoSelectionRow, PosatoDurationChoice |
| Status/content | PosatoNotice, PosatoStatusLabel, PosatoHero, PosatoEmptyState |
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

Text fields use the product outlined treatment, supporting/error copy, and
UI-owned TextFieldState. Website entry is multiline (up to two visible lines),
with inline Add and a URI keyboard/Go action. Return submits; Shift-Return adds
a line break on desktop. Add remains easy to repeat without dismissing focus.

### Nested tab bar

The Websites / Apps control is a soft primaryContainer tray, large radius,
4 padding, and 4 between segments. A selected segment uses surface,
a hairline outlineVariant border, medium radius, and primary text.
Inactive segments retain normal onSurface text, not low-contrast disabled styling.
Labels/counts are centered vertically and horizontally; counts are muted.
The tray's outer edge aligns with the surrounding content.

Use this component for peer choices within a destination. Session / Paused items
uses the platform-specific primary navigation, not a second nested tab bar.

### Rows, menus, and notices

Rows align a 36 symbol surface, readable title/supporting text, and trailing
action. Separators belong to rows; do not stack another separator immediately
after a final row. Disclosure rows expose a clear chevron and a button role.

The ellipsis is a 44 circular control with a 24 icon and a quiet background.
The open menu is at least 200 wide, uses surface, large radius, and a subtle
outline. Edit and Remove are full menu actions; Remove uses the error color.
Both expose actionable semantics without changing the accepted visual treatment.

Notices use Neutral, Positive, Caution, or Critical tone. State must be named
in text and offer a precise available action. Loading, denied/restricted access,
corruption, retryable failure, and saved-but-not-enabled choices stay distinct.

## Layout and Responsive Behavior

### iOS

The app fills the real device viewport, without a fake phone frame.
Respect safe drawing and keyboard insets. The wordmark sits above content;
Session / Paused items lives in the bottom navigation. A visible software
keyboard temporarily hides the wordmark and bottom navigation to make room
for entry; Done clears focus and restores them.
The root applies `windowInsetsPadding(WindowInsets.safeDrawing)` once; these
insets already include the keyboard. Do not append a second IME padding modifier.

Content uses 24 horizontal padding in compact layout. Session uses the same
vertical inset inside its scrollable canvas; Paused items uses 12 vertically.
Lists consume the remaining height
and scroll independently of entry, category tabs, and toolbar. Selected-item
details use a modal bottom sheet with keyboard insets and Close list.

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
badge sits beside it, unlinked at reduced opacity with a "Coming soon" caption,
until the listing is live. Apple badge guidelines are not reviewed for now. The
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
  **Not now** keeps an empty setup possible.
- Summary leads with saved choices, device access, and local/iCloud scope.
  Its saved-website count follows policy changes while the step is visible;
  a failed read keeps the last valid count. Website-entry focus responds only
  to the person's successful submission, never to a remote count change.
  When applicable, a notice names the remaining setup and its existing route.
  **Go to Session** opens Session; it does not start a pause.
- Compact pages keep actions beneath independently scrolling content, with a
  full-width primary button. The iPhone wordmark hides while typing. Expanded
  pages keep actions adjacent to content in a scrolling column capped at
  600 dp inside the existing 820 dp outer canvas. Use the existing typography,
  spacing, colors, safe-area handling, and native permission presentation.

### Session

- Inactive: NO SESSION ACTIVE, Room for what matters., one primary start action.
  Without effective items, Choose paused items routes to the editor.
- Setup: YOUR NEXT PAUSE, How much space do you need?, presets 25 / 45 / 60,
  then Hours / Minutes wheels with explicit Increase / Decrease buttons.
- Bounds: five minutes through 24 hours. Zero hours restricts minutes to 5–59;
  24 hours restricts minutes to zero. Changing hours clamps the total safely.
- Wheels snap, support arrow keys and Home/End on desktop, and expose a range
  and adjustable value to accessibility. Controls grow with wheel text.
- Review: ONE LAST LOOK, resolved end time, real selection/authorization state,
  Start this pause and Change duration. A missing effective selection or mapping
  load failure cannot be presented as ready.
- Active: SESSION ACTIVE, timer end and remaining duration, End session early,
  compact item summary. Copy explicitly describes a local timer.
- Early end: Ready to return?, End session, Keep this pause.
- Ended/expired: inactive state plus the real early-end or expiration message.
- Summary: two disclosure rows, not every website/app. Selected items opens a
  read-only browser with category tabs, website search, and Close list.
  iOS shows opaque application counts; Mac shows actual local names.
- Device setup uses collapsed **iCloud** and **This Mac** rows with short,
  real-state summaries. This Mac is macOS-only. Expanding a row reveals its
  explanation and controls; it never starts a helper check or sync attempt.
  A pending fresh join shows **Check again** inside iCloud options.
  Sync now and Remove workspace live inside iCloud options, with the existing
  removal confirmation. When unlinked, the expanded iCloud options carry the
  full linking sentence as a caption next to the action. Setup controls are
  secondary to the Session action.
- This Mac (macOS only, after iCloud): reads nothing before a press on
  **Check Mac setup** inside its expanded options. This runs one status read
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
    - proxy settings are restored and the administrator rule is removed;
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
- `user-confirmed` (2026-09-11): when a helper read has returned a state other
  than ready and no helper call is running, Session names that state once as a
  notice beside the enforcement notice, above the session action. Expanding
  This Mac moves the sentence into the row, the notice stays silent because
  the row already announces, and it never blocks starting a session. Before
  the first explicit read there is no state to name, so no notice appears, and
  an active session that is enforcing keeps its own notice rather than showing
  an older helper read beside it.

Main navigation stays available during active sessions, and Paused items editing
retains its existing availability. Do not add an unrelated active-session lock.

### Paused items: websites

- Add websites is the primary input. Search is a quiet secondary action below it.
- Commas/newlines separate entries; domain names and HTTP(S) URLs are accepted.
  Only canonical exact hosts reach policy storage; paths/query/fragment do not.
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
cues. Preserve system font scale and expose wheel range adjustment. Native
permissions remain system-owned; Posato never grants its own authorization.

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
