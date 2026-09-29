# `MACOS-015`: Posato on Intel Macs running macOS 13 Ventura

- **Review tier:** High-risk
- **Tier reason:** Revises the ADR 0003 platform baseline, adds a second
  signed and notarized architecture to the release and update path, and
  proposes an `AGENTS.md` verification exception.
- **Dependencies:** None (release 1.3, wave 1). `DOCS-004` and `RELEASE-005`
  wait for this row.
- **Integration group:** PR-INTEL-RELEASE
- **Authority:** [Release roadmap](../release-roadmap.md) revision 13, row
  `MACOS-015` (absorbs `MACOS-016`); maintainer named the row on 2026-09-29.
  The evaluation already on this branch is summarized in the
  [execution record](../executions/macos-015-intel-ventura.md).

## Outcome

Posato 1.3 ships a supported, notarized x86-64 build for Intel Macs running
macOS 13 Ventura or later, with the same blocking, setup, synchronization,
and in-app update behavior as the arm64 build.

## Boundaries

- Propose the ADR 0003 revision (supported architectures, minimum macOS,
  support horizon for Intel and macOS 13) and obtain acceptance before the
  release path changes. The arm64 default build keeps its behavior.
- Propose a narrow `AGENTS.md` exception: the 2019 MacBook Air may run Posato
  as a dedicated test Mac, because Tart cannot run an x86-64 guest. Rely on it
  only after acceptance; the maintainer's own Mac stays excluded.
- Extend `posato-control` to drive that Mac without the maintainer, as it
  drives Tart: installation, prompts, the application UI, and evidence.
- Publish the x86-64 distribution and update-feed entry that the accepted
  ADR 0003 revision specifies; the update path must never offer a build to
  the wrong architecture or system.
- Non-goals: macOS 12 or older, universal binaries unless the ADR revision
  chooses them, and the deferred intermittent CloudKit bootstrap timeout
  unless it reproduces during verification.

## Acceptance

- `AC-01` — The ADR 0003 revision and the `AGENTS.md` exception are accepted
  by the maintainer.
- `AC-02` — On the dedicated MacBook Air, driven by `posato-control`: setup,
  a manual session with actual website and application blocking and release,
  a schedule, iCloud link and sync with a Tart peer, and an in-app update
  between two notarized x86-64 candidates.
- `AC-03` — The arm64 candidate still passes its package checks and the
  accepted flow in Tart, and its update feed never offers the x86-64 build.
- `AC-04` — The availability page and release process name both builds.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Package checks for both architectures: every Mach-O architecture and
  deployment target, signatures, notarization, Gatekeeper, and feed isolation.
- The AC-02 flow on the MacBook Air and the AC-03 flow in Tart, with evidence
  under ignored `build/verification/`.

## Decisions or blockers

- The maintainer prepares the MacBook Air once as a dedicated test Mac: no
  personal Posato data (the evaluation build and its data are removed or a
  separate macOS account is used), the test Apple Account, remote login, and
  screen sharing. The evaluation could not drive its UI remotely: System
  Events timed out and port 5900 was closed.
- The evaluation changed `Package.swift` to macOS 13 for every build; the
  plan must keep the arm64 deployment target where ADR 0003 leaves it.
