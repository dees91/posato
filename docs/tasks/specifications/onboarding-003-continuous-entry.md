# `ONBOARDING-003`: Keep onboarding website entry continuous

- **Review tier:** `standard`
- **Tier reason:** Focus, saved-count presentation, and responsive action layout change; permission and persistence contracts stay unchanged.
- **Dependencies:** none; release 1.1, wave R1.1/W1. Base includes `SESSION-004`, `TARGETS-006`, and `IOS-004`.
- **Integration group:** `PR-ONBOARDING-ENTRY`, milestone `1.1.0`.
- **Authority:** [release roadmap](../release-roadmap.md), [DESIGN.md](../../../DESIGN.md), [accepted usability outcomes](../../wiki/topics/mvp-open-questions.md#post-mvp-session-usability-proposals), [quality contract](../../development/engineering-quality-contract.md). The maintainer authorized brief/worktree preparation and a draft PR for delegated implementation on 2026-09-23.

## Outcome

During first-run setup, a person can add consecutive websites without refocusing the field, see the saved total, and continue with the keyboard open. Expanded Mac permission actions use a consistent arrangement.

## Boundaries

- Preserve the existing six-step flow, completion persistence, optional service setup, permission action hierarchy, and compact full-width primary action.
- Keep live text in the existing composable-owned field. Preserve invalid input, duplicate feedback, batch submission, and the `TARGETS-006` matching rule.
- The saved total comes from saved policy state. Feedback about the last submission must remain distinguishable from that total.
- Own onboarding UI/state, its tests, and the required resource entries. Keep helper ports and lifecycle semantics unchanged during parallel `MACOS-011` work.
- Non-goals: update consent, helper authorization changes, navigation redesign, schema or synchronization changes, and new UI-test tooling.

## Acceptance

- `AC-01`: Adding two valid websites separately through the existing submit actions keeps entry focused and ready for the next website; Continue remains visible and usable above the software keyboard.
- `AC-02`: The website step states the total saved websites after successful submissions, including existing entries. Duplicates, invalid input, and failed saves do not inflate it or discard retryable input.
- `AC-03`: Expanded Mac helper-permission actions have a consistent arrangement within the accepted primary/secondary/quiet hierarchy. Compact layouts and permission behavior remain intact.
- `AC-04`: iPhone, iPad portrait/landscape, and Mac retain usable entry and continuation; the summary reports the persisted total, and finishing setup still skips onboarding on relaunch.

## Verification

- Focused tests for changed saved-count or submission-state behavior; no static rendering or copy tests.
- Drive onboarding through [verify-posato](../../../.agents/skills/verify-posato/SKILL.md) and its [onboarding recipe](../../../.agents/skills/verify-posato/features/onboarding.md). Capture focus/keyboard, successive additions, duplicate/invalid feedback, Continue, summary, relaunch, and compact/expanded permission layouts. Preserve and restore local data.
- Run affected platform checks, final `./gradlew quality`, and an independent completed-change review. Cite ignored run directories in the execution record.

## Decisions or blockers

- `user-confirmed`, 2026-09-23: prepare this task alongside the initial `MACOS-011` proof stage in separate worktrees; implementation will be delegated.
- No known prerequisite blocker. Coordinate shared-document edits and serialize native runs on the same target as recorded in the execution plan.
