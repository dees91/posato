# Execution: `RELEASE-005`

- **Brief:** [Verify the 1.3.0 candidates and publish Posato 1.3](../specifications/release-005-release-1-3.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent plan review; pending
- **Branch:** `docs/release-005-release-1-3`
- **Updated:** 2026-10-04

## Plan

Revision 2 after the plan review. The `RELEASE-004` route, with the 1.3
differences marked.

1. **Plan review:** revision 1 `changes-required`; revision 2 below.
2. **Tooling, not in R, with its own Standard review:** `posato-provisioning
   store prepare --subtitle <text>` sets the en-US subtitle in the App
   Information that the version being prepared brings (maintainer decision
   2026-10-04). Done in `7daa13b`; review below.
3. **Release commit R:** `MARKETING_VERSION = 1.3.0` and the signed-feed
   notes `docs/releases/1.3.0-appcast-notes.txt`, one file for both feeds.
   README, site, store, and `PRIVACY.md` text come from `DOCS-004` (#131),
   not R. R is pinned by its full hash.
4. **Candidates from a clean clone of R:**
   - clean-clone `./gradlew quality`;
   - **arm64 release:** `generateMacOsUpdateFeed`, release channel, build 28
     (the floor is 27: the arm64 feed holds 27 and no Intel feed exists),
     Developer ID identity and the sync Developer ID profile; then the
     **x86-64 release** with `-PposatoMacOsArchitecture=x86_64` in the same
     clone, which requires the arm64 feed beside it with build 28. One bold
     Keychain request per prompt. Right after each run, both
     `release-feed/<arch>` directories are copied out and hashed. The two
     `SHA256SUMS` are merged and checked with `shasum -a 256 -c` against
     both DMGs; each feed's enclosure length equals its DMG.
   - **x86-64 verification candidate**, in a second clean clone of R so it
     cannot touch the release outputs: notarized, candidate channel,
     `-PposatoMacOsArchitecture=x86_64 -PposatoMacOsAllowRosetta=true`, a
     test feed URL and a throwaway test key, no feed generated, never the
     release key (the `MACOS-015` route). Its Info.plist differs from the
     release build's only in the Rosetta switch and the feed and key.
   - **x86-64 release DMG checks on the host:** every Mach-O is x86-64
     only; no Rosetta switch; `SUFeedURL` ends in `appcast-intel.xml`;
     `LSMinimumSystemVersion` 13.0; the Temurin runtime version equals the
     arm64 build's; entitlements equal the arm64 build's; strict deep
     `codesign`, `stapler validate`, and `spctl` pass.
   - **iOS:** archive, export, and upload as build 6; `VALID`; inspect the
     IPA's signature, entitlements, and privacy manifests.
   4b. **iOS store record before the sitting**, from #131's files at a pinned
   head: `store prepare --version 1.3.0 --build 6 --whats-new
   docs/store/en-US/whats-new-1.3.0.txt --description
   docs/store/en-US/description.txt --screenshots docs/store/en-US/screenshots
   --subtitle "Space for what matters." --release after-approval`; a rerun
   that reports every item `unchanged` and `store status --version 1.3.0`
   are the evidence. Nothing is submitted.
5. **Unattended verification:**
   - **macOS 26 `primary`, fresh, notarized arm64 DMG:** unified setup, a
     second pause set, a manual pause with it whose Session summary shows
     that set's counts, the Automation consent and the pause page in Safari
     and Chrome (`MACOS-022` on a release-signed build), a schedule 3
     minutes ahead with the window closed that starts and ends on its own,
     back with Command-[, `observe`.
   - **macOS 15 `legacy`, fresh, arm64 DMG:** the core flow plus a login
     launch (`vm shutdown`, `vm boot`) with a scheduled start.
   - **Upgrade from 1.2.0 on `primary`:** the `v1.2.0` DMG set up with the
     helper, two websites, an application, and a schedule; `vm install
     --replace` with the 1.3.0 DMG: one first set, the default, holds the
     websites and the application; the schedule uses it; Start asks for no
     password and blocks; the schedule still starts. A session running
     during an upgrade is covered by `SCHEDULE-004` AC-02.
   - **macOS 13 `ventura` (arm64 guest):** the x86-64 release DMG installs,
     Gatekeeper accepts it, it shows "This version is for Intel Macs", and
     after Quit no process, daemon, or data is left. The x86-64 verification
     candidate completes setup, a manual pause that `observe` reports
     blocked, and a scheduled start.
   - **Test iPhone:** first the 1.2 upgrade: a `v1.2.0` development build
     with consent, an application, a website, and two overlapping
     schedules; R's build installed and not opened: the scheduled start
     still blocks and the earlier schedule's end does not release the later
     one; then Posato opened: the first set holds the old items. Then on
     R's build: pause sets, a manual pause with the shield and "Website Not
     Allowed", the `NAV-002` path (Pause sets, a set, Back, Schedules), and
     the edge swipe.
   - **Not repeated:** the linked pair; the maintainer verified sync on the
     test account (`user-confirmed`, 2026-10-04), and `SCHEDULE-004` AC-02
     covers it.
   - A `peer` clone with 1.2.0 set up (helper, websites, an application, a
     schedule, update consent answered) is kept for the in-app update.
6. **Completed-change review** of R, the candidates, and the evidence.
7. **Publication sitting** (go given 2026-10-04):
   1. Set `PRIVACY.md`'s effective date to the publication date on #131,
      rebase it onto `main`, and merge it (squash); then rebase this branch
      and merge it.
   2. Tag `v1.3.0` on the final `main` head only when `git diff --name-only
      R HEAD` lists nothing outside `docs/`, `website/`, `video/`,
      `.github/`, `README.md`, `PRIVACY.md`, `tools/posato-control/`, and
      `tools/posato-provisioning/`; `LICENSE`, `NOTICE`,
      `THIRD_PARTY_NOTICES.md`, and `Version.xcconfig` must equal R.
   3. Draft release with exactly the five assets, byte comparison with step
      4, publication as latest with the `DOCS-004` release notes.
   4. `releases/latest/download/appcast.xml` and `appcast-intel.xml` resolve
      and verify. **In-app update** on the prepared `peer` clone: About,
      Check for Updates, install, `launch --adopt`; the first set holds the
      websites and the application, the schedule uses it, the helper is
      ready, `observe` blocks. Any loss or failure is a failure.
      **Rollback:** `gh release edit v1.2.0 --latest` and `v1.3.0` stays
      published but not latest; hold iOS; revert the #131 commit on `main`.
      `appcast-intel.xml` then answers 404 until a fixed release, an
      accepted exception to ADR 0008 (`user-confirmed`, 2026-10-04: no
      Intel installation exists yet). A fix build passes
      `-PposatoMacOsPreviousBuildNumber=28`.
   5. iOS: `store prepare` rerun if #131's store files changed on `main`,
      then `store submit --version 1.3.0`.
   6. Site and README check, Projects Done, milestone `1.3.0` closed.
8. A follow-up PR records the App Review outcome.

**Maintainer steps:** the Keychain prompts in step 4, and after
publication the upload of `.github/assets/social-preview.png` in the
repository settings.

**Maintainer decisions (`user-confirmed`, 2026-10-04):** the pause set
migration gate is met by the `SCHEDULE-004` AC-02 and AC-03 runs and the
migration tests; the rollback exception above; the linked pair is not
repeated; the GitHub notes keep "Posato 1.1 and later" without a 1.1 run.

**Recorded facts:** iOS 26 is the only iOS version verified for 1.3; the
CloudKit schema is unchanged since `v1.2.0`. App Store devices on 1.2 stop
syncing with a 1.3 Mac as soon as it publishes a set, and resume when iOS
1.3 is approved; the pause set rules accept this.

## High-risk plan review

- **Revision 1:** `changes-required`, five Required findings: the Intel
  verification build route; `MACOS-022` on a release-signed build and the
  entitlement deltas since `v1.2.0` in the `TB-08` review; the pause set
  migration gate; the iPhone upgrade from 1.2 and the `NAV-002` path on R;
  the rollback with two feeds. All are folded into revision 2, with the
  maintainer's decisions; most Recommended findings are taken too.

## Tooling review

`store prepare --subtitle` (`7daa13b`): Standard review, nothing Critical
or Required. Taken: the failure hint now says that earlier steps of the run
may already have been written and a rerun is safe; the evidence of the
subtitle is a rerun reporting `unchanged`.
