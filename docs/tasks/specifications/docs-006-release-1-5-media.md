# `DOCS-006`: Prepare the public packaging for Posato 1.5

- **Review tier:** `standard`
- **Tier reason:** The limits page, README, site, store text, and release
  notes change no product behavior, but they make the first public claims
  about Linux, Android, and folder sync, including install steps and the
  ADR 0010 limits; one independent completed-change review covers claim
  accuracy, the moved limits, synthetic captures, and links.
- **Dependencies:** `ANDROID-001`, `SESSION-010`, `TARGETS-010`,
  `TARGETS-011`, `MACOS-028`, `SYNC-023`, and `TARGETS-012` are merged, so
  the captures show the final 1.5 interface. `RELEASE-007` publishes what
  this row prepares.
- **Integration group:** `PR-RELEASE-1-5-MEDIA`, roadmap revision 24, wave
  R1.5/W5.
- **Authority:** [release roadmap](../release-roadmap.md),
  [ADR 0010](../../decisions/0010-linux-android-and-folder-workspace.md)
  ("Limits to publish", accepted 2026-10-20), [DESIGN.md](../../../DESIGN.md),
  [availability and limits](../../product/limits-and-platforms.md),
  [store listing](../../store/en-US/listing.md), and the
  [`DOCS-005` brief](docs-005-release-1-4-media.md) (the recipe this row repeats).
- **Record:** [docs-006-release-1-5-media.md](../executions/docs-006-release-1-5-media.md)

## Outcome

When the maintainer gives the go for 1.5, `RELEASE-007` can publish without
writing or capturing anything: the limits page, the README, `posato.app`,
the App Store description and What's New, and the GitHub release notes
describe Posato 1.5, Linux, Android, and folder sync, with install steps for
the `.deb` and the APK and real Linux and Android screenshots. Amended
2026-10-21 (`user-confirmed`): Linux and Android stand level with the Mac and
iPhone everywhere, from the download buttons to the hero video, and the
privacy policy covers 1.5.

## Boundaries

- **Prepare, never publish.** Merging deploys `posato.app`, so this pull
  request merges only with the release go, as `DOCS-005` did. No App Store
  Connect change or GitHub Release edit. Amended 2026-10-22
  (`user-confirmed`): the pull request merges before the release go, so
  `posato.app` shows the 1.5 copy before `RELEASE-007` publishes the Linux
  and Android files.
- **Limits.** ADR 0010's "Limits to publish" text moves to the limits page
  unchanged in meaning; the page marks Linux and Android available and keeps
  Windows planned. The ADR then points to the page.
- **Captures.** Real applications driven by `posato-control` without the
  maintainer: Linux in a Tart Ubuntu clone, Android on the Posato emulator or
  the test phone, and fresh Mac and iPhone captures of the 1.5 interface.
  Synthetic content only; no account, device name, identifier, personal
  folder path, live pairing code, or home path. A synthetic sync folder and
  a pairing code that expired with its workspace may appear.
- **Copy.** The 1.5 text covers Linux and Android, folder sync, joining by
  pairing code, QR code, or link, and the smaller 1.5 changes. The App Store
  text names no other mobile platform. Clarity review, no em or en dashes, no
  claim beyond verified behavior and stated limits.
- **Non-goals:** product or in-app copy, the Polish listing (`I18N-001`), new
  App Store screenshots (the iPhone and iPad screens they show did not
  change), and the platform matrix, which `RELEASE-007` updates from its own
  checks.

## Acceptance

- `AC-01`: the limits page lists Linux and Android as supported with their
  versions and channel, carries every ADR 0010 limit, and keeps only Windows
  under Planned platforms.
- `AC-02`: the README and `posato.app` describe 1.5, Linux, Android, and
  folder sync, with install steps for the `.deb` and the APK and working
  links, and the site builds.
- `AC-03`: the README and `posato.app` show real Linux and Android
  screenshots with synthetic content. Amended 2026-10-22 (`user-confirmed`):
  real light Android screenshots; Linux appears in the hero's device row
  and, light and dark, in the pull request.
- `AC-04`: the store listing has the 1.5 description and What's New, ready
  for `posato-provisioning store prepare`.
- `AC-05`: the update feed notes and the GitHub release notes for 1.5 are
  ready, the latter in the execution record.
- `AC-06` (2026-10-21): the README and `posato.app` give the four platforms
  equal download buttons and install steps; each "How it works" step pairs
  a computer with a phone so every platform appears; the hero video, the
  README GIF, and the social preview name and show all four.
  Amended 2026-10-22 (`user-confirmed`): every capture is light, each step
  pairs the Mac with a phone, and the hero shows Linux and Android as
  outlines in its device row; the social preview names all four.
- `AC-07` (2026-10-21): `PRIVACY.md` covers folder sync, the key file, the
  pairing code, and blocking on Linux and Android, from the code.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- `website/`: build and a check at phone and wide widths.
- Linux and Android capture runs cited by their ignored run directories; a
  privacy scan of every new tracked PNG.
- `./gradlew qualityLint` for the changed Markdown.

## Decisions or blockers

- **Maintainer:** the release go. Resolved 2026-10-22 (`user-confirmed`):
  merging and the site deploy go ahead before it.
