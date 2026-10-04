# `RELEASE-005`: Verify the 1.3.0 candidates and publish Posato 1.3

- **Review tier:** `high-risk`
- **Tier reason:** Signed artifacts, two stable update feeds, and an App
  Review submission are release operations that cannot be taken back once
  people install them. Release 1.3 is the first with an x86-64 build and a
  second feed, and it migrates every installation to pause sets.
- **Dependencies:** every 1.3 row: `MACOS-022`, `SCHEDULE-005`, `SYNC-020`,
  `MACOS-020`, `MACOS-021`, `MACOS-024`, `MACOS-015`, `NAV-001`,
  `SCHEDULE-004`, `SCHEDULE-006`, `NAV-002`, `SESSION-006`, `WEB-002`
  (merged), and `DOCS-004` (#131), which holds the media, pages, store text
  and screenshots, and the release notes. Release 1.3, wave R1.3/W6.
- **Integration group:** `PR-RELEASE-1-3`, milestone `1.3.0`.
- **Authority:** [release roadmap](../release-roadmap.md) revision 17, row
  `RELEASE-005`; the [RELEASE-004 record](../executions/release-004-release-1-2.md)
  (the proven route); [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md)
  and [ADR 0008](../../decisions/0008-macos-update-delivery.md) with their
  `MACOS-015` amendments; [Apple provisioning](../../development/apple-provisioning.md);
  the [pause set rules](../../product/pause-sets-decisions.md); the
  [threat model](../../security/apple-mvp-threat-model.md). The maintainer
  gave the publication go on 2026-10-04 ("GO GO GO").

## Outcome

For one named source revision R that contains every 1.3 row, the notarized
arm64 and x86-64 macOS candidates and the iOS candidate pass the release
checks and unattended verification. Then Posato 1.3.0 is published: tag
`v1.3.0`; a GitHub Release with both DMGs, `appcast.xml`,
`appcast-intel.xml`, and one `SHA256SUMS`; the in-app update from 1.2.0;
release notes; the `DOCS-004` pages; and the iOS build submitted to App
Review.

## Boundaries

- **Who does what.** The agent does every step it can through the
  repository, `gh`, `tools/posato-provisioning`, and `posato-control`. The
  maintainer answers the Keychain prompts for the release key (and the
  Developer ID key if macOS asks) and anything App Store Connect's API
  cannot do.
- **Version.** `MARKETING_VERSION = 1.3.0` for both applications. macOS
  build 28 for both architectures: the published stable feed holds 27, and
  no build at or above 28 was signed with the release key (the `MACOS-015`
  candidates 9001 to 9003 ran only in destroyed VM clones). iOS build 6
  (`store status`: next free).
- **Intel.** The x86-64 release build refuses to open under Rosetta, so its
  function is verified on a verification build of R made with
  `-PposatoMacOsAllowRosetta=true` in the `ventura` guest, and the release
  DMG itself through its package checks, Gatekeeper, and the refusal.
- **Update path.** A 1.2.0 installation must find 1.3.0 on the stable feed,
  install it, and migrate to a first pause set holding its websites, its
  schedules, and its session. The in-app update runs right after
  publication, because 1.2.0 reads only the stable feed.
- **Non-goals:** new features; a defect found here gets its own row unless
  the maintainer decides to fix it in this release.

## Acceptance

- `AC-01` — Both macOS candidates and the iOS candidate come from R and
  pass the release checks: clean-clone `quality`, Developer ID signature,
  notarization, stapling, Gatekeeper, two validated feeds with one build
  number, and TestFlight processing with release entitlements.
- `AC-02` — The arm64 candidate passes a fresh install with the unified
  setup, a manual pause with a pause set, and a schedule that starts and
  ends on its own on macOS 26 and macOS 15; a 1.2.0 installation replaced by
  it keeps its websites and schedules in a first set and still blocks. The
  x86-64 verification build passes setup and a blocking pause on macOS 13
  under Rosetta, and the x86-64 release DMG shows the refusal there. The
  same revision passes pause sets, a manual pause, and a scheduled start on
  the test iPhone. After publication, 1.2.0 updates in the app to 1.3.0.
- `AC-03` — Tag `v1.3.0` and its GitHub Release exist and are latest; the
  downloaded assets match the verified outputs; both public feeds verify.
- `AC-04` — `posato.app`, the README, the availability page, and
  `PRIVACY.md` describe 1.3 from `DOCS-004`; the iOS build is submitted to
  App Review with the 1.3 description, What's New, screenshots, and
  subtitle.
- `AC-05` — The execution record holds a dated verdict for R, the asset
  checksums, the consumed build numbers, and the `TB-08`/`T-13` review of
  the release artifacts.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Independent plan review before the version bump is built; one
  completed-change review before tagging.
- `vm install --dmg` on the `primary`, `legacy`, and `ventura` lines, the
  1.2.0 replacement, the in-app update from 1.2.0, and the test iPhone.
  Evidence stays in ignored `build/verification/`.
- After publication, download and verify the assets and both public feeds,
  and check the README and site pages.

## Decisions or blockers

- **Blocker (maintainer):** the Keychain prompts during signing.
- **Open:** the App Store subtitle "Space for what matters." is set in App
  Information, which `store prepare` does not change.
