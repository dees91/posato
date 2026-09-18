# `IOS-004`: iPad-appropriate wording and layout

- **Review tier:** `standard`
- **Tier reason:** Device-idiom branching enters shared code and the App Store screenshot set may change; no policy or enforcement change. Copy and layout are checked by platform runs, not unit tests.
- **Dependencies:** none; release 1.1, wave R1.1/W1.
- **Integration group:** `PR-IPAD-COPY`, milestone `1.1.0`.
- **Authority:** [release roadmap](../release-roadmap.md) (revision 1), [DESIGN.md](../../../DESIGN.md) (navigation placement, device labels), [IOS-003 record](../executions/ios-003-app-store-release.md) (iPad kept with four orientations; iPhone-only copy follow-up), [RELEASE-002 record](../executions/release-002-release-candidate.md) (iPad screenshot set and App Store Connect route).

## Outcome

On an iPad, Posato names the device correctly and lays out onboarding, Session, and Paused items for the larger display in all four orientations, and the iPad store screenshots show that state.

## Boundaries

- The device noun comes from the platform's device idiom through a small semantic interface, not from window size or navigation placement; today `PosatoApplication.kt`, `OnboardingScreen.kt`, `PosatoNavigation.kt`, and the short-pause caption in `SessionOverviewContent.kt` assume iPhone whenever the sidebar is absent.
- Mac and iPhone wording stay unchanged.
- Layout uses the existing compact and expanded rules from `DESIGN.md`; an iPad-specific placement rule needs a `DESIGN.md` amendment accepted by the maintainer.
- Store screenshots are refreshed only when a captured surface changed, through the App Store Connect API as in `RELEASE-002`, with synthetic content and the accepted iPad Pro 13-inch frame.
- Non-goals: iPad-only features, multi-window or Stage Manager behavior, Mac Catalyst, keyboard shortcuts.

## Acceptance

- `AC-01` — On an iPad Simulator no user-facing text says iPhone; device labels and nouns say iPad.
- `AC-02` — Onboarding, Session, and Paused items render in all four orientations without clipped, overlapping, or truncated content.
- `AC-03` — The iPhone Simulator shows unchanged wording and layout.
- `AC-04` — The App Store Connect iPad screenshot set matches the shipped state, or the record states that no captured surface changed.

## Verification

- `./gradlew quality`.
- posato-control runs on an iPad Simulator in portrait and landscape and on the iPhone Simulator; run directories cited from `build/verification/`.
- Independent completed-change review against `DESIGN.md`.

## Decisions or blockers

- **Decision (maintainer, at implementation):** whether iPad landscape adopts the sidebar placement; the proposal keeps the current placement rules.
- No blocker.
