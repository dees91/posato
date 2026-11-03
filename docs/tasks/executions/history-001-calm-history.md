# Execution: `HISTORY-001`

- **Brief:** [Decide a calm history of pauses](../specifications/history-001-calm-history.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Claude
- **Reviewer:** independent agent (completed-change review)
- **Branch:** `history-001-calm-history`
- **Updated:** 2026-11-03

## Plan

1. Read the schedule, session, and break tables and the host policy to find
   which existing facts a day can be derived from, and for how long they
   last.
2. Write the decision page with the owner's answers of 2026-10-30, the
   remaining details decided under the owner's instruction to proceed, the
   visual form, the proposed `DESIGN.md` section and `PRIVACY.md` text, and
   the delivery plan.
3. Add the `HISTORY-002` backlog row (roadmap revision 38) and its Projects
   item, route the page from the wiki, and update idea 47.
4. Get one independent review against `DESIGN.md`, `PRIVACY.md`, ADR 0006,
   and the schedule rules, then run `quality`.

## Result

- [The history decision](../../product/history-decisions.md) holds every
  decision with provenance (`AC-01`), the proposed `DESIGN.md` section with
  a sketch and the `PRIVACY.md` text (`AC-02`), and the delivery plan with
  platforms, derivation, tests, tooling, and device runs (`AC-03`).
- Deviation from the brief: the owner chose a history "derived from the
  facts it already has". `observed`: those facts do not last (skip and End
  early facts go with their schedule, a natural end is forgotten after about
  a day, and the schedule table holds only the current plan). The decision
  therefore writes one local day row from those facts (ended early at once,
  ran as planned once the day is over), never synchronized. It keeps the owner's
  boundary (no new synchronized record, no ADR 0006 amendment) and is marked
  `user-confirmed` since the owner accepted it on 2026-11-03.
- `HISTORY-002` is in the roadmap backlog (revision 38).

## Completed-change review

- **Verdict:** `approved` after correction. One independent agent pass
  found no Critical and four Required findings, all corrected:
  - a day derived from facts that can disappear before it is final (End
    early, then the same-day deletion of its schedule; a late received End
    early): ended early is now written at once and can override ran as
    planned;
  - today judged final after its last occurrence, although an edit can run
    again the same day: a day is final only after its date has passed;
  - **Clear history** unreachable after the last schedule is deleted: the
    section shows while recorded days exist;
  - provenance: owner wording quoted, agent choices relabelled, the
    `DESIGN.md` section marked as proposed.
- **Accepted Recommended:** Clear history keeps the last final date; time
  zone and clock rollback tests; `HISTORY-002` is High-risk; the grid is a
  non-interactive accessibility element; Windows follows the 1.6 Windows
  privacy text. Optional items folded: weekday and month abbreviations by
  locale, the log entry typed as a proposal.
- **Owner acceptance:** 2026-11-03, PR #219 questionnaire: the local day
  record, the visual form, and the privacy text, as recommended; the
  `agent-delegated` items are relabelled `user-confirmed` and the `DESIGN.md`
  section is added pending delivery.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew verifyEnglishText` | pass | no accented letters outside translations |
| Relative links in the changed documents | pass | every target exists |
| `./gradlew quality` on `ed278207` | pass | BUILD SUCCESSFUL in 6m 23s |
| Contrast of the day shapes (WCAG relative luminance) | pass | `primary` 6.74:1 and 8.22:1, `onSurfaceVariant` 4.96:1 and 7.77:1 on the light and dark surfaces; `outline` 2.05:1 is used only for the background dot |

## Blockers and accepted risks

- None. `PRIVACY.md` changes only when `HISTORY-002` ships the history.

## Final

- **Status:** `done`
- **Outcome:** met: `AC-01` to `AC-03`, accepted by the owner on 2026-11-03
