# `PLATFORM-002`: Posato for Windows at parity with Linux

- **Review tier:** high-risk
- **Tier reason:** a new privileged Windows service and its local trust
  boundary, kernel-level network filters, code signing of the packages, and a
  release artifact on a new platform.
- **Dependencies:** `ONBOARDING-005` (merged). `PLATFORM-003` (shared
  resident-window core) is folded into this task (`user-confirmed`,
  2026-10-27, roadmap revision 33).
- **Integration group:** `PR-WINDOWS`
- **Authority:** [release roadmap](../release-roadmap.md) revision 33 and
  [ADR 0010](../../decisions/0010-linux-android-and-folder-workspace.md),
  which this task amends with a Windows row accepted on 2026-10-27; maintainer
  direction of 2026-10-25 (one delivery row for Windows parity, after the
  [Windows spike](../../wiki/topics/unattended-verification.md#windows-guests-platform-002-spike)).
- **Record:** [execution record](../executions/platform-002-windows.md).

## Outcome

A person's Windows 11 PC joins their Posato workspace as a Linux computer
does: the same interface, a pause that blocks the chosen websites and ends
the chosen applications, schedules that start while the window is closed,
and pauses shared with Linux and the Mac through one folder. The Mac, Linux,
and Windows hosts keep their resident window, status icon, and start at login
on one shared core.

## Boundaries

- Resident core (`PLATFORM-003`): the window that closes to a status icon,
  the icon's menu model and actions, the quit and first-close rules, and the
  start at login become one core in `:desktopApp`; the Mac and Linux keep
  only their native layers and behave as before.
- Host: `:desktopApp` on the JVM with Compose's `windows-arm64` and
  `windows-x64` runtimes, its own entry point, for Windows 11 on arm64 and
  x64.
- Websites: a Windows service blocks connections to the addresses that the
  chosen hosts and their `www` counterparts resolve to, with Windows
  Filtering Platform filters in a dynamic session, under the same apply,
  clear, status, and acknowledge contract and validation rules as the Linux
  helper; it clears at the session end even while Posato is closed.
- Applications: chosen from the Start menu's shortcuts to installed desktop
  programs; Store (packaged) applications, interpreters, and launchers are
  not offered. The service ends only matching processes of the installing
  user during a pause, never system processes or another user's.
- Privilege: the per-machine MSI installs the service with its one
  administrator approval and records the installing user; the service is
  reachable only by that user and the system through a local named pipe. It
  never reads browsing and never logs hosts.
- Synchronization: the folder workspace as on Linux; no iCloud on Windows.
- Packaging: MSI packages for arm64 and x64 with a signing step that runs
  when an identity is configured; installing and removing them leaves the
  system as it was, the service and any filter included. The arm64 package
  is built in a Tart Windows guest; x64 is built and smoke-tested on a GitHub
  Actions Windows runner.
- Verification: `posato-control -t windows` drives a Tart Windows guest
  (tool first, `verify-posato` skill and feature map updated), replacing
  `tools/windows-spike`.
- Non-goals: automatic updates, the Microsoft Store or MSIX, Windows 10,
  S mode, pause pages, administrator resistance, an Authenticode identity
  (`RELEASE-010` gate), publishing (`RELEASE-010`), and the public packaging
  (`DOCS-007`).

## Acceptance

- `AC-01` — The maintainer accepts the ADR 0010 Windows row before the
  service is implemented. Met on 2026-10-27.
- `AC-02` — In a Tart Windows clone, the driver installs the MSI and passes
  onboarding; a pause makes connections to a chosen host fail in Edge and
  Chrome and ends a chosen application, and both are released at the end, after an
  early end, and when the end passes while Posato is closed; a malformed or
  foreign request is refused.
- `AC-03` — With the window closed, a schedule starts and ends on time while
  the status icon stays; after a restart Posato starts at login and the
  schedule still applies.
- `AC-04` — A Windows guest, a Tart Ubuntu guest, and a Tart Mac in one
  folder workspace: a pause started on one applies on the other two.
- `AC-05` — The arm64 MSI installs and uninstalls cleanly in the guest and
  the x64 MSI on the CI runner, leaving no service, filter, or process.
- `AC-06` — On the shared resident core, the Mac in a Tart VM and Linux in a
  Tart Ubuntu clone close to the status icon, show again from it, start
  hidden at login, and ask before quitting with a schedule on, as before.

## Verification

- Contract tests written first for the service's request validation, its
  filter plan (hosts to addresses, refusing loopback and private ranges),
  and the pipe's caller check, because E2E cannot produce malformed or
  foreign requests reliably.
- `posato-control -t windows` runs for `AC-02` to `AC-05`, and the Mac and
  Linux targets for `AC-06`, with evidence under `build/verification/`;
  `./gradlew quality` on the tip.

## Decisions or blockers

- ADR 0010 Windows row accepted (`user-confirmed`, 2026-10-27): Windows
  Filtering Platform for websites; the service installed by the MSI; Start
  menu desktop programs only.
- Verification runs on the maintainer's unactivated Windows 11 ARM64 image
  (`user-confirmed`, 2026-10-27). The verification guest has UAC off, so the
  administrator prompt itself is not driven; the MSI's elevation path is
  standard Windows Installer.
- Manual gate kept for `RELEASE-010`: an Authenticode signing identity, or
  accepted unsigned packages with SmartScreen's warning on the limits page.
- Risk: the Tart Windows fork depends on private Virtualization framework
  settings; a macOS update can stop every Windows run.
