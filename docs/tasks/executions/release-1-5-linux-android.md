# Execution: `PLATFORM-001`, `SYNC-018`, `LINUX-001`, `ANDROID-001`

- **Brief:** [release-1-5-linux-android.md](../specifications/release-1-5-linux-android.md)
- **Status:** `active`
- **Review tier:** high-risk
- **Implementer:** Claude Code agent
- **Reviewer:** independent agent (completed-change review)
- **Branch:** `platform-linux-android` (one branch; see Result)
- **Updated:** 2026-10-11

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

- **One branch instead of the planned stack.** The work landed as one branch
  (`platform-linux-android`); the folder transport, Linux, and Android
  share the onboarding, sync section, and `posato-control` changes, so a
  split would have rebased every commit twice. The commits stay grouped by
  row.
- **Folder transport (`SYNC-018`).** `FolderSyncPorts` implements the
  bootstrap account, cloud, key, and mailbox ports over a `FolderFileSystem`
  port (`NioFolderFileSystem` for the desktop and Android,
  `FoundationFolderFileSystem` with file coordination and security-scoped
  bookmarks on iOS); `FolderPairing` creates and accepts pairing offers;
  `SelectableSyncPorts` keeps iCloud on Apple hosts until a folder is
  chosen. The bootstrap coordinator, exchange loop, and reducer are
  unchanged. Android seals the workspace key with Android Keystore; the
  desktop keeps it in a `0600` file; iOS with complete file protection.
- **Linux (`LINUX-001`).** `:linuxHelper` is the root service (hosts block,
  process ending, self-clear, request validation); `desktopApp` gains the
  Linux entry point, enforcement, desktop-entry app choices, XDG paths, and
  a `.deb` built by `jpackage` with `prerm` and `postinst` scripts.
- **Android (`ANDROID-001`).** `:androidApp` with a DNS-only `VpnService`,
  a usage-access guard service with the block screen, exact-alarm and boot
  wake-ups, launcher app choices, and the shared Compose interface.
- **Defects found by E2E and fixed test first:** a folder file listed by
  stale metadata but gone reads as missing; sweep and removal tolerate it;
  a leftover link attempt for another folder blocked linking; Ed25519 keys
  failed on Android until converted through X.509; a new join attempt now
  clears the previous refusal. Verification-tool defects: the relay raced
  with itself (unique temporary names and a single-instance lock) and the
  pairing-code field was matched inside a sentence.
- **Found and moved to the backlog:** `SESSION-010` (idea 39): a device that
  links while a pause runs adopts it before the policy phase writes the
  set's websites, so it enforces nothing until the next pause. The order
  predates folder sync and also applies to iCloud links.
- **Documentation:** ADR 0010 (Proposed), the limits page's proposed 1.5
  section, the `posato-control` README, the `verify-posato` feature map
  (`folder-sync.md`, `linux-and-android.md`), the unattended verification
  guide (Linux golden VM and Android emulator), and the wiki topics.

## Completed-change review

- **Verdict:** changes-required (2026-10-11, independent agent, head
  `bc6b55c2`), resolved before the final verification.
- **Required findings and resolution:**
  1. The Linux service stopped answering after one failed connection:
     every connection is answered on a worker with a five-second deadline,
     and a failure ends only that connection; the client times out too, and
     `clear` no longer reports success while a service is present but silent.
  2. One failed tick stopped the self-clear for good: each tick catches its
     failure and the next tick retries.
  3. Choosing an app whose desktop entry starts an interpreter would end
     every program of that interpreter: only native (ELF) programs that are
     not interpreters or launchers, or a whole snap, can be chosen, and the
     service refuses any directory matcher but one snap's
     (regression test first).
  4. Android DNS used the network of the tunnel start: the service follows
     the best non-VPN network and binds each forwarded question to it.
  5. An app already in front when a pause started stayed uncovered: the
     guard follows the app in front across polls.
  6. `:androidApp` was outside the quality gate: its Detekt, ktlint, and unit
     tests now run in `quality` and `qualityLint` (and their findings are
     fixed).
  7. Three Android Lint suppressions in the manifest: removed;
     `QUERY_ALL_PACKAGES` became a `<queries>` declaration for launcher,
     home, and Settings activities.
  8. Agent-delegated limits in the product limits page: moved to ADR 0010
     ("Limits to publish") until the ADR is accepted.
  9. ADR claims that did not match the code (socket mode, iOS background
     refresh, the DNS answer): corrected.
  10. Quality failed on the head: the folder transport, file systems, graphs,
      and sync section were split to the Detekt limits; `quality` passes.
- **Recommended, accepted:** the seen-file size limit is applied; exclusive
  writes use a hard link where the file system has them; pairing reads the
  offer with a coordinated read; Keystore I/O and provider failures read as a
  damaged key; a package upgrade restarts a running service.
- **Recommended, not taken:** the iOS bookmark's stale flag is not renewed
  (no unattended iPhone run could exercise it, see Blockers).

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` at `73f93653` | Passed (207 s) | gradle-run log of the workflow |
| Contract tests written first: folder mailbox, pairing, stale folder, workspace, helper request, hosts block, process match, DNS packet | Passed in `quality` | `shared` jvmTest, `linuxHelper:test`, `androidApp:testDebugUnitTest` |
| `AC-01` two Tart Macs, relay, code join, set and session, removal | Passed (2026-10-10) | `build/verification/e2e/m1-pass-20261010.txt` |
| `AC-02` Linux clone at `73f93653`: install, service, hosts block, calculator ended, early end, malformed host refused, self-clear with the app closed, expiry after relaunch, removal | Passed | `build/verification/e2e/m2-final.log` |
| `AC-03` Android emulator at `73f93653`: onboarding, DNS block, other names resolve, Clock covered, early end | Passed | `build/verification/e2e/m3-final.log` |
| `AC-03` schedule from a Mac starting on Android in the background and after `adb reboot` | Passed | `build/verification/e2e/m5-b-pass-20261011.txt` |
| `AC-04` Android and Linux: pause both ways (24 s, 36 s) | Passed | `build/verification/e2e/m5-a-pass-20261011.txt` |
| `AC-04` new Linux device ended during its first exchange catches up the Mac's set and schedule | Passed | `build/verification/e2e/m5-c-pass-20261011.txt` |
| `AC-05` mistyped code refused on a Mac, workspace intact, right code joins; expiry, tampering, other workspace by contract tests | Passed | `build/verification/e2e/ac05-pass-20261011.txt` |
| `AC-06` removal on Linux: folder emptied, Android needs attention, nothing re-created | Passed | `build/verification/e2e/ac06-multi-pass-20261011.txt` |
| Mac at the final revision: folder remove, create, code, remove; Use another folder returns to iCloud; iCloud links (the first sync on the test account did not finish and stays retryable) and its removal works | Passed | `build/verification/e2e/mac-final.log` |
| `AC-07` test iPhone in folder mode | Blocked | see Blockers |

## Blockers and accepted risks

- **`AC-07` is blocked.** `posato-control install -t device` fails with
  `NO_CONNECTED_DEVICE`: the test iPhone is paired over the network only and
  the driver needs it on a cable. The iOS folder mode compiles and passes the
  iOS host build check, but it has not run on the iPhone. Next action for the
  maintainer: connect the test iPhone by cable; the run then needs a Tart Mac
  whose folder is in iCloud Drive, as prepared for `AC-05`.
- The x86-64 Linux package needs an x86-64 build machine (`RELEASE-007`).
- `SESSION-010` (idea 39): a device that links while a pause runs enforces it
  only from the next pause; the order predates folder sync.
- The GitHub Projects mirror has no "1.5" option in its Release field; the
  release 1.5 items have no Release value until the option is added in the
  project settings.
- Real folder services (Dropbox, OneDrive, Syncthing) are not verified; the
  relay stands in for them, and iCloud Drive was used on the Mac only.

## Final

- **Status:** ready for review
