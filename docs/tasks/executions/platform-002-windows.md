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

9. **Plan-review corrections (all Required, chosen Recommended).**
   - Client: opens the pipe through JNA with `SECURITY_SQOS_PRESENT |
     SECURITY_IDENTIFICATION`, waits on `ERROR_PIPE_BUSY`, and sends nothing
     unless `GetNamedPipeServerProcessId` names a process whose token is
     SYSTEM and whose image is the installed `posato-helper.exe`.
   - Server: DACL grants the user `0x0012018B` (no `FILE_CREATE_PIPE_INSTANCE`);
     the caller SID comes from `ImpersonateNamedPipeClient` and
     `OpenThreadToken`.
   - `install` creates or takes over `%ProgramData%\Posato` with owner
     SYSTEM and a protected DACL (SYSTEM, Administrators); the service checks
     owner and no reparse point before each write and writes through an
     NTFS-specific atomic write, not the Linux `writeAtomically`.
   - JNA: `jnidispatch.dll` ships in the install folder; the helper's
     launcher configuration sets `jna.nounpack=true` and
     `jna.boot.library.path`; the service binary path is quoted.
   - MSI: `install` on `NOT Installed AND NOT REMOVE`, a rollback action that
     deletes the service, `uninstall` on `REMOVE="ALL"`, binaries referenced
     as `"[INSTALLDIR]..."`; a repair keeps the recorded user; a major upgrade
     keeps the state file, so an active pause re-applies when the new service
     starts. `[LogonUser]` is resolved qualified with the computer name;
     SYSTEM and service accounts are refused, and the limits say only the
     installing account is served.
   - Filters carry an `FWPM_CONDITION_ALE_USER_ID` condition for the
     installing user, so system services are never blocked; IPv4-mapped
     connects are tested once in the guest.
   - Service: `SERVICE_CONFIG_REQUIRED_PRIVILEGES_INFO` with the minimum
     privileges, a restricted service SID where WFP allows it, automatic
     start; process ending checks the image and terminates through one
     handle.
   - Start menu: shortcuts with launch arguments (Squirrel `Update.exe`,
     browser `--app-id` and `chrome_proxy.exe`) are not offered; targets are
     sent as long canonical paths.
   - Resident core: menu-open is optional in `StatusSurface`; Windows
     refreshes the tray menu on state changes; `FirstCloseChoice`'s
     `Library/...` path moves into the host's first-close marker.
   - Shared: `NioFolderFileSystem` restricts to owner through
     `AclFileAttributeView` where POSIX modes are absent; `WindowsMain`
     passes `%LOCALAPPDATA%\Posato` for the database and key.
   - Uninstall deletes the installing user's `Run` value when that hive is
     loaded, otherwise the value stays and is inert (the launcher is gone);
     the record names it.
   - Verification: a second standard user in the clone tries the pipe from a
     scheduled task and is refused; a malformed line answers
     `error\trequest`; AC-02 uses a fresh browser profile and a host with
     stable addresses, and the limits name DNS over HTTPS in browsers and
     connections already open; a three-guest smoke (Windows, Ubuntu, Mac
     running at once) runs before the service work.
   - The resident-core extraction lands as its own commit with the `AC-06`
     evidence before Windows code.

## High-risk plan review

- **Verdict:** changes-required (first pass, independent agent)
- **Critical or Required findings:** client trusts a squatted pipe while
  the service is down; `%ProgramData%\Posato` can be pre-created by a user;
  JNA unpacks native code to a temp folder for a LocalSystem service; MSI
  custom actions without conditions, rollback, or upgrade behavior; no
  unattended proof of a refused foreign caller.
- **Resolution:** step 9; second pass pending

## Result

- Pending.

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| pending | | |
