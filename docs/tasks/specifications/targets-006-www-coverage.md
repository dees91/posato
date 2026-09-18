# `TARGETS-006`: Treat www and the typed host as one paused website

- **Review tier:** `standard`
- **Tier reason:** Matching and apply-time coverage are important behavior and
  need tests; the change is a named ADR 0005 clarification, not a new
  enforcement mechanism.
- **Dependencies:** none; release 1.1, wave R1.1/W1. `ONBOARDING-003` shares
  `WebsiteEntry` and starts after this row merges.
- **Integration group:** `PR-WWW-COVERAGE`, milestone `1.1.0`.
- **Authority:** [release roadmap](../release-roadmap.md),
  [ADR 0005](../../decisions/0005-macos-browser-enforcement-and-coexistence.md),
  [DESIGN.md](../../../DESIGN.md),
  [session usability proposals](../../wiki/topics/mvp-open-questions.md#post-mvp-session-usability-proposals).

## Outcome

When a person enters a website, Posato stores that one exact host and pauses
its `www` variant through the matching rule on both platforms, so a redirect
between `example.com` and `www.example.com` no longer slips past the pause.
Entry copy and the saved row say so. Other subdomains stay separate.

## Boundaries

- One stored `ExactDomain` per typed host. Sync format-1 is unchanged.
- Matching treats `www.<host>` and `<host>` as one paused website. No
  wildcard, suffix, or other subdomain coverage.
- macOS applies the rule in `ExactHostPolicy.matches`. iOS expands the
  `WebDomain` set at apply in `IosEnforcementProvider`.
- Removing the typed row ends coverage of both variants.
- Copy follows `DESIGN.md` voice and stays short.
- Broader subdomain coverage remains an open wiki question, not implemented.
- Non-goals: IDN or validation rule changes beyond counterpart derivation,
  grouped list rows, import of lists, schema changes.

## Acceptance

- `AC-01` — Entering `example.com` stores only `example.com`, shows one list
  item with a caption that `www.example.com` is also paused, and says at
  entry that the www variant is included.
- `AC-02` — Entering `www.example.com` stores only that host and pauses
  `example.com` through the same matching rule.
- `AC-03` — Shared tests cover counterpart derivation for bare hosts, `www`
  hosts, multi-label hosts, doubled `www`, and hosts where no counterpart
  applies. Helper tests cover matching either selection. An iOS apply test
  shows both `WebDomain` values for one entry.
- `AC-04` — A Mac session with one stored `example.com` blocks both hosts in
  Safari and Chrome.

## Verification

- `./gradlew quality` including `:macosHelper:check`.
- posato-control on the supported Mac and the iOS Simulator: add one website,
  one SQL row, one list item; remove it and confirm nothing remains.
- Physical Mac browser check for `AC-04`.

## Decisions or blockers

- **Decision (maintainer, 2026-09-18):** one stored row is what the person
  typed; `www` equivalence is a matching rule, not data. Materialized
  counterparts are rejected.
- **Blocker:** none. The extra `www` rows from `af6636b` were removed in the
  UI and `user_version` was reset to 10 on the supported Mac.
