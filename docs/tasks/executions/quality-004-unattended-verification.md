# Execution: `QUALITY-004`

- **Brief:** [Remove the avoidable manual steps from a verification run](../specifications/quality-004-unattended-verification.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** pending
- **Reviewer:** pending
- **Branch:** `feature/quality-004-unattended-verification`
- **Worktree:** `~/Projects/Polyglot/posato-quality-004`
- **Updated:** 2026-09-04

## Plan

1. Measure where the helper's open panel lives and confirm that the pid is
   resolvable under the staged-bundle containment rule.
2. Add the process selector to the desktop element commands with its
   refusals, keeping the default path unchanged, and cover it with tests.
3. Drive the panel with `⌘⇧G` and an absolute path, prove one selection and
   removal, and rewrite the manual step in the macOS mappings feature file.
4. Extend `doctor` into the provisioning gate with one named check per
   one-time condition and a remedy for each.
5. Measure the iOS selection store on the device, record the observed result,
   and state the resulting rule in the iOS mappings feature file.
6. Run `./gradlew quality`, complete the independent completed-change review,
   rerun affected checks, and close this record with the single wiki-log
   entry in the closeout commit.

## Result

- pending

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending
- **Advisory findings:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `:posato-control:test` selector and doctor checks | pending | |
| Driven macOS run: helper panel selection and removal | pending | |
| Device run: iOS selection-store measurement | pending | |
| `./gradlew quality`, `git diff --check`, private-data scan | pending | |

## Blockers and accepted risks

- The macOS run needs the staged development package and the existing
  Accessibility and Screen Recording grants; the device run needs the
  connected iPhone with Screen Time already authorized.
- Accepted limit: the administrator authentication for each Apply stays a
  human step by ADR 0004; this task does not reduce it.

## Final

- **Status:** pending
- **Outcome:** pending
