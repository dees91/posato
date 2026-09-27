# `DOCS-003`: Prepare the public packaging for Posato 1.2

- **Review tier:** `standard`
- **Tier reason:** Public media, README, site, and store text change no product behavior, but they make claims about schedules, the Mac setup, and notices that must match verified behavior; one independent completed-change review covers claim accuracy, synthetic captures, reproducible renders, and budgets.
- **Dependencies:** `ONBOARDING-004` (#95), `NOTIFY-001` (#94), and every `SCHEDULE-002` slice up to #102, which this branch stacks on so that the captures show the 1.2 applications. `RELEASE-004` (#96) publishes what this row prepares.
- **Integration group:** `PR-RELEASE-1-2-MEDIA`, roadmap revision 11, wave R1.2/W5.
- **Authority:** [release roadmap](../release-roadmap.md), [DESIGN.md](../../../DESIGN.md) (voice, palette, website), [schedules and Mac setup](../../product/schedules-and-mac-setup.md), [schedule rules](../../product/schedules-decisions.md), [showcase media](../../../video/README.md) and [storyboard](../../../video/STORYBOARD.md), [store listing](../../store/en-US/listing.md), and the [DOCS-002 brief](docs-002-showcase-media.md) (the motion system and budgets this row keeps).

## Outcome

When the maintainer gives the go for 1.2, `RELEASE-004` can publish without writing or capturing anything: the README, `posato.app`, the showcase media, the App Store description, What's New, and screenshots, and the GitHub release notes all describe Posato 1.2 from real captures of the 1.2 applications.

## Boundaries

- **Prepare, never publish.** No merge, deploy, upload, App Store Connect change, or GitHub Release edit. The site deploys and the store upload happen in `RELEASE-004` after the go.
- **Captures.** Real applications only, driven by `posato-control` without the maintainer: the Mac in a Tart VM (never on the maintainer's Mac), the iPhone showcase on the test iPhone, and the store screenshots on the Simulators named in the listing recipe. Synthetic choices only (`example.com`, `example.net`, the built-in Chess application, one unnamed built-in iPhone application, a schedule named with a neutral word); no account, device name, identifier, or home path. Only resolution is reduced.
- **Story.** The storyboard keeps the `DOCS-002` motion system, rules, and budgets. It adds a schedule scene to the hero and the walkthrough and drops the administrator prompt, which 1.2 no longer shows at Start after setup. The GIF ladder may lower frame rate, palette, or size, never the story.
- **Copy.** New or changed public copy is checked with the `clarity` skill; no em or en dashes in public copy; no claim beyond verified 1.2 behavior and the stated limits (for example, Mac schedules need Posato running in the menu bar, iPhone restrictions can linger).
- **Non-goals:** product or in-app copy changes, a Polish listing, new site pages, WebM output, and the privacy policy text, which `SCHEDULE-001` and `NOTIFY-001` already updated.

## Acceptance

- `AC-01` — The hero and walkthrough show a schedule being planned alongside the timed pause, every action is a visible click or tap with a callout, and the storyboard test passes.
- `AC-02` — `npm run media` reproduces the GIF, site MP4 and poster, walkthrough, stills, and social preview from tracked captures of one recorded product revision, and `npm run verify` passes within the documented budgets.
- `AC-03` — The README and `posato.app` describe 1.2 (schedules, one Mac setup, the menu bar, pause notices) with working links, and the site builds.
- `AC-04` — The store listing has the 1.2 description, What's New, and iPhone 6.9-inch and iPad 13-inch screenshot sets captured by the recorded recipe, ready for `posato-provisioning store prepare`.
- `AC-05` — The GitHub release notes for 1.2 are ready in the execution record, and `RELEASE-004` points to them.

## Verification

- `video/`: clean `npm ci`, `npm run media`, `npm run verify`; contact sheet and GIF first and last frame review.
- `website/`: build and a browser check at phone and desktop widths.
- Capture runs cited by their ignored run directories; a privacy scan of every tracked PNG.
- One independent completed-change review against `DESIGN.md`, the product scope, and the verified limits.

## Decisions or blockers

- **Decision (`user-confirmed`, 2026-09-27):** the maintainer asked for the whole 1.2 packaging to be prepared for a later release go.
- **Blocker (maintainer):** the release go, the social preview upload in the repository settings, and the site deploy, all in `RELEASE-004`.
