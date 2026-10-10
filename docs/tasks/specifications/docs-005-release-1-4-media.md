# `DOCS-005`: Prepare the public packaging for Posato 1.4

- **Review tier:** `standard`
- **Tier reason:** Public media, README, site, and store text change no product
  behavior, but they make claims about the new design, longer quick choices,
  and the Mac fixes that must match verified 1.4 behavior; one independent
  completed-change review covers claim accuracy, synthetic captures,
  reproducible renders, and budgets.
- **Dependencies:** every other release 1.4 row is merged: `DESIGN-004`,
  `IOS-007`, `MACOS-026`, `SESSION-007`, `MACOS-027`, and `SYNC-021`.
  `RELEASE-006` publishes what this row prepares.
- **Integration group:** `PR-RELEASE-1-4-MEDIA`, roadmap revision 20, wave
  R1.4/W2.
- **Authority:** [release roadmap](../release-roadmap.md), [DESIGN.md](../../../DESIGN.md),
  [availability and limits](../../product/limits-and-platforms.md), [showcase media](../../../video/README.md),
  [store listing](../../store/en-US/listing.md), and the
  [`DOCS-004` brief](docs-004-release-1-3-media.md) (the recipe this row repeats).

## Outcome

When the maintainer gives the go for 1.4, `RELEASE-006` can publish without
writing or capturing anything: the README, `posato.app`, the showcase media,
the App Store description, What's New, and screenshots, and the GitHub
release notes describe Posato 1.4 from real captures of the 1.4 applications.

## Boundaries

- **Prepare, never publish.** Merging deploys `posato.app`, so this pull
  request merges only with the release go, as `DOCS-004` did. No App Store
  Connect change or GitHub Release edit.
- **Captures.** Real applications driven by `posato-control` without the
  maintainer: the Mac in a Tart VM, the iPhone on the test iPhone, store
  screenshots on the Simulators named in the listing recipe. Synthetic content
  only; no account, device name, identifier, or home path.
- **Story.** Keep the `WEB-002` homepage, hero, and product line, and the
  storyboard's scenes; recapture every frame on the `DESIGN-004` interface and
  the `SESSION-007` duration choices, within the `DOCS-002` budgets.
- **Copy.** The 1.4 text covers the native design on iPhone and iPad, the
  longer quick choices and **Until end of day**, and the Mac fixes
  (`MACOS-026`, `MACOS-027`, `SYNC-021`). The release notes carry the
  `MACOS-026` one-time limit. Clarity review, no em or en dashes, no claim
  beyond verified behavior and stated limits.
- **Non-goals:** product or in-app copy, the Polish listing (`I18N-001`), new
  site pages, and the platform matrix, which `RELEASE-006` updates from its
  own checks.

## Acceptance

- `AC-01`: the hero and walkthrough show the 1.4 interface with visible clicks
  or taps and callouts, and the storyboard test passes.
- `AC-02`: `npm run media` reproduces every media output from tracked
  captures of one recorded product revision, and `npm run verify` passes.
- `AC-03`: the README and `posato.app` describe 1.4, including both Mac
  downloads, with working links, and the site builds.
- `AC-04`: the store listing has the 1.4 description and What's New and iPhone
  6.9-inch and iPad 13-inch screenshot sets, ready for
  `posato-provisioning store prepare`.
- `AC-05`: the GitHub release notes for 1.4 are ready in the execution record,
  including the `MACOS-026` one-time limit.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- `video/`: clean `npm ci`, `npm run media`, `npm run verify`; contact sheet
  review and `npm run compare:hero`.
- `website/`: build and a check at phone and wide widths.
- Capture runs cited by their ignored run directories; a privacy scan of every
  tracked PNG.

## Decisions or blockers

- **Maintainer:** the release go, which also merges this pull request and
  deploys the site.
