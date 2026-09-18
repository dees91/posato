# `TARGETS-006`: Include the www variant automatically

- **Review tier:** `standard`
- **Tier reason:** Automatic counterpart persistence touches validation and
  persistence, which are important behavior and need tests; the ADR 0005
  exact-domain contract itself does not change.
- **Dependencies:** none; release 1.1, wave R1.1/W1. `ONBOARDING-003` shares
  `WebsiteEntry` and starts after this row merges.
- **Integration group:** `PR-WWW-COVERAGE`, milestone `1.1.0`.
- **Authority:** [release roadmap](../release-roadmap.md) (revision 1),
  [ADR 0005](../../decisions/0005-macos-browser-enforcement-and-coexistence.md)
  (exact-domain denial), [DESIGN.md](../../../DESIGN.md) (voice, entry
  components), [session usability proposals](../../wiki/topics/mvp-open-questions.md#post-mvp-session-usability-proposals).

## Outcome

When a person enters a website, Posato also saves its `www` counterpart (or
the bare host for a `www` entry) as an ordinary exact-domain row, so a
redirect between `example.com` and `www.example.com` no longer slips past the
pause. There is no prompt and no DNS lecture. Existing 1.0 lists receive the
same pair once on upgrade.

## Boundaries

- Matching stays exact-host under ADR 0005; no wildcard, subdomain, or matcher
  alias. The counterpart is a second stored `ExactDomain`.
- The counterpart is an ordinary exact-domain entry: validated, persisted,
  synchronized, listed, counted, and removable like any other; removing one
  does not silently remove the other.
- Expansion runs on add and batch, including the onboarding first-website
  step through shared `WebsiteEntry`, without redesigning onboarding; this
  row does not edit `feature/onboarding`.
- Edit of one row replaces only that host and does not add a counterpart for
  the new value.
- Copy follows `DESIGN.md` voice and stays short; no extra entry control, and
  no claim about redirects beyond what two exact hosts imply.
- Whether broader subdomain coverage is wanted is recorded as an open product
  question in the wiki, not implemented.
- Non-goals: IDN or validation rule changes beyond deriving the counterpart,
  iOS enforcement changes, import of lists, grouped list rows.

## Acceptance

- `AC-01` — Entering `example.com` saves `example.com` and `www.example.com`;
  entering `www.example.com` saves both; there is no extra option or
  confirmation.
- `AC-02` — An accepted counterpart appears as a normal entry on both devices
  after sync and can be removed on its own.
- `AC-03` — Shared tests cover counterpart derivation for bare hosts, `www`
  hosts, multi-label hosts, hosts where no counterpart applies, invalid
  remainder, batch idempotence, and capacity that keeps the typed host and
  skips the counterpart.
- `AC-04` — A Mac session with both entries blocks both hosts in Safari and
  Chrome, verified on a synthetic pair.
- `AC-05` — A 1.0 policy that contains only the apex (or only `www`) gains the
  missing counterpart once on first read after upgrade; a later removal of
  one row is not restored on the next read.

## Verification

- `./gradlew quality` with the new shared tests.
- posato-control runs on the supported Mac and the iOS Simulator: single
  entry, batch entry, onboarding first-website regression, removal of one
  counterpart; a physical Mac browser check for `AC-04`.
- Independent completed-change review against ADR 0005 and `DESIGN.md`.

## Decisions or blockers

- **Decision (maintainer, 2026-09-18):** persist the `www` counterpart
  automatically as a second exact-domain row, with no prompt. One-shot
  expansion of already-saved domains is in scope. Automatic matching (a
  matcher alias) is rejected; ADR 0005 is unchanged.
- No blocker.
