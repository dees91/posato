# Execution: `PLATFORM-001`, `SYNC-018`, `LINUX-001`, `ANDROID-001`

- **Brief:** [release-1-5-linux-android.md](../specifications/release-1-5-linux-android.md)
- **Status:** `active`
- **Review tier:** high-risk
- **Implementer:** Claude Code agent
- **Reviewer:** pending
- **Branch:** `platform-linux-android` (stack: `platform-linux`, `platform-android`)
- **Updated:** 2026-10-10

## Plan

1. **M1 seams.** No new sync interface: the folder supplies the existing
   ports, and the Mac graph delegates them to iCloud or the folder by the
   stored choice. `SyncOperationCore`, `SyncWriter`, the reducer, the codecs,
   and the replica store stay unchanged.
2. **M1 folder.** `FolderSyncPorts` implements the existing bootstrap
   account, cloud, key, and mailbox ports over `java.nio` (desktop and
   Android with all-files access), so `BootstrapCoordinator`, `AppleSync`,
   the join wait, and removal work unchanged: the `Posato` directory is the
   zone, the `workspace` file the anchor, a local key file the key item,
   and a missing key is the existing "waiting for the workspace key" state
   that a pairing code ends. The fetch cursor names the last delivered
   bundle; the next fetch marks it committed in a local seen file, so a
   crash re-delivers instead of losing a bundle. `FolderPairing` creates and
   accepts offers. A one-minute poll is the change trigger.
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

- **Verdict:** changes-required (2026-10-10, independent agent), resolved
  in the ADR, brief, and plan before implementation.
- **Critical or Required findings:** (1) one bad file stops sync; (2)
  pairing expiry overstated, labels and associated data; (3) remove
  workspace undefined for other members; (4) no fetch trigger; (5) helper
  protocol cannot answer `EnforcementPort`; (6) helper input, peer, hosts,
  process, and package-removal rules; (7) Android exact alarms, service
  type, Doze, protected packages; (8) missing acceptance criteria; (9)
  `/etc/hosts` cannot make a name resolve to nothing.
- **Resolution:** (1) skip unverifiable files unmarked, name order; (2)
  check character, separate HKDF labels, expiry in associated data, stale
  offers deleted at launch, limits text; (3) no write without the matching
  `workspace` file, "Sync needs attention" and local removal, refuse create
  over an existing file; (4) one-minute poll; (5) apply, clear, and status
  with session ids; (6) as listed in ADR 0010; (7) exact alarms, `specialUse`,
  battery exemption, protected packages never offered; (8) `AC-02`,
  `AC-03`, `AC-04`, `AC-05`, `AC-06`; (9) `0.0.0.0` and `::`, connection
  fails. Recommended: Keychain on the Mac declined (it needs a companion
  protocol change, not less code); Android folder sources, listing cache,
  check character, create refusal, VPN limits, x86-64 build owner, and
  backup exclusion accepted.

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
