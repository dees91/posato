# Execution: PROTOTYPE-APP-001

- **Brief:** [PROTOTYPE-APP-001](../specifications/prototype-app-001.md)
- **Status:** done
- **Review tier:** Standard
- **Implementer:** Codex
- **Reviewer:** Independent Codex reviewer, separate read-only review context.
- **Branch:** `design/mvp-interaction-polish`
- **Updated:** 2026-09-06

## Plan

1. Port the deterministic model, shared screens, drafts, and hidden controls into isolated native hosts.
2. Drive both targets, review the change, retire HTML, and document the evidence.
3. Refine entry, lists, and platform navigation from maintainer feedback; reconcile the design reference.

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
- Replaced custom-time text entry with bounded hours/minutes wheels and step
  arrows. Prioritized inline website additions over search, with batch
  canonicalization, duplicate skipping, partial recovery, and retained drafts.
- Aligned the segmented control's outer edge with screen content, strengthened inactive-tab
  contrast and app-chooser priority, separated entry guidance from list metadata,
  and unified item menus. Replaced rejected underline tabs with a shared mist/paper
  segmented category control, then moved main destinations to an iPhone bottom bar
  and Mac sidebar. Removed Done and the duplicate sync-footer separator; retained
  active-session restrictions and a readable minimum Mac content width.
- Reconciled `DESIGN.md` with the actual tokens, components, all 16 surfaces,
  native layouts, and interactions, separately from production intent and mocks.

## Verification

- Initial focused/root gates passed with 24 common tests, both iOS frameworks,
  an unsigned Simulator host, and the bundled desktop application.
- Native macOS AX checks completed all four strict walkthroughs, including
  intentionally blocked steps, recovery, local sync failure/retry, and expiry.
  Checked initial and repeated window shortcuts, form submission, website
  save/remove, session start, and draft retention across controls and resizing.
- The repository iOS driver, with the prototype bundle override, passed the
  43-step configuration/session flow and a 15-step overlay, appearance, and
  key-wait flow on an iPhone Simulator. A further 12-step split check confirmed
  keyboard-safe editor layout, draft retention across the sheet, and cancellation.
  Reused lower-level drivers without production API changes or hidden mutation hooks.
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
- Entry refinements passed focused gates and root `quality` with 37 common test
  methods. Coverage includes all valid duration values, batch order/deduplication,
  partial failures, guided progress, and one-shot draft acknowledgment.
- A repeated 37-step iPhone Simulator flow covered arrows, presets, the minimum
  duration, review, 15-site batch entry, subsequent additions, partial failure,
  and search/draft restoration. Separate native Mac checks proved actual
  multiline paste and three consecutive Return submissions without refocusing.
  Both targets exercised scroll/preset/scroll selection; Mac keyboard navigation
  reached 24 hours with the correct next-day end. Driver injection required
  releasing synthetic modifier state; Unicode newline injection is not a paste.
  Dark/larger-text iPhone checks kept the entry action and a result row visible
  with the keyboard open; the enlarged duration form remained scrollable to review.
- Visual refinements passed focused gates and root `quality` with 37 test methods.
  Native Mac checks covered menu edit/save, app chooser/cancel/remove, and
  compact/expanded light/dark layouts with larger text. A 34-step iPhone flow
  checked the 50-item summary, menus, chooser, search, and keyboard entry.
  Larger-text entry kept a complete result row visible above the keyboard;
  a 14-step chooser/menu replay passed after allowing the driver's scroll to
  settle before tapping Cancel. No application correction was needed for that retry.
  Platform navigation passed 32-step configuration/session and 18-step enlarged,
  dark/keyboard iPhone flows; keyboard-restoration checks wait for IME dismissal.
  Native Mac checks confirmed sidebar navigation, draft retention across destination
  changes/resizing, and minimum-width enforcement with enlarged text. The final
  host correction passed independent review. Final pre-push root `quality` passed:
  199 tasks, including the prototype gate and 37 common test methods.
- `DESIGN.md` schema validation and `git diff --check` passed at closeout.

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
- Entry review checked wheel/preset coordination and draft acknowledgment;
  alignment is observable state and each submission acknowledgment applies once.
  Field focus targets the input independently of its trailing Add action.
  Independent source review and JVM verification found no unresolved Critical
  or Required findings after these corrections.
- Independent visual-polish and platform-navigation reviews found no introduced
  Critical or Required defects in component APIs, state, boundaries, or the host correction.
- Independent design-reference review checked tokens, call sites, native hosts,
  and retained authorities; no introduced Critical or Required findings.

## Blockers and accepted risks

- Native evidence covers an Apple Silicon Mac and iPhone Simulator, not a physical
  iPhone, production services, or full accessibility/OS coverage. Prototype UX
  remains subject to human usability testing and explicit production adoption.
