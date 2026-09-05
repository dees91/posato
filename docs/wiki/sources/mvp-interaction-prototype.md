# MVP Interaction Prototype

## Source identity

- **Original evidence revision:**
  `a081d4278cf8517c46b4322ed3c098462f153570`
- **Artifact:** `prototypes/mvp-interaction-flow/index.html`
- **Compose companion:** `prototypes/mvp-interaction-flow/compose/`
- **Source type:** disposable interactive UX prototype
- **Reviewed:** 2026-09-05
- **Authority:** evidence only; not a product, design, architecture, or
  implementation authority

## Why this source exists

The prototype makes the accepted low-fidelity MVP flows inspectable as one
interactive browser artifact. It is useful for finding missing states,
contradictory actions, large-window layout problems, and unclear local-versus-
shared ownership before production UI work starts.

The accepted [design authority](../../../DESIGN.md),
[MVP scope](../../product/mvp-scope.md), and
[architecture baseline](../../decisions/0003-mvp-application-architecture-baseline.md)
remain authoritative. Future task plans may cite this source as usability
evidence, but they must not import its reducer, geometry, fixtures, or web
implementation as a product contract.

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

## Browser-observed evidence

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

## Visual and interaction refinement

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
- `observed`: the dependency-free model regression checks cover duration
  boundaries, return navigation, active-session mapping repair, and the four
  guided scenarios. Run them with
  `node --test prototypes/mvp-interaction-flow/interaction-flow.test.cjs`.

The single HTML artifact still opens directly in a browser. It adds no build
step, remote assets, persistence, network requests, or native application
implementation. Browser evidence remains subject to the limits below.

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

This companion is a presentation library, not a native port of the HTML reducer
or an implementation of blocking, storage, permissions, or iCloud behavior.
Compilation and desktop catalog checks do not establish iOS runtime behavior,
native Apple accessibility conformance, or production design acceptance.

## Evidence limits

- The artifact runs in a browser and does not prove Compose behavior, native
  Apple component behavior, platform authorization, enforcement, CloudKit,
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
