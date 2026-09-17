# Execution: PROTOTYPE-APP-001

- **Brief:** [PROTOTYPE-APP-001](../specifications/prototype-app-001.md)
- **Status:** done
- **Review tier:** Standard
- **Implementer:** Codex
- **Reviewer:** Independent Codex reviewers, separate read-only review contexts.
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
  checks; non-release tag `archive/mvp-interaction-flow-html` retains both at
  `566bdb6` independently of a squash merge or work-branch deletion (the tag was
  deleted on 2026-09-17; the commits stay visible in pull request #32).
- Reworked the 50-website study with primary session actions above two counted
  summaries, read-only details, separate website/app tabs, search, lazy rows,
  and labelled menus. Browser state belongs to composition above editor navigation.
- Replaced custom-time text entry with bounded hours/minutes wheels and step
  arrows. Prioritized inline website additions over search, with batch
  canonicalization, duplicate skipping, partial recovery, and retained drafts.
- Aligned segmented categories with content, strengthened inactive contrast and
  app-chooser priority, separated entry guidance from metadata, and unified menus.
  Main destinations use an iPhone bottom bar and Mac sidebar; category controls
  retain mist/paper segments. Removed Done and the duplicate sync-footer separator.
  Active-session restrictions and a readable minimum Mac content width remain.
- Preserved accepted production requirements in root `DESIGN.md`; the complete
  [prototype reference](../../../prototypes/mvp-interaction-flow/DESIGN.md) records
  actual tokens, components, all 16 surfaces, native layouts, and interactions.
  The product baseline and wiki distinguish documentation from production adoption.
- Integrated the Mac title area and 20 pt corners through AWT and a small
  AppKit/JNI leaf, retaining native controls and removing clipping in fullscreen.
  Configuration is asynchronous on AppKit and strongly retains its window.
- Split fast `verifyPrototype` from explicit `verifyPrototypeHosts`. Native
  resources are built for launch/packaging, not JVM tests; task-backed resources
  and explicit package inputs preserve native-only rebuilds with Compose 1.10.3.

## Verification

- Initial macOS AX checks completed all four strict walkthroughs, blocked steps,
  recovery, sync failure/retry, expiry, shortcuts, website submission/save/remove,
  session start, and draft retention across controls and resizing.
- Earlier iPhone driver replays passed 43-step configuration/session, 15-step
  overlay/appearance/key-wait, and 12-step keyboard/draft/cancellation flows.
  No production API or mutation hook changed.
- The long-list study added nine browser cases; both targets exercised filtering,
  editing, picker retention, read-only details, sessions, keyboard layouts, dark
  appearance, larger text, and retained search/list state.
- Entry refinements passed focused/root gates with 37 common test methods,
  including all valid durations, batch order/deduplication, partial failures,
  guided progress, and one-shot draft acknowledgment. A 37-step iPhone replay
  covered arrows/presets, minimum duration, review, 15-site batch entry, partial
  failure, and restoration; Mac checks proved multiline paste and repeated Return.
  Both targets exercised scroll/preset/scroll; Mac keyboard entry reached 24 hours.
- Visual and navigation checks included a 34-step iPhone menu/chooser/search flow,
  32-step configuration/session and 18-step enlarged/keyboard replays; Mac checks
  covered menus, sidebar, resizing, minimum width, appearance, and draft retention.
  iPhone checks explicitly waited for keyboard dismissal and settled scrolling.
- Earlier hosted-advisory corrections passed root `quality` (199 tasks), all
  31 Mac Free play AX button checks, active sync/failure/retry/completion/expiry,
  and 11-step and 15-step iPhone flows. A fresh tag-only fetch recovered the retired
  files and `bfc4a49` ancestry. Mock sessions were ended after verification.
- The original full-content host passed root `quality --rerun-tasks` (203 tasks)
  and native corner/fullscreen/minimize/drag/resize/appearance/draft checks.
- Final follow-up `quality --rerun-tasks` passed with 193 tasks executed.
  The explicit host gate passed desktop packaging, both linked iOS frameworks,
  and the unsigned Simulator host. Independent `jvmTest --rerun-tasks` passed
  37 tests, with no prototype native compiler or host packaging in its task graph.
  The aggregate dry run likewise excludes those prototype host tasks.
- Both the packaged app and Gradle run launched and closed normally. Packaged
  checks covered initial paint, three fullscreen/restore cycles, minimize/restore,
  the 614-pixel minimum width, header dragging, light/dark appearance, overlay
  dismissal, and retained entry draft. Native-only source changes and restoration
  each invalidated packaging without forced reruns; final radius remains 20 pt.
- Both design documents passed YAML/schema-label/source-path checks; repository
  links and `git diff --check` passed. Captures and logs remain ignored under
  `build/verification/prototype-pr32-followup-2026-09-06/`; earlier native
  evidence remains under `build/verification/`. Existing user apps were untouched.

## Completed-change review

- **Verdict:** no unresolved Critical or Required findings in completed source review.
- Initial reviews corrected guided progress, dark semantics, shortcut/dialog
  focus, iOS field restoration, keyboard-visible list space, and search anchoring.
  Regression tests and affected native checks passed after correction.
- Independent entry, visual, navigation, design-reference, hosted-advisory
  correction, and Mac-window reviews inspected state, component/host ownership,
  callbacks, active copy, archive retention, and platform boundaries.
- Follow-up review separated design authority and host verification and removed
  synchronous AppKit dispatch. Independent review caught ignored Compose task
  dependencies and untracked package resources; both were corrected and checked
  with normal native-only rebuilds, both launch modes, and fresh gates.
- The maintainer explicitly accepted all three hosted P2 advisories after triage:
  active reassurance, Free play button semantics, and HTML archive retention.
  All were addressed; no second hosted pass was requested. The later three
  owner-published comments came from local review, not another hosted pass.

## Blockers and accepted risks

- Native evidence covers an Apple Silicon Mac and iPhone Simulator, not a physical
  iPhone, production services, or full accessibility/OS coverage. Prototype UX
  remains subject to human usability testing and explicit production adoption.
- The AppKit wait-cycle risk was corrected without reproducing a hang; native
  replay establishes tested behavior, not absence of every possible timing race.
