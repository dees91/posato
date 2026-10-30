# `HISTORY-001`: Decide a calm history of pauses

- **Review tier:** `standard`
- **Tier reason:** A discovery row that ends in documents only; it proposes a privacy and design change for the maintainer to accept and writes no product code, so one independent review of the proposal is enough.
- **Dependencies:** none (R1.6/W1). No 1.6 row depends on it; its delivery is composed into a later release.
- **Integration group:** `PR-HISTORY-DECISION`
- **Authority:** [Release roadmap](../release-roadmap.md) row `HISTORY-001`; idea 47 in [open questions](../../wiki/topics/mvp-open-questions.md) (`user-confirmed` 2026-10-20); [DESIGN.md](../../../DESIGN.md) ("Avoid shame, urgency, streaks, scores, gamification"); [`PRIVACY.md`](../../../PRIVACY.md). The maintainer named the row on 2026-10-30.

## Outcome

A recorded product and privacy decision for a quiet history of pauses kept as planned, for example a calendar of days in the spirit of a contribution graph, with a proposed `DESIGN.md` rule and a delivery plan.

## Boundaries

- Decide what a day records: planned and kept pauses, ended early, skipped, breaks (`SCHEDULE-007`); never websites visited or blocked attempts (`PRIVACY.md` forbids browsing history).
- Decide where it lives: on the device only, or synchronized, and what **Remove workspace** and the folder workspace then delete.
- Decide the form: how it avoids streaks, scores, and shame, where it sits in the navigation, and whether it can be turned off or cleared.
- Propose, do not accept: the `DESIGN.md` rule, the `PRIVACY.md` and privacy-manifest change, and an ADR 0006 amendment if the history synchronizes.
- Non-goals: product code, charts outside Posato, exports of the history.

## Acceptance

- `AC-01` — Each decision above is recorded in a product page with provenance, after the maintainer's answers.
- `AC-02` — The proposed `DESIGN.md` rule and screen sketch, and the `PRIVACY.md` text, are in the pull request for the maintainer's acceptance.
- `AC-03` — A delivery plan names the platforms, the data the history derives from (existing session and occurrence facts first), its verification, and a backlog row for the delivery.

## Verification

- Documents only: an independent review of the proposal against `DESIGN.md`, `PRIVACY.md`, ADR 0006, and the schedule rules.

## Decisions or blockers

- `user-confirmed` (2026-10-30, questionnaire): the history stays on each device and is derived from the session and occurrence facts it already has, with no new synchronized record and no ADR 0006 amendment; a day is kept as planned when every scheduled occurrence that day ran to its end without **End early**, a `SCHEDULE-007` break does not spoil it, skipped occurrences do not count, and a day without a plan is neutral rather than empty; the history is visible by default and can be hidden and cleared; it sits in Schedules below the list, with no new tab.
- Still open for the proposal: the visual form within the `DESIGN.md` brand rule.
