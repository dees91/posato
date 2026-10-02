# `MACOS-021`: Explain and bound the helper's CPU use during a session

- **Review tier:** Standard
- **Tier reason:** A resource defect in the normal-user helper; no change to
  privilege, ownership, or the enforcement contract is expected.
- **Dependencies:** `MACOS-022` (merged in #107); release 1.3, wave 2.
  Implementation runs after or strictly apart from `MACOS-020`, which also
  touches the helper and proxy.
- **Integration group:** PR-MAC-HELPER-CPU
- **Authority:** [Release roadmap](../release-roadmap.md) revision 13, row
  `MACOS-021`; idea 18; maintainer named the row on 2026-09-29.

## Outcome

A short measurement shows whether the helper's CPU spike in a first enforced
session recurs. If it does not, the row closes with that result; a reproduced
cause is named, fixed, and bounded.

## Boundaries

- `observed` in `MACOS-012` (Tart clone): the first enforced session in a
  fresh clone kept the normal-user helper at about 60% of one core for about
  14 minutes, then idle; a later session used 0.2%.
- `user-confirmed` (2026-10-02, roadmap revision 16): the maintainer suspects
  a one-off load from parallel machine-learning work on the host, so the row
  is a short measurement, not an investigation.
- Measure on current `main`, which since moved the helper's pipe loop
  (`MACOS-022`) and its proxy handling (`MACOS-020`, `MACOS-024`).
- Keep denial, the exact-domain contract (ADR 0005), and lease renewal
  (ADR 0004) unchanged. No telemetry: measurements stay in local evidence.
- Non-goals: profiling without a reproduction, the Compose desktop process,
  and iOS.

## Acceptance

- `AC-01` — One or two fresh Tart clones on a quiet host: the helper's CPU in
  the first 15 minutes of a first enforced session is recorded, with a blocked
  and an unrelated site checked in the same session.
- `AC-02` — Without a recurrence, the record states the result and the row
  closes. With one, the cause is named and a fix keeps the helper bounded in
  the same run.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- CPU sampling of the helper in fresh Tart clones (`--vm primary`) through
  `posato-control`, before and after, with a blocked and an unrelated site
  checked in the same session.

## Decisions or blockers

- None.
