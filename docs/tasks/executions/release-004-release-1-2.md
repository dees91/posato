# Execution: `RELEASE-004`

- **Brief:** [Verify the 1.2.0 candidates and publish Posato 1.2](../specifications/release-004-release-1-2.md)
- **Status:** `planned` (waits for the 1.2 rows to merge)
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent plan review before step 1
- **Branch:** `docs/release-004-plan`
- **Updated:** 2026-09-27

## Plan

The `RELEASE-003` route, with the 1.2 differences marked:

1. After the maintainer merges the 1.2 rows, rebase this branch on `main` and run the plan review.
2. Release commit: `MARKETING_VERSION = 1.2.0`, the privacy policy's notifications and schedules passages with the effective date left for publication, README and availability wording. Revision R is this commit, pinned by its full hash; it is the only tree the candidates are built from and the one step 7 compares with the tagged tree.
3. Candidates from a clean clone of R: `generateMacOsUpdateFeed` on the release channel with build 27 and previous 26; the App Store archive and upload with the next iOS build.
4. Unattended verification: fresh install on macOS 26 (`primary`) and macOS 15 (`legacy`); **new:** 1.1.0 installed with `vm install --dmg`, then updated in-app to 1.2.0 from a feed served to the VM; the setup offer; a schedule 2 minutes ahead starting and ending on its own; `observe`; the test iPhone core flow and a scheduled start 15 minutes ahead.
5. Store screenshots against the 1.2 UI if the Session or Schedules screens changed; App Store "What's New".
6. Completed-change review; the maintainer merges.
7. Publication in one sitting (maintainer go): annotated tag on the squash commit when its product tree equals R, draft release with exactly the three assets, byte comparison, publication, then `releases/latest/download/appcast.xml` resolves and a 1.1.0 VM sees the update.
8. App Review submission (automatic release unless the maintainer decides otherwise), Projects Done, milestone `1.2.0` closed; a follow-up PR records the App Review outcome.

## Draft release notes

For review; final wording after verification.

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

- Not started.

## Final

- **Status:** `planned`
