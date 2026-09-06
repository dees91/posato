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
- Ported 16 surfaces, four walkthroughs, strict transitions, Free play, mock
  events, previews, hidden controls, appearance options, and retained drafts.
- Retired HTML and its Node suite after common regression and native parity
  checks; the non-release tag `archive/mvp-interaction-flow-html` retains both
  at `566bdb6` independently of a squash merge or work-branch deletion.
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
- Integrated the Mac title area and 20 pt corners through AWT and a small
  AppKit/JNI leaf, retaining native controls and removing clipping in fullscreen.

## Verification

- Native macOS AX checks completed all four strict walkthroughs, blocked steps,
  recovery, sync failure/retry, expiry, shortcuts, website submission/save/remove,
  session start, and draft retention across controls and resizing.
- The repository iOS driver, with the prototype bundle override, passed 43-step
  configuration/session, 15-step overlay/appearance/key-wait, and 12-step
  keyboard/draft/cancellation flows. No production API or mutation hook changed.
- The 50-website fixture and nine browser cases passed focused/root gates.
  Native checks covered filtering, editing, picker retention, read-only details,
  sessions, compact IME layout, dark/larger text, and Mac breakpoint changes.
- Entry refinements passed focused gates and root `quality` with 37 common test
  methods. Coverage includes all valid duration values, batch order/deduplication,
  partial failures, guided progress, and one-shot draft acknowledgment.
- A repeated 37-step iPhone Simulator flow covered arrows, presets, the minimum
  duration, review, 15-site batch entry, subsequent additions, partial failure,
  and search/draft restoration. Separate native Mac checks proved actual
  multiline paste and three consecutive Return submissions without refocusing.
  Both targets exercised scroll/preset/scroll selection; Mac keyboard navigation
  reached 24 hours with the correct next-day end. Injection released modifier state.
  Dark/larger-text iPhone checks kept the entry action and a result row visible
  with the keyboard open; the enlarged duration form remained scrollable to review.
- Visual refinements passed focused gates and root `quality` with 37 test methods.
  Native Mac checks covered menu edit/save, app chooser/cancel/remove, and
  compact/expanded light/dark layouts with larger text. A 34-step iPhone flow
  checked the 50-item summary, menus, chooser, search, and keyboard entry.
  Larger text kept a complete result row visible above the keyboard.
  Platform navigation passed 32-step configuration/session and 18-step enlarged,
  dark/keyboard iPhone flows; keyboard-restoration checks wait for IME dismissal.
  Native Mac checks confirmed sidebar navigation, draft retention across destination
  changes/resizing, and minimum-width enforcement with enlarged text. The final
  host correction passed independent review.
- Final root `quality` passed after the accepted advisories: 199 tasks, including
  the prototype gate, 37 common test methods, packaging, and both iOS frameworks.
  Live Mac AX checks confirmed all 31 Free play commands are buttons and active
  copy remains consistent through sync, failure, retry, completion, and expiry.
  iPhone checks passed 11-step active/sync and 15-step retry/early-end flows;
  Free play activation and button traits were inspected after settled scrolling;
  the mock session was ended after verification.
  A fresh tag-only fetch recovered both retired files and the `bfc4a49` ancestry.
- The full-content Mac host passed the focused prototype gate and a fresh root
  `quality --rerun-tasks`: 203 tasks, including 37 common tests, native bridge
  packaging, and both iOS frameworks. Native checks confirmed 20 pt corners,
  fullscreen/restore, minimize/restore, header dragging, the 614-pixel limit, light/dark
  appearance, overlay dismissal, and draft retention through these transitions.
  Captures and AX evidence stay in ignored `build/verification/`.
- `DESIGN.md` YAML/source-path validation and `git diff --check` passed at closeout.

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
- Entry, visual-polish, and navigation reviews found no Critical or Required
  defects in wheel coordination, draft/focus ownership, component APIs, or hosts.
- Independent design-reference and accepted-advisory reviews found no introduced
  Critical or Required findings. The latter checked the whole Free play loop,
  unchanged callbacks, shared active copy, and the remote archive tag.
- Independent Mac-window review found no Critical or Required defects in JNI,
  AppKit threading, window ownership, lifecycle, packaging, or platform scope.
- The maintainer explicitly accepted all three hosted P2 advisories after triage:
  remove contradictory active reassurance, use existing Secondary buttons for
  commands, and preserve HTML via the non-release tag. All were addressed;
  no second hosted pass was requested. Static copy/roles need no new unit tests.

## Blockers and accepted risks

- Native evidence covers an Apple Silicon Mac and iPhone Simulator, not a physical
  iPhone, production services, or full accessibility/OS coverage. Prototype UX
  remains subject to human usability testing and explicit production adoption.
