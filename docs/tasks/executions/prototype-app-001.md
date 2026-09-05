# Execution: PROTOTYPE-APP-001

- **Brief:** [PROTOTYPE-APP-001](../specifications/prototype-app-001.md)
- **Status:** done
- **Review tier:** Standard
- **Implementer:** Codex
- **Reviewer:** Independent Codex reviewer, separate read-only review context.
- **Branch:** `design/mvp-interaction-polish`
- **Updated:** 2026-09-05

## Plan

1. Port the deterministic model and regression cases into an isolated module.
2. Implement shared design-system screens, draft ownership, and hidden controls.
3. Add thin macOS and iOS hosts and build verification.
4. Drive both native targets, review the completed change, then retire HTML and update documentation.

## Result

- Added isolated `:prototypeApp` and a thin iPhone SwiftUI host using the existing
  prototype design system; production code and services remain untouched.
- Ported all 16 surfaces, strict transitions, configurable items and duration,
  four walkthroughs, Free play, mock events, and deterministic previews.
- Added hidden controls, platform-appropriate presentation, appearance options,
  and remembered drafts across dismissal and compact/expanded layout changes.
- Retired HTML and its Node suite after common regression and native parity
  checks; both remain recoverable at `566bdb6`. Added native run instructions.
- Reworked the 50-website study with primary session actions above two counted
  summaries, read-only selection details, separate website/app tabs, search,
  lazy rows, and labelled options menus. Browser state belongs to composition
  above editor navigation; no production model, service, or dependency changed.

## Verification

- `:prototypeApp:verifyPrototype` and root `quality` passed after implementation:
  formatting, Detekt, 24 common test methods, both iOS frameworks, unsigned
  Simulator host, and bundled desktop application.
- Native macOS AX checks completed all four strict walkthroughs, including
  intentionally blocked steps, recovery, local sync failure/retry, and expiry.
  Checked initial and repeated window shortcuts, form submission, website
  save/remove, session start, and draft retention across controls and resizing.
- The repository iOS driver, with the prototype bundle override, passed the
  43-step configuration/session flow and a 15-step overlay, appearance, and
  key-wait flow on an iPhone Simulator. A further 12-step split check confirmed
  keyboard-safe editor layout, draft retention across the sheet, and cancellation.
  Existing lower-level drivers were reused;
  no production verification API or hidden model mutation hook was added.
- Inspected native screenshots and accessibility trees; captures and run
  identifiers remain under ignored `build/verification/`.
- Added the maintainer-requested 50-website/four-application fixture and four
  whole-app previews, then nine browser cases at compact and expanded sizes.
  After the requested redesign, focused gates and root `quality` passed with
  27 common test methods. Native checks exercised filter/clear/no-match states,
  website menu edit/save/remove, tab retention across the application picker,
  read-only details, review/start/active-session actions, compact IME layout,
  dark appearance, larger text, and Mac breakpoint changes.
- Integrated current `main` without dropping either side's build targets or wiki
  entry; focused independent integration review and the aggregate gate passed.

## Completed-change review

- **Verdict:** no unresolved Critical or Required findings in completed source review.
- Corrected guided progress through normal duration submission and application
  selection; regression tests failed before the correction and passed afterward.
- Native checks corrected semantic dark text color and keyboard ownership:
  window-level shortcuts survive screen transitions, and the dialog takes focus
  and handles dismissal. The independent reviewer inspected the corrections.
- Long-list native checks caught and corrected an iOS text-field saved-state
  restoration crash, insufficient keyboard-visible result space, and list-key
  anchoring after clearing search. Independent review inspected state ownership,
  focus, component APIs, filtering tests, native replay evidence, and documentation.

## Blockers and accepted risks

- Prototype-only behavior does not establish production enforcement, synchronization, or accessibility conformance.
- Runtime checks cover a local Apple Silicon Mac and iPhone Simulator, not a
  physical iPhone or the full supported OS and assistive-technology matrix.
- The redesigned long-list presentation remains a prototype hypothesis for
  human usability and full Apple assistive-technology testing, not production
  design authority or proof of universally optimal UX.
