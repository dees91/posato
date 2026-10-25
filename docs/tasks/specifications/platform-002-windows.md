# `PLATFORM-002`: Posato for Windows at parity with Linux

- **Review tier:** high-risk
- **Tier reason:** a new privileged Windows service and its local trust
  boundary, code signing of the packages, and a release artifact on a new
  platform.
- **Dependencies:** `ONBOARDING-005` (each platform names its own setup and
  device) and `PLATFORM-003` (shared resident-window core), both release 1.6
  wave 1.
- **Integration group:** `PR-WINDOWS`
- **Authority:** [release roadmap](../release-roadmap.md) revision 32 and
  [ADR 0010](../../decisions/0010-linux-android-and-folder-workspace.md),
  which this task amends with a Windows row; maintainer direction of
  2026-10-25 (one delivery row for Windows parity, after the
  [Windows spike](../../wiki/topics/unattended-verification.md#windows-guests-platform-002-spike)).
- **Record:** [execution record](../executions/platform-002-windows.md),
  created when implementation starts.

## Outcome

A person's Windows 11 PC joins their Posato workspace as a Linux computer
does: the same interface, a pause that blocks the chosen websites and ends
the chosen applications, schedules that start while the window is closed,
and pauses shared with Linux and the Mac through one folder.

## Boundaries

- Host: `:desktopApp` on the JVM with Compose's `windows-arm64` and
  `windows-x64` runtimes, its own entry point beside the Mac's and Linux's,
  for Windows 11 on arm64 and x64.
- Websites: a privileged helper blocks the chosen hosts and their `www`
  counterparts during a pause and restores everything at its end, an early
  end, and when the session's end passes while Posato is not running, under
  the same apply, clear, and status contract and validation rules as the
  Linux helper. The mechanism is proposed in the ADR amendment (see
  Decisions).
- Applications: chosen from the Start menu's installed applications; the
  helper ends only matching processes of the installing user during a
  pause, never system processes or another user's.
- Privilege: a Windows service, installed once with one administrator
  approval and reachable only by the installing user and the system,
  through a local channel the amendment names. It never reads browsing and
  never logs hosts.
- Resident: the status-area icon, its menu, and the start at login come
  from the `PLATFORM-003` core; one Posato runs per user.
- Synchronization: the folder workspace as on Linux; no iCloud on Windows.
- Packaging: signed MSI packages for arm64 and x64; installing and removing
  them leaves the system as it was, the service and any block included. The
  arm64 package is built in a Tart Windows guest; x64 is built and
  smoke-tested on a GitHub Actions Windows runner, as Linux x86-64 is.
- Verification: `posato-control -t windows` drives a Tart Windows guest
  (tool first, `verify-posato` skill and feature map updated), replacing
  `tools/windows-spike`.
- Non-goals: automatic updates (Linux has none either), the Microsoft
  Store or MSIX, Windows 10, S mode, pause pages, administrator resistance,
  publishing (`RELEASE-010`), and the public packaging (`DOCS-007`).

## Acceptance

- `AC-01` — The maintainer accepts the ADR 0010 Windows row before the
  service is implemented.
- `AC-02` — In a Tart Windows clone, the driver installs the MSI and passes
  onboarding, approving the one administrator prompt; a pause makes
  connections to a chosen host fail in Edge and Chrome and ends a chosen
  application, and both are released at the end, after an early end, and
  when the end passes while Posato is closed; a malformed host is refused.
- `AC-03` — With the window closed, a schedule starts and ends on time from
  the status icon; after a restart Posato starts at login and the schedule
  still applies.
- `AC-04` — A Windows guest, a Tart Ubuntu guest, and a Tart Mac in one
  folder workspace: a pause started on one applies on the other two.
- `AC-05` — The arm64 MSI installs and uninstalls cleanly in the guest and
  the x64 MSI on the CI runner, leaving no service, hosts block, or process.

## Verification

- Contract tests written first for the helper's request validation, its
  rewrite of the blocking state, and the local channel's caller check,
  because E2E cannot produce malformed or foreign requests reliably.
- `posato-control -t windows` runs for `AC-02` to `AC-05` with evidence
  under `build/verification/`; `./gradlew quality` on the tip.

## Decisions or blockers

- ADR 0010 Windows row (maintainer acceptance): the hosts file as on Linux
  or the Windows Filtering Platform for websites, with browsers' own secure
  DNS in mind; installing the service with the MSI or from onboarding; and
  whether Store (packaged) applications can be chosen.
- Manual gate: an Authenticode signing identity, or accepted unsigned
  packages with SmartScreen's warning on the limits page.
- Manual gate: the license of the Windows 11 image used for verification
  beyond the spike.
- Risk: the Tart Windows fork depends on private Virtualization framework
  settings; a macOS update can stop every Windows run.
