# `DOCS-004`: Prepare the public packaging for Posato 1.3

- **Review tier:** `standard`
- **Tier reason:** Public media, README, site, and store text change no product
  behavior, but they make claims about pause sets, Intel Macs, and back
  gestures that must match verified 1.3 behavior; one independent
  completed-change review covers claim accuracy, synthetic captures,
  reproducible renders, and budgets.
- **Dependencies:** every other release 1.3 row is merged, including
  `SCHEDULE-004`, `SCHEDULE-006`, `MACOS-015`, `NAV-001`, and `WEB-002`.
  `RELEASE-005` publishes what this row prepares.
- **Integration group:** `PR-RELEASE-1-3-MEDIA`, roadmap revision 16, wave
  R1.3/W5.
- **Authority:** [release roadmap](../release-roadmap.md), [DESIGN.md](../../../DESIGN.md),
  [pause sets](../../product/pause-sets.md) and their [rules](../../product/pause-sets-decisions.md),
  [availability and limits](../../product/limits-and-platforms.md), [showcase media](../../../video/README.md),
  [store listing](../../store/en-US/listing.md), and the
  [`DOCS-003` brief](docs-003-release-1-2-media.md) (the recipe this row repeats).

## Outcome

When the maintainer gives the go for 1.3, `RELEASE-005` can publish without
writing or capturing anything: the README, `posato.app`, the showcase media,
the App Store subtitle, description, What's New, and screenshots, and the
GitHub release notes describe Posato 1.3 from real captures of the 1.3
applications.

## Boundaries

- **Prepare, never publish.** Merging deploys `posato.app` (Cloudflare Pages
  builds `website/` from `main`), so this pull request merges only with the
  release go, as `DOCS-003` did. No App Store Connect change or GitHub Release
  edit.
- **Captures.** Real applications driven by `posato-control` without the
  maintainer: the Mac in a Tart VM, the iPhone on the test iPhone, store
  screenshots on the Simulators named in the listing recipe. Synthetic content
  only (`example.com`, `example.net`, neutral set and schedule names, built-in
  applications); no account, device name, identifier, or home path.
- **Story.** Keep the `WEB-002` homepage, hero, and line "A little space. For
  what matters."; recapture the hero and walkthrough so they show pause sets
  (**Pause sets** destination, a set chosen for a session and a schedule)
  instead of the 1.2 **Paused items**, within the `DOCS-002` budgets.
- **Copy.** The App Store subtitle takes the new line. The 1.3 text covers
  pause sets, Intel Macs on macOS 13 (verified under Rosetta, as the
  availability page states), back gestures, and the fixes; the release notes
  say that devices still on 1.2 must update to keep syncing. Clarity review,
  no em or en dashes, no claim beyond verified behavior and stated limits.
- **Privacy policy.** Apply the `PRIVACY.md` edits proposed in the
  [pause set rules](../../product/pause-sets-decisions.md#privacy) (on-device
  set data, sync history, iCloud contents, and the corrected iPhone backup
  sentence) for the maintainer's acceptance; `PRIVACY.md` publishes to
  `posato.app` with this merge.
- **Non-goals:** product or in-app copy, the Polish listing (`I18N-001`), and
  new site pages.

## Acceptance

- `AC-01` — The hero and walkthrough show pause sets with visible clicks or
  taps and callouts, and the storyboard test passes.
- `AC-02` — `npm run media` reproduces every media output from tracked
  captures of one recorded product revision, and `npm run verify` passes.
- `AC-03` — The README and `posato.app` describe 1.3, including both Mac
  downloads, with working links, and the site builds.
- `AC-04` — The store listing has the new subtitle, the 1.3 description and
  What's New, and iPhone 6.9-inch and iPad 13-inch screenshot sets, ready for
  `posato-provisioning store prepare`.
- `AC-05` — The GitHub release notes for 1.3 are ready in the execution
  record, and `RELEASE-005` points to them.
- `AC-06` — `PRIVACY.md` states the pause set data as proposed, and the
  maintainer accepts it.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- `video/`: clean `npm ci`, `npm run media`, `npm run verify`; contact sheet
  review.
- `website/`: build and a check at phone and wide widths, with reduced motion.
- Capture runs cited by their ignored run directories; a privacy scan of every
  tracked PNG.

## Decisions or blockers

- **Maintainer:** acceptance of the `PRIVACY.md` edits, and the release go,
  which also merges this pull request and deploys the site.
