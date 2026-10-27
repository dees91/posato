# Execution: `PLATFORM-002`

- **Brief:** [Posato for Windows at parity with Linux](../specifications/platform-002-windows.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** pending
- **Branch:** `platform-002-windows`
- **Updated:** 2026-10-27

## Plan

1. **Authorities.** ADR 0010 gains the Windows row and its limits
   (`user-confirmed` 2026-10-27); roadmap revision 33 folds `PLATFORM-003`
   into `PLATFORM-002` and the Projects mirror follows in the same step.
2. **Resident core (`PLATFORM-003`).** Move the platform-neutral parts of
   `LinuxResidentPosato` and `ResidentPosato` into
   `app.posato.desktop.resident`: the menu action to window request mapping,
   the close outcome and "keeps running" rules, the quit and first-close
   notices, the status-menu refresh loop, and one `ResidentPosato`
   composable for hosts that draw their own window. A host supplies a
   `StatusSurface` (update, show, action, and menu-open events, presence of
   a watcher), a `LoginLaunch` (`isEnabled`, `setEnabled`), and a
   first-close marker. Linux keeps its D-Bus status item, XDG autostart, and
   instance lock; the Mac keeps `MacPresenceNative`, its window chrome, the
   quit handler, and the updater, and calls the shared mapping and refresh.
   No behavior change; the existing Linux and Mac tests keep passing.
3. **`:windowsHelper` (new module, Kotlin JVM, JNA).** Tests first for
   `HelperRequest` (the Linux grammar; executables are absolute Windows paths
   `X:\...\name.exe`, no `..`, no control characters, at most 64),
   `FilterPlan` (hosts plus `www` names to IPv4 and IPv6 addresses; loopback,
   unspecified, link-local, multicast, and private ranges never filtered; at
   most 16,384 addresses), `ProcessMatch` (case-insensitive path equality and
   owner SID equal to the installing user's; SYSTEM, LOCAL SERVICE, and
   NETWORK SERVICE never), and `PipeCaller` (only the installing user's SID
   or SYSTEM). The service:
   - runs as `LocalSystem` under the SCM (`StartServiceCtrlDispatcherW` from
     the jpackage launcher `posato-helper.exe`), restarts on failure;
   - serves one line per connection on `\\.\pipe\app.posato.helper`, created
     with `FILE_FLAG_FIRST_PIPE_INSTANCE`, `PIPE_REJECT_REMOTE_CLIENTS`, and
     the DACL `D:P(A;;GA;;;SY)(A;;GRGW;;;<installing user SID>)`; each
     connection's client process token is checked again, and a client that
     does not send within five seconds is cut off;
   - opens one WFP dynamic session with its own sublayer and adds block
     filters at `ALE_AUTH_CONNECT_V4/V6` for the plan's addresses; it
     re-resolves the names every minute during a pause and only adds
     addresses until the pause ends; a crash or stop removes every filter
     with the session, and a restart re-applies from the state file;
   - keeps its state in `%ProgramData%\Posato\helper\state` (SYSTEM and
     Administrators only), clears at the session end, and ends the chosen
     processes every second during a pause;
   - `install --user <account>` (MSI deferred action) resolves the account
     to a SID, writes the configuration, creates and starts the service;
     `uninstall` stops and deletes it and removes the state.
   It never reads traffic and never logs hosts.
4. **Windows host (`app.posato.desktop.windows`).** `WindowsMain` with the
   resident core: Compose `Tray` as the `StatusSurface`, the
   `HKCU\...\CurrentVersion\Run` value `Posato` with `--background` as the
   `LoginLaunch`, a per-user instance lock plus an AF_UNIX socket in
   `%LOCALAPPDATA%\Posato` to forward a second launch's show request,
   `StartMenuCatalog` (`.lnk` files in both Start menu folders parsed for
   their local `.exe` target; launchers and interpreters such as `cmd`,
   `powershell`, `wscript`, `rundll32`, `explorer`, `msiexec`, and `java`
   are not offered), and `WindowsSessionEnforcement` over a pipe client on
   the Linux request lines. `OnboardingPermissionPlatform` gains `WINDOWS`;
   the folder key file uses an owner-only ACL where POSIX modes are absent.
5. **Packaging.** `windowsAppJar` with both Windows Compose runtimes, JNA,
   and the helper; `desktopApp/windows/package-msi.ps1` runs jpackage (WiX 3)
   with an `overrides.wxi` that runs `posato-helper.exe install --user
   [LogonUser]` after `InstallFiles` and `uninstall` before `RemoveFiles`,
   both deferred and not impersonated, and `signtool` when
   `POSATO_WINDOWS_SIGNING` names an identity. A GitHub Actions workflow
   builds and smoke-tests x64 (quiet install, service running, uninstall
   leaves no service, pipe, or filter). Notices list JNA.
6. **Tool first.** `posato-control -t windows`: `vm` clone and run of the
   Windows golden through the Tart fork, `build` and `install` of the MSI,
   `launch`, `snapshot` and `press` and `type` through the Java Access
   Bridge probe, `screenshot`, `flow onboard`, `flow session`, and guest
   checks (`Test-NetConnection`, process list, service and filter state);
   the skill and its feature map gain Windows; `tools/windows-spike` goes.
7. **Verification** in order: `AC-06` on the Mac VM and Linux clone, then
   `AC-02`, `AC-03`, `AC-05` in a Windows clone and on CI, then `AC-04`
   across the three guests sharing one host folder.
8. Independent completed-change review, `./gradlew quality`, wiki and log,
   `pr-evidence`, ready, `babysit-pr adopt`.

## High-risk plan review

- **Verdict:** pending
- **Critical or Required findings:** pending
- **Resolution:** pending

## Result

- Pending.

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| pending | | |
