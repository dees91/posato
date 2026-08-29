---
schema: design-md/v1
name: "Posato"
sources:
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
  spacing: unknown
  components: medium
  responsive: low
  interaction: medium
  accessibility: medium
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
  typography:
    application:
      family: "San Francisco through platform semantic text styles"
      size: "Semantic; 17pt default on iOS and 13pt default on macOS"
      weight: "Regular 400, Medium 500, Semibold 600"
      lineHeight: "Platform semantic"
    early-web-and-docs:
      family: "System sans-serif stack"
      size: "Context dependent"
      weight: "Regular 400, Medium 500, Semibold 600"
      lineHeight: "Context dependent"
  spacing: {}
  radii: {}
  shadows: {}
---

# Posato Design System

## Design Intent

This is the accepted minimum brand and product design authority for the Apple
MVP. It was accepted by the maintainer on 2026-08-25 after review of the Gate 3
candidate and its rendered brand board. It is deliberately smaller than a
complete identity or component system.

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

The brand palette expresses warm restraint. It is not a replacement for Apple
semantic application colors.

| Token | Value | Role |
| --- | --- | --- |
| Brand Ink | `#18231F` | Primary brand text and dark neutral |
| Brand Paper | `#F5F2EA` | Warm light brand surface |
| Brand Moss | `#2E5D50` | Primary brand color and application tint |
| Brand Clay | `#A54B35` | Sparse secondary emphasis, never a generic error color |
| Brand Mist | `#DCE4DF` | Quiet supporting surface or illustration field |

Application backgrounds, labels, separators, warnings, and destructive states
use platform semantic colors. Brand Moss is the default application tint for
primary actions and selected state. Brand Clay may appear in brand material or
sparse non-status emphasis; it must not compete with system warning or
destructive semantics.

Dark appearance references are `#121A17` for the deep canvas, `#F7F5EF` for
text, `#76B29E` for Moss, and `#E49A82` for Clay. They are starting references,
not proof of final platform asset variants. Dark Mode and Increase Contrast
assets require component-level verification.

### Typography

Applications use San Francisco through platform semantic text styles. Use
Regular, Medium, and Semibold weights; avoid thin display weights. Hierarchy
comes from semantic size, weight, spacing, and content priority rather than a
second typeface.

Support the full iOS Dynamic Type range. On macOS, use the system control and
text styles appropriate to each native component. Early web and repository
material uses a system sans-serif stack. A custom brand typeface is deferred
and requires a separate licensing decision.

### Spacing, shape, borders, and elevation

No product spacing scale, radius scale, border system, or shadow token is
accepted yet. Use native platform spacing and component geometry until repeated
product screens establish a stable need. Do not copy web brand-board pixel
values into Apple application points.

Favor stable alignment and deliberate negative space. Avoid excessive rounding,
heavy shadows, gradients, decorative panels, and stock card grids. Product
hierarchy should remain legible without elevation effects.

### Application implementation foundation

TARGETS-001, the first reviewed product screen, establishes the reusable
application foundation: one root `PosatoTheme` backed only by Material 3 and
adapted from the existing platform semantic colors and text styles. Feature
screens consume that theme instead of creating their own theme boundary.

This foundation does not create a generic wrapper for every Material 3
component. Promote a feature component or add spacing, shape, border, or
elevation tokens only after repeated screen evidence establishes a shared
contract.

### Iconography and imagery

Use SF Symbols for familiar interface actions and give essential symbols text
or accessible labels. Keep symbol weight and scale consistent with surrounding
semantic text styles.

The placeholder product mark is an **open interval**: two softly weighted,
asymmetric vertical forms separated by deliberate negative space, with a subtle
forward shift that suggests pause followed by continued choice. It may echo a
pause mark without copying a media-control glyph. It must work in one color,
contain no text, and remain recognizable at small sizes.

Avoid shields, padlocks, stop signs, warning tape, flames, dopamine imagery,
achievement rings, screenshots, and generic productivity illustrations. Final
logo geometry, rendering layers, and platform icon assets are unknown.

## Components

The following are product contracts, not implemented components. Their exact
geometry and framework ownership remain open.

### Application shell

The shell establishes one status-led content region and a platform-native place
for secondary navigation or settings. It does not introduce a dashboard grid.
The PR #1 shell contains only:

- product name: **Posato**;
- primary line: **Pause. Then choose.**;
- supporting line: **A quiet pause between impulse and action.**; and
- build-state note: **This build contains the application shell only.**

It has no fake controls, unfinished navigation, illustration, gradient, or
placeholder icon pretending to be final.

### Status summary

The status summary contains the current session state, end time or
action-required reason, device scope when relevant, and one primary action.
Supported semantic states include inactive, active, waiting, pending,
retryable, and action required. State never relies on color alone.

### Paused-item list and row

The list separates exact shared domains from application policies whose native
selection is local to each device. A row identifies type, current local mapping,
and whether action is required. Editing preserves the previous valid value
until replacement input validates.

The visible name for the singleton semantic application policy is
**Application group**. Its name may synchronize, but application choices remain
local to each device. Until a native selection producer exists, the shared UI
states **Apps still need to be chosen on this device.** and does not present a
fake, disabled, or speculative selection action.

### Session setup and review

Session setup collects duration or end time. Review shows the resolved end time,
effective local items, and any missing permission or mapping before the start
action. It does not use urgency, scoring, or commitment theater.

### Active-session surface

The active surface leads with **Session active until [time]**, shows effective
local items and truthful local sync state, and offers a clearly labeled route
to **End session early**. Early termination uses a clear confirmation, not a
hidden, tiny, timed, or gesture-only control.

### Synchronization status and action

Synchronization distinguishes local-only, pending local work, syncing, last
completed local attempt, retryable failure, waiting for the iCloud Keychain
workspace key, and action-required failure. **Sync now** is a manual action.
Completion never claims that every other device received the change.

### Action-required notice

The notice names the bounded problem, preserves the last valid state, and gives
one exact repair or retry action. Permission loss, invalid local selection,
helper or proxy failure, network or quota failure, and delayed key delivery are
different states rather than one generic error.

### Blocked presentation

The platform-owned blocked presentation says that the selected site or app is
paused, gives the session end time when available, and provides a route to
Posato. It does not display or retain browsing history, full attempted URLs, or
application-usage timelines.

## Layout and Responsive Behavior

### Shared structure

macOS and iOS share hierarchy, state names, product vocabulary, and policy
meaning. They do not share forced-identical chrome or navigation. The primary
status and action remain easy to find as the surrounding platform layout
changes.

### iOS

Keep the primary action comfortably reachable, limit simultaneous controls,
and use standard navigation, sheets, dialogs, and system permission prompts.
Layouts reflow for the full Dynamic Type range instead of truncating the end
time, action-required reason, or primary action.

### macOS

Use a resizable system window, a denser but still calm information layout,
menu-bar commands, standard keyboard shortcuts, and keyboard-complete flows.
Do not stretch a mobile screen to desktop width or replace standard window
behavior with custom chrome.

### Adaptation limits

No exact application width, grid, breakpoint, sidebar behavior, minimum window
size, orientation rule, or overflow strategy is evidenced yet. Define these in
the first reviewed screen slice that needs them and update this document from
rendered evidence.

## Interaction and Motion

### Core flows

- Onboarding explains purpose and privacy, invokes **Sync with iCloud**, waits
  for an existing workspace key instead of creating a parallel workspace,
  requests platform permissions in context, then collects shared domains and
  device-local app mappings.
- Paused-item management validates locally, displays the local result, and
  marks encrypted work pending for synchronization.
- Manual session setup chooses duration or end time, reviews effective local
  items, resolves action-required state, and starts the session.
- Active blocking presents **paused until**, routes to Posato, expires normally,
  or follows the explicit early-end confirmation and synchronized stop path.
- Synchronization commits local state and pending work atomically, reports only
  the local attempt result, retains valid state across retryable failure, and
  distinguishes delayed key delivery from other errors.
- Recovery identifies the bounded failure category, offers a precise action,
  verifies the repaired state, and returns to truthful status.

The maintained low-fidelity diagrams live in
`docs/wiki/topics/brand-and-design-baseline.md`.

### Feedback and language

Prefer **No session active**, **Session active until [time]**, **This site is
paused until [time]**, **End session early**, **Last sync completed on this
device at [time]**, **waiting**, and **needs your attention**.

Avoid **locked in**, **access denied** in ordinary product copy, **give up**,
**break focus**, **everything is synced**, **detox**, **addiction**,
**discipline**, **streak**, and language that describes a person as a failure.

### Motion

Motion is functional and restrained. Honor Reduce Motion. Do not animate every
countdown tick, create urgency through motion, auto-dismiss decision-critical
information, or announce every background retry. Exact transition durations,
easing, and component animations are unknown until an implemented interaction
is reviewed.

## Accessibility

- Treat WCAG 2.1 AA contrast as a minimum reference and test all real states in
  light, dark, and increased-contrast appearances.
- Pair color with text and, where useful, a distinct symbol for active, paused,
  pending, success, warning, and error state.
- Support VoiceOver, Voice Control, Switch Control, Full Keyboard Access, and a
  logical focus order for every primary flow.
- Target 44 by 44 point controls on iOS and 28 by 28 point controls on macOS;
  never go below Apple's current platform minimums.
- Preserve essential content at accessibility text sizes. Reflow rather than
  truncating end time, action-required reason, or primary action.
- Present permission rationale before the system prompt. Do not imitate System
  Settings or imply that Posato granted a permission.
- Announce meaningful state changes, not countdown seconds or routine retries.
- Keep early termination deliberate through copy and confirmation, never by
  reducing accessibility.

Declared accessibility intent is not implementation evidence. Every component
and flow still requires keyboard, assistive-technology, text-scaling, contrast,
and reduced-motion verification on supported physical platforms.

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
- `Observed`: `docs/wiki/sources/apple-design-guidance.md` records the current
  authoritative platform guidance used by the baseline.
- `Observed`: no production application UI or final brand asset exists in the
  repository at acceptance time.

### Derived

- `Derived`: WCAG 2.x sRGB contrast is 14.45:1 for Ink on Paper, 6.71:1 for
  Moss on Paper, 7.51:1 for white on Moss, 5.13:1 for Clay on Paper, and
  5.74:1 for white on Clay.
- `Derived`: the accepted information questions reduce to one status-led
  hierarchy rather than an equal-weight dashboard because state, end or repair
  condition, local scope, sync state, and action have an explicit priority.

### Inferred

- `Inferred`: the open-interval mark best expresses the accepted promise when
  it reads as space deliberately opened rather than a barrier imposed.
- `Inferred`: status summary, paused-item row, session review, synchronization
  status, action-required notice, and blocked presentation are the smallest
  reusable component contracts implied by the accepted flows.
- `Inferred`: platform semantic surfaces with Moss as the sole control tint
  preserve Apple familiarity while allowing the brand to remain recognizable.

### Unknown

- `Unknown`: final logo geometry, icon layers, wordmark treatment, custom
  typeface, illustration system, and marketing design.
- `Unknown`: exact application spacing, radii, borders, shadows, component
  dimensions, navigation model, breakpoints, and minimum macOS window size.
- `Unknown`: final Dark Mode and Increase Contrast asset values beyond the
  accepted reference colors.
- `Unknown`: implemented semantics, focus order, assistive-technology output,
  text reflow, localization expansion, and motion behavior.
- `Unknown`: final system-blocked presentation within the limits of each
  enforcement mechanism.

No material source contradiction remains. The rendered brand direction uses
Paper and Mist as expressive brand surfaces, while the application contract
uses platform semantic surfaces; these are separate contexts. Moss is the sole
default control tint, while Clay remains non-status secondary brand emphasis.

## Reproduction Guidelines

1. Start with the current platform's native window, navigation, typography,
   semantic surfaces, labels, controls, dialogs, and accessibility behavior.
2. Apply Brand Moss as the single default control tint. Use the remaining brand
   palette for identity and sparse content emphasis, not as replacements for
   semantic system states.
3. Build every primary surface around current status, end or repair condition,
   local effective scope, truthful local sync state, and one primary action.
4. Preserve product vocabulary exactly until a reviewed copy decision updates
   it. Never introduce scoring, shame, surveillance, or cloud-delivery claims.
5. Keep macOS and iOS semantically aligned but structurally native. Do not
   translate Apple point values into web pixels or force shared chrome.
6. Implement accessible semantics, focus, text scaling, contrast variants,
   keyboard behavior, and reduced motion with each component rather than as a
   later polish pass.
7. Add an exact token only when declaration, implementation, or repeated
   rendered evidence proves it. Record platform-specific exceptions instead of
   averaging them into a false cross-platform value.
8. For PR #1, render only the accepted four shell text elements using system
   background, label colors, typography, and Moss tint. Do not add fake
   functionality or the unfinished icon.
9. After each reviewed UI slice, reconcile intended tokens, requested component
   values, and rendered evidence, then update confidence and Unknown claims.
