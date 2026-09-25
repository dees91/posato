# Execution: `RELEASE-003`

- **Brief:** [Verify the 1.1.0 candidates and publish Posato 1.1](../specifications/release-003-release-1-1.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent plan review (approved); completed-change review pending
- **Branch:** `feature/release-003-release-1-1`
- **Updated:** 2026-09-25

## Plan

1. Driver: `vm install --dmg --replace` moves an installed older build to the
   guest Trash and installs the candidate as a person would; own review and a
   VM run (public 1.0.0, then notarized build 25 over it).
2. Release commit: `MARKETING_VERSION = 1.1.0`; the ADR 0008 privacy and
   availability passages reconciled with the `MACOS-011` measurements and dated
   for the publication day; README, website availability and privacy summary;
   release notes and the App Store "What's New" text (not tracked).
3. Candidates from R (branch head after steps 1-2): clean-clone `quality`;
   macOS build 26 through `generateMacOsUpdateFeed` on the release channel with
   previous build 25 (Keychain prompt); iOS build above the highest in App
   Store Connect, archived, inspected, uploaded, `VALID` in TestFlight.
4. Unattended verification: macOS 26 VM from 1.0.0 to 1.1.0 with `--replace`
   and preserved state, consent, core flow; macOS 15 VM fresh install and core
   flow; the same revision on the test iPhone; store screenshots against the
   1.1 UI. iOS 18: the maintainer checks the TestFlight build (D1).
5. One completed-change review, closeout of this record, `gh pr ready 84`; no
   hosted review (version string and documentation; the driver change has its
   own local review). The maintainer merges.
6. Publication right after the merge: tag `v1.1.0` on the squash commit only if
   its product tree equals R, draft release with the three assets, byte-for-byte
   check of the downloads, publication as latest, public feed check, site check,
   App Store version 1.1.0 with automatic release (D2) submitted. Projects Done
   and milestone `1.1.0` closed at publication.
7. Follow-up PR after App Review: outcome, ADR 0008 status, wiki.

## High-risk plan review

- **Verdict:** approved on revision 2.
- **Critical or Required findings:** R1 `vm install` refuses an installed
  build, so 1.0.0 to 1.1.0 could not be driven; R2 build 25 was already
  notarized by `QUALITY-007`; R3 tagging R off `main` after a squash merge.
- **Resolution:** step 1 adds `--replace`; build 26 with previous 25; tag only
  the squash commit when its product tree equals R, otherwise stop.

## Result

- **Revision R** = `0e12347` (version 1.1.0 and the updater wording). Later
  commits change only documentation, the website, the store assets, and the
  verification driver, none of which ships in an artifact; the tag rule
  therefore also excludes `tools/posato-control` (deviation from plan step 6).
- **macOS 1.1.0 (26)** from a clean clone of R through
  `generateMacOsUpdateFeed` on the release channel, previous build 25:
  `Posato-1.1.0.dmg` SHA-256
  `2a0a1da00b0f58f67922409d5d43488a4a8ffc0364909f62ca3903c3085f72ab`,
  signed `appcast.xml` (one item, build 26, macOS 15 and arm64, enclosure
  `releases/download/v1.1.0/Posato-1.1.0.dmg`), `SHA256SUMS`. Consumed
  macOS build numbers: 8-19 and 21-26; 20 was never built.
- **iOS 1.1.0 (4)** archived from R, exported for App Store Connect, uploaded,
  `VALID`, in internal TestFlight.
- **Driver.** `vm install --replace` moves an installed release to the Trash
  first; `vm install` terminates a candidate whose update-consent sheet
  refuses the first quit and records `firstOpenQuit`.
- **Public wording.** `PRIVACY.md` gains Updates on Mac and the reconciled
  opening and Diagnostics passages (effective 26 September 2026); the
  availability page, README, and website describe 1.1 and the manual move
  from 1.0. The store screenshots were recaptured (the website list now names
  the `www` variant; About shows 1.1.0) and the What's New text is in the
  listing.
- **Defect found (`IOS-006`).** On the test iPhone, relaunching Posato during
  a session can end it as expired and lift its restrictions (4 of 6
  relaunches; always with a fast XCUITest relaunch). Code analysis: the path
  is unchanged since 1.0.0, so 1.1 does not introduce it. `user-confirmed`,
  2026-09-25: publish 1.1 with the limit stated on the availability page and
  fix it in release 1.2 (roadmap revision 8), with no patch release.
- **TB-08 / T-13.** Sparkle 2.10.0 pinned; the feed and archive signatures
  and the enclosure were validated against the DMG before publication; the
  release key stayed in the maintainer's Keychain and was used through one
  Keychain prompt; the application embeds only the stable feed and the
  tracked key.

## Completed-change review

- **Verdict:** pending

## Verification

Evidence: ignored `build/verification/release-003/` (run groups 01-12).

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality`, worktree and clean clone of R | pass | clean clone 5 min 51 s |
| `:posato-control:check` after each driver change | pass | unit tests for `requireInstallable` |
| macOS artifacts | pass | `codesign --deep --strict`, `stapler`, `spctl` (Notarized Developer ID), Info.plist feed, key, version, build; entitlements app `allow-jit` only, helper none |
| iOS artifacts | pass | Apple Distribution, Family Controls, CloudKit Production, app group, `get-task-allow` false, privacy manifests, `altool` validate and upload |
| macOS 26 VM: 1.0.0 then 1.1.0 with `--replace` | pass | website, application, iCloud workspace kept; consent sheet; About Updates; manual check before publication reports an update error and changes nothing |
| macOS 26 VM: session | pass | blocked, relaunch and Resume, early end, 5-minute expiry; `observe` blocked and allowed each time |
| macOS 15 VM: fresh 1.1.0 | pass | onboarding with iCloud received a website from the macOS 26 install; session blocked, reboot and Resume, early end, allowed |
| Test iPhone (iOS 26.5), dev-signed R | pass with `IOS-006` | start, Calculator shield, Safari "Website Not Allowed", early end, unblocked; relaunch defect above |
| Store screenshots | refreshed | iPhone 6.9-inch and iPad 13-inch sets, 1.1 UI |
| iOS 18 (D1) | pending | maintainer check of TestFlight build 4 |

## Blockers and accepted risks

- 1.0 has no updater: the move to 1.1 is a manual download, as stated.
- `IOS-006` is published as a limit until release 1.2.
- Observations, not defects of this release: the first iCloud sync in a
  fresh guest fails once and succeeds on retry (as in `MACOS-011`); a device
  that receives a synced early end during onboarding shows "Restrictions may
  still apply", cleared by Retry; one guest agent lost its host connection
  during a session and recovered after a guest reboot; a clone that waited
  for a key under a broken iCloud Keychain adopted a stale workspace key.
- Environment repaired: the primary golden VM's iCloud Keychain (Resume Data
  Sync); stale test-account workspaces removed through the app.

## Final

- **Status:** `active`
- **Outcome:** pending
