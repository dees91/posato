# `TARGETS-006`: Explain exact-host coverage and offer the www variant

- **Review tier:** `standard`
- **Tier reason:** Entry-time guidance plus one derived exact-domain entry touch validation and persistence, which are important behavior and need tests; the ADR 0005 exact-domain contract itself does not change.
- **Dependencies:** none; release 1.1, wave R1.1/W1. `ONBOARDING-003` shares `WebsiteEntry` and starts after this row merges.
- **Integration group:** `PR-WWW-COVERAGE`, milestone `1.1.0`.
- **Authority:** [release roadmap](../release-roadmap.md) (revision 1), [ADR 0005](../../decisions/0005-macos-browser-enforcement-and-coexistence.md) (exact-domain denial), [DESIGN.md](../../../DESIGN.md) (voice, entry components), [session usability proposals](../../wiki/topics/mvp-open-questions.md#post-mvp-session-usability-proposals).

## Outcome

When a person enters a website, Posato says that only that exact host is paused and offers, in one action, to add its `www` counterpart (or the bare host for a `www` entry), so a redirect between `example.com` and `www.example.com` no longer slips past the pause unexpectedly.

## Boundaries

- Matching stays exact-host under ADR 0005; no wildcard, subdomain, or automatic inclusion.
- The counterpart is an ordinary exact-domain entry: validated, persisted, synchronized, listed, counted, and removable like any other; removing one does not silently remove the other.
- The guidance and the option live in the shared `WebsiteEntry` and apply wherever it is used, including batch entry and the onboarding first-website step, without redesigning onboarding; this row does not edit `feature/onboarding`.
- Copy follows `DESIGN.md` voice and stays short; no claim about redirects beyond what exact-host matching implies.
- Whether broader subdomain coverage is wanted is recorded as an open product question in the wiki, not implemented.
- Non-goals: IDN or validation rule changes beyond deriving the counterpart, iOS enforcement changes, import of lists.

## Acceptance

- `AC-01` — Entering `example.com` shows the exact-host guidance and an option to also add `www.example.com`; entering `www.example.com` offers `example.com`.
- `AC-02` — An accepted counterpart appears as a normal entry on both devices after sync and can be removed on its own.
- `AC-03` — Shared tests cover counterpart derivation for bare hosts, `www` hosts, multi-label hosts, hosts where no counterpart applies, and invalid input.
- `AC-04` — A Mac session with both entries blocks both hosts in Safari and Chrome, verified on a synthetic pair.

## Verification

- `./gradlew quality` with the new shared tests.
- posato-control runs on the supported Mac and the iOS Simulator: single entry, batch entry, onboarding first-website regression, removal of one counterpart; a physical Mac browser check for `AC-04`.
- Independent completed-change review against ADR 0005 and `DESIGN.md`.

## Decisions or blockers

- **Decision (maintainer, at implementation):** the counterpart is opt-in per entry (proposed) or pre-selected; the guidance shows on every entry (proposed) or once.
- No blocker.
