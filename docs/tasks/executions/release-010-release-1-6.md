# Execution: `RELEASE-010`

- **Brief:** [Verify and publish Posato 1.6.0](../specifications/release-010-release-1-6.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent agents (plan and completed change)
- **Branch:** `release-010-release-1-6`
- **Updated:** 2026-11-04

## Plan

`releasing.md` steps 1 to 9 with the brief's differences:

1. **R:** `MARKETING_VERSION = 1.6.0`; Android `versionCode` 2 and
   `versionName` 1.6.0; the SmartScreen lines for the unsigned MSI on the
   limits page, the README, the site in six languages, and the release
   notes; the Windows signing backlog row (roadmap revision 39). The
   appcast notes are on `main` from `DOCS-007`. `quality iosSwiftTest` in a
   clean clone of R.
2. **Candidates from a clean clone of R:** arm64 and x86-64 release feeds
   with build 33; the x86-64 verification candidate (throwaway key,
   loopback feed); host checks; iOS archive and upload; `store prepare` for
   six locales; the signed APK; both `.deb` files from the workflow; the
   x64 MSI from the `Windows package` workflow on R; the arm64 MSI built in
   a Windows clone from R. Hashes into this record.
3. **Matrix** (reduced): `primary` fresh DMG (setup, manual pause, a
   schedule with the window closed, a break, a website that opens briefly,
   `observe`); `ventura`; the test iPhone with R (core flow, a pause whose
   items change while it runs, an end while Posato is closed, a break); the
   Linux clone with the `.deb` (same row checks plus `/etc/hosts`); the
   Android phone with the APK (same row checks); a Windows clone with the
   arm64 MSI (install, onboarding, a pause that blocks a website and ends an
   app, early end, uninstall, SmartScreen not driven); folder sync chain
   Linux, Android, Windows, Mac; a kept `peer` clone on 1.5.2.
4. Completed-change review, then merge, tag, GitHub Release with eleven
   assets, the in-app update on `peer`, `store submit`, public checks,
   Projects, milestone, closeout after App Review.

## Result

- Pending.
