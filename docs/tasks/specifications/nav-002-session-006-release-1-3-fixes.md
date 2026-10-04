# `NAV-002` and `SESSION-006`: Release 1.3 defects found in packaging

- **Review tier:** Standard
- **Tier reason:** A navigation lifecycle correction and Session copy and
  visibility; no persistence, synchronization, privacy, or enforcement
  change.
- **Dependencies:** `NAV-001` and `SCHEDULE-004` (merged); release 1.3,
  wave 4. `DOCS-004` and `RELEASE-005` wait for both rows.
- **Integration group:** PR-RELEASE-1-3-FIXES
- **Authority:** [Release roadmap](../release-roadmap.md) revision 17, rows
  `NAV-002` and `SESSION-006`; the maintainer asked on 2026-10-03 to fix the
  three defects that the `DOCS-004` captures found before packaging.

## Outcome

Leaving a pause set's screen and then switching destinations no longer
crashes Posato on iOS, and Session describes pause sets instead of the former
single item list.

## Boundaries

- `NAV-002`: the iOS app terminated with `IllegalStateException` ("already
  been disposed") after Pause sets, a set's screen, back, and then Schedules.
  The set's screen holds its own stack, and Navigation 3 discards a removed
  screen's content only at the end of a later recomposition, after the
  enclosing stack's back dispatcher was disposed with its children. Correct
  the stack's dispatcher lifecycle in `PosatoNavStack` without changing the
  pinned Navigation 3 or navigationevent versions; upstream has the same
  behavior.
- `SESSION-006`:
  - replace **Paused items** in strings that the app still shows with
    **Pause sets** or "your pause sets";
  - word the running session's frozen-count caption and the summary's notes
    in terms of the session's set;
  - while only a scheduled pause restricts, hide the default set's summary,
    because each running part already names its own set.
- Non-goals: enforcement, schedule composition, unused string resources,
  and a Navigation 3 upgrade.

## Acceptance

- `AC-01` — The crash sequence that failed on `main` passes ten times in a
  row on an iPhone and an iPad Simulator and on the test iPhone, with no new
  crash report.
- `AC-02` — iOS edge swipe, Command-[, the Mac trackpad swipe, and the Back
  buttons still return one screen, and switching destinations afterwards
  works.
- `AC-03` — A running manual session shows the new caption; a running
  scheduled pause shows no default-set summary, and its part caption names
  its set.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- The crash scenario through `posato-control run` on Simulators and the test
  iPhone (`-t device`), before and after the change.
- The Mac in a Tart VM (`--vm primary`): back paths, a manual session, and a
  scheduled pause.
- `./gradlew quality`.

## Decisions or blockers

- None.
