# Execution: `MACOS-013`

- **Brief:** [Keep Posato for Mac running in the menu bar](../specifications/macos-013-menu-bar.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude, autonomously on the maintainer's delegation (2026-09-26)
- **Reviewer:** independent agents (plan and completed change)
- **Branch:** `feature/macos-013-menu-bar`
- **Updated:** 2026-09-26
## Plan

Revision 15 of the quality contract applies:

- E2E through `posato-control` in Tart clones is the primary proof.
- Isolated tests cover only F1-F5 below; each is written and shown failing
  before its implementation.
- Each E2E run records its revision, state, command, result, and run
  directory.

| # | Failure E2E cannot reliably expose | Test home |
| --- | --- | --- |
| F1 | The menu's state or action claims confirmed restrictions for a session that is not enforcing (`APPLY_FAILED`, `CLEAR_FAILED`, `RESUME_REQUIRED`, pending, or a closed maintenance gate): injected faults only | jvmTest, pure function |
| F2 | An admitted update relaunch, or a system termination flagged within its time box, waits on the Quit confirmation; a stale flag from a cancelled logout skips a later confirmation | jvmTest, pure decision |
| F3 | Idle wait on desktop: it keeps reading every second, misses a start or a replica snapshot for up to 60 s, or changes iOS's default loop | `SessionTransitionOwnerTest`, virtual time |
| F4 | The resident exchange skips its start-up run, runs while unlinked, stops, or runs more often than every 30 min | jvmTest, virtual time |
| F5 | The observable maintenance state disagrees with the persisted gate: closed at start-up, and after a cycle until reopened | `MaintenanceAdmissionTest` |

Steps:

0. `/skill-advisor`. The `test-audit` skill gates every test.
1. **Spikes, first, in a VM, on a throwaway build.** Each decision is
   recorded before it is used:
   - (a) `keyAELaunchedAsLogInItem` seen through a leaf observer installed
     before AWT, on a login launch from `SMAppService.mainApp`;
   - (b) a leaf `NSMenuItem` **Close Window** (`performClose:`) surviving
     AWT's menus beside Sparkle's item;
   - (c) a graceful restart through loginwindow in the guest, with
     `TALLogoutSavesState` off;
   - (d) guest sleep and wake (`pmset sleepnow`).

   If (a) fails, a login launch cannot start windowless, and that D3 detail
   goes back to the maintainer. If (c) or (d) fails, the row becomes a
   blocker with the exact command.
2. **Shared.**
   - `Content(hostsSession: Boolean = true)` keeps iOS unchanged.
   - `runWhileHosted(idleWait)` wakes on start, settle, replica snapshot, or
     foreground. Its 60 s safety recheck is desktop-only (F3).
   - A public `DesktopPresence` in `DesktopApplicationComponents` exposes:
     - `runWhileResident()`, with one exchange at start and one every 30 min
       while linked (F4);
     - `menu: StateFlow` (F1), fed by `MaintenanceAdmission`'s state (F5);
     - `onMenuOpened()` and `quitPrompt()` (F2);
     - a conflated window-request channel (setup, early end, or Session),
       routed under the existing guards.
   - While onboarding is incomplete, a request only shows the window.
   - The hidden window pauses the `SessionViewModel` ticker and the consent
     prompt.
3. **Presence leaf** (`WindowChrome.m`, `ServiceManagement`, the `Updater.m`
   callback pattern; callbacks only enqueue work):
   - an `NSStatusItem` with a drawn template mark, filled only for confirmed
     restrictions, an accessibility description, and a menu built from
     Kotlin titles and action ids, with open and close callbacks;
   - the regular or accessory policy, following the main window and any
     updater or alert window (the updater posts a notification before it
     presents);
   - reopen (`AppReopenedListener`);
   - the power-off flag with a time box;
   - `NSAlert` sheets for the Quit confirmation (asynchronous quit response)
     and the first-close notice, shown before the switch to accessory;
   - `SMAppService.mainApp`;
   - the launch-reason probe from spike (a).
4. **Desktop.**
   - The window stays composed and hides on close.
   - `MacUpdater.start` and `runWhileResident` run at application scope, the
     updater after AWT's menu exists.
   - The minutes refresh on menu open and each minute while it is open.
   - The first-close marker lives in Application Support.
   - `Desktop.setQuitHandler` applies the F2 decision.
5. **This Mac.**
   - A `LoginItemPort` feeds the **Open Posato at login** row.
   - Verified removal unregisters the login item.
   - New copy: maintenance menu text, and "Restrictions not active on this
     Mac." for `RESUME_REQUIRED`.
6. **`posato-control`.**
   - `menu`: bridge `status-menu` to read, press, and choose.
   - `window close`: the AX close button.
   - `resources`: footprint, CPU, and wakeups for the app and helper.
   - `vm restart`: a graceful loginwindow restart, from spike (c).
   - Login Items records are read with `vm text` after opening the pane in
     the guest.
   - README and feature map.
7. **E2E.** Every ADR 0009 row, plus:
   - no notification prompt at launch;
   - expiry within a tolerance under 60 s;
   - the Check for Updates… item present;
   - the refusal alert visible with the window closed;
   - login launch with a negative control (switch off);
   - reopen through LaunchServices.

   The update gate uses the development package's loopback feed.
8. Apply the ADR 0003, 0004, and 0008 amendments, the `DESIGN.md` rules, and
   the README, limits, and website wording. Mark ADR 0009 delivered, and
   update the wiki and the log.
9. Completed-change review, `./gradlew quality`, push, `/visual-pr`, and
   mark the PR ready. No hosted review.

## High-risk plan review

- **Verdict:** `pending`; the first pass had 1 Critical and 8 Required findings, all folded in above.
## Result

- Pending.

## Verification

- Pending.

## Blockers and accepted risks

- None yet.
