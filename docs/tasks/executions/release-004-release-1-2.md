# Execution: `RELEASE-004`

- **Brief:** [Verify the 1.2.0 candidates and publish Posato 1.2](../specifications/release-004-release-1-2.md)
- **Status:** `in progress`: candidates verified, waiting for the publication go
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent plan review (revisions 1 to 3), Standard review of the tooling, and the completed-change review of step 6
- **Branch:** `docs/release-004-plan`
- **Updated:** 2026-09-28

## Plan

Revision 3 after the plan reviews. The `RELEASE-003` route, with the 1.2 differences marked.

1. Plan review: revision 1 `changes-required`, revision 2 approved on the condition folded in here as revision 3 (early live iOS store steps, earlier-version wording, update failure criteria).
2. **Tooling, not in R, with its own Standard review:** `posato-provisioning store withdraw --version <v>` cancels a waiting App Review submission and waits until the version is editable again; `store prepare --rename-from <v>` renames the version record only when it is the one unreleased version, and a rerun after a rename changes nothing. Tests; the first live calls come in step 4b.
3. **Release commit R:** `MARKETING_VERSION = 1.2.0` and the signed-feed notes file `docs/releases/1.2.0-appcast-notes.txt`, worded to stay true whatever the step 5 gates show (no schedule-sharing or started-elsewhere claims). README, limits, and site wording come from `#103`, not R. R is pinned by its full hash.
4. **Candidates from a clean clone of R:** clean-clone `quality`; `generateMacOsUpdateFeed` on the release channel with build 27 and `-PposatoMacOsPreviousBuildNumber=26` (no macOS build at or above 27 was recorded; the `MACOS-013` update-gate builds 1000 and 1001 name no key in their record, but they ran only in destroyed VM clones, so no install holds them and the floor stays 26), one bold Keychain request to the maintainer for the release key (and the Developer ID key if macOS asks); the App Store archive and upload as build 5 (`store status`: highest is 4), `VALID` in TestFlight.
   4b. **iOS store record, before the sitting:** once build 5 is `VALID`, `store withdraw --version 1.1.0`, then `store prepare --version 1.2.0 --rename-from 1.1.0 --build 5 --whats-new docs/store/en-US/whats-new-1.2.0.txt --description docs/store/en-US/description.txt --release after-approval --screenshots docs/store/en-US/screenshots`, then `store status --version 1.2.0` shows everything `COMPLETE`. Nothing is published; App Store devices already run 1.0.0.
5. **Unattended verification on the notarized DMG** (`vm install --dmg`):
   - macOS 26 `primary` fresh: unified setup, a manual pause, a schedule 3 minutes ahead starting and ending on its own with the window closed, pause notices, `observe`.
   - macOS 15 `legacy` fresh: the same matrix plus a login launch (`vm shutdown`, `vm boot`); any dialog the driver cannot answer is a blocker, not a pass.
   - Upgrade from real 1.1.0 on `primary`: 1.1.0 set up with the helper, websites, an application, and a linked workspace, then `vm install --replace` with 1.2.0: data and workspace kept, the new helper replaces the old one, the setup offer appears and completes with one password, Start then asks for none, a scheduled start works.
   - Linked pair on candidates (`primary` and `peer`, Production CloudKit): schedule sync, automatic start on both, End early across devices, the started-elsewhere notice. If a gate fails, the `DOCS-003` fallback copy is used.
   - Mixed pair: a 1.1.0 peer linked to a 1.2 candidate; saving a schedule shows the "Update Posato" note, the 1.1 peer stops syncing without losing data and catches up after `--replace` to 1.2.
   - Test iPhone on iOS 26 (development build of R): core flow and a scheduled start. Mac to iPhone sync on release builds is not driven (Production CloudKit versus a development-signed iPhone); no CloudKit schema changed since `v1.1.0`.
6. Completed-change review of R, the candidates, and the evidence.
7. **Publication sitting (maintainer go),** in this order:
   1. Merge `#103` (squash), then `git rebase --onto origin/main <#103 head>` for `#96`, set `PRIVACY.md`'s effective date to the publication date, and merge `#96`. R stays reachable through the tag's comparison note and this record even after the branch is rewritten.
   2. Tag `v1.2.0` on the final `main` head only when `git diff --name-only R HEAD` lists nothing outside `docs/`, `website/`, `video/`, `.github/`, `README.md`, `PRIVACY.md`, `tools/posato-control/`, and `tools/posato-provisioning/`; `LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`, and `Version.xcconfig` must match R.
   3. Draft release with exactly the three assets, byte comparison with step 4, publication with the release notes from the `DOCS-003` record (or its fallback copy).
   4. `releases/latest/download/appcast.xml` resolves and verifies. **In-app update:** on a `primary` clone prepared before the go with 1.1.0 set up (helper, websites, linked workspace, update consent answered), About, Check for Updates, install, `launch --adopt`, state preserved, `observe`. It fails when the update is not offered, the install errors, websites, applications, or the workspace are lost, the helper is not ready, or `observe` does not report blocked. **Rollback** then: `gh release edit v1.1.0 --latest`, so the stable feed offers build 26 again, and hold iOS; a fix build needs `-PposatoMacOsPreviousBuildNumber=27` or higher, because the stable feed reports 26 again. The `#103` pages would then promise 1.2 while `releases/latest` serves 1.1.0, so the rollback also reverts the `#103` squash commit on `main` until a fixed 1.2 is published.
   5. iOS: `store submit --version 1.2.0` (the record was prepared in step 4b).
   6. Site deploy check, Projects Done, milestone `1.2.0` closed.
8. A follow-up PR records the App Review outcome.

**Maintainer steps:** the Keychain prompt in step 4, the publication go, and nothing else unless a dialog cannot be driven.

**Accepted without a run:** the next release installing over a 1.2 app kept in the menu bar with its login item and grant was exercised only on development builds 1000 to 1001 (`MACOS-013`); a candidate-channel A to B run on R is not planned.

### TB-08 and T-13 review of the release artifacts

- R's product sources equal `main` `c988670`; only documentation, the website, the media, the store assets, the provisioning tool, and `Version.xcconfig` differ; no dependency, notice, or entitlement file changed since `v1.1.0`.
- The DMG's SHA-256 matches `SHA256SUMS`; Gatekeeper accepts it as Notarized Developer ID and its ticket is stapled; every nested item is signed with the hardened runtime and passes a strict deep verification.
- `appcast.xml` holds one item (build 27, 1.2.0, macOS 15.0, arm64, the `v1.2.0` enclosure with the DMG's length, no release-notes link, no deltas); its notes equal `docs/releases/1.2.0-appcast-notes.txt` at R; the feed and archive EdDSA signatures verify against the application's `SUPublicEDKey`, and the application reads only the stable feed.
- Entitlements equal 1.1.0's: the app has JIT only, the helper and Sparkle none, the sync companion CloudKit Production and its keychain group. The IPA carries Apple Distribution, Family Controls, the app group, CloudKit Production, and no `get-task-allow`.
- The release key stayed in the maintainer's Keychain; the signing prompt was answered on the Mac.

## High-risk plan review

- **Revision 1:** `changes-required` with eight Required findings: no pre-publication mechanism for the in-app update from 1.1.0; feed notes signed before the copy gates; Mac and iOS release order; tag rule and merge order; `--description` availability and the iOS build number; macOS 15 coverage; iOS 18; the real 1.1.0 upgrade.
- **Revision 2:** approved on the condition that the new store commands run live well before the sitting (N1); earlier-version wording (N2) and update failure criteria (N3) recommended. All three are folded into revision 3 above.

## Draft release notes

Superseded by the ready notes in the [`DOCS-003` record](docs-003-release-1-2-media.md#github-release-notes-for-120) and the store texts in `docs/store/en-US/`; kept here as the plan's history.

> **Posato 1.2**
>
> - **Schedules.** Plan recurring pauses by weekday and time. They run on every device you set up, also when Posato's window is closed, and catch up when your Mac wakes or you sign in during a scheduled pause. Skip the next one or end one early. On a Mac, quitting Posato stops new scheduled starts until it opens again.
> - **One setup on the Mac.** A single **Set up Posato** turns on blocking, opens Posato quietly at login, and lets pauses and schedules start without repeated passwords.
> - **Pause notices.** Posato tells you when a pause ends, or when one starts on another device. You can turn this off in About Posato.
> - **Menu bar.** Posato stays in the menu bar with the window closed.
>
> If you use Posato on more than one device, update all of them: once a schedule is saved, devices still on 1.1 stop syncing until they update.

App Store "What's New" (iPhone):

> Schedules: plan recurring pauses by weekday and time, shared with your other devices. Pause notices when a pause ends or starts on another device. Update Posato on all your devices to keep them in sync.

## Result

- **Merges (2026-09-28):** the 1.2 stack #92 to #102 squash-merged in order by the agent at the maintainer's request; `main` `c988670` has the tested tree of `3089523`.
- **Revision R** = `7b34972c3068bf4e6288483f505c20772ad99d6e` (version 1.2.0 on top of `#103` and the plan commits; the appcast notes file is in R). Clean-clone `quality` passed.
- **Tooling (not in R):** `store withdraw` and `store prepare --rename-from` (`33af5eb`, `f019aa5`), Standard review approved; its one Recommended finding (stop at once when App Review finishes the version first) is fixed with a test.
- **macOS 1.2.0 (27)** from a clean clone of R through `generateMacOsUpdateFeed` on the release channel, previous 26, Developer ID G2: `Posato-1.2.0.dmg` SHA-256 `48ef94aaf0243f342e814222bd0f74a803d6a017d52cbe78fec3ff78f1f3d013`, notarized and stapled, Gatekeeper `accepted`; `appcast.xml` one item, build 27, 1.2.0, macOS 15.0, enclosure `releases/download/v1.2.0/Posato-1.2.0.dmg` with the DMG's length; `SHA256SUMS`. Consumed macOS build number: 27.
- **iOS 1.2.0 (5)** archived from the same clone, exported and inspected (Apple Distribution on the app and the extension, Family Controls, the app group, CloudKit Production, `get-task-allow` false, both privacy manifests), validated, uploaded, `VALID`. Consumed iOS build number: 5.
- **iOS store record (step 4b, 2026-09-28, maintainer confirmed the withdrawal):** `store withdraw --version 1.1.0` canceled the waiting submission (state `DEVELOPER_REJECTED`); `store prepare --version 1.2.0 --rename-from 1.1.0 ...` renamed the record, attached build 5, set What's New and the description, and replaced both screenshot sets (4 each, `COMPLETE`); release after approval. Not submitted. App Store devices stay on 1.0.0 until 1.2.0 is approved.
- **Driver (not in R):** `vm install` dragged the topmost "Posato" label, which can be the image window's title or the desktop volume icon, and required an exact read of "/Applications", which recognition misread as "/Appligations"; it now takes the icon beside the link and tolerates two recognition errors (`6c35325`, tested). The DMG itself has the same layout as 1.1.0's.

### Verification on the candidates (2026-09-28)

Run directories are under the release worktree's ignored `build/verification/runs/`: candidate installs `20260928-103316-491f` and `20260928-103355-45df` (the linked pair), `20260928-113548-b5f8` and `20260928-114014-b4be` (1.1.0), `20260928-121841-8d72` and `20260928-123349-aaba` (the replacements with 1.2.0), `20260928-123724-8bb4` (macOS 15), `20260928-130227-628f` (the 1.1.0 installation kept for the update at publication); the other runs of the day between 10:33 and 13:00 are the driven flows in the table; the iPhone runs are `20260928-105619-7184` to `20260928-112314-74ef`.

| Check | Target | Result |
| --- | --- | --- |
| Install from the notarized DMG | macOS 26, `primary` and `peer` | pass: dragged to Applications, Gatekeeper `accepted`, Notarized Developer ID, 1.2.0 (27) |
| Unified setup with iCloud, linked pair | both | pass; the peer's iCloud Keychain paused once and `vm icloud --resume` recovered it |
| Website sync, manual pause | `primary` to `peer` | pass: website arrived; Start asked for no password; `observe` paused; End early allowed at once |
| Update consent | both | the one-time "Check for updates automatically?" alert appears after setup and blocks the window until answered |
| Evidence limits | all Mac runs | the driver's menu read does not open the menu, so its minutes-left text can be older than the minute refresh a person gets on opening it; a text search for "to allow this" matched macOS's Background Items notice, not an administrator dialog (confirmed on screenshots) |
| Schedule sync and automatic start | both | pass: listed on the peer; started on both with the primary's window closed, no password; "Scheduled pause started" in Notification Center |
| End early across devices | `primary` to `peer` | pass: the peer was allowed 18 s after syncing |
| Started-elsewhere notice | `peer`, window closed | pass: "Pause started" 37 s after the primary's start, once the peer had notification permission |
| Upgrade from real 1.1.0 | macOS 26 `primary` | pass: 1.1.0 from the v1.1.0 release installed by drag, linked to iCloud with the helper enabled and a website; `vm install --replace` with 1.2.0 kept the website and the workspace; Session offered "Finish setting up this Mac"; Set up Posato asked for the administrator password once (1.1 had no grant) and reported "This Mac is ready."; a scheduled start then ran with no password, and a manual start asked for none and blocked |
| Mixed pair, 1.1 peer | `primary` 1.2, `peer` 1.1.0 | pass: on the linked Mac with no schedule, Schedules shows "Update Posato on each of them to keep them in sync."; after a schedule was saved on 1.2, the 1.1 peer reported "Sync needs attention" and kept its website; after `--replace` to 1.2 it reported "Latest sync attempt completed" |
| macOS 15 | `legacy`, macOS 15.6.1 | pass: install by drag, Gatekeeper `accepted`; unified setup to "This Mac is ready."; a manual start with no administrator dialog, blocked, ended early; a schedule saved, window closed, the VM shut down and booted: Posato opened at login in the menu bar, the scheduled pause started and blocked with no dialog, "Scheduled pause started, Legacy focus, until 1:02 PM" in Notification Center, End early from the menu bar allowed at once |
| Mac and iPhone, development builds of R | `peer` macOS 26 and the test iPhone, Development CloudKit | pass: the iPhone joined the Mac's workspace; a schedule saved on the Mac appeared on the iPhone and started there with Posato force-quit (the app shield at once, Safari's "Website Not Allowed" about a minute later); End early on the iPhone reached the Mac; a pause started on the Mac showed "Pause started" on the iPhone after it synced; a 5-minute iPhone pause with Posato closed ended with "Pause over"; runs `20260928-131536-dac6` to `20260928-135045-319d`. Release builds of both cannot be paired (Production CloudKit versus a development-signed iPhone) |
| Test iPhone, R's product code | iOS 26.5 | pass: core flow (Calculator shield, "Website Not Allowed", early end), a schedule starting and ending with the app force-quit, early end; Safari showed a blank page in the first minute of the scheduled pause and "Website Not Allowed" a minute later |
- **Before the merges (2026-09-28):** the linked-device checks passed on development builds of product head `3089523` in two Tart VMs: schedule sync, automatic start on both Macs, **End early** reaching the other Mac, and the started-elsewhere notice ([`DOCS-003` record](docs-003-release-1-2-media.md)). They do not replace step 4 on the signed candidates.

## Final

- **Status:** `planned`
