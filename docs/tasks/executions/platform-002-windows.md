# Execution: `PLATFORM-002`

- **Brief:** [Posato for Windows at parity with Linux](../specifications/platform-002-windows.md)
- **Status:** `ready-for-review`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent agent (plan and completed change)
- **Branch:** `platform-002-windows`
- **Updated:** 2026-10-28

## Plan

1. ADR 0010 Windows row and limits (`user-confirmed` 2026-10-27); roadmap
   revision 33 folds `PLATFORM-003` in; the Projects mirror follows.
2. Resident core (`PLATFORM-003`) in `app.posato.desktop.resident`: menu
   actions, close and keep-running rules, quit and first-close notices, the
   status-menu refresh, one `ResidentPosato`; hosts supply a `StatusSurface`,
   a `LoginLaunch`, and a first-close marker. No behavior change.
3. `:windowsHelper` (Kotlin, JNA), contract tests first: a LocalSystem
   service with one line per connection on `\\.\pipe\app.posato.helper`
   (first instance, remote clients refused, DACL for SYSTEM and the
   installing user, caller from impersonation, 5 s deadline); WFP block
   filters in a dynamic session, limited to the installing user, never
   loopback or private ranges, at most 16,384 addresses, names re-resolved
   every minute; chosen processes of that user ended every second; state in
   a SYSTEM-owned `%ProgramData%\Posato`.
4. Windows host: notification-area icon, `Run` value with `--background`,
   instance lock and show socket in `%LOCALAPPDATA%\Posato`, Start menu
   catalog without launchers, interpreters, or packaged apps, a pipe client
   that checks the server before sending.
5. MSI (jpackage, WiX 3) with deferred `install`, rollback, `uninstall`, and
   `purge` (not on a major upgrade); signing only with
   `POSATO_WINDOWS_SIGNING`; an x64 CI build and smoke test; notices list
   JNA.
6. Tool first: `posato-control -t windows`, the skill and feature map;
   `tools/windows-spike` removed.
7. Verification `AC-02` to `AC-06`, then review, quality, closeout.

## High-risk plan review

- **Verdict:** approved after two changes-required passes.
- **Findings resolved before implementation:** a squatted pipe trusted
  while the service is down; a user-created `%ProgramData%\Posato`; JNA
  unpacking to a temp folder; MSI actions without conditions, rollback, or
  upgrade behavior; no proof of a refused foreign caller; a major upgrade
  deleting the state (split into `uninstall` and `purge`).

## Result

- Windows 11 arm64 and x64 run Posato with the shared interface, pauses,
  schedules with the window closed, start at login, and the folder
  workspace; the Mac and Linux run on the shared resident core.
- Found and fixed during verification: a newer MSI installed beside the
  older one (jpackage's `FindRelatedProductsEx` fails in the arm64 guest;
  now `FindRelatedProducts`); the folder workspace refused Windows paths;
  Chrome and Edge shortcuts were not offered; Posato's icon was missing on
  Windows windows; first-close wording now names the notification area.
- Found by review and fixed: the client now checks the server through the
  Service Control Manager (process ID, LocalSystem, installed image), since a
  filtered token cannot open the service's process; the folder setup never
  follows a link and secures an existing folder through one handle; the MSI
  records the installing user by `[UserSID]`, so domain and Microsoft Entra
  accounts work.
- Deviations, `agent-delegated`:
  - Windows onboarding copy is agent-written and awaits maintainer approval.
  - No restricted service SID: the service needs its LocalSystem rights for
    WFP and for ending the user's processes; minimal privileges are set.
  - The UAC prompt and the notification-area menu are not driven.
  - Uninstall removes the `Run` value only when the user's hive is loaded;
    otherwise it stays and is inert.
  - The key file's ACL is owner-only but not protected (Java cannot set it).
- Follow-up: when a pause ends while Posato is closed, the helper keeps
  reporting the expired session until the next pause; shared behavior on
  Linux too, no effect on enforcement.

## Completed-change review

- **Verdict:** approved on the second pass; the first was changes-required.
- **Required, fixed:** server check failing with UAC on (`R1`); a junction
  planted during the folder setup (`R2`).
- **Recommended:** domain and Entra accounts (fixed); restricted service
  SID (recorded above). **Optional:** CI path filter (fixed).

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `AC-02` MSI install, onboarding, Edge and Chrome blocked, app ended, early end, end while closed, malformed and foreign requests | Pass | `runs/ac02-20261027` |
| `AC-02` again with UAC on, Posato on a filtered token | Pass | `runs/ac02-uac-696d8ec6`, `runs/smoke-uac-724fa6d2` |
| `AC-03` schedule with the window closed; start at login after a restart | Pass | `runs/ac03-20261027` |
| `AC-04` Windows, Ubuntu, and Mac guests in one folder workspace | Pass | `runs/ac04-750c964d` |
| `AC-05` arm64 upgrade during a pause and clean uninstall | Pass | `runs/ac05-750c964d`, `runs/ac05-uac-696d8ec6` |
| `AC-05` x64 build, install, pause, uninstall on CI | Pass | "Windows package" workflow |
| `AC-06` Mac VM and Linux clone on the shared core | Pass | `runs/ac06-mac-750c964d`, `runs/ac06-linux-750c964d` |
| `R1` and `R2` shown failing before the fix | Reproduced | `runs/r1-before-a97154be`, `runs/r2-before-a97154be`, `runs/r2-after-696d8ec6` |
| `./gradlew quality` at `724fa6d2` plus the closeout | Pass | local |
