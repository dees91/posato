# `IOS-004`: iPad-appropriate wording and layout

- **Review tier:** `standard`
- **Tier reason:** Device-idiom branching enters shared code, the navigation placement changes with the iPad orientation, and the verification driver gains a command; no policy or enforcement change. Copy and layout are checked by platform runs; the placement rule and the driver command have unit tests.
- **Dependencies:** none; release 1.1, wave R1.1/W1.
- **Integration group:** `PR-IPAD-COPY`, milestone `1.1.0`.
- **Authority:** [release roadmap](../release-roadmap.md) (revision 2), [DESIGN.md](../../../DESIGN.md) (navigation placement, device labels), [IOS-003 record](../executions/ios-003-app-store-release.md) (iPad kept with four orientations; iPhone-only copy follow-up), [RELEASE-002 record](../executions/release-002-release-candidate.md) (iPad screenshot set and App Store Connect route).

## Outcome

On an iPad, Posato names the device correctly and lays out onboarding, Session, and Paused items for the larger display in all four orientations, and the iPad store screenshots show that state.

## Boundaries

- The device noun comes from the platform's device idiom through a small semantic interface, not from window size or navigation placement; today `PosatoApplication.kt`, `OnboardingScreen.kt`, `PosatoNavigation.kt`, and the short-pause caption in `SessionOverviewContent.kt` assume iPhone whenever the sidebar is absent.
- Mac and iPhone wording stay unchanged.
- Layout uses the existing compact and expanded rules from `DESIGN.md`. The one iPad-specific placement rule is the accepted landscape sidebar below, recorded as a `DESIGN.md` amendment; placement follows the device idiom and the window orientation, and the iPhone keeps the bottom navigation in both orientations.
- Store screenshots are refreshed only when a captured surface changed, through the App Store Connect API as in `RELEASE-002`, with synthetic content and the accepted iPad Pro 13-inch frame.
- Non-goals: iPad-only features, multi-window or Stage Manager behavior, Mac Catalyst, keyboard shortcuts, Navigation 3 and system back gestures.

## Acceptance

- `AC-01` — On an iPad Simulator no user-facing text says iPhone; device labels and nouns say iPad.
- `AC-02` — Onboarding, Session, and Paused items render in all four orientations without clipped, overlapping, or truncated content; iPad landscape shows the sidebar with the device label and About Posato, portrait shows the bottom navigation, and rotation keeps the destination and entered text.
- `AC-03` — The iPhone Simulator shows unchanged wording and layout.
- `AC-04` — The App Store Connect iPad screenshot set (portrait) matches the shipped state, or the record states that no captured surface changed.

## Verification

- `./gradlew quality`.
- posato-control runs on an iPad Simulator in portrait and landscape and on the iPhone Simulator; run directories cited from `build/verification/`.
- Independent completed-change review against `DESIGN.md`.

## Decisions or blockers

- **Decision (`user-confirmed`, 2026-09-22):** iPad landscape adopts the sidebar placement; iPad portrait and the iPhone keep the bottom navigation. Navigation 3 with system back gestures is a separate task through the roadmap intake rule, not part of this row.
- **Decision (`user-confirmed`, 2026-09-22):** `posato-control` gains an `orient` command and scenario step that rotates iOS targets through XCUITest, so four-orientation checks never synthesize input on the maintainer's desktop.
- No blocker.
