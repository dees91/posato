# Execution: `MACOS-026`

- **Brief:** [An in-app update replaces Posato without asking "Quit Posato?"](../specifications/macos-026-update-without-quit-question.md)
- **Status:** `active`
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

- Pending.

## Completed-change review

- **Verdict:** `pending`

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |

## Blockers and accepted risks

- None yet.

## Final

- **Status:** `active`
