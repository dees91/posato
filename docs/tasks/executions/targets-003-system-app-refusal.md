# Execution: `TARGETS-003` system-app refusal

- **Brief:** [`../specifications/targets-003-system-app-refusal.md`](../specifications/targets-003-system-app-refusal.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** `Muse Code session`
- **Reviewer:** `two independent review agents`
- **Branch:** `chore/targets-003-system-app-refusal`
- **Updated:** `2026-09-08`

## Plan

1. Shared Swift refusal source in the helper target; picker check plus enforcement adoption; stale comment corrected.
2. Swift refusal and protocol round-trip tests; Kotlin decode test.
3. Kotlin result, rejection, mapping, UI copy, and ViewModel coverage.
4. Driver recipe `mapping-errors` line plus stale wiki sentence corrected.
5. Focused tests, aggregate quality, scans, driver refusal/control rows, manual keyboard pass, independent completed-change review, PR.

## Result

- Picker refuses system-critical bundles and CoreServices paths before signature inspection with whole-batch rejection; enforcement adopts the shared source unchanged.
- Outcome byte `7` flows through the Kotlin decoder into rejection `SYSTEM` and the Finder-naming copy; `SELF`, `INVALID_OR_UNSIGNED`, and iOS-owned `UNSUPPORTED` are not reused.
- Material deviation: extracted `refusalOutcome` after SwiftLint flagged `select()` complexity 11, and avoided a single-element multi-line array both formatters could not agree on; test byte `8` asserts `IllegalStateException` per the pre-existing `error(...)` contract.

## Completed-change review

- **Verdict:** `approved` (two independent passes)
- **Critical or Required findings:** none in either pass.
- **Resolution:** no corrections needed; affected verification (focused suites, aggregate quality) was already green after the last correction.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Focused Swift tests | `pass` | 155/155 incl. 4 new refusal and round-trip rows |
| Focused JVM tests | `pass` | Protocol 3/3, mappings 8/8, ViewModel 28/28 incl. new rows |
| Aggregate quality | `pass` | `./gradlew quality` `BUILD SUCCESSFUL`, 197 tasks |
| `git diff --check`, secret/path scan | `pass` | clean |
| Driver refusal/control rows | `pass` | Finder path refused with new copy at count 0; Safari mapped, persisted across relaunch, removed; runs under `build/verification/` |
| Independent completed-change review | `pass` | two approvals, no findings at any severity |

## Blockers and accepted risks

- VoiceOver was not separately run: the refusal copy renders through the existing failure-text path already covered by the TARGETS-003 physical gate, and no new focusable surface was added.

## Final

- **Status:** `done`
- **Outcome:** implementation, affected verification, driver gate, and independent review are complete; the branch is ready for push and pull request
