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

Stops before: feed signing, iOS upload, tag push, release publication, App
Store version creation, and submission.

## High-risk plan review

- **Verdict:** approved on revision 2.
- **Critical or Required findings:** R1 `vm install` refuses an installed
  build, so 1.0.0 to 1.1.0 could not be driven; R2 build 25 was already
  notarized by `QUALITY-007`; R3 tagging R off `main` after a squash merge.
- **Resolution:** step 1 adds `--replace`; build 26 with previous 25; tag only
  the squash commit when its product tree equals R, otherwise stop.

## Result

- Pending.

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |

## Blockers and accepted risks

- 1.0 has no updater: moving to 1.1 is a manual download, stated in the
  availability text and the release notes.

## Final

- **Status:** `active`
- **Outcome:** pending
