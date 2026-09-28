# Execution: `RELEASE-004`

- **Brief:** [Verify the 1.2.0 candidates and publish Posato 1.2](../specifications/release-004-release-1-2.md)
- **Status:** `planned` (waits for the 1.2 rows to merge)
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent plan review before step 1
- **Branch:** `docs/release-004-plan`
- **Updated:** 2026-09-28

## Plan

Revision 2 after the plan review. The `RELEASE-003` route, with the 1.2 differences marked.

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
   1. Merge `#103`, rebase `#96` on it, merge `#96`.
   2. Tag `v1.2.0` on the final `main` head only when `git diff --name-only R HEAD` lists nothing outside `docs/`, `website/`, `video/`, `.github/`, `README.md`, `PRIVACY.md`, `tools/posato-control/`, and `tools/posato-provisioning/`; `LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`, and `Version.xcconfig` must match R.
   3. Draft release with exactly the three assets, byte comparison with step 4, publication with the release notes from the `DOCS-003` record (or its fallback copy).
   4. `releases/latest/download/appcast.xml` resolves and verifies. **In-app update:** on a `primary` clone prepared before the go with 1.1.0 set up (helper, websites, linked workspace, update consent answered), About, Check for Updates, install, `launch --adopt`, state preserved, `observe`. It fails when the update is not offered, the install errors, websites, applications, or the workspace are lost, the helper is not ready, or `observe` does not report blocked. **Rollback** then: `gh release edit v1.1.0 --latest`, so the stable feed offers build 26 again, and hold iOS; a fix build needs `-PposatoMacOsPreviousBuildNumber=27` or higher, because the stable feed reports 26 again. The site from `#103` stays live, which is acceptable because the 1.2.0 DMG stays public.
   5. iOS: `store submit --version 1.2.0` (the record was prepared in step 4b).
   6. Site deploy check, Projects Done, milestone `1.2.0` closed.
8. A follow-up PR records the App Review outcome.

**Maintainer steps:** the Keychain prompt in step 4, the publication go, and nothing else unless a dialog cannot be driven.

**Accepted without a run:** the next release installing over a 1.2 app kept in the menu bar with its login item and grant was exercised only on development builds 1000 to 1001 (`MACOS-013`); a candidate-channel A to B run on R is not planned.

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
- **Before the merges (2026-09-28):** the linked-device checks passed on development builds of product head `3089523` in two Tart VMs: schedule sync, automatic start on both Macs, **End early** reaching the other Mac, and the started-elsewhere notice ([`DOCS-003` record](docs-003-release-1-2-media.md)). They do not replace step 4 on the signed candidates.

## Final

- **Status:** `planned`
