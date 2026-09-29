# `MACOS-015`: Posato on Intel Macs running macOS 13 Ventura

- **Review tier:** High-risk
- **Tier reason:** Revises the ADR 0003 platform baseline and the ADR 0008
  update channel, adds a second signed and notarized architecture, and
  proposes an `AGENTS.md` verification exception.
- **Dependencies:** None (release 1.3, wave 1). `DOCS-004` and `RELEASE-005`
  wait for this row.
- **Integration group:** PR-INTEL-RELEASE
- **Authority:** [Release roadmap](../release-roadmap.md) revision 13, row
  `MACOS-015` (absorbs `MACOS-016`); maintainer named the row on 2026-09-29.
  The evaluation already on this branch is summarized in the
  [execution record](../executions/macos-015-intel-ventura.md).

## Outcome

Posato 1.3 can ship a notarized x86-64 build for Intel Macs running macOS 13
Ventura or later that passes the same accepted flow as the arm64 build.

## Boundaries

- **Decisions to propose and have accepted first:** an ADR 0003 revision
  (architectures, minimum macOS, support horizon, whether arm64 on macOS 13
  and 14 stays unsupported, and Rosetta: refuse the x86-64 build on Apple
  silicon or support it); an ADR 0008 revision with the `TB-08`/`T-13`
  threat-model update (a separate x86-64 feed or per-item requirements in one
  feed, and build numbers across architectures); and a narrow `AGENTS.md`
  exception for the dedicated MacBook Air, because Tart cannot run an x86-64
  guest. Nothing relies on them before acceptance.
- The feed that installed 1.1 and 1.2 arm64 clients read never offers an
  x86-64 build, and the x86-64 channel never offers an arm64 build or one with
  a higher minimum macOS.
- This row delivers release tooling and verified candidates on a test feed;
  only `RELEASE-005` publishes anything public.
- macOS 13 compatibility stays enforced after this row: the quality gate
  compiles the macOS 13 native parts, and later macOS rows inherit that.
  The arm64 bundles keep their accepted minimum. The x86-64 Java runtime is
  pinned to the arm64 Temurin version and obtainable by any contributor.
- Setup must work from a clean state without a restart, or the missed daemon
  submission seen in the evaluation becomes a named, disclosed risk. The
  driver never runs `sfltool resetbtm`.
- Non-goals: macOS 12 or older, and the deferred CloudKit bootstrap timeout
  unless it reproduces.

## Acceptance

- `AC-01` — The maintainer accepts the ADR 0003 and ADR 0008 revisions and
  the `AGENTS.md` exception.
- `AC-02` — On the dedicated MacBook Air, driven by `posato-control` from a
  clean state: the macOS 15 flow of the availability page (installation,
  setup, pause, a schedule after restart with login launch, start notice,
  early end from the menu bar), plus the pause page, the application picker,
  sync with a Tart peer, helper removal, and an update between two x86-64
  candidates.
- `AC-03` — The arm64 candidate passes its package checks and the same flow
  in Tart, feeds stay separated as stated, and the chosen Rosetta behavior is
  shown in an arm64 Tart guest.
- `AC-04` — The availability page shows only the verified Intel evidence and
  any accepted limits.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Package checks for both architectures: Mach-O architecture and deployment
  target, signatures, notarization, Gatekeeper, and feed contents.
- `hypothesis` to test first: an arm64 macOS 13 Tart guest with Rosetta may
  cover macOS 13 regressions unattended before the MacBook Air runs.
- MacBook Air runs are the exception path, allowed only after `AC-01`.

## Decisions or blockers

- The maintainer prepares the MacBook Air once as a dedicated test Mac: no
  personal Posato data or other user sessions, the test Apple Account, SSH
  key login, screen sharing with credentials named in `local.properties`,
  automatic login, FileVault off or an unattended restart path, no sleep or
  lock, and the one-time privacy grants for the driver. The evaluation could
  not drive its UI remotely. If development-signed builds run there, it is
  registered through `posato-provisioning`.
