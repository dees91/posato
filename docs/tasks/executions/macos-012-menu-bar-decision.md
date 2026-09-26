# Execution: `MACOS-012`

- **Brief:** [Menu bar presence decision](../specifications/macos-012-menu-bar-decision.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent agent (plan and decision record); maintainer on PR #87
- **Branch:** `docs/macos-012-menu-bar-decision`
- **Updated:** 2026-09-26

## Plan

`observed` start point (main `13fc900`), which shapes the comparison:

- **Window-hosted work.** These run in `LaunchedEffect`s inside the window's
  composition:
  - the session tick loop (`SessionTransitionOwner.runWhileHosted`, 1 s),
    which also ticks with no session;
  - `MacUpdater.start`;
  - the update-consent prompt.

  Closing the window ends all three, and `onCloseRequest` also exits the
  process.
- **Lease.** The helper renews the daemon lease, not the JVM, every 5 s
  against a 15 s deadline. It restores when its inherited stdin reaches end of
  file. It accepts only a parent signed as the application. Keeping blocking
  alive therefore means keeping that parent alive. The daemon does not know
  when the session ends.
- **Synchronization.** It runs only on foreground (`ON_RESUME`), **Sync now**,
  policy edits, and session transitions. There is no timer or subscription, so
  nothing arrives while the window is closed.
- **Relaunch.** A relaunch with an active session enters `RESUME_REQUIRED` and
  needs an administrator prompt.
- **Absent today.** There is no quit hook, launch-at-login item, tray, or
  activation-policy change.
- **Instances.** The instance lock is shared. A second process started
  directly runs beside the first, and update admission then refuses with
  `OTHER_INSTANCE`. Only LaunchServices keeps bundle launches to one.

Steps:

1. Write the decision as ADR 0009, status `Proposed`, in the ADR 0008 shape:
   - context and evidence;
   - a comparison table;
   - the decision;
   - the authority amendments;
   - a `MACOS-013` delivery plan;
   - the constraints handed to `MACOS-014`, `NOTIFY-001`, `SCHEDULE-001`, and
     `MACOS-019`.

   Route it from the wiki index and from idea 7. Add one log entry.
2. Compare A, B, and C against the brief's criteria, plus two added ones:
   - **Preserve guarantees.** Where the updater start and consent prompt run
     without a window. How Sparkle UI and installation work for a process that
     rarely quits. What "window close" means in the ADR 0008 scenarios. That
     `MACOS-009` removal stays reachable. The ADR 0004 lease and
     reconciliation guarantees.
   - **Reopen and duplicate instances.** Dock click, Finder or Spotlight open,
     a login item beside a direct launch, and their effect on update
     admission.

   Evidence comes from code and ADR analysis, with provenance labels. B and C
   are not built (`inferred`):
   - B re-implements Kotlin-owned session, policy, and sync orchestration in
     Swift.
   - C still needs a resident JVM for the session's helper pipes.
   - Both change the ADR 0004 parent-signature trust boundary.
   - C's cost is at least A plus a native agent.
3. Measure resources in a clone created by `posato-control vm create --line
   primary`. Nothing runs on the host Mac.
   - **Method.** The driver cannot launch without a window, snapshot a
     windowless process, or run a non-Posato binary. So the clone is driven
     two ways:
     - direct `tart exec` runs `footprint`, `ps` CPU time deltas, and
       `top -stats` idle wakeups and energy impact, with the JVM and the
       helper sampled separately. It also runs the P3 agent and opens the
       status menu through an Accessibility `AXPress`, inside the guest only;
     - `posato-control vm text` and `vm screenshot` read the open menu.

     Each sample is 10 minutes after 2 minutes of settling. Figures are
     labelled `observed` in a VM, not hardware energy.
   - **P0.** The current dev-signed Posato with its window open, idle and
     during a session.
   - **P1 (A).** A local patch in a detached, never-pushed throwaway worktree:
     - the window closes without exiting the process;
     - the session owner and updater start are hosted outside the window;
     - a Compose `Tray` menu shows the session and remaining time.

     Measured idle and during a session. A capability check covers the
     template icon in light and dark mode, live remaining time, and the menu
     in VoiceOver.
   - **P2.** A JNI `NSStatusItem` variant. It is built only if P1's `Tray`
     fails that capability check. AWT's tray is already an `NSStatusItem` in
     the same JVM, so it gets no separate timed run.
   - **P3.** A `swiftc` status-item agent in the scratchpad, idle only. It is
     the floor for B and C.
   - **Launch at login.** The probe runs from the P1 patch, because only
     Posato's own bundle can answer it. It asks whether registering
     `SMAppService` `mainApp` creates a background-item record separate from the daemon's,
     and whether disabling it in System Settings is independent of the daemon.

   Captures stay in `build/verification/`. The throwaway worktree and the
   clone are removed at the end.
4. Write out the full text of each change (`AC-02`):
   - the ADR 0003 and ADR 0004 amendments, including a rewrite of the ADR 0004
     "Quit ends the helper" clarification;
   - the ADR 0008 window-close meaning;
   - the `DESIGN.md` menu, window, Dock, and quit rules, including the menu
     **End** keeping the early-end confirmation (`SESSION-005` builds on it);
   - action-required menu states (Resume restrictions, retry, helper
     unavailable) instead of a countdown while nothing is enforced;
   - the README and limits wording.

   The `MACOS-013` delivery plan names the `posato-control` extensions it
   needs:
   - launch and adopt without a window;
   - status-item open and inspection through `AXExtrasMenuBar`;
   - window close told apart from Quit;
   - a login launch through VM logout or reboot.
5. Present the open decisions with a recommendation for each (`AC-04`):
   - the process model;
   - Quit during a session, where logout, restart, and Sparkle relaunch can't
     be refused;
   - launch at login, which must never show an unprompted administrator
     prompt at login;
   - exchanges while the window is closed, and how often.
6. Get an independent review of the record, resolve its findings, and run
   `./gradlew quality` as the standing gate. Record the maintainer's decision,
   then mark the PR ready.

## High-risk plan review

- **Verdict:** `approved` after correction. The first pass returned
  changes-required with four Required findings and no Critical:
  - the preserve boundary for `MACOS-011`, `MACOS-009`, and ADR 0004;
  - a wrong single-instance fact;
  - driver limits without a window;
  - early-end friction and `RESUME_REQUIRED`.

  All four are folded into the plan above. Accepted Recommended items: P2 is
  built only on a capability failure, JVM and helper are sampled separately,
  P0 includes a session, and the login probe has a stated question.

## Result

- Pending.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Pending | | |

## Blockers and accepted risks

- None yet.
