# Execution: `PLATFORM-002`

- **Brief:** [Posato for Windows at parity with Linux](../specifications/platform-002-windows.md)
- **Status:** `ready-for-review`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent agent (plan and completed change)
- **Branch:** `platform-002-windows`
- **Updated:** 2026-10-28

## Plan

1. ADR 0010 Windows row; roadmap revision 33 folds `PLATFORM-003` in.
2. Resident core in `app.posato.desktop.resident`; hosts supply a
   `StatusSurface`, a `LoginLaunch`, and a first-close marker.
3. `:windowsHelper` (Kotlin, JNA), contract tests first; details below.
4. Windows host: tray icon, `Run` value, instance lock, Start menu catalog,
   pipe client.
5. MSI (jpackage, WiX 3), optional signing, x64 CI smoke; notices list JNA.
6. Tool first: `posato-control -t windows`; `tools/windows-spike` removed.
7. Verification `AC-02` to `AC-06`, then review, quality, closeout.

## High-risk plan review

- **Verdict:** approved after two changes-required passes, resolving a
  squatted pipe, a user-created state folder, JNA unpacking to temp, MSI
  conditions and rollback, proof of a refused caller, and a major upgrade
  deleting the state.

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
- Maintainer decisions (`user-confirmed`, 2026-10-28):
  - The Windows onboarding wording (`onboarding_permission_windows_*`) is
    approved as written.
  - No restricted service SID in this pull request; minimal privileges are
    set. Least privilege becomes one shared rule for the three desktops in
    backlog row `PLATFORM-004` (idea 57).
  - The UAC prompt and the notification-area menu are not driven: an
    accepted verification limit.
  - The expired session a helper keeps after a pause ends while Posato is
    closed (shared code, Linux too, no effect on enforcement) is fixed once
    for both in backlog row `SESSION-011` (idea 58), not here.
- Deviations, `agent-delegated`:
  - Uninstall removes the `Run` value only when the user's hive is loaded;
    otherwise it stays and is inert.
  - The key file's ACL is owner-only but not protected (Java cannot set it).
  - ADR 0010's Windows details and limits wait below for confirmation.

## Windows details pending confirmation

`agent-delegated`, implemented and verified here; moved out of ADR 0010
until the maintainer confirms them.

- **Channel.** `\\.\pipe\app.posato.helper` is the first instance, rejects
  remote clients, and grants only the system and the installing user, who
  is recorded by SID; each caller is identified again by impersonation, and
  a request not sent within five seconds is cut off. The client sends
  nothing unless the Service Control Manager reports the pipe's server
  process as the `LocalSystem` service from the installed image.
- **Websites.** Each host and its `www` name are resolved and blocked at the
  outbound connect layers for the installing user only, in one dynamic WFP
  session with its own sublayer; names are resolved again every minute and
  addresses only added until the end. Loopback, unspecified, link-local,
  multicast, and private addresses are never filtered. Filters vanish with
  the session; a restart applies the saved pause again.
- **Applications and state.** Start menu shortcuts to a local `.exe` only,
  no launchers or interpreters; state in a SYSTEM-owned
  `%ProgramData%\Posato`, kept by a major upgrade, removed on uninstall.
- **Draft limits** for `DOCS-007`:
  - On Windows, website blocking stops connections to the addresses that a
    paused website's name resolves to. A site that shares addresses with
    other sites, such as one behind a large content network, can block
    those sites too, and a site that moves to new addresses can open until
    Posato sees them, within about a minute. A browser with its own DNS over
    HTTPS can reach addresses Posato has not seen, and a connection already
    open when the pause starts stays open. There is no pause page, and a VPN
    or a proxy bypasses it.
  - On Windows, the installer sets up a system service with administrator
    approval. While a pause runs, the service ends the paused apps you
    chose, so unsaved work in them can be lost. It clears the pause at its
    end time even when Posato is closed, and uninstalling Posato clears an
    active pause. Microsoft Store apps and shortcuts that start a script or
    a command line cannot be paused. Posato pauses only the Windows account
    that installed it.

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
| `AC-04` Windows, Ubuntu, and Mac guests in one folder workspace | Pass (Windows MSI built from `a97154be`); rerun at the final head pending | `runs/ac04-750c964d` |
| `AC-05` arm64 upgrade during a pause and clean uninstall | Pass | `runs/ac05-750c964d`, `runs/ac05-uac-696d8ec6` |
| `AC-05` x64 build, install, pause, uninstall on CI | Pass | "Windows package" workflow |
| `AC-06` Mac VM and Linux clone on the shared core | Pass | `runs/ac06-mac-750c964d`, `runs/ac06-linux-750c964d` |
| `R1` and `R2` shown failing before the fix | Reproduced | `runs/r1-before-a97154be`, `runs/r2-before-a97154be`, `runs/r2-after-696d8ec6` |
| `./gradlew quality` at `724fa6d2` plus the closeout | Pass | local |
