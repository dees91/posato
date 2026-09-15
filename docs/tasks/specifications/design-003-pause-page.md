# `DESIGN-003`: Style the macOS pause page in Posato's design language

- **Review tier:** `standard`
- **Tier reason:** A self-contained page served by the existing loopback listener changes presentation only; an independent check still covers the ADR 0005 content limits and both browsers.
- **Dependencies:** completed `MACOS-004` and `DESIGN-002` (Forest icon and palette, merged as `c8112c4`).
- **Integration group:** `PR-PAUSE-PAGE`, roadmap wave Release/R2, in parallel with `MACOS-009` and `SYNC-017`. `DOCS-001` and `WEB-001` follow it.
- **Authority:** [MVP roadmap](../mvp-roadmap.md) (revision 17), [browser enforcement ADR](../../decisions/0005-macos-browser-enforcement-and-coexistence.md) (fixed presentation), [DESIGN.md](../../../DESIGN.md) (voice, palette, typography, identity), [privacy policy](../../../PRIVACY.md) (Blocking on Mac).

## Outcome

When Posato blocks a website in Safari or Chrome, the pause page looks like Posato: the mark, the accepted palette in light and dark appearance, a clear "paused" message with the session end when available, and a calm route back to the app, while remaining the same local, self-contained page.

## Boundaries

- `observed` starting point: `BlockedPage.swift` returns unstyled HTML with a headline and one sentence, served only at `GET /blocked` on the `127.0.0.1` listener with `Cache-Control: no-store`.
- Keep ADR 0005 limits: no attempted or selected target, query, fragment, network or third-party asset, or session-mutation endpoint. Styles and the mark are inline; use the system font stack and no JavaScript. The page stays on `127.0.0.1` (revision 17 decision).
- Follow the DESIGN.md voice: no shame, urgency, or claims of perfect prevention. The route back to Posato stays text unless a safe fixed activation already exists; adding a URL scheme or other new entry point is out of scope.
- Change only `macosHelper/Sources/PosatoMacOSHelper/BlockedPage.swift`, its tests, and a DESIGN.md pause page subsection. `MACOS-009` owns the This Mac removal UI and helper lifecycle code.
- Non-goals: the iPhone Screen Time shield, new copy for enforcement limits, localization beyond the existing locale-aware time.

## Acceptance

- `AC-01` — The page renders the mark, headline, session end, and route to Posato in both light and dark appearance, readable at default and enlarged text sizes.
- `AC-02` — Tests assert that the response contains no script, external URL, form, or attempted host, and stays within the proxy's response bounds.
- `AC-03` — On a physical Mac, a blocked HTTP site and a blocked HTTPS site show the styled page in Safari and Google Chrome.
- `AC-04` — DESIGN.md records the pause page layout and copy.

## Verification

- Swift helper tests and `./gradlew quality`.
- Physical run in Safari and Chrome through [verify-posato](../../../.agents/skills/verify-posato/SKILL.md), checking both appearances; evidence stays under `build/verification/`. Run this Mac gate before `MACOS-009` unregisters the helper, or after it re-enables the helper.
- Independent completed-change review against ADR 0005 and DESIGN.md.

## Decisions or blockers

- None. The maintainer reviews the styled page in the pull request before merge.
