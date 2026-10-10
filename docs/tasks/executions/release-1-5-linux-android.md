# Execution: `PLATFORM-001`, `SYNC-018`, `LINUX-001`, `ANDROID-001`

- **Brief:** [release-1-5-linux-android.md](../specifications/release-1-5-linux-android.md)
- **Status:** `active`
- **Review tier:** high-risk
- **Implementer:** Claude Code agent
- **Reviewer:** pending
- **Branch:** `platform-linux-android` (stack: `platform-linux`, `platform-android`)
- **Updated:** 2026-10-10

## Plan

1. **M1 seams.** Extract a transport-neutral `WorkspaceSync` interface from
   `AppleSync` for its consumers (`PosatoApplication`, policy and schedule
   stores, sync UI). Extract `BundleMailbox` (save, fetch changes, delete
   all) without `AccountBinding`; the Apple adapters keep the binding inside.
   Reuse `SyncOperationCore`, `SyncWriter`, the reducer, the codecs, and the
   replica store unchanged.
2. **M1 folder.** `FolderSync` implements `WorkspaceSync` with a
   `FolderMailbox` over a small `FolderAccess` port (list, read, write
   atomically, delete; `java.nio` on desktop, Storage Access Framework on
   Android), a `PairingEnvelope` (HKDF from the code, AES-GCM), a
   `LocalWorkspaceKeyStore` port (0600 file on desktop, Keystore-wrapped
   file on Android), and its own bootstrap state table. The fetch cursor
   names the last delivered bundle; the next fetch marks it committed in a
   local seen table, so a crash re-delivers instead of losing a bundle.
3. **M1 UI and Mac.** The sync section offers **Sync with iCloud** (Apple
   hosts only) and **Sync with a folder**: choose a folder, create or join
   with a code, show a code, remove. The Mac graph chooses the transport
   from the stored mode. `posato-control` gains a shared host folder for
   Tart guests (`--dir`) and flow steps for the folder path.
4. **M2 Linux.** OS switch in `Main.kt` and `desktopApp/build.gradle.kts`
   (macOS tasks guarded, Linux compose runtime, `.deb`); XDG paths;
   `linuxHelper` module (systemd unit, Unix socket, hosts block, process
   ending, self-clear at end); `LinuxLocalApplicationMappings` from desktop
   entries; `JvmSessionEnforcement` with Linux links; no updater or menu bar
   presence. Driver: `VmLine.LINUX`, guest JDK and package, Linux desktop
   backend over AT-SPI (fallback: a verification-only seam), `xdotool`,
   screenshots.
5. **M3 Android.** `:androidApp`, `AndroidApplicationGraph`, SQLDelight
   Android driver, `DnsVpnService`, `UsageBlockService` with `BlockActivity`,
   package-based mappings, notifications, foreground service while a pause
   or schedule is on, boot receiver. Driver: `Target.ANDROID` over `adb` and
   UIAutomator dumps, `appops` grants, SAF folder picker, and a host-side
   folder relay between the emulator and the shared host folder.
6. Docs: limits page, availability, wiki topics, `verify-posato` feature
   map, one wiki-log entry per pull request.

## High-risk plan review

- **Verdict:** pending

## Result

- Pending.

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |

## Blockers and accepted risks

- None yet.

## Final

- **Status:** active
