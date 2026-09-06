# MVP Interaction Prototype

## Source identity

- **Original evidence revision:**
  `a081d4278cf8517c46b4322ed3c098462f153570`
- **Current artifact:** [native interaction prototype](../../../prototypes/mvp-interaction-flow/README.md)
- **Design-system library:** `prototypes/mvp-interaction-flow/compose/`
- **Historical HTML and Node suite:** non-release tag
  `archive/mvp-interaction-flow-html`, pinned to `566bdb6`
- **Source type:** disposable interactive UX prototype
- **Reviewed:** 2026-09-06
- **Authority:** evidence only; not a product, design, architecture, or
  implementation authority

## Why this source exists

The prototype makes the accepted low-fidelity MVP flows inspectable as one
interactive UX study, now running in two isolated native hosts. It is useful for finding missing states,
contradictory actions, large-window layout problems, and unclear local-versus-
shared ownership before production UI work starts.

The accepted [design authority](../../../DESIGN.md),
[MVP scope](../../product/mvp-scope.md), and
[architecture baseline](../../decisions/0003-mvp-application-architecture-baseline.md)
remain authoritative. Future task plans may cite this source as usability
evidence, but they must not import its reducer, geometry, fixtures, or web
implementation as a product contract.

`DESIGN.md` now also contains a source-backed description of the current native
prototype, explicitly labelled as a prototype reference. Recording its concrete
appearance and behavior does not promote its mock services or implementation
parameters into production requirements.

## Maintainer-confirmed evidence

- `user-confirmed`: Free play is an inspection workbench. It may prepare
  deterministic prerequisite state so every listed prototype action can be
  exercised independently.
- `user-confirmed`: the prototype must demonstrate configurable exact website
  entries and synthetic application selection rather than imply a fixed
  one-site, one-application limit.
- `user-confirmed`: macOS needs a large-window presentation that keeps sparse
  task content and its primary action visually related.
- `user-confirmed`: the prototype is an MVP UX artifact, not production code.
- `user-confirmed` (2026-09-06): update `DESIGN.md` to describe the current
  prototype accurately, including the refined controls, lists, duration entry,
  and platform-specific main navigation.
- `user-confirmed` (2026-09-06): preserve the retired HTML and Node suite under
  the non-release archive tag so squash merging or deleting the work branch
  cannot remove their only durable reference.

## Historical browser-observed evidence

At the pinned revision, browser checks directly exercised representative
strict product paths for:

- first-installation setup through ready state;
- session setup, review, start, blocked presentation, intentional early end,
  and normal expiry;
- action-required review for missing permission and missing local application
  mapping; and
- existing-workspace discovery with delayed synchronizable-Keychain delivery,
  waiting, retry, and no parallel-workspace action.

Additional recorded checks exercised all Free play actions from reset,
configurable website and synthetic application items, invalid and duplicate
input, preservation of the last valid value during a failed edit, independent
Mac and iPhone application mappings, keyboard submission, responsive overflow,
and automated browser accessibility scanning in the checked states.

These are `observed` prototype results, not production acceptance evidence.

## Historical visual and interaction refinement

- `user-confirmed`: refine this existing prototype in a dedicated worktree,
  with a distinctive, pleasant design and intuitive interactions appropriate
  to Posato. This does not accept new production navigation or geometry.
- `observed`: the refined preview opens at the ready state. A separate
  inspection panel retains the guided walkthroughs, state facts, and every
  independently exercisable Free play action. Moment shortcuts expose first
  visit, active session, recovery, and Keychain waiting without opening that
  panel.
- `hypothesis`: warm paper surfaces, moss accents, an asymmetric open-interval
  motif, restrained separators, and a compact session/items navigation make
  the status-led experience calmer and easier to scan. Explicit light/dark
  appearance controls make both treatments inspectable; the initial choice
  follows the browser's system preference.
- `observed`: the duration form supports 25-, 45-, and 60-minute presets and
  validated custom whole-minute durations from 5 minutes to 24 hours. Review,
  active state, and early-end cancellation use the same resolved end time,
  including the next-day case. These bounds and the fixed 17:45 demo clock
  remain prototype fixtures, not new product requirements.
- `observed`: external iCloud results, sync completion/failure, the paused
  message, and expiry remain explicitly simulated. A simulation dock is
  also available within the Mac full-screen preview.
- `observed`: the former dependency-free model regression checks covered duration
  boundaries, return navigation, active-session mapping repair, and the four
  guided scenarios. The 24 Node cases are retained at `566bdb6`; current checks
  run as common Kotlin tests through `:prototypeApp:jvmTest`.

The single HTML artifact had no build step, remote assets, persistence, or
network requests. It has been superseded by the native prototype; historical
browser evidence does not become native runtime evidence through migration.

## Compose component companion

- `user-confirmed`: after committing the HTML refinement, provide a complete
  reusable design system for the prototype as Compose functions.
- `observed`: the isolated `:prototypeDesignSystem` module provides Material 3
  theme tokens, identity, controls, fields, lists, notices, session patterns,
  adaptive navigation, and separately packaged prototype inspection chrome.
  The [component map](../../../prototypes/mvp-interaction-flow/compose/README.md)
  connects each reusable HTML family to a composable or slot-based composition.
- `observed`: the common-code catalog has six sections and two preview sizes
  sharing eighteen deterministic appearance/section cases. JVM, iOS device,
  iOS simulator, and preview-only Android source sets compile using the
  repository's pinned toolchain and quality gates.
- `observed`: the native desktop catalog supports editable text, synthetic
  callback feedback, light/dark and stronger-contrast variants, and enlarged
  text. Manual checks exercised compact and expanded layouts and preservation
  of the field value and error state when crossing the layout breakpoint.
- `inferred`: keeping this companion outside production modules permits
  inspection and reuse without treating the HTML study as accepted production
  geometry. No production application depends on the new module.

This companion remains a presentation library, not a native port of the HTML reducer
or an implementation of blocking, storage, permissions, or iCloud behavior.
Compilation and desktop catalog checks do not establish iOS runtime behavior,
native Apple accessibility conformance, or production design acceptance.

## Native interaction prototype

- `user-confirmed`: migrate the study to Compose Multiplatform, with separate
  iPhone and macOS runtime targets, synthetic data, shared design-system screens,
  a toggled inspection overlay, and retirement of HTML after native parity checks.
- `observed`: `:prototypeApp` contains all 16 surfaces, a strict immutable reducer,
  a ViewModel, four guided scenarios, independently exercisable Free play actions,
  and deterministic compact/expanded previews. It depends on the prototype design
  system, not production modules. The SwiftUI host only embeds the Compose controller.
- `observed`: each installation owns independent memory-only state. Restart
  returns to ready fixtures; the fixed 17:45 clock and external outcomes remain
  manually controlled. No production permission, persistence, synchronization,
  enforcement, or helper boundary is invoked.
- `observed`: native checks exercised website entry, rejected edits, multiple
  synthetic applications, custom duration review, session start, early-end
  cancellation and confirmation, and the hidden controls. The macOS editor
  retained its draft across overlay dismissal and the layout breakpoint. iPhone
  Simulator checks exercised dark appearance, stronger contrast, larger text,
  and workspace-key waiting in the native bottom sheet and full-screen content.
- `observed`: common test methods include table-driven boundary cases and
  both platform variants, replacing the old 24-case Node suite. Regression
  corrections cover guided progress through normal duration and application forms;
  further coverage includes website filtering without changing source order,
  batch additions, draft acknowledgment, and every valid whole-minute duration.
- `inferred`: native rendering now provides a more useful inspection surface
  for later Compose work, without promoting the study's geometry or reducer into
  accepted product code.

### Long-list usability probe

- `user-confirmed`: add many synthetic website rows and evaluate whether Session
  and Paused items remain useful with a long selection.
- `observed`: the selectable long-list moment contains 50 websites and four
  local applications without changing the default ready fixture.
- `superseded`: the first native layout put the session action after the entire
  selection and application management after all website rows, several viewports
  below the beginning. Desktop scrolling reached both actions. On iPhone Simulator,
  the session action was reachable by scrolling;
  the Paused items probe exhausted the driver's ten-swipe budget around website
  30, before reaching application management. This is a discoverability and
  navigation-cost result: a continued native run reached the chooser and verified
  opening and cancelling the application picker without changing the selection.
- `user-confirmed`: implement the short session summary, separate website/app
  sections, and website search in the prototype, prioritizing usable long lists.
- `observed`: Session and its review now place the primary action above two
  counted disclosure rows. Full selection details are read-only. Paused items
  keeps category tabs, search, and section actions above independently scrolling
  lazy lists. Native checks found the last website by search, opened/cancelled
  both editors without losing the query or tab, saved and removed a website
  through its menu, and inspected the selection during an active session.
- `observed`: compact keyboard layout leaves complete search results visible on
  the tested iPhone Simulator, including the larger-text treatment. The Mac
  preserves filtering when resizing across the compact/expanded breakpoint;
  clearing a query returns results to the beginning instead of following the
  previously matched row's key into the full list.
- `observed`: restoring a searched text field through a saved subtree crashed
  the tested iOS prototype in `TextUndoManager.Saver.restore`. Composition-owned
  browser state above editor navigation removed that path; the exact native
  search/edit/cancel replay passed. This is bounded prototype runtime evidence,
  not a general claim about other Compose versions or platforms.
- `open`: validate the revised presentation with people and production Apple
  accessibility technologies before adopting its exact geometry. Native probes
  establish reachable controls and state retention, not user preference or a
  universally optimal layout.

### Entry-first interaction correction

- `user-confirmed`: entering a custom duration should avoid a bare numeric field;
  add up/down controls to the proposed hour/minute wheels. Adding websites should
  be more direct than searching, including repeated or multi-website entry.
- `observed`: the prototype now offers bounded hour/minute wheels, one-unit
  arrows, desktop keyboard steps, and an immediately resolved end time. Website
  entry is inline; comma/newline batches reuse exact-host validation, skip
  duplicates, and retain invalid entries. Search is secondary in management but
  remains directly available in read-only selection details.
- `inferred`: these controls reduce keyboard switching and repeated navigation
  during setup. They do not establish human preference or change production
  timing, validation, or design authority.
- `user-confirmed`: separate entry guidance from list metadata, emphasize the
  app chooser, keep inactive category tabs visibly selectable and aligned with
  screen content, and make row menus consistent with the prototype palette.
- `superseded`: counted underline tabs improved inactive-state visibility, but
  the maintainer rejected their visual fit as too close to default Material styling.
- `user-confirmed`: keep inactive segments visibly selectable; labels may have
  horizontal padding when the control's outer edge aligns with screen content.
- `observed`: the replacement uses a shared mist surface, an outlined paper
  selection, and centered padded labels with optional quiet inline counts. Metadata/actions
  stay centered, the app chooser is primary, and row menus are icon-led. The sync
  footer adds no second separator. These remain study-level presentation choices.
- `superseded`: the maintainer first accepted the same segmented component for
  application and category navigation, including two controls on the compact
  management screen. The later platform-navigation direction replaces that placement.
- `user-confirmed`: move Session/Paused items to bottom navigation on iPhone and
  an icon-and-label sidebar on Mac, retaining Websites/Apps inside the destination.
- `observed`: the separate destination components preserve the prototype palette,
  selected/disabled semantics, and active-session editing restrictions. Main
  navigation appears after onboarding; the management destination no longer has
  Done. iPhone keyboard entry hides the header and bottom navigation; dismissal
  restores them without discarding the website draft or category selection.
- `observed`: a very narrow Mac window with enlarged text squeezed a category
  count onto two lines. The prototype host now reserves a phone-width content
  area beside the sidebar; native resizing honors this minimum and the enlarged
  labels remain readable. This is prototype geometry, not a production window contract.
- `user-confirmed`: integrate the Mac title area with the app background and
  use larger rounded corners while retaining native window controls.
- `observed`: the prototype JVM host uses AWT full-content properties and a
  narrow AppKit/JNI bridge for a unified toolbar and 20 pt frame-layer clipping.
  This is an explicit prototype radius, not the OS-selected default. Fullscreen
  disables clipping; the iPhone shell and production modules are unchanged.
  The native frame hierarchy is an OS/runtime integration assumption that needs
  fresh verification before production use or a runtime upgrade.

The [run instructions](../../../prototypes/mvp-interaction-flow/README.md) cover
desktop packaging, Xcode launch, controls, and verification. The original HTML
and Node suite remain recoverable in Git; they are no longer maintained runtime
targets. Review and verification details belong to the task execution record,
not to this source's authority.

## Evidence limits

- Historical browser checks do not prove native behavior. Current native checks
  cover only the mock macOS app and iPhone Simulator, not platform authorization, enforcement, CloudKit,
  Keychain, helper IPC, extension lifecycle, accessibility technology support,
  performance, security, or physical-device behavior.
- Synthetic application names, sample domains, validation and normalization
  rules, timing, layout geometry, copy variants, and deterministic workbench
  prerequisites are fixtures or hypotheses unless an accepted authority says
  otherwise.
- Free play shortcuts do not relax the strict product state model.
- Automated accessibility scans do not replace keyboard, VoiceOver, Voice
  Control, Switch Control, Dynamic Type, Full Keyboard Access, contrast, and
  reduced-motion verification in the production applications.
- The source must not be copied wholesale into product code.

## Planning use

The accepted [MVP roadmap](../../tasks/mvp-roadmap.md) routes this evidence to
the smallest UI tasks that consume it: exact-domain and semantic-application
target management, device-local mapping, shared manual-session behavior, local
session integration, and first- and second-installation onboarding. Each task
must still plan and obtain fresh Compose, simulator, runtime, accessibility,
and physical-device evidence where applicable.

## Related synthesis

- [Brand and product design baseline](../topics/brand-and-design-baseline.md)
- [MVP open questions](../topics/mvp-open-questions.md)
