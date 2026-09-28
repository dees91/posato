# Execution: `RELEASE-004`

- **Brief:** [Verify the 1.2.0 candidates and publish Posato 1.2](../specifications/release-004-release-1-2.md)
- **Status:** `planned` (waits for the 1.2 rows to merge)
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent plan review before step 1
- **Branch:** `docs/release-004-plan`
- **Updated:** 2026-09-27

## Plan

Revision 2 after the plan review. The `RELEASE-003` route, with the 1.2 differences marked.

1. Plan review (done, revision 1 `changes-required`); revision 2 is re-reviewed before step 2.
2. **Tooling, not in R:** `posato-provisioning store withdraw --version <v>` cancels a waiting App Review submission, and `store prepare --rename-from <v>` renames the only unreleased version record, because App Store Connect holds one unreleased version at a time. Tests; no live call yet.
3. **Release commit R:** `MARKETING_VERSION = 1.2.0` and the signed-feed notes file `docs/releases/1.2.0-appcast-notes.txt`, worded to stay true whatever the step 5 gates show (no schedule-sharing or started-elsewhere claims). README, limits, and site wording come from `#103`, not R. R is pinned by its full hash.
4. **Candidates from a clean clone of R:** clean-clone `quality`; `generateMacOsUpdateFeed` on the release channel with build 27 and `-PposatoMacOsPreviousBuildNumber=26` (no macOS build at or above 27 exists; 1000 and 1001 were unkeyed `MACOS-013` test builds), one bold Keychain request to the maintainer for the release key (and the Developer ID key if macOS asks); the App Store archive and upload as build 5 (`store status`: highest is 4), `VALID` in TestFlight.
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
   4. `releases/latest/download/appcast.xml` resolves and verifies. **In-app update:** on a `primary` clone prepared before the go with 1.1.0 set up (helper, websites, linked workspace, update consent answered), About, Check for Updates, install, `launch --adopt`, state preserved, `observe`. **Rollback** if it fails: `gh release edit v1.1.0 --latest`, so the stable feed offers build 26 again, and hold iOS.
   5. iOS: `store withdraw --version 1.1.0`, then `store prepare --version 1.2.0 --rename-from 1.1.0 --build 5 --whats-new docs/store/en-US/whats-new-1.2.0.txt --description docs/store/en-US/description.txt --release after-approval --screenshots docs/store/en-US/screenshots`, then `store submit --version 1.2.0`.
   6. Site deploy check, Projects Done, milestone `1.2.0` closed.
8. A follow-up PR records the App Review outcome.

**Maintainer steps:** the Keychain prompt in step 4, the publication go, and nothing else unless a dialog cannot be driven.

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

- Not started; the plan still waits for the merges.
- **Before the merges (2026-09-28):** the linked-device checks passed on development builds of product head `3089523` in two Tart VMs: schedule sync, automatic start on both Macs, **End early** reaching the other Mac, and the started-elsewhere notice ([`DOCS-003` record](docs-003-release-1-2-media.md)). They do not replace step 4 on the signed candidates.

## Final

- **Status:** `planned`
