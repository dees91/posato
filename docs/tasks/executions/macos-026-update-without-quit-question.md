# Execution: `MACOS-026`

- **Brief:** [An in-app update replaces Posato without asking "Quit Posato?"](../specifications/macos-026-update-without-quit-question.md)
- **Status:** `done`: implemented, verified, and reviewed
- **Review tier:** `standard`
- **Implementer:** Claude
- **Reviewer:** different agent (completed-change review)
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

- **Verdict:** `changes-required` on evidence only; the product code was
  approved as it stands at `2dd1fac3`: a person's quit cannot skip the
  question, ADR 0008 and ADR 0004 hold, and the threading and JNI calls are
  sound. Required: cite real run directories (done above). Recommended,
  taken: the accepted limits and the handoff above, and the two extra quit
  checks on 9103.

## Verification

| Check run | Result | Evidence (run directories) |
| --- | --- | --- |
| Reproduction on `main`: 9101 with schedule Evening on, update to 9102 | "Quit Posato?" appeared after **Install and Relaunch**; after **Quit**, 9102 relaunched | `20261009-200445-fa8b` |
| `AC-01`: 9102 to 9103, schedule on, no pause | 9102 running as process 2197; Check for Updates offered 9103; after **Install and Relaunch** no dialog in 60 s of watching; 9103 running as process 3287 | `20261009-210918-7fea`, `20261009-211035-d0ac`, `20261009-211046-d93b`, `20261009-211633-28c3`, `ac01-after` |
| `AC-04`: after the update | a new schedule (set Work) saved, started at 21:21, and `observe` reported example.com `paused`; after its end at 21:36, `loaded` | `20261009-211642-d144`, `20261009-212141-5ece`, `20261009-213649-687b` |
| `AC-03`: during that scheduled pause on 9103 | Command-Q and **Quit Posato** in the status menu both showed "Quit Posato?"; **Keep Posato open** kept the process | `20261009-212158-8a5a`, `ac03-cmdq-screen`, `20261009-212223-ebcb`, `ac03-menu-screen` |
| `AC-02`: 9102 reinstalled with `vm install --replace`, manual pause with Work | pause started and `observe` reported `paused`; **Install Update** answered "The update can't be installed now. End the current session first"; still 9102, same process; Command-Q right after still asked | `ac02-downgrade`, `20261009-214015-82ed`, `20261009-214037-04a5`, `20261009-214042-addd`, `20261009-214052-b2c2`, `ac02-refusal`, `20261009-214119-b5c8`, `ac02-cmdq-after-refusal` |
| `AC-02`: after **End session** | example.com `loaded`; 9102 to 9103 installed with no dialog in 60 s; 9103 running as process 4608 with "Background helper enabled" | `20261009-214141-93de`, `20261009-214147-84f8`, `20261009-214921-5269`, `20261009-215043-0ce0`, `20261009-215048-21c5`, `ac02-after` |
| A person's quit on 9103, fresh `peer` clone after onboarding, no schedule and no pause | Command-Q quit at once without a dialog | `20261009-220633-cc9b` |
| A person's quit on 9103, schedule Morning on, no pause | Command-Q showed "Quit Posato?" with "Quit stops new scheduled starts"; **Keep Posato open** kept the process | `r2-schedule-saved`, `20261009-220816-023c`, `r2-schedules-prompt`, `20261009-220824-39f9` |

The `primary` runs were in one fresh clone on macOS 26 and the last two rows
in a fresh `peer` clone; both clones were destroyed afterwards. Run
directories are under `build/verification/runs/` of this worktree; some
labels passed with `--run-id` were reused across commands, so the table cites
the timestamped directory each command wrote. The `vm click` presses on
Sparkle's buttons write no run directory; the screen text before and after
them is in the cited screenshots. `03214b71` and later commits change only
this record and the wiki.

The maintainer later ordered (2026-10-09): "pomin teraz weryfikacje vm /
iphone - uznajmy ze juz sie odbyly i wystarczy" ("skip VM / iPhone
verification now; let's treat it as already done and sufficient"). The
order arrived after the two `peer` runs above had finished, so their results
stand; no further VM run was made.

## Blockers and accepted risks

- The host disk filled up during the run; the verification resumed from the
  recorded state once space was freed. No result depends on the interruption.
- Accepted by design: updating from 1.2 or 1.3 to 1.4.0 still asks once
  while a schedule is on; press **Quit** to continue. During a pause the
  install is refused instead; builds before 1.2 never ask. The running build
  handles its own termination, so only updates from 1.4.0 onwards skip the
  question.
- Accepted limit (delegated decision of the coordinating agent, 2026-10-09):
  if Sparkle stops after `updaterWillRelaunchApplication:` and before it
  terminates Posato, a person's Command-Q within the following 60 s skips
  the question. Update admission has already closed maintenance, so no pause
  can be enforcing at that moment; the impact is a quit without the schedules
  reminder.
- Possible follow-up, not taken: an `AtomicReference` for the one-shot
  timestamp. Losing a race only brings the question back, and the change
  would need new candidates.

## Handoff

- `DOCS-005`: put the one-time limit above in the 1.4 release notes and
  What's New: "Updating from 1.2 or 1.3 to 1.4.0 still asks once while a
  schedule is on; press Quit to continue. During a pause the install is
  refused instead."

## Final

- **Status:** `done`; ready for review on the pull request
