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

## Result

- **Revision R** = `3090752bf064a036438894dae06bb2951a322555` (version 1.3.0
  on `main` `38b4e1c` plus this row's plan, feed notes, and the
  `store prepare --subtitle` tooling; no product source differs from
  `38b4e1c`). Clean-clone `./gradlew quality` passed.
- **macOS 1.3.0 (28)** from a clean clone of R, release channel, Developer ID
  G2, the floor read from the stable feed (27; no Intel feed yet):
  - `Posato-1.3.0.dmg` (arm64) SHA-256
    `23e5d5b9520740779b41e97128c1cc476c00e9616af98c9aeef8ae7eb142f634`;
    `appcast.xml`
    `ab81ddcb37fa9333693715adf8bf6c15bdda6ab0a82dd8192f52cf939c89cd5e`;
  - `Posato-1.3.0-intel.dmg` (x86-64) SHA-256
    `f5b024bb22620516c53a51cc9272581633ffbfd7e5b82547db190d82dda10293`;
    `appcast-intel.xml`
    `16e9a256c7fd0ee1def8c28f548802c97a933fd3d0e097ff20e91c50608ad437`;
  - one merged `SHA256SUMS` with both DMG lines; `shasum -a 256 -c` OK.
  - Consumed macOS build number: 28 (both architectures).
- **x86-64 verification candidate** from a second clean clone of R:
  candidate channel, Rosetta switch, loopback test feed, a throwaway key that
  was never stored; notarized; SHA-256
  `cc151e5ad976ce05cc9c002f0cb28588da4237e1705d9c2e857b767bf091c274`;
  never published.
- **iOS 1.3.0 (6)** archived from the clean clone, exported, inspected,
  validated, uploaded, `VALID`. Consumed iOS build number: 6.
- **iOS store record (step 4b):** `store prepare` created version 1.3.0,
  attached build 6, set What's New, the description, and the subtitle
  "Space for what matters.", and replaced both screenshot sets, from
  #131's files at `6adfc6a`; a rerun reported every item `unchanged`;
  `store status`: `PREPARE_FOR_SUBMISSION`, release after approval. Not
  submitted.

### Verification on the candidates (2026-10-04)

Run directories are under the release worktree's ignored
`build/verification/runs/`; copies of the key screenshots are in
`build/verification/release-005/`.

| Check | Target | Result |
| --- | --- | --- |
| Install from the arm64 DMG, unified setup | macOS 26 `primary`, fresh | pass: 1.3.0 (28), Notarized Developer ID, setup ready |
| Second set, manual pause with it | `primary` | pass: Session shows "Set: Evening" and that set's 2 websites (the default set was empty); new caption shown |
| Pause page after Automation consent (`MACOS-022`) | `primary` | pass: Safari and Chrome (installed in the guest for this run) showed "This site is paused until 15:21" from the loopback page |
| Schedule with the window closed | `primary` | pass: started at 15:17 and blocked (`observe`), ended at 15:33 and allowed |
| Command-[ back, then Schedules | `primary` | pass, once Posato was the front app |
| Core flow and login launch | macOS 15 `legacy`, fresh | pass: setup ready; a manual pause blocked; after `vm shutdown` and `vm boot` Posato opened at login and a schedule started and blocked with no dialog |
| Upgrade from 1.2.0 | `primary` | pass: 1.2.0 set up with the helper, 2 websites, Chess, and Deep work; after `vm install --replace`: one default set "My set" with 2 websites and 1 app, used by Deep work, helper enabled; a manual start raised no dialog and blocked both websites and Chess. The migrated schedule's own start was not observed (23:00); fresh schedules started above, and the iPhone run below started migrated schedules |
| Intel release DMG | macOS 13.6 `ventura`, Rosetta | pass: Gatekeeper accepted the install; "This version is for Intel Macs" with Quit and Download; after Quit no process, launchd job, daemon, or data |
| Intel verification candidate | `ventura` | pass: x86-64 under Rosetta, setup ready, a manual pause blocked (`outcome: paused`), a schedule started and blocked |
| Upgrade from 1.2 without opening | test iPhone, iOS 26 | pass: a `v1.2.0` development build with consent, Calculator, two websites, and overlapping schedules Early 14:47-15:02 and Late 14:50-15:10; R installed and not opened: Calculator shielded and "Website Not Allowed" during Early (`rel005-iphone-upgrade-early`) and still after Early ended (`rel005-iphone-upgrade-late`); opened afterwards: one default set "My set" with 2 websites and 1 app, used by both schedules |
| `NAV-002` path and edge swipe on R | test iPhone | pass: 5 of 5, the swipe returned from a set and the editor, one app process throughout |
| Linked pair | - | not repeated (maintainer decision) |

- **Driver notes:** `flow schedule` saves weekdays only, so Sunday was added
  in the editor; a 1.2 setup in a fresh clone can need `vm prompt toggle
  --row PosatoMacOSHelper`; the Intel guest needs `posato.vm.venturaGolden`
  in `local.properties`.

### TB-08 and T-13 review of the release artifacts

- R's product sources equal `main` `38b4e1c`; R differs only in
  `Version.xcconfig`, documentation, the feed notes, and
  `tools/posato-provisioning/`.
- New since `v1.2.0`: `com.apple.security.automation.apple-events` on the
  app and the helper (`MACOS-022`, so they can show the pause page in the
  browser after consent); Navigation 3 (`NAV-001`); the x86-64 Temurin
  runtime pinned by SHA-256 (`MACOS-015`). The app otherwise keeps JIT only,
  Sparkle's updater none, and the sync companion CloudKit Production and
  its keychain group.
- Both DMGs: Notarized Developer ID, stapled; the application passes a
  strict deep verification; `LSMinimumSystemVersion` 13.0; no Rosetta
  switch; 38 Mach-O files of exactly the DMG's architecture; Temurin
  21.0.12.1 in both; `SUFeedURL` is `appcast.xml` for arm64 and
  `appcast-intel.xml` for x86-64, with the same `SUPublicEDKey`.
- Each feed holds one item: build 28, 1.3.0, minimum 13.0, the arm64
  hardware requirement only in `appcast.xml`, no release-notes link or
  deltas, an enclosure under `releases/download/v1.3.0/` whose length equals
  its DMG; the build task verified both signatures against the embedded
  key.
- The IPA: Apple Distribution on the app and the extension, Family Controls,
  the app group, CloudKit Production, `get-task-allow` false, both privacy
  manifests.
- The release key stayed in the maintainer's Keychain.
