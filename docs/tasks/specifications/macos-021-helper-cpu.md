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

The helper's CPU use during an enforced session is explained and stays
bounded, including in the first session of a freshly set-up Mac and under
heavy proxied traffic.

## Boundaries

- `observed` in `MACOS-012` (Tart clone): the first enforced session in a
  fresh clone kept the normal-user helper at about 60% of one core for about
  14 minutes, then idle; a later session used 0.2%. System traffic through
  the loopback proxy after boot is a `hypothesis`.
- Reproduce first and name the cause with a profile. Fix only what the
  evidence shows; if the cost is legitimate traffic, bound it rather than
  hide it.
- `MACOS-022` since moved the pipe loop to a serial worker and native work to
  the main run loop; measure on current `main`, not the `MACOS-012` build.
- Keep denial, the exact-domain contract (ADR 0005), and lease renewal
  (ADR 0004) unchanged. No telemetry: measurements stay in local evidence.
- Non-goals: the Compose desktop process's own resource use, and iOS.

## Acceptance

- `AC-01` — A first enforced session in a fresh Tart clone is measured on
  `main`; the spike is reproduced with a named cause, or its absence over
  repeated fresh clones is recorded.
- `AC-02` — With a fix, the same first session stays below an agreed CPU
  bound across its first 15 minutes.
- `AC-03` — A heavy proxied-traffic run keeps the helper within that bound
  while blocking and relaying as before.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- CPU sampling of the helper in fresh Tart clones (`--vm primary`) through
  `posato-control`, before and after, with a blocked and an unrelated site
  checked in the same session.

## Decisions or blockers

- The CPU bound for `AC-02` and `AC-03` is proposed with the measurements and
  accepted by the maintainer.
