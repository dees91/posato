# `DOCS-007`: Prepare the public packaging for Posato 1.6

- **Review tier:** `standard`
- **Tier reason:** The limits page, README, site, privacy policy, store text,
  and release notes change no product behavior, but they make the first
  public claims about Windows and the 1.6 pause features in six languages;
  one independent completed-change review covers claim accuracy, the moved
  Windows limits, translations, synthetic captures, and links.
- **Dependencies:** `ONBOARDING-005`, `SCHEDULE-007`, `TARGETS-013`,
  `PLATFORM-002`, `I18N-001`, and `I18N-002` are merged, so the captures show
  the final 1.6 interface. `RELEASE-010` publishes the files this row links.
- **Integration group:** `PR-RELEASE-1-6-MEDIA`, wave R1.6/W3.
- **Authority:** [release roadmap](../release-roadmap.md),
  [ADR 0010](../../decisions/0010-linux-android-and-folder-workspace.md),
  the [`PLATFORM-002` record](../executions/platform-002-windows.md) (draft
  Windows limits), [DESIGN.md](../../../DESIGN.md),
  [availability and limits](../../product/limits-and-platforms.md),
  [`PRIVACY.md`](../../../PRIVACY.md), the
  [localization guide](../../development/localization.md), and the
  [`DOCS-006` brief](docs-006-release-1-5-media.md) (the recipe this row repeats).
- **Record:** [docs-007-release-1-6-media.md](../executions/docs-007-release-1-6-media.md)

## Outcome

`RELEASE-010` can publish 1.6 without writing or capturing anything: the
limits page, the README, `posato.app`, the privacy policy, the App Store
description and What's New, the update feed notes, and the GitHub release
notes describe Posato 1.6, Windows, a break in a scheduled pause, and a
website opened briefly during a pause, in every shipped language, with
Windows install steps and real Windows screenshots.

## Boundaries

- **Merge.** `user-confirmed` (2026-11-04): the pull request goes to
  `babysit-pr` and merges when reviewed, before `RELEASE-010`; until then the
  Windows download links lead to the latest release, which has no MSI yet.
  The owner chose this knowing that `posato.app` then shows the whole 1.6
  copy before the release, as in 1.5: the 1.6 features, the 1.6.0 file
  names, and the Availability text. `PRIVACY.md` takes the merge date as its
  effective date in every language, because the changed policy goes live
  with the merge.
- **Limits.** The draft Windows limits in the `PLATFORM-002` record and its
  pending Windows details are `user-confirmed` (2026-11-04): the details
  enter ADR 0010, the limits move to the limits page unchanged in meaning,
  Windows becomes a supported platform, and ADR 0010 points to the page.
- **Copy.** Windows stands level with the other four platforms: a download
  button and install steps for the MSI (arm64 and x64) on the site and in
  the README, and the hero names five platforms. The 1.6 features are
  described within their stated limits. Every site page and store listing
  changes in all six shipped languages, following the localization guide;
  the App Store text names no other mobile platform. Clarity review, no em
  or en dashes in public copy.
- **Privacy.** `PRIVACY.md` and its five translations cover blocking on
  Windows and Posato's data on Windows, from the code.
- **Media.** `user-confirmed` (2026-11-04): only new things. Real Windows
  screenshots from a Tart Windows guest, stills of a break and of a website
  that opens briefly, Windows in the hero's device row, and a social preview
  naming five platforms. Existing stills stay where their screen did not
  change. Synthetic content only, as in `DOCS-006`.
- **Provenance cleanups** (`user-confirmed` 2026-11-04): the `SCHEDULE-007`
  manual-session-during-a-break rule and the `TARGETS-013` ADR 0004 note lose
  their "pending the owner's confirmation" wording.
- **Non-goals:** product or in-app copy, new App Store screenshots, and the
  verified platform matrix, which `RELEASE-010` updates from its own checks.

## Acceptance

- `AC-01`: the limits page lists Windows 11 (arm64 and x64) as supported,
  carries the Windows limits, and no longer plans Windows; ADR 0010 holds the
  confirmed Windows details and points to the page.
- `AC-02`: the README and `posato.app` in every language describe 1.6 and
  Windows, with an equal Windows download button and MSI install steps and
  working links, and the site builds.
- `AC-03`: the README and `posato.app` show real Windows screenshots and the
  two new features with synthetic content; the hero and social preview name
  five platforms.
- `AC-04`: the store listings in every language have the 1.6 description and
  What's New, ready for `posato-provisioning store prepare`.
- `AC-05`: the update feed notes and the GitHub release notes for 1.6 are
  ready, the latter in the execution record.
- `AC-06`: `PRIVACY.md` and its translations cover Windows.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- `website/`: build and a check at phone and wide widths; `video/`
  `npm run media`.
- Capture runs cited by their ignored run directories; a privacy scan of
  every new tracked PNG.
- `./gradlew qualityLint`, `verifyEnglishText`, and `verifyLocaleLists`.

## Decisions or blockers

- None open; the owner answered the merge, media, Windows, and
  `SCHEDULE-007` questions on 2026-11-04.
