# `SESSION-004`: Edit websites and apps from the Session screen

- **Review tier:** `standard`
- **Tier reason:** A navigation and presentation change inside the accepted two-destination model; no policy, enforcement, or sync behavior changes. An independent review checks `DESIGN.md` conformance and that the active session's frozen set stays truthful.
- **Dependencies:** none; release 1.1, wave R1.1/W1.
- **Integration group:** `PR-SESSION-EDIT-ROUTE`, milestone `1.1.0`.
- **Authority:** [release roadmap](../release-roadmap.md) (revision 2), [DESIGN.md](../../../DESIGN.md) (two primary destinations, action hierarchy), [session usability proposals](../../wiki/topics/mvp-open-questions.md#post-mvp-session-usability-proposals) (`user-confirmed` frustration and the observed cause in `SessionSelectionSummary.kt`).

## Outcome

A person on the Session screen who wants to add or change a paused website or app reaches editing in one action from the paused-items summary, and the selected-items browser no longer reads as a place to type a new website.

## Boundaries

- The route lands on the existing editing surface under Paused items; Session does not gain a second editor, and no third primary destination appears.
- The selected-items browser's search field reads as a filter, and an explicit category-specific add or edit action routes to Paused items (`user-confirmed`, 2026-09-21).
- Both platforms: the Mac sidebar placement and the iPhone bottom navigation, using existing components; a new component requires a `DESIGN.md` amendment.
- During an active session the route still works, the policy changes as today, and the active summary keeps showing the frozen start set (`SESSION-003`).
- Non-goals: policy model, sync, onboarding, unrelated Paused items editor behavior, `TARGETS-006` entry guidance.

## Acceptance

- `AC-01` — From the Session screen summary, one click or tap reaches website editing and one reaches app editing on the Mac and on the iPhone.
- `AC-02` — The selected-items search field cannot be mistaken for website entry: it only filters and reads as a filter, or an explicit add route sits beside it.
- `AC-03` — With a session active, the route works and the active summary still shows the frozen set; ending the session shows the edited policy.
- `AC-04` — The new controls have readable accessibility labels and are reachable by the verification driver on the desktop.

## Verification

- `./gradlew quality`.
- posato-control runs on the supported Mac and the iOS Simulator: route to websites and apps, search filtering, the active-session case, desktop accessibility snapshot; run directories cited from `build/verification/`.
- Independent completed-change review against `DESIGN.md`.

## Decisions or blockers

- **Decision (`user-confirmed`, 2026-09-21):** provide both an explicit add or edit action and filter wording.
- No blocker.
