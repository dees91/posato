# Execution: `MACOS-026`

- **Brief:** [An in-app update replaces Posato without asking "Quit Posato?"](../specifications/macos-026-update-without-quit-question.md)
- **Status:** `active`: implemented and verified; waiting for the completed-change review
- **Review tier:** `standard`
- **Implementer:** Claude
- **Reviewer:** different agent; pending until assigned
- **Branch:** `task/macos-026-update-without-quit-question`
- **Updated:** 2026-10-09

## Plan

1. Maintainer decision `D1`: accepted (`user-confirmed`, 2026-10-09) as
   `AC-02`; ADR 0008's refusal during a pause stays.
2. Reproduce on `main` before the change: candidates N and N+1 built from
   `main` with a throwaway key, a fresh `primary` clone, a schedule on, and
   the "Quit Posato?" dialog after **Install and Relaunch**.
3. Pass a narrow updater-termination signal from the updater leaf to the
   quit handler and skip the confirmation only for it.
4. Candidates N and N+1 from the branch; `AC-01` to `AC-04` in fresh
   `primary` clones.
5. Completed-change review by a different agent; closeout of this record and
   one wiki-log entry.

## Result

- `2dd1fac3`: Sparkle's `updaterWillRelaunchApplication:` calls
  `MacUpdater.onRelaunchRequested`, which stores a one-shot timestamp. Only
  the AWT quit handler, the path Sparkle's termination takes, reads it, and
  treats it as a system termination inside the existing 60 s window that
  already exempts power-off. **Quit Posato** in the menu, Command-Q while no
  relaunch is pending, and closing the window still ask. Update admission is
  unchanged: during a pause it answers before Sparkle reaches the relaunch,
  so a refused install never sets the timestamp.
- `./gradlew quality` passed on `2dd1fac3` (9 min 11 s).
- Notarized Developer ID candidates, release channel structure, test feed on
  the guest's loopback, throwaway update key deleted after the run:
  9101 from `main` `ae99a9da`; 9102 and 9103 from `2dd1fac3`.

## Completed-change review

- **Verdict:** `pending`; requested from a different agent.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Reproduction on `main`: 9101 with schedule Evening on, update to 9102 | "Quit Posato?" appeared after **Install and Relaunch**; after **Quit**, 9102 relaunched | `20261009-200445-fa8b` |
| `AC-01`: 9102 to 9103, schedule on, no pause | no dialog in 60 s of watching; 9103 relaunched as a new process | `ac01-9103`, `ac01-relaunch`, `ac01-after` |
| `AC-04`: after the update | a new schedule (set Work) started at 21:21 and `observe` reported example.com `paused`; it ended at 21:36 and `loaded`; This Mac reads "Background helper enabled" | `ac04-schedule`, `ac04-observe`, `ac04-after-second` |
| `AC-03`: during that scheduled pause on 9103 | Command-Q and **Quit Posato** in the status menu both showed "Quit Posato?"; **Keep Posato open** kept the process | `ac03-cmdq-screen`, `ac03-menu-screen` |
| `AC-02`: 9102 reinstalled with `vm install --replace`, manual pause with Work | **Install Update** answered "The update can't be installed now. End the current session first"; still 9102, same process; Command-Q right after still asked | `ac02-refusal`, `ac02-cmdq-after-refusal` |
| `AC-02`: after **End session** | example.com `loaded`; 9102 to 9103 installed with no dialog in 60 s; 9103 relaunched, helper enabled | `ac02-relaunch`, `ac02-after` |

All runs were in one fresh `primary` clone on macOS 26, under
`build/verification/runs/` of this worktree; the clone was destroyed after
the run.

## Blockers and accepted risks

- The host disk filled up during the run; the verification resumed from the
  recorded state once space was freed. No result depends on the interruption.
- Accepted by design: an update from a build without this change (1.3.0 and
  earlier) still asks once, because the running build handles its own
  termination. Updates from 1.4.0 onwards do not ask.

## Final

- **Status:** `active`
